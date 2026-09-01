# 06 — IMPLEMENTATION PLAN (PHASES 1–11)

Conventions: each phase lists Objective · Files likely to change/create · Tasks · Tests · Dependencies · Completion criteria.
**Phase 1 is complete (this audit). Nothing beyond Phase 1 has been started.**

---

## PHASE 1 — Project + Excel Audit ✅ (this document set)
- **Objective:** inspect project, profile both Excel files, define Parcel ID strategy, mappings, architecture docs.
- **Deliverables:** 01–06 markdown docs; zero code changes.
- **Completion criteria:** all docs reviewed and approved by data owner.

## PHASE 2 — Database Models + Migrations
- **Objective:** stand up Django project + `Ruda_Survey` PostgreSQL schema exactly per doc 03.
- **Files:** `manage.py`, `config/settings/{base,dev,prod}.py`, `apps/surveys/models.py` (+`parcels`, `import_batches`, `audit_logs`, images, revisions), migrations, DB triggers/permission SQL, `.env.example`.
- **Tasks:** models with constraints/indexes; immutability triggers + app-role grants; admin read-only registrations; CI lint setup; git init.
- **Tests:** migration reversibility; trigger blocks UPDATE/DELETE on survey_master/survey_changes; unique constraints (`parcel_code`, `(parcel_id,revision_no)`, `(revision_id,image_type)`); NULL discipline checks.
- **Dependencies:** PostgreSQL 15 instance; approved doc 02/03.
- **Done when:** `pytest` green; `makemigrations --check` clean; triggers proven by tests.

## PHASE 3 — Excel Import
- **Objective:** deterministic import of master workbook into immutable survey_master + derived parcels.
- **Files:** `apps/surveys/importer/` (parser, parcel-derivation, manifest writer), management command `import_master`, fixtures of file hash.
- **Tasks:** openpyxl cached-value read; sentinel rules per doc 02 §F; NID fill-down + NID-606 split policy (pending data-owner answer §G4); warnings manifest (corrupted cells, text numerics); re-import creates new batch only.
- **Tests:** golden-file import → row count 14,872; spot assertions vs known rows (5, 7005, 14875); NULL counts match audit; idempotency (double import = second batch, no mutation).
- **Dependencies:** Phase 2; answers to doc 02 §G open questions (non-blocking defaults defined).
- **Done when:** imported DB matches audit statistics report; manifest review signed off.

## PHASE 4 — Django REST APIs
- **Objective:** implement all doc 04 endpoints v1.
- **Files:** serializers, viewsets/apiviews, urls, permissions, throttles, JWT settings, error-handler middleware, OpenAPI schema (drf-spectacular).
- **Tests:** APITestCase per endpoint incl. auth matrix, validation errors, 409 idempotency replay, pagination, immutability (no PATCH routes exist).
- **Dependencies:** Phases 2–3.
- **Done when:** contract tests green; Postman/collection smoke run documented.

## PHASE 5 — Immutable Revision Engine
- **Objective:** revision numbering, parent chaining, diff computation, current-view overlay.
- **Files:** `services/revisions.py`, signals for audit entries, `current` overlay logic in API layer.
- **Tests:** chain integrity (rev1→N), concurrent submissions serialize correctly, stale-parent conflict path, master untouched after revisions (hash compare).
- **Dependencies:** Phase 4.
- **Done when:** adversarial test suite proves no mutation path exists.

## PHASE 6 — Android Survey Form
- **Objective:** app skeleton + Login→Dashboard→Search→Original→Edit→Review flow against APIs.
- **Files:** Gradle setup, theme (Material3/XML), nav graph, Room schema v1, Retrofit client, ViewModels/Fragments per doc 05, null-hiding helpers.
- **Tests:** unit (validators, use cases), Room DAO tests, screenshot/intent tests for null-field hiding.
- **Dependencies:** Phase 4 endpoints available on staging.
- **Done when:** full manual workflow passes on device over Wi-Fi.

## PHASE 7 — Camera/Image Capture
- **Objective:** two-image capture bound to parcel+revision.
- **Files:** CaptureFlowActivity (CameraX), ImageRepository, multipart upload API, thumbnail cache.
- **Tests:** capture flow instrumentation (both types enforced), upload idempotency, duplicate-type 409 handling, EXIF stripping verified.
- **Dependencies:** Phase 6.
- **Done when:** images visible server-side under correct storage keys.

## PHASE 8 — Offline Sync
- **Objective:** offline-first queue with WorkManager per doc 05 §4.
- **Files:** SyncQueueDao, SyncWorker/ImageUploadWorker, connectivity observer, conflict UX strings.
- **Tests:** airplane-mode E2E (draft→capture→sync later), kill/reboot persistence, backoff behavior, conflict re-base path.
- **Dependencies:** Phases 6–7.
- **Done when:** 24 h field simulation loses nothing and reconciles cleanly.

## PHASE 9 — Survey Sheet + PDF
- **Objective:** sheet rendering (HTML preview) + official PDF export matching WB2 layout.
- **Files:** backend sheet/pdf services (doc 04 #8/#9), Android preview screen, print share intent.
- **Tests:** pixel/regression snapshot of generated PDF for golden parcels; header/footer stamps (revision, timestamp, user).
- **Dependencies:** Phase 5 (current-state logic).
- **Done when:** PDF accepted by survey team sample review.

## PHASE 10 — Audit + Security
- **Objective:** complete audit trail + hardening.
- **Files:** audit middleware/signals (doc 03 §2.5 events), rate-limit tuning, secret management, TLS config, role matrix enforcement, retention job stubs.
- **Tests:** every user action produces expected audit row; permission escalation attempts fail; penetration checklist (auth bypass, IDOR on parcel ids, mass-assignment).
- **Dependencies:** Phases 4–9.
- **Done when:** security checklist signed; audit queries satisfy proof scenarios.

## PHASE 11 — Testing + Production QA
- **Objective:** production readiness.
- **Files:** load scripts (locust/k6), UAT scripts, backup/restore runbook, deployment configs, monitoring hooks.
- **Tests:** full regression; UAT with real surveyors on PKG-14 area; restore drill; performance budget (search <300 ms p95, PDF <3 s).
- **Dependencies:** all above.
- **Done when:** go-live sign-off.

---

## Immediate Next Actions (awaiting approval — DO NOT START AUTOMATICALLY)
1. Approve docs 01–06 (esp. Parcel ID strategy doc 02 §C3 and open questions §G).
2. Provision PostgreSQL + decide repo layout (backend/android monorepo).
3. Begin Phase 2.
