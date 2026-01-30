package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.inputmethodservice.Keyboard
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

class MyKeyboard : Keyboard {

    private val mContext: Context
    var mShiftIconOn: Drawable? = null
    var mShiftIconOff: Drawable? = null
    var mShiftIconCapsLock: Drawable? = null
    private var mShiftKey: Key? = null

    private var isCapsLockOn: Boolean = false
    private var mRecalculatedHeight: Int = 0

    override fun getHeight(): Int {
        return if (mRecalculatedHeight > 0) mRecalculatedHeight else super.getHeight()
    }

    private fun init() {
        mShiftIconOn = ContextCompat.getDrawable(mContext, R.drawable.ic_shift_on)
        mShiftIconOff = ContextCompat.getDrawable(mContext, R.drawable.ic_shift_off)
        mShiftIconCapsLock = ContextCompat.getDrawable(mContext, R.drawable.ic_caps_lock_on)
        initKeyboardProperties()
        applyCustomLayout()
    }

    fun applyCustomLayout() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)
        val keyHeightPercent = sharedPreferences.getInt(ImeConstants.PREF_KEY_HEIGHT, 100)
        val screenWidth = mContext.resources.displayMetrics.widthPixels
        val scaleFactor = keyHeightPercent / 100f
        
        val rows = keys.groupBy { it.y }
        val sortedY = rows.keys.sorted()
        
        var currentY = 0
        var lastOriginalY = sortedY.firstOrNull() ?: 0

        for ((index, y) in sortedY.withIndex()) {
            val rowKeys = rows[y]?.sortedBy { it.x } ?: continue
            
            val rowSpacing = y - lastOriginalY
            currentY += (rowSpacing * scaleFactor).toInt()
            lastOriginalY = y

            val firstKeyInRow = rowKeys[0]
            val newHeight = (firstKeyInRow.height * scaleFactor).toInt()
            
            // Apply 5% gap ONLY to Row 3 (Index 2)
            val isHomeRow = index == 2 && rowKeys.size == 9
            
            var currentX = 0
            for (key in rowKeys) {
                key.height = newHeight
                key.y = currentY
                
                if (isHomeRow) {
                    if (key == firstKeyInRow) {
                        // Force 5% initial gap for the middle row
                        currentX = (screenWidth * 0.05).toInt()
                    }
                    key.x = currentX + key.gap
                    currentX = key.x + key.width
                } else {
                    // For all other rows (including the 4th row), 
                    // we keep the XML-defined horizontal positioning.
                }
            }
        }

        val lastRowY = sortedY.lastOrNull() ?: 0
        val lastRowKeys = rows[lastRowY]
        if (lastRowKeys != null && lastRowKeys.isNotEmpty()) {
            mRecalculatedHeight = currentY + (lastRowKeys[0].height)
        } else {
            mRecalculatedHeight = currentY
        }
    }

    fun hasShiftKey(): Boolean {
        return mShiftKey != null
    }

    private fun initKeyboardProperties() {
        mShiftKey = keys.find { it.codes.isNotEmpty() && it.codes[0] == KEYCODE_SHIFT }
    }

    constructor(context: Context, xmlLayoutResId: Int) : super(context, xmlLayoutResId) {
        this.mContext = context
        init()
    }

    constructor(context: Context, xmlLayoutResId: Int, modeId: Int, width: Int, height: Int) : super(context, xmlLayoutResId, modeId, width, height) {
        this.mContext = context
        init()
    }

    constructor(context: Context, xmlLayoutResId: Int, modeId: Int) : super(context, xmlLayoutResId, modeId) {
        this.mContext = context
        init()
    }

    override fun createKeyFromXml(res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser): Key {
        val key = MyKey(res, parent, x, y, parser)
        val attributes = parser.let { res.obtainAttributes(it, R.styleable.MyKey) }
        key.shiftedCode = attributes.getInteger(R.styleable.MyKey_shiftedCode, 0)
        key.forPackage = attributes.getString(R.styleable.MyKey_forPackage)
        attributes.recycle()
        return key
    }

    fun setCapsLock(isOn: Boolean) {
        isCapsLockOn = isOn
    }

    override fun setShifted(isShiftedNewState: Boolean): Boolean {
        val changed = super.setShifted(isShiftedNewState)

        mShiftKey?.icon = when {
            isCapsLockOn -> mShiftIconCapsLock
            isShiftedNewState -> mShiftIconOn
            else -> mShiftIconOff
        }

        for (key in keys) {
            val myKey = key as? MyKey ?: continue
            if (myKey.codes.isEmpty() || myKey.originalCode == 0) continue

            if (isShiftedNewState) {
                if (myKey.shiftedCode != 0) {
                    myKey.codes[0] = myKey.shiftedCode
                    when (myKey.shiftedCode) {
                        -2002 -> myKey.label = "ਾਂ"
                        -2003 -> myKey.label = "੍ਰ"
                        -2004 -> myKey.label = "੍ਹ"
                        -2005 -> myKey.label = "੍ਵ"
                        -2006 -> myKey.label = "੍ਯ"
                        else -> myKey.label = myKey.shiftedCode.toChar().toString()
                    }
                } else {
                    val originalLabel = myKey.originalLabel
                    if (originalLabel != null && originalLabel.length == 1 && Character.isLetter(originalLabel[0])) {
                        myKey.label = originalLabel.toString().uppercase()
                        myKey.codes[0] = myKey.originalCode.toChar().uppercaseChar().code
                    }
                }
            } else {
                myKey.codes[0] = myKey.originalCode
                myKey.label = myKey.originalLabel
            }
        }
        return changed
    }
}
