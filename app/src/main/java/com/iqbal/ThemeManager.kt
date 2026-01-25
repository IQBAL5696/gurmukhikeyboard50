package com.iqbal

import android.content.Context
import android.graphics.drawable.Drawable
import android.inputmethodservice.KeyboardView
import android.util.Log
import androidx.core.content.ContextCompat
import com.iqbal.gurmukhikeyboard50.R // Added import for R class

object ThemeManager {

    fun applyThemeToKeyboardView(themedContext: Context, keyboardView: KeyboardView) {
        keyboardView.background = getBackground(themedContext, R.attr.keyboardViewBackground)
    }

    private fun getBackground(context: Context, attr: Int): Drawable? {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
        try {
            val backgroundResId = typedArray.getResourceId(0, 0)
            if (backgroundResId != 0) {
                return ContextCompat.getDrawable(context, backgroundResId)
            }
        } finally {
            typedArray.recycle()
        }
        return null
    }
}
