# PHASE 3 — IMPLEMENTATION REPORT

**Date:** 2026-08-25 · **Status: ✅ COMPLETE** (import executed, verified, idempotent)
**Scope delivered:** Excel parsing/validation · parcel derivation · immutable master import · batch tracking · error reporting · idempotent re-runs · dry-run · automated tests (Groups A–M) · data-quality reporting

---

## 1–2. Files created / modified

```
backend/surveys/
├── services/
│   ├── __init__.py
│   ├── normalization.py          coercion ladder, owner-key normaliser, corruption detector
│   ├── excel_reader.py           dual-handle read-only reader, header resolver/validator
│   ├── parcel_derivation.py      deterministic grouping engine + quality summary
│   └── excel_import.py           orchestrator: idempotency, atomic write, reports
├── management/commands/import_survey.py     [--dry-run] [--report-out]
├── tests/
│   ├── import_fixtures.py        synthetic workbook builder (real files untouched)
│   ├── test_import_units.py      Groups A + B  (17 tests)
│   ├── test_import_derivation.py Groups C + D + E (14 tests)
│   └── test_import_integration.py Groups F–M on the REAL file (13 tests)
└── scripts/verify_import.py      live-DB integrity verifier → markdown

D:\Ruda_survey\
├── EXCEL_IMPORT_MAPPING.md       header rules + full column→field map
├── PARCEL_DERIVATION_RULES.md    authoritative derivation spec
├── PHASE_3_DRY_RUN_REPORT.md
├── DATA_QUALITY_REPORT.md
├── PHASE_3_IMPLEMENTATION_REPORT.md  ← this file
Modified: requirements.txt (+openpyxl), docs 04/05 already aligned in Phase 2.
02_EXCEL_MAPPING.md unchanged (no mapping corrections needed).
```

## 3. Import service behaviour

* validates file → sheet (`Anex 4.1…`) → title → combined rows-2/3 headers → column signatures;
* parses with cached-formula values; verbatim formula strings into `raw_data`;
  formula-without-cache ⇒ row REJECTED (`FORMULA_NO_CACHE`), never invented;
* derives parcels deterministically (see PARCEL_DERIVATION_RULES.md);
* **idempotency:** SHA-256 match ⇒ `DUPLICATE / ALREADY_IMPORTED`, no writes;
  byte-different re-export of same rows ⇒ `OVERLAPPING_SOURCE` refusal
  (>50 % sr_no overlap) unless operator passes `allow_overlap=True`;
* **atomicity:** parcels+master written in ONE transaction; failure rolls back
  everything and marks the batch FAILED with error JSON. Batch statuses use the
  Phase-2 enum (`pending/running/completed/failed`) — duplicate detection runs
  BEFORE batch creation and returns result-status `DUPLICATE` without creating a
  row (documented compatibility deviation from the suggested status vocabulary);
* provenance per row: filename + batch FK + source_row_number + sr_no +
  source_nid + raw_data(30 verbatim cells) + formula flags.

## 4. Real-data execution (after validated dry run)

| Stage | Result |
|---|---|
| Dry run | `DRY_RUN_OK` — 14,872 rows, 0 rejected, 4,654 candidate parcels (report: PHASE_3_DRY_RUN_REPORT.md) |
| Real import | **COMPLETED**, duration 31.5 s |
| Import batch id | **2** |
| Parcels created | **4,654** |
| Master records imported | **14,872** |
| Rejected / errors | **0 / 0** |
| Warnings | 2,561 classified (DATA_QUALITY_REPORT.md) |
| Second import | `DUPLICATE / already_imported=True`, zero new rows, still 1 batch |

## 5. Live-database integrity verification (all PASS)

17-point check via `scripts/verify_import.py`: batch counters ✓ · counts
(14872/4654) ✓ · no duplicate (batch,row) groups ✓ · no duplicate parcel_code ✓
· provenance completeness (parcel/raw_data/sr_no/batch) ✓ · NULL source_nid =
2,282 exactly ✓ · distinct NIDs = 4,536 ✓ · corrupted-cell rows = 2 verbatim ✓
· sentinel preservation (`-`, `Not Identified`) ✓ · spot checks rows 5/7005/
14875 ✓ · **trigger UPDATE block confirmed against live production rows** ✓.

## 6. Testing (mandatory groups A–M)

Command: `DJANGO_SETTINGS_MODULE=config.settings.test python manage.py test surveys`
plus `manage.py check` and `makemigrations --check --dry-run` (both clean).

| Metric | Value |
|---|---|
| Total tests (whole suite incl. Phase 2) | **135** (45 new for Phase 3) |
| Passed | **135** |
| Failed / Errors | **0 / 0** |
| Skipped | 0 (PostgreSQL available — nothing skipped) |
| Coverage (source=surveys) | **95 %** overall; importer modules 84–97 % |

Group coverage: A file-validation 6 · B header-mapping 6 · C derivation 9 ·
D normalization 8 · E formulas 3 · F master import (within lifecycle class) ·
G immutability 2 · H triple-import idempotency 1 · I batch record 1 ·
J overlap/error handling 1 (+unit-level J cases) · K dry-run zero-writes 1 ·
L source integrity 2 · M completeness/provenance 4.
All fixtures are synthetic in-memory workbooks except the integration group,
which exercises the real hash-pinned master READ-ONLY.

Defects found & fixed during this phase's testing: O(n²) read-only iteration
(per-row `iter_rows`) rewritten to lock-step streaming; missing numeric flags
on chainage/person-count columns (would have leaked strings into NUMERIC
columns — now guarded by registry-consistency test); split-classification now
flags BOTH halves; parcel reuse on allow_overlap re-imports.

## 7. PostgreSQL verification — PERFORMED ✅

All Phase 3 tests and both command executions ran against PostgreSQL 18.6
(`Ruda_Survey` + ephemeral `test_Ruda_Survey`). No SQLite shortcuts were used.

## 8. Source-file integrity

SHA-256 before implementation: `b04dc2b7…949c252` — after import: identical
(asserted in-test AND re-checked post-import). Neither Excel file was modified,
renamed, or re-saved at any point. The second workbook (`RTW PKG-14 …`,
`11EEF33E…`) remains untouched as the future field-survey template.

## 9. Unresolved issues (documented, non-blocking)

1. WB2 `Survey Sheet Parcel ID` real-world format — pending filled samples (§G1).
2. NID-606-style split policy applied here needs formal sign-off (32 NIDs/98 parcels).
3. Chainage unit label vs magnitude mismatch (stored as metres per doc 02 Part D).
4. The 2 corrupted district cells await an eventual corrected workbook (new batch).

## 10. Recommendation for Phase 4

Proceed to Django REST APIs: parcel lookup by `parcel_code`/`source_nid`
(both indexed), `/original/` served straight from `survey_master` (immutable,
already populated), revision creation writing ONLY to `survey_changes`.
JWT auth + throttles per doc 04. No schema changes anticipated.
