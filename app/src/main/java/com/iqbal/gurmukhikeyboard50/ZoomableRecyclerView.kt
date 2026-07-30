package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView

class ZoomableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var scaleFactor = 1f
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var translateX = 0f
    private var translateY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = -1

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
                activePointerId = ev.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = ev.getX(pointerIndex)
                    val y = ev.getY(pointerIndex)

                    if (!scaleDetector.isInProgress && scaleFactor > 1f) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY

                        translateX += dx
                        translateY += dy
                        
                        // Limit panning to keep image in view
                        limitTranslation()
                        invalidate()
                        
                        // Consume event if zoomed in to prevent swiping pages while panning
                        lastTouchX = x
                        lastTouchY = y
                        return true
                    }

                    lastTouchX = x
                    lastTouchY = y
                }
            }
            MotionEvent.ACTION_UP -> {
                activePointerId = -1
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = ev.getX(newPointerIndex)
                    lastTouchY = ev.getY(newPointerIndex)
                    activePointerId = ev.getPointerId(newPointerIndex)
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun limitTranslation() {
        val width = width.toFloat()
        val height = height.toFloat()
        
        val maxWidth = (scaleFactor - 1) * width
        val maxHeight = (scaleFactor - 1) * height
        
        translateX = translateX.coerceIn(-maxWidth, 0f)
        translateY = translateY.coerceIn(-maxHeight, 0f)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(1.0f, 5.0f)

            // Keep the zoom centered on the focal point
            val focusX = detector.focusX
            val focusY = detector.focusY
            translateX -= focusX * (detector.scaleFactor - 1) * scaleFactor
            translateY -= focusY * (detector.scaleFactor - 1) * scaleFactor

            limitTranslation()
            invalidate()
            return true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up if needed
    }
}
