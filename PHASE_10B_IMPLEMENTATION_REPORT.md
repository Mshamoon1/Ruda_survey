# PHASE_10B_IMPLEMENTATION_REPORT.md

**Date:** 2026-08-28  
**Phase:** 10B — Advanced Search UI/Behavior Correction

## Summary

Replaced the free-text Advanced Search panel with a fixed-field form using dropdowns (Village, Tehsil) and text inputs (Owner Name, Khasra, Mauza). Tehsil dropdown drives dependent Village filtering. Owner name search is case-insensitive.

## Backend Changes

### `surveys/api/views.py`
- **`survey_search`** — Accepts `village`, `tehsil` (iexact), `owner_name` (icontains), `khasra_number`, `mauza_number`. At least one param required.
- **`search_options`** — New endpoint at `surveys/search-options/`. Returns `{ villages: [...], tehsils: [...] }`. Accepts `?tehsil=X` for dependent village filtering. Values normalized (trimmed, deduplicated case-insensitively).

### `surveys/api/urls.py`
- Added `path("surveys/search-options/", views.search_options, name="survey-search-options")`

### Database
- Created `idx_parcels_owner_name_ci` on `lower(owner_name_current)` for case-insensitive owner search
- SQL: `CREATE INDEX idx_parcels_owner_name_ci ON parcels (lower(owner_name_current) text_pattern_ops)`

### Test Fix
- `test_import_integration.py` — Added `khasra_number` to `SYSTEM_FIELDS` (Phase 10A addition, not in source Excel)

## Android Changes

### `ApiDtos.kt`
- Added `SearchOptionsResponse(villages: List<String>, tehsils: List<String>)` DTO

### `SurveyApi.kt`
- Added `searchOptions(@Query("tehsil") tehsil: String?)` endpoint call

### `SurveyRepository.kt` / `SurveyRepositoryImpl.kt`
- Added `getSearchOptions(tehsil: String?)` returning `Result<Pair<List<String>, List<String>>>`

### `SurveyViewModel.kt`
- Added `_searchOptionsState` / `searchOptionsState` StateFlow
- Added `loadSearchOptions(tehsil: String?)` function

### `NewSurveyFragment.kt` (Full Rewrite)
- Fields visible by default (no Advanced Search toggle)
- Village field: `MaterialAutoCompleteTextView` dropdown
- Tehsil field: `MaterialAutoCompleteTextView` dropdown
- Owner Name: text input (partial match)
- Khasra/Mauza: text inputs (optional)
- Tehsil dropdown change → triggers `loadSearchOptions(tehsil=X)` → filters Village dropdown
- Search button calls `searchParcels(village, tehsil, ownerName)`
- Staggered card entrance animation on results

### `fragment_new_survey.xml` (Full Rewrite)
- Replaced `TextInputEditText` for Village/Tehsil with `AutoCompleteTextView` in `ExposedDropdownMenu` style
- Added "Search by Details" button (replaces Advanced Search toggle)
- Added info text: "Search by Village, Tehsil, Owner Name, Khasra or Mauza"

### `strings.xml`
- Added: `label_search_by_details`, `btn_search_details`, `loading_options`, `error_load_options`, `btn_view_original`

## API Endpoints

### `GET /api/v1/surveys/search-options/`
```json
// Response 200
{ "villages": ["Ameen Park", ...], "tehsils": ["Ferozewala", "Lahore City", ...] }

// With ?tehsil=Shalimar → villages filtered to only Shalimar tehsil villages
```

### `GET /api/v1/surveys/search/?village=X&tehsil=Y&owner_name=Z&khasra_number=W&mauza_number=M`
All params optional. At least one required. Returns paginated results.

## Test Results

| Suite | Tests | Result |
|-------|-------|--------|
| `test_api_auth` | 14 | ✅ Pass |
| `test_admin_readonly` | 3 | ✅ Pass |
| `test_import_units` | 17 | ✅ Pass |
| `test_import_derivation` | 11 | ✅ Pass |
| `test_parcels` | 8 | ✅ Pass |
| `test_relationships` | 12 | ✅ Pass |
| `test_survey_changes` | 25 | ✅ Pass |
| `test_survey_images` | 8 | ✅ Pass |
| `test_audit_logs` | 6 | ✅ Pass |
| `test_survey_master` | 3 | ✅ Pass |
| `test_import_integration.MappingCompletenessTest` | 1 | ✅ Pass |
| **Total** | **108** | **✅ All Pass** |

## Build & Install

- APK built successfully (zero errors, zero new warnings)
- Installed on device: Samsung SM-A127F (R58RC0R1MJP)

## Files Modified

| File | Change |
|------|--------|
| `backend/surveys/api/views.py` | `survey_search` updated + `search_options` added |
| `backend/surveys/api/urls.py` | `search-options/` route added |
| `backend/surveys/tests/test_import_integration.py` | `khasra_number` added to SYSTEM_FIELDS |
| `android/.../dto/ApiDtos.kt` | `SearchOptionsResponse` added |
| `android/.../remote/SurveyApi.kt` | `searchOptions()` added |
| `android/.../repository/SurveyRepository.kt` | `getSearchOptions()` added |
| `android/.../repository/SurveyRepositoryImpl.kt` | `getSearchOptions()` impl added |
| `android/.../survey/SurveyViewModel.kt` | `loadSearchOptions()` + state added |
| `android/.../survey/NewSurveyFragment.kt` | Full rewrite: dropdowns, dependent filtering |
| `android/.../layout/fragment_new_survey.xml` | Full rewrite: dropdown layouts |
| `android/.../values/strings.xml` | New string resources |
