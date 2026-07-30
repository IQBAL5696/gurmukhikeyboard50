package com.iqbal.gurmukhikeyboard50
import java.text.DecimalFormat; import java.text.DecimalFormatSymbols; import java.util.*; import kotlin.math.*
object CalculatorHelper {
    private val gurmukhiToEnglishMap = mapOf('੦' to '0', '੧' to '1', '੨' to '2', '੩' to '3', '੪' to '4', '੫' to '5', '੬' to '6', '੭' to '7', '੮' to '8', '੯' to '9')

    fun formatIndianStyle(value: Double): String = formatIndianStyleString(value.toString())
    fun formatInternationalStyle(value: Double): String = formatInternationalStyleString(value.toString())

    fun formatIndianStyleString(value: String): String {
        if (value.isEmpty() || value == "Infinity" || value == "-Infinity" || value == "NaN") return value
        val parts = value.split(".")
        var integerPart = parts[0]
        val isNegative = integerPart.startsWith("-")
        if (isNegative) integerPart = integerPart.substring(1)
        
        // Remove trailing .0 from Double.toString() if it was there
        if (parts.size > 1 && (parts[1] == "0" || parts[1].isEmpty()) && !value.contains(".")) {
            // This is just to handle the case where we pass a whole number as string
        }

        val sb = StringBuilder()
        var count = 0
        var firstGroup = true
        for (i in integerPart.length - 1 downTo 0) {
            sb.append(integerPart[i])
            count++
            if (firstGroup) {
                if (count == 3 && i > 0) { sb.append(","); count = 0; firstGroup = false }
            } else {
                if (count == 2 && i > 0) { sb.append(","); count = 0 }
            }
        }
        var result = (if (isNegative) "-" else "") + sb.reverse().toString()
        if (parts.size > 1) {
            // Keep at most 10 decimal places as per dfSimple
            val dec = if (parts[1].length > 10) parts[1].substring(0, 10) else parts[1]
            if (dec.isNotEmpty()) result += ".$dec"
        }
        return result
    }

    fun formatInternationalStyleString(value: String): String {
        if (value.isEmpty() || value == "Infinity" || value == "-Infinity" || value == "NaN") return value
        val parts = value.split(".")
        var integerPart = parts[0]
        val isNegative = integerPart.startsWith("-")
        if (isNegative) integerPart = integerPart.substring(1)

        val sb = StringBuilder()
        var count = 0
        for (i in integerPart.length - 1 downTo 0) {
            if (count == 3) { sb.append(","); count = 0 }
            sb.append(integerPart[i])
            count++
        }
        var result = (if (isNegative) "-" else "") + sb.reverse().toString()
        if (parts.size > 1) {
            val dec = if (parts[1].length > 10) parts[1].substring(0, 10) else parts[1]
            if (dec.isNotEmpty()) result += ".$dec"
        }
        return result
    }

    fun getInternationalCompact(text: String): String? {
        val clean = text.replace(",", "").replace(" ", "").split(".")[0]; val n = clean.toLongOrNull() ?: return null; if (n < 1000000) return null; val result = when { n >= 1000000000000L -> String.format("%.2f Trillion", n / 1000000000000.0); n >= 1000000000L -> String.format("%.2f Billion", n / 1000000000.0); n >= 1000000L -> String.format("%.2f Million", n / 1000000.0); else -> null }?.trimEnd('0')?.trimEnd('.'); return if (result != null) "$text=$result" else null
    }

    private fun parseUnitsToSeconds(input: String, treatMAsMonth: Boolean): String {
        val yearSec = 31536000L; val monthSec = if (treatMAsMonth) 2628000L else 60L; val daySec = 86400L
        val durationPattern = "([\\d\\.]+)([ymdhs])".toRegex()
        return durationPattern.replace(input) { match ->
            val v = match.groupValues[1]; val unit = match.groupValues[2]
            val mult = when (unit) { "y" -> yearSec; "m" -> monthSec; "d" -> daySec; "h" -> 3600L; "s" -> 1L; else -> 1L }
            "($v*$mult)"
        }
    }

    private fun formatSecondsToResult(totalSeconds: Double, isAgeResult: Boolean): String {
        val isNeg = totalSeconds < 0; var s = round(abs(totalSeconds)).toLong()
        if (isAgeResult) {
            val yearSec = 31536000L; val monthSec = 2628000L
            val y = s / yearSec; s %= yearSec; val m = s / monthSec; s %= monthSec; val d = round(s / 86400.0).toLong()
            val parts = mutableListOf<String>()
            if (y > 0) parts.add("${y}y"); if (m > 0) parts.add("${m}m"); if (d > 0) parts.add("${d}d")
            if (parts.isEmpty()) return "0d"
            val res = parts.joinToString(" "); return if (isNeg) "-($res)" else res
        } else {
            val h = s / 3600; s %= 3600; val m = s / 60; val sec = s % 60
            val parts = mutableListOf<String>()
            if (h > 0) parts.add("${h}h")
            if (m > 0) parts.add("${m}m")
            if (sec > 0 || parts.isEmpty()) parts.add("${sec}s")
            val res = parts.joinToString(" "); return if (isNeg) "-($res)" else res
        }
    }

    fun evaluate(text: String): String? {
        if (text.isBlank()) return null
        // Normalize all common multiplication/division symbols
        var expression = text.replace(",", "").replace("×", "*").replace("x", "*").replace("X", "*").replace("÷", "/").trim()
        if (expression.endsWith("=")) expression = expression.dropLast(1)
        if (expression.isBlank()) return null
        
        // Don't evaluate if it ends with an operator (likely still typing)
        if ("+-*/%^√".any { expression.endsWith(it) }) return null

        val sb = StringBuilder()
        var hasDigit = false
        var hasOperator = false
        val mathChars = "+-*/%^√!"
        val allowedChars = "0123456789" + mathChars + "().hmsydsincoartalgep"

        for (char in expression) {
            val englishChar = gurmukhiToEnglishMap[char] ?: char
            if (englishChar.isDigit() || char == 'π' || char == 'e') hasDigit = true
            if (englishChar in mathChars) hasOperator = true
            if (englishChar !in allowedChars && char !in listOf('π', 'e') && !char.isWhitespace()) return null
            if (!char.isWhitespace()) sb.append(englishChar)
        }
        
        // If it's just a number (no operators like +, -, *, / etc.), don't show calculator result
        if (!hasDigit || !hasOperator) return null
        var englishExpr = sb.toString()

        val hasUnits = englishExpr.contains("[ymdhs]".toRegex())
        val isAge = englishExpr.contains("[yd]".toRegex())

        if (hasUnits) englishExpr = parseUnitsToSeconds(englishExpr, isAge)

        return try {
            val result = evalExpression(englishExpr)
            if (result.isInfinite() || result.isNaN()) return null
            val formattedResult = if (hasUnits) formatSecondsToResult(result, isAge) else formatIndianStyle(result)
            "$text=$formattedResult"
        } catch (e: Exception) { null }
    }

    private fun evalExpression(str: String): Double {
        return object {
            var pos = -1; var ch = 0; var lastValue = 0.0; var lastOp = ' '
            fun nextChar() { ch = if (++pos < str.length) str[pos].code else -1 }
            fun eat(charToEat: Int): Boolean { while (ch == ' '.code) nextChar(); if (ch == charToEat) { nextChar(); return true }; return false }
            fun parse(): Double { nextChar(); val x = parseExpression(); if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar()); return x }
            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> { lastOp = '+'; lastValue = x; val y = parseTerm(); x += y }
                        eat('-'.code) -> { lastOp = '-'; lastValue = x; val y = parseTerm(); x -= y }
                        else -> return x
                    }
                }
            }
            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> { lastOp = '*'; lastValue = x; x *= parseFactor() }
                        eat('/'.code) -> { lastOp = '/'; lastValue = x; x /= parseFactor() }
                        else -> return x
                    }
                }
            }
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor(); if (eat('-'.code)) return -parseFactor()
                var x: Double; val startPos = pos
                when {
                    eat('('.code) -> { x = parseExpression(); eat(')'.code) }
                    ch in '0'.code..'9'.code || ch == '.'.code -> { while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar(); x = str.substring(startPos, pos).toDouble() }
                    ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code -> {
                        while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                        val func = str.substring(startPos, pos)
                        x = when (func) {
                            "sin" -> sin(Math.toRadians(parseFactor())); "cos" -> cos(Math.toRadians(parseFactor())); "tan" -> tan(Math.toRadians(parseFactor()))
                            "sqrt" -> sqrt(parseFactor()); "log" -> log10(parseFactor()); "ln" -> ln(parseFactor()); "PI" -> PI; "E" -> E
                            else -> 0.0
                        }
                    }
                    eat('√'.code) -> x = sqrt(parseFactor())
                    else -> return 0.0
                }
                if (eat('%'.code)) {
                    if (lastOp == '+' || lastOp == '-') { x = lastValue * (x / 100.0) } else { x /= 100.0 }
                }
                if (eat('!'.code)) { var res = 1.0; for (i in 1..x.toInt()) res *= i; x = res }
                if (eat('^'.code)) x = x.pow(parseFactor())
                return x
            }
        }.parse()
    }
}
