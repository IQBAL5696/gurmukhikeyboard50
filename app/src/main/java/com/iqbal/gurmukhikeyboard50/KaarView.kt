package com.iqbal.gurmukhikeyboard50

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class KaarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#FFD700") // Golden
        strokeWidth = 14f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        // Enhanced Glow Effect
        setShadowLayer(15f, 0f, 0f, Color.parseColor("#80FFD700"))
    }

    private val ikOnkarPath = Path()
    private var progress = 0f
    private val pathMeasure = PathMeasure()
    private var pathLength = 0f

    init {
        // Required for shadow layer to work with hardware acceleration
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startDrawingAnimation()
    }

    private fun startDrawingAnimation() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500 
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            setupCalligraphyPath(w.toFloat(), h.toFloat())
        }
    }

    private fun setupCalligraphyPath(w: Float, h: Float) {
        ikOnkarPath.reset()

        // ੧ (Ek)
        val startX = w * 0.35f
        val startY = h * 0.60f
        ikOnkarPath.moveTo(startX, startY)
        
        ikOnkarPath.cubicTo(w * 0.10f, h * 0.45f, w * 0.25f, h * 0.25f, w * 0.45f, h * 0.45f)
        ikOnkarPath.cubicTo(w * 0.60f, h * 0.60f, w * 0.45f, h * 0.80f, w * 0.30f, h * 0.75f)
        ikOnkarPath.cubicTo(w * 0.15f, h * 0.70f, w * 0.20f, h * 0.50f, w * 0.40f, h * 0.55f)

        // ਓ (Body of Onkar)
        ikOnkarPath.lineTo(w * 0.60f, h * 0.55f)
        ikOnkarPath.quadTo(w * 0.75f, h * 0.85f, w * 0.85f, h * 0.55f)
        ikOnkarPath.quadTo(w * 0.75f, h * 0.40f, w * 0.60f, h * 0.50f)
        
        // Upper part of O
        ikOnkarPath.quadTo(w * 0.55f, h * 0.35f, w * 0.70f, h * 0.30f)

        // 'ਕਾਰ' (The top stroke)
        ikOnkarPath.cubicTo(
            w * 0.95f, h * 0.20f, 
            w * 0.50f, h * 0.05f, // Adjusted Y from -0.05 to 0.05 to prevent clipping
            w * 0.10f, h * 0.20f 
        )
        
        pathMeasure.setPath(ikOnkarPath, false)
        pathLength = pathMeasure.length
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pathLength > 0) {
            // Animate the path using DashPathEffect
            val phase = pathLength * (1f - progress)
            paint.pathEffect = DashPathEffect(floatArrayOf(pathLength, pathLength), phase)
            canvas.drawPath(ikOnkarPath, paint)
        }
    }
}
