package com.example.shabbatalarm.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.shabbatalarm.MainActivity
import com.example.shabbatalarm.R
import com.example.shabbatalarm.ShabbatAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that plays the alarm tone on the ALARM audio stream for the
 * user-configured duration (5–60 seconds, persisted in [AlarmRepository]) and then
 * stops itself.
 *
 * The auto-stop is driven by a Coroutine on the main dispatcher, scoped to the
 * service lifecycle and cancelled in onDestroy.
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoStopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService onStartCommand")

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startPlayback()
        startVibrationIfEnabled()
        scheduleAutoStop()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        autoStopJob?.cancel()
        serviceScope.cancel()
        releasePlayer()
        stopVibration()
        // Removal/reschedule of the alarm in the list is handled by AlarmReceiver
        // (it knows which alarm fired by its id). The service is alarm-agnostic.
        AlarmWakeLock.release()
        Log.d(TAG, "AlarmService onDestroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPlayback() {
        val repo = AlarmRepository(this)
        val storedToneUri = repo.getAlarmToneUri()?.let { Uri.parse(it) }
        val toneUri: Uri = storedToneUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        val userVolume = repo.getAlarmVolume()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                stopSelf()
                true
            }
            try {
                setDataSource(this@AlarmService, toneUri)
                prepare()
                val startVol = (FADE_START_VOLUME * userVolume).coerceAtMost(userVolume)
                setVolume(startVol, startVol)
                start()
                Log.d(TAG, "Playback started on ALARM stream (fade-in enabled, ceiling=$userVolume)")
                scheduleFadeIn(userVolume)
            } catch (t: Throwable) {
                Log.e(TAG, "Playback failed for $toneUri — trying system default", t)
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: Settings.System.DEFAULT_ALARM_ALERT_URI
                try {
                    reset()
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    setDataSource(this@AlarmService, fallbackUri)
                    prepare()
                    setVolume(userVolume, userVolume)
                    start()
                    Log.d(TAG, "Fallback playback (system default) started")
                } catch (t2: Throwable) {
                    Log.e(TAG, "Fallback playback also failed", t2)
                    stopSelf()
                }
            }
        }
    }

    private fun scheduleAutoStop() {
        val durationSeconds = AlarmRepository(this).getDurationSeconds()
        val durationMs = durationSeconds * 1_000L
        autoStopJob = serviceScope.launch {
            delay(durationMs)
            Log.d(TAG, "${durationSeconds}s elapsed — stopping service")
            stopSelf()
        }
    }

    /**
     * Ramps MediaPlayer volume from [FADE_START_VOLUME] up to 1.0 over a short window.
     * For very short alarms (<6s) we skip the fade-in and play at full volume from the start.
     */
    private fun scheduleFadeIn(ceiling: Float) {
        val durationMs = AlarmRepository(this).getDurationSeconds() * 1_000L
        if (durationMs < 6_000L) {
            mediaPlayer?.setVolume(ceiling, ceiling)
            return
        }
        val startVol = (FADE_START_VOLUME * ceiling).coerceAtMost(ceiling)
        val fadeInMs = minOf(MAX_FADE_IN_MS, (durationMs * 0.3).toLong())
        val steps = 20
        val stepDelay = fadeInMs / steps
        serviceScope.launch {
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val volume = startVol + (ceiling - startVol) * progress
                mediaPlayer?.setVolume(volume, volume)
                delay(stepDelay)
            }
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
    }

    private fun startVibrationIfEnabled() {
        if (!AlarmRepository(this).getVibrationEnabled()) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return

        // Pattern: 500ms on, 500ms off, repeating from index 0.
        val pattern = longArrayOf(0L, 500L, 500L)
        val effect = VibrationEffect.createWaveform(pattern, 0)
        v.vibrate(effect)
        vibrator = v
        Log.d(TAG, "Vibration started")
    }

    private fun stopVibration() {
        vibrator?.let { runCatching { it.cancel() } }
        vibrator = null
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ShabbatAlarmApp.ALARM_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val NOTIFICATION_ID = 1001
        private const val FADE_START_VOLUME = 0.2f
        private const val MAX_FADE_IN_MS = 3_000L
    }
}
