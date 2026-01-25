package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.util.Log

object NanakshahiTest {
    /**
     * ਖਾਲਸਾ ਸਾਜਨਾ ਦਿਵਸ (30 ਮਾਰਚ 1699, ਜੂਲੀਅਨ) ਦੀ ਗਣਨਾ ਦਾ ਟੈਸਟ।
     * ਇਤਿਹਾਸਿਕ ਤੌਰ 'ਤੇ ਇਹ 1 ਵੈਸਾਖ 1756 ਬਿਕਰਮੀ ਸੀ।
     */
    fun runVaisakhi1699Test(context: Context) {
        val day = 30
        val month = 3
        val year = 1699
        
        Log.d("NanakshahiTest", "--- Starting Vaisakhi 1699 Validation ---")
        Log.d("NanakshahiTest", "Input: $day/$month/$year (Julian)")
        
        try {
            // 1. Convert information
            val result = NanakshahiCalendar.convert(context, day, month, year)
            Log.d("NanakshahiTest", "Detailed Result:\n$result")
            
            // 2. Specific field checks (using internal methods)
            // Note: NanakshahiCalendar logic for Bikrami Year: y + 57 (if m >= 3)
            val bikYear = NanakshahiCalendar.getBikramiYear(day, month, year)
            Log.d("NanakshahiTest", "Calculated Bikrami Year: $bikYear (Expected: 1756)")
            
            Log.d("NanakshahiTest", "--- Validation Complete ---")
            
        } catch (e: Exception) {
            Log.e("NanakshahiTest", "Error during test: ${e.message}")
        }
    }
}
