package com.ruda.survey.ui.survey

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.util.SparseArray
import android.os.Parcelable
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentSurveyFormBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.repository.SurveyRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SurveyDropdownStateTest {
    private lateinit var controller: ActivityController<DropdownHostActivity>
    private lateinit var binding: FragmentSurveyFormBinding
    private lateinit var repo: MemoryRepository
    private val cache = RepositoryFactory::class.java.getDeclaredField("cachedSurveyRepo").apply { isAccessible = true }
    private var previousRepository: Any? = null
    private val statuses = listOf("RESIDENTIAL", "COMMERCIAL", "CATTLE FARM", "AGRICULTURAL", "EMPTY PLOT",
        "UNDER CONSTRUCTION", "AGRI", "DERAS", "OTHER")
    private val natures = listOf("PACCA", "SEMI-PACCA", "KACHA")
    private val sample = SurveyItem(id = "test", srNo = 14875, parcelId = "parcel", pkg = "1",
        ownerName = "Owner", status = "residential", natureOfConstruction = "semi-pacca", lat = 31.5, lng = 74.2)

    @Before fun rememberRepository() { previousRepository = cache.get(null) }
    @After fun tearDown() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
        cache.set(null, previousRepository)
    }

    private fun open(survey: SurveyItem) {
        repo = MemoryRepository(survey)
        cache.set(null, repo)
        controller = Robolectric.buildActivity(DropdownHostActivity::class.java).setup().visible()
        val activity = controller.get()
        ViewModelProvider(activity, SurveyViewModelFactory(repo))[SurveyViewModel::class.java].saveFormState(survey)
        val fragment = SurveyFormFragment()
        activity.supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).commitNow()
        binding = FragmentSurveyFormBinding.bind(fragment.requireView())
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun originalArrayAdapterFiltersButFixedOptionsStayComplete() {
        open(sample)
        val original = ArrayAdapter(controller.get(), android.R.layout.simple_list_item_1, statuses)
        filter(original, "RESIDENTIAL")
        assertEquals(1, original.count)
        for (query in listOf("RESIDENTIAL", "UNDER CONSTRUCTION", "not an option", "")) {
            filter(binding.etStructureStatus.adapter as ArrayAdapter<*>, query)
            assertOptions(binding.etStructureStatus, statuses)
        }
    }

    @Test fun editingUpperFieldsAndRestoringSelectionsDoesNotNarrowEitherDropdown() {
        open(sample)
        assertEquals("RESIDENTIAL", binding.etStructureStatus.text.toString())
        assertEquals("SEMI-PACCA", binding.etConstructionNature.text.toString())
        binding.etOwnerName.setText("Edited Owner")
        binding.etPackageNo.setText("Edited Package")
        selectAndReopen(binding.etStructureStatus, statuses, "COMMERCIAL")
        selectAndReopen(binding.etConstructionNature, natures, "KACHA")
        val state = SparseArray<Parcelable>()
        binding.root.saveHierarchyState(state)
        binding.root.restoreHierarchyState(state)
        filter(binding.etStructureStatus.adapter as ArrayAdapter<*>, "COMMERCIAL")
        filter(binding.etConstructionNature.adapter as ArrayAdapter<*>, "KACHA")
        assertOptions(binding.etStructureStatus, statuses)
        assertOptions(binding.etConstructionNature, natures)
        assertEquals("Edited Owner", binding.etOwnerName.text.toString())
        assertEquals("Edited Package", binding.etPackageNo.text.toString())
        controller.recreate()
        val restored = controller.get().supportFragmentManager.findFragmentById(android.R.id.content)!!
        binding = FragmentSurveyFormBinding.bind(restored.requireView())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("COMMERCIAL", binding.etStructureStatus.text.toString())
        assertEquals("KACHA", binding.etConstructionNature.text.toString())
        assertOptions(binding.etStructureStatus, statuses)
        assertOptions(binding.etConstructionNature, natures)
        // Existing save code reads the selected canonical backend values.
        binding.btnSaveDraft.performClick()
        await { repo.saved.status == "commercial" && repo.saved.natureOfConstruction == "kacha" }
        assertEquals("Edited Owner", repo.saved.ownerName)
        assertEquals("Edited Package", repo.saved.pkg)
        val freshForm = SurveyFormFragment()
        controller.get().supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, freshForm).commitNow()
        binding = FragmentSurveyFormBinding.bind(freshForm.requireView())
        assertEquals("COMMERCIAL", binding.etStructureStatus.text.toString())
        assertEquals("KACHA", binding.etConstructionNature.text.toString())
        assertOptions(binding.etStructureStatus, statuses)
        assertOptions(binding.etConstructionNature, natures)
    }

    @Test fun newFormAllowsSelectingAndReopeningEveryOption() {
        open(SurveyItem(lat = 31.5, lng = 74.2))
        assertEquals("", binding.etStructureStatus.text.toString())
        assertEquals("", binding.etConstructionNature.text.toString())
        statuses.forEach { selectAndReopen(binding.etStructureStatus, statuses, it) }
        natures.forEach { selectAndReopen(binding.etConstructionNature, natures, it) }
    }

    @Test fun unknownServerSelectionsStayVisibleButDoNotReplacePredefinedOptions() {
        open(sample.copy(status = "Constructed", natureOfConstruction = "Legacy material"))
        assertEquals("Constructed", binding.etStructureStatus.text.toString())
        assertEquals("Legacy material", binding.etConstructionNature.text.toString())
        assertOptions(binding.etStructureStatus, statuses)
        assertOptions(binding.etConstructionNature, natures)
    }

    private fun selectAndReopen(field: AutoCompleteTextView, options: List<String>, choice: String) {
        field.setText(choice, false)
        field.onItemClickListener.onItemClick(null, null, options.indexOf(choice), 0)
        filter(field.adapter as ArrayAdapter<*>, choice)
        field.showDropDown()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(field.isPopupShowing)
        assertOptions(field, options)
        field.dismissDropDown()
    }
    private fun assertOptions(field: AutoCompleteTextView, expected: List<String>) {
        assertEquals(expected, (0 until field.adapter.count).map { field.adapter.getItem(it) })
    }
    private fun filter(adapter: ArrayAdapter<*>, text: String) {
        val done = AtomicBoolean(false)
        adapter.filter.filter(text) { done.set(true) }
        await { done.get() }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            Thread.sleep(10)
        }
        assertTrue("Asynchronous operation should complete", condition())
    }

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

class DropdownHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_RudaSurvey)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = android.R.id.content })
    }
}
