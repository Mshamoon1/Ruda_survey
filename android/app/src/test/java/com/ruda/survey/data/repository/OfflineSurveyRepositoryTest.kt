package com.ruda.survey.data.repository

import android.app.Application
import androidx.room.Room
import com.ruda.survey.data.local.SurveyDatabase
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.utils.TokenManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OfflineSurveyRepositoryTest {
    private lateinit var db: SurveyDatabase
    private lateinit var api: SurveyApi
    private lateinit var tokens: TokenManager
    private fun repository() = SurveyRepositoryImpl(api, tokens, db.syncDao(), db,
        RuntimeEnvironment.getApplication().filesDir, { false })

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SurveyDatabase::class.java)
            .allowMainThreadQueries().build()
        api = mock()
        tokens = mock()
        whenever(tokens.getAuthenticatedUserId()).thenReturn("user-a")
        whenever(tokens.isOfflineAccessAllowed()).thenReturn(true)
        whenever(tokens.hasTokens()).thenReturn(true)
    }
    @After fun close() { db.close() }

    @Test fun mySurveysIncludesOnlyCreatedOrUpdatedRecordsAndMapKeepsAll() = runTest {
        whenever(tokens.getUserSurveyIds()).thenReturn(emptySet())
        val repo = repository()
        repo.cache(SurveyItem(id = "untouched", srNo = 1))
        repo.cache(SurveyItem(id = "edited", srNo = 2))
        repo.updateSurvey(repo.getSurveyById("edited").getOrThrow().copy(ownerName = "Updated")).getOrThrow()
        val created = repo.createSurvey(SurveyItem(srNo = 3)).getOrThrow()
        val all = repository().getAllSurveys().getOrThrow()
        assertEquals(3, all.size)
        assertEquals(setOf("edited", created.id), repo.filterMySurveys(all).map { it.id }.toSet())
        whenever(tokens.getAuthenticatedUserId()).thenReturn("user-b")
        assertTrue(repo.filterMySurveys(all).isEmpty())
    }

    @Test fun mySurveysMembershipSurvivesServerRefresh() = runTest {
        whenever(tokens.getUserSurveyIds()).thenReturn(emptySet())
        repository().cache(SurveyItem(id = "local-1", serverId = "server-1", srNo = 1,
            workedOnByUserId = "user-a"))
        whenever(api.getAllSurveys()).thenReturn(retrofit2.Response.success(
            com.ruda.survey.data.dto.SurveyListResponse(success = true, data = listOf(
                com.ruda.survey.data.dto.SurveyItemDto(id = "server-1", sr_no = 1),
                com.ruda.survey.data.dto.SurveyItemDto(id = "other", sr_no = 2)))))
        val online = SurveyRepositoryImpl(api, tokens, db.syncDao(), db,
            RuntimeEnvironment.getApplication().filesDir, { true })
        val all = online.getAllSurveys(true).getOrThrow()
        assertEquals(2, all.size)
        assertEquals("local-1", online.filterMySurveys(all).single().id)
    }

    @Test fun backendTotalUsesResponseMetadataInsteadOfLocalSubset() = runTest {
        repository().createSurvey(SurveyItem(srNo = 1)).getOrThrow()
        whenever(api.getAllSurveys()).thenReturn(retrofit2.Response.success(
            com.ruda.survey.data.dto.SurveyListResponse(success = true, totalSurveys = 14876, count = 1,
                data = listOf(com.ruda.survey.data.dto.SurveyItemDto(id = "one", lat = 31.52, lng = 74.35)))))
        val online = SurveyRepositoryImpl(api, tokens, db.syncDao(), db,
            RuntimeEnvironment.getApplication().filesDir, { true })
        assertEquals(14876, online.getBackendSurveyTotal(true).getOrThrow())
        verify(tokens).saveBackendSurveyTotal("user-a", 14876)
        val cached = repository().getAllSurveys().getOrThrow()
        assertEquals(2, cached.size)
        assertEquals(31.52, cached.single { it.id == "one" }.lat, 0.00001)
        assertEquals("PENDING_CREATE", cached.single { it.id != "one" }.syncStatus)
    }

    @Test fun offlineTotalRetainsBackendCountInsteadOfLocalSubset() = runTest {
        repository().createSurvey(SurveyItem(srNo = 1)).getOrThrow()
        whenever(tokens.getBackendSurveyTotal("user-a")).thenReturn(14876)
        assertEquals(14876, repository().getBackendSurveyTotal(true).getOrThrow())
        verifyNoInteractions(api)
    }

    @Test fun cachedSurveySurvivesRepositoryRecreationWithoutNetwork() = runTest {
        repository().cache(SurveyItem(id = "server-1", srNo = 12, parcelId = "P12", khasraNo = "K12"))
        val restored = repository().getSurveyBySrNo(12).getOrThrow()
        assertEquals("K12", restored.khasraNo)
        assertEquals("server-1", repository().getAllSurveys().getOrThrow().single().id)
        verifyNoInteractions(api)
    }
    @Test fun anotherAccountCannotReadCachedSurveys() = runTest {
        repository().cache(SurveyItem(id = "server-1", srNo = 12))
        whenever(tokens.getAuthenticatedUserId()).thenReturn("user-b")
        assertTrue(repository().getAllSurveys().getOrThrow().isEmpty())
        assertTrue(repository().getSurveyBySrNo(12).isFailure)
    }
    @Test fun expiredDailySessionCannotReadSurveyCache() = runTest {
        repository().cache(SurveyItem(id = "server-1", srNo = 12))
        whenever(tokens.isOfflineAccessAllowed()).thenReturn(false)
        assertTrue(repository().getSurveyBySrNo(12).isFailure)
        verifyNoInteractions(api)
    }
    @Test fun createCommitsCompleteSnapshotAndQueueWithoutNetwork() = runTest {
        val saved = repository().createSurvey(SurveyItem(srNo = 19, parcelId = "P19",
            ownerName = "Local owner", image1Bytes = byteArrayOf(1, 2, 3))).getOrThrow()
        assertEquals("PENDING_CREATE", saved.syncStatus)
        assertArrayEquals(byteArrayOf(1, 2, 3), java.io.File(saved.image1LocalPath!!).readBytes())
        assertNull(saved.image1Bytes)
        assertEquals(saved, repository().getSurveyById(saved.id).getOrThrow())
        val queued = db.syncDao().getPendingItems().single()
        assertEquals(saved.id, queued.parcelCode)
        assertTrue(queued.dataJson.contains("Local owner"))
        assertFalse(queued.dataJson.contains("image1Bytes"))
        verifyNoInteractions(api)
    }
    @Test fun createThenUpdatePreservesOrderedImmutableOperations() = runTest {
        val original = repository().createSurvey(SurveyItem(srNo = 20, parcelId = "P20", khasraNo = "K20")).getOrThrow()
        val edited = repository().updateSurvey(original.copy(ownerName = "Edited")).getOrThrow()
        assertEquals("PENDING_UPDATE", edited.syncStatus)
        assertEquals("Edited", repository().findSurvey("K20").getOrThrow().ownerName)
        assertEquals(edited.id, repository().findSurvey("P20").getOrThrow().id)
        val queue = db.syncDao().getPendingItems()
        assertEquals(2, queue.size)
        assertNotEquals(queue[0].clientUuid, queue[1].clientUuid)
        assertFalse(queue[0].dataJson.contains("Edited"))
        assertTrue(queue[1].dataJson.contains("Edited"))
        verifyNoInteractions(api)
    }
    @Test fun updateWithoutLocalRecordIsRejected() = runTest {
        assertTrue(repository().updateSurvey(SurveyItem(id = "missing", srNo = 2)).isFailure)
        assertTrue(db.syncDao().getPendingItems().isEmpty())
        verifyNoInteractions(api)
    }

    @Test fun successfulSyncReconcilesRoomAndUsesStableOperationKey() = runTest {
        val keys = mutableListOf<String>()
        api = org.mockito.Mockito.mock(SurveyApi::class.java) { call ->
            if (call.method.name == "createSurvey") {
                keys.add(call.getArgument(23))
                retrofit2.Response.success(com.ruda.survey.data.dto.CreateSurveyResponse(true, "ok",
                    com.ruda.survey.data.dto.SurveyItemDto(id = "server-42", sr_no = 42)))
            } else org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
        }
        whenever(tokens.getAccessToken()).thenReturn("access")
        val saved = repository().createSurvey(SurveyItem(srNo = 42)).getOrThrow()
        val operation = db.syncDao().getPendingItems().single()
        val result = com.ruda.survey.data.sync.SyncRepository(api, db.syncDao(), tokens, db).processQueue()
        assertEquals(1, result.synced)
        assertEquals(listOf(operation.clientUuid), keys)
        val local = repository().getSurveyById(saved.id).getOrThrow()
        assertEquals("server-42", local.serverId)
        assertEquals("SYNCED", local.syncStatus)
        assertTrue(db.syncDao().getPendingItems().isEmpty())
    }

    @Test fun uncertainDeliveryIsPreservedAndNeverBlindlyPostedAgain() = runTest {
        var calls = 0
        api = org.mockito.Mockito.mock(SurveyApi::class.java) { call ->
            if (call.method.name == "createSurvey") { calls++; throw java.net.SocketTimeoutException("response lost") }
            else org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
        }
        whenever(tokens.getAccessToken()).thenReturn("access")
        val saved = repository().createSurvey(SurveyItem(srNo = 43)).getOrThrow()
        val sync = com.ruda.survey.data.sync.SyncRepository(api, db.syncDao(), tokens, db)
        assertEquals(1, sync.processQueue().conflicts)
        sync.processQueue()
        assertEquals(1, calls)
        assertEquals("CONFLICT", repository().getSurveyById(saved.id).getOrThrow().syncStatus)
        assertEquals("DELIVERY_UNCONFIRMED", db.syncDao().getAllEntries().single().errorCode)
    }

    @Test fun draftRestoresEvidenceAndMetadataWithoutPuttingBytesInRoom() = runTest {
        val metadata = com.ruda.survey.domain.model.ImageMetadata("imgOne", 31.5, 74.2, 2f, "Area", 1234, "door")
        val draft = repository().saveDraft(SurveyItem(srNo = 51, image1Bytes = byteArrayOf(7, 8),
            landOwnerDocBytes = byteArrayOf(9), landOwnerDocName = "deed.pdf", imageMetadata = listOf(metadata))).getOrThrow()
        val restored = repository().getDraft()!!
        assertEquals(draft.clientUuid, restored.clientUuid)
        assertEquals(metadata, restored.imageMetadata.single())
        assertNull(restored.image1Bytes)
        assertArrayEquals(byteArrayOf(7, 8), java.io.File(restored.image1LocalPath!!).readBytes())
        assertArrayEquals(byteArrayOf(9), java.io.File(restored.documentLocalPath!!).readBytes())
        assertTrue(db.syncDao().getAllEntries().isEmpty())
        val saved = repository().createSurvey(restored).getOrThrow()
        repository().createSurvey(restored).getOrThrow()
        assertEquals(1, db.syncDao().getAllEntries().size)
        assertEquals("local_" + draft.clientUuid, saved.id)
        assertNull(repository().getDraft())
    }

    @Test fun authenticationRejectionPausesWithoutConsumingRetryOrDeletingWork() = runTest {
        api = org.mockito.Mockito.mock(SurveyApi::class.java) { call ->
            if (call.method.name == "createSurvey") retrofit2.Response.error<com.ruda.survey.data.dto.CreateSurveyResponse>(
                401, okhttp3.ResponseBody.create(null, "expired"))
            else org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
        }
        whenever(tokens.getAccessToken()).thenReturn("expired-access")
        val saved = repository().createSurvey(SurveyItem(srNo = 60)).getOrThrow()
        repository().createSurvey(SurveyItem(srNo = 61)).getOrThrow()
        val result = com.ruda.survey.data.sync.SyncRepository(api, db.syncDao(), tokens, db).processQueue()
        assertTrue(result.authenticationRequired)
        verify(tokens).requireOnlineAuthentication()
        assertEquals(2, db.syncDao().getPendingItems().size)
        assertTrue(db.syncDao().getPendingItems().all { it.retryCount == 0 && it.status == "PENDING" })
        assertEquals(saved.id, repository().getSurveyById(saved.id).getOrThrow().id)
    }

    @Test fun anotherUserCannotUploadPendingWork() = runTest {
        whenever(tokens.getAccessToken()).thenReturn("access")
        repository().createSurvey(SurveyItem(srNo = 62)).getOrThrow()
        whenever(tokens.getAuthenticatedUserId()).thenReturn("user-b")
        com.ruda.survey.data.sync.SyncRepository(api, db.syncDao(), tokens, db).processQueue()
        verifyNoInteractions(api)
        assertEquals("PENDING", db.syncDao().getPendingItems().single().status)
    }

    @Test fun interruptedUploadIsRecoveredToReviewWithEvidenceIntact() = runTest {
        whenever(tokens.getAccessToken()).thenReturn("access")
        val saved = repository().createSurvey(SurveyItem(srNo = 63, image1Bytes = byteArrayOf(1))).getOrThrow()
        val entry = db.syncDao().getPendingItems().single()
        db.syncDao().markInProgress(entry.id)
        val result = com.ruda.survey.data.sync.SyncRepository(api, db.syncDao(), tokens, db).processQueue()
        assertEquals(1, result.conflicts)
        assertTrue(java.io.File(saved.image1LocalPath!!).exists())
        assertEquals("CONFLICT", repository().getSurveyById(saved.id).getOrThrow().syncStatus)
        verifyNoInteractions(api)
    }

    @Test fun changedServerRecordBecomesConflictWithoutPut() = runTest {
        whenever(tokens.getAccessToken()).thenReturn("access")
        val base = SurveyItem(id = "server-64", serverId = "server-64", srNo = 64, ownerName = "Original")
        repository().cache(base)
        repository().updateSurvey(base.copy(ownerName = "My offline edit")).getOrThrow()
        whenever(api.getSurveyById("server-64")).thenReturn(retrofit2.Response.success(
            com.ruda.survey.data.dto.SurveyDataWrapper(true, "ok", mapOf("_id" to "server-64", "sr_no" to 64,
                "identification" to mapOf("owner_name" to "Another surveyor's edit")))))
        val result = com.ruda.survey.data.sync.SyncRepository(api, db.syncDao(), tokens, db).processQueue()
        assertEquals(1, result.conflicts)
        assertEquals("My offline edit", repository().getSurveyById(base.id).getOrThrow().ownerName)
        verify(api).getSurveyById("server-64")
        verifyNoMoreInteractions(api)
        assertTrue(db.syncDao().getAllEntries().single().lastError!!.contains("Another surveyor"))
    }

    @Test fun createAlwaysPrecedesUpdateAndServerIdIsReconciled() = runTest {
        val calls = mutableListOf<String>()
        api = org.mockito.Mockito.mock(SurveyApi::class.java) { call ->
            when (call.method.name) {
                "createSurvey" -> {
                    calls.add("POST")
                    retrofit2.Response.success(com.ruda.survey.data.dto.CreateSurveyResponse(true, "ok",
                        com.ruda.survey.data.dto.SurveyItemDto(id = "server-70", sr_no = 70)))
                }
                "getSurveyById" -> {
                    calls.add("GET:" + call.getArgument<String>(0))
                    retrofit2.Response.success(com.ruda.survey.data.dto.SurveyDataWrapper(true, "ok",
                        mapOf("_id" to "server-70", "sr_no" to 70)))
                }
                "updateSurvey" -> {
                    calls.add("PUT:" + call.getArgument<String>(0))
                    retrofit2.Response.success(com.ruda.survey.data.dto.CreateSurveyResponse(true, "ok",
                        com.ruda.survey.data.dto.SurveyItemDto(id = "server-70", sr_no = 70,
                            identification = com.ruda.survey.data.dto.IdentificationDto("Edited", null, null, null, null, null, null, null))))
                }
                else -> org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
            }
        }
        whenever(tokens.getAccessToken()).thenReturn("access")
        val saved = repository().createSurvey(SurveyItem(srNo = 70)).getOrThrow()
        repository().updateSurvey(saved.copy(ownerName = "Edited")).getOrThrow()
        val result = com.ruda.survey.data.sync.SyncRepository(api, db.syncDao(), tokens, db).processQueue()
        assertEquals(2, result.synced)
        assertEquals(listOf("POST", "GET:server-70", "PUT:server-70"), calls)
        assertEquals("Edited", repository().getSurveyById(saved.id).getOrThrow().ownerName)
        assertEquals("SYNCED", repository().getSurveyById(saved.id).getOrThrow().syncStatus)
    }

    @Test fun safeConnectionRetryKeepsSameOperationIdentifier() = runTest {
        val keys = mutableListOf<String>()
        api = org.mockito.Mockito.mock(SurveyApi::class.java) { call ->
            if (call.method.name == "createSurvey") {
                keys.add(call.getArgument(23))
                if (keys.size == 1) throw java.net.UnknownHostException("offline")
                retrofit2.Response.success(com.ruda.survey.data.dto.CreateSurveyResponse(true, "ok",
                    com.ruda.survey.data.dto.SurveyItemDto(id = "server-71", sr_no = 71)))
            } else org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
        }
        whenever(tokens.getAccessToken()).thenReturn("access")
        repository().createSurvey(SurveyItem(srNo = 71)).getOrThrow()
        val sync = com.ruda.survey.data.sync.SyncRepository(api, db.syncDao(), tokens, db)
        assertTrue(sync.processQueue().retryNeeded)
        val pending = db.syncDao().getPendingItems().single()
        assertEquals(1, pending.retryCount)
        db.syncDao().resetForRetry(pending.id)
        assertEquals(1, sync.processQueue().synced)
        assertEquals(listOf(pending.clientUuid, pending.clientUuid), keys)
    }
}
