package com.iqbal.gurmukhikeyboard50

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDecember1705GregorianMasya() {
        NanakshahiCalendar.selectedSystem = NanakshahiCalendar.CalendarSystem.GREGORIAN
        val jd16 = AstronomyUtils.julianDayFromFields(1705, 12, 16.0)
        val tithi16 = NanakshahiCalendar.getTithiNumberFromJD(jd16)
        // Tithi 30 is Amavasya / Masya
        assertEquals(30, tithi16)

        val jd5 = AstronomyUtils.julianDayFromFields(1705, 12, 5.0)
        val tithi5 = NanakshahiCalendar.getTithiNumberFromJD(jd5)
        assertNotEquals(30, tithi5)
    }

    @Test
    fun testSuryaSiddhantaSystem() {
        NanakshahiCalendar.selectedSystem = NanakshahiCalendar.CalendarSystem.BIKRAMI_SURYA
        assertEquals(NanakshahiCalendar.CalendarSystem.BIKRAMI_SURYA, NanakshahiCalendar.selectedSystem)

        // Test Vaisakhi 1699 (30 March 1699 Julian = JD 2341694.5)
        val jd1699 = AstronomyUtils.julianDayFromFields(1699, 3, 30.0)
        val (month, day) = NanakshahiCalendar.getSolarBikramiDate(
            null,
            jd1699,
            NanakshahiCalendar.LocationConfig.AMRITSAR
        )
        assertNotNull(month)
        assertTrue(day in 1..31)

        // Verify Bikrami year calculation
        val bikYear = NanakshahiCalendar.getBikramiYear(30, 3, 1699)
        assertEquals(1756, bikYear)
    }
}