package com.ruda.survey.ui.survey

import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ruda.survey.R
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.ui.MainActivity
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SurveyDropdownTest {
    private val statuses = listOf("RESIDENTIAL", "COMMERCIAL", "CATTLE FARM", "AGRICULTURAL",
        "EMPTY PLOT", "UNDER CONSTRUCTION", "AGRI", "DERAS", "OTHER")
    private val natures = listOf("PACCA", "SEMI-PACCA", "KACHA")
    private val sample = SurveyItem(id = "dropdown-test", srNo = 14875, parcelId = "parcel-1", pkg = "1",
        ownerName = "Original Owner", cnic = "35202-1234567-1", phone = "0300123456",
        village = "Village", khasraNo = "124/3", lat = 31.5, lng = 74.2,
        status = "residential", natureOfConstruction = "semi-pacca")

    @Test fun fixedChoicesIgnoreFrameworkFilterRequestsThatNarrowArrayAdapter() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var original: ArrayAdapter<String>
        lateinit var fixed: SurveyOptionsAdapter
        instrumentation.runOnMainSync {
            original = ArrayAdapter(instrumentation.targetContext, android.R.layout.simple_list_item_1, statuses)
            fixed = SurveyOptionsAdapter(instrumentation.targetContext, statuses)
        }
        for (query in listOf("RESIDENTIAL", "UNDER CONSTRUCTION", "not in the list", "", "COMMERCIAL")) {
            filter(original, query)
            filter(fixed, query)
            instrumentation.runOnMainSync {
                if (query == "RESIDENTIAL") assertEquals(1, original.count)
                assertEquals(statuses, (0 until fixed.count).map { fixed.getItem(it) })
            }
        }
    }

    @Test fun editLookupUpperFieldChangesReselectionAndSavedValuesKeepFullLists() {
        val repo = MemoryRepository(sample)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                model(activity, repo)
                nav(activity).navigate(R.id.newSurveyFragment)
            }
            onView(withId(R.id.etSerialNumber)).perform(replaceText("14875"), closeSoftKeyboard())
            onView(withId(R.id.btnSerialLookup)).perform(click())
            await { scenario.onActivity { assertTrue(model(it, repo).srNoLookupState.value is UiState.Success) } }
            onView(withId(R.id.btnViewOriginal)).perform(scrollTo(), click())
            onView(withId(R.id.etOwnerName)).perform(scrollTo(), replaceText("Edited Owner"), closeSoftKeyboard())
            onView(withId(R.id.etPackageNo)).perform(scrollTo(), replaceText("Updated package"), closeSoftKeyboard())
            choose(scenario, R.id.etStructureStatus, statuses, "COMMERCIAL")
            choose(scenario, R.id.etConstructionNature, natures, "KACHA")
            // The same restoration path that may trigger AutoCompleteTextView filtering.
            scenario.recreate()
            openAndCheck(scenario, R.id.etStructureStatus, statuses)
            pressBack()
            openAndCheck(scenario, R.id.etConstructionNature, natures)
            pressBack()
            onView(withId(R.id.btnSaveDraft)).perform(click())
            await {
                assertEquals("commercial", repo.saved.status)
                assertEquals("kacha", repo.saved.natureOfConstruction)
                assertEquals("Edited Owner", repo.saved.ownerName)
                assertEquals("Updated package", repo.saved.pkg)
            }
            scenario.onActivity { activity ->
                nav(activity).popBackStack()
                model(activity, repo).saveFormState(repo.saved)
                nav(activity).navigate(R.id.surveyFormFragment)
            }
            scenario.onActivity { activity ->
                assertEquals("COMMERCIAL", activity.findViewById<AutoCompleteTextView>(R.id.etStructureStatus).text.toString())
                assertEquals("KACHA", activity.findViewById<AutoCompleteTextView>(R.id.etConstructionNature).text.toString())
            }
            openAndCheck(scenario, R.id.etStructureStatus, statuses)
            pressBack()
            openAndCheck(scenario, R.id.etConstructionNature, natures)
            pressBack()
        }
    }

    @Test fun newSurveyCanSelectAndReopenBothCompleteLists() {
        val repo = MemoryRepository(sample)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Nonzero location avoids requesting GPS in this isolated dropdown test.
                model(activity, repo).saveFormState(SurveyItem(lat = 31.5, lng = 74.2))
                nav(activity).navigate(R.id.surveyFormFragment)
            }
            choose(scenario, R.id.etStructureStatus, statuses, "UNDER CONSTRUCTION")
            choose(scenario, R.id.etConstructionNature, natures, "SEMI-PACCA")
        }
    }

    @Test fun unfamiliarServerValuesRemainVisibleWithoutBecomingOptions() {
        val repo = MemoryRepository(sample)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                model(activity, repo).saveFormState(sample.copy(status = "Constructed", natureOfConstruction = "Legacy material"))
                nav(activity).navigate(R.id.surveyFormFragment)
            }
            scenario.onActivity { activity ->
                assertEquals("Constructed", activity.findViewById<AutoCompleteTextView>(R.id.etStructureStatus).text.toString())
                assertEquals("Legacy material", activity.findViewById<AutoCompleteTextView>(R.id.etConstructionNature).text.toString())
            }
            openAndCheck(scenario, R.id.etStructureStatus, statuses)
            pressBack()
            openAndCheck(scenario, R.id.etConstructionNature, natures)
            pressBack()
        }
    }

    private fun choose(scenario: ActivityScenario<MainActivity>, id: Int, options: List<String>, choice: String) {
        openAndCheck(scenario, id, options)
        onData(equalTo(choice)).inRoot(isPlatformPopup()).perform(click())
        scenario.onActivity { assertEquals(choice, it.findViewById<AutoCompleteTextView>(id).text.toString()) }
        openAndCheck(scenario, id, options)
        pressBack()
    }

    private fun openAndCheck(scenario: ActivityScenario<MainActivity>, id: Int, options: List<String>) {
        onView(withId(id)).perform(scrollTo(), click())
        scenario.onActivity { activity ->
            val field = activity.findViewById<AutoCompleteTextView>(id)
            assertTrue("Dropdown should open when the field is tapped", field.isPopupShowing)
            assertEquals(options, (0 until field.adapter.count).map { field.adapter.getItem(it) })
        }
    }

    private fun filter(adapter: ArrayAdapter<String>, query: String) {
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { adapter.filter.filter(query) { done.countDown() } }
        assertTrue("Filter should finish", done.await(5, TimeUnit.SECONDS))
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
