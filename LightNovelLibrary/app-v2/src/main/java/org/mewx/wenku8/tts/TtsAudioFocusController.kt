package org.mewx.wenku8.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

enum class TtsAudioFocusEvent {
    GAIN,
    LOSS,
    LOSS_TRANSIENT,
}

/** Injectable audio-focus boundary so both real devices and Robolectric tests run deterministically. */
interface TtsAudioFocusController {
    fun requestFocus(onFocusChanged: (TtsAudioFocusEvent) -> Unit): Boolean
    fun abandonFocus()
}

object NoOpTtsAudioFocusController : TtsAudioFocusController {
    override fun requestFocus(onFocusChanged: (TtsAudioFocusEvent) -> Unit): Boolean = true
    override fun abandonFocus() = Unit
}

/**
 * Manages Android [AudioManager] media-speech focus across API 24–37 so incoming phone calls,
 * voice messages, or competing audio players pause TTS cleanly.
 */
class AndroidTtsAudioFocusController(
    appContext: Context,
) : TtsAudioFocusController {

    private val audioManager =
        appContext.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var activeFocusRequest: AudioFocusRequest? = null
    private var legacyListener: AudioManager.OnAudioFocusChangeListener? = null

    override fun requestFocus(onFocusChanged: (TtsAudioFocusEvent) -> Unit): Boolean {
        val manager = audioManager ?: return true
        abandonFocus()

        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> onFocusChanged(TtsAudioFocusEvent.GAIN)
                AudioManager.AUDIOFOCUS_LOSS -> onFocusChanged(TtsAudioFocusEvent.LOSS)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> onFocusChanged(TtsAudioFocusEvent.LOSS_TRANSIENT)
            }
        }
        legacyListener = listener

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(listener)
                .build()
            activeFocusRequest = req
            runCatching { manager.requestAudioFocus(req) }
                .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                manager.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandonFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let { req ->
                runCatching { manager.abandonAudioFocusRequest(req) }
            }
            activeFocusRequest = null
        } else {
            legacyListener?.let { listener ->
                @Suppress("DEPRECATION")
                runCatching { manager.abandonAudioFocus(listener) }
            }
        }
        legacyListener = null
    }
}
