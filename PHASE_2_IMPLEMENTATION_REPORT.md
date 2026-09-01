# PHASE 2 — IMPLEMENTATION REPORT

**Project:** RUDA Survey · Database Models + Migrations + Testing
**Date:** 2026-08-25
**Stack as built:** Python 3.12.10 · Django 5.2.17 (LTS) · PostgreSQL 18.6 (localhost:5433) · psycopg 3.3.4
**Database:** `Ruda_Survey` · application role `ruda_app` (LOGIN, CREATEDB, schema owner; superuser reserved for provisioning)
**Status: ✅ COMPLETE — all mandatory tests executed and passing on live PostgreSQL.**

---

## 1. Files created

```
backend/
├── manage.py
├── requirements.txt                     (Django>=5.2,<6.0 · psycopg[binary] · coverage)
├── .env / .env.example                  (local DB credentials — .env must never be committed)
├── config/
│   ├── __init__.py
│   ├── settings/{__init__,base,dev,test}.py
│   ├── urls.py                          (admin only — no API routes in this phase)
│   └── wsgi.py / asgi.py
├── surveys/
│   ├── __init__.py · apps.py
│   ├── admin.py                         (all six models registered READ-ONLY)
│   ├── models/
│   │   ├── __init__.py                  (model registry)
│   │   ├── immutable.py                 (ORM-level immutability guards)
│   │   ├── parcels.py
│   │   ├── import_batches.py
│   │   ├── survey_master.py
│   │   ├── survey_changes.py
│   │   ├── survey_images.py
│   │   └── audit_logs.py
│   ├── migrations/
│   │   ├── 0001_initial.py              (generated — full schema)
│   │   └── 0002_db_level_immutability.py(hand-written, reversible, vendor-guarded)
│   └── tests/
│       ├── base.py                      (fixtures: parcel/master/revision/image/batch/user)
│       ├── test_parcels.py              (Group A)
│       ├── test_survey_master.py        (Groups B + F)
│       ├── test_survey_changes.py       (Groups C + D + E)
│       ├── test_survey_images.py        (Group G)
│       ├── test_audit_logs.py           (Group H)
│       ├── test_import_batches.py       (Group I)
│       ├── test_relationships.py        (Group J)
│       └── test_admin_readonly.py
└── scripts/dump_schema.py               (live-catalog schema verifier → markdown)
PHASE_2_SCHEMA_SUMMARY.md                (live verification output — tables/PK/FK/UNIQUE/CHECK/indexes/triggers/nullability)
```

## 2. Files modified

| File | Change |
|---|---|
| `03_DATABASE_ARCHITECTURE.md` | added §5 “Phase 2 implementation deltas” (authoritative as-built record) + §2.4 type rename |
| `04_API_ARCHITECTURE.md` | image type SECONDARY → **SECOND** (endpoint #7) |
| `05_ANDROID_ARCHITECTURE.md` | camera flow FRONT→**SECOND** naming |
| `02_EXCEL_MAPPING.md` | **unchanged this phase** (no mapping corrections required) |

## 3. Models created (6)

`Parcel`, `ImportBatch`, `SurveyMaster`, `SurveyChange`, `SurveyImage`, `AuditLog` (+ abstract bases `ImmutableModel`/`AppendOnlyModel`). Physical table names pinned via `Meta.db_table`: `parcels`, `import_batches`, `survey_master`, `survey_changes`, `survey_images`, `audit_logs`.

## 4. Fields created (high level)

* **parcels**: id · parcel_code (unique canonical Parcel ID string, e.g. `RUDA-P14-00001`) · source_nid (nullable, indexed, NOT unique by design) · village/tehsil/district/owner_name_current (denormalized header) · current_revision FK (SET_NULL convenience pointer) · created_at/updated_at.
* **import_batches**: source_filename · file_checksum (SHA-256, indexed) · sheet_name · total_rows/successful_rows/failed_rows/warning_count · status · error_summary JSONB · imported_by FK · imported_at.
* **survey_master**: import_batch FK · source_row_number · sr_no · parcel FK · source_nid · **all 30 mapped Excel fields per doc 02 Part D** (`chainage_m … extra_note`; NOT NULL set = {sr_no, project_component, owner_name, village, structure_status, impact_extent}) · is_formula_area/is_formula_compensation · raw_data JSONB (all original cells verbatim) · imported_at.
* **survey_changes**: client_uuid UUID unique · parcel FK · revision_no PositiveInt · parent_revision self-FK nullable · base_master_row_ids BIGINT[] · changes JSONB diff · full_payload JSONB (**complete** survey state) · change_reason · changed_by FK · changed_at · created_at · accepted_at · status draft/submitted/synced/rejected · device_info JSONB.
* **survey_images**: revision FK · parcel FK (denormalized) · image_type FRONT|SECOND · file_path TEXT (no binaries in PG) · original_filename · content_type · file_size · checksum_sha256 indexed · width_px/height_px · captured_at/uploaded_at/uploaded_by · client_uuid unique · sync_status.
* **audit_logs**: occurred_at · user FK nullable · parcel FK nullable · revision FK nullable · action · entity_type/entity_id · correlation_id UUID indexed · details JSONB · device_info JSONB · ip_address.

## 5. Constraints

| Constraint | Table |
|---|---|
| PK `id BIGINT IDENTITY` on all six tables | all |
| UNIQUE `parcel_code` | parcels (+ CHECK non-empty) |
| UNIQUE `(import_batch_id, source_row_number)` | survey_master |
| UNIQUE `(parcel_id, revision_no)` | survey_changes |
| UNIQUE `client_uuid` | survey_changes, survey_images |
| UNIQUE `(revision_id, image_type)` | survey_images (≤1 FRONT + ≤1 SECOND per revision) |
| CHECK `revision_no > 0` | survey_changes |
| CHECK `sr_no > 0`, `source_row_number > 0` | survey_master |
| CHECK `image_type IN ('FRONT','SECOND')` | survey_images |
| CHECK `file_size IS NULL OR ≥ 0`, `sync_status IN ('pending','synced')` | survey_images |
| CHECK `status IN ('draft','submitted','synced','rejected')` | survey_changes |
| CHECK `status IN ('pending','running','completed','failed')` | import_batches |
| FK PROTECT network: batch→master, parcel→master/changes/images/audit, revision→images/audit, parent-revision→child, user→changed_by/uploaded_by/audit | prevents any parent deletion that would orphan immutable history |

## 6. Indexes (every non-obvious choice justified)

| Index | Justification |
|---|---|
| parcels.source_nid | manual-entry fallback lookup until real WB2 Parcel IDs exist (doc 02 §C3.5); deliberately non-unique |
| survey_master.source_nid · village · structure_status | provenance lookups; village/status filtered lists |
| survey_changes.changed_at · status | sync-queue scans (“oldest submitted”), dashboards |
| survey_changes.parent_revision_id | chain walks for audit reconstruction |
| survey_images.checksum_sha256 | dedup/integrity verification |
| audit_logs.(parcel_id, occurred_at), (user_id, occurred_at), action, occurred_at | standard audit queries; composites cover time-range scans per user/parcel |
| import_batches.file_checksum | verify re-import of same workbook |

FK columns are auto-indexed by PostgreSQL/Django (parcel_id everywhere, revision_id, etc.). No index exists without a documented query pattern.

## 7. Migrations

* `surveys/0001_initial.py` — generated, deterministic, creates all six tables with constraints/indexes.
* `surveys/0002_db_level_immutability.py` — hand-written `RunPython` (reversible): creates trigger functions `surveys_block_all_mutation()` and `surveys_changes_lifecycle_guard()` + four triggers (vendor-guarded; no-op outside PostgreSQL). No destructive operations anywhere in the chain.

## 8. Immutability implementation (three layers)

1. **ORM guards** (`surveys/models/immutable.py`): `ImmutableModel.save()` insert-only; `delete()`, queryset `update()/bulk_update()` raise `ImmutableRecordError`. Revisions additionally expose ONLY `transition_status()` (validated state machine draft→submitted→synced/rejected; sets accepted_at automatically).
2. **PostgreSQL triggers** (authoritative):
   - `trg_survey_master_immutable` BEFORE UPDATE OR DELETE → exception;
   - `trg_audit_logs_immutable` BEFORE UPDATE OR DELETE → exception;
   - `trg_parcels_no_delete` BEFORE DELETE → exception;
   - `trg_survey_changes_appendonly` BEFORE UPDATE OR DELETE FOR EACH ROW → DELETE always blocked; UPDATE permitted **only** when solely `status`/`accepted_at` differ, else exception `RUDA-SURVEY: only status/accepted_at may change…`.
   Triggers bind table owners too; escape hatch = explicit superuser-only `ALTER TABLE … DISABLE TRIGGER <name>` (audited action, never used by app code).
3. **Django Admin read-only** for all six models (add/change/delete permissions hard-disabled; viewing allowed).

## 9. Test cases (90 total across groups A–J)

| Group | File | Cases |
|---|---|---|
| A Parcels | test_parcels.py | 8 (creation, uniqueness, duplicate rejection, NID repeatable, NID NULL, indexes, delete protection ×2 incl. childless-trigger case) |
| B Master immutability | test_survey_master.py | 11 (ORM save/update/delete/bulk_update blocked; DB update/delete/JSONB-update blocked by trigger; values intact; bulk_create allowed) |
| C Revision creation | test_survey_changes.py | 3 (master=100 → rev1=120 → rev2=150 story; independent reconstruction of full payload; base row ids recorded) |
| D Revision immutability | test_survey_changes.py | 12 (ORM block ×4; lifecycle channel works; illegal transition rejected; limited_update whitelist; DB delete/payload/changed_by blocked; status-only UPDATE allowed with payload intact; values intact) |
| E Numbering/idempotency | test_survey_changes.py | 7 (1,2,3 sequence; duplicate rev-no rejected; same rev-no other parcel OK; zero/negative rejected; duplicate client_uuid rejected; uuid unique index present; parent protected) |
| F NULL handling | test_survey_master.py | 9 (None→NULL round-trip; `-`/`0`/`Not Identified` sentinels preserved verbatim; required-field NOT NULL meta-check; batch+row uniqueness; positive checks; named indexes; trigger presence) |
| G Images | test_survey_images.py | 8 (FRONT+SECOND accepted; INVALID_TYPE rejected at validation AND by DB CHECK; revision+parcel linkage; duplicate type per revision rejected; checksum stored+indexed; client_uuid idempotency; captured/uploaded timestamps; negative size rejected) |
| H Audit log | test_audit_logs.py | 10 (metadata round-trip; system events NULL user/parcel; ORM update/delete blocked; DB update/delete blocked; content intact; correlation id queryable; occurred_at auto) |
| I Import batch | test_import_batches.py | 8 (verbatim filename incl. spaces; checksum lookup; counters/warnings; invalid status rejected; error_summary JSON; imported_at; master-row linkage; protected from deletion) |
| J Relationships | test_relationships.py + admin tests | navigation parcel→master/revisions/images/audit/batch; five parent-deletion protections; parcels raw-delete trigger; six tables present; admin read-only ×3 |
| Admin read-only | test_admin_readonly.py | 3 |

## 10. Test results (exact)

Command: `DJANGO_SETTINGS_MODULE=config.settings.test python manage.py test surveys`

```
Found 90 test(s).
System check identified no issues (0 silenced).
Ran 90 tests in 3.301s
OK            ← exit code 0
Destroying test database for alias 'default'...
```

| Metric | Value |
|---|---|
| Total | 90 |
| Passed | **90** |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 (PG-specific skips inactive — PostgreSQL WAS available) |
| Coverage (`coverage run/report`, source=surveys) | **98 %** (935 stmts, 20 miss — misses are migration reverse-SQL lines and `__str__` methods) |

During development 14 tests initially failed/errored (transaction-savepoint ordering in raw-SQL assertions, an enum aliasing bug, a missing `timezone` import, one test-side filter bug). All were fixed; final suite is fully green. No failing tests were hidden.

## 11. PostgreSQL verification status — PERFORMED ✅

* `python manage.py check` → 0 issues.
* `makemigrations --check --dry-run` → "No changes detected", exit 0.
* `migrate` against real `Ruda_Survey` (18.6) → all migrations applied, exit 0.
* **Clean-database proof:** scratch DB `ruda_phase2_cleancheck` created empty → `migrate` applied everything (incl. triggers) → dropped afterwards. The test runner likewise rebuilds `test_Ruda_Survey` from scratch every run.
* Live trigger smoke test executed against dev DB before formal suite (all mutations blocked; lifecycle status UPDATE allowed).
* Full catalog introspection written to **`PHASE_2_SCHEMA_SUMMARY.md`**: 6/6 tables exist; PK/FK/UNIQUE/CHECK constraints enumerated; all named indexes present; **4/4 immutability triggers present and enabled**; nullability matches doc 02 Part D exactly.
* SQLite fallback was NOT needed and therefore not exercised.

## 12. Known limitations

1. Superuser can still disable triggers deliberately (`ALTER TABLE … DISABLE TRIGGER`) — accepted, documented escape hatch for genuine corrections; should be paired with a logged change process in Phase 10.
2. `parcels.current_revision` is a plain pointer — kept consistent by the future revision service (Phase 5), not by a database rule.
3. `image_type` enum lives in a CHECK constraint; adding types later = migration.
4. `raw_data` JSONB duplicates every row's cell data (~storage overhead accepted for provenance).
5. Coverage excludes SQL inside migrations (68 % line-coverage on 0002 due to unexecuted `backwards`/non-PG guard paths).
6. `.env` currently holds local dev credentials; must be git-ignored when the repo is initialized (git init itself deferred — not requested).

## 13. Unresolved Parcel ID issue (documented, not silently invented)

Phase 1 doc 02 §C3 defines the strategy but two inputs remain open (§G): exact WB2 `Survey Sheet Parcel ID` format once filled samples exist, and NID-606 split policy confirmation. **Accordingly, Phase 2 implements NO generation logic**: `parcels.parcel_code` is a unique, format-free VARCHAR(32) (CHECK non-empty only), ready to be populated deterministically by the Phase 3 import/parcel-derivation service. Nothing in the schema assumes or fabricates IDs.

## 14. Recommendation for Phase 3

Proceed to **Excel Import** using: openpyxl cached-value reads; sentinel rules per doc 02 §F; contiguous-block parcel derivation with fill-down NULL NIDs and the documented NID-606 split (or keep, pending §G answer); import writes rows via `bulk_create` into `survey_master` (insert-only path already proven by tests), updates `import_batches` counters/error_summary, and assigns `parcel_code` values per the agreed format. Re-import must create a NEW batch — never mutate batch 1. Data-owner answers to doc 02 §G1/G4 should be collected before first production import but do not block building the importer.

---

**Completion criteria checklist:** project valid ✅ · models ✅ · migrations ✅ · clean migrate ✅ · checks pass ✅ · master immutability ✅ · revision immutability ✅ · numbering ✅ · NULL handling ✅ · images ✅ · audit ✅ · import batch ✅ · relationships ✅ · PostgreSQL verification performed ✅ · documentation ✅
