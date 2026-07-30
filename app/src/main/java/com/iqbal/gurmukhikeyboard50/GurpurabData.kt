package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.Color
import java.util.Calendar

object GurpurabData {
    private val gurpurabCache = mutableMapOf<Int, List<NanakshahiCalendar.Gurpurab>>()

    fun getSgpcGurpurabs(ctx: Context, nsY: Int): List<NanakshahiCalendar.Gurpurab> {
        gurpurabCache[nsY]?.let { return it }
        val g = mutableListOf<NanakshahiCalendar.Gurpurab>()
        data class E(val m: String, val p: String, val t: Int, val s: Int, val n: String, val d: String, val fD: Int? = null, val fM: String? = null)
        val evs = listOf(
            E("ਕੱਤਕ", NanakshahiCalendar.SUDI, 15, 1, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ", "ਸਿੱਖ ਧਰਮ ਦੇ ਬਾਨੀ ਜਗਤ ਗੁਰੂ ਬਾਬਾ ਨਾਨਕ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            E("ਵੈਸਾਖ", NanakshahiCalendar.SUDI, 3, 1, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ", "ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ (੧ ਵੈਸਾਖ)", 1, "ਵੈਸਾਖ"),
            E("ਚੇਤ", NanakshahiCalendar.SUDI, 15, 1, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ", "ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ (ਚੇਤ ਪੂਰਨਮਾਸ਼ੀ)", 1, "ਵੈਸਾਖ"),
            E("ਵੈਸਾਖ", NanakshahiCalendar.SUDI, 3, 1, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ", "ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ (ਵੈਸਾਖ)।", 1, "ਵੈਸਾਖ"),
            E("ਅੱਸੂ", NanakshahiCalendar.VADI, 10, 71, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ", "ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ ਦੇ ਕਰਤਾਰਪੁਰ ਸਾਹਿਬ ਵਿਖੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 8, "ਅੱਸੂ"),
            
            E("ਵੈਸਾਖ", NanakshahiCalendar.VADI, 1, 36, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ", "ਦੂਜੀ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 5, "ਵੈਸਾਖ"),
            E("ਅੱਸੂ", NanakshahiCalendar.VADI, 5, 71, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ", "ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 4, "ਅੱਸੂ"),
            E("ਚੇਤ", NanakshahiCalendar.SUDI, 4, 84, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ", "ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 3, "ਵੈਸਾਖ"),

            E("ਵੈਸਾਖ", NanakshahiCalendar.SUDI, 14, 11, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ", "ਤੀਜੀ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 9, "ਜੇਠ"),
            E("ਚੇਤ", NanakshahiCalendar.SUDI, 4, 84, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ", "ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 3, "ਵੈਸਾਖ"),
            E("ਭਾਦੋਂ", NanakshahiCalendar.SUDI, 15, 106, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ", "ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 2, "ਅੱਸੂ"),

            E("ਕੱਤਕ", NanakshahiCalendar.VADI, 2, 66, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਰਾਮਦਾਸ ਜੀ", "ਚੌਥੀ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਰਾਮਦਾਸ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 25, "ਅੱਸੂ"),
            E("ਭਾਦੋਂ", NanakshahiCalendar.SUDI, 15, 106, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਰਾਮਦਾਸ ਜੀ", "ਗੁਰੂ ਰਾਮਦਾਸ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 2, "ਅੱਸੂ"),
            E("ਭਾਦੋਂ", NanakshahiCalendar.SUDI, 3, 113, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਰਾਮਦਾਸ ਜੀ", "ਗੁਰੂ ਰਾਮਦਾਸ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 2, "ਅੱਸੂ"),

            E("ਵੈਸਾਖ", NanakshahiCalendar.VADI, 7, 95, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ", "ਪੰਜਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 19, "ਵੈਸਾਖ"),
            E("ਭਾਦੋਂ", NanakshahiCalendar.SUDI, 3, 113, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ", "ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 2, "ਅੱਸੂ"),
            E("ਹਾੜ", NanakshahiCalendar.SUDI, 4, 138, "ਸ਼ਹੀਦੀ ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ", "ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ ਦੀ ਲਾਸਾਨੀ ਸ਼ਹਾਦਤ ਦਾ ਦਿਨ।", 2, "ਹਾੜ"),
            E("ਹਾੜ", NanakshahiCalendar.SUDI, 4, 138, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ", "ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 2, "ਹਾੜ"),

            E("ਹਾੜ", NanakshahiCalendar.VADI, 7, 127, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਹਰਿਗੋਬਿੰਦ ਸਾਹਿਬ ਜੀ", "ਛੇਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਹਰਿਗੋਬਿੰਦ ਸਾਹਿਬ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 21, "ਹਾੜ"),
            E("ਜੇਠ", NanakshahiCalendar.VADI, 14, 138, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਹਰਿਗੋਬਿੰਦ ਸਾਹਿਬ ਜੀ", "ਗੁਰੂ ਹਰਿਗੋਬਿੰਦ ਸਾਹਿਬ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 28, "ਜੇਠ"),
            E("ਚੇਤ", NanakshahiCalendar.SUDI, 5, 176, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਹਰਿਗੋਬਿੰਦ ਸਾਹਿਬ ਜੀ", "ਗੁਰੂ ਹਰਿਗੋਬਿੰਦ ਸਾਹਿਬ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 6, "ਚੇਤ"),

            E("ਮਾਘ", NanakshahiCalendar.SUDI, 13, 162, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਹਰਿ ਰਾਇ ਜੀ", "ਸੱਤਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਹਰਿ ਰਾਇ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 19, "ਮਾਘ"),
            E("ਚੇਤ", NanakshahiCalendar.VADI, 15, 176, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਹਰਿ ਰਾਇ ਜੀ", "ਗੁਰੂ ਹਰਿ ਰਾਇ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 1, "ਚੇਤ"),
            E("ਕੱਤਕ", NanakshahiCalendar.VADI, 9, 193, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਹਰਿ ਰਾਇ ਜੀ", "ਗੁਰੂ ਹਰਿ ਰਾਇ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 6, "ਕੱਤਕ"),

            E("ਸਾਵਣ", NanakshahiCalendar.VADI, 10, 188, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ", "ਅੱਠਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 8, "ਸਾਵਣ"),
            E("ਕੱਤਕ", NanakshahiCalendar.VADI, 9, 193, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ", "ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 6, "ਕੱਤਕ"),
            E("ਚੇਤ", NanakshahiCalendar.SUDI, 14, 196, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ", "ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 3, "ਵੈਸਾਖ"),

            E("ਵੈਸਾਖ", NanakshahiCalendar.VADI, 5, 153, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ", "ਨੌਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 5, "ਵੈਸਾਖ"),
            E("ਚੇਤ", NanakshahiCalendar.SUDI, 14, 196, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ", "ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 3, "ਵੈਸਾਖ"),
            E("ਮੱਘਰ", NanakshahiCalendar.SUDI, 5, 207, "ਸ਼ਹੀਦੀ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ", "ਹਿੰਦ ਦੀ ਚਾਦਰ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ ਦੀ ਸ਼ਹਾਦਤ ਦਾ ਦਿਨ।", 11, "ਮੱਘਰ"),
            E("ਮੱਘਰ", NanakshahiCalendar.SUDI, 5, 207, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ", "ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 11, "ਮੱਘਰ"),

            E("ਪੋਹ", NanakshahiCalendar.SUDI, 7, 198, "ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ", "ਦਸਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।", 23, "ਪੋਹ"),
            E("ਮੱਘਰ", NanakshahiCalendar.SUDI, 5, 207, "ਗੁਰਗੱਦੀ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ", "ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ ਦੇ ਗੁਰਗੱਦੀ ਦਿਵਸ।", 11, "ਮੱਘਰ"),
            E("ਕੱਤਕ", NanakshahiCalendar.SUDI, 5, 240, "ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ", "ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।", 7, "ਕੱਤਕ"),

            E("ਭਾਦੋਂ", NanakshahiCalendar.SUDI, 1, 136, "ਪਹਿਲਾ ਪ੍ਰਕਾਸ਼ ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ", "ਹਰਿਮੰਦਰ ਸਾਹਿਬ ਵਿਖੇ ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਦਾ ਪਹਿਲਾ ਪ੍ਰਕਾਸ਼।", 17, "ਭਾਦੋਂ"),
            E("ਭਾਦੋਂ", NanakshahiCalendar.VADI, 3, 238, "ਸੰਪੂਰਨਤਾ ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ", "ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਦੀ ਸੰਪੂਰਨਤਾ ਦਾ ਦਿਵਸ।", 15, "ਭਾਦੋਂ"),
            E("ਕੱਤਕ", NanakshahiCalendar.SUDI, 4, 240, "ਗੁਰਗੱਦੀ ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ", "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਨੂੰ ਗੁਰਗੱਦੀ ਸੌਂਪਣ ਦਾ ਦਿਨ।", 6, "ਕੱਤਕ"),
            E("ਹਾੜ", NanakshahiCalendar.VADI, 5, 138, "ਸਿਰਜਣਾ ਸ੍ਰੀ ਅਕਾਲ ਤਖਤ ਸਾਹਿਬ", "ਸ੍ਰੀ ਅਕਾਲ ਤਖਤ ਸਾਹਿਬ ਜੀ ਦੀ ਸਿਰਜਣਾ ਦਾ ਦਿਨ।", 18, "ਹਾੜ")
        )
        val wC = NanakshahiCalendar.findChet1ForYear(ctx, nsY + 1468)
        for (i in 0..384) {
            val jd = AstronomyUtils.julianDay(wC)
            val sr = NanakshahiCalendar.calculateSunriseJD(jd, NanakshahiCalendar.LocationConfig.AMRITSAR.lat, NanakshahiCalendar.LocationConfig.AMRITSAR.lon)
            val tR = NanakshahiCalendar.getTithiResultFromJD(ctx, sr, NanakshahiCalendar.LocationConfig.AMRITSAR.lat, NanakshahiCalendar.LocationConfig.AMRITSAR.lon)
            val nD = NanakshahiCalendar.getNanakshahiDate(ctx, wC.get(Calendar.DAY_OF_MONTH), wC.get(Calendar.MONTH) + 1, NanakshahiCalendar.getAstronomicalYear(wC))
            if (nD.year > nsY) break
            if (nD.year == nsY) {
                for (e in evs) {
                    if (nsY < e.s) continue
                    var m = false
                    
                    // Check Fixed Nanakshahi date (if available)
                    if (e.fD != null && e.fM != null) {
                        if (nD.day == e.fD && nD.month == e.fM) m = true
                    }
                    
                    // Check Bikrami Lunar date (only if not already matched by fixed date, or always if in Bikrami mode)
                    if (!m && !NanakshahiCalendar.isMoolNanakshahiMode && tR.monthName.contains(e.m)) {
                        m = if (e.t == 15) (e.p == NanakshahiCalendar.SUDI && tR.isPunia) || (e.p == NanakshahiCalendar.VADI && tR.isMasaya) else tR.paksha == e.p && tR.tithi == e.t
                    }

                    if (m && g.none { it.name == e.n }) {
                        g.add(NanakshahiCalendar.Gurpurab(nD.day, nD.month, e.n, e.d, if (e.n.contains("ਸ਼ਹੀਦੀ")) Color.RED else if (e.n.contains("ਗੁਰਗੱਦੀ")) Color.BLUE else Color.parseColor("#FF5733"), wC.clone() as Calendar))
                    }
                }
            }
            wC.add(Calendar.DAY_OF_MONTH, 1)
        }
        gurpurabCache[nsY] = g
        return g
    }

    fun clearCache() {
        gurpurabCache.clear()
    }
}
