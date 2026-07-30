package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.DynamicDrawableSpan
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat

object GurbaniUIUtils {

    fun applyIkOnkarToSpannable(context: Context, spannable: SpannableStringBuilder, fontSizePx: Float, isLarivaar: Boolean): CharSequence {
        // Handle spacing and prevent line breaks between Ik Onkar and the next word
        // Process backwards to maintain correct indices while modifying the string
        var i = spannable.length - 1
        while (i >= 0) {
            if (spannable[i] == 'ੴ') {
                // 1. Handle trailing spacing (Prevent line break after Ik Onkar)
                if (i + 1 < spannable.length) {
                    val nextChar = spannable[i + 1]
                    if (nextChar == ' ') {
                        if (isLarivaar) {
                            spannable.replace(i + 1, i + 1 + 1, "\u2060")
                        } else {
                            spannable.replace(i + 1, i + 1 + 1, "\u00A0")
                        }
                    } else if (nextChar != '\u2060' && nextChar != '\u00A0' && nextChar != '\n') {
                        spannable.insert(i + 1, "\u2060")
                    }
                }

                // 2. Handle preceding spacing (Add one-line gap before Ik Onkar)
                // Remove horizontal whitespace before ੴ
                var p = i - 1
                while (p >= 0 && (spannable[p] == ' ' || spannable[p] == '\u00A0' || spannable[p] == '\u2060' || spannable[p] == '\u200B')) {
                    spannable.delete(p, p + 1)
                    i-- // Adjust i as we deleted a char before it
                    p--
                }

                // Ensure exactly two newlines (one-line gap) before ੴ, unless it's at the start
                if (i > 0) {
                    var nlCount = 0
                    var p2 = i - 1
                    while (p2 >= 0 && spannable[p2] == '\n') {
                        nlCount++
                        p2--
                    }

                    if (nlCount < 2) {
                        val needed = 2 - nlCount
                        spannable.insert(i, "\n".repeat(needed))
                        // i moves forward with newlines, but while loop i-- will check them
                    } else if (nlCount > 2) {
                        // Collapse extra newlines to exactly 2
                        val extra = nlCount - 2
                        spannable.delete(i - extra, i)
                        i -= extra
                    }
                }
            }
            i--
        }

        val text = spannable.toString()
        if (text.contains("ੴ")) {
            val baseDrawable = ContextCompat.getDrawable(context, R.drawable.ik_onkar_svg)
            if (baseDrawable != null) {
                // Optimal height multiplier for Ik Onkar
                val h = (fontSizePx * 2f).toInt()
                val ratio = baseDrawable.intrinsicWidth.toFloat() / baseDrawable.intrinsicHeight.toFloat()
                val w = (h * ratio).toInt()
                var index = text.indexOf("ੴ")
                while (index >= 0) {
                    val drawable = baseDrawable.constantState?.newDrawable()?.mutate() ?: baseDrawable
                    drawable.setBounds(0, 0, w, h)
                    spannable.setSpan(
                        BaselineImageSpan(drawable, 0, isLarivaar),
                        index,
                        index + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    index = text.indexOf("ੴ", index + 1)
                }
            }
        }
        return spannable
    }

    private class BaselineImageSpan(private val drawable: Drawable, private val extraPadding: Int, private val isLarivaar: Boolean)
        : DynamicDrawableSpan(ALIGN_BASELINE) {

        override fun getDrawable(): Drawable = drawable

        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            val rect = drawable.bounds
            if (fm != null) {
                val pfm = paint.fontMetricsInt
                fm.ascent = pfm.ascent
                fm.descent = pfm.descent
                fm.top = pfm.top
                fm.bottom = pfm.bottom
            }
            
            // In Larivaar mode: width reduced to 0.62f so the next word slides under the 'kar' tail (Connected appearance)
            // In Pad-ched mode: width set to 0.72f as requested
            val widthMultiplier = if (isLarivaar) 0.62f else 0.72f
            return (rect.width() * widthMultiplier).toInt() + extraPadding
        }

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            // Find the correct color from text spans to match the dynamic color changes
            var tint = paint.color
            if (text is Spanned) {
                val spans = text.getSpans(start, end, CharacterStyle::class.java)
                for (span in spans) {
                    if (span is GurbaniSearchHelper.SharedColorSpan) {
                        tint = span.dynamicColor.color
                        break
                    } else if (span is ForegroundColorSpan) {
                        tint = span.foregroundColor
                        break
                    }
                }
            }

            drawable.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(tint, BlendModeCompat.SRC_IN)

            canvas.save()
            val fm = paint.fontMetricsInt
            val drawableHeight = drawable.bounds.height()
            val transY = y - drawableHeight + (fm.descent - fm.ascent) * 0.001f
            canvas.translate(x, transY)
            drawable.draw(canvas)
            canvas.restore()
        }
    }
}
