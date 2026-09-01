# 03 — DATABASE ARCHITECTURE (PHASE 1 PROPOSAL)

**Database:** `Ruda_Survey` on PostgreSQL 15+ (PostGIS optional, not required for Phase 2–4 scope).
**Design driver (business rule #1):** the imported master data is **immutable**; every user edit produces a **new revision record**; history is append-only and never rewritten through normal survey editing.

---

## 1. Entity Overview

```
import_batches 1───n survey_master n───1 parcels 1───n survey_changes 1───n survey_images
                                            │                    │
                                            └─────── n audit_logs ┘ (polymorphic refs)

users (Django auth) 1───n survey_changes / audit_logs
```

Core tables (per brief): **survey_master**, **survey_changes**.
Supporting: **survey_images**, **audit_logs**, plus minimal infrastructure tables `parcels`, `import_batches` (justified in §2.0), and Django's built-in `auth_user`.

---

## 2. Table Specifications

### 2.0 `parcels` (supporting — canonical Parcel ID registry)

Justification: the master file has no literal Parcel ID column; §C of doc 02 derives one. A parcel is the unit the user searches for and edits; revisions attach to it.

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGSERIAL PK | NO | Surrogate |
| parcel_code | CITEXT UNIQUE NOT NULL | NO | User-facing ID e.g. `RUDA-P14-00001` |
| source_nid | INTEGER NULL idx | YES | Original hidden col B value |
| village / tehsil / district | VARCHAR | YES | Denormalized header from first master line |
| owner_name_current | VARCHAR(255) | YES | Latest known owner (from latest revision) |
| current_revision_id | BIGINT FK→survey_changes.id NULL | YES | Pointer convenience |
| created_at / updated_at | TIMESTAMPTZ | NO | |

Indexes: `UNIQUE(parcel_code)`, `(source_nid)`, `(village)`.

### 2.1 `import_batches`

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| source_filename | TEXT | Original name incl. spaces |
| file_hash | CHAR(64) | SHA-256 pinning |
| sheet_name / row_count / warning_count | — | Import manifest summary |
| imported_by / imported_at | FK auth_user / TIMESTAMPTZ | |

### 2.2 `survey_master` — IMMUTABLE after import

One row per original Excel line-item (**14,872 rows expected**). Field mapping = doc 02 Part D.

Key columns (full list per doc 02 §D):

| Column | Type | Constraint |
|---|---|---|
| id | BIGSERIAL | PK |
| import_batch_id | BIGINT FK→import_batches | NOT NULL |
| source_row_number / sr_no | INTEGER | `UNIQUE(import_batch_id, source_row_number)` |
| parcel_id | BIGINT FK→parcels | NOT NULL, indexed |
| source_nid | INTEGER | nullable, indexed (provenance) |
| …30 mapped data fields… | per doc 02 §D | mostly nullable |
| raw_data | JSONB | all 30 original cells verbatim |
| is_formula_area / is_formula_compensation | BOOLEAN | provenance flags |
| imported_at | TIMESTAMPTZ DEFAULT now() | NOT NULL |

Immutability enforcement (layered):
1. App layer: Django model is read-only (no PUT/PATCH endpoints, admin read-only).
2. DB layer: trigger `BEFORE UPDATE OR DELETE ON survey_master → RAISE EXCEPTION`; table owned by a role without UPDATE/DELETE grants for the app role.
3. Re-import creates a new `import_batch_id` rather than mutating rows.

Indexes: `(parcel_id)`, `(source_nid)`, `(village)`, `(structure_status)`.

### 2.3 `survey_changes` — APPEND-ONLY revision log

One row per submitted revision of a parcel. Never updated/deleted by normal editing.

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGSERIAL PK | NO | |
| client_uuid | UUID UNIQUE NOT NULL | NO | Offline idempotency key generated on device |
| parcel_id | BIGINT FK→parcels NOT NULL idx | NO | |
| revision_no | INTEGER NOT NULL | NO | `UNIQUE(parcel_id, revision_no)`; assigned server-side at accept time |
| parent_revision_id | BIGINT FK→survey_changes NULL idx | YES | NULL ⇒ base = master snapshot |
| base_master_row_ids | BIGINT[] | NO | Master lines this revision covers |
| changes | JSONB NOT NULL | NO | Field-level diff `{field:{from,to}}` vs previous state |
| full_payload | JSONB NOT NULL | NO | Complete WB2-schema snapshot of this revision |
| status | VARCHAR(16) CHECK IN ('draft','submitted','synced','rejected') | NO | draft rows live on device only until synced |
| created_by | BIGINT FK→auth_user NOT NULL | NO | |
| device_info | JSONB | YES | model/os/app-version (doc 05) |
| created_at / accepted_at | TIMESTAMPTZ | NO/YES | |

Append-only enforcement: same trigger/grant pattern as survey_master (`BEFORE UPDATE OF (status…) limited; DELETE blocked`). Only `status` transitions draft→submitted→synced/rejected are permitted updates, via a dedicated narrow function with audit write.
Revisions are **never** physically deleted; superseded revisions simply stop being "current".

Chain semantics:
```
master (immutable)
   └─ rev 1 ─ rev 2 ─ rev 3 …        (each immutable once accepted; "current" = max(revision_no))
```

### 2.4 `survey_images`

Binary files live on disk/object storage; only metadata here.

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGSERIAL PK | NO | |
| revision_id | BIGINT FK→survey_changes NOT NULL idx | NO | Image belongs to a revision |
| parcel_id | BIGINT FK→parcels NOT NULL idx | NO | Denormalized for direct parcel queries |
| image_type | VARCHAR(12) CHECK IN ('FRONT','SECOND') NOT NULL | NO | Brief's two images (renamed from SECONDARY in Phase 2 — see §5) |
| file_path | TEXT NOT NULL | NO | Storage-relative path |
| checksum_sha256 | CHAR(64) NOT NULL | NO | Integrity + dedup |
| mime_type / size_bytes / width_px / height_px | — | YES | |
| captured_at | TIMESTAMPTZ | YES | Device clock at shutter |
| uploaded_by FK→auth_user / uploaded_at / sync_status | — | NO/NO/'pending'/'synced' | |
| client_uuid | UUID UNIQUE | NO | Idempotent upload |

Constraint: `UNIQUE(revision_id, image_type)` → exactly ≤2 images per type per revision.
Storage layout: `{MEDIA_ROOT}/surveys/{parcel_code}/{revision_no:04d}/{image_type}_{checksum10}.{jpg|png}`
(e.g. `surveys/RUDA-P14-00042/0003/FRONT_9f31ab02c1.jpg`). Rationale against storing binaries in PostgreSQL: backup bloat, streaming cost, no transactional need; LargeObjects/BLOB rejected unless a documented compliance reason appears.

### 2.5 `audit_logs`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGSERIAL PK | NO | |
| occurred_at | TIMESTAMPTZ DEFAULT now() NOT NULL | NO | Server time |
| user_id | BIGINT FK→auth_user NULL | YES | NULL only for system jobs |
| parcel_id / parcel_code | BIGINT / CITEXT | YES | Either may be null for login events etc. |
| action | VARCHAR(48) NOT NULL | NO | LOGIN, PARCEL_SEARCH, REVISION_CREATED, IMAGE_UPLOADED, SYNC_COMPLETED, EXPORT_PDF… |
| entity_type / entity_id | VARCHAR(32) / BIGINT | YES | 'revision', 'image', 'parcel'… |
| revision_no | INTEGER | YES | |
| details | JSONB | YES | diff summary, search term, error info |
| device_info | JSONB | YES | |
| ip_address | INET | YES | |

Insert-only (no UPDATE/DELETE grants). Indexes: `(parcel_id, occurred_at)`, `(user_id, occurred_at)`, `(action)`.

---

## 3. Cross-Cutting Rules

1. **NULL discipline:** empty ⇒ NULL; sentinel strings that exist in the source stay verbatim in master; display normalization happens in API serializers/view-layer only (doc 02 §F).
2. **Immutability matrix**

| Table | INSERT | UPDATE | DELETE |
|---|---|---|---|
| survey_master | import job only | ✖ blocked | ✖ blocked |
| parcels | import/system | header refresh only (logged) | ✖ blocked |
| survey_changes | API accept path | status-transition function only | ✖ blocked |
| survey_images | upload path | sync_status flip only | ✖ (retention policy later) |
| audit_logs | system | ✖ | ✖ |

3. **Money/measure types:** NUMERIC everywhere; never FLOAT for compensation/rates.
4. **Time:** TIMESTAMPTZ, UTC storage, Asia/Karachi rendering.
5. **Migrations:** Django migrations are the single schema authority from Phase 2 onward.

## 4. Expected Volumes

survey_master ≈ 14,872 rows/import · parcels ≈ 4,500–5,000 · revisions grow ~O(parcels × visits) · images = 2×revisions ≈ modest GBs/year on disk — comfortably within default PostgreSQL/file-system settings.

---

## 5. PHASE 2 IMPLEMENTATION DELTAS (as built — this section is authoritative)

Implemented in `backend/` (Django 5.2 LTS, psycopg 3, PostgreSQL). Migrations: `surveys/0001_initial.py` (schema) + `surveys/0002_db_level_immutability.py` (triggers).

| Doc-03 proposal | As implemented | Reason |
|---|---|---|
| `parcel_code CITEXT` | `VARCHAR(32) UNIQUE CHECK (<> '')`, index | CITEXT needs a PG extension; format normalization moves to app layer (Phase 3/4) |
| image types `FRONT/SECONDARY` | **`FRONT` / `SECOND`** | Phase 2 brief renamed the second type; docs 04/05 updated accordingly |
| `size_bytes` | `file_size BIGINT` + CHECK ≥ 0 | naming clarity |
| revision timestamps `created_at/accepted_at` | added `changed_at DEFAULT now()` as well (brief requires changed_at + created_at + accepted_at) | client edit time vs row creation vs server acceptance are distinct facts |
| parcels: no updated_at | added `updated_at auto_now` | header refresh tracking |
| immutability "trigger and/or privileges" | **Triggers** (owner-proof), single owning role `ruda_app` | table owners bypass privilege checks but never triggers; escape hatch = explicit superuser `ALTER TABLE … DISABLE TRIGGER` (auditable action) |
| audit user FK nullable SET_NULL | nullable **PROTECT** | SET_NULL would mutate immutable rows on user deletion; users are deactivated instead |
| `survey_changes.changed_by` FK NOT NULL | kept NOT NULL PROTECT | attribution permanence |
| parcels DELETE | blocked by child-FK PROTECT **and** trigger `trg_parcels_no_delete` | even childless parcel cannot dangle history |
| `raw_data JSONB` | kept, NOT NULL | provenance of all 30 original cells |

Additional constraints/indexes beyond doc 03: `ck_master_sr_no_positive`, `ck_master_source_row_positive`, `ck_survey_changes_revision_no_positive`, `ck_survey_images_size_nonnegative`, `ck_survey_images_sync_status_valid`, index `idx_changes_changed_at`, indexes on `status` (sync queue lookups) and `checksum_sha256`, composite audit indexes `(parcel_id, occurred_at)` / `(user_id, occurred_at)`.

Enforcement layers (all three verified by tests):
1. ORM guards (`surveys/models/immutable.py`) — INSERT-only save(), blocked delete()/update()/bulk_update(); revisions expose only `transition_status()` for status/accepted_at.
2. PostgreSQL triggers — `surveys_block_all_mutation()` on survey_master & audit_logs (UPDATE+DELETE) and parcels (DELETE); `surveys_changes_lifecycle_guard()` permits UPDATE only when solely status/accepted_at change; DELETE always blocked.
3. Read-only Django admin for all six models.

Database role model: `ruda_app` (LOGIN, CREATEDB for test runner, owns schema) + superuser reserved for provisioning/escape hatch.

