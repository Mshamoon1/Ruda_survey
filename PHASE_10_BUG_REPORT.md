# PHASE 10 — BUG REPORT

**Date:** 2026-08-27
**Sprint:** Phase 10 — Real Device QA
**Tester:** Automated (ADB + uiautomator)

---

## BUG-001: SurveyViewModel Crash on New Survey

| Field | Value |
|-------|-------|
| **ID** | BUG-001 |
| **Severity** | P0 (Crash) |
| **Status** | FIXED |
| **Component** | Android / UI / Survey |
| **Reproducibility** | 100% |

### Description
App crashes with Samsung "Something went wrong" dialog when tapping "New Survey" on the dashboard.

### Steps to Reproduce
1. Login with valid credentials
2. Tap "New Survey" on dashboard
3. App crashes immediately

### Root Cause
`SurveyViewModel` constructor requires `repository`, `syncRepository`, `connectivityObserver`, `appContext` parameters. All fragments use `ViewModelProvider(requireActivity())` which uses `AndroidViewModelFactory` — this factory can only create ViewModels with no-arg or `Application`-only constructors. Throws `NoSuchMethodException`.

### Stack Trace
```
java.lang.RuntimeException: Cannot create an instance of class com.ruda.survey.ui.survey.SurveyViewModel
Caused by: java.lang.NoSuchMethodException: com.ruda.survey.ui.survey.SurveyViewModel.<init> []
```

### Fix
Created `SurveyViewModelFactory` class and updated 5 fragments to use it:
- `NewSurveyFragment.kt`
- `SheetFragment.kt`
- `SurveyFormFragment.kt`
- `ReviewFragment.kt`
- `CameraFragment.kt`

---

## BUG-002: Submit/Take Photo Buttons Have No Click Handlers

| Field | Value |
|-------|-------|
| **ID** | BUG-002 |
| **Severity** | P1 (Major workflow broken) |
| **Status** | OPEN |
| **Component** | Android / UI / SurveyFormFragment |
| **Reproducibility** | 100% |

### Description
"Submit Revision" and "Take Photo" buttons are visible in the survey form but tapping them does nothing. The entire survey workflow (edit → photo → review → submit) is broken.

### Steps to Reproduce
1. Login → New Survey → Search RUDA-P14-R00005
2. Tap "View/Edit Survey"
3. Scroll to see "Submit Revision" and "Take Photo" buttons
4. Tap either button — nothing happens

### Root Cause
`SurveyFormFragment.onViewCreated()` only sets up `btnSaveDraft` click listener. The layout defines `btnSubmitRevision` and `btnTakePhoto` but no click listeners are attached. The nav_graph defines `action_form_to_camera` and `action_form_to_review` but they're never triggered.

### Fix Required
Add to `SurveyFormFragment.onViewCreated()`:
```kotlin
binding.btnTakePhoto.setOnClickListener {
    // Save draft first
    saveDraftToViewModel()
    findNavController().navigate(R.id.action_form_to_camera)
}

binding.btnSubmitRevision.setOnClickListener {
    // Save draft first
    saveDraftToViewModel()
    findNavController().navigate(R.id.action_form_to_review)
}
```

---

## BUG-003: API_BASE_URL Missing /api/v1/ Prefix

| Field | Value |
|-------|-------|
| **ID** | BUG-003 |
| **Severity** | P2 (Login broken on real device) |
| **Status** | FIXED |
| **Component** | Android / Network / ApiClient |
| **Reproducibility** | 100% |

### Description
Login fails with 404 because the app sends requests to `http://192.168.100.41:8000/auth/login/` instead of `http://192.168.100.41:8000/api/v1/auth/login/`.

### Root Cause
`API_BASE_URL` in `local.properties` is `http://192.168.100.41:8000` but Retrofit endpoints don't include the `/api/v1/` prefix.

### Fix
Changed `local.properties`:
```
API_BASE_URL=http\://192.168.100.41\:8000/api/v1/
```

### Note
This same bug affects the emulator default `http://10.0.2.2:8000`. The `build.gradle` fallback should also include `/api/v1/`.

---

## BUG-004: Session Lost on Navigation Away

| Field | Value |
|-------|-------|
| **ID** | BUG-004 |
| **Severity** | P2 |
| **Status** | OPEN |
| **Component** | Android / Auth / Session |
| **Reproducibility** | Intermittent |

### Description
When the user presses Home or switches to another app, the login session is lost. Returning to the app shows the login screen instead of the dashboard.

### Steps to Reproduce
1. Login successfully
2. Press Home button
3. Open another app (e.g., email)
4. Return to RUDA Survey
5. Login screen shown instead of dashboard

---

## BUG-005: New Survey Search Field Shows Previous Value

| Field | Value |
|-------|-------|
| **ID** | BUG-005 |
| **Severity** | P3 (UI) |
| **Status** | OPEN |
| **Component** | Android / UI / NewSurveyFragment |
| **Reproducibility** | 100% |

### Description
The parcel ID field in the New Survey screen shows the previously searched parcel code instead of being empty.

### Impact
Minor UX issue. User must manually clear the field before entering a new parcel code.
