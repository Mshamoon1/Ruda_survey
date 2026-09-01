# PHASE 10 — REAL DEVICE MOBILE QA REPORT

**Date:** 2026-08-27
**Device:** Samsung SM-A127F, Android 13 (API 33), arm64-v8a, 720×1600, 300dpi
**Build:** Gradle 8.14.1, AGP 8.7.3, Kotlin 1.9.24, compileSdk 34, minSdk 26
**APK:** `app-debug.apk` (8.6 MB)
**Server:** Django 5.2.17 on `192.168.100.41:8000`

---

## Test Results Summary

| TC | Description | Status | Notes |
|----|-------------|--------|-------|
| TC-01 | Installation | **PASS** | APK installs successfully |
| TC-02 | Login Valid | **PASS** | surveyor1/test-pass-123 → Dashboard |
| TC-03 | Login Invalid | **PASS** | "Invalid username or password" shown |
| TC-04 | Login Empty | **PASS** | Client validation blocks submission |
| TC-05 | Dashboard | **PASS** | Shows "New Survey" + "Logout" |
| TC-06 | Search Valid | **PASS** | RUDA-P14-R00005 → Found with correct data |
| TC-07 | Search Invalid | **PASS** | PARCEL_NOT_FOUND error |
| TC-09 | Original Data | **PASS** | Form loads with correct values |
| TC-10 | Current Data | **PASS** | Shows effective state from revision 8 |
| TC-11 | Edit Single Field | **PASS** | Owner name editable |
| TC-12 | Edit Multiple | **BLOCKED** | Cannot reach submit (P1 bug) |
| TC-13 | NULL Fields | **PASS** | NULL fields show empty |
| TC-14 | Change Review | **BLOCKED** | Submit button has no click handler |
| TC-15 | FRONT Camera | **BLOCKED** | Take Photo button has no click handler |
| TC-16 | SECOND Camera | **BLOCKED** | Same as TC-15 |
| TC-17 | Camera Permission | **BLOCKED** | Same as TC-15 |
| TC-18 | Online Submission | **BLOCKED** | Submit button has no click handler |
| TC-19 | DB Verification | **BLOCKED** | Cannot submit revision |
| TC-20 | Draft Preservation | **BLOCKED** | Cannot submit |
| TC-21-24 | Offline Sync | **BLOCKED** | Cannot submit |
| TC-25-28 | Edge Cases | **NOT TESTED** | |
| TC-29 | Logout | **PASS** | Tokens cleared, returns to login |
| TC-30 | PDF Download | **BLOCKED** | Cannot reach sheet fragment |
| TC-31 | Excel Export | **BLOCKED** | Same as TC-30 |
| TC-33 | Performance | **PASS** | Login <2s, Search <1s |
| TC-34 | Logcat | **PASS** | No crashes after ViewModel fix |
| TC-35 | Crash Survival | **PASS** | App survives invalid input |

---

## Bugs Found

### BUG-001: P0 — SurveyViewModel Crash on New Survey (FIXED)
- **Symptom:** App crashes with "Something went wrong" dialog when tapping "New Survey"
- **Root Cause:** `SurveyViewModel` has constructor params (`repository`, `syncRepository`, `connectivityObserver`, `appContext`) but all fragments use `ViewModelProvider(requireActivity())` which requires a no-arg or Application-only constructor
- **Fix:** Created `SurveyViewModelFactory` and updated all 5 fragments to use it
- **Files:** `SurveyViewModelFactory.kt` (new), `NewSurveyFragment.kt`, `SheetFragment.kt`, `SurveyFormFragment.kt`, `ReviewFragment.kt`, `CameraFragment.kt`

### BUG-002: P1 — Submit/Take Photo Buttons Have No Click Handlers
- **Symptom:** "Submit Revision" and "Take Photo" buttons are visible in the survey form but tapping them does nothing
- **Root Cause:** `SurveyFormFragment` only sets up `btnSaveDraft` click listener. `btnSubmitRevision` and `btnTakePhoto` have no listeners despite being defined in the layout and nav_graph
- **Impact:** Cannot submit revisions, take photos, or complete the survey workflow
- **Fix Required:** Add click listeners in `SurveyFormFragment.onViewCreated()`:
  - `btnTakePhoto` → `findNavController().navigate(R.id.action_form_to_camera)`
  - `btnSubmitRevision` → first save draft, then `findNavController().navigate(R.id.action_form_to_review)`

### BUG-003: P2 — API_BASE_URL Missing /api/v1/ Prefix (FIXED)
- **Symptom:** Login fails with 404 "auth/login/ didn't match"
- **Root Cause:** `API_BASE_URL` in `local.properties` was `http://192.168.100.41:8000` but Retrofit endpoints don't include `/api/v1/` prefix
- **Fix:** Changed to `http://192.168.100.41:8000/api/v1/`
- **Note:** This only affected `local.properties` (not checked into VCS). The emulator default `http://10.0.2.2:8000` has the same bug.

### BUG-004: P2 — Session Lost on Navigation Away
- **Symptom:** Pressing Home or switching to another app loses login state, returns to login screen on return
- **Root Cause:** Likely the Activity is being recreated or the token check fails
- **Impact:** Surveyor loses in-progress work when switching apps

### BUG-005: P3 — New Survey Search Field Shows Previous Parcel Code
- **Symptom:** The parcel ID field in New Survey shows "RUDA-P14-R00005" from a previous search
- **Root Cause:** Fragment state not cleared between visits, or EditText retains value

---

## Environment Issues Fixed

1. **Windows Firewall:** Port 8000 blocked → Added rule `netsh advfirewall firewall add rule name="Django RUDA" dir=in action=allow protocol=tcp localport=8000`
2. **ALLOWED_HOSTS:** Django rejected requests from `192.168.100.41` → Added to `RUDA_ALLOWED_HOSTS` in `.env`
3. **Network Security Config:** App couldn't reach cleartext HTTP → Added `network_security_config.xml` and manifest reference

---

## Screenshots

| # | Screen | File |
|---|--------|------|
| 1 | Login | `03_invalid_login.png` |
| 2 | Dashboard | `05_dashboard.png` |
| 3 | Search (Valid) | `06_search_valid.png` |
| 4 | Search (Invalid) | `07_search_invalid.png` |
| 5 | Survey Form | `09_survey_form.png` |
| 6 | Survey Form (Scrolled) | `10_survey_form_scrolled.png` |

---

## Recommended Next Steps

1. **Fix BUG-002** (P1): Wire up `btnSubmitRevision` and `btnTakePhoto` click listeners in `SurveyFormFragment`
2. **Fix BUG-004** (P2): Investigate session persistence on Activity recreation
3. **Fix BUG-005** (P3): Clear search field on fragment creation
4. **Re-run full test suite** after fixes
5. **Test offline sync** (TC-21 through TC-24) once submission works
