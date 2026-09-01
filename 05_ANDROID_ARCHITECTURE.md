# 05 — ANDROID ARCHITECTURE (PHASE 1 PROPOSAL — NOT IMPLEMENTED)

**Stack:** Kotlin · XML views (no Compose) · MVVM + ViewModels/StateFlow · Material 3 components via MDC-XML · Retrofit/OkHttp · Room · CameraX · WorkManager · Hilt · DataStore · Navigation Component.

---

## 1. Module / Package Layout (single Gradle module `:app` initially; clean seams for later split)

```
app/src/main/java/com/ruda/survey/
|-- RudaApp.kt                      (Application; WorkManager/Hilt init)
|-- di/                             (Hilt modules: NetworkModule, DatabaseModule, RepoModule)
|-- data/
|   |-- remote/                     (Retrofit APIs, DTOs, interceptors: AuthInterceptor, TokenAuthenticator)
|   |-- local/
|   |   |-- dao/                    (ParcelDao, RevisionDao, ImageDao, SyncQueueDao, UserDao)
|   |   `-- entity/                 (Room entities mirroring API payloads)
|   |-- repository/                 (ParcelRepository, SurveyRepository, ImageRepository, SyncRepository)
|   `-- session/                    (DataStore token store, SessionManager)
|-- domain/
|   |-- model/                      (Parcel, MasterLine, SurveyDraft, SurveyImage, Revision)
|   `-- usecase/                    (LoginUseCase, SearchParcelUseCase, BuildRevisionUseCase,
|                                     ValidateFormUseCase, EnqueueSyncUseCase)
|-- ui/
|   |-- login/                      ( LoginActivity, LoginViewModel )
|   |-- dashboard/                  ( DashboardActivity -- assigned parcels, pending syncs )
|   |-- survey/
|   |   |-- SearchParcelFragment    (manual Parcel ID entry)
|   |   |-- OriginalDataFragment    (read-only master view; NULL fields hidden)
|   |   |-- EditSurveyFragment      (WB2-schema form)
|   |   |-- ReviewChangesFragment   (diff list before submit)
|   |   `-- SheetPreviewFragment    (survey sheet preview)
|   |-- camera/                     (CaptureFlowActivity -- two steps FRONT -> SECOND)
|   `-- common/                     (adapters, Material3 dialogs, NullAwareAdapter helpers)
|-- work/                           (SyncWorker, ImageUploadWorker, RetryPolicy)
|-- util/                           (formatters, CNIC/contact validators, GPS utils)
`-- nav/                            (NavGraph wiring the survey workflow)
```

## 2. Screen ↔ Workflow Mapping (brief §11 flow)

| Step | Screen | Notes |
|---|---|---|
| Login | LoginActivity | JWT stored in encrypted DataStore |
| Dashboard | DashboardActivity | offline badge, pending-sync count |
| New Survey → Manual Parcel ID | SearchParcelFragment | uppercase input filter, regex validation |
| Search/Fetch Original | ParcelRepository → API #2/#3 | cached copy in Room for offline |
| Display Original Data | OriginalDataFragment | read-only; **NULL fields are not rendered** (doc 02 §F rule) |
| Edit Survey | EditSurveyFragment | WB2 fields as Material inputs; empty ⇒ null, no placeholder text |
| Capture 2 Images | CaptureFlowActivity (CameraX) | enforced sequence FRONT → SECOND; EXIF stripped; compressed JPEG |
| Review Changes | ReviewChangesFragment | field-level diff old→new |
| Create New Revision | BuildRevisionUseCase → POST #6 | client_uuid idempotency; queues if offline |
| Survey Sheet | SheetPreviewFragment → GET #8/#9 | HTML preview now, PDF later |
| Submit/Sync | SyncWorker (WorkManager) | uploads revisions then images |

## 3. Data Flow

UI (Fragment) → ViewModel (StateFlow<UiState>) → UseCase → Repository
→ **Room = single source of truth** for screens; Repository merges server results.
Retrofit never talks to UI directly.

## 4. Offline-First Decision (brief §12): **YES — recommended**

Field work has unreliable connectivity. Architecture:

1. **Room cache:** parcels/master lines fetched once per assignment; original data readable offline.
2. **Local draft:** edits saved as draft revision rows (`status=draft`) with generated `client_uuid`.
3. **Sync queue table:** ordered ops (revision-create, image-upload) with state/pending retries/backoff.
4. **WorkManager:** `UniqueWork` expedited sync on demand + periodic 6 h maintenance; network+storage constraints; exponential backoff; survives reboot/process death.
5. **Conflict policy:** revisions are append-only by design → conflicts are rare; on 409 stale-parent the client re-reads current and re-submits as a *new* revision (no silent merge); user sees "server had newer data" toast. Server assigns authoritative `revision_no`; device keeps provisional numbers only for display.
6. **Images:** stored in app-specific storage until upload ACK, checksum verified, then eligible for cleanup.

## 5. Camera Plan (Phase 7 scope note)

CameraX `ImageCapture` (not preview-heavy analysis), bind to lifecycle-aware PreviewView;
two-step guided capture; mandatory image before draft can be submitted; store `FRONT`/`SECOND`
type with capture timestamp; no gallery import allowed (integrity).

> Phase 2 note: the second image type is named `SECOND` (previously drafted as SECONDARY) to match
> the database CHECK constraint implemented in Phase 2.

## 6. Cross-Cutting UI Rules

- **NULL-hiding:** every list item/card builder checks null/empty → skips that row entirely (shared helper).
- Material 3 dynamic colors disabled for brand consistency; dark toolbar theme for outdoor readability; large touch targets for field gloves.
- All network errors map to human-readable strings; retry affordances everywhere.

---

## 7. PHASE 10A — Khasra/Mauza Search + Point Images (IMPLEMENTED)

### 7.1 Search Flow
```
NewSurveyFragment
  ├── [Parcel Code] → Search by Parcel ID (existing)
  └── [Khasra Number] + [Mauza Number] → Advanced Search (NEW)
        ├── API: GET /api/v1/surveys/search/?khasra_number=X&mauza_number=Y
        ├── Results: RecyclerView with SearchResultsAdapter
        ├── Single result → direct navigation to survey details
        └── Multiple results → surveyor selects from list
```

### 7.2 Search API
- **Endpoint:** `GET /api/v1/surveys/search/`
- **Parameters:** `khasra_number` (optional), `mauza_number` (optional)
- **At least one required**
- **Response:** `{"results": [...], "count": N}`
- **Max 50 results**

### 7.3 Image Types
| Type | Description | Phase |
|---|---|---|
| FRONT | Front evidence photo | Phase 2 |
| SECOND | Secondary evidence photo | Phase 2 |
| POINT_1 | Point/coordinate evidence image 1 | Phase 10A |
| POINT_2 | Point/coordinate evidence image 2 | Phase 10A |

### 7.4 Camera Flow
```
CameraFragment
  └── ExposedDropdownMenu for image type selection
        ├── FRONT
        ├── SECOND
        ├── POINT_1
        └── POINT_2
```

### 7.5 Key Files
| File | Purpose |
|---|---|
| `ui/survey/NewSurveyFragment.kt` | Search UI with khasra/mauza fields |
| `ui/survey/SearchResultsAdapter.kt` | RecyclerView adapter for search results |
| `ui/camera/CameraFragment.kt` | Camera with POINT_1/POINT_2 type selection |
| `data/dto/ApiDtos.kt` | SearchRequest, SearchResult, SearchResponse DTOs |
| `data/remote/SurveyApi.kt` | searchSurveys() endpoint |
| `domain/model/Models.kt` | SearchParcel data class |
| `data/repository/SurveyRepositoryImpl.kt` | searchParcels() implementation |
