# PHASE 4 — API IMPLEMENTATION REPORT

**Date:** 2026-08-25 · **Status: ✅ COMPLETE**
**Stack added:** DRF 3.18 · SimpleJWT 5.5.1 (token_blacklist) · drf-spectacular 0.30 · Pillow 12.3
**Regression:** Phase 2 (90) + Phase 3 (45) tests still pass unchanged → **204/204 total, 95 % coverage**

---

## 1. Files created

```
surveys/
├── api/
│   ├── __init__.py
│   ├── errors.py            uniform {"error":{code,message,details}} envelope + domain exceptions
│   ├── permissions.py       HasRoleAtLeast / IsSurveyorOrAbove / IsSupervisorOrAbove / IsAdminRole
│   ├── pagination.py        page_size_query_param, max 100
│   ├── serializers.py       explicit request/response schemas (no blind model exposure)
│   ├── urls.py              all /api/v1/ routes
│   └── views.py             auth + survey + revision + image + sheet/pdf + admin views
├── models/user_profile.py   UserRole(SURVEYOR<SUPERVISOR<ADMIN) profile, auto-created via signal
├── signals.py
├── migrations/0003_userprofile.py
└── services/{audit,revision_service,image_service,sheet_service}.py
config/settings/base.py      REST_FRAMEWORK / SIMPLE_JWT / SPECTACULAR settings
config/urls.py               /api/v1/ include + schema/docs
scripts/api_smoke.py         live smoke driver
D:\Ruda_survey\PHASE_4_API_SMOKE_TEST.md
```
Modified: `requirements.txt`, `settings/base.py` (ALLOWED_HOSTS += testserver), `04_API_ARCHITECTURE.md` (as-built section), `models/__init__.py`, `apps.py`.

## 2. Endpoints (all live under `/api/v1/`)

| Method Path | Perm | Purpose |
|---|---|---|
| POST /auth/login/ | anon (30/min) | JWT pair + user{role}; failures audited |
| POST /auth/refresh/ | anon (60/min) | rotation + blacklist |
| POST /auth/logout/ | any | blacklist refresh |
| GET /auth/me/ | any | identity echo |
| GET /surveys/parcel/{code}/ | any | exact indexed lookup; original vs current clearly split; 404 PARCEL_NOT_FOUND |
| GET /surveys/{code}/original/ | any | immutable lines + batch provenance; **no write verbs routed** (405) |
| GET /surveys/{code}/current/ | any | effective state with explicit `source`/`revision_no` |
| GET+POST /surveys/{code}/revisions/ | any / surveyor+ | paginated newest-first history; append-only create |
| POST /surveys/{code}/revisions/{n}/images/ | surveyor+ | FRONT/SECOND multipart upload |
| POST /surveys/{code}/revisions/{n}/status/ | supervisor+ | synced/rejected via transition_status() only |
| GET /surveys/{code}/sheet/ | any | UI payload: `fields` omits NULLs; `original` keeps them |
| GET /surveys/{code}/pdf/ | any | **503 PDF_GENERATOR_UNAVAILABLE** (Phase 9 boundary) |
| GET /admin/audit-logs/, /admin/stats/ | admin | read-only inspection |

## 3. Authentication & permissions
SimpleJWT bearer tokens: access 30 min, refresh 7 d with rotation+blacklist. Roles enforced by rank (SURVEYOR=1 < SUPERVISOR=2 < ADMIN=3). Ordinary users have zero DB-level privileges beyond the app role.

## 4. Revision workflow guarantees (verified by tests)
Complete-snapshot rule (unchanged fields carried forward) · field-level diff incl. explicit NULL↔value transitions · empty-diff rejection · server-derived protected fields (id/revision_no/status/changed_by/accepted_at rejected on input via whitelist) · client_uuid idempotent replay (200 replayed=true vs fresh 201) · `select_for_update()` parcel-row lock ⇒ parallel submits produced exactly {1,2,3,4} · stale parent ⇒ 409 · master untouched (DB re-checked after API writes).

## 5. Image workflow
Magic-byte sniffing (FFD8FF / PNG sig) + Pillow decode + format allow-list; 10 MB cap; SHA-256 checksum; server-generated path `{parcel}/{rev:04d}/{TYPE}_{hash10}.{ext}` inside MEDIA_ROOT (containment asserted); client filename sanitized to basename metadata only; duplicate type ⇒ 409 IMAGE_ALREADY_EXISTS without deleting predecessor; IMAGE_UPLOADED audit entry.

## 6. Audit workflow
LOGIN_SUCCESS / LOGIN_FAILURE (username attempted only) / LOGOUT / REVISION_CREATED (changed-fields summary) / REVISION_STATUS_CHANGED / IMAGE_UPLOADED. IP + X-Request-ID correlation captured. Insert-only table; no passwords/JWTs ever serialized into details (asserted).

## 7. Test results (exact)

```
Found 204 test(s). System check identified no issues.
Ran 204 tests … OK          exit code 0
```

| Metric | Value |
|---|---|
| Total (Phase2+3+4) | **204** (90+45+69) |
| Passed | **204** |
| Failed / Errors / Skipped | 0 / 0 / 0 |
| Coverage (source=surveys) | **95 %** (3,052 stmts, 160 miss — migration reverse-SQL & trivial __str__s dominate misses) |
| `manage.py check` | clean |
| `makemigrations --check` | No changes detected |

New Phase-4 groups: A auth 9 · B authorization 5 · C lookup 5 · D original 4 · E current 3 · F create-revision 9 · G idempotency 1(+live) · H concurrency 1 (4 real threads on PG) · I history 4 · J images 8 · K sheet 4 · L audit 4 · M immutability regression 3 (+ full Phase-2 suite rerun in same run) · N security 6 · O OpenAPI 2.

Notable defects caught during development and fixed: JSONField null-rejection breaking value→NULL transitions; ScopedRateThrottle-without-scope silently dropping endpoints from the OpenAPI schema; missing role gates on status/image endpoints; hash-case mismatch; multipart kwargs misuse in smoke driver.

## 8. Live API smoke test (real DB, real imported data)

`PHASE_4_API_SMOKE_TEST.md` records **20/20 PASS** against PostgreSQL using production parcel `RUDA-P14-R00005`: login (invalid+valid), unauthorized block, lookup, original, current, revision creation (201 + diff), master-unchanged SQL proof, history, FRONT+SECOND uploads (201), duplicate FRONT (409), supervisor sync, sheet (NULL-hiding verified), duplicate client_uuid replay (200 replayed=true, single row), PDF 503 envelope, OpenAPI 200, final counts.

PostgreSQL direct verification after smoke: masters **14,872 (unchanged)** · revisions grew only by smoke activity (7 total, unique per parcel) · images = 3 metadata rows + files on disk · audit rows present for every mutation · no duplicate client_uuids/revision numbers (constraints + queries).

## 9. Known limitations
1. PDF generation intentionally deferred to Phase 9 behind a documented 503 boundary.
2. Image *download/serving* endpoint not exposed yet (upload + storage_key only).
3. User management remains in Django Admin (no user CRUD API — out of brief scope).
4. Throttles are in-memory (per-process); multi-worker deployment should swap cache to Redis (documented, no infra added now).
5. `admin_stats` shape is informational, not contractual.

## 10. Recommendation for Phase 5
Proceed to the Immutable Revision Engine hardening already partially covered here: formalise conflict/rebase policy for offline clients (Phase 8 interplay), add revision-chain integrity service tests (parent pointers, max(revision_no) invariants under load), and expose `current` overlay as a reusable selector shared by sheet/PDF services. No schema changes required.
