package com.secondmemory.android.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.secondmemory.android.BuildConfig
import com.secondmemory.android.MainActivity
import com.secondmemory.android.R
import com.secondmemory.android.data.AppSettings
import com.secondmemory.android.data.SessionResult
import com.secondmemory.android.network.AuthException
import com.secondmemory.android.network.BackendClient
import com.secondmemory.android.network.BackendException
import com.secondmemory.android.network.ConflictException
import com.secondmemory.android.state.MemoryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10

class MemoryRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val processing = AtomicBoolean(false)
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedElapsed = 0L
    private var startedAt = ""
    private var maxSeconds = 0L
    private var stoppedAtLimit = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACTIVATE -> startRecording(intent.getLongExtra(EXTRA_MAX_SECONDS, 0L))
            ACTION_DEACTIVATE -> stopAndProcess()
            ACTION_RETRY -> intent.getStringExtra(EXTRA_AUDIO_PATH)?.let { process(File(it)) }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(maxSeconds: Long) {
        if (recorder != null || processing.get()) return
        this.maxSeconds = maxSeconds
        stoppedAtLimit = false
        createChannel()
        try {
            val directory = File(filesDir, RECORDINGS_DIR).apply { mkdirs() }
            outputFile = File(directory, "memory-${System.currentTimeMillis()}.m4a")
            startedAt = OffsetDateTime.now().toString()
            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else {
                @Suppress("DEPRECATION") MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            startedElapsed = SystemClock.elapsedRealtime()
            startMicrophoneForeground(
                notification(getString(R.string.recording_active_title), getString(R.string.notification_active_text))
            )
            MemoryState.active()
            handler.post(ticker)
            handler.post(levelPoller)
        } catch (ex: Exception) {
            recorder?.release(); recorder = null
            MemoryState.failure(getString(R.string.error_microphone, ex.message.orEmpty()))
            stopSelf()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (recorder != null) {
                val elapsed = (SystemClock.elapsedRealtime() - startedElapsed) / 1000
                if (maxSeconds > 0 && elapsed >= maxSeconds) {
                    // The account can't process anything longer, so stop here and process what we have.
                    stoppedAtLimit = true
                    stopAndProcess()
                    return
                }
                MemoryState.active(elapsed)
                handler.postDelayed(this, 1000)
            }
        }
    }

    /** Feeds the level meter: fast while the recording screen shows it, slow otherwise. */
    private val levelPoller = object : Runnable {
        override fun run() {
            val active = recorder ?: return
            MemoryState.level(normalizedLevel(runCatching { active.maxAmplitude }.getOrDefault(0)))
            handler.postDelayed(this, if (MemoryState.levelObserved()) 100L else 1000L)
        }
    }

    private fun stopAndProcess() {
        val file = outputFile ?: return
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(levelPoller)
        MemoryState.level(0f)
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            AppSettings.saveLastAudio(this, file.absolutePath)
            process(file)
        } catch (ex: RuntimeException) {
            recorder?.release(); recorder = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            // The file is unusable, so don't offer "Retry processing" for it.
            runCatching { file.delete() }
            MemoryState.failure(getString(R.string.error_recording_unusable), null)
            stopSelf()
        }
    }

    private fun process(file: File) {
        if (!processing.compareAndSet(false, true)) return
        createChannel()
        val processingTitle = getString(R.string.notification_processing_title)
        val processingNotification = notification(processingTitle, getString(R.string.progress_preparing_upload), canStop = false)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, processingNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID, processingNotification)
        MemoryState.processing(getString(R.string.progress_preparing_upload), file.absolutePath)
        AppSettings.savePendingAudio(this, file.absolutePath)
        scope.launch {
            try {
                val client = BackendClient(
                    BuildConfig.DEFAULT_BACKEND_URL,
                    AppSettings.authToken(this@MemoryRecordingService)
                )
                client.health()
                var sessionId = AppSettings.pendingSession(this@MemoryRecordingService, file.absolutePath)
                if (sessionId == null) {
                    sessionId = client.createSession(startedAt.ifBlank { OffsetDateTime.now().toString() })
                    AppSettings.savePendingSession(this@MemoryRecordingService, file.absolutePath, sessionId)
                }
                if (!AppSettings.pendingUploaded(this@MemoryRecordingService)) {
                    showProgress(getString(R.string.progress_uploading), file)
                    client.upload(sessionId, file)
                    AppSettings.markPendingUploaded(this@MemoryRecordingService)
                }
                showProgress(getString(R.string.progress_working), file)
                // 409: an earlier attempt is still running (or already finished), so just follow it.
                try { client.startProcessing(sessionId) } catch (alreadyRunning: ConflictException) { }
                val result = pollUntilDone(client, sessionId, file)
                AppSettings.saveLastResult(this@MemoryRecordingService, result)
                AppSettings.clearPending(this@MemoryRecordingService)
                // The server has the transcript now; the local copy of the audio is no longer needed.
                runCatching { file.delete() }
                MemoryState.ready(result)
                val count = result.memories.size
                finalNotification(
                    getString(R.string.notification_ready_title),
                    resources.getQuantityString(
                        if (stoppedAtLimit) R.plurals.memories_extracted_at_limit else R.plurals.memories_extracted,
                        count, count
                    )
                )
            } catch (ex: AuthException) {
                AppSettings.clearSession(this@MemoryRecordingService)
                MemoryState.failure(getString(R.string.error_recording_session_expired), file.absolutePath)
                finalNotification(getString(R.string.notification_sign_in_title), getString(R.string.notification_sign_in_text))
            } catch (ex: Exception) {
                MemoryState.failure(ex.message ?: getString(R.string.error_processing_failed), file.absolutePath)
                finalNotification(getString(R.string.notification_attention_title), getString(R.string.notification_attention_text))
            } finally {
                processing.set(false)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun showProgress(label: String, file: File) {
        MemoryState.processing(label, file.absolutePath)
        updateNotification(getString(R.string.notification_processing_title), label)
    }

    private suspend fun pollUntilDone(client: BackendClient, sessionId: String, file: File): SessionResult {
        val deadline = SystemClock.elapsedRealtime() + POLL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val detail = client.sessionDetail(sessionId)
            when (val status = detail.session.status) {
                "COMPLETED" -> return SessionResult(
                    sessionId = sessionId,
                    transcript = detail.transcript,
                    summary = detail.session.summary.orEmpty(),
                    memories = detail.memories
                )
                "FAILED" -> throw BackendException(detail.session.errorMessage ?: getString(R.string.error_processing_failed))
                else -> showProgress(statusLabel(status), file)
            }
            delay(POLL_INTERVAL_MS)
        }
        throw BackendException(getString(R.string.error_processing_slow))
    }

    private fun statusLabel(status: String): String = getString(
        when (status) {
            "AUDIO_RECEIVED" -> R.string.progress_preparing
            "TRANSCRIBING" -> R.string.progress_transcribing
            "TRANSCRIPTION_COMPLETE" -> R.string.progress_transcribed
            "PROCESSING" -> R.string.progress_extracting
            else -> R.string.progress_working
        }
    )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.recording_channel_description) })
    }

    private fun startMicrophoneForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun notification(title: String, text: String, canStop: Boolean = true): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_memory_foreground).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setOngoing(canStop).setOnlyAlertOnce(true)
        if (canStop) {
            val stop = PendingIntent.getService(this, 2, Intent(this, MemoryRecordingService::class.java).setAction(ACTION_DEACTIVATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, getString(R.string.action_stop_and_save), stop)
        }
        return builder.build()
    }

    private fun updateNotification(title: String, text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text, false))

    private fun finalNotification(title: String, text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text, false))

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(levelPoller)
        recorder?.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Folder under filesDir that holds recordings until they are processed. */
        const val RECORDINGS_DIR = "recordings"
        private const val CHANNEL_ID = "memory_recording"
        private const val NOTIFICATION_ID = 501
        private const val ACTION_ACTIVATE = "com.secondmemory.ACTIVATE"
        private const val ACTION_DEACTIVATE = "com.secondmemory.DEACTIVATE"
        private const val ACTION_RETRY = "com.secondmemory.RETRY"
        private const val EXTRA_AUDIO_PATH = "audio_path"
        private const val EXTRA_MAX_SECONDS = "max_seconds"
        private const val POLL_INTERVAL_MS = 4_000L
        private const val POLL_TIMEOUT_MS = 15 * 60 * 1_000L

        /** Peak amplitude (0..32767) as a 0..1 level on a -50 dB..0 dB scale, which tracks speech well. */
        private fun normalizedLevel(amplitude: Int): Float {
            if (amplitude <= 0) return 0f
            val decibels = 20 * log10(amplitude / 32767.0)
            return ((decibels + 50) / 50).toFloat().coerceIn(0f, 1f)
        }

        /** Starts recording; it stops on its own after [maxSeconds] (0 = no limit). */
        fun activate(context: Context, maxSeconds: Long) = ContextCompat.startForegroundService(context,
            Intent(context, MemoryRecordingService::class.java).setAction(ACTION_ACTIVATE)
                .putExtra(EXTRA_MAX_SECONDS, maxSeconds))
        fun deactivate(context: Context) = context.startService(
            Intent(context, MemoryRecordingService::class.java).setAction(ACTION_DEACTIVATE))
        fun retry(context: Context, path: String) = ContextCompat.startForegroundService(context,
            Intent(context, MemoryRecordingService::class.java).setAction(ACTION_RETRY).putExtra(EXTRA_AUDIO_PATH, path))
    }
}
