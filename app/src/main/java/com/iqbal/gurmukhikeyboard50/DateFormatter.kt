package com.iqbal.gurmukhikeyboard50

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object DateFormatter {
    fun toGurmukhiNumber(n: Int): String {
        val map = mapOf('0' to '੦', '1' to '੧', '2' to '੨', '3' to '੩', '4' to '੪', '5' to '੫', '6' to '੬', '7' to '੭', '8' to '੮', '9' to '੯')
        return n.toString().map { map[it] ?: it }.joinToString("")
    }

    fun toGurmukhiNumber(s: String): String {
        val map = mapOf('0' to '੦', '1' to '੧', '2' to '੨', '3' to '੩', '4' to '੪', '5' to '੫', '6' to '੬', '7' to '੭', '8' to '੮', '9' to '੯')
        return s.map { map[it] ?: it }.joinToString("")
    }

    fun toGurmukhiYear(n: Int): String {
        val aN = if (n <= 0) abs(n) + 1 else n
        val s = toGurmukhiNumber(aN)
        return if (n <= 0) "$s ਈ.ਪੂ." else s
    }

    fun toGurmukhiNanakshahiYear(n: Int): String {
        val aN = if (n <= 0) abs(n) + 1 else n
        val nS = toGurmukhiNumber(aN)
        return if (n <= 0) "$nS ਨਾ.ਪੂ." else nS
    }

    fun weekdayNamePunjabi(i: Int): String =
        listOf("ਐਤਵਾਰ", "ਸੋਮਵਾਰ", "ਮੰਗਲਵਾਰ", "ਬੁਧਵਾਰ", "ਵੀਰਵਾਰ", "ਸ਼ੁਕਰਵਾਰ", "ਸ਼ਨੀਚਰਵਾਰ").getOrElse(i) { "" }

    fun formatDateJulian(jd: Double): String {
        val (y, m, d) = jdToCalendarFieldsJulian(jd)
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "$d ${months[m - 1]}"
    }

    private fun jdToCalendarFieldsJulian(jd: Double): Triple<Int, Int, Int> {
        val z = kotlin.math.floor(jd + 0.5).toInt()
        val b = z + 1524
        val c = kotlin.math.floor((b - 122.1) / 365.25).toInt()
        val d = kotlin.math.floor(365.25 * c).toInt()
        val e = kotlin.math.floor((b - d) / 30.6001).toInt()
        val day = b - d - kotlin.math.floor(30.6001 * e).toInt()
        val month = if (e < 14) e - 1 else e - 13
        val year = if (month > 2) c - 4716 else c - 4715
        return Triple(year, month, day)
    }
}
