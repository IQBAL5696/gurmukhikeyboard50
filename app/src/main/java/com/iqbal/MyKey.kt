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

    private val density = res.displayMetrics.density
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

    override fun isInside(x: Int, y: Int): Boolean {
        val code = codes.firstOrNull() ?: 0
        // For Space key (32), apply strict horizontal bounds to avoid overlapping 1234 / comma / period keys,
        // and allow vertical gap padding.
        if (code == 32) {
            val hMargin = (2 * density).toInt()
            val vMargin = (12 * density).toInt()
            return x >= this.x - hMargin && x < this.x + width + hMargin && 
                   y >= this.y - vMargin && y < this.y + height + vMargin
        }
        
        // Expand hit area slightly for all keys to handle gaps (horizontal and vertical)
        // This fixes "corner touch not working" for keys like 'A' and 'L'
        val hMargin = (width * 0.15).toInt() // 15% of key width as extra horizontal hit area
        val vMargin = (height * 0.15).toInt() // 15% of key height as extra vertical hit area
        
        var left = this.x - hMargin
        var right = this.x + width + hMargin
        val top = this.y - vMargin
        val bottom = this.y + height + vMargin

        // If it's a left-edge key, extend hit area to screen edge
        if ((edgeFlags and Keyboard.EDGE_LEFT) != 0) {
            left = 0
        }
        
        // For right-edge keys, we expand significantly to catch touches near the edge
        if ((edgeFlags and Keyboard.EDGE_RIGHT) != 0) {
            right += (width * 0.5).toInt() // Add 50% width extra on the right
        }

        return x >= left && x < right && y >= top && y < bottom
    }
}
