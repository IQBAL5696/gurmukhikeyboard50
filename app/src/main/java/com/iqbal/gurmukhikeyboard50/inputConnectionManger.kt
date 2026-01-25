package com.iqbal.gurmukhikeyboard50

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class InputConnectionManager {

    private var currentInputConnection: InputConnection? = null

    fun setCurrentInputConnection(ic: InputConnection?) {
        this.currentInputConnection = ic
    }

    // Public getter for currentInputConnection
    fun getCurrentInputConnection(): InputConnection? {
        return currentInputConnection
    }

    fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        return currentInputConnection?.commitText(text, newCursorPosition) ?: false
    }

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        return currentInputConnection?.deleteSurroundingText(beforeLength, afterLength) ?: false
    }

    fun sendKeyEvent(event: KeyEvent): Boolean {
        return currentInputConnection?.sendKeyEvent(event) ?: false
    }

    fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        return currentInputConnection?.getTextBeforeCursor(n, flags)
    }

    fun finishComposingText(): Boolean {
        return currentInputConnection?.finishComposingText() ?: false
    }

    /**
     * Deletes a specified number of characters before the cursor and then commits new text.
     * Useful for replacing typed text with a suggestion.
     *
     * @param typedTextLength The number of characters before the cursor to delete (e.g., the length of the typed prefix that matched the suggestion).
     * @param newText The new text to commit.
     * @param newCursorPosition The new cursor position within the committed text (usually 1 or newText.length).
     */
    fun commitReplacingTypedText(typedTextLength: Int, newText: CharSequence, newCursorPosition: Int) {
        currentInputConnection?.beginBatchEdit()
        deleteSurroundingText(typedTextLength, 0)
        commitText(newText, newCursorPosition) // This will now use the updated commitText
        currentInputConnection?.endBatchEdit()
    }

    // Add any other InputConnection methods you might need to wrap
}
