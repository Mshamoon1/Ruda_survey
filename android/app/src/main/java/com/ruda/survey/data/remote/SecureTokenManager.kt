package com.ruda.survey.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ruda.survey.utils.TokenManager

class SecureTokenManager private constructor(context: Context) : TokenManager {

    companion object {
        @Volatile
        private var instance: SecureTokenManager? = null

        fun getInstance(context: Context): SecureTokenManager {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val mgr = SecureTokenManager(context.applicationContext)
                instance = mgr
                return mgr
            }
        }
    }

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

    override fun addUserSurveyId(id: String) {
        val current = prefs.getStringSet("owned_survey_ids_v2", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id)
        prefs.edit().putStringSet("owned_survey_ids_v2", current).apply()
    }

    override fun getUserSurveyIds(): Set<String> {
        return prefs.getStringSet("owned_survey_ids_v2", emptySet()) ?: emptySet()
    }

    override fun addUserSurveyIds(ids: Collection<String>) {
        val current = prefs.getStringSet("owned_survey_ids_v2", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.addAll(ids)
        prefs.edit().putStringSet("owned_survey_ids_v2", current).apply()
    }

    override fun clearUserSurveyIds() {
        prefs.edit().remove("owned_survey_ids_v2").apply()
    }
}
