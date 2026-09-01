# PHASE 3 — DRY RUN REPORT

**Command:** `python manage.py import_survey "02_Annex 4.1_Affected Residential & Commercial Structures.xlsx" --dry-run`
**Executed:** 2026-08-25 · duration 18.9 s · **Result: `DRY_RUN_OK`**
**Database writes: ZERO** (no parcels, no master rows, not even an import_batches row).

| Metric | Value |
|---|---|
| Source file | `02_Annex 4.1_Affected Residential & Commercial Structures.xlsx` |
| SHA-256 | `b04dc2b708f2a2ba324199fafdc5ad37d61f2f4bf1c4dab4fa81e799a949c252` |
| Sheet | `Anex 4.1 Str(Rpr)Dec25Hsn (F)` |
| Total source rows | **14,872** |
| Valid rows | **14,872** |
| Invalid / rejected rows | **0** |
| Derived candidate parcels | **4,654** |
| Errors | **0** |
| Warnings | **2,561** (classified; see below) |

## Data-quality findings (classified, NOT auto-fixed)

| Class | Count | Disposition per documented rules |
|---|---|---|
| NULL NID continuation rows | 2,282 (matches Phase 1 exactly) | attached to parent parcel via fill-down rule |
| Distinct non-null NIDs | 4,536 | preserved as `source_nid` provenance |
| Multi-owner NIDs (after owner-key normalisation) | **32** (Phase-1 raw count was 54; spelling variants merged by the documented normaliser) | blocks split → 98 AMBIGUOUS parcels |
| Ambiguous split parcels | 98 (e.g. R00046/R00047, R01333/R01334 …) | flagged `AMBIGUOUS`, never merged |
| Suspected corrupted cells (`+K2C2888:T2888`) | 2 (source rows 2899, 2900) | imported verbatim + WARNING |
| Unparseable numerics stored as NULL | 2,559 cells | NULL + WARNING; raw text kept in `raw_data` |
| `-` placeholders in numeric fields | 3,558 (INFO) | NULL per mapping |
| Near-duplicate business tuples | 856 kinds | Phase 1 established these are legitimate multi-floor/meter entries; counted, kept |

## Import readiness verdict

✅ READY — zero errors/rejections, row count and NULL-NID counts match the
Phase 1 audit to the digit, derivation is deterministic (unit-tested), and all
quality issues are classified with documented dispositions.

**Authorised to proceed to the real import** (executed separately — see
`PHASE_3_IMPLEMENTATION_REPORT.md`, batch #2).
