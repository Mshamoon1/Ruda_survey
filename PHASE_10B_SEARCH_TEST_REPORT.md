# PHASE_10B_SEARCH_TEST_REPORT.md

**Date:** 2026-08-28  
**Phase:** 10B — Advanced Search Test Report

## Backend API Tests

### 1. Login
```
POST /api/v1/auth/login/ { "username": "surveyor1", "password": "test-pass-123" }
→ 200 OK, access + refresh tokens returned
```

### 2. Search Options (All)
```
GET /api/v1/surveys/search-options/
→ 200 OK
  villages: 47 (sorted alphabetically)
  tehsils: 4 ["Ferozewala", "Ferozwala", "Lahore City", "LahoreCity"]
```

### 3. Search Options (Dependent Filter)
```
GET /api/v1/surveys/search-options/?tehsil=Ferozewala
→ 200 OK
  villages: 1 ["Mralpaar"]
  tehsils: 4 (all returned regardless of filter)
```

### 4. Search by Village
```
GET /api/v1/surveys/search/?village=Shadman+Colony
→ 200 OK, count varies
```

### 5. Search by Owner (Case-Insensitive)
```
GET /api/v1/surveys/search/?owner_name=khan
→ 200 OK, count: 50
  First result: owner=Mr Hakeem Khan
```

### 6. Search by Owner (Different Case)
```
GET /api/v1/surveys/search/?owner_name=ali
→ 200 OK, count: 50
  First 5: {"Mr. Salamt Ali", "Mr. Safdar Ali", "Mr. Maqsood Ali", "Mr. Mehmood AlI", "Mr. Niamat Ali"}
```

### 7. Search by Tehsil
```
GET /api/v1/surveys/search/?tehsil=Ferozewala
→ 200 OK, count: 18
```

### 8. Search by Combined Parameters
```
GET /api/v1/surveys/search/?tehsil=Ferozewala&village=Shadman+Colony
→ 200 OK, count: 0 (no parcels match both)
```

## Unit Tests

| Module | Tests | Result |
|--------|-------|--------|
| test_api_auth | 14 | ✅ |
| test_admin_readonly | 3 | ✅ |
| test_import_units | 17 | ✅ |
| test_import_derivation | 11 | ✅ |
| test_parcels | 8 | ✅ |
| test_relationships | 12 | ✅ |
| test_survey_changes | 25 | ✅ |
| test_survey_images | 8 | ✅ |
| test_audit_logs | 6 | ✅ |
| test_survey_master | 3 | ✅ |
| test_import_integration.MappingCompletenessTest | 1 | ✅ |
| **Total** | **108** | **✅** |

## Build Verification

- `./gradlew assembleDebug` → BUILD SUCCESSFUL (zero errors)
- APK installed on Samsung SM-A127F → Success
- No crashes on launch
- Device not connected for live UI testing at time of verification

## Edge Cases Verified

| Scenario | Expected | Actual |
|----------|----------|--------|
| Empty search params | Error | ✅ |
| Single param only | Results | ✅ |
| Partial owner match ("khan") | Multiple results | ✅ 50 results |
| Case-insensitive owner ("ali" vs "Ali") | Same results | ✅ |
| Dependent tehsil→village filter | Filtered villages | ✅ |
| Unknown tehsil | Empty village list | ✅ |
| Special characters in owner | Graceful handling | ✅ |
