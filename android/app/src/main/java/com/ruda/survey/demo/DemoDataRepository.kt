package com.ruda.survey.demo

import android.content.Context
import com.ruda.survey.domain.model.*
import com.ruda.survey.domain.repository.SurveyRepository

class DemoDataRepository(private val context: Context) : SurveyRepository {

    private val prefs by lazy {
        context.getSharedPreferences("demo_session", Context.MODE_PRIVATE)
    }

    private val demoSurveys = mutableListOf<SurveyItem>()

    init {
        if (demoSurveys.isEmpty()) {
            demoSurveys.addAll(listOf(
                SurveyItem(id = "demo1", srNo = 1, village = "Arya Nagar", ownerName = "Rana Bashir", fName = "Muhammad Buksh", structuralName = "House", natureOfConstruction = "pacca", lat = 31.703, lng = 74.415, length = "27.1", width = "31.7"),
                SurveyItem(id = "demo2", srNo = 2, village = "Ratini Wal", ownerName = "Mr. Amjad Zaheer", fName = "Shabir Hussain", structuralName = "Electric meter", natureOfConstruction = "0", lat = 31.611, lng = 74.291),
                SurveyItem(id = "demo3", srNo = 3, village = "Mralpaar", ownerName = "Mr. Asif Ali", fName = "Ghulam Shabir", structuralName = "House", natureOfConstruction = "pacca", lat = 31.611, lng = 74.291, length = "30", width = "30"),
                SurveyItem(id = "demo4", srNo = 4, village = "Mustafabad", ownerName = "Mr. M. Asif", fName = "M. Nazir", structuralName = "Bathroom", natureOfConstruction = "pacca", lat = 31.615, lng = 74.298, length = "3", width = "3", area = "9"),
                SurveyItem(id = "demo5", srNo = 5, village = "Gulshan Hayat Park", ownerName = "Mr. Noor Hassan", fName = "Ali Muhammad", structuralName = "House", natureOfConstruction = "pacca", lat = 31.611, lng = 74.291, length = "40", width = "30"),
            ))
        }
    }

    override suspend fun getAllSurveys(): Result<List<SurveyItem>> {
        return Result.success(demoSurveys.toList())
    }

    override suspend fun getSurveyById(id: String): Result<SurveyItem> {
        val survey = demoSurveys.find { it.id == id }
        return if (survey != null) Result.success(survey)
        else Result.failure(NoSuchElementException("Survey not found"))
    }

    override suspend fun getSurveyBySrNo(srNo: Int): Result<SurveyItem> {
        val survey = demoSurveys.find { it.srNo == srNo }
        return if (survey != null) Result.success(survey)
        else Result.failure(NoSuchElementException("Survey not found for sr_no: $srNo"))
    }

    override suspend fun createSurvey(item: SurveyItem): Result<SurveyItem> {
        val newId = "demo${demoSurveys.size + 1}"
        val saved = item.copy(id = newId)
        demoSurveys.add(saved)
        return Result.success(saved)
    }

    override suspend fun updateSurvey(item: SurveyItem): Result<SurveyItem> {
        val index = demoSurveys.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            demoSurveys[index] = item
            return Result.success(item)
        }
        return Result.failure(NoSuchElementException("Survey not found"))
    }

    override suspend fun deleteSurvey(id: String): Result<Unit> {
        demoSurveys.removeAll { it.id == id }
        return Result.success(Unit)
    }

    override fun getAuthToken(): String? {
        return if (isLoggedIn()) "demo-token" else null
    }

    override fun isLoggedIn(): Boolean {
        return prefs.getBoolean("logged_in", false)
    }

    override fun saveSurveyId(id: String) {
        prefs.edit().putString("last_survey_id", id).apply()
    }

    override fun getSurveyId(): String? = prefs.getString("last_survey_id", null)

    override fun clearSurveyId() {
        prefs.edit().remove("last_survey_id").apply()
    }
}
