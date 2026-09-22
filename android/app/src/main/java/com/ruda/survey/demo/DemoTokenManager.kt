package com.ruda.survey.demo

import android.content.Context
import com.ruda.survey.utils.TokenManager

class DemoTokenManager(context: Context) : TokenManager {

    private val prefs = context.getSharedPreferences("demo_prefs", Context.MODE_PRIVATE)

    override fun saveTokens(access: String, refresh: String) {
        prefs.edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .apply()
    }

    override fun getAccessToken(): String? = prefs.getString("access_token", null)

    override fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    override fun clearTokens() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }

    override fun hasTokens(): Boolean = getAccessToken() != null

    override fun saveSurveyId(id: String) {
        prefs.edit().putString("last_survey_id", id).apply()
    }

    override fun getSurveyId(): String? = prefs.getString("last_survey_id", null)

    override fun clearSurveyId() {
        prefs.edit().remove("last_survey_id").apply()
    }

    override fun addUserSurveyId(id: String) {
        val current = prefs.getStringSet("owned_survey_ids_v2", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id)
        prefs.edit().putStringSet("owned_survey_ids_v2", current).apply()
    }

    override fun addUserSurveyIds(ids: Collection<String>) {
        val current = prefs.getStringSet("owned_survey_ids_v2", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.addAll(ids)
        prefs.edit().putStringSet("owned_survey_ids_v2", current).apply()
    }

    override fun getUserSurveyIds(): Set<String> {
        return prefs.getStringSet("owned_survey_ids_v2", emptySet()) ?: emptySet()
    }

    override fun clearUserSurveyIds() {
        prefs.edit().remove("owned_survey_ids_v2").apply()
    }

    override fun getNewSurveyCount(): Int = prefs.getInt("new_survey_count", 0)

    override fun incrementNewSurveyCount() {
        val current = getNewSurveyCount()
        prefs.edit().putInt("new_survey_count", current + 1).apply()
    }

    override fun resetNewSurveyCount() {
        prefs.edit().putInt("new_survey_count", 0).apply()
    }

    override fun saveUserEmail(email: String) {
        prefs.edit().putString("user_email", email).apply()
    }

    override fun getUserEmail(): String? = prefs.getString("user_email", null)

    override fun saveUserName(name: String) {
        prefs.edit().putString("user_name", name).apply()
    }

    override fun getUserName(): String? = prefs.getString("user_name", null)
}
