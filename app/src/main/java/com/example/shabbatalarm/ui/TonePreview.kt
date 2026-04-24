package com.example.shabbatalarm.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays an alarm tone for a short preview window on the ALARM audio stream
 * so the user can audition it before selecting. Calling [play] again cancels
 * any in-flight preview. [release] fully tears down the underlying MediaPlayer.
 */
class TonePreview(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private var player: MediaPlayer? = null
    private var stopJob: Job? = null

    fun play(
        uri: Uri,
        volume: Float = 1f,
        previewDurationMs: Long = DEFAULT_PREVIEW_MS
    ) {
        release()
        try {
            val v = volume.coerceIn(0f, 1f)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                setVolume(v, v)
                start()
            }
            stopJob = scope.launch {
                delay(previewDurationMs)
                release()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Preview playback failed for $uri", t)
            release()
        }
    }

    /** Updates the volume of the currently-playing preview, if any. */
    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        player?.let { runCatching { it.setVolume(v, v) } }
    }

    fun isPlaying(): Boolean = player?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false

    fun release() {
        stopJob?.cancel()
        stopJob = null
        player?.apply {
            runCatching { if (isPlaying) stop() }
            runCatching { release() }
        }
        player = null
    }

    companion object {
        private const val TAG = "TonePreview"
        private const val DEFAULT_PREVIEW_MS = 5_000L
    }
}
