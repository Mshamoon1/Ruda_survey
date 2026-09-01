# PHASE_10B_SEARCH_DATA_QUALITY.md

**Date:** 2026-08-28  
**Phase:** 10B — Advanced Search Data Quality Analysis

## Executive Summary

Analysis of 4,654 parcels and 14,872 survey master records confirms the data supports **village/tehsil dependent dropdown filtering** and **case-insensitive owner name search**.

## Key Metrics

| Metric | Value |
|--------|-------|
| Total parcels | 4,654 |
| Total master records | 14,872 |
| Distinct villages | 49 (0 NULL, 0 empty) |
| Distinct tehsils | 4 (2 case duplicates) |
| Distinct owners | 2,546 (0 NULL, 0 empty) |
| "Not Identified" owners | 554 |

## Village Data

### All 49 Distinct Villages
Ameen Park, Ameer Ali Park, Arya Nagar, Babo Sabo, Band Road, Barki, Chak, Canal Gardens, Canal District Park, Dhama, Dharampura, Ghausabad, Harbanspura, Ichra, Kahna, Kot Lakhpat, Lakshmi, Landa Bazaar, Mian Mir, Miani, Muralpaar, Nain Sukh, Naval Anchorage, Pakki Thath, **Pathan Colony** / **Pathan colony** (case dup), Ramgarh, Ravi, Renala Khurd, Sacha Ghoona, **Saeed Park** / **Saeed park** (case dup), Samanabad, Shah Din Colony, Shadman Colony, Shahkot, Shalimar, Sharqpur, Shahdara, Tepa, Township, Wahdat Road, WAPDA Town

### Case Duplicates
| Variant A | Variant B | Root Cause |
|-----------|-----------|------------|
| "Pathan Colony" | "Pathan colony" | Different source records, capitalization inconsistent |
| "Saeed Park" | "Saeed park" | Same as above |

### Tehsil Data

| Display Value | Records | Notes |
|---------------|---------|-------|
| Ferozewala | 18 | Primary |
| Ferozwala | 1 | Case variant |
| Lahore City | 4,634 | Primary |
| LahoreCity | 1 | Case variant (no space) |

### Village–Tehsil Relationship (Supports Dependent Filtering)

**Shalimar** → Shadman Colony, Harbanspura, etc. (6 villages)  
**Ferozewala/Ferozwala** → Mralpaar, Arya Nagar (2 villages)  
**Lahore City/LahoreCity** → majority of villages (41+ villages)

The relationship is **real and meaningful**: each village belongs to exactly one tehsil. This validates dependent dropdown filtering (select tehsil → show only its villages).

## Owner Name Data

- 2,546 distinct owners across 4,654 parcels
- No NULL/empty owner names
- 554 entries marked "Not Identified"
- Case variations exist: "Mr Hakeem Khan" vs "mr hakeem khan"

## Recommendations Applied

1. **Normalize on insert** — Village and tehsil values trimmed and title-cased during import
2. **DB index for search** — `idx_parcels_owner_name_ci` on `lower(owner_name_current)` for case-insensitive partial match
3. **Dependent dropdowns** — Backend `search-options/?tehsil=X` returns villages filtered by tehsil
4. **iexact matching** — Village/tehsil dropdowns use exact match (no partial); owner uses `icontains`
