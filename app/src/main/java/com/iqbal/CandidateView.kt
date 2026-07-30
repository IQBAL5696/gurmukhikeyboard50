package com.iqbal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.ArrayList
import android.util.TypedValue
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.iqbal.gurmukhikeyboard50.ImeConstants
import com.iqbal.gurmukhikeyboard50.R
import com.iqbal.gurmukhikeyboard50.KeyboardType
import com.iqbal.gurmukhikeyboard50.MyKeyboardIME

class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val suggestions = ArrayList<String>()
    private val textPaint = TextPaint()
    private var onSuggestionClickListener: ((String) -> Unit)? = null
    private var onSuggestionLongClickListener: ((String) -> Unit)? = null
    private val suggestionSpacing = 70f // Increased spacing between suggestions

    private var touchedSuggestionIndex = -1
    private var correctionIndex = -1
    private var startX = 0f
    private var startY = 0f
    private val touchSlop = 10f
    private var isLongPressTriggered = false
    private var service: MyKeyboardIME? = null
    private var isBoldMode = false

    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (touchedSuggestionIndex != -1 && touchedSuggestionIndex < suggestions.size) {
            isLongPressTriggered = true
            onSuggestionLongClickListener?.invoke(suggestions[touchedSuggestionIndex].trim())
            touchedSuggestionIndex = -1
        }
    }

    init {
        textPaint.isAntiAlias = true
        // standard suggestion text size
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 19f, resources.displayMetrics)
        loadCustomFont()
        updatePaintColors()
    }

    fun setService(service: MyKeyboardIME) {
        this.service = service
        invalidate()
    }

    fun setSuggestionTextSize(sizeSp: Float) {
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
        requestLayout()
        invalidate()
    }

    fun loadCustomFont() {
        try {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val fontFileName = sharedPrefs.getString("pref_keyboard_font", "AKHAR.ttf.TTF")
            isBoldMode = fontFileName == "default_bold"

            val currentType = service?.keyboardManager?.currentKeyboardType

            if (currentType == KeyboardType.ENGLISH) {
                textPaint.typeface = if (isBoldMode) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                textPaint.isFakeBoldText = isBoldMode
                return
            }

            if (fontFileName == "default") {
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.isFakeBoldText = false
                return
            }
            if (fontFileName == "default_bold") {
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.isFakeBoldText = true
                return
            }

            val customTypeface = try {
                Typeface.createFromAsset(context.assets, "fonts/$fontFileName")
            } catch (e: Exception) {
                val altName = if (fontFileName?.endsWith(".ttf", true) == true) {
                    if (fontFileName.endsWith(".ttf")) fontFileName.replace(".ttf", ".TTF")
                    else fontFileName.replace(".TTF", ".ttf")
                } else fontFileName
                try { Typeface.createFromAsset(context.assets, "fonts/$altName") } catch (e2: Exception) { null }
            }

            if (customTypeface != null) {
                textPaint.typeface = customTypeface
                textPaint.isFakeBoldText = false
            } else {
                textPaint.typeface = Typeface.DEFAULT
                textPaint.isFakeBoldText = false
            }
        } catch (e: Exception) {
            textPaint.typeface = Typeface.DEFAULT
            Log.e("CandidateView", "Error loading font", e)
        }
    }

    private fun updatePaintColors() {
        try {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentTheme = sharedPrefs.getString(ImeConstants.PREF_KEYBOARD_THEME, "light")

            if (currentTheme == "custom") {
                textPaint.color = Color.WHITE
                setBackgroundColor(Color.TRANSPARENT)
            } else {
                applyThemeColors()
                setBackgroundColor(Color.TRANSPARENT) 
            }
        } catch (e: Exception) {
            textPaint.color = Color.BLACK
            setBackgroundColor(Color.TRANSPARENT)
            Log.e("CandidateView", "Error updating colors", e)
        }
    }

    fun setSuggestions(newSuggestions: List<String>, correctionIndex: Int = -1) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        this.correctionIndex = correctionIndex
        loadCustomFont()
        updatePaintColors()
        requestLayout()
        invalidate()
    }

    fun setOnSuggestionClickListener(listener: (String) -> Unit) {
        this.onSuggestionClickListener = listener
    }

    fun setOnSuggestionLongClickListener(listener: (String) -> Unit) {
        this.onSuggestionLongClickListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var totalWidth = paddingLeft + paddingRight
        if (suggestions.isNotEmpty()) {
            for (i in suggestions.indices) {
                val suggestion = suggestions[i]
                val isCorrection = i == correctionIndex
                val oldBold = textPaint.isFakeBoldText
                textPaint.isFakeBoldText = isBoldMode || isCorrection
                totalWidth += textPaint.measureText(suggestion).toInt() + suggestionSpacing.toInt()
                textPaint.isFakeBoldText = oldBold
            }
            totalWidth -= suggestionSpacing.toInt()
        } else {
            totalWidth += textPaint.measureText("ਜੀ ਆਇਆਂ ਨੂੰ").toInt()
        }

        val resolvedWidth = resolveSize(maxOf(totalWidth, 1), widthMeasureSpec)
        // Match standard row height (40dp)
        val standardHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics).toInt()
        setMeasuredDimension(resolvedWidth, standardHeightPx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val y = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

        if (suggestions.isEmpty()) {
            val welcomeMsg = "ਜੀ ਆਇਆਂ ਨੂੰ"
            textPaint.isFakeBoldText = false
            applyThemeColors()
            val textWidth = textPaint.measureText(welcomeMsg)
            val x = (width - textWidth) / 2f
            canvas.drawText(welcomeMsg, x, y, textPaint)
            return
        }

        var currentX = paddingLeft.toFloat()
        for (i in suggestions.indices) {
            val suggestion = suggestions[i]
            val isCorrection = i == correctionIndex
            
            textPaint.isFakeBoldText = isBoldMode || isCorrection
            applyThemeColors()
            
            // If it's a correction, we could also use a slightly different color if theme allows,
            // but bolding is a good standard for keyboards.
            
            canvas.drawText(suggestion, currentX, y, textPaint)
            currentX += textPaint.measureText(suggestion) + suggestionSpacing
        }
    }

    private fun applyThemeColors() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isCustomTheme = sharedPrefs.getString(ImeConstants.PREF_KEYBOARD_THEME, "light") == "custom"
        if (isCustomTheme) {
            textPaint.color = Color.WHITE
        } else {
            val textColorValue = TypedValue()
            if (context.theme.resolveAttribute(R.attr.functionalKeyTextColor, textColorValue, true) ||
                context.theme.resolveAttribute(R.attr.keyboardKeyTextColor, textColorValue, true)) {
                textPaint.color = if (textColorValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && textColorValue.type <= TypedValue.TYPE_LAST_COLOR_INT) textColorValue.data else Color.BLACK
            } else {
                textPaint.color = Color.BLACK
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (suggestions.isEmpty()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                touchedSuggestionIndex = -1
                isLongPressHandled(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (Math.abs(event.x - startX) > touchSlop || Math.abs(event.y - startY) > touchSlop) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                if (!isLongPressTriggered && touchedSuggestionIndex != -1 && touchedSuggestionIndex < suggestions.size) {
                    onSuggestionClickListener?.invoke(suggestions[touchedSuggestionIndex])
                }
                touchedSuggestionIndex = -1
                isLongPressTriggered = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                touchedSuggestionIndex = -1
                isLongPressTriggered = false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isLongPressHandled(event: MotionEvent) {
        var currentX = paddingLeft.toFloat()
        for (i in suggestions.indices) {
            val textWidth = textPaint.measureText(suggestions[i])
            val suggestionRight = currentX + textWidth
            if (event.x >= currentX && event.x <= suggestionRight) {
                touchedSuggestionIndex = i
                break
            }
            currentX = suggestionRight + suggestionSpacing
        }

        if (touchedSuggestionIndex != -1) {
            longPressHandler.postDelayed(longPressRunnable, 500)
        }
    }
}
