# PHASE 10 — REAL ANDROID MOBILE QA/UAT TESTING PLAN

## Status: PLANNING (Read-Only Mode)
**Date:** 2026-08-27
**Purpose:** Real device testing ONLY — no new features, no redesign, no Phase 11

---

## CRITICAL FINDINGS FROM DOCUMENTATION REVIEW

### Missing Documentation
| File | Status |
|------|--------|
| PHASE_6_ANDROID_TEST_REPORT.md | NOT FOUND |
| PHASE_6_16KB_COMPATIBILITY_REPORT.md | NOT FOUND |
| OFFLINE_SYNC_ARCHITECTURE.md | NOT FOUND |
| SYNC_STATE_MACHINE.md | NOT FOUND |
| PHASE_8_IMPLEMENTATION_REPORT.md | NOT FOUND |
| PHASE_8_UI_TEST_REPORT.md | NOT FOUND |
| PHASE_8_REVISION_WORKFLOW.md | NOT FOUND |

### Known Issues to Verify
| Issue | Severity | Phase |
|-------|----------|-------|
| Destructive Room migration (fallbackToDestructiveMigration) | HIGH | 7 |
| No conflict resolution UI (409 requires manual refresh) | MEDIUM | 7 |
| No draft persistence/cleanup after sync | MEDIUM | 7 |
| No image EXIF stripping | MEDIUM | 7 |
| No instrumented tests (androidTest/) | MEDIUM | ALL |

### API Configuration
- **Current:** `http://10.0.2.2:8000` (emulator only)
- **Required:** LAN IP address for real device
- **File:** `android/local.properties` → `API_BASE_URL`

---

## PRE-TESTING CHECKLIST

### 1. Backend Preparation
- [ ] Start Django server on `0.0.0.0:8000`
- [ ] Verify firewall allows port 8000 on LAN
- [ ] Record machine LAN IP (e.g., `192.168.x.x`)
- [ ] Verify PostgreSQL Ruda_Survey database is accessible
- [ ] Record baseline counts:
  - survey_master: ___
  - parcels: ___
  - survey_changes: ___
  - survey_images: ___
  - audit_logs: ___

### 2. Android Build Preparation
- [ ] Update `API_BASE_URL` in `local.properties` to LAN IP
- [ ] Run `./gradlew clean`
- [ ] Run `./gradlew assembleDebug`
- [ ] Record build result, APK path, size, version

### 3. Device Preparation
- [ ] Enable USB debugging on physical device
- [ ] Connect device via USB
- [ ] Run `adb devices` to verify detection
- [ ] Record device information:
  - Manufacturer: ___
  - Model: ___
  - Android version: ___
  - API level: ___
  - CPU architecture: ___
  - RAM: ___
  - Screen resolution: ___
  - Available storage: ___

---

## TEST CASES

### TC-01: Installation
| Step | Action | Expected |
|------|--------|----------|
| 1 | `adb install -r <apk>` | Install succeeds |
| 2 | Open app | App launches |
| 3 | Observe startup | No crash, splash/main screen appears |

### TC-02: Login - Valid Credentials
| Step | Action | Expected |
|------|--------|----------|
| 1 | Enter valid username | Input accepted |
| 2 | Enter valid password (masked) | Password hidden |
| 3 | Tap Login | Loading indicator |
| 4 | Wait for response | Dashboard appears |
| 5 | Check Logcat | No JWT visible in logs |

### TC-03: Login - Invalid Credentials
| Step | Action | Expected |
|------|--------|----------|
| 1 | Enter valid username | Input accepted |
| 2 | Enter wrong password | Input accepted |
| 3 | Tap Login | Error message shown |
| 4 | Verify error | "Invalid username or password" |

### TC-04: Login - Empty Fields
| Step | Action | Expected |
|------|--------|----------|
| 1 | Leave username empty | Validation error |
| 2 | Leave password empty | Validation error |
| 3 | Submit | Form not submitted |

### TC-05: Dashboard
| Step | Action | Expected |
|------|--------|----------|
| 1 | After login | Dashboard displays |
| 2 | Check user info | Username/role shown |
| 3 | Check sync status | Pending count shown |
| 4 | Tap "New Survey" | Navigates to search |

### TC-06: Parcel Search - Valid
| Step | Action | Expected |
|------|--------|----------|
| 1 | Tap "New Survey" | Search screen |
| 2 | Enter `RUDA-P14-R00005` | Input accepted |
| 3 | Tap Search | Loading indicator |
| 4 | Wait | Original data appears |

### TC-07: Parcel Search - Invalid
| Step | Action | Expected |
|------|--------|----------|
| 1 | Enter `RUDA-P14-INVALID` | Input accepted |
| 2 | Tap Search | Loading indicator |
| 3 | Wait | "Parcel not found" message |

### TC-08: Parcel Search - Empty
| Step | Action | Expected |
|------|--------|----------|
| 1 | Leave empty | — |
| 2 | Tap Search | Validation error |

### TC-09: Original Data - Read Only
| Step | Action | Expected |
|------|--------|----------|
| 1 | View original data | All fields displayed |
| 2 | Try to tap/edit field | No edit mode |
| 3 | Verify values | Match backend data |

### TC-10: Current Data
| Step | Action | Expected |
|------|--------|----------|
| 1 | View current data | State displayed |
| 2 | If no revision | Shows original |
| 3 | If revision exists | Shows effective state |

### TC-11: Edit Survey - Single Field
| Step | Action | Expected |
|------|--------|----------|
| 1 | Open edit form | Form loads |
| 2 | Tap one field | Keyboard opens |
| 3 | Enter new value | Input accepted |
| 4 | Move to next field | Previous value retained |
| 5 | Check draft | Draft exists |

### TC-12: Edit Survey - Multiple Fields
| Step | Action | Expected |
|------|--------|----------|
| 1 | Edit 3+ fields | All changes saved |
| 2 | Navigate back | Draft preserved |
| 3 | Return to form | All changes present |

### TC-13: NULL Field Handling
| Step | Action | Expected |
|------|--------|----------|
| 1 | Find NULL field | Field hidden |
| 2 | Edit field with value | Clear to empty |
| 3 | Submit | NULL sent to server |
| 4 | Verify | Not "null" string, not "N/A" |

### TC-14: Change Review
| Step | Action | Expected |
|------|--------|----------|
| 1 | Modify 1 field | — |
| 2 | Open Review | Only changed field shown |
| 3 | Modify 3 fields | — |
| 4 | Open Review | Only changed fields shown |
| 5 | Verify | Original vs New correct |

### TC-15: FRONT Camera
| Step | Action | Expected |
|------|--------|----------|
| 1 | Tap FRONT PHOTO | Permission request |
| 2 | Grant permission | Camera opens |
| 3 | Take photo | Preview shown |
| 4 | Tap Retake | Return to camera |
| 5 | Take photo again | Preview |
| 6 | Accept | Labeled as FRONT |

### TC-16: SECOND Camera
| Step | Action | Expected |
|------|--------|----------|
| 1 | Tap SECOND PHOTO | Camera opens |
| 2 | Take photo | Preview |
| 3 | Accept | Labeled as SECOND |
| 4 | Verify | Not swapped with FRONT |

### TC-17: Camera Permission Denial
| Step | Action | Expected |
|------|--------|----------|
| 1 | Deny permission | No crash |
| 2 | Show message | "Camera access required" |
| 3 | Deny again | No crash |

### TC-18: Online Submission
| Step | Action | Expected |
|------|--------|----------|
| 1 | Edit survey | Changes made |
| 2 | Capture FRONT | Image saved |
| 3 | Capture SECOND | Image saved |
| 4 | Review changes | Diff shown |
| 5 | Submit | Loading state |
| 6 | Wait | Success message |
| 7 | Check revision | Revision created |

### TC-19: Database Verification
| Step | Action | Expected |
|------|--------|----------|
| 1 | Query survey_master | Count unchanged |
| 2 | Query survey_changes | Count +1 |
| 3 | Query survey_images | Count +2 (FRONT, SECOND) |
| 4 | Query audit_logs | Events logged |
| 5 | Verify client_uuid | Exists exactly once |

### TC-20: App Restart - Draft Preservation
| Step | Action | Expected |
|------|--------|----------|
| 1 | Create draft | Fields edited |
| 2 | Capture images | FRONT, SECOND saved |
| 3 | Force close app | App terminated |
| 4 | Reopen app | Draft exists |
| 5 | Verify | All fields, images present |

### TC-21: Offline Submission
| Step | Action | Expected |
|------|--------|----------|
| 1 | Disable WiFi | — |
| 2 | Disable Mobile Data | — |
| 3 | Edit survey | Changes made |
| 4 | Capture FRONT | Saved locally |
| 5 | Capture SECOND | Saved locally |
| 6 | Submit | "Queued for sync" NOT "Submitted" |
| 7 | Verify | Status = PENDING |

### TC-22: Offline App Restart
| Step | Action | Expected |
|------|--------|----------|
| 1 | While offline | — |
| 2 | Force close app | App terminated |
| 3 | Reopen app | Draft exists |
| 4 | Verify images | FRONT, SECOND present |
| 5 | Verify sync queue | Entry = PENDING |

### TC-23: Online Sync
| Step | Action | Expected |
|------|--------|----------|
| 1 | Enable WiFi | — |
| 2 | Wait for WorkManager | Sync starts |
| 3 | Monitor status | PENDING → SYNCING → SYNCED |
| 4 | Verify | Revision created |
| 5 | Verify | No duplicate revision |

### TC-24: Duplicate Sync Prevention
| Step | Action | Expected |
|------|--------|----------|
| 1 | Submit offline | Queued |
| 2 | Sync starts | Processing |
| 3 | During sync | Do not submit again |
| 4 | After sync | ONE revision in DB |

### TC-25: Network Failure During Submission
| Step | Action | Expected |
|------|--------|----------|
| 1 | Start submission | Loading |
| 2 | Disable network | — |
| 3 | Observe | No crash |
| 4 | Restore network | Retry |

### TC-26: App Backgrounding
| Step | Action | Expected |
|------|--------|----------|
| 1 | While editing | — |
| 2 | Press Home | App backgrounded |
| 3 | Return to app | State preserved |
| 4 | While camera preview | — |
| 5 | Press Home | — |
| 6 | Return | Camera state preserved |

### TC-27: Screen Rotation
| Step | Action | Expected |
|------|--------|----------|
| 1 | On login screen | Rotate device |
| 2 | Verify | Data not lost |
| 3 | On edit form | Rotate device |
| 4 | Verify | Draft preserved |

### TC-28: Back Button
| Step | Action | Expected |
|------|--------|----------|
| 1 | On edit form | Press Back |
| 2 | Verify | No data loss |
| 3 | On camera | Press Back |
| 4 | Verify | Return to form |

### TC-29: Logout
| Step | Action | Expected |
|------|--------|----------|
| 1 | Tap Logout | Tokens cleared |
| 2 | Press Back | Cannot access survey |
| 3 | Login again | Authentication works |

### TC-30: PDF Download
| Step | Action | Expected |
|------|--------|----------|
| 1 | Open Survey Sheet | Sheet loads |
| 2 | Tap "Download PDF" | Progress indicator |
| 3 | Wait | PDF saved |
| 4 | Open PDF | Parcel ID, revision, data correct |
| 5 | Tap "Share PDF" | Share dialog opens |

### TC-31: Excel Export
| Step | Action | Expected |
|------|--------|----------|
| 1 | Open Survey Sheet | Sheet loads |
| 2 | Tap "Export to Excel" | Progress indicator |
| 3 | Wait | Excel saved |
| 4 | Open Excel | Data correct |

### TC-32: 16KB Page Size Compatibility
| Step | Action | Expected |
|------|--------|----------|
| 1 | Check native libs | None (.so files) |
| 2 | Verify | No 16KB issues |
| 3 | Document | APK structure |

### TC-33: Performance
| Step | Action | Expected |
|------|--------|----------|
| 1 | Measure startup | <3 seconds |
| 2 | Measure login | <2 seconds |
| 3 | Measure parcel lookup | <2 seconds |
| 4 | Measure form scroll | Smooth (60fps) |
| 5 | Measure camera launch | <1 second |
| 6 | Measure submission | <5 seconds |

### TC-34: Logcat Monitoring
| Step | Action | Expected |
|------|--------|----------|
| 1 | Filter FATAL | No crashes |
| 2 | Filter ANR | No ANRs |
| 3 | Filter SecurityException | None |
| 4 | Filter Room errors | None |
| 5 | Filter WorkManager errors | None |

### TC-35: Crash Survival
| Step | Action | Expected |
|------|--------|----------|
| 1 | No internet | App survives |
| 2 | Server unavailable | App survives |
| 3 | Invalid parcel | App survives |
| 4 | Invalid credentials | App survives |
| 5 | Camera denied | App survives |
| 6 | Malformed response | App survives |
| 7 | Expired token | App survives |
| 8 | Upload failure | App survives |
| 9 | Revision conflict | App survives |

---

## SCREENSHOT EVIDENCE CHECKLIST

| # | Screen | Captured |
|---|--------|----------|
| 1 | Login | [ ] |
| 2 | Dashboard | [ ] |
| 3 | Parcel Search | [ ] |
| 4 | Original Data | [ ] |
| 5 | Current Data | [ ] |
| 6 | Edit Form | [ ] |
| 7 | Change Review | [ ] |
| 8 | FRONT Camera | [ ] |
| 9 | SECOND Camera | [ ] |
| 10 | Submit/Sync | [ ] |
| 11 | Survey Sheet | [ ] |
| 12 | Revision History | [ ] |
| 13 | PDF Download | [ ] |
| 14 | Excel Export | [ ] |

---

## BUG CLASSIFICATION

| Severity | Definition |
|----------|------------|
| P0 | App crash / data corruption / security |
| P1 | Major workflow broken |
| P2 | Important but workaround exists |
| P3 | UI / minor issue |

---

## FINAL TEST RESULT TEMPLATE

```
Device:
Model:
Android:
API:
Architecture:

Build:
APK:
APK Size:
Version:

Test cases:
PASS:
FAIL:
BLOCKED:
NOT TESTED:

Critical bugs:
P0:
P1:
P2:
P3:

Backend verification:
Master count:
Revision count:
Image count:
Audit count:

Offline sync: PASS/FAIL
Online submission: PASS/FAIL
Camera: FRONT PASS/FAIL, SECOND PASS/FAIL
PDF: PASS/FAIL/N/A
16KB: PASS/FAIL/BLOCKED
```

---

## EXECUTION NOTES

This plan requires:
1. Physical Android device connected via USB
2. Django server running on accessible LAN IP
3. PostgreSQL database with test data
4. ADB installed and configured

**DO NOT:**
- Mark untested items as PASS
- Use fake test results
- Start Phase 11
- Add new features
- Change API contracts
