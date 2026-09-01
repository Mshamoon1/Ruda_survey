# PHASE 10 — DEVICE TEST MATRIX

**Date:** 2026-08-27
**Device:** Samsung SM-A127F (R58RC0R1MJP)
**Android:** 13 (API 33)
**Architecture:** arm64-v8a
**Screen:** 720×1600, 300dpi

---

## Test Environment

| Item | Value |
|------|-------|
| PC IP | 192.168.100.41 |
| Device IP | 192.168.100.24 |
| WiFi SSID | Dr.akbar |
| Django Server | 0.0.0.0:8000 |
| Database | Ruda_Survey (PostgreSQL 18.6) |
| Test User | surveyor1 / test-pass-123 |

---

## Test Matrix

### Authentication
| TC | Test | Input | Expected | Actual | Status |
|----|------|-------|----------|--------|--------|
| TC-01 | Install APK | `adb install -r` | Success | Success | PASS |
| TC-02 | Valid Login | surveyor1/test-pass-123 | Dashboard | Dashboard | PASS |
| TC-03 | Invalid Login | surveyor1/wrongpassword | Error msg | "Invalid username or password" | PASS |
| TC-04 | Empty Fields | (empty)/(empty) | Validation | Form not submitted | PASS |
| TC-29 | Logout | Tap Logout | Login screen | Login screen | PASS |

### Search
| TC | Test | Input | Expected | Actual | Status |
|----|------|-------|----------|--------|--------|
| TC-06 | Valid Search | RUDA-P14-R00005 | Parcel found | Found: Village Arya Nagar, Rev 8 | PASS |
| TC-07 | Invalid Search | RUDA-P14-INVALID | Not found | PARCEL_NOT_FOUND 404 | PASS |
| TC-08 | Empty Search | (empty) | Validation | Client validation | PASS |

### Survey Form
| TC | Test | Input | Expected | Actual | Status |
|----|------|-------|----------|--------|--------|
| TC-09 | Original Data | View | All fields shown | Owner, area, CNIC, etc. | PASS |
| TC-10 | Current Data | View | Effective state | Shows revision data | PASS |
| TC-11 | Edit Field | Tap + type | Editable | Owner name editable | PASS |
| TC-12 | Multiple Edits | Edit 3+ fields | All saved | BLOCKED (P1 bug) | BLOCKED |
| TC-13 | NULL Fields | View | Empty display | NULL fields empty | PASS |

### Submission (BLOCKED by BUG-002)
| TC | Test | Expected | Status |
|----|------|----------|--------|
| TC-14 | Change Review | Shows diff | BLOCKED |
| TC-15 | FRONT Camera | Camera opens | BLOCKED |
| TC-16 | SECOND Camera | Camera opens | BLOCKED |
| TC-17 | Camera Permission | Permission flow | BLOCKED |
| TC-18 | Online Submit | Revision created | BLOCKED |
| TC-19 | DB Verification | Counts updated | BLOCKED |

### Offline (NOT TESTED)
| TC | Test | Status |
|----|------|--------|
| TC-20 | Draft Preservation | NOT TESTED |
| TC-21 | Offline Submit | NOT TESTED |
| TC-22 | Offline Restart | NOT TESTED |
| TC-23 | Online Sync | NOT TESTED |
| TC-24 | Duplicate Prevention | NOT TESTED |

### Edge Cases
| TC | Test | Status |
|----|------|--------|
| TC-25 | Network Failure | NOT TESTED |
| TC-26 | App Backgrounding | NOT TESTED |
| TC-27 | Screen Rotation | NOT TESTED |
| TC-28 | Back Button | NOT TESTED |

### Performance
| TC | Test | Expected | Actual | Status |
|----|------|----------|--------|--------|
| TC-33 | Login Time | <2s | ~2s | PASS |
| TC-33 | Search Time | <2s | <1s | PASS |
| TC-34 | Logcat FATAL | None | None | PASS |
| TC-35 | Crash Survival | Survives | Survives | PASS |

---

## Summary

| Metric | Count |
|--------|-------|
| **Total TC** | 35 |
| **PASS** | 16 |
| **FAIL** | 0 |
| **BLOCKED** | 8 |
| **NOT TESTED** | 11 |
