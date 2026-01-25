package com.iqbal

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"

/**
 * Commits a newline character if the current editor is WhatsApp.
 *
 * @return `true` if the event was handled (i.e., it was WhatsApp), `false` otherwise.
 */
fun handleWhatsAppEnterKey(inputConnection: InputConnection?, editorInfo: EditorInfo?): Boolean {
    if (editorInfo?.packageName != WHATSAPP_PACKAGE_NAME) {
        return false
    }

    inputConnection?.commitText("\n", 1)
    return true
}
