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

class MemoryRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val processing = AtomicBoolean(false)
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedElapsed = 0L
    private var startedAt = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACTIVATE -> startRecording()
            ACTION_DEACTIVATE -> stopAndProcess()
            ACTION_RETRY -> intent.getStringExtra(EXTRA_AUDIO_PATH)?.let { process(File(it)) }
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null || processing.get()) return
        createChannel()
        try {
            val directory = File(filesDir, "recordings").apply { mkdirs() }
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
            startMicrophoneForeground(notification("Memory is active", "Tap Deactivate when the conversation ends"))
            MemoryState.active()
            handler.post(ticker)
        } catch (ex: Exception) {
            recorder?.release(); recorder = null
            MemoryState.failure("Could not activate the microphone: ${ex.message}")
            stopSelf()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (recorder != null) {
                MemoryState.active((SystemClock.elapsedRealtime() - startedElapsed) / 1000)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun stopAndProcess() {
        val file = outputFile ?: return
        handler.removeCallbacks(ticker)
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            AppSettings.saveLastAudio(this, file.absolutePath)
            process(file)
        } catch (ex: RuntimeException) {
            recorder?.release(); recorder = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            MemoryState.failure("The recording was too short or could not be saved. Activate Memory and try again.", file.absolutePath)
            stopSelf()
        }
    }

    private fun process(file: File) {
        if (!processing.compareAndSet(false, true)) return
        createChannel()
        val processingNotification = notification("Processing memory", "Preparing upload…", canDeactivate = false)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, processingNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID, processingNotification)
        MemoryState.processing("Preparing upload…", file.absolutePath)
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
                    MemoryState.processing("Uploading recording…", file.absolutePath)
                    updateNotification("Processing memory", "Uploading recording…")
                    client.upload(sessionId, file)
                    AppSettings.markPendingUploaded(this@MemoryRecordingService)
                }
                MemoryState.processing("Transcribing and extracting memories…", file.absolutePath)
                updateNotification("Processing memory", "Transcribing and extracting memories…")
                client.startProcessing(sessionId)
                val result = pollUntilDone(client, sessionId, file)
                AppSettings.saveLastResult(this@MemoryRecordingService, result)
                AppSettings.clearPending(this@MemoryRecordingService)
                MemoryState.ready(result, file.absolutePath)
                finalNotification("Memory ready", "${result.memories.size} memories extracted")
            } catch (ex: AuthException) {
                AppSettings.clearSession(this@MemoryRecordingService)
                MemoryState.failure("Your session expired. Open the app to sign in again.", file.absolutePath)
                finalNotification("Sign-in needed", "Open the app to sign in again")
            } catch (ex: Exception) {
                MemoryState.failure(ex.message ?: "Processing failed", file.absolutePath)
                finalNotification("Memory needs attention", "Open the app to retry")
            } finally {
                processing.set(false)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private suspend fun pollUntilDone(client: BackendClient, sessionId: String, file: File): SessionResult {
        val deadline = SystemClock.elapsedRealtime() + POLL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val detail = client.sessionDetail(sessionId)
            val status = detail.session.status
            when (status) {
                "COMPLETED" -> return SessionResult(
                    sessionId = sessionId,
                    transcript = detail.transcript,
                    summary = detail.session.summary.orEmpty(),
                    memories = detail.memories
                )
                "FAILED" -> throw BackendException(detail.session.errorMessage ?: "Processing failed")
                else -> {
                    val label = statusLabel(status)
                    MemoryState.processing(label, file.absolutePath)
                    updateNotification("Processing memory", label)
                }
            }
            delay(POLL_INTERVAL_MS)
        }
        throw BackendException("Processing is taking longer than expected. Check back in the app shortly.")
    }

    private fun statusLabel(status: String): String = when (status) {
        "AUDIO_RECEIVED" -> "Preparing…"
        "TRANSCRIBING" -> "Transcribing your recording…"
        "TRANSCRIPTION_COMPLETE" -> "Transcription complete, extracting memories…"
        "PROCESSING" -> "Extracting memories…"
        else -> "Transcribing and extracting memories…"
    }

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

    private fun notification(title: String, text: String, canDeactivate: Boolean = true): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_memory_foreground).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setOngoing(canDeactivate).setOnlyAlertOnce(true)
        if (canDeactivate) {
            val stop = PendingIntent.getService(this, 2, Intent(this, MemoryRecordingService::class.java).setAction(ACTION_DEACTIVATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "Deactivate", stop)
        }
        return builder.build()
    }

    private fun updateNotification(title: String, text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text, false))

    private fun finalNotification(title: String, text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text, false))

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        recorder?.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "memory_recording"
        private const val NOTIFICATION_ID = 501
        private const val ACTION_ACTIVATE = "com.secondmemory.ACTIVATE"
        private const val ACTION_DEACTIVATE = "com.secondmemory.DEACTIVATE"
        private const val ACTION_RETRY = "com.secondmemory.RETRY"
        private const val EXTRA_AUDIO_PATH = "audio_path"
        private const val POLL_INTERVAL_MS = 4_000L
        private const val POLL_TIMEOUT_MS = 15 * 60 * 1_000L

        fun activate(context: Context) = ContextCompat.startForegroundService(context,
            Intent(context, MemoryRecordingService::class.java).setAction(ACTION_ACTIVATE))
        fun deactivate(context: Context) = context.startService(
            Intent(context, MemoryRecordingService::class.java).setAction(ACTION_DEACTIVATE))
        fun retry(context: Context, path: String) = ContextCompat.startForegroundService(context,
            Intent(context, MemoryRecordingService::class.java).setAction(ACTION_RETRY).putExtra(EXTRA_AUDIO_PATH, path))
    }
}
