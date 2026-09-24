package com.ruda.survey.ui.survey

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ruda.survey.R
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateSessionTest {
    private val tokenCache = com.ruda.survey.data.remote.RepositoryFactory::class.java
        .getDeclaredField("cachedTokenManager").apply { isAccessible = true }
    private var originalTokenManager: com.ruda.survey.utils.TokenManager? = null

    @org.junit.Before fun isolateSubmissionCounters() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val real = com.ruda.survey.data.remote.RepositoryFactory.getTokenManager(context)
        originalTokenManager = real
        tokenCache.set(null, object : com.ruda.survey.utils.TokenManager by real {
            override fun incrementNewSurveyCount() {}
            override fun addUserSurveyId(id: String) {}
        })
    }

    @org.junit.After fun restoreTokenManager() { tokenCache.set(null, originalTokenManager) }

    @Test fun lateLookupCannotRestoreACompletedSession() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val sample = SurveyItem(id = "late", srNo = 14875)
        val backing = MemoryRepository(sample)
        val repo = object : SurveyRepository by backing {
            override suspend fun getSurveyBySrNo(srNo: Int): Result<SurveyItem> {
                entered.countDown()
                try {
                    check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    return Result.success(sample)
                } finally { finished.countDown() }
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var model: SurveyViewModel
        try {
            instrumentation.runOnMainSync {
                model = SurveyViewModel(repo)
                model.lookupBySrNo("14875")
            }
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync { model.startUpdateSession() }
        } finally { release.countDown() }
        assertTrue(finished.await(10, java.util.concurrent.TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertTrue(model.srNoLookupState.value is UiState.Empty)
            assertNull(model.currentSurvey)
        }
    }

    @Test fun consecutiveUpdatesAndBackNavigationStartClean() {
        val repo = MemoryRepository(SurveyItem(id = "session-test", srNo = 14875, parcelId = "parcel",
            ownerName = "Owner", village = "Village", lat = 31.5, lng = 74.2, pkg = "1",
            cnic = "3520212345671", phone = "03001234567", khasraNo = "124/3",
            status = "residential", natureOfConstruction = "pacca"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { model(it, repo); nav(it).navigate(R.id.dashboardFragment) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "dashboard-logo.png")
                .outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            for (serial in listOf(14875, 14876)) {
                repo.saved = repo.saved.copy(id = "session-$serial", srNo = serial)
                onView(withId(R.id.btnNewSurvey)).perform(scrollTo(), click())
                await { scenario.onActivity {
                    assertEquals(R.id.newSurveyFragment, nav(it).currentDestination?.id)
                    assertEquals(android.view.View.GONE, it.findViewById<android.view.View>(R.id.cardResult)?.visibility)
                    assertNull(model(it, repo).currentSurvey)
                    assertEquals("", it.findViewById<android.widget.EditText>(R.id.etSerialNumber).text.toString())
                } }
                onView(withId(R.id.etSerialNumber)).perform(replaceText(serial.toString()), closeSoftKeyboard())
                onView(withId(R.id.btnSerialLookup)).perform(click())
                await { scenario.onActivity { assertTrue(model(it, repo).srNoLookupState.value is UiState.Success) } }
                scenario.recreate()
                scenario.onActivity { assertEquals(serial, model(it, repo).currentSurvey?.srNo) }
                onView(withId(R.id.btnViewOriginal)).perform(scrollTo(), click())
                await { scenario.onActivity { assertEquals(R.id.surveyFormFragment, nav(it).currentDestination?.id) } }
                scenario.onActivity {
                    val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
                    val bytes = java.io.ByteArrayOutputStream().also { output ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                    }.toByteArray()
                    bitmap.recycle()
                    model(it, repo).queueGpsImage(com.ruda.survey.domain.model.PendingImage(
                        "imgOne", bytes, bytes, "test.png", 31.5, 74.2, null, null, 0L, null))
                }
                onView(withId(R.id.etOwnerName)).perform(scrollTo(), replaceText("Edited $serial"), closeSoftKeyboard())
                onView(withId(R.id.btnReviewChanges)).perform(click())
                await { scenario.onActivity { assertEquals(R.id.reviewFragment, nav(it).currentDestination?.id) } }
                onView(withId(R.id.btnSubmit)).perform(scrollTo(), click())
                await { scenario.onActivity {
                    assertEquals("Edited $serial", repo.saved.ownerName)
                    assertTrue(model(it, repo).srNoLookupState.value is UiState.Empty)
                    assertEquals(serial, model(it, repo).currentSurvey?.srNo)
                } }
                await { scenario.onActivity { assertEquals(R.id.sheetFragment, nav(it).currentDestination?.id) } }
                onView(withId(R.id.btnDone)).perform(scrollTo(), click())
                await { scenario.onActivity { assertEquals(R.id.dashboardFragment, nav(it).currentDestination?.id) } }
            }
            onView(withId(R.id.btnNewSurvey)).perform(scrollTo(), click())
            await { scenario.onActivity {
                assertEquals(R.id.newSurveyFragment, nav(it).currentDestination?.id)
                assertTrue(model(it, repo).srNoLookupState.value is UiState.Empty)
            } }
            onView(withId(R.id.etSerialNumber)).perform(replaceText("14876"), closeSoftKeyboard())
            onView(withId(R.id.btnSerialLookup)).perform(click())
            await { scenario.onActivity { assertTrue(model(it, repo).srNoLookupState.value is UiState.Success) } }
            pressBack()
            onView(withId(R.id.btnNewSurvey)).perform(scrollTo(), click())
            await { scenario.onActivity {
                assertEquals(R.id.newSurveyFragment, nav(it).currentDestination?.id)
                assertTrue(model(it, repo).srNoLookupState.value is UiState.Empty)
                assertEquals(android.view.View.GONE, it.findViewById<android.view.View>(R.id.cardResult)?.visibility)
            } }
        }
    }
    private fun model(activity: MainActivity, repo: SurveyRepository) =
        ViewModelProvider(activity, SurveyViewModelFactory(repo))[SurveyViewModel::class.java]
    private fun nav(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

    private fun await(assertion: () -> Unit) {
        val deadline = System.currentTimeMillis() + 10000
        while (true) {
            try { assertion(); return } catch (error: AssertionError) {
                if (System.currentTimeMillis() >= deadline) throw error
                Thread.sleep(50)
            }
        }
    }

    /** Exercise the real form, selection mapping and save path without contacting a server. */
    private class MemoryRepository(@Volatile var saved: SurveyItem) : SurveyRepository {
        override suspend fun getAllSurveys(forceRefresh: Boolean) = Result.success(listOf(saved))
        override suspend fun getSurveyById(id: String) = Result.success(saved)
        override suspend fun getSurveyBySrNo(srNo: Int) = Result.success(saved)
        override suspend fun createSurvey(item: SurveyItem) = Result.success(item).also { saved = item }
        override suspend fun updateSurvey(item: SurveyItem) = Result.success(item).also { saved = item }
        override suspend fun deleteSurvey(id: String) = Result.success(Unit)
        override fun getAuthToken(): String? = null
        override fun isLoggedIn() = true
        override fun saveSurveyId(id: String) {}
        override fun getSurveyId(): String? = saved.id
        override fun clearSurveyId() {}
    }
}
