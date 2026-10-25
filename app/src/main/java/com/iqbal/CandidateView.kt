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
    private val dividerPaint = Paint()
    private var onSuggestionClickListener: ((String, Int) -> Unit)? = null
    private var onSuggestionLongClickListener: ((String) -> Unit)? = null
    private var segmentWidth = 0f

    private var touchedSuggestionIndex = -1
    private var correctionIndex = -1
    private val highlightedIndices = mutableSetOf<Int>()
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
        
        dividerPaint.isAntiAlias = true
        dividerPaint.strokeWidth = 2f
        
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
                dividerPaint.color = Color.argb(80, 255, 255, 255)
                setBackgroundColor(Color.TRANSPARENT)
            } else {
                applyThemeColors()
                setBackgroundColor(Color.TRANSPARENT) 
            }
        } catch (e: Exception) {
            textPaint.color = Color.BLACK
            dividerPaint.color = Color.LTGRAY
            setBackgroundColor(Color.TRANSPARENT)
            Log.e("CandidateView", "Error updating colors", e)
        }
    }

    fun setSuggestions(newSuggestions: List<String>, correctionIndex: Int = -1, highlighted: List<Int> = emptyList()) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        this.correctionIndex = correctionIndex
        this.highlightedIndices.clear()
        this.highlightedIndices.addAll(highlighted)
        loadCustomFont()
        updatePaintColors()
        requestLayout()
        invalidate()
    }

    fun setOnSuggestionClickListener(listener: (String, Int) -> Unit) {
        this.onSuggestionClickListener = listener
    }

    fun setOnSuggestionLongClickListener(listener: (String) -> Unit) {
        this.onSuggestionLongClickListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        segmentWidth = (parentWidth / 3f)
        
        // Match standard row height (40dp)
        val standardHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics).toInt()
        setMeasuredDimension(parentWidth, standardHeightPx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

        if (suggestions.isEmpty()) {
            val welcomeMsg = "ਜੀ ਆਇਆਂ ਨੂੰ"
            textPaint.isFakeBoldText = false
            applyThemeColors()
            val textWidth = textPaint.measureText(welcomeMsg)
            val x = (width - textWidth) / 2f
            canvas.drawText(welcomeMsg, x, centerY, textPaint)
            return
        }

        // Limit drawing to 3 suggestions
        val count = minOf(suggestions.size, 3)
        val typedWord = suggestions.getOrNull(0) ?: ""
        for (i in 0 until count) {
            val suggestion = suggestions[i]
            val isCorrection = (i == correctionIndex || highlightedIndices.contains(i)) && suggestion.isNotEmpty()
            val isSuggestion = i > 0 && suggestion.isNotEmpty()
            
            val segmentLeft = i * segmentWidth
            val textWidth = textPaint.measureText(suggestion)
            
            if (i > 0) {
                applyThemeColors(isSuggestion = true, isCorrection = isCorrection)
                canvas.drawLine(segmentLeft, 10f, segmentLeft, height - 10f, dividerPaint)
            }

            // Correction highlighting: Underline ONLY for the auto-commit word
            if (i == correctionIndex && suggestion.isNotEmpty()) {
                applyThemeColors(isSuggestion = true, isCorrection = true)
                val ux = segmentLeft + (segmentWidth - textWidth) / 2f
                val uy = centerY + 6f
                canvas.drawLine(ux, uy, ux + textWidth, uy, textPaint)
            }

            // Highlighting completion suffix: if i == 1 and suggestion starts with typedWord (e.g. "iq" -> "iqbal")
            if (i == 1 && typedWord.isNotEmpty() && suggestion.startsWith(typedWord, ignoreCase = true) && suggestion.length > typedWord.length) {
                val prefix = suggestion.substring(0, typedWord.length)
                val suffix = suggestion.substring(typedWord.length)
                
                val fullWidth = textPaint.measureText(suggestion)
                var startX = segmentLeft + (segmentWidth - fullWidth) / 2f
                
                // Draw typed prefix ("iq") in standard text color
                applyThemeColors(isSuggestion = false, isCorrection = false)
                canvas.drawText(prefix, startX, centerY, textPaint)
                startX += textPaint.measureText(prefix)
                
                // Draw completion suffix ("bal") in vibrant electric blue
                applyThemeColors(isSuggestion = true, isCorrection = isCorrection)
                canvas.drawText(suffix, startX, centerY, textPaint)
            } else {
                applyThemeColors(isSuggestion = isSuggestion, isCorrection = isCorrection)
                val x = segmentLeft + (segmentWidth - textWidth) / 2f
                canvas.drawText(suggestion, x, centerY, textPaint)
            }
        }
    }

    private fun applyThemeColors(isSuggestion: Boolean = false, isCorrection: Boolean = false) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val currentTheme = sharedPrefs.getString(ImeConstants.PREF_KEYBOARD_THEME, "system") ?: "system"
        val isDark = currentTheme in listOf("dark", "midnight", "night_sky", "space", "indigo", "custom")
        val isCustomTheme = currentTheme == "custom"
        
        val baseTypeface = textPaint.typeface
        if (isCorrection || isSuggestion) {
            textPaint.typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textPaint.isFakeBoldText = true
        } else {
            textPaint.typeface = if (isBoldMode) Typeface.create(baseTypeface, Typeface.BOLD) else Typeface.create(baseTypeface, Typeface.NORMAL)
            textPaint.isFakeBoldText = isBoldMode
        }

        if (isSuggestion || isCorrection) {
            textPaint.color = Color.parseColor("#1565C0") // Deep Dark Blue
            dividerPaint.color = if (isDark) Color.argb(80, 255, 255, 255) else Color.argb(40, 0, 0, 0)
        } else if (isCustomTheme) {
            textPaint.color = Color.WHITE
            dividerPaint.color = Color.argb(80, 255, 255, 255)
        } else {
            val textColorValue = TypedValue()
            val themeTextColor = if (context.theme.resolveAttribute(R.attr.candidatesTextColor, textColorValue, true) ||
                context.theme.resolveAttribute(R.attr.keyboardKeyTextColor, textColorValue, true) ||
                context.theme.resolveAttribute(R.attr.functionalKeyTextColor, textColorValue, true)) {
                if (textColorValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && textColorValue.type <= TypedValue.TYPE_LAST_COLOR_INT) textColorValue.data else Color.BLACK
            } else {
                Color.BLACK
            }
            textPaint.color = themeTextColor
            dividerPaint.color = Color.argb(40, 0, 0, 0)
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
                    onSuggestionClickListener?.invoke(suggestions[touchedSuggestionIndex], touchedSuggestionIndex)
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
        if (segmentWidth <= 0) return
        val index = (event.x / segmentWidth).toInt()
        if (index >= 0 && index < minOf(suggestions.size, 3)) {
            touchedSuggestionIndex = index
            longPressHandler.postDelayed(longPressRunnable, 500)
        }
    }
}
