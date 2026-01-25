package com.iqbal.gurmukhikeyboard50

import java.util.Calendar
import java.util.GregorianCalendar

fun main() {
    val cal = GregorianCalendar(1667, Calendar.JANUARY, 1)
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    println("Jan 1, 1667 (Gregorian) Day of Week: $dayOfWeek")
    
    val cal2 = GregorianCalendar(1666, Calendar.DECEMBER, 22)
    // GregorianCalendar's default cutover is 1582. 
    // So 1666 is Gregorian.
    println("Dec 22, 1666 (Gregorian) Day of Week: ${cal2.get(Calendar.DAY_OF_WEEK)}")
}
