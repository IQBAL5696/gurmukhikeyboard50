package com.iqbal.gurmukhikeyboard50

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

val WHATSAPP_PACKAGE_NAMES = listOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * Commits a newline character if the current editor is WhatsApp or WhatsApp Business.
 *
 * @return `true` if the event was handled (i.e., it was WhatsApp), `false` otherwise.
 */
fun handleWhatsAppEnterKey(inputConnection: InputConnection?, editorInfo: EditorInfo?): Boolean {
    if (editorInfo?.packageName !in WHATSAPP_PACKAGE_NAMES) {
        return false
    }

    inputConnection?.commitText("\n", 1)
    return true
}
