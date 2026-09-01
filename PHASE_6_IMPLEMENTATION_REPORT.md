# PHASE 6 — Android Foundation Implementation Report

**Date:** 2026-08-26
**Status:** COMPLETE

---

## 1. Objective

Build the Android app skeleton with Login → Dashboard → Parcel Search → Original Data → Survey Form → Review → Camera → Sheet flow, all consuming the frozen API contract from Phase 4/5.

---

## 2. Architecture

- **Stack:** Kotlin + XML views (Material 3), MVVM + ViewModels/StateFlow, Retrofit/OkHttp, Room, CameraX, WorkManager, Navigation Component, EncryptedSharedPreferences.
- **Package:** `com.ruda.survey`
- **Build config:** compileSdk 34, minSdk 26, targetSdk 34, AGP 8.2.2, Kotlin 1.9.22

---

## 3. Files Created / Modified

### Gradle & Build
| File | Purpose |
|------|---------|
| `android/app/build.gradle` | App module with all dependencies |
| `android/app/proguard-rules.pro` | ProGuard/R8 keep rules for Retrofit, Gson, Room, OkHttp |
| `android/local.properties` | `API_BASE_URL=http://10.0.2.2:8000` |

### Manifest & Resources
| File | Purpose |
|------|---------|
| `AndroidManifest.xml` | INTERNET, CAMERA, ACCESS_NETWORK_STATE permissions; Application class |
| `res/values/strings.xml` | All user-facing strings |
| `res/values/colors.xml` | Primary, error, background, text colors |
| `res/values/themes.xml` | Material 3 theme |
| `res/navigation/nav_graph.xml` | 7-fragment navigation graph |
| `res/layout/activity_main.xml` | NavHostFragment container |
| `res/layout/fragment_login.xml` | Login form |
| `res/layout/fragment_dashboard.xml` | Dashboard with New Survey + Logout |
| `res/layout/fragment_new_survey.xml` | Parcel ID search |
| `res/layout/fragment_survey_form.xml` | Editable survey form |
| `res/layout/fragment_camera.xml` | CameraX preview + capture |
| `res/layout/fragment_review.xml` | Review + submit |
| `res/layout/fragment_sheet.xml` | Survey sheet display |

### Kotlin Source

#### Domain Layer
| File | Purpose |
|------|---------|
| `domain/model/Models.kt` | UiState, AuthState, ParcelInfo, SurveyData, EditableDraft, RevisionResult, ImageInfo |
| `domain/repository/SurveyRepository.kt` | Repository interface |

#### Data Layer
| File | Purpose |
|------|---------|
| `data/dto/ApiDtos.kt` | All API request/response DTOs (Gson-compatible) |
| `data/remote/SurveyApi.kt` | Retrofit API interface (all 13 endpoints) |
| `data/remote/ApiClient.kt` | Retrofit/OkHttp builder with auth interceptor |
| `data/remote/SecureTokenManager.kt` | EncryptedSharedPreferences token storage |
| `data/local/Entities.kt` | 5 Room entities: CachedParcel, CachedSurvey, DraftSurvey, PendingSubmission, CachedImage |
| `data/local/SurveyDao.kt` | DAO with 20+ queries for caching, drafts, pending submissions, images |
| `data/local/SurveyDatabase.kt` | Room database (v1) |
| `data/repository/SurveyRepositoryImpl.kt` | Full repository implementation |

#### UI Layer
| File | Purpose |
|------|---------|
| `ui/MainActivity.kt` | Navigation host |
| `ui/auth/LoginFragment.kt` | Login screen with JWT |
| `ui/auth/AuthViewModel.kt` | Login/logout via repository |
| `ui/dashboard/DashboardFragment.kt` | Dashboard navigation |
| `ui/survey/NewSurveyFragment.kt` | Parcel ID search |
| `ui/survey/SurveyViewModel.kt` | Core ViewModel: parcel lookup, draft management, revision submission, image upload |
| `ui/survey/SurveyFormFragment.kt` | Editable form with original data |
| `ui/survey/ReviewFragment.kt` | Review changes + submit |
| `ui/survey/SheetFragment.kt` | Survey sheet display |
| `ui/camera/CameraFragment.kt` | CameraX two-step capture (FRONT/SECOND) |

#### App
| File | Purpose |
|------|---------|
| `RudaSurveyApp.kt` | Application + WorkManager Configuration.Provider |

### Tests
| File | Purpose |
|------|---------|
| `test/.../domain/model/EditableDraftTest.kt` | 8 tests: computeChanges, UUID generation, defaults |
| `test/.../domain/model/ModelsTest.kt` | 8 tests: UiState, AuthState, ParcelInfo, ImageInfo, RevisionResult |
| `test/.../data/local/EntitiesTest.kt` | 10 tests: entity construction, default values |
| `test/.../data/repository/SurveyRepositoryImplTest.kt` | 14 tests: all repository methods (login, logout, parcel lookup, surveys, revision, image upload, sheet) |

---

## 4. Navigation Flow

```
LoginFragment → DashboardFragment → NewSurveyFragment → SurveyFormFragment → CameraFragment
                                              ↓                                    ↓
                                        ReviewFragment → SheetFragment
```

---

## 5. Room Database Schema (v1)

| Table | Primary Key | Purpose |
|-------|-------------|---------|
| `cached_parcels` | `parcel_code` | Offline parcel cache |
| `cached_surveys` | `(parcel_code, survey_type)` | Offline survey data cache |
| `draft_surveys` | `parcel_code` | Local draft revisions |
| `pending_submissions` | `id` (auto) | Offline revision queue |
| `cached_images` | `id` (auto) | Offline image storage |

---

## 6. API Integration

All API calls go through `SurveyRepositoryImpl` → `SurveyApi` (Retrofit). The frozen contract from Phase 4/5 is used exactly:

| Endpoint | Method | Retrofit Function |
|----------|--------|-------------------|
| `/api/v1/auth/login/` | POST | `login()` |
| `/api/v1/auth/logout/` | POST | `logout()` |
| `/api/v1/parcels/{code}/` | GET | `parcelLookup()` |
| `/api/v1/parcels/{code}/original/` | GET | `originalData()` |
| `/api/v1/parcels/{code}/current/` | GET | `currentData()` |
| `/api/v1/parcels/{code}/revisions/` | GET/POST | `revisions()` / `createRevision()` |
| `/api/v1/parcels/{code}/revisions/{no}/images/` | POST | `uploadImage()` |
| `/api/v1/parcels/{code}/revisions/{no}/status/` | POST | `changeRevisionStatus()` |
| `/api/v1/survey/sheet/` | GET | `surveySheet()` |
| `/api/v1/survey/pdf/` | GET | `surveyPdf()` |
| `/api/v1/admin/import/` | POST | `importExcel()` |
| `/api/v1/admin/users/` | GET | `listUsers()` |
| `/api/v1/admin/stats/` | GET | `getStats()` |

---

## 7. Test Coverage

| Test Group | File | Count | Status |
|------------|------|-------|--------|
| EditableDraft | EditableDraftTest.kt | 8 | PASS |
| Domain Models | ModelsTest.kt | 8 | PASS |
| Room Entities | EntitiesTest.kt | 10 | PASS |
| Repository | SurveyRepositoryImplTest.kt | 14 | PASS |
| **Total** | | **40** | |

---

## 8. 16KB Page Size Compatibility

- minSdk 26 (Android 8.0+) supports ELF 16KB page sizes natively
- No native libraries (.so) bundled — pure Kotlin/JVM
- No 16KB compatibility issues

---

## 9. Known Limitations (Phase 7+ scope)

1. **Offline sync queue**: `PendingSubmission` entity exists but `SyncWorker` not yet implemented (Phase 7)
2. **Image upload retry**: Not implemented (Phase 7)
3. **Camera EXIF stripping**: Not yet implemented (Phase 7)
4. **PDF generation**: Backend returns 503 (documented), Android sheet view shows data only
5. **Use case layer**: Empty — repository called directly from ViewModels (acceptable for Phase 6 scope)
6. **DI (Hilt)**: Not wired — manual dependency injection via constructors

---

## 10. Next Phase

Proceed to Phase 7: Offline sync with WorkManager, image upload retry, pending queue processing, and conflict handling.
