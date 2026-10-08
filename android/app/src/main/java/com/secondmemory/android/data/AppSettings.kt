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
    private const val THEME_MODE = "theme_mode"
    private const val REMINDERS_ENABLED = "reminders_enabled"
    private const val DIGEST_ENABLED = "digest_enabled"
    private const val DIGEST_MINUTE_OF_DAY = "digest_minute_of_day"
    private const val SCHEDULED_REMINDERS = "scheduled_reminders"
    private const val NOTIFICATION_CARD_DISMISSED = "notification_card_dismissed"
    private const val DATA_OWNER = "data_owner"

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

    /**
     * Marks a recording as unfinished before any network call, so it stays retryable across app
     * restarts even if the phone is offline. A different recording starts with no session or upload.
     */
    fun savePendingAudio(context: Context, audioPath: String) {
        val store = prefs(context)
        if (store.getString(PENDING_AUDIO, null) == audioPath) return
        store.edit().putString(LAST_AUDIO, audioPath).putString(PENDING_AUDIO, audioPath)
            .remove(PENDING_SESSION).putBoolean(PENDING_UPLOADED, false).apply()
    }

    fun savePendingSession(context: Context, audioPath: String, sessionId: String) {
        prefs(context).edit().putString(LAST_AUDIO, audioPath).putString(PENDING_AUDIO, audioPath)
            .putString(PENDING_SESSION, sessionId)
            .putBoolean(PENDING_UPLOADED, false).apply()
    }

    /** Audio path of a recording whose processing has not finished successfully, if any. */
    fun pendingAudio(context: Context): String? = prefs(context).getString(PENDING_AUDIO, null)

    fun pendingUploaded(context: Context): Boolean = prefs(context).getBoolean(PENDING_UPLOADED, false)
    fun markPendingUploaded(context: Context) = prefs(context).edit().putBoolean(PENDING_UPLOADED, true).apply()
    fun clearPending(context: Context) = prefs(context).edit().remove(PENDING_SESSION)
        .remove(PENDING_AUDIO).remove(PENDING_UPLOADED).apply()

    fun themeMode(context: Context): ThemeMode =
        prefs(context).getString(THEME_MODE, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    fun saveThemeMode(context: Context, mode: ThemeMode) = prefs(context).edit().putString(THEME_MODE, mode.name).apply()

    // Notification preferences are device settings: they survive signing out.
    fun remindersEnabled(context: Context): Boolean = prefs(context).getBoolean(REMINDERS_ENABLED, true)
    fun setRemindersEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(REMINDERS_ENABLED, enabled).apply()

    fun digestEnabled(context: Context): Boolean = prefs(context).getBoolean(DIGEST_ENABLED, true)
    fun setDigestEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(DIGEST_ENABLED, enabled).apply()

    /** Time of the morning summary as minutes after midnight (default 8:00). */
    fun digestMinuteOfDay(context: Context): Int = prefs(context).getInt(DIGEST_MINUTE_OF_DAY, 8 * 60)
    fun setDigestMinuteOfDay(context: Context, minute: Int) =
        prefs(context).edit().putInt(DIGEST_MINUTE_OF_DAY, minute).apply()

    /** Memory ids with a reminder alarm set, so alarms that are no longer wanted can be cancelled. */
    fun scheduledReminders(context: Context): Set<String> =
        prefs(context).getStringSet(SCHEDULED_REMINDERS, emptySet())?.toSet() ?: emptySet()
    fun saveScheduledReminders(context: Context, ids: Set<String>) =
        prefs(context).edit().putStringSet(SCHEDULED_REMINDERS, ids).apply()

    fun notificationCardDismissed(context: Context): Boolean = prefs(context).getBoolean(NOTIFICATION_CARD_DISMISSED, false)
    fun dismissNotificationCard(context: Context) =
        prefs(context).edit().putBoolean(NOTIFICATION_CARD_DISMISSED, true).apply()

    /** The account whose results and pending recording are stored on this phone. */
    fun dataOwner(context: Context): String? = prefs(context).getString(DATA_OWNER, null)
    fun saveDataOwner(context: Context, userId: String) = prefs(context).edit().putString(DATA_OWNER, userId).apply()

    /**
     * Forgets everything stored for the signed-in person: the session, the last result (which holds
     * a transcript), the pending recording and the data owner. The theme choice is a device setting
     * and stays.
     */
    fun clearUserData(context: Context) {
        clearSession(context)
        prefs(context).edit().remove(LAST_RESULT).remove(LAST_AUDIO).remove(PENDING_SESSION)
            .remove(PENDING_AUDIO).remove(PENDING_UPLOADED).remove(DATA_OWNER).apply()
    }
}
