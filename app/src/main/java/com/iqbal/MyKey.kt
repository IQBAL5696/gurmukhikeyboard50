package com.iqbal.gurmukhikeyboard50

import android.content.res.Resources
import android.inputmethodservice.Keyboard
import android.content.res.XmlResourceParser // Use the specific type

class MyKey(
    res: Resources,
    parent: Keyboard.Row,
    x: Int,
    y: Int,
    parser: XmlResourceParser // This is the type expected by Keyboard.Key constructor
) : Keyboard.Key(res, parent, x, y, parser) {

    var shiftedCode: Int = 0
    var originalCode: Int = 0
    var originalLabel: CharSequence? = null
    var forPackage: String? = null

    init {
        // Store the original code and label that were parsed by the superclass
        if (codes.isNotEmpty()) {
            originalCode = codes[0] // Assuming the first code is the primary/unshifted one
        }
        originalLabel = label

        // The 'shiftedCodes' attribute will be read and set onto this MyKey instance
        // by our custom MyKeyboard class's createKeyFromXml method.
    }
}
