package com.iqbal.gurmukhikeyboard50

import android.inputmethodservice.KeyboardView

/**
 * Custom extension of KeyboardView.OnKeyboardActionListener for additional gesture handling.
 * This interface allows the IME to respond to swipe gestures on space key and other directions.
 */
interface CustomKeyboardActionListener : KeyboardView.OnKeyboardActionListener {
    /**
     * Called when the space key is swiped left (e.g., for language switch Gurmukhi <-> QWERTY).
     */
    fun onSpaceKeySwipeLeft()

    /**
     * Called when the space key is swiped right (e.g., for language switch QWERTY <-> Gurmukhi).
     */
    fun onSpaceKeySwipeRight()

    /**
     * Called when swiping down on the keyboard (e.g., to hide keyboard).
     */
    override fun swipeDown()

    /**
     * Called when swiping up on the keyboard (e.g., for shift or caps lock).
     */
    override fun swipeUp()

    /**
     * Called when swiping left on the keyboard (e.g., previous page in emoji).
     */
    override fun swipeLeft()

    /**
     * Called when swiping right on the keyboard (e.g., next page in emoji).
     */
    override fun swipeRight()
}