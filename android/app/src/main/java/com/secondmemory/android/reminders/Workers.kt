package com.secondmemory.android.reminders

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.core.app.NotificationManagerCompat
import com.secondmemory.android.BuildConfig
import com.secondmemory.android.data.AppSettings
import com.secondmemory.android.network.AuthException
import com.secondmemory.android.network.BackendClient
import com.secondmemory.android.network.BackendException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private val NeedsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
private const val MAX_ATTEMPTS = 5

private fun client(context: Context): BackendClient? =
    AppSettings.authToken(context)?.let { BackendClient(BuildConfig.DEFAULT_BACKEND_URL, it) }

/** Background upkeep of reminders and the morning summary while someone is signed in. */
object ReminderSync {
    private const val SYNC_NOW = "reminder-sync"
    private const val SYNC_PERIODIC = "reminder-sync-periodic"
    internal const val TAG_ACTIONS = "memory-action"

    /** After sign-in, app start, reboot or update: a sync now, one every 6 hours, and the summary alarm. */
    fun start(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderSyncWorker>(6, TimeUnit.HOURS).setConstraints(NeedsNetwork).build()
        )
        requestSync(context)
        DigestAlarm.schedule(context)
    }

    /** Re-plans reminders soon; called whenever tasks or due dates change. */
    fun requestSync(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderSyncWorker>().setConstraints(NeedsNetwork).build()
        )
    }

    /** Signing out: no more reminders, summaries or pending notification actions. */
    fun stop(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(SYNC_NOW)
        work.cancelUniqueWork(SYNC_PERIODIC)
        work.cancelAllWorkByTag(TAG_ACTIONS)
        ReminderAlarms.cancelAll(context)
        DigestAlarm.cancel(context)
        NotificationManagerCompat.from(context).cancelAll()
    }
}

/** Fetches open tasks and sets an alarm for each upcoming due time. */
class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val client = client(applicationContext)
        if (client == null || !AppSettings.remindersEnabled(applicationContext)) {
            ReminderAlarms.cancelAll(applicationContext)
            return Result.success()
        }
        return try {
            val open = withContext(Dispatchers.IO) { client.openMemories(false) }
            ReminderAlarms.schedule(applicationContext, ReminderPlanner.plan(open, Instant.now(), ZoneId.systemDefault()))
            Result.success()
        } catch (ex: AuthException) {
            Result.success() // signed out on the server; the app notices on next open
        } catch (ex: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }
}

/** Applies "Done" / "Tomorrow" from a reminder notification. */
class MemoryActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val memoryId = inputData.getString(KEY_MEMORY_ID) ?: return Result.failure()
        val client = client(applicationContext) ?: return Result.success()
        return try {
            withContext(Dispatchers.IO) {
                when (inputData.getString(KEY_ACTION)) {
                    DONE -> client.updateMemory(memoryId, status = "DONE")
                    TOMORROW -> client.updateMemory(memoryId, dueAt = Instant.now().plus(1, ChronoUnit.DAYS).toString())
                }
            }
            ReminderSync.requestSync(applicationContext)
            Result.success()
        } catch (ex: AuthException) {
            Result.success()
        } catch (ex: BackendException) {
            Result.success() // e.g. the memory was deleted meanwhile: nothing left to do
        } catch (ex: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val DONE = "done"
        const val TOMORROW = "tomorrow"
        private const val KEY_MEMORY_ID = "memory_id"
        private const val KEY_ACTION = "action"

        fun enqueue(context: Context, memoryId: String, action: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "memory-action-$memoryId", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MemoryActionWorker>()
                    .setConstraints(NeedsNetwork)
                    .setInputData(workDataOf(KEY_MEMORY_ID to memoryId, KEY_ACTION to action))
                    .addTag(ReminderSync.TAG_ACTIONS)
                    .build()
            )
        }
    }
}

/** Builds and shows the morning summary (nothing is shown when nothing is due). */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val client = client(applicationContext) ?: return Result.success()
        return try {
            val open = withContext(Dispatchers.IO) { client.openMemories(false) }
            Notifications.showDigest(applicationContext, ReminderPlanner.digest(open, Instant.now(), ZoneId.systemDefault()))
            Result.success()
        } catch (ex: AuthException) {
            Result.success()
        } catch (ex: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "morning-summary", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DigestWorker>().setConstraints(NeedsNetwork).build()
            )
        }
    }
}
