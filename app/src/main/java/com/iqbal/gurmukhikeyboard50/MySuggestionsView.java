package com.iqbal.gurmukhikeyboard50;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import android.util.TypedValue;

public class MySuggestionsView extends View {
    private static final String TAG = "MySuggestionsView";

    private String[] suggestions = new String[0];
    private Paint paint;
    private SuggestionPickListener listener;
    private float desiredHeight;

    public MySuggestionsView(Context context) {
        super(context);
        Log.d(TAG, "Constructor(Context) called");
        init();
    }

    public MySuggestionsView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "Constructor(Context, AttributeSet) called");
        init();
    }

    public MySuggestionsView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Log.d(TAG, "Constructor(Context, AttributeSet, int) called");
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ਥੀਮ ਐਟਰੀਬਿਊਟ ਤੋਂ ਟੈਕਸਟ ਦਾ ਰੰਗ ਪ੍ਰਾਪਤ ਕਰੋ
        TypedValue typedValue = new TypedValue();
        // Ensure R.attr.candidatesTextColor is defined in your project's attrs.xml and theme
        // For now, using a default color if not found to avoid crash, but theming should be fixed
        boolean resolved = getContext().getTheme().resolveAttribute(R.attr.candidatesTextColor, typedValue, true);
        if (resolved) {
            paint.setColor(typedValue.data); // ਥੀਮ ਵਾਲਾ ਰੰਗ ਵਰਤੋ
        } else {
            paint.setColor(0xFF000000); // Default to black if attribute not found
            Log.w(TAG, "R.attr.candidatesTextColor not found in theme. Defaulting to black.");
        }

        paint.setTextSize(48f); // ਡਿਫਾਲਟ ਟੈਕਸਟ ਆਕਾਰ - ਇਸਨੂੰ ਵੀ ਥੀਮ-ਅਨੁਕੂਲ ਜਾਂ sp ਯੂਨਿਟਾਂ ਵਿੱਚ ਬਣਾਇਆ ਜਾ ਸਕਦਾ ਹੈ

        Paint.FontMetrics fm = paint.getFontMetrics();
        desiredHeight = (fm.bottom - fm.top + fm.leading) + getPaddingTop() + getPaddingBottom() + 20; // Added some extra padding
        Log.d(TAG, "init: desiredHeight calculated = " + desiredHeight);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.d(TAG, "onMeasure: widthSpec=" + MeasureSpec.toString(widthMeasureSpec) + ", heightSpec=" + MeasureSpec.toString(heightMeasureSpec));

        int width = MeasureSpec.getSize(widthMeasureSpec); // Use the width provided by parent

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int height;

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
            Log.d(TAG, "onMeasure: Height is EXACTLY: " + height);
        } else {
            height = (int) desiredHeight;
            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(height, heightSize);
            }
            Log.d(TAG, "onMeasure: Height calculated (not EXACTLY): " + height);
        }

        setMeasuredDimension(width, height);
        Log.d(TAG, "onMeasure: setMeasuredDimension(width=" + width + ", height=" + height + ")");
    }

    public void setSuggestionsArray(String[] words) {
        suggestions = (words != null) ? words : new String[0];
        Log.d(TAG, "setSuggestionsArray: " + suggestions.length + " words. First word: " + (suggestions.length > 0 ? suggestions[0] : "N/A"));
        requestLayout(); // Important if the content change affects size
        invalidate();    // Important to trigger redraw
    }

    public void setSuggestionListener(SuggestionPickListener l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Log.d(TAG, "onDraw: Called. Number of suggestions: " + (suggestions != null ? suggestions.length : "null"));
        if (suggestions == null || suggestions.length == 0) {
            Log.d(TAG, "onDraw: No suggestions to draw.");
            return;
        }
        float x = 20 + getPaddingLeft(); // Start after left padding
        Paint.FontMetrics fm = paint.getFontMetrics();
        // Ensure y is calculated based on current padding and font metrics
        float y = getPaddingTop() - fm.top;

        Log.d(TAG, "onDraw: Drawing suggestions. Start x=" + x + ", y=" + y);
        for (String word : suggestions) {
            if (word == null) continue;
            canvas.drawText(word, x, y, paint);
            x += paint.measureText(word) + 60; // 60 is spacing between words
        }
        Log.d(TAG, "onDraw: Finished drawing suggestions.");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "onTouchEvent: action=" + event.getAction());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (suggestions == null || suggestions.length == 0 || listener == null) {
                Log.d(TAG, "onTouchEvent: No suggestions or listener, calling super.");
                return super.onTouchEvent(event);
            }
            float x = 20 + getPaddingLeft();
            Paint.FontMetrics fm = paint.getFontMetrics();
            // Calculate text actual visible height for touch detection
            float textVisibleHeight = fm.descent - fm.ascent; // More direct way for visible height from baseline
            float topY = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - textVisibleHeight) / 2 ; // Center text vertically for touch
            float bottomY = topY + textVisibleHeight;

            Log.d(TAG, "onTouchEvent: ACTION_DOWN. Calculated touchable Y range: " + topY + " - " + bottomY + ". Event Y: " + event.getY());

            if (event.getY() >= topY && event.getY() <= bottomY) {
                for (String word : suggestions) {
                    if (word == null) continue;
                    float wordWidth = paint.measureText(word);
                    float clickableWidth = wordWidth + 60; // Includes spacing for touch
                    if (event.getX() >= x && event.getX() < x + wordWidth) { // Only wordWidth should be clickable, not spacing
                        Log.d(TAG, "onTouchEvent: Suggestion picked: '" + word + "'");
                        listener.onSuggestionPicked(word);
                        return true; // Event handled
                    }
                    x += clickableWidth;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    public interface SuggestionPickListener {
        void onSuggestionPicked(String word);
    }

    public void clearSuggestions() {
        Log.d(TAG, "clearSuggestions called");
        suggestions = new String[0];
        requestLayout();
        invalidate();
    }
}
