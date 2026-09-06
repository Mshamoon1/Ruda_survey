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
}
