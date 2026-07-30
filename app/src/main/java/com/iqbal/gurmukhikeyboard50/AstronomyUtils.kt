package com.iqbal.gurmukhikeyboard50

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

object AstronomyUtils {
    fun julianDay(cal: Calendar): Double {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = cal.timeInMillis }
        val y = if (u.get(Calendar.ERA) == 0) 1 - u.get(Calendar.YEAR) else u.get(Calendar.YEAR)
        val m = u.get(Calendar.MONTH) + 1
        val d = u.get(Calendar.DAY_OF_MONTH) + (u.get(Calendar.HOUR_OF_DAY) + u.get(Calendar.MINUTE) / 60.0 + u.get(Calendar.SECOND) / 3600.0) / 24.0
        return julianDayFromFields(y, m, d)
    }

    fun julianDayFromFields(yIn: Int, mIn: Int, d: Double): Double {
        var y = yIn
        var m = mIn
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0).toInt()
        val isG = (yIn > 1752) || (yIn == 1752 && (mIn > 9 || (mIn == 9 && d >= 14)))
        val b = if (isG) 2 - a + floor(a / 4.0).toInt() else 0
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    fun calculateDeltaT(y: Double): Double {
        val t = (y - 1820) / 100.0
        return when {
            y < 1820 -> 32.0 * t * t + 20.0
            y < 2005 -> 64.69 + 0.293 * (y - 2005)
            else -> 64.69 + 0.293 * (y - 2005) + 0.005 * (y - 2005).pow(2)
        }
    }

    fun jdToYear(jd: Double): Double = (jd - 2440587.5) / 365.25 + 1970.0

    fun getWeekdayFromJD(jd: Double): Int = (floor(jd + 0.5).toInt() + 1) % 7

    fun sunLongitudeJD(jd: Double): Double {
        val t = (jd + calculateDeltaT(jdToYear(jd)) / 86400.0 - 2451545.0) / 36525.0
        var l = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val mR = Math.toRadians(m % 360.0)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mR) + (0.019993 - 0.000101 * t) * sin(2 * mR) + 0.000289 * sin(3 * mR)
        var lon = (l + c) % 360.0
        lon += 0.00134 * cos(Math.toRadians(153.23 + 22518.7541 * t))
        lon += 0.00154 * cos(Math.toRadians(216.57 + 45037.5082 * t))
        lon += 0.00200 * cos(Math.toRadians(312.69 + 32964.4670 * t))
        lon += 0.00179 * sin(Math.toRadians(350.74 + 445267.1142 * t))
        return (lon + 360.0) % 360.0
    }

    fun moonLongitudeJD(jd: Double): Double {
        val t = (jd + calculateDeltaT(jdToYear(jd)) / 86400.0 - 2451545.0) / 36525.0
        val lp = 218.3164477 + 481267.8812307 * t
        val d = 297.8501921 + 445267.1114034 * t
        val mm = 134.9633964 + 477198.8675055 * t
        val f = 93.2720950 + 483202.0175233 * t
        val dR = Math.toRadians(d % 360.0)
        val mmR = Math.toRadians(mm % 360.0)
        val fR = Math.toRadians(f % 360.0)
        var lon = lp + 6.288774 * sin(mmR) + 1.274027 * sin(2 * dR - mmR) + 0.658309 * sin(2 * dR) + 0.213618 * sin(2 * mmR) - 0.114332 * sin(2 * fR) + 0.058793 * sin(2 * dR - 2 * mmR) + 0.057066 * sin(2 * dR - Math.toRadians(357.529) - mmR) + 0.053320 * sin(2 * dR + mmR) + 0.045758 * sin(2 * dR - mmR - Math.toRadians(357.529))
        return (lon % 360.0 + 360.0) % 360.0
    }

    fun getSunRAAndDec(jd: Double): Pair<Double, Double> {
        val l = Math.toRadians(sunLongitudeJD(jd))
        val e = Math.toRadians(23.439)
        val ra = atan2(sin(l) * cos(e), cos(l))
        val dec = asin(sin(e) * sin(l))
        return Math.toDegrees(ra) to Math.toDegrees(dec)
    }

    fun getMoonRAAndDec(jd: Double): Pair<Double, Double> {
        val t = (jd - 2451545.0) / 36525.0
        val lp = 218.316 + 481267.881 * t
        val d = Math.toRadians(297.850 + 445267.111 * t)
        val m = Math.toRadians(357.529 + 35999.050 * t)
        val mm = Math.toRadians(134.963 + 477198.867 * t)
        val f = Math.toRadians(93.272 + 483202.018 * t)
        var lon = lp + 6.289 * sin(mm) + 1.274 * sin(2 * d - mm) + 0.658 * sin(2 * d) + 0.214 * sin(2 * mm) - 0.186 * sin(m) - 0.114 * sin(2 * f) + 0.060 * sin(2 * d - m)
        var lat = 5.128 * sin(f) + 0.281 * sin(mm + f) + 0.278 * sin(mm - f) + 0.173 * sin(2 * d - f) + 0.055 * sin(2 * d - mm + f)
        val eps = Math.toRadians(23.439 - 0.013 * t)
        val ra = atan2(sin(Math.toRadians(lon)) * cos(eps) - tan(Math.toRadians(lat)) * sin(eps), cos(Math.toRadians(lon)))
        val dec = asin(sin(Math.toRadians(lat)) * cos(eps) + cos(Math.toRadians(lat)) * sin(eps) * sin(Math.toRadians(lon)))
        return Math.toDegrees(ra) to Math.toDegrees(dec)
    }

    fun getGreenwichSiderealTime(jd: Double): Double {
        val jd0 = floor(jd + 0.5) - 0.5
        val ut = (jd - jd0) * 24.0
        val t = (jd0 - 2451545.0) / 36525.0
        var gmst = 6.697374558 + 0.06570982441908 * (jd0 - 2451545.0) + 1.00273790935 * ut + 0.000026 * t * t
        return (gmst * 15.0 % 360.0 + 360.0) % 360.0
    }

    fun calculateEquationOfTime(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = 280.46607 + 36000.76908 * t
        val m = 357.52911 + 35999.05029 * t
        val e = 0.016708634 - 0.000042037 * t
        val ob = 23.439291 - 0.0130042 * t
        val y = tan(Math.toRadians(ob) / 2.0).pow(2)
        val eq = y * sin(2.0 * Math.toRadians(l0)) - 2.0 * e * sin(Math.toRadians(m)) + 4.0 * e * y * sin(Math.toRadians(m)) * cos(2.0 * Math.toRadians(l0)) - 0.5 * y * y * sin(4.0 * Math.toRadians(l0)) - 1.25 * e * e * sin(2.0 * Math.toRadians(m))
        return eq * 4.0 * 180.0 / PI
    }
}
