package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.inputmethodservice.Keyboard
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent

object OnKeyHelper {

    // VERY LIGHT VIBRATION (1–3 ms)
    fun performHapticFeedback(context: Context, vibrateOnKeypress: Boolean) {
        if (vibrateOnKeypress) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        2,  // duration LOWEST possible
                        10  // amplitude 0–255 (10 is ultra soft)
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(2)
            }
        }
    }

    // VERY LOW VOLUME KEY PRESS SOUND
    fun playKeyClick(context: Context, soundOnKeypress: Boolean, primaryCode: Int, logTag: String) {
        if (soundOnKeypress) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val soundEffectToPlay = when (primaryCode) {
                Keyboard.KEYCODE_DELETE -> AudioManager.FX_KEYPRESS_DELETE
                Keyboard.KEYCODE_DONE, Keyboard.KEYCODE_SHIFT, KeyEvent.KEYCODE_ENTER ->
                    AudioManager.FX_KEYPRESS_STANDARD
                KeyEvent.KEYCODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }

            try {
                am.playSoundEffect(soundEffectToPlay, 0.05f)
                // 0.05f = VERY VERY LOW volume (max 1.0f)
            } catch (e: Exception) {
                Log.e(logTag, "Error playing sound effect", e)
            }
        }
    }
}
