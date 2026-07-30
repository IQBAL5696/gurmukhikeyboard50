package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.PreferenceManager
import kotlin.math.abs

class MyKeyboardView(context: Context, attrs: AttributeSet?) : KeyboardView(context, attrs) {

    private val textPaint = Paint()
    private val hintPaint = Paint()
    private var customTypeface: Typeface? = null
    private var isBoldMode = false
    
    private var keyboardKeyBackground: Drawable? = null
    private var functionalKeyBackground: Drawable? = null
    private var keyTextColor = Color.BLACK
    private var functionalKeyTextColor = Color.BLACK
    private var iconColor = Color.BLACK
    private var keyTextSize: Float = 0f
    private var keyPressedColor = 0x44FFFFFF
    
    private var keyPreviewBgColor = Color.WHITE
    private var keyPreviewTextColor = Color.BLACK
    
    private lateinit var gestureDetector: GestureDetector
    private var service: MyKeyboardIME? = null

    private var spacebarStartX = 0f
    private var isSpacebarDragging = false
    private var isSwipeDetected = false
    private val moveThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15f, context.resources.displayMetrics)
    private var lastMoveX = 0f

    private var previewPopup: PopupWindow? = null
    private var previewTextView: TextView? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismissKeyPreview() }
    private val POPUP_DISMISS_DELAY = 50L

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressKey: Keyboard.Key? = null
    private var longPressMenuPopup: PopupWindow? = null
    private var isLongPressHandled = false
    private var variantTextViews = mutableListOf<TextView>()
    private var highlightedVariantIndex = -1

    private val LONG_PRESS_TIMEOUT = 400L
    private val longPressRunnable = Runnable {
        longPressKey?.let { key ->
            if (getVariantsForKey(key).isNotEmpty()) {
                isLongPressHandled = true
                showLongPressMenu(key)
                unpressAllKeys()
                val now = SystemClock.uptimeMillis()
                val cancelEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                super.onTouchEvent(cancelEvent)
                cancelEvent.recycle()
            }
        }
    }

    private val gesturePath = mutableListOf<String>()
    private var isGestureTyping = false
    private val swipePath = Path()
    private val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4") // Google Blue
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        alpha = 180
    }

    init {
        isProximityCorrectionEnabled = false
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        
        hintPaint.isAntiAlias = true
        hintPaint.textAlign = Paint.Align.CENTER
        hintPaint.alpha = 100

        loadCustomFont()
        resolveThemeAttributes()
        isPreviewEnabled = false

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || isSpacebarDragging) return false
                val dx = e2.x - e1.x; val dy = e2.y - e1.y; val swipeThreshold = 100; val swipeVelocityThreshold = 100
                if (abs(dx) > abs(dy)) { 
                    if (abs(dx) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) { 
                        isSwipeDetected = true
                        if (dx > 0) onKeyboardActionListener?.swipeRight() else onKeyboardActionListener?.swipeLeft()
                        return true 
                    } 
                }
                return false
            }
        })
    }

    fun loadCustomFont() {
        try {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val fontFileName = sharedPrefs.getString("pref_keyboard_font", "AKHAR.ttf.TTF")
            isBoldMode = fontFileName == "default_bold"
            
            if (fontFileName == "default") {
                customTypeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.typeface = customTypeface
                hintPaint.typeface = customTypeface
                textPaint.isFakeBoldText = false
                return
            }

            if (fontFileName == "default_bold") {
                customTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.typeface = customTypeface
                hintPaint.typeface = customTypeface
                textPaint.isFakeBoldText = true
                return
            }

            customTypeface = try {
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
                hintPaint.typeface = customTypeface
                textPaint.isFakeBoldText = false
            } else {
                textPaint.typeface = Typeface.DEFAULT
            }
        } catch (e: Exception) {
            customTypeface = null
            textPaint.typeface = Typeface.DEFAULT
            Log.e("MyKeyboardView", "Error loading font", e)
        }
    }

    fun unpressAllKeys() {
        val keys = keyboard?.keys ?: return
        for (key in keys) {
            key.pressed = false
        }
        invalidateAllKeys()
        invalidate()
    }

    fun setService(service: MyKeyboardIME) { this.service = service; resolveThemeAttributes(); invalidateAllKeys() }

    private fun resolveThemeAttributes() {
        try {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentTheme = sharedPrefs.getString(ImeConstants.PREF_KEYBOARD_THEME, "light")
            val useRounded = sharedPrefs.getBoolean(ImeConstants.PREF_USE_ROUNDED_KEYS, true)
            val roundnessDp = if (useRounded) sharedPrefs.getInt(ImeConstants.PREF_KEY_ROUNDNESS, 5).toFloat() else 0f
            
            val useTransparencyPref = sharedPrefs.getBoolean(ImeConstants.PREF_USE_KEY_TRANSPARENCY, false)
            var opacity = if (useTransparencyPref) sharedPrefs.getInt(ImeConstants.PREF_KEY_OPACITY, 200) else 255
            
            val typedValue = TypedValue()
            
            if (currentTheme == "custom") {
                opacity = 0
                keyTextColor = Color.WHITE
                functionalKeyTextColor = Color.WHITE
                iconColor = Color.WHITE
                keyPreviewBgColor = Color.WHITE
                keyPreviewTextColor = Color.BLACK
            } else {
                if (context.theme.resolveAttribute(R.attr.keyboardKeyTextColor, typedValue, true)) keyTextColor = typedValue.data
                if (context.theme.resolveAttribute(R.attr.functionalKeyTextColor, typedValue, true)) functionalKeyTextColor = typedValue.data else functionalKeyTextColor = keyTextColor
                if (context.theme.resolveAttribute(R.attr.iconColor, typedValue, true)) iconColor = typedValue.data
                
                if (context.theme.resolveAttribute(R.attr.keyPreviewBackground, typedValue, true)) {
                    keyPreviewBgColor = if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) typedValue.data else Color.WHITE
                } else keyPreviewBgColor = Color.WHITE
                
                if (context.theme.resolveAttribute(R.attr.keyPreviewTextColor, typedValue, true)) keyPreviewTextColor = typedValue.data else keyPreviewTextColor = keyTextColor
            }

            if (context.theme.resolveAttribute(R.attr.keyPressedBackgroundColor, typedValue, true)) keyPressedColor = typedValue.data else keyPressedColor = 0x44FFFFFF
            val density = context.resources.displayMetrics.density; val cornerRadius = roundnessDp * density
            
            if (context.theme.resolveAttribute(R.attr.keyboardKeyBackground, typedValue, true)) { 
                val baseColor = if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) typedValue.data else Color.LTGRAY
                val colorWithAlpha = Color.argb(opacity, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                keyboardKeyBackground = createRoundedDrawable(colorWithAlpha, keyPressedColor, cornerRadius, currentTheme == "custom") 
            }
            
            if (context.theme.resolveAttribute(R.attr.functionalKeyBackground, typedValue, true)) { 
                val baseColor = if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) typedValue.data else Color.GRAY
                val colorWithAlpha = Color.argb(opacity, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                functionalKeyBackground = createRoundedDrawable(colorWithAlpha, keyPressedColor, cornerRadius, currentTheme == "custom") 
            }
            
            hintPaint.color = keyTextColor
        } catch (e: Exception) {
            Log.e("MyKeyboardView", "Error resolving theme attributes", e)
        }
    }

    private fun createRoundedDrawable(color: Int, pressedColor: Int, radius: Float, isCustomTheme: Boolean = false): Drawable {
        val topDrawable = GradientDrawable().apply { 
            shape = GradientDrawable.RECTANGLE
            setColor(color) 
            cornerRadius = radius 
            if (isCustomTheme) {
                setStroke(1, Color.argb(40, 255, 255, 255))
            }
        }
        
        val normalDrawable = if (isCustomTheme) {
            topDrawable
        } else {
            val elevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.5f, context.resources.displayMetrics).toInt()
            val shadowColor = darkenColor(color)
            val shadowDrawable = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(shadowColor); cornerRadius = radius }
            val layerDrawable = LayerDrawable(arrayOf(shadowDrawable, topDrawable))
            layerDrawable.setLayerInset(1, 0, 0, 0, elevation)
            layerDrawable
        }

        val pressedDrawable = GradientDrawable().apply { 
            shape = GradientDrawable.RECTANGLE
            setColor(if (isCustomTheme) Color.argb(60, 255, 255, 255) else pressedColor)
            cornerRadius = radius 
        }
        
        val stateListDrawable = StateListDrawable()
        stateListDrawable.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
        stateListDrawable.addState(intArrayOf(), normalDrawable)
        return stateListDrawable
    }

    private fun darkenColor(color: Int): Int { val hsv = FloatArray(3); Color.colorToHSV(color, hsv); hsv[2] *= 0.75f; return Color.HSVToColor(hsv) }
    fun setKeyTextSize(size: Float) { this.keyTextSize = size; invalidate() }
    override fun setKeyboard(newKeyboard: Keyboard?) { super.setKeyboard(newKeyboard); loadCustomFont(); resolveThemeAttributes(); invalidate() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); setPopupParent(this); setPopupOffset(0, 0) }

    private class KeyPreviewDrawable(
        private val bgColor: Int,
        private val radius: Float,
        private val density: Float,
        private val keyWidth: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4285F4")
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        override fun draw(canvas: Canvas) {
            val r = bounds
            val w = r.width().toFloat()
            val h = r.height().toFloat()
            val bubbleHeight = h * 0.65f
            
            val path = Path()
            path.moveTo(radius, 0f)
            path.lineTo(w - radius, 0f)
            path.quadTo(w, 0f, w, radius)
            path.lineTo(w, bubbleHeight - radius)
            path.quadTo(w, bubbleHeight, w - radius, bubbleHeight)
            
            // Stem/Tail connecting to key (Rectangle)
            val centerX = w / 2f
            val halfKey = keyWidth / 2f

            path.lineTo(centerX + halfKey, bubbleHeight)
            path.lineTo(centerX + halfKey, h)
            path.lineTo(centerX - halfKey, h)
            path.lineTo(centerX - halfKey, bubbleHeight)
            
            path.lineTo(radius, bubbleHeight)
            path.quadTo(0f, bubbleHeight, 0f, bubbleHeight - radius)
            path.lineTo(0f, radius)
            path.quadTo(0f, 0f, radius, 0f)
            path.close()

            canvas.drawPath(path, paint)
            canvas.drawPath(path, strokePaint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun showKeyPreview(key: Keyboard.Key) {
        if (isLongPressHandled) return
        dismissHandler.removeCallbacks(dismissRunnable); val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context); val popupEnabled = sharedPrefs.getBoolean("popup_on_keypress", true)
        if (!popupEnabled || isFunctional(key) || (key.label == null && key.icon == null)) { dismissKeyPreview(); return }
        val scale = sharedPrefs.getInt("popup_size_scale", 100) / 100f
        if (previewPopup == null) {
            try {
                val view = LayoutInflater.from(context).inflate(R.layout.small_key_preview, null)
                previewTextView = view as TextView
                previewPopup = PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                previewPopup?.isClippingEnabled = false; previewPopup?.isTouchable = false; previewPopup?.animationStyle = 0
            } catch (e: Exception) {
                Log.e("MyKeyboardView", "Failed to inflate key preview", e)
                return
            }
        }

        previewTextView?.text = key.label?.toString() ?: ""
        previewTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 45f * scale)
        
        val density = context.resources.displayMetrics.density
        val radius = 14f * density
        
        // Ensure popup width is at least key width plus room for rounded corners
        val popupWidth = maxOf((60 * density * scale).toInt(), (key.width + radius * 2).toInt())
        val popupHeight = (100 * density * scale).toInt()
        
        previewTextView?.background = KeyPreviewDrawable(keyPreviewBgColor, radius, density, key.width.toFloat())
        previewTextView?.setTextColor(keyPreviewTextColor)
        previewTextView?.gravity = Gravity.CENTER
        previewTextView?.setPadding(0, 0, 0, (popupHeight * 0.45f).toInt())
        
        val currentType = service?.keyboardManager?.currentKeyboardType
        if (currentType == KeyboardType.ENGLISH || currentType == KeyboardType.NUMPAD) {
            previewTextView?.typeface = if (isBoldMode) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        } else {
            previewTextView?.typeface = customTypeface ?: Typeface.DEFAULT
        }
        
        previewTextView?.minWidth = popupWidth; previewTextView?.minHeight = popupHeight
        
        val location = IntArray(2); getLocationInWindow(location)
        val x = location[0] + key.x + (key.width - popupWidth) / 2
        val y = location[1] + key.y - popupHeight + (10 * density).toInt() // Overlap slightly to look connected

        try {
            if (previewPopup?.isShowing == true) previewPopup?.update(x, y, popupWidth, popupHeight) else previewPopup?.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        } catch (e: Exception) {
            Log.e("MyKeyboardView", "Error showing preview popup", e)
        }
    }

    private fun dismissKeyPreview() { try { previewPopup?.dismiss() } catch (e: Exception) {} }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); dismissHandler.removeCallbacks(dismissRunnable); dismissKeyPreview() }

    private fun showLongPressMenu(key: Keyboard.Key) {
        val variants = getVariantsForKey(key)
        if (variants.isEmpty()) return
        dismissKeyPreview()
        variantTextViews.clear()
        highlightedVariantIndex = 0 // Auto-highlight first variant for faster selection

        val context = context; val density = context.resources.displayMetrics.density
        val menuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            val drawable = GradientDrawable().apply { 
                setColor(keyPreviewBgColor)
                cornerRadius = 16 * density
                setStroke((2 * density).toInt(), Color.parseColor("#4285F4")) 
            }
            background = drawable; elevation = 10 * density
        }

        val currentType = service?.keyboardManager?.currentKeyboardType
        val isEnglishOrNumpad = currentType == KeyboardType.ENGLISH || currentType == KeyboardType.NUMPAD

        for (i in variants.indices) {
            val variant = variants[i]
            val tv = TextView(context).apply {
                text = variant; textSize = 28f; setPadding((20 * density).toInt(), (10 * density).toInt(), (20 * density).toInt(), (10 * density).toInt()); setTextColor(keyPreviewTextColor); isClickable = true
                
                if (isEnglishOrNumpad) {
                    typeface = if (isBoldMode) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                } else {
                    typeface = customTypeface ?: Typeface.DEFAULT
                }

                if (i == 0) {
                    setBackgroundColor(if (keyPreviewBgColor == Color.WHITE) Color.parseColor("#DDDDDD") else Color.parseColor("#444444"))
                } else {
                    val stateList = StateListDrawable(); stateList.addState(intArrayOf(android.R.attr.state_pressed), android.graphics.drawable.ColorDrawable(if (keyPreviewBgColor == Color.WHITE) Color.parseColor("#EEEEEE") else Color.parseColor("#555555"))); background = stateList
                }
            }
            menuLayout.addView(tv)
            variantTextViews.add(tv)
        }

        try {
            longPressMenuPopup = PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                isOutsideTouchable = true; isFocusable = false
                animationStyle = 0
                val location = IntArray(2); getLocationInWindow(location)
                menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val popupWidth = menuLayout.measuredWidth; val popupHeight = menuLayout.measuredWidth // Use width for height approximation or proper measure
                val x = location[0] + key.x + (key.width - popupWidth) / 2
                val y = location[1] + key.y - menuLayout.measuredHeight - (10 * density).toInt()
                showAtLocation(this@MyKeyboardView, Gravity.NO_GRAVITY, x, y)
            }
        } catch (e: Exception) {
            Log.e("MyKeyboardView", "Error showing long press menu", e)
        }
    }

    private fun getVariantsForKey(key: Keyboard.Key): List<String> {
        val variants = mutableListOf<String>()
        val currentType = service?.keyboardManager?.currentKeyboardType
        val isGurmukhi = currentType == KeyboardType.GURMUKHI

        if (key is MyKey && key.shiftedCode != 0 && isGurmukhi) {
            val shiftedLabel = when (key.shiftedCode) { -2002 -> "ਾਂ"; -2003 -> "੍ਰ"; -2004 -> "੍ਹ"; -2005 -> "੍ਵ"; -2006 -> "੍ਯ"; else -> key.shiftedCode.toChar().toString() }
            variants.add(shiftedLabel)
        }
        val label = key.label?.toString() ?: ""
        val extraVariants = if (isGurmukhi) {
            when (label) {
                "ੳ" -> listOf("ਉ", "ਊ", "ਓ"); "ਅ" -> listOf("ਆ", "ਐ", "ਔ"); "ੲ" -> listOf("ਇ", "ਈ", "ਏ"); "ਸ" -> listOf("ਸ਼"); "ਖ" -> listOf("ਖ਼"); "ਗ" -> listOf("ਗ਼"); "ਜ" -> listOf("ਜ਼"); "ਫ" -> listOf("ਫ਼"); "ਲ" -> listOf("ਲ਼"); "1" -> listOf("੧", "①"); "2" -> listOf("੨", "②"); "3" -> listOf("੩", "③"); "?" -> listOf("!", "¿", "¡"); "." -> listOf("।", "...", ",")
                else -> emptyList()
            }
        } else {
            when (label) {
                "a" -> listOf("á", "à", "â", "ä", "æ", "ã", "å", "ā"); "e" -> listOf("é", "è", "ê", "ë", "ē", "ė", "ę"); "i" -> listOf("í", "ì", "î", "ï", "ī", "į"); "o" -> listOf("ó", "ò", "ô", "ö", "õ", "ø", "œ", "ō"); "u" -> listOf("ú", "ù", "û", "ü", "ū"); "s" -> listOf("ß", "ś", "š"); "n" -> listOf("ñ", "ń"); "c" -> listOf("ç", "ć", "č"); "y" -> listOf("ÿ"); "1" -> listOf("①", "¹"); "2" -> listOf("②", "²"); "3" -> listOf("③", "³"); "?" -> listOf("!", "¿", "¡"); "." -> listOf("...", ",", ":", ";")
                else -> emptyList()
            }
        }
        for (v in extraVariants) { if (!variants.contains(v)) variants.add(v) }
        return variants
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        val x = me.x.toInt() - paddingLeft; val y = me.y.toInt() - paddingTop
        gestureDetector.onTouchEvent(me)
        when (me.action) {
            MotionEvent.ACTION_DOWN -> {
                isSwipeDetected = false
                isLongPressHandled = false
                gesturePath.clear()
                isGestureTyping = false
                swipePath.reset()
                swipePath.moveTo(me.x, me.y)
                val key = findKeyAt(x, y)
                if (key != null) {
                    if (!isFunctional(key)) {
                        key.label?.toString()?.let { gesturePath.add(it) }
                    }
                    if (key.codes.contains(32)) { spacebarStartX = me.x; lastMoveX = me.x; isSpacebarDragging = false } else { spacebarStartX = -1f }
                    showKeyPreview(key); longPressKey = key; longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
                } else { spacebarStartX = -1f }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLongPressHandled) {
                    val screenX = me.rawX; val screenY = me.rawY; var found = -1
                    for (i in variantTextViews.indices) {
                        val tv = variantTextViews[i]; val viewLoc = IntArray(2); tv.getLocationOnScreen(viewLoc)
                        val rect = Rect(viewLoc[0], viewLoc[1], viewLoc[0] + tv.width, viewLoc[1] + tv.height)
                        if (rect.contains(screenX.toInt(), screenY.toInt())) { found = i; break }
                    }
                    if (found != highlightedVariantIndex) {
                        highlightedVariantIndex = found
                        for (i in variantTextViews.indices) { variantTextViews[i].setBackgroundColor(if (i == highlightedVariantIndex) (if (keyPreviewBgColor == Color.WHITE) Color.parseColor("#DDDDDD") else Color.parseColor("#444444")) else keyPreviewBgColor) }
                    }
                    return true
                }
                if (spacebarStartX != -1f) {
                    val totalDx = me.x - spacebarStartX
                    if (isSpacebarDragging || abs(totalDx) > moveThreshold * 1.2f) {
                        isSpacebarDragging = true; val dxSinceLast = me.x - lastMoveX
                        if (abs(dxSinceLast) > moveThreshold) {
                            val listener = onKeyboardActionListener
                            if (listener is MyKeyboardActionListener) {
                                if (dxSinceLast > 0) listener.moveCursor(1)
                                else listener.moveCursor(-1)
                            }
                            lastMoveX = me.x
                        }
                        dismissKeyPreview(); longPressHandler.removeCallbacks(longPressRunnable); return true
                    }
                }
                
                val key = findKeyAt(x, y)
                if (key != null) {
                    showKeyPreview(key)
                    if (!isFunctional(key) && !isSpacebarDragging) {
                        val label = key.label?.toString()
                        if (label != null && (gesturePath.isEmpty() || gesturePath.last() != label)) {
                            gesturePath.add(label)
                            if (gesturePath.size > 1) {
                                isGestureTyping = true
                                dismissKeyPreview()
                                longPressHandler.removeCallbacks(longPressRunnable)
                            }
                        }
                    }
                } else {
                    dismissKeyPreview()
                }

                if (isGestureTyping) {
                    swipePath.lineTo(me.x, me.y)
                    invalidate()
                }

                longPressKey?.let { if (!it.isInside(x, y)) longPressHandler.removeCallbacks(longPressRunnable) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                dismissHandler.postDelayed(dismissRunnable, POPUP_DISMISS_DELAY)
                
                if (isGestureTyping && gesturePath.size >= 2) {
                    service?.handleGestureSequence(gesturePath)
                    gesturePath.clear()
                    isGestureTyping = false
                    swipePath.reset()
                    invalidate()
                    unpressAllKeys()
                    return true
                }

                swipePath.reset()
                invalidate()

                if (isSwipeDetected) {
                    isSwipeDetected = false
                    unpressAllKeys()
                    return true
                }

                if (isLongPressHandled) {
                    if (highlightedVariantIndex != -1) { onKeyboardActionListener?.onText(variantTextViews[highlightedVariantIndex].text) }
                    try { longPressMenuPopup?.dismiss() } catch (e: Exception) {}
                    isLongPressHandled = false; unpressAllKeys(); return true
                }

                if (isSpacebarDragging) { isSpacebarDragging = false; spacebarStartX = -1f; unpressAllKeys(); val cancelEvent = MotionEvent.obtain(me); cancelEvent.action = MotionEvent.ACTION_CANCEL; super.onTouchEvent(cancelEvent); cancelEvent.recycle(); return true }
                
                spacebarStartX = -1f
                val result = super.onTouchEvent(me)
                unpressAllKeys()
                return result
            }
        }
        return super.onTouchEvent(me)
    }

    private fun findKeyAt(x: Int, y: Int): Keyboard.Key? {
        val keys = keyboard?.keys ?: return null
        for (key in keys) if (key.isInside(x, y)) return key
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics).toInt()
        for (key in keys) if (x >= key.x - margin && x < key.x + key.width + margin && y >= key.y - margin && y < key.y + key.height + margin) return key
        return null
    }

    override fun onDraw(canvas: Canvas) {
        val keyboard = this.keyboard ?: return
        val isShifted = keyboard.isShifted; val keys = keyboard.keys
        val currentType = service?.keyboardManager?.currentKeyboardType
        
        for (key in keys) {
            val isFunc = isFunctional(key)
            val backgroundDrawable = if (isFunc) functionalKeyBackground else keyboardKeyBackground
            val textColor = if (isFunc) functionalKeyTextColor else keyTextColor
            
            backgroundDrawable?.let { 
                it.state = if (key.pressed) intArrayOf(android.R.attr.state_pressed) else intArrayOf()
                it.setBounds(key.x + paddingLeft, key.y + paddingTop, key.x + key.width + paddingLeft, key.y + key.height + paddingTop)
                it.draw(canvas) 
            }

            if (currentType == KeyboardType.ENGLISH || currentType == KeyboardType.NUMPAD) {
                textPaint.typeface = if (isBoldMode) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                hintPaint.typeface = if (isBoldMode) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                textPaint.isFakeBoldText = isBoldMode
            } else {
                textPaint.typeface = customTypeface ?: Typeface.DEFAULT
                hintPaint.typeface = customTypeface ?: Typeface.DEFAULT
                textPaint.isFakeBoldText = isBoldMode
            }

            val codes = key.codes; val isSpacebar = codes.contains(32)
            if (isSpacebar) {
                val label = key.label?.toString() ?: if (currentType == KeyboardType.GURMUKHI) "ਪੰਜਾਬੀ" else if (currentType == KeyboardType.ENGLISH) "English" else ""
                
                if (label.isNotEmpty()) {
                    textPaint.color = textColor; textPaint.textSize = key.height * 0.35f; textPaint.alpha = 180; 
                    val x = key.x + (key.width / 2f) + paddingLeft; val y = key.y + (key.height / 2f) + paddingTop - ((textPaint.descent() + textPaint.ascent()) / 2f); canvas.drawText(label, x, y, textPaint)
                } else if (key.icon != null) {
                    drawIcon(canvas, key.icon, key)
                }
            } else if (key.icon != null) {
                drawIcon(canvas, key.icon, key)
            } else if (key.label != null) {
                textPaint.color = textColor; textPaint.alpha = 255
                if (!isShifted && !isFunc && key is MyKey && key.shiftedCode != 0) {
                    val hintLabel = when (key.shiftedCode) { -2002 -> "ਾਂ"; -2003 -> "੍ਰ"; -2004 -> "੍ਹ"; -2005 -> "੍ਵ"; -2006 -> "੍ਯ"; else -> key.shiftedCode.toChar().toString() }
                    hintPaint.textSize = key.height * 0.25f; val hintX = key.x + key.width - (key.width * 0.2f) + paddingLeft; val hintY = key.y + (key.height * 0.3f) + paddingTop; canvas.drawText(hintLabel, hintX, hintY, hintPaint)
                }
                val firstCode = codes.firstOrNull() ?: 0
                if (firstCode == -103 || firstCode == -140) {
                    textPaint.textSize = key.height * 0.3f; val line1 = "1 2"; val line2 = "3 4"; val x = key.x + (key.width / 2f) + paddingLeft; val y1 = key.y + (key.height / 4f) - ((textPaint.descent() + textPaint.ascent()) / 2f) + paddingTop; val y2 = key.y + (key.height * 3 / 4f) - ((textPaint.descent() + textPaint.ascent()) / 2f) + paddingTop; canvas.drawText(line1, x, y1, textPaint); canvas.drawText(line2, x, y2, textPaint)
                } else {
                    if (firstCode == -2 || firstCode == -101 || firstCode == -100 || firstCode == ImeConstants.KEYCODE_SWITCH_TO_SYMBOLS) {
                        textPaint.textSize = if (keyTextSize > 0) keyTextSize * 0.7f else key.height * 0.45f
                        textPaint.typeface = if (isBoldMode) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    } else if (keyTextSize > 0) {
                        textPaint.textSize = keyTextSize
                    } else {
                        textPaint.textSize = key.height * 0.65f
                    }
                    val label = key.label.toString(); val x = key.x + (key.width / 2f) + paddingLeft; val y = key.y + (key.height / 2f) + paddingTop - ((textPaint.descent() + textPaint.ascent()) / 2f); canvas.drawText(label, x, y, textPaint)
                }
            }
        }
        
        if (isGestureTyping) {
            canvas.drawPath(swipePath, swipePaint)
        }
    }

    private fun isFunctional(key: Keyboard.Key): Boolean { val code = key.codes.firstOrNull() ?: 0; return when (code) { Keyboard.KEYCODE_DELETE, Keyboard.KEYCODE_SHIFT, Keyboard.KEYCODE_MODE_CHANGE, Keyboard.KEYCODE_DONE, -100, -101, -103, -2, 32, -153, ImeConstants.KEYCODE_SWITCH_TO_SYMBOLS, -140 -> true; else -> false } }
    private fun drawIcon(canvas: Canvas, icon: Drawable, key: Keyboard.Key) {
        val wrappedIcon = DrawableCompat.wrap(icon.mutate())
        val codes = key.codes
        val isActionOrShift = codes.contains(-4) || codes.contains(-1)
        val tint = if (isActionOrShift) Color.parseColor("#4285F4") else iconColor
        DrawableCompat.setTint(wrappedIcon, tint)
        wrappedIcon.state = if (key.pressed) intArrayOf(android.R.attr.state_pressed) else intArrayOf()
        val iconWidth = wrappedIcon.intrinsicWidth
        val iconHeight = wrappedIcon.intrinsicHeight
        val x = key.x + (key.width - iconWidth) / 2 + paddingLeft
        val y = key.y + (key.height - iconHeight) / 2 + paddingTop
        wrappedIcon.setBounds(x, y, x + iconWidth, y + iconHeight)
        wrappedIcon.draw(canvas)
    }
}
