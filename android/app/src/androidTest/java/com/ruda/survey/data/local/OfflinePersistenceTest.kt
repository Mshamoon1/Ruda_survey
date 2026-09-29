package com.ruda.survey.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.data.repository.SurveyRepositoryImpl
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.domain.model.ImageMetadata
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.utils.TokenManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID

/** Real Android SQLite/filesystem, isolated from the application's database and APIs. */
@RunWith(AndroidJUnit4::class)
class OfflinePersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens = object : TokenManager by com.ruda.survey.demo.DemoTokenManager(context) {
        override fun isOfflineAccessAllowed() = true
        override fun getAuthenticatedUserId() = "offline-instrumentation-user"
        override fun getAccessToken() = "test-token-not-for-network"
        override fun hasTokens() = true
        override fun addUserSurveyId(id: String) {}
    }

    private fun api(reject: Boolean = false): SurveyApi = Proxy.newProxyInstance(
        SurveyApi::class.java.classLoader, arrayOf(SurveyApi::class.java)
    ) { _, method, _ ->
        if (reject && method.name == "createSurvey") retrofit2.Response.error<com.ruda.survey.data.dto.CreateSurveyResponse>(
            400, okhttp3.ResponseBody.create(null, "validation rejected"))
        else throw AssertionError("Unexpected API call: " + method.name)
    } as SurveyApi

    private fun database(name: String) = Room.databaseBuilder(context, SurveyDatabase::class.java, name)
        .addMigrations(SurveyDatabase.MIGRATION_1_2).build()

    @Test fun reopenRetainsSurveyQueueEvidenceAndMetadata() = runBlocking {
        val name = "offline-test-" + UUID.randomUUID() + ".db"
        val files = File(context.cacheDir, "offline-test-" + UUID.randomUUID())
        var db = database(name)
        try {
            val repo = SurveyRepositoryImpl(api(), tokens, db.syncDao(), db, files, { false })
            val metadata = ImageMetadata("imgOne", 31.5, 74.2, 2f, "Test area", 1234567, "door")
            val saved = repo.createSurvey(SurveyItem(srNo = 91, parcelId = "TEST91", ownerName = "Offline",
                image1Bytes = byteArrayOf(1, 2, 3), imageMetadata = listOf(metadata))).getOrThrow()
            val operation = db.syncDao().getPendingItems().single().clientUuid
            db.close()
            db = database(name)
            val reopened = SurveyRepositoryImpl(api(), tokens, db.syncDao(), db, files, { false })
            val restored = reopened.getSurveyBySrNo(91).getOrThrow()
            assertEquals(saved.id, restored.id)
            assertEquals(metadata, restored.imageMetadata.single())
            assertArrayEquals(byteArrayOf(1, 2, 3), File(restored.image1LocalPath!!).readBytes())
            assertEquals(operation, db.syncDao().getPendingItems().single().clientUuid)
            assertEquals("PENDING_CREATE", restored.syncStatus)
        } finally {
            db.close()
            context.deleteDatabase(name)
            files.deleteRecursively()
        }
    }

    @Test fun versionOneMigrationPreservesDraft() = runBlocking {
        val name = "offline-migration-test-" + UUID.randomUUID() + ".db"
        var db = database(name)
        try {
            db.surveyDao().insertDraft(DraftSurvey("preserve", 4, "{\"owner\":\"unchanged\"}"))
            db.openHelper.writableDatabase.execSQL("DROP TABLE sync_queue")
            db.openHelper.writableDatabase.execSQL("PRAGMA user_version = 1")
            db.close()
            db = database(name)
            assertEquals(4, db.surveyDao().getDraft("preserve")!!.baseRevisionNo)
            assertTrue(db.syncDao().getAllEntries().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun failedSyncNeverDeletesLocalEvidence() = runBlocking {
        val name = "offline-failure-test-" + UUID.randomUUID() + ".db"
        val files = File(context.cacheDir, "offline-failure-test-" + UUID.randomUUID())
        val db = database(name)
        try {
            val api = api(reject = true)
            val repo = SurveyRepositoryImpl(api, tokens, db.syncDao(), db, files, { false })
            val saved = repo.createSurvey(SurveyItem(srNo = 92, image1Bytes = byteArrayOf(4, 5))).getOrThrow()
            assertEquals(1, SyncRepository(api, db.syncDao(), tokens, db).processQueue().failed)
            assertEquals("FAILED", repo.getSurveyById(saved.id).getOrThrow().syncStatus)
            assertArrayEquals(byteArrayOf(4, 5), File(saved.image1LocalPath!!).readBytes())
            assertEquals(1, db.syncDao().getAllEntries().size)
            assertEquals(1, db.surveyDao().getUnuploadedImages(saved.id).size)
        } finally { db.close(); context.deleteDatabase(name); files.deleteRecursively() }
    }
}
