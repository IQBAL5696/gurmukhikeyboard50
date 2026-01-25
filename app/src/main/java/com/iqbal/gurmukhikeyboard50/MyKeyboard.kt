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
        if (mRecalculatedHeight > 0) {
            return mRecalculatedHeight
        }
        return super.getHeight()
    }

    private fun init() {
        mShiftIconOn = ContextCompat.getDrawable(mContext, R.drawable.ic_shift_on)
        mShiftIconOff = ContextCompat.getDrawable(mContext, R.drawable.ic_shift_off)
        mShiftIconCapsLock = ContextCompat.getDrawable(mContext, R.drawable.ic_caps_lock_on)
        initKeyboardProperties()
    }

    fun applyCustomLayout(kv: MyKeyboardView) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)
        val keyHeight = sharedPreferences.getInt("key_height_abs", -1)
        val keyGap = sharedPreferences.getInt("key_gap_abs", -1)

        if (keyHeight != -1 || keyGap != -1) {
            for (key in keys) {
                if (keyHeight != -1) key.height = keyHeight
                if (keyGap != -1) key.gap = keyGap
            }

            mRecalculatedHeight = keys.maxOfOrNull { it.y + it.height } ?: 0
        }

        kv.invalidateAllKeys()
        kv.requestLayout()
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
            
            // ✅ Fix: Skip empty/spacer keys used in Split mode
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
