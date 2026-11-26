package com.example.data

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

object SoundEffectsHelper {
    fun playSuccess(enabled: Boolean) {
        if (!enabled) return
        playTone(ToneGenerator.TONE_PROP_ACK, 120)
    }

    fun playTap(enabled: Boolean) {
        if (!enabled) return
        playTone(ToneGenerator.TONE_PROP_BEEP, 60)
    }

    private fun playTone(tone: Int, durationMs: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tg.startTone(tone, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, (durationMs + 150).toLong())
        } catch (_: Exception) {
        }
    }
}
