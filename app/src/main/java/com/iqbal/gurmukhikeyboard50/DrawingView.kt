package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var drawPath: Path = Path()
    private var drawPaint: Paint = Paint()
    private var paintColor = Color.BLACK
    private var strokeWidth = 10f

    private val paths = mutableListOf<DrawingPath>()
    private val undonePaths = mutableListOf<DrawingPath>()

    private var mX = 0f
    private var mY = 0f
    private val TOUCH_TOLERANCE = 4f

    data class DrawingPath(val path: Path, val color: Int, val strokeWidth: Float)

    init {
        setupDrawing()
    }

    private fun setupDrawing() {
        drawPaint.color = paintColor
        drawPaint.isAntiAlias = true
        drawPaint.strokeWidth = strokeWidth
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeJoin = Paint.Join.ROUND
        drawPaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        // Draw all completed paths
        for (dp in paths) {
            drawPaint.color = dp.color
            drawPaint.strokeWidth = dp.strokeWidth
            canvas.drawPath(dp.path, drawPaint)
        }
        // Draw the current path being drawn
        drawPaint.color = paintColor
        drawPaint.strokeWidth = strokeWidth
        canvas.drawPath(drawPath, drawPaint)
    }

    private fun touchStart(x: Float, y: Float) {
        undonePaths.clear()
        drawPath.reset()
        drawPath.moveTo(x, y)
        mX = x
        mY = y
        invalidate()
    }

    private fun touchMove(x: Float, y: Float) {
        val dx = Math.abs(x - mX)
        val dy = Math.abs(y - mY)
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            drawPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
            mX = x
            mY = y
        }
        invalidate()
    }

    private fun touchUp() {
        drawPath.lineTo(mX, mY)
        // Add current path to history
        paths.add(DrawingPath(Path(drawPath), paintColor, strokeWidth))
        drawPath.reset()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart(x, y)
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchMove(x, y)
            }
            MotionEvent.ACTION_UP -> {
                touchUp()
            }
            else -> return false
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setColor(newColor: Int) {
        paintColor = newColor
        drawPaint.color = paintColor
    }

    fun setStrokeWidth(newWidth: Float) {
        strokeWidth = newWidth
        drawPaint.strokeWidth = strokeWidth
    }

    fun clear() {
        paths.clear()
        undonePaths.clear()
        drawPath.reset()
        invalidate()
    }

    fun undo() {
        if (paths.isNotEmpty()) {
            undonePaths.add(paths.removeAt(paths.size - 1))
            invalidate()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            paths.add(undonePaths.removeAt(undonePaths.size - 1))
            invalidate()
        }
    }
}
