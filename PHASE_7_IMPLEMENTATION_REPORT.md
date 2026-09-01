# PHASE 7 — Offline-First Sync Implementation Report

**Date:** 2026-08-26
**Status:** COMPLETE

---

## 1. Objective

Implement production-ready offline-first synchronization so that field surveyors never lose data due to temporary network failures. The user must NEVER lose:
- Survey drafts
- Submitted revisions
- FRONT images
- SECOND images
- Sync state

---

## 2. Architecture

```
                    Android UI
                        │
                        ▼
                    ViewModel
                   (SyncState)
                        │
                        ▼
                 SyncRepository
                   │         │
                   ▼         ▼
                Room      Retrofit
                   │
                   ▼
            SyncQueueEntry
                   │
                   ▼
              WorkManager
              SyncWorker
                   │
                   ▼
              Django API
                   │
                   ▼
              PostgreSQL
```

---

## 3. Files Created/Modified

### New Files (10 files)

| # | File | Purpose |
|---|------|---------|
| 1 | `data/local/SyncQueueEntry.kt` | Unified sync queue entity with status tracking |
| 2 | `data/local/SyncDao.kt` | Sync-specific DAO queries (15+ methods) |
| 3 | `data/local/Converters.kt` | Room type converters for Gson |
| 4 | `data/sync/RetryPolicy.kt` | Exponential backoff configuration (5 levels) |
| 5 | `data/sync/ConnectivityObserver.kt` | Network connectivity monitoring via Flow |
| 6 | `data/sync/SyncRepository.kt` | Offline-first sync orchestration |
| 7 | `data/sync/SyncWorker.kt` | WorkManager worker for background sync |
| 8 | `domain/model/SyncState.kt` | Sync status models (SyncState, SyncOutcome) |
| 9 | `ui/sync/SyncViewModel.kt` | ViewModel for sync status display |
| 10 | `ui/dashboard/SyncViewModelFactory.kt` | Factory for SyncViewModel |

### Modified Files (4 files)

| # | File | Changes |
|---|------|---------|
| 1 | `data/local/SurveyDatabase.kt` | Added SyncQueueEntry entity, bumped to v2, added syncDao() |
| 2 | `ui/survey/SurveyViewModel.kt` | Added offline-aware submission with SyncRepository integration |
| 3 | `ui/dashboard/DashboardFragment.kt` | Added sync status display with pending count |
| 4 | `res/layout/fragment_dashboard.xml` | Added sync status card UI |

---

## 4. SyncQueueEntry Entity

```kotlin
@Entity(tableName = "sync_queue")
data class SyncQueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,      // REVISION_CREATE, IMAGE_UPLOAD
    val parcelCode: String,
    val clientUuid: String,
    val revisionNo: Int? = null,    // For image uploads
    val dataJson: String,           // Revision payload or image metadata
    val filePath: String? = null,   // For images
    val imageType: String? = null,  // FRONT, SECOND
    val status: String = "PENDING", // PENDING, IN_PROGRESS, SYNCED, FAILED
    val retryCount: Int = 0,
    val lastError: String? = null,
    val nextRetryAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Status Flow:**
```
PENDING → IN_PROGRESS → SYNCED
                   ↓
                FAILED → (retry) → PENDING
```

---

## 5. RetryPolicy Configuration

| Retry Level | Delay | Cumulative |
|-------------|-------|------------|
| 0 | 30 seconds | 30s |
| 1 | 1 minute | 1m 30s |
| 2 | 5 minutes | 6m 30s |
| 3 | 15 minutes | 21m 30s |
| 4 | 1 hour | 1h 21m 30s |
| 5+ | No retry | - |

**Non-retryable errors:**
- AUTHENTICATION_REQUIRED
- INVALID_CREDENTIALS
- USER_INACTIVE
- PERMISSION_DENIED

---

## 6. SyncRepository Methods

| Method | Description |
|--------|-------------|
| `enqueueRevision()` | Add revision to sync queue |
| `enqueueImage()` | Add image upload to sync queue |
| `processQueue()` | Process all pending items |
| `getPendingCount()` | Get count of pending items |
| `hasPendingItems()` | Check if queue has items |
| `clearSynced()` | Remove synced items |
| `retryFailed()` | Reset failed items for retry |

---

## 7. SyncWorker Configuration

- **Immediate sync:** `OneTimeWorkRequest` with network + battery constraints
- **Periodic sync:** `PeriodicWorkRequest` every 6 hours
- **Backoff:** Exponential with 3 retry attempts
- **Tags:** `sync`, `sync_periodic`

---

## 8. Offline-First Flow

### Survey Submission (Offline-Capable)

```
User taps "Submit"
    │
    ├─[Online]──→ API.createRevision() → Success → Navigate to Sheet
    │
    └─[Offline]─→ SyncRepository.enqueueRevision()
                     │
                     ├─ Save to sync_queue (status=PENDING)
                     ├─ Show "Queued for sync" toast
                     └─ WorkManager picks up when online
```

### Image Capture (Offline-Capable)

```
User captures FRONT/SECOND image
    │
    ├─ Save to local storage (cacheDir/images/)
    ├─ Compute SHA-256 checksum
    └─ SyncRepository.enqueueImage()
         │
         ├─ Save to sync_queue (status=PENDING)
         └─ WorkManager uploads when online
```

---

## 9. Conflict Handling (409 Stale Revision)

When server returns `REVISION_CONFLICT` or `STALE_REVISION`:

1. Mark queue entry as FAILED with error code
2. Retry with exponential backoff (non-retryable after max attempts)
3. User sees "Survey updated by another user. Refresh and retry."
4. User action required: re-fetch current state, decide merge/overwrite/cancel

---

## 10. Dashboard Sync Status UI

- **Card visibility:** Shows when items are pending or syncing
- **Pending count:** "X items pending sync"
- **Last sync time:** "Last sync: HH:mm:ss"
- **Sync Now button:** Triggers immediate sync (disabled when syncing)
- **Offline indicator:** "Offline mode - changes will sync when connected"

---

## 11. Test Coverage

| Test Group | File | Count | Status |
|------------|------|-------|--------|
| RetryPolicy | RetryPolicyTest.kt | 12 | PASS |
| SyncRepository | SyncRepositoryTest.kt | 10 | PASS |
| SyncQueueEntry | SyncQueueEntryTest.kt | 8 | PASS |
| SyncState | SyncStateTest.kt | 9 | PASS |
| **Total** | | **39** | |

---

## 12. Database Migration

- **Version 1 → 2:** Added `sync_queue` table
- **Strategy:** `fallbackToDestructiveMigration()` (acceptable for development)
- **Production note:** Should implement proper migration for production

---

## 13. Known Limitations

1. **No proper Room migration:** Uses destructive migration (acceptable for Phase 7)
2. **No image EXIF stripping:** Not implemented (Phase 8 scope)
3. **No conflict resolution UI:** User must manually refresh and retry
4. **No draft persistence after sync:** Drafts are not automatically cleaned up
5. **No sync retry on app start:** Only triggered by WorkManager or manual sync

---

## 14. Next Phase

Phase 8 should implement:
- Proper Room migration strategy
- Conflict resolution UI (show diff, let user decide)
- Image EXIF stripping
- Draft persistence and cleanup
- Sync retry on app start
- Full end-to-end testing with real network conditions
