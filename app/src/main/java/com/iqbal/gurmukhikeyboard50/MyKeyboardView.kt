package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.PreferenceManager

class MyKeyboardView(context: Context, attrs: AttributeSet?) : KeyboardView(context, attrs) {

    private val textPaint = Paint()
    private var keyboardKeyBackground: Drawable? = null
    private var functionalKeyBackground: Drawable? = null
    private var keyTextColor = 0
    private var functionalKeyTextColor = 0
    private var iconColor = 0
    private var keyTextSize: Float = 0f
    private var keyPressedColor = 0
    private lateinit var gestureDetector: GestureDetector
    private var service: MyKeyboardIME? = null

    init {
        isProximityCorrectionEnabled = false
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        resolveThemeAttributes()

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val swipeThreshold = 100
                val swipeVelocityThreshold = 100

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (Math.abs(dx) > swipeThreshold && Math.abs(velocityX) > swipeVelocityThreshold) {
                        if (dx > 0) onKeyboardActionListener?.swipeRight() else onKeyboardActionListener?.swipeLeft()
                        return true
                    }
                } else {
                    if (Math.abs(dy) > swipeThreshold && Math.abs(velocityY) > swipeVelocityThreshold) {
                        if (dy > 0) onKeyboardActionListener?.swipeDown() else onKeyboardActionListener?.swipeUp()
                        return true
                    }
                }
                return false
            }
        })
    }

    fun setService(service: MyKeyboardIME) {
        this.service = service
    }

    private fun resolveThemeAttributes() {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(R.attr.keyPressedBackgroundColor, typedValue, true)) {
            keyPressedColor = typedValue.data
        } else {
            keyPressedColor = 0x44FFFFFF
        }
        val cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, context.resources.displayMetrics)

        if (context.theme.resolveAttribute(R.attr.keyboardKeyBackground, typedValue, true)) {
            keyboardKeyBackground = if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                createRoundedDrawable(typedValue.data, keyPressedColor, cornerRadius)
            } else {
                ContextCompat.getDrawable(context, typedValue.resourceId)
            }
        }

        if (context.theme.resolveAttribute(R.attr.functionalKeyBackground, typedValue, true)) {
            functionalKeyBackground = if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                createRoundedDrawable(typedValue.data, keyPressedColor, cornerRadius)
            } else {
                ContextCompat.getDrawable(context, typedValue.resourceId)
            }
        }

        if (context.theme.resolveAttribute(R.attr.keyboardKeyTextColor, typedValue, true)) {
            keyTextColor = typedValue.data
        }
        if (context.theme.resolveAttribute(R.attr.functionalKeyTextColor, typedValue, true)) {
            functionalKeyTextColor = typedValue.data
        } else {
            functionalKeyTextColor = keyTextColor
        }
        if (context.theme.resolveAttribute(R.attr.iconColor, typedValue, true)) {
            iconColor = typedValue.data
        }
    }

    private fun createRoundedDrawable(color: Int, pressedColor: Int, radius: Float): Drawable {
        val defaultDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(pressedColor)
            cornerRadius = radius
        }
        val stateListDrawable = StateListDrawable()
        stateListDrawable.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
        stateListDrawable.addState(intArrayOf(), defaultDrawable)
        return stateListDrawable
    }

    fun setKeyTextSize(size: Float) {
        this.keyTextSize = size
        invalidate()
    }

    override fun setKeyboard(newKeyboard: Keyboard?) {
        super.setKeyboard(newKeyboard)
        resolveThemeAttributes()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setPopupParent(this)
        setPopupOffset(0, 0)
    }

    override fun onLongPress(key: Keyboard.Key?): Boolean {
        // Essential for immediate swipe activation on long press
        return super.onLongPress(key)
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val popupEnabled = sharedPrefs.getBoolean("popup_on_keypress", true)

        if (me.action == MotionEvent.ACTION_DOWN || me.action == MotionEvent.ACTION_MOVE) {
            val x = me.x.toInt() - paddingLeft
            val y = me.y.toInt() - paddingTop
            val keys = keyboard?.keys
            if (keys != null) {
                for (key in keys) {
                    if (key.isInside(x, y)) {
                        isPreviewEnabled = popupEnabled && !isFunctional(key)
                        break
                    }
                }
            }
        }
        
        gestureDetector.onTouchEvent(me)
        return super.onTouchEvent(me)
    }

    override fun onDraw(canvas: Canvas) {
        val keyboard = this.keyboard ?: return
        val keys = keyboard.keys
        for (key in keys) {
            val isFunc = isFunctional(key)
            val backgroundDrawable = if (isFunc) functionalKeyBackground else keyboardKeyBackground
            val textColor = if (isFunc) functionalKeyTextColor else keyTextColor

            backgroundDrawable?.let {
                it.state = if (key.pressed) intArrayOf(android.R.attr.state_pressed) else intArrayOf()
                it.setBounds(key.x + paddingLeft, key.y + paddingTop, key.x + key.width + paddingLeft, key.y + key.height + paddingTop)
                it.draw(canvas)
            }

            if (key.icon != null) {
                drawIcon(canvas, key.icon, key)
            } else if (key.label != null) {
                textPaint.color = textColor
                val firstCode = key.codes.firstOrNull()
                if (firstCode == -103) {
                    textPaint.textSize = key.height * 0.3f
                    val line1 = "1 2"; val line2 = "3 4"
                    val x = key.x + (key.width / 2f) + paddingLeft
                    val y1 = key.y + (key.height / 4f) - ((textPaint.descent() + textPaint.ascent()) / 2f) + paddingTop
                    val y2 = key.y + (key.height * 3 / 4f) - ((textPaint.descent() + textPaint.ascent()) / 2f) + paddingTop
                    canvas.drawText(line1, x, y1, textPaint); canvas.drawText(line2, x, y2, textPaint)
                } else {
                    if (firstCode == -2 || firstCode == -101 || firstCode == -100 || firstCode == ImeConstants.KEYCODE_SWITCH_TO_SYMBOLS) {
                        textPaint.textSize = if (keyTextSize > 0) keyTextSize * 0.7f else key.height * 0.45f
                    } else if (keyTextSize > 0) {
                        textPaint.textSize = keyTextSize
                    } else {
                        textPaint.textSize = key.height * 0.65f
                    }
                    val label = key.label.toString()
                    val x = key.x + (key.width / 2f) + paddingLeft
                    val y = key.y + (key.height / 2f) + paddingTop - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    canvas.drawText(label, x, y, textPaint)
                }
            }
        }
    }

    private fun isFunctional(key: Keyboard.Key): Boolean {
        val code = key.codes.firstOrNull() ?: 0
        return when (code) {
            Keyboard.KEYCODE_DELETE, Keyboard.KEYCODE_SHIFT, Keyboard.KEYCODE_MODE_CHANGE, Keyboard.KEYCODE_DONE, -100, -101, -103, -2, 32, 44, 46, ImeConstants.KEYCODE_SWITCH_TO_SYMBOLS -> true
            else -> false
        }
    }

    private fun drawIcon(canvas: Canvas, icon: Drawable, key: Keyboard.Key) {
        val wrappedIcon = DrawableCompat.wrap(icon.mutate())
        DrawableCompat.setTint(wrappedIcon, iconColor)
        wrappedIcon.state = if (key.pressed) intArrayOf(android.R.attr.state_pressed) else intArrayOf()
        val iconWidth = wrappedIcon.intrinsicWidth; val iconHeight = wrappedIcon.intrinsicHeight
        val x = key.x + (key.width - iconWidth) / 2 + paddingLeft
        val y = key.y + (key.height - iconHeight) / 2 + paddingTop
        wrappedIcon.setBounds(x, y, x + iconWidth, y + iconHeight)
        wrappedIcon.draw(canvas)
    }
}
