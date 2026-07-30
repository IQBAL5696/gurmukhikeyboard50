package com.iqbal.gurmukhikeyboard50

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Date

fun main() {
    // GregorianCalendar default cutover is 1582.
    // So Jan 1, 1667 is shown as 1667.
    val cal = GregorianCalendar(1667, Calendar.JANUARY, 1)
    println("Gregorian: ${cal.get(Calendar.DAY_OF_MONTH)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.YEAR)}")
    
    // To see the Historical (Julian) date, we change the cutover to 1752 (British/Indian style)
    val historicalCal = GregorianCalendar()
    historicalCal.setGregorianChange(Date(-6857222400000L)) // Sept 14, 1752
    historicalCal.timeInMillis = cal.timeInMillis
    
    val day = historicalCal.get(Calendar.DAY_OF_MONTH)
    val month = historicalCal.get(Calendar.MONTH) + 1
    val year = historicalCal.get(Calendar.YEAR)
    
    println("Historical (Julian): $day-$month-$year") 
    // This will print 22-12-1666
}
