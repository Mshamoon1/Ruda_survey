# 04 — API ARCHITECTURE

**Status: IMPLEMENTED in Phase 4 (see §AS-BUILT below). The original Phase-1 proposal is preserved above the as-built section where still accurate.**

---

## AS-BUILT (PHASE 4) — authoritative

**Stack:** Django 5.2 LTS · DRF 3.18 · SimpleJWT 5.5 (blacklist app enabled) · drf-spectacular 0.30 · PostgreSQL 18.6.

### Versioning
All endpoints live under `/api/v1/`. Additive changes only within v1; a breaking v2 would mount at `/api/v2/` alongside. Machine-readable schema at `/api/v1/schema/` (OpenAPI 3, JSON via `Accept: application/vnd.oai.openapi+json`) and Swagger UI at `/api/v1/docs/`.

### Authentication
* `POST /api/v1/auth/login/` → `{access(30 min), refresh(7 d), user{username, role}}`; throttled `30/min`.
* `POST /api/v1/auth/refresh/` → rotation ON, old refresh blacklisted; `60/min`.
* `POST /api/v1/auth/logout/` → blacklists supplied refresh token.
* Header: `Authorization: Bearer <access>`. Passwords/tokens are never logged.

### Roles (auto-created profile per user, default SURVEYOR)
| Role | Read | Create revision / upload image | Status transitions (synced/rejected) | Audit + stats |
|---|---|---|---|---|
| SURVEYOR | ✔ | ✔ | ✖ | ✖ |
| SUPERVISOR | ✔ | ✔ | ✔ | ✖ |
| ADMIN | ✔ | ✔ | ✔ | ✔ |

### Endpoints (all under /api/v1/, all JSON unless noted)
| Method+Path | Auth | Notes |
|---|---|---|
| POST auth/login · auth/refresh · auth/logout | mixed | envelope errors |
| GET auth/me | any | username+role |
| GET surveys/parcel/{parcel_code}/ | any | exact indexed lookup; 404 PARCEL_NOT_FOUND |
| GET surveys/{code}/original/ | any | master lines + batch provenance; **no PUT/PATCH/DELETE routes exist** |
| GET surveys/{code}/current/ | any | `source: master|revision`, `revision_no`, effective `data` |
| GET surveys/{code}/revisions/ | any | paginated (`page`,`page_size`≤100), newest first |
| POST surveys/{code}/revisions/ | surveyor+ | body `{client_uuid, data{…WB2 fields incl. explicit nulls}, change_reason?, device_info?, parent_revision_no?}`; throttled 120/h |
| POST surveys/{code}/revisions/{rev}/images/ | surveyor+ | multipart FRONT|SECOND ≤10 MB; magic-byte+Pillow validation; throttled 60/h |
| POST surveys/{code}/revisions/{rev}/status/ | supervisor+ | `{target: synced|rejected, reason?}` — delegates to `transition_status()` only |
| GET surveys/{code}/sheet/ | any | UI contract: top-level `fields` OMITS nulls; `original` keeps them; images embedded |
| GET surveys/{code}/pdf/ | any | **503 PDF_GENERATOR_UNAVAILABLE** until Phase 9 service lands |
| GET admin/audit-logs/ · admin/stats/ | admin | read-only |

### Revision workflow (server-side guarantees)
1. parcel validated → client_uuid replay returns existing revision with HTTP 200 + `"replayed": true` (fresh = 201);
2. baseline = latest revision else master-mapped ORIGINAL state;
3. unknown/protected payload keys rejected (`VALIDATION_ERROR.unknown_fields`);
4. merged FULL snapshot stored in `full_payload`; field-level diff (with explicit NULL transitions) in `changes`;
5. empty diff ⇒ 400 NO-change error;
6. `revision_no` allocated under `select_for_update()` on the parcel row inside `transaction.atomic()`; `(parcel_id,revision_no)` unique constraint backstops; IntegrityError race ⇒ retry/replay resolution;
7. stale `parent_revision_no` ⇒ 409 REVISION_CONFLICT;
8. creation writes REVISION_CREATED audit entry (user, parcel, revision, IP, correlation id).

Allowed status machine (unchanged from Phase 2): `draft→submitted|rejected`, `submitted→synced|rejected`, terminal otherwise — always via `SurveyChange.transition_status()`. Supervisor "approval" ≡ `synced`.

### Images
Metadata in `survey_images`; bytes on disk at `{MEDIA_ROOT}/surveys/{parcel_code}/{rev:04d}/{TYPE}_{sha256[:10]}.{jpg|png}` (path generated server-side; resolved-path containment asserted). JPEG/PNG by magic bytes + Pillow decode; size cap 10 MB; duplicate type ⇒ 409 IMAGE_ALREADY_EXISTS (old image never auto-deleted); checksum = SHA-256 of decoded bytes.

### Error contract
`{"error":{"code","message","details"}}` — stable codes: AUTHENTICATION_REQUIRED, TOKEN_EXPIRED, INVALID_CREDENTIALS, PERMISSION_DENIED, PARCEL_NOT_FOUND, INVALID_REVISION, REVISION_CONFLICT, IMAGE_ALREADY_EXISTS, INVALID_IMAGE, VALIDATION_ERROR, INVALID_STATUS_TRANSITION, UNSUPPORTED_MEDIA_TYPE, METHOD_NOT_ALLOWED, THROTTLED, NOT_FOUND, PDF_GENERATOR_UNAVAILABLE, SERVER_ERROR. Stack traces are never exposed (unexpected ⇒ SERVER_ERROR).

### Audit events
LOGIN_SUCCESS · LOGIN_FAILURE (username attempted only) · LOGOUT · REVISION_CREATED · REVISION_STATUS_CHANGED · IMAGE_UPLOADED. Insert-only; no credentials recorded.

### Rate limits (ScopedRateThrottle)
login 30/min · auth_refresh 60/min · revision_create 120/hour · image_upload 60/hour. Documented, no extra infra.

### Known limitations
PDF generation deferred to Phase 9 behind the documented 503 boundary; image serving endpoint not exposed (upload-only for now); user management stays in Django Admin; import remains a management command (admin visibility read-only).
