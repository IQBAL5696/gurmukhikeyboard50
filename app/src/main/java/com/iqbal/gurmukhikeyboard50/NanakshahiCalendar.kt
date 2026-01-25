package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

object NanakshahiCalendar {

    const val SUDI = "ਸੁਦੀ"
    const val VADI = "ਵਦੀ"
    const val ADHIK = "ਅਧਿਕ"

    var currentTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    var currentMode: CalculationMode = CalculationMode.ASTRONOMICAL
    enum class CalculationMode { ASTRONOMICAL, FIXED }

    data class NanakshahiDate(val day: Int, var month: String, val year: Int)
    data class TithiResult(val tithi: Int, val paksha: String, val monthName: String, val isAdhik: Boolean)
    data class PaharInfo(val name: String, val startTime: Long, val endTime: Long)
    data class DualTithiResult(val isDual: Boolean, val firstMonth: String, val firstPaksha: String, val firstTithi: Int, val secondMonth: String, val secondPaksha: String, val secondTithi: Int)
    data class Gurpurab(val day: Int, val month: String, val name: String, val history: String? = null, val gurpurabColor: Int? = null, val gregDate: Calendar? = null)
    data class MonthlyDayCell(val day: Int?, val displayText: String, val isToday: Boolean = false, val gurpurabName: String? = null, val gurpurabHistory: String? = null, val gurpurabColor: Int? = null, val isSangrand: Boolean = false, val isPunia: Boolean = false, val isMasaya: Boolean = false, val isEmpty: Boolean = false)
    data class DateDifference(val years: Int, val months: Int, val days: Int)
    data class LocationConfig(val lat: Double, val lon: Double) {
        companion object { val AMRITSAR = LocationConfig(31.62, 74.87) }
    }

    private val tithiCache = mutableMapOf<Double, Int>()
    private val gurpurabCache = mutableMapOf<Int, List<Gurpurab>>()

    fun calculateDateDifference(startDate: Calendar, endDate: Calendar): DateDifference {
        if (startDate.timeInMillis == endDate.timeInMillis) return DateDifference(0, 0, 0)
        var start = startDate.clone() as Calendar; var end = endDate.clone() as Calendar
        if (start.timeInMillis > end.timeInMillis) { val tmp = start; start = end; end = tmp }
        var years = 0; var months = 0
        while (true) {
            val test = start.clone() as Calendar; test.add(Calendar.YEAR, 1)
            if (test.timeInMillis <= end.timeInMillis) { start = test; years++ } else break
        }
        while (true) {
            val test = start.clone() as Calendar; test.add(Calendar.MONTH, 1)
            if (test.timeInMillis <= end.timeInMillis) { start = test; months++ } else break
        }
        val days = ((end.timeInMillis - start.timeInMillis) / 86400000L).toInt()
        return DateDifference(years, months, days)
    }

    fun getBikramiYear(d: Int, m: Int, y: Int): Int = if (m < 3 || (m == 3 && d < 14)) y + 56 else y + 57

    fun generateMonthlyCalendar(context: Context, month: Int, year: Int, location: LocationConfig = LocationConfig.AMRITSAR): List<MonthlyDayCell> {
        val cal = Calendar.getInstance(currentTimeZone).apply { set(year, month - 1, 1, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<MonthlyDayCell>()
        val firstDayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1
        repeat(firstDayIndex) { cells.add(MonthlyDayCell(null, "", isEmpty = true)) }
        val engMonthFormat = SimpleDateFormat("MMM", Locale.ENGLISH)

        for (d in 1..maxDay) {
            cal.set(Calendar.DAY_OF_MONTH, d); val jd = julianDay(cal); val sunriseJd = calculateSunriseJD(jd, location.lat, location.lon)
            val (sMonth, sDay) = getSolarBikramiDate(sunriseJd); val isPunia = isPuniaDay(sunriseJd); val isMasaya = isMasayaDay(sunriseJd)
            val tithiDetail = getTithiResultFromJD(context, sunriseJd); val dualTithi = getDualTithiAtSunriseFromJD(context, sunriseJd)
            val tithiText = if (dualTithi.isDual) "${dualTithi.firstMonth} ${dualTithi.firstPaksha} ${toGurmukhiNumber(dualTithi.firstTithi)} / ${dualTithi.secondMonth} ${dualTithi.secondPaksha} ${toGurmukhiNumber(dualTithi.secondTithi)}" else "${tithiDetail.monthName} ${tithiDetail.paksha} ${toGurmukhiNumber(tithiDetail.tithi)}"
            val nsDate = getNanakshahiDate(context, d, month, year); val allGurpurabs = getSgpcGurpurabs(context, nsDate.year)
            val gurpurabs = allGurpurabs.filter { it.day == nsDate.day && it.month == nsDate.month }
            val statusLabel = when { isMasaya -> " (ਮੱਸਿਆ)"; isPunia -> " (ਪੁੰਨਿਆ)"; else -> "" }
            val engDateText = "$d ${engMonthFormat.format(cal.time)}"
            var display = "$engDateText\n${toGurmukhiNumber(sDay)} $sMonth$statusLabel\n$tithiText"
            if (gurpurabs.isNotEmpty()) display += "\n${gurpurabs.joinToString { it.name }}"
            val today = Calendar.getInstance(currentTimeZone)
            val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            cells.add(MonthlyDayCell(d, display, isToday, gurpurabs.firstOrNull()?.name, gurpurabs.firstOrNull()?.history, gurpurabs.firstOrNull()?.gurpurabColor, sDay == 1, isPunia, isMasaya))
        }
        return cells
    }

    fun convert(context: Context, day: Int, month: Int, year: Int, location: LocationConfig = LocationConfig.AMRITSAR): String {
        val cal = Calendar.getInstance(currentTimeZone).apply { set(year, month - 1, day, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
        val jd = julianDay(cal); val sunriseJd = calculateSunriseJD(jd, location.lat, location.lon); val sunsetJd = calculateSunsetJD(jd, location.lat, location.lon)
        val moonriseJd = calculateMoonriseJD(jd, location.lat, location.lon); val moonsetJd = calculateMoonsetJD(jd, location.lat, location.lon)
        val (sMonth, sDay) = getSolarBikramiDate(sunriseJd); val tithi = getTithiResultFromJD(context, sunriseJd); val nsDate = getNanakshahiDate(context, day, month, year)
        val bikramiYear = getBikramiYear(day, month, year); val weekday = weekdayNamePunjabi(cal.get(Calendar.DAY_OF_WEEK) - 1)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply { timeZone = currentTimeZone }
        val sunLine = "☀️ ${timeFormat.format(java.util.Date(jdToMillis(sunriseJd)))} - ${timeFormat.format(java.util.Date(jdToMillis(sunsetJd)))}"
        val moonLine = if (moonriseJd > 0) "🌙 ${timeFormat.format(java.util.Date(jdToMillis(moonriseJd)))} - ${timeFormat.format(java.util.Date(jdToMillis(moonsetJd)))}" else "🌙 (ਨਹੀਂ)"
        val pahars = calculate8Pahars(sunriseJd, sunsetJd, calculateSunriseJD(jd + 1.0, location.lat, location.lon))
        val amritVela = pahars.last(); val amritLine = "✨ ਅੰਮ੍ਰਿਤ ਵੇਲਾ: ${timeFormat.format(java.util.Date(amritVela.startTime))} - ${timeFormat.format(java.util.Date(amritVela.endTime))}"
        val dayLen = (sunsetJd - sunriseJd) * 24.0; val dayH = floor(dayLen).toInt(); val dayM = floor((dayLen - dayH) * 60.0).toInt()
        val lenLine = "⏳ ਦਿਨ: ${toGurmukhiNumber(dayH)} ਘੰਟੇ ${toGurmukhiNumber(dayM)} ਮਿੰਟ | ਰਾਤ: ${toGurmukhiNumber(23 - dayH)} ਘੰਟੇ ${toGurmukhiNumber(60 - dayM)} ਮਿੰਟ"
        val season = getPunjabiSeason(sMonth); val moonPhase = getMoonPhaseIcon(tithi.tithi, tithi.paksha == SUDI); val gurpurabs = detectGurpurabs(context, nsDate)
        val gurLine = if (gurpurabs.isNotEmpty()) "🎉 " + gurpurabs.joinToString { it.name } else ""; val countdown = getUpcomingEventCountdown(context, nsDate)
        val sangrandTime = if (sDay == 1) " (ਸੰਕਰਾਂਤ ਸਮਾਂ: ${timeFormat.format(java.util.Date(jdToMillis(findSangrandMoment(sunriseJd))))})" else ""
        val statusLabel = if (isPuniaDay(sunriseJd)) " (ਪੁੰਨਿਆ)" else if (isMasayaDay(sunriseJd)) " (ਮੱਸਿਆ)" else ""
        return "$sunLine\n$moonLine\n$amritLine\n$lenLine\n\nਸੰਮਤ ${toGurmukhiYear(bikramiYear)} $sMonth ਰੁੱਤ $season$sangrandTime\n$moonPhase ${tithi.monthName} ${tithi.paksha} ${toGurmukhiNumber(tithi.tithi)}$statusLabel\n${toGurmukhiNumber(nsDate.day)} ${nsDate.month} ${toGurmukhiYear(nsDate.year)} ਨਾਨਕਸ਼ਾਹੀ\n$weekday\n" + (if (gurLine.isNotBlank()) "$gurLine\n" else "") + countdown
    }

    private fun getMoonPhaseIcon(tithi: Int, isSudi: Boolean): String = when { tithi == 15 && isSudi -> "🌕"; tithi == 15 && !isSudi -> "🌑"; isSudi -> if (tithi < 8) "🌒" else "🌓"; else -> if (tithi < 8) "🌘" else "🌗" }

    private fun getUpcomingEventCountdown(context: Context, nsDate: NanakshahiDate): String {
        val allEvents = getSgpcGurpurabs(context, nsDate.year); val months = listOf("ਚੇਤ", "ਵੈਸਾਖ", "ਜੇਠ", "ਹਾੜ", "ਸਾਵਣ", "ਭਾਦੋਂ", "ਅੱਸੂ", "ਕੱਤਕ", "ਮੱਘਰ", "ਪੋਹ", "ਮਾਘ", "ਫੱਗਣ")
        val currentMonthIdx = months.indexOf(nsDate.month); val upcoming = allEvents.filter { val eventMonthIdx = months.indexOf(it.month); eventMonthIdx > currentMonthIdx || (eventMonthIdx == currentMonthIdx && it.day > nsDate.day) }.minByOrNull { val eventMonthIdx = months.indexOf(it.month); eventMonthIdx * 31 + it.day }
        return upcoming?.let { "🔔 ਅਗਲਾ ਗੁਰਪੁਰਬ: ${it.name} (${toGurmukhiNumber(it.day)} ${it.month})" } ?: ""
    }

    private fun findSangrandMoment(jdAround: Double): Double {
        var curr = jdAround; val rashiToday = getSunRashiFromJD(jdAround); val targetLon = (rashiToday - 1) * 30.0
        for (i in 0..15) {
            val lon = when (currentMode) { CalculationMode.ASTRONOMICAL -> getSunLongitudeSidereal(curr); CalculationMode.FIXED -> (sunLongitudeJD(curr) - 24.1 + 360.0) % 360.0 }
            var diff = lon - targetLon; while (diff > 180) diff -= 360; while (diff < -180) diff += 360
            if (abs(diff) < 0.00001) break; curr -= diff / 0.9856 
        }
        return curr
    }

    fun getPunjabiSeason(solarMonth: String): String = when (solarMonth) { "ਚੇਤ", "ਵੈਸਾਖ" -> "ਬਸੰਤ"; "ਜੇਠ", "ਹਾੜ" -> "ਗ੍ਰੀਖਮ"; "ਸਾਵਣ", "ਭਾਦੋਂ" -> "ਪਾਵਸ"; "ਅੱਸੂ", "ਕੱਤਕ" -> "ਸ਼ਰਦ"; "ਮੱਘਰ", "ਪੋਹ" -> "ਸ਼ਿਸ਼ਿਰ"; "ਮਾਘ", "ਫੱਗਣ" -> "ਹਿਮਕਰ"; else -> "ਅਗਿਆਤ" }

    fun calculate8Pahars(sunriseJd: Double, sunsetJd: Double, nextSunriseJd: Double): List<PaharInfo> {
        val pahars = mutableListOf<PaharInfo>(); val dayDuration = (sunsetJd - sunriseJd) / 4.0; val nightDuration = (nextSunriseJd - sunsetJd) / 4.0
        val dayNames = listOf("ਪਹਿਲਾ ਪਹਿਰ (ਦਿਨ)", "ਦੂਜਾ ਪਹਿਰ (ਦਿਨ)", "ਤੀਜਾ ਪਹਿਰ (ਦਿਨ)", "ਚੌਥਾ ਪਹਿਰ (ਦਿਨ)")
        for (i in 0..3) pahars.add(PaharInfo(dayNames[i], jdToMillis(sunriseJd + i * dayDuration), jdToMillis(sunriseJd + (i + 1) * dayDuration)))
        val nightNames = listOf("ਪਹਿਲਾ ਪਹਿਰ (ਰਾਤ)", "ਦੂਜਾ ਪਹਿਰ (ਰਾਤ)", "ਤੀਜਾ ਪਹਿਰ (ਰਾਤ)", "ਚੌਥਾ ਪਹਿਰ (ਅੰਮ੍ਰਿਤ ਵੇਲਾ)")
        for (i in 0..3) pahars.add(PaharInfo(nightNames[i], jdToMillis(sunsetJd + i * nightDuration), jdToMillis(sunsetJd + (i + 1) * nightDuration)))
        return pahars
    }

    private fun isPuniaDay(sunriseJd: Double): Boolean { val purnimaMoment = findPurnimaMoment(sunriseJd); return purnimaMoment >= sunriseJd && purnimaMoment < sunriseJd + 1.0 }
    private fun isMasayaDay(sunriseJd: Double): Boolean { val amavasyaMoment = findAmavasyaMoment(sunriseJd); return amavasyaMoment >= sunriseJd && amavasyaMoment < sunriseJd + 1.0 }
    private fun getAyanamsa(jd: Double): Double { val t = (jd - 2451545.0) / 36525.0; return 23.857083 + (5029.0966 * t + 1.1116 * t * t) / 3600.0 }

    private fun sunLongitudeJD(jd: Double): Double {
        val t = (jd + calculateDeltaT(jdToYear(jd)) / 86400.0 - 2451545.0) / 36525.0; var l = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t; val mRad = Math.toRadians(m % 360.0); val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) + (0.019993 - 0.000101 * t) * sin(2 * mRad) + 0.000289 * sin(3 * mRad)
        var lon = (l + c) % 360.0; lon += 0.00134 * cos(Math.toRadians(153.23 + 22518.7541 * t)); lon += 0.00154 * cos(Math.toRadians(216.57 + 45037.5082 * t)); lon += 0.00200 * cos(Math.toRadians(312.69 + 32964.4670 * t))
        return (lon + 360.0) % 360.0
    }

    private fun moonLongitudeJD(jd: Double): Double {
        val t = (jd + calculateDeltaT(jdToYear(jd)) / 86400.0 - 2451545.0) / 36525.0; val lp = 218.3164477 + 481267.8812307 * t; val d = 297.8501921 + 445267.1114034 * t; val mm = 134.9633964 + 477198.8675055 * t; val f = 93.2720950 + 483202.0175233 * t
        val dRad = Math.toRadians(d % 360.0); val mmRad = Math.toRadians(mm % 360.0); val fRad = Math.toRadians(f % 360.0); var lon = lp + 6.288774 * sin(mmRad) + 1.274027 * sin(2 * dRad - mmRad) + 0.658309 * sin(2 * dRad) + 0.213618 * sin(2 * mmRad) - 0.114332 * sin(2 * fRad) + 0.058793 * sin(2 * dRad - 2 * mmRad) + 0.057066 * sin(2 * dRad - Math.toRadians(357.529) - mmRad)
        return (lon % 360.0 + 360.0) % 360.0
    }

    private fun getSunRAAndDec(jd: Double): Pair<Double, Double> { val lambda = sunLongitudeJD(jd); val eps = Math.toRadians(23.439); val l = Math.toRadians(lambda); val ra = atan2(sin(l) * cos(eps), cos(l)); val dec = asin(sin(eps) * sin(l)); return Math.toDegrees(ra) to Math.toDegrees(dec) }
    private fun getMoonRAAndDec(jd: Double): Pair<Double, Double> { val lambda = moonLongitudeJD(jd); val eps = Math.toRadians(23.439); val l = Math.toRadians(lambda); val ra = atan2(sin(l) * cos(eps), cos(l)); val dec = asin(sin(eps) * sin(l)); return Math.toDegrees(ra) to Math.toDegrees(dec) }
    private fun getGreenwichSiderealTime(jd: Double): Double { val jd0 = floor(jd + 0.5) - 0.5; val ut = (jd - jd0) * 24.0; val t = (jd0 - 2451545.0) / 36525.0; var gmst = 6.697374558 + 0.06570982441908 * (jd0 - 2451545.0) + 1.00273790935 * ut + 0.000026 * t * t; return (gmst * 15.0 % 360.0 + 360.0) % 360.0 }
    private fun getSunLongitudeSidereal(jd: Double): Double { val lon = sunLongitudeJD(jd); val ayan = getAyanamsa(jd); return (lon - ayan + 360.0) % 360.0 }
    private fun getSunRashiFromJD(jd: Double): Int { val lon = when (currentMode) { CalculationMode.ASTRONOMICAL -> getSunLongitudeSidereal(jd); CalculationMode.FIXED -> (sunLongitudeJD(jd) - 24.1 + 360.0) % 360.0 }; val safeLon = (lon % 360.0 + 360.0) % 360.0; return (floor(safeLon / 30.0).toInt() % 12) + 1 }

    private fun getSolarBikramiDate(jd: Double): Pair<String, Int> {
        val sunriseToday = floor(jd + 0.5) - 0.5; val rashiToday = getSunRashiFromJD(sunriseToday); val rashiTomorrow = getSunRashiFromJD(sunriseToday + 1.0)
        if (rashiToday != rashiTomorrow) return rashiToBikramiMonth(rashiTomorrow) to 1
        var sangrandJd = sunriseToday; for (i in 1..35) { val d = sunriseToday - i; if (getSunRashiFromJD(d) != getSunRashiFromJD(d + 1.0)) { sangrandJd = d; break } }
        val day = (floor(sunriseToday + 0.5) - floor(sangrandJd + 0.5)).toInt() + 1
        return rashiToBikramiMonth(rashiToday) to day
    }

    private fun getTithiNumberFromJD(jd: Double): Int { tithiCache[jd]?.let { return it }; var diff = moonLongitudeJD(jd) - sunLongitudeJD(jd); while (diff < 0) diff += 360; val result = (floor(diff / 12.0).toInt() % 30) + 1; if (tithiCache.size > 1000) tithiCache.clear(); tithiCache[jd] = result; return result }
    private fun findAmavasyaMoment(jdAround: Double): Double { var curr = jdAround; for (i in 0..15) { var diff = moonLongitudeJD(curr) - sunLongitudeJD(curr); while (diff < 0) diff += 360; while (diff >= 360) diff -= 360; val delta = if (diff > 180) (diff - 360) else diff; if (abs(delta) < 0.0001) break; curr -= delta / 12.19 }; return curr }
    private fun findPurnimaMoment(jdAround: Double): Double { var curr = jdAround; for (i in 0..20) { var diff = (moonLongitudeJD(curr) - sunLongitudeJD(curr) + 360.0) % 360.0; var dist = diff - 180.0; while (dist < -180) dist += 360; while (dist >= 180) dist -= 360; if (abs(dist) < 0.0001) break; curr -= dist / 12.19 }; return curr }

    private fun resolveLunarMonthWithAdhik(context: Context, sunriseJd: Double, tithiRaw: Int): String {
        val definingAmavasyaJd = if (tithiRaw <= 15) findPreviousAmavasyaMoment(sunriseJd) else findNextAmavasyaMoment(sunriseJd)
        val rashi = getSunRashiFromJD(definingAmavasyaJd + 0.001); val baseMonthName = rashiToBikramiMonth(rashi)
        val amantaStartJd = if (tithiRaw <= 15) definingAmavasyaJd else findPreviousAmavasyaMoment(sunriseJd)
        val amantaEndJd = if (tithiRaw <= 15) findNextAmavasyaMoment(sunriseJd) else definingAmavasyaJd
        val rStart = getSunRashiFromJD(amantaStartJd + 0.001); val rEnd = getSunRashiFromJD(amantaEndJd - 0.001)
        return if (rStart == rEnd) ADHIK + " " + baseMonthName else baseMonthName
    }

    private fun findNextAmavasyaMoment(jd: Double): Double = findMoment(jd, 25.0, ::findAmavasyaMoment)
    private fun findPreviousAmavasyaMoment(jd: Double): Double = findMoment(jd, -25.0, ::findAmavasyaMoment)
    private fun findMoment(jd: Double, step: Double, finder: (Double) -> Double): Double { var res = finder(jd); if (step > 0 && res <= jd) res = finder(jd + step); if (step < 0 && res >= jd) res = finder(jd + step); return res }

    fun calculateMoonriseJD(jd: Double, lat: Double, lon: Double): Double { val jd0 = floor(jd + 0.5) - 0.5; var t = 12.0; for (i in 0..10) { val currJd = jd0 + t / 24.0; val (ra, dec) = getMoonRAAndDec(currJd); val ha = (getGreenwichSiderealTime(currJd) + lon) - ra; val h = asin(sin(Math.toRadians(lat)) * sin(Math.toRadians(dec)) + cos(Math.toRadians(lat)) * cos(Math.toRadians(dec)) * cos(Math.toRadians(ha))); t -= (Math.toDegrees(h) - (-0.583)) / 15.0 }; return jd0 + t / 24.0 }
    fun calculateMoonsetJD(jd: Double, lat: Double, lon: Double): Double { val jd0 = floor(jd + 0.5) - 0.5; var t = 24.0; for (i in 0..10) { val currJd = jd0 + t / 24.0; val (ra, dec) = getMoonRAAndDec(currJd); val ha = (getGreenwichSiderealTime(currJd) + lon) - ra; val h = asin(sin(Math.toRadians(lat)) * sin(Math.toRadians(dec)) + cos(Math.toRadians(lat)) * cos(Math.toRadians(dec)) * cos(Math.toRadians(ha))); t += (Math.toDegrees(h) - (-0.583)) / 15.0 }; return jd0 + t / 24.0 }

    private fun calculateSunTimeJD(jdIn: Double, lat: Double, lon: Double, rise: Boolean): Double { val jd0 = floor(jdIn + 0.5) - 0.5; var t = if (rise) 6.0 else 18.0; for (i in 0..5) { val currJd = jd0 + t / 24.0; val (ra, dec) = getSunRAAndDec(currJd); val ha = (getGreenwichSiderealTime(currJd) + lon) - ra; val h = asin(sin(Math.toRadians(lat)) * sin(Math.toRadians(dec)) + cos(Math.toRadians(lat)) * cos(Math.toRadians(dec)) * cos(Math.toRadians(ha))); val diff = Math.toDegrees(h) - (-0.833); if (rise) t -= diff / 15.0 else t += diff / 15.0 }; return jd0 + t / 24.0 }
    fun calculateSunriseJD(jd: Double, lat: Double, lon: Double): Double = calculateSunTimeJD(jd, lat, lon, true)
    fun calculateSunsetJD(jd: Double, lat: Double, lon: Double): Double = calculateSunTimeJD(jd, lat, lon, false)

    private fun getTithiResultFromJD(context: Context, jd: Double): TithiResult {
        val raw = getTithiNumberFromJD(jd); val paksha = if (raw <= 15) SUDI else VADI; val tithi = if (raw <= 15) raw else raw - 15
        val lunarMonthName = resolveLunarMonthWithAdhik(context, jd, raw); return TithiResult(tithi, paksha, lunarMonthName, lunarMonthName.contains(ADHIK))
    }

    private fun getDualTithiAtSunriseFromJD(context: Context, sunriseJd: Double): DualTithiResult {
        val window = 0.5 / 24.0; val bRaw = getTithiNumberFromJD(sunriseJd - window); val aRaw = getTithiNumberFromJD(sunriseJd + window)
        val mB = resolveLunarMonthWithAdhik(context, sunriseJd - window, bRaw); val mA = resolveLunarMonthWithAdhik(context, sunriseJd + window, aRaw)
        fun p(t: Int): String { return if (t <= 15) SUDI else VADI }
        fun n(t: Int): Int { return if (t <= 15) t else t - 15 }
        if (bRaw != aRaw) return DualTithiResult(true, mB, p(bRaw), n(bRaw), mA, p(aRaw), n(aRaw))
        return DualTithiResult(false, mB, p(bRaw), n(bRaw), "", "", 0)
    }

    @Synchronized
    fun getSgpcGurpurabs(context: Context, nsYear: Int): List<Gurpurab> {
        gurpurabCache[nsYear]?.let { return it }
        val gurpurabs = mutableListOf<Gurpurab>()
        val events = listOf(
            Triple("ਕੱਤਕ", SUDI, 15) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ", "ਸਿੱਖ ਧਰਮ ਦੇ ਬਾਨੀ ਜਗਤ ਗੁਰੂ ਬਾਬਾ ਨਾਨਕ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਅੱਸੂ", VADI, 8) to Pair("ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ", "ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ ਦੇ ਕਰਤਾਰਪੁਰ ਸਾਹਿਬ ਵਿਖੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।"),
            Triple("ਵੈਸਾਖ", VADI, 1) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ", "ਦੂਜੀ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਅੱਸੂ", SUDI, 10) to Pair("ਗੁਰਗੱਦੀ ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ", "ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ ਨੂੰ ਗੁਰਗੱਦੀ ਸੌਂਪਣ ਦਾ ਦਿਨ।"),
            Triple("ਚੇਤ", SUDI, 4) to Pair("ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ", "ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।"),
            Triple("ਵੈਸਾਖ", SUDI, 14) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ", "ਤੀਜੀ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਅੱਸੂ", SUDI, 15) to Pair("ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ", "ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।"),
            Triple("ਅੱਸੂ", VADI, 2) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਰਾਮ ਦਾਸ ਜੀ", "ਚੌਥੀ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਰਾਮ ਦਾਸ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਅੱਸੂ", SUDI, 15) to Pair("ਗੁਰਗੱਦੀ ਗੁਰੂ ਰਾਮ ਦਾਸ ਜੀ", "ਗੁਰੂ ਰਾਮ ਦਾਸ ਜੀ ਨੂੰ ਗੁਰਗੱਦੀ ਸੌਂਪਣ ਦਾ ਦਿਨ।"),
            Triple("ਅੱਸੂ", SUDI, 3) to Pair("ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਰਾਮ ਦਾਸ ਜੀ", "ਗੁਰੂ ਰਾਮ ਦਾਸ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।"),
            Triple("ਵੈਸਾਖ", VADI, 7) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ", "ਪੰਜਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਹਾੜ", SUDI, 4) to Pair("ਸ਼ਹੀਦੀ ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ", "ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ ਦੀ ਲਾਸਾਨੀ ਸ਼ਹਾਦਤ ਦਾ ਦਿਨ।"),
            Triple("ਹਾੜ", VADI, 1) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਹਰਗੋਬਿੰਦ ਜੀ", "ਛੇਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਹਰਗੋਬਿੰਦ ਸਾਹਿਬ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਮਾਘ", SUDI, 13) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂਰ ਹਰਿ ਰਾਇ ਜੀ", "ਸੱਤਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਹਰਿ ਰਾਇ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਸਾਵਣ", VADI, 9) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ", "ਅੱਠਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਵੈਸਾਖ", VADI, 5) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ", "ਨੌਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਮੱਘਰ", SUDI, 5) to Pair("ਸ਼ਹੀਦੀ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ", "ਹਿੰਦ ਦੀ ਚਾਦਰ ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ ਦੀ ਸ਼ਹਾਦਤ ਦਾ ਦਿਨ।"),
            Triple("ਪੋਹ", SUDI, 7) to Pair("ਪ੍ਰਕਾਸ਼ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ", "ਦਸਵੀਂ ਪਾਤਸ਼ਾਹੀ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ ਦਾ ਪ੍ਰਕਾਸ਼ ਪੁਰਬ।"),
            Triple("ਕੱਤਕ", VADI, 5) to Pair("ਜੋਤੀ ਜੋਤਿ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ", "ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ ਦੇ ਜੋਤੀ-ਜੋਤਿ ਸਮਾਉਣ ਦਾ ਦਿਨ।"),
            Triple("ਅੱਸੂ", SUDI, 2) to Pair("ਗੁਰਗੱਦੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ", "ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਨੂੰ ਗੁਰਗੱਦੀ ਸੌਂਪਣ ਦਾ ਇਤਿਹਾਸਕ ਦਿਹਾੜਾ।")
        )
        events.forEach { (lunarInfo, eventData) -> 
            findLunarDate(nsYear, lunarInfo.first, lunarInfo.second, lunarInfo.third, context)?.let { (nsDate, gregDate) ->
                val name = eventData.first
                val history = eventData.second
                val color = when { name.contains("ਸ਼ਹੀਦੀ") -> Color.RED; name.contains("ਗੁਰਗੱਦੀ") -> Color.BLUE; else -> Color.parseColor("#FF5733") }
                gurpurabs.add(Gurpurab(nsDate.day, nsDate.month, name, history, color, gregDate)) 
            } 
        }
        gurpurabCache[nsYear] = gurpurabs; return gurpurabs
    }

    private fun findLunarDate(nsYear: Int, nsM: String, paksha: String, tithi: Int, context: Context): Pair<NanakshahiDate, Calendar>? {
        val cal = findChet1(nsYear); val targetRaw = if (paksha == SUDI) tithi else tithi + 15
        for (i in 0..400) { 
            val check = cal.clone() as Calendar; check.add(Calendar.DAY_OF_YEAR, i)
            val sunriseJd = calculateSunriseJD(julianDay(check), LocationConfig.AMRITSAR.lat, LocationConfig.AMRITSAR.lon)
            if (getTithiNumberFromJD(sunriseJd) != targetRaw) continue
            val tithiResult = getTithiResultFromJD(context, sunriseJd)
            if (tithiResult.monthName.contains(nsM) && tithiResult.paksha == paksha && tithiResult.tithi == tithi) {
                val ns = getNanakshahiDate(context, check.get(Calendar.DAY_OF_MONTH), check.get(Calendar.MONTH) + 1, check.get(Calendar.YEAR))
                return ns to check
            }
        }
        return null
    }

    private fun findChet1(nsYear: Int): Calendar { val year = nsYear + 1468; val cal = Calendar.getInstance(currentTimeZone).apply { clear(); set(year, Calendar.MARCH, 5, 12, 0, 0) }; var prevLon = sunLongitudeJD(calculateSunriseJD(julianDay(cal), LocationConfig.AMRITSAR.lat, LocationConfig.AMRITSAR.lon)); for (i in 1..35) { val check = cal.clone() as Calendar; check.add(Calendar.DAY_OF_MONTH, i); val lon = sunLongitudeJD(calculateSunriseJD(julianDay(check), LocationConfig.AMRITSAR.lat, LocationConfig.AMRITSAR.lon)); if (prevLon > 330 && lon < 30) return check; prevLon = lon }; return Calendar.getInstance(currentTimeZone).apply { clear(); set(year, Calendar.MARCH, 14, 12, 0, 0) } }
    private fun rashiToBikramiMonth(rashi: Int): String = when (rashi) { 1 -> "ਵੈਸਾਖ"; 2 -> "ਜੇਠ"; 3 -> "ਹਾੜ"; 4 -> "ਸਾਵਣ"; 5 -> "ਭਾਦੋਂ"; 6 -> "ਅੱਸੂ"; 7 -> "ਕੱਤਕ"; 8 -> "ਮੱਘਰ"; 9 -> "ਪੋਹ"; 10 -> "ਮਾਘ"; 11 -> "ਫੱਗਣ"; 12 -> "ਚੇਤ"; else -> "ਅਗਿਆਤ" }
    private fun detectGurpurabs(context: Context, nanak: NanakshahiDate): List<Gurpurab> = getSgpcGurpurabs(context, nanak.year).filter { it.day == nanak.day && it.month == nanak.month }

    private fun getNanakshahiDate(context: Context, d: Int, m: Int, y: Int): NanakshahiDate {
        val isLeap = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0); val nsYear = if (m > 3 || (m == 3 && d >= 14)) y - 1468 else y - 1469
        val (nd, nm) = when {
            m == 3 && d >= 14 || m == 4 && d <= 13 -> (if (m == 3) d - 13 else d + 18) to "ਚੇਤ"
            m == 4 && d >= 14 || m == 5 && d <= 14 -> (if (m == 4) d - 13 else d + 17) to "ਵੈਸਾਖ"
            m == 5 && d >= 15 || m == 6 && d <= 14 -> (if (m == 5) d - 14 else d + 17) to "ਜੇਠ"
            m == 6 && d >= 15 || m == 7 && d <= 15 -> (if (m == 6) d - 14 else d + 16) to "ਹਾੜ"
            m == 7 && d >= 16 || m == 8 && d <= 15 -> (if (m == 7) d - 15 else d + 16) to "ਸਾਵਣ"
            m == 8 && d >= 16 || m == 9 && d <= 14 -> (if (m == 8) d - 15 else d + 15) to "ਭਾਦੋਂ"
            m == 9 && d >= 15 || m == 10 && d <= 14 -> (if (m == 9) d - 14 else d + 16) to "ਅੱਸੂ"
            m == 10 && d >= 16 || m == 11 && d <= 14 -> (if (m == 10) d - 15 else d + 16) to "ਕੱਤਕ"
            m == 11 && d >= 15 || m == 12 && d <= 14 -> (if (m == 11) d - 14 else d + 16) to "ਮੱਘਰ"
            m == 12 && d >= 15 || m == 1 && d <= 13 -> (if (m == 12) d - 14 else d + 17) to "ਪੋਹ"
            m == 1 && d >= 14 || m == 2 && d <= 12 -> (if (m == 1) d - 13 else d + 18) to "ਮਾਘ"
            m == 2 && d >= 13 || m == 3 && d <= 13 -> (if (m == 2) d - 12 else d + (if (isLeap) 17 else 16)) to "ਫੱਗਣ"
            else -> (0) to "ਅਗਿਆਤ"
        }
        return NanakshahiDate(nd, nm, nsYear)
    }

    private fun jdToMillis(jd: Double): Long = ((jd - 2440587.5) * 86400000.0).roundToLong()
    private fun julianDay(cal: Calendar): Double { val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = cal.timeInMillis }; val y = utcCal.get(Calendar.YEAR); val m = utcCal.get(Calendar.MONTH) + 1; val d = utcCal.get(Calendar.DAY_OF_MONTH) + (utcCal.get(Calendar.HOUR_OF_DAY) + utcCal.get(Calendar.MINUTE) / 60.0 + utcCal.get(Calendar.SECOND) / 3600.0) / 24.0; return julianDayFromFields(y, m, d) }
    private fun julianDayFromFields(yIn: Int, mIn: Int, d: Double): Double { var y = yIn; var m = mIn; if (m <= 2) { y -= 1; m += 12 }; val isGregorian = (y > 1752) || (y == 1752 && m > 9) || (y == 1752 && m == 9 && d >= 14.0); return if (isGregorian) { val a = y / 100; val b = 2 - a + (a / 4); floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5 } else { floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d - 1524.5 } }
    fun toGurmukhiNumber(n: Int): String { val map = mapOf('0' to '੦', '1' to '੧', '2' to '੨', '3' to '੩', '4' to '੪', '5' to '੫', '6' to '੬', '7' to '੭', '8' to '੮', '9' to '੯'); return n.toString().map { map[it] ?: it }.joinToString("") }
    fun toGurmukhiYear(n: Int): String { val absN = if (n <= 0) abs(n) + 1 else n; val numStr = toGurmukhiNumber(absN); return if (n <= 0) "$numStr ਈ.ਪੂ." else numStr }
    fun weekdayNamePunjabi(index: Int): String = listOf("ਐਤਵਾਰ", "ਸੋਮਵਾਰ", "ਮੰਗਲਵਾਰ", "ਬੁਧਵਾਰ", "ਵੀਰਵਾਰ", "ਸ਼ੁਕਰਵਾਰ", "ਸ਼ਨੀਚਰਵਾਰ").getOrElse(index) { "" }
    private fun calculateDeltaT(year: Double): Double = when { year < 1600 -> 1574.2 - 556.01 * ((year - 1000) / 100); year < 2005 -> 64.69 + 0.293 * (year - 2005); else -> -20 + 32 * ((year - 1820) / 100).pow(2) }
    private fun jdToYear(jd: Double): Double = (jd - 2440587.5) / 365.25 + 1970.0
}
