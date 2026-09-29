# Offline-first implementation audit

## Existing failures

- Login stores a token only. Startup never checks a daily session; logout buttons do not clear tokens.
- SecureTokenManager silently falls back to plaintext preferences.
- SurveyRepositoryImpl keeps its cache in memory, never reads/writes the existing cached_surveys table, and calls Retrofit before saving locally.
- Create/update failures enqueue incomplete payloads, omit attachments, then report failure to the UI. Both operations become REVISION_CREATE.
- SyncRepository ignores clientUuid on the wire, always POSTs, never reconciles Room, and reports standalone images as uploaded without sending them.
- IN_PROGRESS entries are stranded after process death. Retries are linear, permanent errors and authentication errors are not distinguished.
- SyncViewModel and WorkManager can process the same queue concurrently. REPLACE cancels in-flight work.
- Connectivity checks INTERNET rather than VALIDATED. Dashboard installs its sync listener after an indefinitely collecting flow.
- Database upgrades use destructive migration. Images and documents are only retained in ViewModel memory/cache before submission.

## Contract evidence and outstanding dependencies

The checked-in AuthApi exposes POST login returning {success,user,token}; no refresh endpoint or refresh token is defined. SurveyApi exposes GET surveys, GET survey/{id}, GET sr_no/{sr_no}, multipart POST survey and PUT survey/{id}. DTOs use _id and nested identification/covered_area. No backend source, assigned-record endpoint, revision endpoint, idempotency acceptance, or conditional-update contract is present in this checkout.

Do not invent a refresh endpoint or claim server duplicate/conflict protection from an Android UUID alone. Backend contract confirmation is required for those guarantees and for assigned-subset preloading. Keep immutable queued snapshots until audit semantics are confirmed.

The pre-existing user modification to fragment_dashboard.xml must be preserved.

## Verification

Baseline and phase results are recorded as implementation progresses. Device/backend scenarios must not be reported as passing without running them.

## Implemented Android behavior

- Daily access is centralized in OfflineSessionPolicy, uses the configured local time zone, records online authentication securely, and checks backward clock movement. JWT expiry alone does not invalidate the daily local session.
- The existing Room cache, draft table and sync queue persist complete user-scoped snapshots. Create/update commit locally before scheduling WorkManager. Find supports serial number, parcel code and khasra number for available records.
- Photos/documents live in app-owned files; Room stores paths and metadata. Drafts, queued snapshots and files survive repository/database reopen. Logout and API failures retain pending work.
- WorkManager uses connectivity constraints, periodic recovery and connectivity-triggered work. A shared processor lock and atomic queue claim prevent concurrent processing. Create/update operations retain stable IDs and immutable ordering.
- Successful responses reconcile server IDs and local state. HTTP 401 pauses authentication without consuming operation retries. Validation errors retain failed records. Conflicts preserve local/server snapshots.
- Ambiguous delivery (including interrupted in-progress uploads) requires review instead of automatically replaying an unconfirmed mutation. This is intentionally conservative until backend idempotency is confirmed.
- Updates compare the original edit snapshot with server data; conditional If-Match is sent when the server supplies an ETag. A server without conditional-update support still has a race between GET and PUT.
- Dashboard reads queue counts, failures, conflicts and last successful sync; manual sync schedules the same worker. Sheet status shows pending/synced/review state.

## Backend integration still required

1. Supply the refresh endpoint, request/response schema and token rotation rules. The current API exposes only login, so automatic JWT refresh is not implemented; HTTP 401 requires online sign-in while retaining local work.
2. Confirm server-side Idempotency-Key behavior, retention and replay response, or provide operation-status lookup. Stable client identifiers alone cannot establish exactly-once server effects. Uncertain deliveries currently stop for review.
3. Supply append-only revision/base-revision and atomic conflict semantics. Current preflight comparison cannot replace an atomic server revision check when ETags are unavailable. Queued edits are not compacted.
4. Supply an assigned-survey/master-data endpoint or confirm that GET surveys is already authorization-scoped. Login currently caches records returned by the existing endpoint; failed preload leaves only previously saved records available.
5. Provide a staging account/environment for real credential, token refresh, duplicate acceptance, attachment association and background connectivity tests. No live backend writes were used for automated verification.

## Requested twelve-case matrix

These are coverage statements, not claims that the full live workflow passed.

| Case | Automated coverage | Remaining end-to-end verification |
| --- | --- | --- |
| 1 Daily online login | Auth repository records daily verification; date policy tested | Real credentials, preload and Dashboard transition |
| 2 Same-day offline reopen | Daily policy and restored Room data | Full UI relaunch with network disabled |
| 3 Next-day offline | Midnight/expired policy and repository access rejection | Internet-required screen on device |
| 4 Offline create | Complete local snapshot, durable evidence and queue without API | Form submission and Dashboard count on device |
| 5 Offline update | Local lookup and ordered immutable updates | Full Find/Edit/Update UI |
| 6 Network returns | Mock create/update ordering and server-ID reconciliation | Real WorkManager connectivity trigger and server acceptance |
| 7 Mid-sync failure | Ambiguous timeout retains work and stops replay; safe connection errors retry | Controlled network interruption against staging |
| 8 Duplicate protection | Stable ID on safe retries; no blind uncertain replay | Backend deduplication contract and acceptance test |
| 9 Offline image | Durable file/metadata and failed-upload retention | Camera-to-server association using staging |
| 10 Process death | Database reopen and interrupted-operation recovery | Kill/restart and phone reboot UI workflow |
| 11 JWT expires offline | Daily access is independent of JWT; 401 preserves queue | Automatic refresh blocked by missing API contract |
| 12 Refresh invalid | Authentication pause retains queue and evidence | Refresh rejection/resumption blocked by missing API contract |

## Final verification results (2026-09-25)

- Final `:app:assembleDebug :app:testDebugUnitTest`: BUILD SUCCESSFUL. 119 tests, zero failures and zero errors, including the final account-bound cache-write change.
- Physical Samsung SM-G960F / Android 10: all three OfflinePersistenceTest tests passed through direct AndroidJUnitRunner instrumentation (0.866 seconds). Coverage: migration preserves draft; database reopen preserves survey, queue, evidence and metadata; rejected upload retains local evidence and failed state.
- Gradle connectedDebugAndroidTest's UTP runner failed before installing the new test package (fatal runner configuration/results collection error). Installed both APKs with adb replacement installs and ran the exact test class directly; output is saved in build/offline-device-tests.txt. Existing application data was not cleared.
- `git diff --check` passed. The user's pre-existing Dashboard XML changes were retained.
- Debug artifact: app/build/outputs/apk/debug/app-debug.apk. Installed on the connected device.
- Full UI/network/backend matrix is not certified. In particular, no production credential login, token refresh, real duplicate acceptance, atomic server revision test, camera-to-server test or device reboot scenario was run. The table above identifies the remaining work.

Background execution remains subject to Android scheduling and the existing battery-not-low constraint. Pending work is durable even when execution is deferred.
- Optional launcher smoke check did not return a completed launch; it is not counted as passed. No AndroidRuntime crash appeared in the sampled log, which is insufficient to certify UI startup.
