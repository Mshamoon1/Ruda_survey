# 02 — EXCEL AUDIT & FIELD MAPPING (PHASE 1)

**Scope:** Complete structural analysis of both source workbooks + Parcel ID analysis + field-by-field database mappings.
**Rule honored:** Both files were opened read-only. Nothing was modified.

Workbook identities are pinned by SHA-256 (see doc 01) so the import can verify it reads exactly these files.

---

# PART A — WORKBOOK 1: MASTER / ORIGINAL SURVEY DATA

## A1. Identity

| Property | Value |
|---|---|
| Filename | `02_Annex 4.1_Affected Residential & Commercial Structures.xlsx` |
| Sheets | 1 → `Anex 4.1 Str(Rpr)Dec25Hsn (F)` (visible; no hidden sheets) |
| Declared dimensions | 14,876 rows × 30 columns |
| Physical layout | Row 1 = report title · Rows 2–3 = two-tier merged headers · Row 4 = column numbers 1–28 · **Rows 5–14876 = 14,872 data records** |
| Merged cells | **3,014 ranges** — 2,994 in column D (`Affected Persons #`, vertical merges spanning each parcel's rows), ~20 header merges |
| Hidden rows/cols | **Column B (`NID`) is hidden.** No hidden rows |
| Formulas | Column A `=A5+1` chain (Sr. No counter; only A5 is a literal `1`) · Column V `=U{T}*T{T}` (Area) · Column Y `=(V{T}*X{T})/1000000` (Compensation, Rs. million). Cached values exist and were used for profiling |
| Defined names | None |

## A2. Column Inventory & Profile (data rows 5–14876)

Legend: NN = non-null count of 14,872 · Dist = distinct values

| Col | Excel header (verbatim) | NN | Nulls | Dist | Types observed | Notes / issues |
|---|---|---|---|---|---|---|
| A | `Sr. No` | 14,872 | 0 | 14,872 | int | Formula counter 1…14,872. Unique but a *spreadsheet artifact*, not a business key |
| B | `NID` *(hidden)* | 12,590 | 2,282 | 4,536 | int | De-facto parcel/group identifier. Sparse ints 1–59,571 (55,035 gaps). Max repeat 250×. See §C |
| C | `Chainage (Km)` | 14,872 | 0 | 1,584 | int 11,983 · float 398 · **str 2,491** | Values like `150`, `15250`, `35296` — magnitude suggests metres/RD, not km (label mismatch risk). Text-typed cells present |
| D | `Affected Persons #` | 5,058 | 9,814 | 4,957 | int | Filled only on first row of each merged block → per-parcel count; continuation rows NULL by design |
| E | `Phase#` | 14,872 | 0 | 4 raw | str | Raw: `Ph#1 `, `Ph#2 `, `Ph#2`, `Ph#3 ` (trailing-space variants). Normalized: Ph#1=1,429 · Ph#2=13,027 · Ph#3=416 |
| F | Coordinates → `North (Latitude)` | 14,863 | 9 (+1,538 literal `"-"`) | 2,883 | float 13,325 · str 1,538 | `"-"` placeholder stored as text. Range ≈ 31.46–31.70 N |
| G | `East (Longitude)` | 14,863 | 9 (+1,518 literal `"-"`) | 2,868 | float 13,345 · str 1,518 | Same `"-"` issue. Range ≈ 74.28–74.42 E |
| H | `Project Commponent` *(sic)* | 14,872 | 0 | 3 | str | `River Channelization` 10,962 · `RTW & Roads` 3,907 · `Dam` 3 |
| I | Identification → `Owner's Name` | 14,872 | 0 | 2,846 | str | Contains sentinels: `Not Identified`, `Not Identified `, `Not Identified (Locked)`, `Owner not Identified` |
| J | `Father's Name` | 14,865 | 7 | 1,925 | str | `"-"` placeholders common |
| K | `Caste` | 14,864 | 8 | 97 | str | `"-"` placeholders; casing variants (`Arain`/`Arian`) |
| L | `Village` | 14,872 | 0 | 51 | str | Clean-ish; 51 distinct villages |
| M | `Tehsil` | 14,872 | 0 | 5 raw | str | `Lahore City` vs `LahoreCity` vs `-` → needs normalization map |
| N | `District` | 14,871 | 1 | 4 raw | str | `Lahore`, `Sheikhupura`, `-`, plus **corrupted value `Lahore+K2C2888:T2888` at sheet rows 2899–2900** |
| O | `Ownership Documents (Sale Deed, Registry, Allotment Letter, Intiqal, Aks Shajra, Other)` | 14,870 | 2 | 18 | str | e.g. `E-Stamp paper`, `Registry`, `Sale Deed/ Allotment Letter`, `Intiqal`; `-` placeholders |
| P | `Status of Structure (Residential, Commercial, Agri. Deras, Other )` | 14,872 | 0 | 13 | str | `Residential`, `Commercial`, `Cattle Farm`, `Residential (plot)`, … trailing-space variants |
| Q | `Structure Name` | 14,654 | 218 | 586 | str | Line-item level: `Room`, `Electric meter`, `1st Floor`, `House`, `Boundary Wall`, `Sui gas meter` … |
| R | `Number of Structure` | 14,848 | 24 | 29 | int 14,847 · str 1 | Quantity of that structure line |
| S | `Status (Owner,Tenant)` | 14,872 | 0 | 1 | str | Constant `Owner` in every row |
| T | Size → `Length (ft)` | 14,865 | 7 | 215 | float/int/str(6) | `0` used for non-dimensional items (meters etc.) |
| U | `Width (ft)` | 14,848 | 24 | 199 | float/int/str(51) | Same `0` convention |
| V | `Area (Sq.ft/ R.ft) ` | 14,814 | 58 | 1,241 | float/int | **Formula `=T*U`.** Running feet for walls (length-only) vs sq.ft ambiguity must be preserved via unit note |
| W | `Nature of Construction (Pacca, Semi-Pacca, Katcha) ` | 14,870 | 2 | 14 raw | str 13,046 · int 1,824 | `Pacca`/`pacca`/`Semi-Pacca`/`Katcha`; `0` and `-` as placeholders |
| X | `Unit Rate (Rs.)` | 14,671 | 201 | 7 | int | Rate schedule: 1400, 2200, 3255, 15000, 30000, 70000 (+1 more) |
| Y | `Structure Compensation (Rs. million)` | 14,871 | 1 | 1,562 | float/int | **Formula `=V*X/1000000`** |
| Z | `Extent of Impact` | 14,872 | 0 | 1 | str | Constant `Major Impact` |
| AA | `Location (Right, Left, Center)` | 14,869 | 3 | 4 raw | str | `Right`/`Right `/`right`/`Left` → normalize case/space |
| AB | `RoW (Y/N)` | 14,872 | 0 | 2 | str | `Yes` or `-` (never `No`) → `-` semantically means "not stated", not "No" |
| AC | `Off-set from Propsed Revised CL of River (m)` *(sic)* | 14,865 | 7 (+512 literal `-`) | 521 | int 14,353 · str 512 | `-` placeholders |
| AD | *(unnamed)* | 47 | 14,825 | 3 | str | Stray tenancy notes: `On Rent`, `On rent`, `Rentee` — no header; preserve as remark |

## A3. Granularity Model (critical for schema design)

One Excel row = **one structure line-item** (e.g., "Electric meter"), NOT one owner/parcel.
Rows belonging to the same affected person/parcel repeat owner/village/location values and are:
- grouped visually by merged cells in column D,
- keyed logically by `NID` (when present),
- contiguous (a parcel's rows sit together; a new parcel starts when `NID`/owner changes).

→ The DB therefore needs a **parcel-level entity derived during import** plus immutable line-items. See doc 03.

## A4. Sample Records (verbatim, cached values)

```
row 5   : Sr=1  NID=1     Ch=150    Phase="Ph#1 " Lat=31.70384951 Lon=74.4159358 Comp=Dam
          Owner="Rana Bashir" Father="Muhammad Buksh" Caste=Rajput Village=Arya Nagar
          Tehsil=Ferozwala District=Sheikhupura Doc=Intiqal StructType=Cattle Farm
          Name="Hawaili i.e. rooms, verenda" Qty=4 Tenure=Owner L=27.1 W=31.7 Area=859.07
          Nature=Semi-Pacca Rate=2200 Comp_M=1.889954 Impact=Major Location="Right " RoW=Yes Offset=439
row 6   : Sr=2  NID=2     same parcel context, Name=Bathroom Qty=2 Area=12.25 Comp_M=0.02695
row 7005: Sr=7001 NID=1819 Ch=14776 Phase="Ph#1 " Lat=31.61806 Lon=74.30333 Comp=River Channelization
          Owner="Owner not Identified" Village=Ravi Clifton Colony Name="Electric meter"
          L=0 W=0 Area=0 Nature="-" Rate=15000 Comp_M=0.015
row 14875:Sr=14871 NID=7724 Ch=35296 Phase="Ph#3 " Lat=31.46891 Lon=74.1815 Comp=RTW & Roads
          Owner="Mr. Taj Din" Village=Park View City Name="1st Floor" L=45 W=25 Area=1125
          Nature=Pacca Rate=3255 Comp_M=3.661875 Location=Left
```

## A5. Duplicate Analysis (master)

- Exact duplicate full rows: none beyond formula-generated counters.
- Near-duplicate business tuples (lat+lon+owner+structure+L+W): **≈999 repeated tuples** — legitimate because one structure type can appear multiple times per parcel with different floors/meters; must NOT be deduped automatically.
- Duplicate columns: **none**.
- Header near-duplicates caused by merge spans only.

## A6. Field Classification (master)

| Class | Columns |
|---|---|
| Numeric | C (mixed!), D, R, T, U, V, X, Y, AC |
| Coordinate (lat/lon pair) | F, G |
| Text/free | H, I, J, K, L, M, N, O, P, Q, AA, AB |
| Categorical/enumerable | E, H, K, M, N, O, P, W, Z, AA, AB |
| Computed (formula) | A, V, Y |
| Date/time | **none present anywhere in either workbook** |
| Identifier candidates | B (NID), A (Sr. No) |

---

# PART B — WORKBOOK 2: NEW SURVEY TEMPLATE (REVISION DATA TARGET)

## B1. Identity

| Property | Value |
|---|---|
| Filename | `RTW PKG-14  Excl Permarma 123 11-8-26.xlsx` |
| Sheets | 1 → `Sheet1` (visible; no hidden sheets/rows/cols) |
| Declared dimensions | 269 rows × 21 columns |
| Layout | Rows 1–7 = merged multi-tier header block (21 merged ranges) · Rows 8–268 = data region |
| Data status | **TEMPLATE IS EMPTY**: only `Sr. No` pre-filled 1–254. Zero values in all columns B–U |
| Separator rows | 7 intentionally blank Sr.No rows: 38, 74, 110, 146, 182, 218, 254 → page-break pattern (35 entries/page); template capacity = 254 survey entries |
| Formulas | None |

## B2. Combined Header (rows 1–7 flattened)

| Col | Flattened header | Meaning |
|---|---|---|
| A | `Sr. No` | Entry number (pre-filled 1–254) |
| B | `Survey Sheet Parcel ID` | **The application's Parcel ID business key** |
| C | `RD` | Reduced Distance / chainage reference |
| D/E | Coordinates `N` / `E` | Latitude / Longitude re-capture point |
| F | `Pkg #` | Package number (context constant, cf. filename PKG-14) |
| G | `Village` | Village name |
| H | `Owner's Name` | Identification block |
| I | `Father's Name` | Identification block |
| J | `CNIC No` | National identity card number |
| K | `Khasra Number` | Land revenue record plot number |
| L | `Contact Number` | Phone |
| M | `LAND OWNER DOC` | Ownership document presented |
| N | `Electricity Connection Name` | Utility verification field |
| O | `Land Area` | Land area |
| P | `Status of Structure (Residential, Commercial, Agri. Deras, Other )` | Structure use |
| Q | `Structure Name` | Structure line description |
| R/S/T | COVERED AREA: `Length (ft)` / `Width (ft)` / `Area (ft2)` | Covered area measurements |
| U | `Nature of Construction (pacca,semi pacca ,katcha)` | Construction class |

## B3. Role of this Workbook

It is the **target form definition**: the fields the Android app will collect during a new field survey, one entry per parcel visit. Because every cell is empty:

1. There is **no existing Parcel ID ↔ master linkage ground truth** to validate against.
2. Its schema proves the app must capture *new* attributes absent from the master: CNIC, Khasra Number, Contact Number, Electricity Connection Name, Land Area, RD, Pkg #.
3. It confirms Parcel ID lives at **parcel level**, while `Structure Name`/covered-area fields remain **line-item level** within an entry.

---

# PART C — PARCEL ID ANALYSIS (SECTION 3 OF THE BRIEF)

## C1. Where Parcel ID lives

| Dataset | Verdict |
|---|---|
| Workbook 2 | Explicit column **B `Survey Sheet Parcel ID`** — the canonical Parcel ID field users will type into the app. Currently empty (template). Expected format: free-form alphanumeric until first real data arrives |
| Workbook 1 (master) | **No column is literally named "Parcel ID".** The de-facto parcel/business identifier is **hidden column B `NID`** (Notice/owner-group ID; semantics: groups the structure lines of one affected person/parcel) |

## C2. NID quality assessment (why cleansing is mandatory before using it as Parcel ID)

Measured over 14,872 master rows:

| Metric | Value | Consequence |
|---|---|---|
| Non-null / null | 12,590 / 2,282 | 15.3 % of rows have no NID |
| Orphan NULL-NID blocks (no NID above) | **0** | Every NULL-NID run follows an NID'd row → NULLs are *continuation* rows inheriting the parcel above (fill-down safe) |
| Distinct NIDs | 4,536 | ~2.8 structure-lines per parcel on average |
| Numeric / range | 100 % int · min 1 · max 59,571 | Sparse numbering; 55,035 integers missing inside range → IDs come from a larger notice register |
| NIDs repeating across >1 distinct owner | 54 | Mostly spelling variants of the same person (`mr.arshad ali` vs `mr. arshad ali`) but some genuine multi-person groups (`mr. iman ulllah` + `ms. zubaida bibi`) |
| Worst offender | **NID 606 → 250 rows spanning multiple unrelated owners** | Clearly a fill-down/catch-all error; cannot be trusted blindly |
| Uniqueness per row | Never unique (by design) | Parcel key ≠ row key |

## C3. Recommended Parcel ID strategy (decision gate for Phase 3)

1. Import preserves the raw value as `source_nid` (nullable int) on every master line — provenance never lost.
2. During import, derive a **canonical `parcel_id`** per contiguous block: start a new parcel when `NID` changes OR owner-name/village changes materially; fill continuation NULL-NIDs down from their block header.
3. Blocks whose NID is provably polluted (case: NID 606) are split per owner-group and assigned fresh surrogate parcel IDs; the original NID stays on every line for traceability.
4. Canonical format recommendation: stable generated code, e.g. `RUDA-P14-00001` (package-prefixed, zero-padded), exposed to users; internal integer PK remains surrogate.
5. Until real Workbook-2 data exists, the app accepts manual entry matched against `parcel_id` **and** `source_nid` (both indexed).

## C4. Constraint recommendations for Parcel ID

- `survey_master.parcel_id`: `NOT NULL`, indexed (`btree`); part of unique `(parcel_id, sr_no)`.
- Dedicated `parcels.parcel_code`: `UNIQUE`, `NOT NULL`, `CITEXT`.
- `source_nid`: plain index, nullable (2,282 legit NULLs).
- App-side validation: strip whitespace, uppercase, regex `^[A-Z0-9\-]{3,32}$` (revisit when real IDs arrive).

## C5. Examples of NID values (as found)

`1, 2, 606 (×250 rows), 1058 (×7), 1217 (×6), 1286, 1287, 1288, 1819, 4740, 4745, 6033, 6439, 7482, 7724`

---

# PART D — MASTER FIELD MAPPING (SECTION 4: survey_master, IMMUTABLE)

Rules applied:
- **Every original column survives** — nothing dropped; even stray col AD is kept.
- Truly-empty cells → SQL `NULL`. Literal sentinel strings that exist in the source (`"-"`, `"Not Identified"`, `"0"`) are stored **verbatim** (immutability) and normalized only in read-model/display layers.
- `raw_*` JSONB snapshot keeps the untouched cell of every column so future corrections never need the original file.
- snake_case names; units embedded in names where the Excel header carries them.

| # | Excel col → header | DB field | Type | Nullable | Required | Notes / transformation |
|---|---|---|---|---|---|---|
| 1 | A `Sr. No` | `sr_no` | INTEGER | NO | YES | Row counter; unique within import batch |
| 2 | B `NID` | `source_nid` | INTEGER | YES | no | Hidden col; raw parcel group ref; keep as-is |
| 3 | C `Chainage (Km)` | `chainage_m` | NUMERIC(10,2) | YES | no | Label says Km but magnitudes indicate m/RD; store numeric parsed from mixed text; rename honest unit `_m`, keep raw in snapshot |
| 4 | D `Affected Persons #` | `affected_persons_count` | INTEGER | YES | no | NULL on continuation rows (merged-cell artifact — do NOT backfill in master) |
| 5 | E `Phase#` | `phase_code` | VARCHAR(16) | YES | no | Trim spaces; `Ph#1 ` → `PH1` style normalization in view layer; raw kept |
| 6 | F Latitude | `latitude` | NUMERIC(10,7) | YES | no | NULL if blank **or** literal `-`; else parse float |
| 7 | G Longitude | `longitude` | NUMERIC(10,7) | YES | no | Same rule as latitude |
| 8 | H `Project Commponent` | `project_component` | VARCHAR(64) | NO | YES | Typo fixed in name; value verbatim (`Dam`/`River Channelization`/`RTW & Roads`) |
| 9 | I `Owner's Name` | `owner_name` | VARCHAR(255) | NO | YES | Verbatim incl. `Not Identified` sentinels |
| 10 | J `Father's Name` | `father_name` | VARCHAR(255) | YES | no | `-` kept verbatim (source value) |
| 11 | K `Caste` | `caste` | VARCHAR(96) | YES | no | — |
| 12 | L `Village` | `village` | VARCHAR(128) | NO | YES | — |
| 13 | M `Tehsil` | `tehsil` | VARCHAR(96) | YES | no | `LahoreCity` variants normalized only at display |
| 14 | N `District` | `district` | VARCHAR(96) | YES | no | Corrupted `Lahore+K2C2888:T2888` (rows 2899–2900) imported verbatim + import-warning logged |
| 15 | O `Ownership Documents…` | `ownership_documents` | VARCHAR(255) | YES | no | Multi-value string; tokenize later in view layer |
| 16 | P `Status of Structure…` | `structure_status` | VARCHAR(64) | NO | YES | Residential/Commercial/Cattle Farm/(plot) variants verbatim |
| 17 | Q `Structure Name` | `structure_name` | VARCHAR(255) | YES | no | 218 NULLs respected |
| 18 | R `Number of Structure` | `structure_count` | INTEGER | YES | no | One text cell → cast-or-null with warning |
| 19 | S `Status (Owner,Tenant)` | `tenure_status` | VARCHAR(16) | YES | no | Constant `Owner` today; nullable because future rows may differ |
| 20 | T Length ft | `length_ft` | NUMERIC(10,2) | YES | no | — |
| 21 | U Width ft | `width_ft` | NUMERIC(10,2) | YES | no | — |
| 22 | V Area Sq.ft/R.ft | `area_value` | NUMERIC(12,2) | YES | no | Cached formula result; unit ambiguity (sq.ft vs R.ft) documented, resolved in view layer |
| 23 | W Nature of Construction | `construction_nature` | VARCHAR(32) | YES | no | `Pacca`/`pacca`/`0`/`-` verbatim; enum mapping at display |
| 24 | X Unit Rate Rs | `unit_rate_rs` | INTEGER | YES | no | — |
| 25 | Y Compensation Rs.million | `compensation_million` | NUMERIC(14,6) | YES | no | Cached formula `(V*X)/1e6` |
| 26 | Z Extent of Impact | `impact_extent` | VARCHAR(32) | NO | YES | Constant `Major Impact` today |
| 27 | AA Location | `river_location` | VARCHAR(16) | YES | no | Normalize `right`/`Right ` → `Right` in view layer |
| 28 | AB `RoW (Y/N)` | `in_row_yn` | VARCHAR(4) | YES | no | `Yes`/`-` verbatim; `-` ≠ `No` |
| 29 | AC Off-set m | `cl_offset_m` | INTEGER | YES | no | `-` → parse-fail → keep verbatim? No: numeric col gets NULL; verbatim `-` lives in `raw_data` |
| 30 | AD (unnamed stray notes) | `extra_note` | VARCHAR(64) | YES | no | 47 `On Rent/Rentee` cells preserved |

**Import/system columns added to survey_master:** `id` BIGSERIAL PK · `import_batch_id` FK · `source_file_hash` CHAR(64) · `sheet_name` · `source_row_number` INT · `parcel_id` FK (derived, see §C) · `is_formula_area BOOLEAN`, `is_formula_compensation BOOLEAN` (provenance flags) · `raw_data JSONB` (all 30 cells verbatim) · `imported_at TIMESTAMPTZ DEFAULT now()` · `imported_by`.

Uniqueness: `UNIQUE(import_batch_id, source_row_number)`; index on `(parcel_id)`, `(source_nid)`, `(village)`.

---

# PART E — REVISION FIELD MAPPING (SECTION 5: survey payload captured by the app)

Target schema = Workbook 2. These fields populate a **new revision** (append-only). UI shows a field only when its value is non-NULL (Section 7 requirement). Transformations: trim → typed parse → sentinel handling identical to Part D.

| # | Excel col → header (WB2) | DB field (snake_case) | Type | Nullable | UI visible when NULL? | Notes |
|---|---|---|---|---|---|---|
| 1 | A `Sr. No` | *(system: revision sequence)* | — | — | — | Superseded by server-assigned `revision_no`; not user-visible data |
| 2 | B `Survey Sheet Parcel ID` | `parcel_id` | VARCHAR(32) | NO | n/a (search key) | Manual entry; lookup key against parcels |
| 3 | C `RD` | `rd_value` | NUMERIC(10,2) | YES | **hidden** | Chainage/RD at visit |
| 4 | D Coord N | `latitude` | NUMERIC(10,7) | YES | hidden | GPS capture preferred |
| 5 | E Coord E | `longitude` | NUMERIC(10,7) | YES | hidden | — |
| 6 | F `Pkg #` | `package_no` | VARCHAR(32) | YES | hidden | Default `PKG-14` from context |
| 7 | G `Village` | `village` | VARCHAR(128) | YES | hidden | Pre-filled from master, editable |
| 8 | H `Owner's Name` | `owner_name` | VARCHAR(255) | YES | hidden | Pre-filled from master, editable |
| 9 | I `Father's Name` | `father_name` | VARCHAR(255) | YES | hidden | — |
| 10 | J `CNIC No` | `cnic_no` | VARCHAR(15) | YES | hidden | Format `XXXXX-XXXXXXX-X` validated softly (13 digits tolerated) |
| 11 | K `Khasra Number` | `khasra_number` | VARCHAR(64) | YES | hidden | New attribute (absent in master) |
| 12 | L `Contact Number` | `contact_number` | VARCHAR(20) | YES | hidden | Digits/spaces/+ tolerated |
| 13 | M `LAND OWNER DOC` | `land_owner_doc` | VARCHAR(128) | YES | hidden | Enum-ish: Sale Deed/Registry/Allotment Letter/Intiqal/Aks Shajra/E-Stamp/Other |
| 14 | N `Electricity Connection Name` | `electricity_connection_name` | VARCHAR(128) | YES | hidden | New attribute |
| 15 | O `Land Area` | `land_area` | NUMERIC(12,2) | YES | hidden | Unit unspecified in template → keep unit flag `land_area_unit` default `kanal` TBD (open question) |
| 16 | P Status of Structure | `structure_status` | VARCHAR(64) | YES | hidden | Enum: Residential/Commercial/Agri. Deras/Other |
| 17 | Q `Structure Name` | `structure_name` | VARCHAR(255) | YES | hidden | Line-item label |
| 18 | R Length ft | `length_ft` | NUMERIC(10,2) | YES | hidden | — |
| 19 | S Width ft | `width_ft` | NUMERIC(10,2) | YES | hidden | — |
| 20 | T Area ft² | `area_sqft` | NUMERIC(12,2) | YES | hidden | Auto-computed L×W client-side; editable; recomputed server-side if both dims present |
| 21 | U Nature of Construction | `construction_nature` | VARCHAR(32) | YES | hidden | Enum: Pacca/Semi-Pacca/Katcha |

Plus revision-record system fields: `revision_no` (1,2,3,…), `parent_revision_id`, `changes JSONB` (field-level diff vs previous state), `full_payload JSONB`, `status` (draft/submitted/synced), `created_by`, `device_info`, `client_uuid` (offline idempotency), timestamps. Full table design in doc 03.

**Naming transformations log (examples):** `Structure Compensation (Rs. million)` → `compensation_million`; `Off-set from Propsed Revised CL of River (m)` → `cl_offset_m`; `Project Commponent` → `project_component`; `Status of Structure (…)` → `structure_status`; `COVERED AREA Length (ft)` → `length_ft`. Every renamed field above documents its origin column — no original column lost.

---

# PART F — NULL POLICY (SECTION 7)

1. Empty cell ⇒ SQL `NULL` (never `'N/A'`, `'-'`, `'Unknown'`, `'null'`, `0`).
2. Sentinels that literally exist in the source (`-`, `0` in nature-of-construction, `Not Identified`) are **real data** → stored verbatim in the immutable master; treated as "no information" only in display logic.
3. Nullable fields (master): everything except `sr_no`, `project_component`, `owner_name`, `village`, `structure_status`, `impact_extent` (+ system columns).
4. Android rule: **if a field is NULL/empty → do not render that field's row/card at all** (no placeholders like "—").
5. Numeric parsing failures (e.g., text in `Chainage`) → NULL + import warning row in the import manifest; the raw string stays in `raw_data`.

# PART G — OPEN QUESTIONS FOR THE DATA OWNER (blocking none of Phases 2–4)

1. Confirm intended meaning/format of `Survey Sheet Parcel ID` once filled samples exist.
2. `Chainage (Km)` unit mismatch (values look like metres/RD).
3. `Land Area` unit (kanal/marla/sq.ft?) in WB2.
4. Policy for NID 606 (250 rows, multiple owners) — split or keep?
5. Is `Affected Persons #` a head-count of persons per parcel (assumed)?
