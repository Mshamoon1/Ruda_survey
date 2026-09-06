package com.ruda.survey.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ruda.survey.utils.TokenManager

class SecureTokenManager(context: Context) : TokenManager {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "ruda_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("SecureTokenManager", "EncryptedSharedPreferences failed, falling back to plain", e)
        context.getSharedPreferences("ruda_plain_prefs", Context.MODE_PRIVATE)
    }

    override fun saveTokens(access: String, refresh: String) {
        prefs.edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .apply()
    }

    override fun getAccessToken(): String? = try {
        prefs.getString("access_token", null)
    } catch (e: Exception) {
        Log.e("SecureTokenManager", "getAccessToken failed", e)
        null
    }

    override fun getRefreshToken(): String? = try {
        prefs.getString("refresh_token", null)
    } catch (e: Exception) {
        Log.e("SecureTokenManager", "getRefreshToken failed", e)
        null
    }

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
