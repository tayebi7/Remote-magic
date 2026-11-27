package com.remotemagic.app.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator

class Feedback(context: Context) {
    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var tone: ToneGenerator? = null

    fun press(vibrate: Boolean, sound: Boolean) {
        if (vibrate) vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        if (sound) {
            val generator = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 40) }.getOrNull()
            tone = generator
            generator?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
