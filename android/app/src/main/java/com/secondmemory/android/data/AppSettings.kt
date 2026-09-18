package com.secondmemory.android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AppSettings {
    private const val FILE = "second_memory_settings"
    private const val AUTH_FILE = "second_memory_auth"
    private const val AUTH_TOKEN = "auth_token"
    private const val USER_ID = "user_id"
    private const val USERNAME = "username"
    private const val LAST_RESULT = "last_result"
    private const val LAST_AUDIO = "last_audio"
    private const val PENDING_SESSION = "pending_session"
    private const val PENDING_AUDIO = "pending_audio"
    private const val PENDING_UPLOADED = "pending_uploaded"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun authPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        AUTH_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun authToken(context: Context): String? = authPrefs(context).getString(AUTH_TOKEN, null)

    fun userId(context: Context): String? = authPrefs(context).getString(USER_ID, null)

    fun username(context: Context): String? = authPrefs(context).getString(USERNAME, null)

    fun isLoggedIn(context: Context): Boolean = !authToken(context).isNullOrBlank()

    fun saveSession(context: Context, token: String, userId: String, username: String) {
        authPrefs(context).edit()
            .putString(AUTH_TOKEN, token)
            .putString(USER_ID, userId)
            .putString(USERNAME, username)
            .apply()
    }

    fun clearSession(context: Context) {
        authPrefs(context).edit().remove(AUTH_TOKEN).remove(USER_ID).remove(USERNAME).apply()
    }

    fun lastResult(context: Context): SessionResult? =
        prefs(context).getString(LAST_RESULT, null)?.let { runCatching { ResultJson.decode(it) }.getOrNull() }

    fun saveLastResult(context: Context, result: SessionResult) {
        prefs(context).edit().putString(LAST_RESULT, ResultJson.encode(result)).apply()
    }

    fun lastAudio(context: Context): String? = prefs(context).getString(LAST_AUDIO, null)
    fun saveLastAudio(context: Context, path: String) = prefs(context).edit().putString(LAST_AUDIO, path).apply()

    fun pendingSession(context: Context, audioPath: String): String? {
        val store = prefs(context)
        return if (store.getString(PENDING_AUDIO, null) == audioPath) store.getString(PENDING_SESSION, null) else null
    }

    fun savePendingSession(context: Context, audioPath: String, sessionId: String) {
        prefs(context).edit().putString(LAST_AUDIO, audioPath).putString(PENDING_AUDIO, audioPath)
            .putString(PENDING_SESSION, sessionId)
            .putBoolean(PENDING_UPLOADED, false).apply()
    }

    fun pendingUploaded(context: Context): Boolean = prefs(context).getBoolean(PENDING_UPLOADED, false)
    fun markPendingUploaded(context: Context) = prefs(context).edit().putBoolean(PENDING_UPLOADED, true).apply()
    fun clearPending(context: Context) = prefs(context).edit().remove(PENDING_SESSION)
        .remove(PENDING_AUDIO).remove(PENDING_UPLOADED).apply()
}
