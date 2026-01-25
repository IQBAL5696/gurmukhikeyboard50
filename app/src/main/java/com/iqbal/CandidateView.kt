package com.iqbal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.ArrayList
import android.util.TypedValue
import android.os.Handler
import android.os.Looper
import com.iqbal.gurmukhikeyboard50.R

class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val suggestions = ArrayList<String>()
    private val textPaint = TextPaint()
    private var onSuggestionClickListener: ((String) -> Unit)? = null
    private var onSuggestionLongClickListener: ((String) -> Unit)? = null
    private val suggestionSpacing = 40f

    private var touchedSuggestionIndex = -1
    private var startX = 0f
    private var startY = 0f
    private val touchSlop = 10f
    private var isLongPressTriggered = false

    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (touchedSuggestionIndex != -1 && touchedSuggestionIndex < suggestions.size) {
            isLongPressTriggered = true
            onSuggestionLongClickListener?.invoke(suggestions[touchedSuggestionIndex].trim())
            touchedSuggestionIndex = -1
        }
    }

    init {
        val textColorValue = TypedValue()
        if (context.theme.resolveAttribute(R.attr.candidatesTextColor, textColorValue, true)) {
            textPaint.color = textColorValue.data
        } else {
            textPaint.color = Color.BLACK
        }
        textPaint.textSize = 45f
        textPaint.isAntiAlias = true

        val backgroundColorValue = TypedValue()
        if (context.theme.resolveAttribute(R.attr.candidatesBackground, backgroundColorValue, true)) {
            setBackgroundColor(backgroundColorValue.data)
        } else {
            setBackgroundColor(Color.WHITE)
        }
    }

    fun setSuggestions(newSuggestions: List<String>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
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
            for (suggestion in suggestions) {
                totalWidth += textPaint.measureText(suggestion).toInt() + suggestionSpacing.toInt()
            }
            totalWidth -= suggestionSpacing.toInt()
        }

        val resolvedWidth = resolveSize(totalWidth, widthMeasureSpec)
        val desiredHeight = (textPaint.fontSpacing + paddingTop + paddingBottom).toInt()
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (suggestions.isEmpty()) return

        val y = paddingTop + textPaint.fontSpacing / 2 + textPaint.descent() + (height - paddingTop - paddingBottom - textPaint.fontSpacing) / 2
        var currentX = paddingLeft.toFloat()

        for (suggestion in suggestions) {
            canvas.drawText(suggestion, currentX, y, textPaint)
            currentX += textPaint.measureText(suggestion) + suggestionSpacing
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                touchedSuggestionIndex = -1
                isLongPressTriggered = false

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
                    onSuggestionClickListener?.invoke(suggestions[touchedSuggestionIndex].trim())
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
}
