package com.iqbal.gurmukhikeyboard50
import android.content.Context; import android.graphics.Color; import java.text.SimpleDateFormat; import java.util.Calendar; import java.util.GregorianCalendar; import java.util.Locale; import java.util.TimeZone; import kotlin.math.*

object NanakshahiCalendar { const val SUDI = "ਸੁਦੀ"; const val VADI = "ਵਦੀ"; const val ADHIK = "ਅਧਿਕ"; var currentTimeZone: TimeZone = TimeZone.getDefault()
    enum class CalendarSystem { MOOL_NANAKSHAHI, BIKRAMI_DRIK, BIKRAMI_SURYA, BIKRAMI_LUNAR, GREGORIAN }; var selectedSystem: CalendarSystem = CalendarSystem.BIKRAMI_DRIK; set(value) { if (field != value) { field = value; clearCaches() } }
    var isMoolNanakshahiMode: Boolean; get() = selectedSystem == CalendarSystem.MOOL_NANAKSHAHI; set(value) { selectedSystem = if (value) CalendarSystem.MOOL_NANAKSHAHI else CalendarSystem.BIKRAMI_DRIK }
    private fun clearCaches() { solarCache.clear(); tithiCache.clear(); GurpurabData.clearCache() }
    data class NanakshahiDate(val day: Int, var month: String, val year: Int)
    data class TithiResult(val tithi: Int, val paksha: String, val monthName: String, val isAdhik: Boolean, val isPunia: Boolean = false, val isMasaya: Boolean = false, val tithiDisplay: String = "", val percent: Int = 0, val startTime: Long = 0, val endTime: Long = 0, val nakshatra: String = "", val yoga: String = "", val karana: String = "")
    data class Gurpurab(val day: Int, val month: String, val name: String, val history: String? = null, val gurpurabColor: Int? = null, val gregDate: Calendar? = null)
    data class MonthlyDayCell(val day: Int?, val displayText: String, val isToday: Boolean = false, val gurpurabName: String? = null, val gurpurabHistory: String? = null, val gurpurabColor: Int? = null, val isSangrand: Boolean = false, val isPunia: Boolean = false, val isMasaya: Boolean = false, val isEmpty: Boolean = false, val gregCal: Calendar? = null, val isCurrentMonth: Boolean = true)
    data class DateDifference(val years: Int, val months: Int, val days: Int)
    data class LocationConfig(val lat: Double, val lon: Double) { companion object { val AMRITSAR = LocationConfig(31.62, 74.87) } }
    val DESI_MONTHS = listOf("ਚੇਤ", "ਵੈਸਾਖ", "ਜੇਠ", "ਹਾੜ", "ਸਾਵਣ", "ਭਾਦੋਂ", "ਅੱਸੂ", "ਕੱਤਕ", "ਮੱਘਰ", "ਪੋਹ", "ਮਾਘ", "ਫੱਗਣ"); val RASHIS = listOf("ਮੇਖ", "ਬ੍ਰਿਖ", "ਮਿਥੁਨ", "ਕਰਕ", "ਸਿੰਘ", "ਕੰਨਿਆ", "ਤੁਲਾ", "ਬ੍ਰਿਸ਼ਚਕ", "ਧਨੂ", "ਮਕਰ", "ਕੁੰਭ", "ਮੀਨ"); val RASHI_SYMBOLS = listOf("♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓"); val NAKSHATRAS = listOf("ਅਸ਼ਵਿਨੀ", "ਭਰਣੀ", "ਕ੍ਰਿਤਿਕਾ", "ਰੋਹਿਣੀ", "ਮ੍ਰਿਗਸ਼ਿਰਾ", "ਆਰਦਰਾ", "ਪੁਨਰਵਸੂ", "ਪੁਸ਼ਯ", "ਆਸ਼ਲੇਸ਼ਾ", "ਮਘਾ", "ਪੂਰਵਾ ਫਾਲਗੁਨੀ", "ਉੱਤਰਾ ਫਾਲਗੁਨੀ", "ਹਸਤ", "ਚਿਤਰਾ", "ਸਵਾਤੀ", "ਵਿਸ਼ਾਖਾ", "ਅਨੁਰਾਧਾ", "ਜੇਸ਼ਠਾ", "ਮੂਲ", "ਪੂਰਵਾਸ਼ਾਢਾ", "ਉੱਤਰਾਸ਼ਾਢਾ", "ਸ਼ਰਵਣ", "ਧਨਿਸ਼ਠਾ", "ਸ਼ਤਭਿਸ਼ਾ", "ਪੂਰਵਾ ਭਾਦਰਪਦ", "ਉੱਤਰਾ ਭਾਦਰਪਦ", "ਰੇਵਤੀ"); val YOGAS = listOf("ਵਿਸ਼ਕੁੰਭ", "ਪ੍ਰੀਤੀ", "ਆਯੁਸ਼ਮਾਨ", "ਸੌਭਾਗਯ", "ਸ਼ੋਭਨ", "ਅਤਿਗੰਡ", "ਸੁਕਰਮਾ", "ਧ੍ਰਿਤੀ", "ਸ਼ੂਲ", "ਗੰਡ", "ਵ੍ਰਿਧੀ", "ਧਰੁਵ", "ਵਿਆਘਾਤ", "ਹਰਸ਼ਣ", "ਵਜ੍ਰ", "ਸਿੱਧੀ", "ਵਿਆਤੀਪਾਤ", "ਵਰੀਯਾਨ", "ਪਰਿਘ", "ਸ਼ਿਵ", "ਸਿੱਧ", "ਸਾਧਯ", "ਸ਼ੁਭ", "ਸ਼ੁਕਲ", "ਬ੍ਰਹਮ", "ਐਂਦਰ", "ਵੈਧ੍ਰਿਤੀ"); val KARANAS = listOf("ਬਵ", "ਬਾਲਵ", "ਕੌਲਵ", "ਤੈਤਿਲ", "ਗਰ", "ਵਣਿਜ", "ਵਿਸ਼ਟੀ", "ਸ਼ਕੁਨੀ", "ਚਤੁਸ਼ਪਦ", "ਨਾਗ", "ਕਿੰਸਤੁਘਨ")

    private val tithiCache = mutableMapOf<Long, TithiResult>(); private val solarCache = mutableMapOf<Long, Pair<String, Int>>();
    fun getCalendarInstance(y: Int, m: Int, d: Int): Calendar { val cal = Calendar.getInstance(currentTimeZone); cal.clear(); if (y <= 0) { cal.set(Calendar.ERA, GregorianCalendar.BC); cal.set(Calendar.YEAR, 1 - y) } else { cal.set(Calendar.ERA, GregorianCalendar.AD); cal.set(Calendar.YEAR, y) }; cal.set(Calendar.MONTH, m - 1); cal.set(Calendar.DAY_OF_MONTH, d); cal.set(Calendar.HOUR_OF_DAY, 12); return cal }
    fun getAstronomicalYear(cal: Calendar): Int { val y = cal.get(Calendar.YEAR); return if (cal.get(Calendar.ERA) == GregorianCalendar.BC) 1 - y else y }
    fun calculateDateDifference(s: Calendar, e: Calendar): DateDifference { if (s.timeInMillis == e.timeInMillis) return DateDifference(0, 0, 0); var start = s.clone() as Calendar; var end = e.clone() as Calendar; if (start.timeInMillis > end.timeInMillis) { val t = start; start = end; end = t }; var y = getAstronomicalYear(end) - getAstronomicalYear(start); var m = end.get(Calendar.MONTH) - start.get(Calendar.MONTH); var d = end.get(Calendar.DAY_OF_MONTH) - start.get(Calendar.DAY_OF_MONTH); if (d < 0) { val p = end.clone() as Calendar; p.add(Calendar.MONTH, -1); d += p.getActualMaximum(Calendar.DAY_OF_MONTH); m-- }; if (m < 0) { m += 12; y-- }; return DateDifference(y, m, d) }

    fun getBikramiYear(d: Int, m: Int, y: Int): Int {
        return if (m < 3 || (m == 3 && d < 14)) y + 56 else y + 57
    }

    private fun createMonthlyDayCell(ctx: Context, cal: Calendar, curr: Boolean, desi: Boolean, mName: String?, location: LocationConfig): MonthlyDayCell { val jd = julianDay(cal); val srJd = calculateSunriseJD(jd, location.lat, location.lon); val (sM, sD) = getSolarBikramiDate(ctx, jd, location); val tR = getTithiResultFromJD(ctx, srJd, location.lat, location.lon); val ns = getNanakshahiDate(ctx, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, getAstronomicalYear(cal)); val gur = GurpurabData.getSgpcGurpurabs(ctx, ns.year).filter { it.day == ns.day && it.month == ns.month }; val lbl = when { tR.isMasaya -> " (ਮੱਸਿਆ)"; tR.isPunia -> " (ਪੁੰਨਿਆ)"; else -> "" }; val tTxt = "${tR.monthName} ${tR.paksha} ${tR.tithiDisplay}"; val fmt = if (desi) SimpleDateFormat("d MMM", Locale.ENGLISH) else SimpleDateFormat("MMM", Locale.ENGLISH); val eng = if (selectedSystem != CalendarSystem.GREGORIAN && getAstronomicalYear(cal) < 1752) DateFormatter.formatDateJulian(jd) else if (desi) fmt.format(cal.time) else "${cal.get(Calendar.DAY_OF_MONTH)} ${fmt.format(cal.time)}";
        val mainDayStr = if (desi) "${DateFormatter.toGurmukhiNumber(sD)} $sM" else eng
        val subDayStr = if (desi) eng else "${DateFormatter.toGurmukhiNumber(sD)} $sM"
        val disp = "$mainDayStr$lbl\n$subDayStr\n$tTxt" + (if (gur.isNotEmpty()) "\n${gur.joinToString { it.name }}" else "");
        val today = Calendar.getInstance(currentTimeZone); val isT = getAstronomicalYear(cal) == getAstronomicalYear(today) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR); return MonthlyDayCell(if (desi) sD else cal.get(Calendar.DAY_OF_MONTH), disp, isT, gur.firstOrNull()?.name, gur.firstOrNull()?.history, gur.firstOrNull()?.gurpurabColor, sD == 1, tR.isPunia, tR.isMasaya, gregCal = cal.clone() as Calendar, isCurrentMonth = curr) }
    fun generateMonthlyCalendar(ctx: Context, m: Int, y: Int, location: LocationConfig = LocationConfig.AMRITSAR): List<MonthlyDayCell> { val cells = mutableListOf<MonthlyDayCell>(); val c = getCalendarInstance(y, m, 1); val jd = julianDay(c); val w = c.clone() as Calendar; w.add(Calendar.DAY_OF_MONTH, -getWeekdayFromJD(jd)); for (i in 0 until 42) { cells.add(createMonthlyDayCell(ctx, w, getAstronomicalYear(w) == y && w.get(Calendar.MONTH) == m - 1, false, null, location)); w.add(Calendar.DAY_OF_MONTH, 1) }; return cells }
    fun generateMonthlyCalendarDesi(ctx: Context, nm: String, ny: Int, location: LocationConfig = LocationConfig.AMRITSAR): List<MonthlyDayCell> { val s = findDesiMonthStart(ny, nm, ctx) ?: return listOf(); val cells = mutableListOf<MonthlyDayCell>(); val w = s.clone() as Calendar; w.add(Calendar.DAY_OF_MONTH, -getWeekdayFromJD(julianDay(s))); for (i in 0 until 42) { val (m, _) = getSolarBikramiDate(ctx, julianDay(w), location); cells.add(createMonthlyDayCell(ctx, w, m == nm, true, nm, location)); w.add(Calendar.DAY_OF_MONTH, 1) }; return cells }
    fun convert(ctx: Context, d: Int, m: Int, y: Int, location: LocationConfig = LocationConfig.AMRITSAR): String { val c = getCalendarInstance(y, m, d); val jd = julianDay(c); val sr = calculateSunriseJD(jd, location.lat, location.lon); val ss = calculateSunsetJD(jd, location.lat, location.lon); val mr = calculateMoonriseJD(jd, location.lat, location.lon); val ms = calculateMoonsetJD(jd, location.lat, location.lon); val (sM, sD) = getSolarBikramiDate(ctx, jd, location); val tR = getTithiResultFromJD(ctx, sr, location.lat, location.lon); val ns = getNanakshahiDate(ctx, d, m, y); val wd = getWeekdayFromJD(jd); val wdName = DateFormatter.weekdayNamePunjabi(wd); val tF = SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = currentTimeZone }; val dtF = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH).apply { timeZone = currentTimeZone }; val methodLabel = when(selectedSystem) { CalendarSystem.MOOL_NANAKSHAHI -> "ਬਿਕ੍ਰਮੀ (ਮੂਲ ਨਾਨਕਸ਼ਾਹੀ)"; CalendarSystem.BIKRAMI_DRIK -> "ਬਿਕ੍ਰਮੀ (ਦ੍ਰਿਕ ਗਣਿਤ)"; CalendarSystem.BIKRAMI_SURYA -> "ਬਿਕ੍ਰਮੀ (ਸੂਰਯ ਸਿਧਾਂਤ)"; CalendarSystem.BIKRAMI_LUNAR -> "ਬਿਕ੍ਰਮੀ (ਚੰਦਰ ਮਹੀਨਾ)"; CalendarSystem.GREGORIAN -> "ਗ੍ਰੈਗੋਰੀਅਨ (ਅੰਗਰੇਜ਼ੀ)" }; val mLong = AstronomyUtils.moonLongitudeJD(jd); val sLong = AstronomyUtils.sunLongitudeJD(jd); val moonIllum = (1.0 - cos(Math.toRadians(mLong - sLong))) / 2.0 * 100.0; val moonAge = ((mLong - sLong + 360.0) % 360.0) / 12.19075; val moonDist = 385000.56 - 20905.0 * cos(Math.toRadians(134.963 + 477198.867 * (jd - 2451545.0) / 36525.0)); val eqTime = AstronomyUtils.calculateEquationOfTime(jd); val panchak = if (getNakshatraFromJD(jd) in 23..27) "⚠️ ਪੰਚਕ ਚੱਲ ਰਿਹਾ ਹੈ" else ""; val moonDir = getMoonDirection(getTithiNumberFromJD(jd)); val moonIcon = getMoonPhaseIcon(moonIllum, mLong - sLong); val eclipse = detectEclipse(jd, tR.isPunia, tR.isMasaya); val equinox = findEquinoxMoment(jd); val sunPos = AstronomyUtils.getSunRAAndDec(jd); val sRIdx = monthToRashiIndex(sM) - 1; val mRIdx = (floor(getMoonLongitudeSidereal(jd) / 30.0).toInt() % 12); val ayan = getAyanamsa(jd); val ayDeg = floor(ayan).toInt(); val ayMin = floor(abs(ayan - ayDeg) * 60).toInt(); val aySec = floor(abs(((ayan - ayDeg) * 60 - ayMin) * 60)).toInt(); val res = StringBuilder(); val gregMonths = listOf("ਜਨਵਰੀ", "ਫ਼ਰਵਰੀ", "ਮਾਰਚ", "ਅਪ੍ਰੈਲ", "ਮਈ", "ਜੂਨ", "ਜੁਲਾਈ", "ਅਗਸਤ", "ਸਤੰਬਰ", "ਅਕਤੂਬਰ", "ਨਵੰਬਰ", "ਦਸੰਬਰ"); val gregMonth = gregMonths.getOrElse(m - 1) { "" }; val gregDateLine = "$wdName, ${DateFormatter.toGurmukhiNumber(d)} $gregMonth, ${DateFormatter.toGurmukhiYear(y)}"; val nsLine = "ਸੰਮਤ ${DateFormatter.toGurmukhiNanakshahiYear(ns.year)} ਨਾਨਕਸ਼ਾਹੀ"; val dayLen = (ss - sr) * 24.0; val dLH = floor(dayLen).toInt(); val dLM = floor((dayLen - dLH) * 60).toInt(); val fmtMT = { target: Double -> val timeStr = tF.format(java.util.Date(jdToMillis(target))); if (floor(target + 0.5) != floor(jd + 0.5)) "${DateFormatter.toGurmukhiNumber(timeStr)}, ${SimpleDateFormat("MMM d", Locale.ENGLISH).apply { timeZone = currentTimeZone }.format(java.util.Date(jdToMillis(target)))}" else DateFormatter.toGurmukhiNumber(timeStr) }; res.append("📅 **ਤਾਰੀਖ ਤੇ ਸਮਾਂ**\n"); res.append("    $gregDateLine\n"); res.append("    $nsLine\n"); res.append("    $methodLabel: ${DateFormatter.toGurmukhiNumber(sD)} $sM, ਸੰਮਤ ${DateFormatter.toGurmukhiYear(getBikramiYear(d, m, y))} ਬਿਕ੍ਰਮੀ\n"); res.append("    🙏 **ਵਾਰ ਦਾ ਉਪਦੇਸ਼**: ${getGurbaniVaarLine(wd)}\n\n"); res.append("🌙 **ਚੰਦਰ ਜਾਣਕਾਰੀ**\n"); res.append("    ਥਿਤੀ: ${tR.monthName} ${tR.paksha} ${tR.tithiDisplay} $moonIcon\n"); res.append("    🎑 ਚੰਦਰੋਦਯ (Moonrise): ${fmtMT(mr)}\n"); res.append("    🌃 ਚੰਦਰ ਅਸਤ (Moonset): ${fmtMT(ms)}\n"); if (tR.isPunia) res.append("    🌕 ਪੂਰਨਮਾਸੀ (Full Moon): ${DateFormatter.toGurmukhiNumber(tF.format(java.util.Date(jdToMillis(findPurnimaMoment(sr)))))} \n"); if (tR.isMasaya) res.append("    🌑 ਮੱਸਿਆ (New Moon): ${DateFormatter.toGurmukhiNumber(tF.format(java.util.Date(jdToMillis(findAmavasyaMoment(sr)))))} \n"); res.append("    ਸਮਾਪਤੀ: ${dtF.format(java.util.Date(tR.endTime))}\n"); res.append("    ਨਕਸ਼ਤਰ: ${tR.nakshatra}, ਯੋਗ: ${tR.yoga}, ਕਰਣ: ${tR.karana}\n"); res.append("    ਚੰਦਰ ਰਾਸ਼ੀ: ${RASHI_SYMBOLS[max(0, mRIdx)]} ${RASHIS[max(0, mRIdx)]}\n"); res.append("    ਚੰਦਰ ਰੌਸ਼ਨੀ: ${DateFormatter.toGurmukhiNumber(moonIllum.toInt())}%, ਉਮਰ: ${DateFormatter.toGurmukhiNumber(moonAge.toInt())} ਦਿਨ\n"); res.append("    🌍 ਧਰਤੀ–ਚੰਦਰ ਦੂਰੀ: ${DateFormatter.toGurmukhiNumber(moonDist.toInt())} km\n"); res.append("    ਚੰਦਰ ਨਿਵਾਸ (Moon Direction): $moonDir\n"); if (panchak.isNotEmpty()) res.append("    $panchak\n"); if (eclipse.isNotEmpty()) res.append("     Telescope **ਖਗੋਲੀ ਘਟਨਾ**: $eclipse\n\n"); else res.append("\n"); res.append("🌞 **ਸੂਰਜ ਜਾਣਕਾਰੀ**\n"); res.append("    ਸੂਰਜੋਦਯ: ${DateFormatter.toGurmukhiNumber(tF.format(java.util.Date(jdToMillis(sr))))}, ਸੂਰਿਆਸਤ: ${DateFormatter.toGurmukhiNumber(tF.format(java.util.Date(jdToMillis(ss))))}\n"); res.append("    ਦਿਨ ਦੀ ਲੰਬਾਈ: ${DateFormatter.toGurmukhiNumber(dLH)} ਘੰਟੇ ${DateFormatter.toGurmukhiNumber(dLM)} ਮਿੰਟ\n");
        val rCurrent = getSunRashiFromJD(jd);
        val sankJd = findSangrandMoment(jd, rCurrent);
        val sangTime = DateFormatter.toGurmukhiNumber(tF.format(java.util.Date(jdToMillis(sankJd))));
        res.append("    ਸੂਰਜ ਰਾਸ਼ੀ: ${RASHI_SYMBOLS[max(0, sRIdx)]} ${RASHIS[max(0, sRIdx)]}\n");
        res.append("    ਰਾਸ਼ੀ ਪ੍ਰਵੇਸ਼ (ਸੰਕ੍ਰਾਂਤੀ): $sangTime\n");
        res.append("    ਸਮਾਂ ਸਮੀਕਰਨ (EoT): ${DateFormatter.toGurmukhiNumber(floor(abs(eqTime)).toInt())} ਮਿੰਟ ${DateFormatter.toGurmukhiNumber(floor(abs(eqTime % 1.0) * 60).toInt())} ਸੈਕਿੰਡ\n\n"); res.append("🕰 **ਖਗੋਲੀ ਤੇ ਹੋਰ**\n"); res.append("    ਹਿਜਰੀ: ${getHijriDate(jd)}\n"); res.append("    ਫ਼ਾਰਸੀ: ${getPersianDate(jd)}\n"); res.append("    ਅਯਨਾਂਸ਼: $ayDeg° $ayMin' $aySec\", JD: ${DateFormatter.toGurmukhiNumber(floor(jd + 0.5).toInt())}\n"); res.append("    ਬਸੰਤੀ ਸਮਰਾਤ: ${dtF.format(java.util.Date(jdToMillis(equinox)))}\n"); val gur = detectGurpurabs(ctx, ns); if (gur.isNotEmpty()) res.append("\n🎉 **ਧਾਰਮਿਕ ਦਿਹਾੜੇ**: " + gur.joinToString { it.name }); if (sD == 1) res.append("\n📖 **ਬਾਰਹ ਮਾਹਾ ਉਪਦੇਸ਼**: ${getBarahMahaLine(sM)}"); return res.toString() }
    private fun getGurbaniVaarLine(wd: Int): String { return when (wd) { 0 -> "ਆਦਿਤ ਕਰੇ ਭਗਤਿ ਆਰੰਭੁ ॥"; 1 -> "ਸੋਮਵਾਰਿ ਸਸਿ ਅੰਮ੍ਰਿਤੁ ਝਰੈ ॥"; 2 -> "ਮੰਗਲਵਾਰਿ ਲੇ ਪੰਚ ਧਨੁ ॥"; 3 -> "ਬੁਧਵਾਰਿ ਬੁਧਿ ਕਰੈ ਪ੍ਰਗਾਸੁ ॥"; 4 -> "ਬ੍ਰਿਹਸਪਤਿ ਬਿਖਿਆ ਹੇਤੁ ਤਿਆਗੈ ॥"; 5 -> "ਸੁਕ੍ਰਿਤੁ ਸਹਾਰੈ ਸੁ ਮਤਿ ਵਿਚਾਰੈ ॥"; 6 -> "ਥਾਵਰਿ ਥਿਰੁ ਕਰਿ ਰਾਖੈ ਸੋਇ ॥"; else -> "" } }
    private fun detectEclipse(jd: Double, isPunia: Boolean, isMasaya: Boolean): String {
        val (y, m, d) = AstronomyUtils.jdToYear(jd).let { yr -> val z = floor(jd + 0.5).toInt(); val a = if (selectedSystem == CalendarSystem.GREGORIAN || z >= 2361222) { val alpha = floor((z - 1867216.25) / 36524.25).toInt(); z + 1 + alpha - (alpha / 4) } else z; val b = a + 1524; val c = floor((b - 122.1) / 365.25).toInt(); val d = floor(365.25 * c).toInt(); val e = floor((b - d) / 30.6001).toInt(); val day = b - d - floor(30.6001 * e).toInt(); val month = if (e < 14) e - 1 else e - 13; val year = if (month > 2) c - 4716 else c - 4715; Triple(year, month, day) }
        if (y == 2026 && m == 3 && d == 3) return "🌖 ਚੰਦ ਗ੍ਰਹਣ (Lunar Eclipse) - ਸ਼ਾਮ ੫:੫⁹ ਤੋਂ ੬:੪੭"
        val moonLon = AstronomyUtils.moonLongitudeJD(jd); val nodeLon = getAscendingNodeLongitude(jd)
        val distToNode = abs(angleDiff(moonLon, nodeLon))
        val tF = SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = currentTimeZone }
        return when {
            isPunia && distToNode < 6.0 -> {
                val peakJd = findPurnimaMoment(jd)
                val peakTime = DateFormatter.toGurmukhiNumber(tF.format(java.util.Date(jdToMillis(peakJd))))
                "🌑 ਚੰਦ ਗ੍ਰਹਣ ਸੰਭਾਵਨਾ (Possible Lunar Eclipse) - ਸਿਖਰ ਸਮਾਂ: $peakTime"
            }
            isMasaya && distToNode < 10.0 -> {
                val peakJd = findAmavasyaMoment(jd)
                val peakTime = DateFormatter.toGurmukhiNumber(tF.format(java.util.Date(jdToMillis(peakJd))))
                "☀️ ਸੂਰਜ ਗ੍ਰਹਣ ਸੰਭਾਵਨਾ (Possible Solar Eclipse) - ਸਿਖਰ ਸਮਾਂ: $peakTime"
            }
            else -> ""
        }
    }
    private fun getAscendingNodeLongitude(jd: Double): Double { val t = (jd - 2451545.0) / 36525.0; return (125.044522 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000.0 + 360.0) % 360.0 }
    private fun getBarahMahaLine(m: String): String { return when (m) { "ਚੇਤ" -> "ਚੇਤੁ ਗੋਵਿੰਦਾ ਅਰਾਧੀਐ ਹੋਵੈ ਅਨੰਦੁ ਘਣਾ ॥"; "ਵੈਸਾਖ" -> "ਵੈਸਾਖਿ ਧੀਰਨਿ ਕਿਉ ਵਾਢੀਆ ਜਿਨਾ ਪ੍ਰੇਮ ਬਿਛੋਹੁ ॥"; "ਜੇਠ" -> "ਹਰਿ ਜੇਠਿ ਜੁੜੰਦਾ ਲੋੜੀਐ ਜਿਸੁ ਅਗੈ ਸਭਿ n ਨਿਵੰਨਿ ॥"; "ਹਾੜ" -> "ਆਸਾੜੁ ਤਪੰਦਾ ਤਿਸੁ ਲਗੈਰਿ n ਨਾਹੁ n ਜਿੰਨ੍ਹ੍ਹਨਾ ਪਾਸਿ ॥"; "ਸਾਵਣ" -> "ਸਾਵਣਿ ਸਰਸੀ ਕਾਮਣੀ ਚਰਨ ਕਮਲ ਸਿਉ ਪਿਆਰੁ ॥"; "ਭਾਦੋਂ" -> "ਭਾਦੁਇ ਭਰਮਿ ਭੁਲਾਣੀਆ ਜੋਬਨਿ ਪਛੁਤਾਣੀ ॥"; "ਅੱਸੂ" -> "ਅਸੁਨਿ ਪ੍ਰੇਮ ਉਮਾਹੜਾ ਕਿਉ ਪਾਈਐ ਹਰਿ ਦਰਸੁ ॥"; "ਕੱੱਤਕ" -> "ਕਤਿਕਿ ਕਰਮ ਕਮਾਵਣੇ ਦੋਸੁ ਨ ਕਾਹੂ ਜੋਗੁ ॥"; "ਮੱੱਘਰ" -> "ਮੰਘਿਰਿ ਮਾਹਿ ਸੋਹੰਦੀਆ ਹਰਿ ke ਗੁਣ ਗਾਵਣਹਾਰ ॥"; "ਪੋਹ" -> "ਪੋਖਿ ਤੁਸਾਰੁ n ਵਿਆਪਈ ਕੰਠਿ ਮਿਲਿਆ ਹਰਿ n ਨਾਹੁ ॥"; "ਮਾਘ" -> "ਮਾਘਿ ਮਜਨੁ ਸੰਗਿ ਸਾਧੂਆ ਧੂੜੀ ਕਰਿ ਇਸਨਾਨੁ ॥"; "ਫੱੱਗਣ" -> "ਫਲਗੁਣਿ ਅਨੰਦ ਉਪਾਰਜਨਾ ਹਰਿ ਸਜਣ ਪ੍ਰਗਟੇ ਆਇ ॥"; else -> "ਗੁਰੂ ਘਰ ਜਾ ਕੇ ਮਹੀਨੇ ਦਾ ਪਾਠ ਸਰਵਣ ਕਰੋ।" } }
    private fun getMoonPhaseIcon(illum: Double, diff: Double): String { val d = (diff + 360.0) % 360.0; return when { d < 15 -> "🌑"; d < 75 -> "🌒"; d < 105 -> "🌓"; d < 165 -> "🌔"; d < 195 -> "🌕"; d < 255 -> "🌖"; d < 285 -> "🌗"; d < 345 -> "🌘"; else -> "🌑" } }
    fun monthToRashiIndex(m: String): Int = when (m) { "ਵੈਸਾਖ" -> 1; "ਜੇਠ" -> 2; "ਹਾੜ" -> 3; "ਸਾਵਣ" -> 4; "ਭਾਦੋਂ" -> 5; "ਅੱਸੂ" -> 6; "ਕੱੱਤਕ" -> 7; "ਮੱੱਘਰ" -> 8; "ਪੋਹ" -> 9; "ਮਾਘ" -> 10; "ਫੱੱਗਣ" -> 11; "ਚੇਤ" -> 12; else -> 0 }
    fun getShortNanakshahiDate(ctx: Context, d: Int, m: Int, y: Int): String { val ns = getNanakshahiDate(ctx, d, m, y); val bY = getBikramiYear(d, m, y); val c = getCalendarInstance(y, m, d); val jd = julianDay(c); val tR = getTithiResultFromJD(ctx, calculateSunriseJD(jd, LocationConfig.AMRITSAR.lat, LocationConfig.AMRITSAR.lon), LocationConfig.AMRITSAR.lat, LocationConfig.AMRITSAR.lon); val lbl = if (tR.isPunia) " (ਪੁੰਨਿਆ)" else if (tR.isMasaya) " (ਮੱਸਿਆ)" else ""; val eng = SimpleDateFormat("d MMM", Locale.ENGLISH).format(c.time); val wd = weekdayNamePunjabi(getWeekdayFromJD(jd)); return "${toGurmukhiNumber(ns.day)} ${ns.month}, ਸੰਮਤ ${toGurmukhiNanakshahiYear(ns.year)} ਨਾਨਕਸ਼ਾਹੀ, ${tR.monthName} ${tR.paksha} ${tR.tithiDisplay}$lbl, ਸੰਮਤ ${toGurmukhiYear(bY)} ਬਿਕਰਮੀ ($wd, $eng)" }
    private fun angleDiff(a: Double, b: Double): Double { var d = a - b; while (d > 180) d -= 360; while (d < -180) d += 360; return d }
    private fun findSangrandMoment(jd: Double, target: Int? = null): Double { var curr = jd; val r = target ?: getSunRashiFromJD(jd); val tL = (r - 1) * 30.0; for (i in 0..25) { val lon = getSunLongitudeSidereal(curr); var d = angleDiff(lon, tL); if (abs(d) < 1e-6) break; val lonNext = getSunLongitudeSidereal(curr + 1e-4); val der = angleDiff(lonNext, lon) / 1e-4; if (abs(der) < 1e-10) break; curr -= d / der }; return curr }
    private fun findEquinoxMoment(jd: Double): Double { var curr = jd; for (i in 0..15) { val lon = AstronomyUtils.sunLongitudeJD(curr); var d = lon; while (d > 180) d -= 360; while (d < -180) d += 360; if (abs(d) < 1e-6) break; curr -= d / 0.9856 }; return curr }
    private fun isPuniaDay(jd: Double, lat: Double, lon: Double): Boolean { val sunrise = calculateSunriseJD(jd, lat, lon); val nextSunrise = calculateSunriseJD(jd + 1.0, lat, lon); val p = findPurnimaMoment(sunrise); return p >= sunrise && p < nextSunrise }
    private fun isMasayaDay(jd: Double, lat: Double, lon: Double): Boolean { val sunrise = calculateSunriseJD(jd, lat, lon); val nextSunrise = calculateSunriseJD(jd + 1.0, lat, lon); val a = findAmavasyaMoment(sunrise); return a >= sunrise && a < nextSunrise }

    private val base1699Value by lazy { getOldFormulaVal(2342031.5) }

    private fun getOldFormulaVal(j: Double): Double {
        val t = (j - 2451545.0) / 36525.0
        return 23.460148 + 1.396042 * t + 0.000308 * t * t
    }

    private fun getAyanamsa(jd: Double): Double {
        if (selectedSystem == CalendarSystem.BIKRAMI_SURYA) {
            val jd1699 = 2342031.5   // 29 March 1699
            return if (jd < jd1699) {
                getOldFormulaVal(jd)
            } else {
                val years = (jd - jd1699) / 365.2421875
                base1699Value + (0.013969444 * years)
            }
        }
        val t = (jd - 2451545.0) / 36525.0
        return 23.85808 + 1.39633 * t + 0.000309 * t * t
    }

    private fun getSunLongitudeSidereal(jd: Double): Double { val l = AstronomyUtils.sunLongitudeJD(jd); val a = if (selectedSystem == CalendarSystem.MOOL_NANAKSHAHI) getAyanamsa(2451180.5) else getAyanamsa(jd); return (l - a + 360.0) % 360.0 }
    private fun getMoonLongitudeSidereal(jd: Double): Double { val l = AstronomyUtils.moonLongitudeJD(jd); val a = if (selectedSystem == CalendarSystem.MOOL_NANAKSHAHI) getAyanamsa(2451180.5) else getAyanamsa(jd); return (l - a + 360.0) % 360.0 }
    private fun getSunRashiFromJD(jd: Double): Int = (floor(getSunLongitudeSidereal(jd) / 30.0).toInt() % 12) + 1
    fun getSolarBikramiDate(ctx: Context, jd: Double, location: LocationConfig = LocationConfig.AMRITSAR): Pair<String, Int> {
        if (selectedSystem == CalendarSystem.BIKRAMI_LUNAR) {
            val srJd = calculateSunriseJD(jd, location.lat, location.lon)
            val tR = getTithiResultFromJD(ctx, srJd, location.lat, location.lon)
            return tR.monthName to tR.tithi
        }
        if (isMoolNanakshahiMode) { val (y, m, d) = AstronomyUtils.jdToYear(jd).let { yr -> val z = floor(jd + 0.5).toInt(); val a = if (selectedSystem == CalendarSystem.GREGORIAN || z >= 2361222) { val alpha = floor((z - 1867216.25) / 36524.25).toInt(); z + 1 + alpha - (alpha / 4) } else z; val b = a + 1524; val c = floor((b - 122.1) / 365.25).toInt(); val d = floor(365.25 * c).toInt(); val e = floor((b - d) / 30.6001).toInt(); val day = b - d - floor(30.6001 * e).toInt(); val month = if (e < 14) e - 1 else e - 13; val year = if (month > 2) c - 4716 else c - 4715; Triple(year, month, day) }; val isL = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0); val isML = isL && (y % 3200 != 0); return when { (m == 3 && d >= 14) || (m == 4 && d <= 13) -> "ਚੇਤ" to if (m == 3) d - 13 else d + 18; (m == 4 && d >= 14) || (m == 5 && d <= 14) -> "ਵੈਸਾਖ" to if (m == 4) d - 13 else d + 17; (m == 5 && d >= 15) || (m == 6 && d <= 14) -> "ਜੇਠ" to if (m == 5) d - 14 else d + 16; (m == 6 && d >= 15) || (m == 7 && d <= 15) -> "ਹਾੜ" to if (m == 6) d - 14 else d + 16; (m == 7 && d >= 16) || (m == 8 && d <= 15) -> "ਸਾਵਣ" to if (m == 7) d - 15 else d + 16; (m == 8 && d >= 16) || (m == 9 && d <= 14) -> "ਭਾਦੋਂ" to if (m == 8) d - 15 else d + 16; (m == 9 && d >= 15) || (m == 10 && d <= 14) -> "ਅੱਸੂ" to if (m == 9) d - 14 else d + 16; (m == 10 && d >= 15) || (m == 11 && d <= 13) -> "ਕੱਤਕ" to if (m == 10) d - 14 else d + 17; (m == 11 && d >= 14) || (m == 12 && d <= 13) -> "ਮੱਘਰ" to if (m == 11) d - 13 else d + 18; (m == 12 && d >= 14) || (m == 1 && d <= 12) -> "ਪੋਹ" to if (m == 12) d - 13 else d + 18; (m == 1 && d >= 13) || (m == 2 && d <= 11) -> "ਮਾਘ" to if (m == 1) d - 12 else d + 19; else -> "ਫੱਗਣ" to if (m == 2) d - 11 else if (isML) d + 18 else d + 17 } }; 
        val key = jd.toBits() xor (location.lat.hashCode().toLong() xor (location.lon.hashCode().toLong() shl 32)); solarCache[key]?.let { return it }; val endOfDayJD = floor(jd + 0.5) + 0.5; val rCurrent = getSunRashiFromJD(endOfDayJD - 1e-6); val sankrantiJD = findSangrandMoment(endOfDayJD - 2.0, rCurrent); val ds = (floor(jd + 0.5) - floor(sankrantiJD + 0.5)).toInt(); val res = rashiToBikramiMonth(rCurrent) to (ds + 1); if (solarCache.size > 2000) solarCache.clear(); solarCache[key] = res; return res }
    fun getTithiNumberFromJD(jd: Double): Int { var d = AstronomyUtils.moonLongitudeJD(jd) - AstronomyUtils.sunLongitudeJD(jd); while (d < 0) d += 360; return (floor(d / 12.0).toInt() % 30) + 1 }
    fun getTithiFractionFromJD(jd: Double): Double { var d = AstronomyUtils.moonLongitudeJD(jd) - AstronomyUtils.sunLongitudeJD(jd); while (d < 0) d += 360; val t = d / 12.0; return t - floor(t) }
    fun getNakshatraFromJD(jd: Double): Int = (floor(getMoonLongitudeSidereal(jd) * 27.0 / 360.0).toInt() % 27) + 1
    fun findTithiMoment(jd: Double, n: Int): Double { var c = jd; val t = ((n - 1) * 12.0) % 360.0; for (i in 0..15) { var d = (AstronomyUtils.moonLongitudeJD(c) - AstronomyUtils.sunLongitudeJD(c) + 360.0) % 360.0; var dist = d - t; while (dist < -180) dist += 360; while (dist >= 180) dist -= 360; if (abs(dist) < 1e-4) break; c -= dist / 12.19075 }; return c }
    fun findAmavasyaMoment(jd: Double): Double { var c = jd; for (i in 0..15) { var d = (AstronomyUtils.moonLongitudeJD(c) - AstronomyUtils.sunLongitudeJD(c) + 360.0) % 360.0; if (d > 180) d -= 360; if (abs(d) < 1e-4) break; c -= d / 12.19 }; return c }
    fun findPurnimaMoment(jd: Double): Double { var c = jd; for (i in 0..20) { var d = (AstronomyUtils.moonLongitudeJD(c) - AstronomyUtils.sunLongitudeJD(c) + 360.0) % 360.0; var dist = d - 180.0; while (dist < -180) dist += 360; while (dist >= 180) dist -= 360; if (abs(dist) < 1e-4) break; c -= dist / 12.19 }; return c }
    private fun resolveLunarMonthWithAdhik(ctx: Context, srJd: Double, raw: Int): String {
        val dAJd = if (raw <= 15) findMoment(srJd, -25.0, ::findAmavasyaMoment) else findMoment(srJd, 25.0, ::findAmavasyaMoment)
        val r = getSunRashiFromJD(dAJd + 0.001)
        val bM = rashiToBikramiMonth(r)
        val aSJd = if (raw <= 15) dAJd else findMoment(srJd, -25.0, ::findAmavasyaMoment)
        val aEJd = if (raw <= 15) findMoment(srJd, 25.0, ::findAmavasyaMoment) else dAJd
        return if (getSunRashiFromJD(aSJd + 0.001) == getSunRashiFromJD(aEJd - 0.001)) ADHIK + " " + bM else bM
    }
    private fun findMoment(jd: Double, s: Double, f: (Double) -> Double): Double { var r = f(jd); if (s > 0 && r <= jd) r = f(jd + s); if (s < 0 && r >= jd) r = f(jd + s); return r }
    fun calculateMoonriseJD(jd: Double, lat: Double, lon: Double): Double {
        val jd0 = floor(jd + 0.5) - 0.5
        var prevAlt = -1.0
        for (step in 0..96) {
            val cJd = jd0 + step / 96.0
            val (ra, dec) = AstronomyUtils.getMoonRAAndDec(cJd)
            val lst = (AstronomyUtils.getGreenwichSiderealTime(cJd) + lon) % 360.0
            val ha = Math.toRadians((lst - ra + 360.0) % 360.0)
            val alt = asin(sin(Math.toRadians(lat)) * sin(Math.toRadians(dec)) + cos(Math.toRadians(lat)) * cos(Math.toRadians(dec)) * cos(ha))
            if (step > 0 && prevAlt < 0 && alt >= 0) return cJd
            prevAlt = alt
        }
        return jd0 + 0.5
    }
    fun calculateMoonsetJD(jd: Double, lat: Double, lon: Double): Double {
        val jd0 = floor(jd + 0.5) - 0.5
        var prevAlt = 1.0
        for (step in 0..96) {
            val cJd = jd0 + step / 96.0
            val (ra, dec) = AstronomyUtils.getMoonRAAndDec(cJd)
            val lst = (AstronomyUtils.getGreenwichSiderealTime(cJd) + lon) % 360.0
            val ha = Math.toRadians((lst - ra + 360.0) % 360.0)
            val alt = asin(sin(Math.toRadians(lat)) * sin(Math.toRadians(dec)) + cos(Math.toRadians(lat)) * cos(Math.toRadians(dec)) * cos(ha))
            if (step > 0 && prevAlt > 0 && alt <= 0) return cJd
            prevAlt = alt
        }
        return jd0 + 0.9
    }
    private fun calculateSunTimeJD(jdIn: Double, lat: Double, lon: Double, rise: Boolean): Double { val jd0 = floor(jdIn + 0.5) - 0.5; var t = if (rise) 6.0 else 18.0; for (i in 0..5) { val cJd = jd0 + t / 24.0; val (ra, dec) = AstronomyUtils.getSunRAAndDec(cJd); val ha = (AstronomyUtils.getGreenwichSiderealTime(cJd) + lon) - ra; val h = asin(sin(Math.toRadians(lat)) * sin(Math.toRadians(dec)) + cos(Math.toRadians(lat)) * cos(Math.toRadians(dec)) * cos(Math.toRadians(ha))); val diff = Math.toDegrees(h) - (-0.833); if (rise) t -= diff / 15.0 else t += diff / 15.0 }; return jd0 + t / 24.0 }
    fun calculateSunriseJD(jd: Double, lat: Double, lon: Double): Double = calculateSunTimeJD(jd, lat, lon, true)
    fun calculateSunsetJD(jd: Double, lat: Double, lon: Double): Double = calculateSunTimeJD(jd, lat, lon, false)
    fun getTithiResultFromJD(ctx: Context, jd: Double, lat: Double, lon: Double): TithiResult { val key = (jd * 100000).toLong() + selectedSystem.ordinal * 1000; tithiCache[key]?.let { return it }; val raw = getTithiNumberFromJD(jd); val frac = getTithiFractionFromJD(jd); val nRaw = getTithiNumberFromJD(jd + 1.0); val sT = findTithiMoment(jd, raw); val eT = findTithiMoment(jd, raw + 1); val nIdx = getNakshatraFromJD(jd); val nN = NAKSHATRAS.getOrElse(nIdx - 1) { "ਅਗਿਆਤ" }; val isP = isPuniaDay(jd, lat, lon); val isM = isMasayaDay(jd, lat, lon); val pak = if (raw <= 15) SUDI else VADI; val sTInt = when { raw == 15 || raw == 30 -> 15; raw <= 15 -> raw; else -> raw - 15 }; var dTi = DateFormatter.toGurmukhiNumber(sTInt); var fTInt = sTInt; if (nRaw == ((raw % 30) + 1 % 30) + 1 && nRaw != (raw % 30) + 1) { val skR = (raw % 30) + 1; val skD = when { skR == 15 || skR == 30 -> 15; skR <= 15 -> skR; else -> skR - 15 }; dTi += " (${DateFormatter.toGurmukhiNumber(skD)} ਛੱੱਡੀ)" } else if ((isP || isM) && sTInt != 15) { dTi = DateFormatter.toGurmukhiNumber(15); fTInt = 15 }; val mN = resolveLunarMonthWithAdhik(ctx, jd, raw); val sunLon = getSunLongitudeSidereal(jd); val moonLon = getMoonLongitudeSidereal(jd); val yogaIdx = (floor((sunLon + moonLon) % 360.0 / (360.0 / 27.0)).toInt() % 27); val yogaN = YOGAS[yogaIdx]; val karanaVal = floor(getTithiFractionFromJD(jd) * 60.0).toInt(); val karanaIdx = if (raw == 1) 10 else if (raw >= 58) (raw - 58) + 7 else ((karanaVal - 1) % 7); val karanaN = KARANAS[karanaIdx.coerceIn(0, 10)]; val res = TithiResult(fTInt, pak, mN, mN.contains(ADHIK), isP, isM, dTi, (frac * 100).toInt(), jdToMillis(sT), jdToMillis(eT), nN, yogaN, karanaN); if (tithiCache.size > 2000) tithiCache.clear(); tithiCache[key] = res; return res }
    fun findDesiMonthStart(ny: Int, nm: String, ctx: Context): Calendar? { val sC = findChet1ForYear(ctx, ny + 1468); for (i in 0..400) { val jd = julianDay(sC); val (sM, sD) = getSolarBikramiDate(ctx, jd, LocationConfig.AMRITSAR); if (sM == nm && sD == 1) return sC; sC.add(Calendar.DAY_OF_MONTH, 1) }; return null }
    fun findChet1ForYear(ctx: Context, y: Int): Calendar { if (isMoolNanakshahiMode) return getCalendarInstance(y, 3, 14); val c = getCalendarInstance(y, 3, 1).apply { set(Calendar.MILLISECOND, 0) }; for (i in 0..60) { val jd = julianDay(c); val (m, d) = getSolarBikramiDate(ctx, jd, LocationConfig.AMRITSAR); if (m == "ਚੇਤ" && d == 1) return c; c.add(Calendar.DAY_OF_MONTH, 1) }; return getCalendarInstance(y, 3, 14).apply { set(Calendar.MILLISECOND, 0) } }
    fun rashiToBikramiMonth(r: Int): String = when (r) { 1 -> "ਵੈਸਾਖ"; 2 -> "ਜੇਠ"; 3 -> "ਹਾੜ"; 4 -> "ਸਾਵਣ"; 5 -> "ਭਾਦੋਂ"; 6 -> "ਅੱਸੂ"; 7 -> "ਕੱਤਕ"; 8 -> "ਮੱਘਰ"; 9 -> "ਪੋਹ"; 10 -> "ਮਾਘ"; 11 -> "ਫੱਗਣ"; 12 -> "ਚੇਤ"; else -> "ਅਗਿਆਤ" }
    private fun detectGurpurabs(ctx: Context, ns: NanakshahiDate): List<Gurpurab> = GurpurabData.getSgpcGurpurabs(ctx, ns.year).filter { it.day == ns.day && it.month == ns.month }
    fun getNanakshahiDate(ctx: Context, d: Int, m: Int, y: Int): NanakshahiDate { val c = getCalendarInstance(y, m, d).apply { set(Calendar.MILLISECOND, 0) }; val (sM, sD) = getSolarBikramiDate(ctx, julianDay(c), LocationConfig.AMRITSAR); val nsY = if (c.before(findChet1ForYear(ctx, y))) y - 1469 else y - 1468; return NanakshahiDate(sD, sM, nsY) }
    fun jdToMillis(jd: Double): Long = ((jd - 2440587.5) * 86400000.0).roundToLong()
    fun julianDay(cal: Calendar): Double = AstronomyUtils.julianDay(cal)
    fun julianDayFromFields(yIn: Int, mIn: Int, d: Double): Double = AstronomyUtils.julianDayFromFields(yIn, mIn, d)
    fun toGurmukhiNumber(n: Int): String = DateFormatter.toGurmukhiNumber(n)
    fun toGurmukhiNumber(s: String): String = DateFormatter.toGurmukhiNumber(s)
    fun toGurmukhiYear(n: Int): String = DateFormatter.toGurmukhiYear(n)
    fun toGurmukhiNanakshahiYear(n: Int): String = DateFormatter.toGurmukhiNanakshahiYear(n)
    fun weekdayNamePunjabi(i: Int): String = DateFormatter.weekdayNamePunjabi(i)
    fun getWeekdayFromJD(jd: Double): Int = AstronomyUtils.getWeekdayFromJD(jd)
    fun getHijriDate(jd: Double): String { return "" }; fun getPersianDate(jd: Double): String { return "" }; fun getPaharInfo(sr: Double, ss: Double, now: Double): String { val dayP = (ss - sr) / 4.0; val nightP = (1.0 - (ss - sr)) / 4.0; return if (now >= sr && now < ss) { val p = floor((now - sr) / dayP).toInt() + 1; "ਦਿਨ ਦਾ ${DateFormatter.toGurmukhiNumber(p)} ਪਹਿਰ" } else { val rel = if (now < sr) now + 1.0 - ss else now - ss; val p = floor(rel / nightP).toInt() + 1; if (p == 4) "ਅੰਮ੍ਰਿਤ ਵੇਲਾ (ਰਾਤ ਦਾ 4 ਪਹਿਰ)" else "ਰਾਤ ਦਾ ${DateFormatter.toGurmukhiNumber(p)} ਪਹਿਰ" } }; fun getMoonDirection(tithi: Int): String { return when (tithi) { 1, 9 -> "ਪੂਰਬ (East)"; 2, 10 -> "ਉੱੱਤਰ (North)"; 3, 11 -> "ਪੱੱਛਮ (West)"; 4, 12 -> "ਦੱਖਣ (South)"; 5, 13 -> "ਅਗਨੀ (South-East)"; 6, 14 -> "ਨੈਰਿਤ (South-West)"; 7, 15 -> "ਵਾਯਵ (North-West)"; else -> "ਈਸ਼ਾਨ (North-East)" } }
    fun isDaytimeAtLocation(lat: Double, lon: Double, tz: TimeZone): Boolean {
        val now = Calendar.getInstance(tz)
        val jd = julianDay(now)
        val sunPos = AstronomyUtils.getSunRAAndDec(jd)
        val gmst = AstronomyUtils.getGreenwichSiderealTime(jd)
        val hourAngle = Math.toRadians(gmst + lon - sunPos.first)
        val latRad = Math.toRadians(lat)
        val declRad = Math.toRadians(sunPos.second)
        val altitude = asin(sin(latRad) * sin(declRad) + cos(latRad) * cos(declRad) * cos(hourAngle))
        return altitude > 0
    }
}
