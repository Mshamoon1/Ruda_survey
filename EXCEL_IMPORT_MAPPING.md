# EXCEL IMPORT MAPPING — MASTER WORKBOOK (PHASE 3)

**Source:** `02_Annex 4.1_Affected Residential & Commercial Structures.xlsx`
SHA-256 `b04dc2b7…949c252` (pinned in `01_PROJECT_AUDIT.md`)
**Reader module:** `backend/surveys/services/excel_reader.py`

> Note: the Phase 3 brief's "SOURCE FILES" section listed the two filenames in
> swapped order relative to their documented characteristics. This project
> follows the **characteristics** (and Phases 1–2 documentation): the file above
> is the MASTER dataset (14,872 rows × 30 columns, hidden NID, formulas);
> `RTW PKG-14 … .xlsx` is the blank field-survey template and is NOT imported.

---

## 1. Physical layout handling

| Zone | Rows | Treatment |
|---|---|---|
| Title | row 1 | must contain `Annex 4.1` else `UNEXPECTED_TITLE` abort |
| Two-tier merged headers | rows 2–3 | combined per column: non-empty cells joined with `" | "` then lower-cased/collapsed (`resolve_headers_from_head`) |
| Column numbering | row 4 | informational only |
| Data | rows 5+ | streamed in a single lock-step pass over TWO read-only workbook handles |

Formula strategy: workbook opened twice — `data_only=True` supplies **cached
results used for DB values**; `data_only=False` supplies verbatim cell contents
(formula strings like `=U5*T5`) stored in `raw_data` JSONB. A formula cell with
no cached result ⇒ `FORMULA_NO_CACHE` ERROR ⇒ row rejected (never invent values).

## 2. Header validation signatures

Each column must contain all listed fragments (case/space-insensitive) or the
import aborts with `HEADER_MISMATCH`; column AD must be UNNAMED (`UNEXPECTED_COLUMN`
otherwise); exactly one sheet whose name contains `Anex 4.1` is accepted.

## 3. Column → database field map (authoritative)

| Col | Combined header signature required | DB field (survey_master) | Type | Coercion |
|---|---|---|---|---|
| A | sr · no | `sr_no` | int | int-or-reject |
| B | nid | `source_nid` | bigint | numeric→int / blank→NULL |
| C | chainage | `chainage_m` | numeric(10,2) | numeric ladder |
| D | affected persons | `affected_persons_count` | integer | numeric ladder |
| E | phase | `phase_code` | varchar(16) | text |
| F | north + lat | `latitude` | numeric(10,7) | numeric ladder |
| G | east + long | `longitude` | numeric(10,7) | numeric ladder |
| H | project | `project_component` ✱required | varchar(64) | text |
| I | owner | `owner_name` ✱required | varchar(255) | text |
| J | father | `father_name` | varchar(255) | text |
| K | caste | `caste` | varchar(96) | text |
| L | village | `village` ✱required | varchar(128) | text |
| M | tehsil | `tehsil` | varchar(96) | text |
| N | district | `district` | varchar(96) | text + corruption scan |
| O | ownership documents | `ownership_documents` | varchar(255) | text |
| P | status of structure | `structure_status` ✱required | varchar(64) | text |
| Q | structure name | `structure_name` | varchar(255) | text |
| R | number of structure | `structure_count` | integer | numeric ladder |
| S | status (owner | `tenure_status` | varchar(16) | text |
| T | length | `length_ft` | numeric(10,2) | numeric ladder |
| U | width | `width_ft` | numeric(10,2) | numeric ladder |
| V | area | `area_value` | numeric(12,2) | cached formula result; flag `is_formula_area` |
| W | nature of construction | `construction_nature` | varchar(32) | text |
| X | unit rate | `unit_rate_rs` | integer | numeric ladder |
| Y | compensation | `compensation_million` | numeric(14,6) | cached formula `(V*X)/1e6`; flag `is_formula_compensation` |
| Z | extent of impact | `impact_extent` ✱required | varchar(32) | text |
| AA | location | `river_location` | varchar(16) | text |
| AB | row (y/n | `in_row_yn` | varchar(4) | text |
| AC | off-set | `cl_offset_m` | integer | numeric ladder |
| AD | *(must be unnamed)* | `extra_note` | varchar(64) | text |

Completeness rule: `ALL_FIELDS` (30) ≡ SurveyMaster concrete fields minus the 8
system/provenance fields — enforced by automated test
(`MappingCompletenessTest.test_excel_mapping_covers_model_exactly`) and by
`test_b6_numeric_spec_matches_coercion_registry`.

## 4. Value coercion ladder (numeric fields)

1. empty/whitespace → `NULL`
2. already numeric → converted (Decimal quantised to 6dp, ints exact)
3. `"-"` → `NULL` + INFO `DASH_PLACEHOLDER_NULL` (documented placeholder)
4. other unparseable text → `NULL` + **WARNING** `INVALID_NUMERIC_STORED_AS_NULL`
   (verbatim string preserved in `raw_data`) — per doc 02 §F.5
5. fractional value into an int column → truncated + WARNING
   `FRACTIONAL_INT_TRUNCATED`

Text fields are stored verbatim after whitespace-collapse only: `-`, `0`,
`Not Identified`, `Lahore+K2C2888:T2888` etc. are REAL DATA and are never
"cleaned" into NULLs.

## 5. Row-level error codes

| Code | Severity | Effect |
|---|---|---|
| REQUIRED_BLANK | ERROR | row rejected |
| SR_NO_UNREADABLE | ERROR | row rejected |
| FORMULA_NO_CACHE (+ROW_REJECTED) | ERROR | row rejected |
| SUSPECTED_CORRUPTED_CELL | WARNING | imported verbatim, flagged |
| DASH_PLACEHOLDER_NULL | INFO | imported with NULL |
| INVALID_NUMERIC_STORED_AS_NULL / FRACTIONAL_INT_TRUNCATED | WARNING | imported with NULL |

Workbook-level abort codes: `FILE_NOT_FOUND`, `FILE_UNREADABLE`,
`UNEXPECTED_SHEET_STRUCTURE`, `UNEXPECTED_TITLE`, `HEADER_MISMATCH`,
`UNEXPECTED_COLUMN`.
