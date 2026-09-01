# PHASE 10A — KHASRA/MAUZA DATA QUALITY REPORT

**Date:** 2026-08-27
**Database:** PostgreSQL 18.6, Ruda_Survey, role ruda_app

---

## 1. Source Data Analysis

### 1.1 Excel Source Columns
The source Excel file contains 30 columns (A through AD). **Neither khasra_number nor mauza_number exist in the source data.** These are field-survey-only fields that will be populated through the revision workflow.

| Column | Field | Notes |
|---|---|---|
| A | Sr. No | Sequential counter |
| B | NID | Hidden column, used for parcel derivation |
| C | Chainage (m) | Decimal |
| D | Affected persons count | Integer |
| E | Phase code | String |
| F | Latitude | Decimal(10,7) |
| G | Longitude | Decimal(10,7) |
| H | Project component | String |
| I | Owner name | String |
| J | Father name | String |
| K | Caste | String |
| L | Village | String — 51 unique values |
| M | Tehsil | String |
| N | District | String |
| O | Ownership documents | String |
| P | Structure status | String |
| Q | Structure name | String |
| R | Structure count | Integer |
| S | Tenure status | String |
| T | Length (ft) | Decimal |
| U | Width (ft) | Decimal |
| V | Area value | Decimal (formula) |
| W | Construction nature | String |
| X | Unit rate (Rs) | Integer |
| Y | Compensation (million) | Decimal (formula) |
| Z | Impact extent | String |
| AA | River location | String |
| AB | In row Y/N | String |
| AC | CL offset (m) | Integer |
| AD | Extra note | String |

### 1.2 Khasra/Mauza Existence Check
```
Total master rows: 14,872
Rows with khasra-like text in raw_data: 0
Rows with mauza-like text in raw_data: 0
```

**Result:** Neither khasra_number nor mauza_number exist in the source data. They are **field-survey-only** fields.

---

## 2. Current Data Statistics

### 2.1 Parcels
- **Total parcels:** 4,654
- **Unique villages:** 51
- **khasra_number (all NULL):** 4,654 (100%)
- **mauza_number (all NULL):** 4,654 (100%)

### 2.2 Coordinates
- **latitude set:** 13,325 / 14,872 (89.6%)
- **longitude set:** 13,345 / 14,872 (89.7%)

### 2.3 Top Villages (by master row count)
| Village | Master Rows | Parcels |
|---|---|---|
| Mustafabad | 3,248 | 558 |
| Farkhabad | 2,879 | 1,135 |
| Kamran Park | 1,752 | 42 |
| Saeed Park | 852 | 502 |
| Ravi Clifton Colony | 789 | 184 |

### 2.4 Revisions
- **Total revisions:** 8 (all synced)
- **Images:** 5 (FRONT/SECOND types)

---

## 3. Uniqueness Analysis

### 3.1 Khasra + Mauza Combination
Since khasra_number and mauza_number are both NULL for all existing records, the combination uniqueness check is moot at this time.

**Expected behavior when field surveys begin:**
- If khasra + mauza combination is unique per parcel: search returns exactly 1 result
- If duplicates exist: search returns multiple results, surveyor selects correct one
- No UNIQUE constraint added at database level (data not yet populated)

### 3.2 Recommendation
**Do NOT add a UNIQUE constraint on (khasra_number, mauza_number) yet.** Wait until field survey data is populated and uniqueness is verified in production.

---

## 4. Normalization Rules

| Rule | Applied To | Behavior |
|---|---|---|
| Case-insensitive | khasra_number, mauza_number | `ILIKE` / `__iexact` |
| Whitespace trim | Search input | `.trim()` before query |
| Original value preserved | Stored in parcels table | Never overwritten by search normalization |

---

## 5. Search Behavior

### 5.1 Parameters
- `khasra_number` — optional, case-insensitive, whitespace-trimmed
- `mauza_number` — optional, case-insensitive, whitespace-trimmed
- At least one parameter required

### 5.2 Response
```json
{
  "results": [
    {
      "parcel_code": "RUDA-P14-R00005",
      "khasra_number": "123",
      "mauza_number": "45",
      "owner_name": "Example Person",
      "village": "Example Village",
      "tehsil": "Ferozwala",
      "district": "Sheikhupura",
      "source_nid": 606
    }
  ],
  "count": 1
}
```

### 5.3 Limits
- Maximum 50 results returned
- Authentication required (JWT)

---

## 6. Data Quality Issues

| Issue | Status | Notes |
|---|---|---|
| Khasra numbers missing | Expected | Field-survey-only data |
| Mauza numbers missing | Expected | Field-survey-only data |
| Village name variants | Noted | "Saeed Park" vs "Saeed park" — case variants exist |
| Duplicate village names | Noted | "Pathan Colony" vs "Pathan colony" — case variants |

**No data quality issues that block implementation.** The search will work correctly once field survey data is populated.
