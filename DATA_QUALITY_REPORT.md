# DATA QUALITY REPORT — MASTER IMPORT (PHASE 3)

**Source batch:** import_batch #2 · `02_Annex 4.1_Affected Residential & Commercial Structures.xlsx`
**SHA-256:** `b04dc2b7…949c252` (verified unchanged after import)
**Scope:** 14,872 imported rows · 4,654 canonical parcels

Severity legend: **VALID** = clean · **WARNING** = imported with classified
observation · **AMBIGUOUS** = parcel required conservative interpretation ·
**ERROR** = rejected (none occurred).

---

## 1. Issue inventory (whole-file parse)

| Code | Severity | Cells/Rows | Meaning |
|---|---|---|---|
| `DASH_PLACEHOLDER_NULL` | INFO | 3,558 | `-` in numeric fields → NULL per mapping |
| `INVALID_NUMERIC_STORED_AS_NULL` | WARNING | 2,559 | unparseable numeric text → NULL; verbatim kept in `raw_data` |
| `SUSPECTED_CORRUPTED_CELL` | WARNING | 2 (rows 2899, 2900) | district contains pasted range artifact `Lahore+K2C2888:T2888`, stored verbatim |
| `CONTINUATION_OWNER_DRIFT` / `VILLAGE_DRIFT` | WARNING | within-block drifts | reported on affected parcels |
| `SPLIT_FROM_MULTI_OWNER_NID` | WARNING | 98 parcels / 32 NIDs | documented conservative split |

Totals: **0 ERROR rows · 2,561 warnings · 14,872/14,872 rows VALID-for-import.**

## 2. Multi-owner NIDs (top examples, post-normalisation)

NID → distinct owner keys found inside one contiguous block:

```
5902 → iiyas | muhammad riaz          6021 → iman ulllah | zubaida bibi
6285 → umer farook | umer farooq      6563 → samena begam | tahir mahmood
6892 → mazhar | raisat ali            1085 → abdul majeed | m qasim
606 → (split into multiple parcels)   … full list in import_batches#2.error_summary
```

Split halves are individually addressable, e.g. `RUDA-P14-R00046` /
`RUDA-P14-R00047`. No two owners share a parcel_code.

## 3. NULL-NID continuation handling

2,282 rows (15.3 %) had no NID; all were absorbed by their preceding block per
the fill-down rule (Phase 1: zero orphan runs). They remain queryable via
`survey_master.source_nid IS NULL` and inherit their parcel from contiguity.

## 4. Formula provenance (correction to Phase-1 estimate)

| Field | Formula-flagged rows (full file) |
|---|---|
| `is_formula_area` (`=T*U`) | **9,835** |
| `is_formula_compensation` (`=(V*X)/1e6`) | **14,870** |

Phase 1 sampled only the first ~4,000 rows for formula counting and reported
"≥3,999"; the true whole-file figure is the one above. All values came from
Excel's cached results — zero values were recomputed or invented.

## 5. Near-duplicate tuples

856 duplicate business-tuple kinds (lat+lon+owner-key+structure+L+W) — consistent
with Phase 1's ≈999 estimate under a slightly tighter key. Phase 1 established
these as legitimate (multiple floors/meters per structure type); they are
counted here and intentionally NOT deduplicated.

## 6. Items requiring data-owner attention (no action taken automatically)

1. Ratify the 32-NID split policy (PARCEL_DERIVATION_RULES.md §6).
2. Confirm final Parcel-ID vocabulary when filled WB2 sheets arrive.
3. Chainage unit label mismatch (values look like metres/RD).
4. The 2 corrupted district cells may warrant a corrected source workbook in a
   future batch (a NEW import batch would be created; batch #2 stays immutable).
