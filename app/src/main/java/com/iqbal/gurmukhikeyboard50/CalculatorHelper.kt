package com.iqbal.gurmukhikeyboard50

import java.util.*
import kotlin.math.*

data class Complex(val real: Double, val imag: Double = 0.0) {
    operator fun plus(other: Complex) = Complex(real + other.real, imag + other.imag)
    operator fun minus(other: Complex) = Complex(real - other.real, imag - other.imag)
    operator fun times(other: Complex) = Complex(real * other.real - imag * other.imag, real * other.imag + imag * other.real)
    operator fun div(other: Complex): Complex {
        val d = other.real * other.real + other.imag * other.imag
        return Complex((real * other.real + imag * other.imag) / d, (imag * other.real - real * other.imag) / d)
    }
    fun abs() = sqrt(real * real + imag * imag)
    fun pow(n: Int): Complex {
        var res = Complex(1.0, 0.0)
        for (i in 0 until n) res *= this
        return res
    }
    override fun toString(): String {
        if (abs(imag) < 1e-9) return String.format("%.2f", real).trimEnd('0').trimEnd('.')
        val re = String.format("%.2f", real).trimEnd('0').trimEnd('.')
        val im = String.format("%.2f", abs(imag)).trimEnd('0').trimEnd('.')
        return if (real == 0.0) "${if (imag < 0) "-" else ""}${im}i"
        else "$re ${if (imag < 0) "-" else "+"} ${im}i"
    }
}

object CalculatorHelper {
    private val gurmukhiToEnglishMap = mapOf('੦' to '0', '੧' to '1', '੨' to '2', '੩' to '3', '੪' to '4', '੫' to '5', '੬' to '6', '੭' to '7', '੮' to '8', '੯' to '9')

    private const val H_PLANCK = 6.62607015e-34
    private const val H_BAR = 1.0545718e-34
    private const val K_BOLTZMANN = 1.380649e-23
    private const val G_GRAV = 6.67430e-11
    private const val C_LIGHT = 299792458.0
    private const val R_GAS = 8.314462618
    private const val F_FARADAY = 96485.3321

    fun formatIndianStyle(value: Double): String = formatIndianStyleString(value.toString())

    fun formatIndianStyleString(value: String): String {
        if (value.isEmpty() || value == "Infinity" || value == "-Infinity") return value
        if (value == "NaN") return "Wrong Number"
        // Only format if it's a numeric string
        if (!value.matches(Regex("-?\\d*\\.?\\d*(?:e[+-]?\\d+)?", RegexOption.IGNORE_CASE))) return value
        if (value.contains("E", ignoreCase = true)) return value // Keep scientific notation
        val parts = value.split(".")
        var integerPart = parts[0]
        val isNegative = integerPart.startsWith("-")
        if (isNegative) integerPart = integerPart.substring(1)

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
            val dec = if (parts[1].length > 10) parts[1].substring(0, 10) else parts[1]
            if (dec.isNotEmpty()) result += ".$dec"
        }
        return result
    }

    fun formatInternationalStyleString(value: String): String {
        if (value.isEmpty() || value == "Infinity" || value == "-Infinity") return value
        if (value == "NaN") return "Wrong Number"
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

    fun evaluate(text: String): String? {
        if (text.isBlank()) return null
        val result = evaluateFull(text, false) ?: return null
        if (result is Double) {
            if (result.isNaN() || result.isInfinite()) return null
            return "$text=${formatIndianStyle(result)}"
        }
        // If it's already an equation result (contains roots or descriptive string)
        val resultStr = result.toString()
        if (text.contains("=") && resultStr.contains("=")) {
            return "$text=$resultStr"
        }
        return "$text=$result"
    }

    fun evaluateFull(text: String, useRadians: Boolean = false, xValue: Double? = null): Any? {
        val preprocessed = preprocessExpression(text, xValue != null)
        if (preprocessed.contains("=") || preprocessed.contains("Undefined") || preprocessed.contains("collinear") || preprocessed.contains("No Real Roots") ||
            preprocessed.contains("²") || preprocessed.contains("i") || (preprocessed.contains("x") && xValue == null) || preprocessed.contains("cos(") ||
            preprocessed.contains(")(") || preprocessed.contains("[") || preprocessed.contains("Prime") || preprocessed.contains("True") || preprocessed.contains("False")) {
            return preprocessed
        }
        return evaluatePreprocessed(preprocessed, useRadians, xValue)
    }

    fun preprocessExpression(text: String, isGraph: Boolean = false): String {
        if (text.isBlank()) return ""
        val englishText = StringBuilder()
        for (char in text) {
            val englishChar = gurmukhiToEnglishMap[char] ?: char
            englishText.append(englishChar)
        }
        var expression = englishText.toString().replace("×", "*").replace("÷", "/").replace(" ", "").lowercase()
            .replace("²", "^2").replace("³", "^3")
            .replace("([a-z])(\\d)".toRegex(), "$1^$2") // Support x2 as x^2
            .replace("power\\[([a-z0-9.]+),(\\d+)\\]".toRegex(), "$1^$2") // Support power[x,2]
            .replace("surd\\[(\\d+\\.?\\d*),(\\d+)\\]".toRegex(), "root_$2($1)") // Support surd[6,3]

        // Handle business percentage: A + B% -> A + (A*B/100)
        val businessPercentPlusRegex = "(-?\\d+\\.?\\d*)\\+([-+]?\\d+\\.?\\d*)%".toRegex()
        val businessPercentMinusRegex = "(-?\\d+\\.?\\d*)-([-+]?\\d+\\.?\\d*)%".toRegex()
        var lastExpr = ""
        while (expression != lastExpr) {
            lastExpr = expression
            expression = businessPercentPlusRegex.replace(expression) { match ->
                val a = match.groupValues[1].toDouble()
                val b = match.groupValues[2].toDouble()
                (a + (a * b / 100.0)).toString()
            }
            expression = businessPercentMinusRegex.replace(expression) { match ->
                val a = match.groupValues[1].toDouble()
                val b = match.groupValues[2].toDouble()
                (a - (a * b / 100.0)).toString()
            }
        }

        // Standardize common variables to 'x' using regex to avoid breaking function names (like 'tan' or 'quad')
        if (!expression.contains("dist_rt") && !expression.contains("total_cost")) {
            // Replace t, z, X, y, l, b with x only if they are STANDALONE variables
            // Removed a, b, c from here as they collide with function names (quad, fact, etc.)
            expression = expression.replace("(?<![a-z])[tzXylb](?![a-z])".toRegex(), "x")
        }

        // Coordinate geometry helpers
        val distRegex = "distance\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        distRegex.find(expression)?.let { match ->
            val x1 = match.groupValues[1].toDouble(); val y1 = match.groupValues[2].toDouble()
            val x2 = match.groupValues[3].toDouble(); val y2 = match.groupValues[4].toDouble()
            return sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2)).toString()
        }

        val midRegex = "midpoint\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        midRegex.find(expression)?.let { match ->
            val x1 = match.groupValues[1].toDouble(); val y1 = match.groupValues[2].toDouble()
            val x2 = match.groupValues[3].toDouble(); val y2 = match.groupValues[4].toDouble()
            return "x=${formatIndianStyle((x1 + x2) / 2)}, y=${formatIndianStyle((y1 + y2) / 2)}"
        }

        val centroidRegex = "centroid\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        centroidRegex.find(expression)?.let { match ->
            val x1 = match.groupValues[1].toDouble(); val y1 = match.groupValues[2].toDouble()
            val x2 = match.groupValues[3].toDouble(); val y2 = match.groupValues[4].toDouble()
            val x3 = match.groupValues[5].toDouble(); val y3 = match.groupValues[6].toDouble()
            return "x=${formatIndianStyle((x1 + x2 + x3) / 3)}, y=${formatIndianStyle((y1 + y2 + y3) / 3)}"
        }

        val slopeRegex = "slope\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        slopeRegex.find(expression)?.let { match ->
            val x1 = match.groupValues[1].toDouble(); val y1 = match.groupValues[2].toDouble()
            val x2 = match.groupValues[3].toDouble(); val y2 = match.groupValues[4].toDouble()
            if (abs(x2 - x1) < 1e-10) return "Undefined (Vertical)"
            return ((y2 - y1) / (x2 - x1)).toString()
        }

        val circumRegex = "circumcenter\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        circumRegex.find(expression)?.let { match ->
            val x1 = match.groupValues[1].toDouble(); val y1 = match.groupValues[2].toDouble()
            val x2 = match.groupValues[3].toDouble(); val y2 = match.groupValues[4].toDouble()
            val x3 = match.groupValues[5].toDouble(); val y3 = match.groupValues[6].toDouble()
            val d = 2 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
            if (abs(d) < 1e-10) return "Points are collinear"
            val ux = ((x1.pow(2) + y1.pow(2)) * (y2 - y3) + (x2.pow(2) + y2.pow(2)) * (y3 - y1) + (x3.pow(2) + y3.pow(2)) * (y1 - y2)) / d
            val uy = ((x1.pow(2) + y1.pow(2)) * (x3 - x2) + (x2.pow(2) + y2.pow(2)) * (x1 - x3) + (x3.pow(2) + y3.pow(2)) * (x2 - x1)) / d
            return "x=${formatIndianStyle(ux)}, y=${formatIndianStyle(uy)}"
        }

        // Geometry Phase 2
        val frustumPyrRegex = "frustum_pyramid\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        frustumPyrRegex.find(expression)?.let { match ->
            val a = match.groupValues[1].toDouble(); val b = match.groupValues[2].toDouble(); val h = match.groupValues[3].toDouble()
            return (h * (a * a + b * b + a * b) / 3.0).toString()
        }

        val frustumConeRegex = "frustum_cone\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        frustumConeRegex.find(expression)?.let { match ->
            val r1 = match.groupValues[1].toDouble(); val r2 = match.groupValues[2].toDouble(); val h = match.groupValues[3].toDouble()
            return (PI * h * (r1 * r1 + r2 * r2 + r1 * r2) / 3.0).toString()
        }

        val orthoRegex = "orthocenter\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        orthoRegex.find(expression)?.let { match ->
            val x1 = match.groupValues[1].toDouble(); val y1 = match.groupValues[2].toDouble()
            val x2 = match.groupValues[3].toDouble(); val y2 = match.groupValues[4].toDouble()
            val x3 = match.groupValues[5].toDouble(); val y3 = match.groupValues[6].toDouble()
            val a1 = x3 - x2; val b1 = y3 - y2; val c1 = x1 * (x3 - x2) + y1 * (y3 - y2)
            val a2 = x3 - x1; val b2 = y3 - y1; val c2 = x2 * (x3 - x1) + y2 * (y3 - y1)
            val det = a1 * b2 - a2 * b1
            if (abs(det) < 1e-10) return "Points are collinear"
            return "x=${formatIndianStyle((c1 * b2 - c2 * b1) / det)}, y=${formatIndianStyle((a1 * c2 - a2 * c1) / det)}"
        }

        val eulerRegex = "platonic_euler\\((\\d+),(\\d+),(\\d+)\\)".toRegex()
        eulerRegex.find(expression)?.let { match ->
            val v = match.groupValues[1].toInt(); val e = match.groupValues[2].toInt(); val f = match.groupValues[3].toInt()
            return (v - e + f == 2).toString()
        }

        val lateralConeRegex = "lateral_area_cone\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        lateralConeRegex.find(expression)?.let { match ->
            val r = match.groupValues[1].toDouble(); val l = match.groupValues[2].toDouble()
            return (PI * r * l).toString()
        }

        // Calculus Phase 2
        val integralRegex = "integral\\((.*),(-?\\d+\\.?\\d*|pi|e),(-?\\d+\\.?\\d*|pi|e)\\)".toRegex()
        integralRegex.find(expression)?.let { match ->
            val expr = match.groupValues[1]
            val a = match.groupValues[2].replace("pi", PI.toString()).replace("e", E.toString()).toDoubleOrNull() ?: 0.0
            val b = match.groupValues[3].replace("pi", PI.toString()).replace("e", E.toString()).toDoubleOrNull() ?: 0.0
            return numericalIntegral(expr, a, b).toString()
        }

        val derivRegex = "derivative\\((.*),(-?\\d+\\.?\\d*|pi|e)\\)".toRegex()
        derivRegex.find(expression)?.let { match ->
            val expr = match.groupValues[1]
            val x = match.groupValues[2].replace("pi", PI.toString()).replace("e", E.toString()).toDoubleOrNull() ?: 0.0
            return numericalDerivative(expr, x).toString()
        }

        val newtonRegex = "newton\\((.*),(-?\\d+\\.?\\d*|pi|e)\\)".toRegex()
        newtonRegex.find(expression)?.let { match ->
            val expr = match.groupValues[1]
            val x0 = match.groupValues[2].replace("pi", PI.toString()).replace("e", E.toString()).toDoubleOrNull() ?: 0.0
            val fx0 = evaluateFull(expr, true, x0) as? Double ?: 0.0
            val dfx0 = numericalDerivative(expr, x0)
            if (abs(dfx0) < 1e-12) return "Derivative near zero"
            return "x1=${formatIndianStyle(x0 - fx0 / dfx0)}"
        }

        // expand(k1, k2, ...) -> (x+k1)(x+k2)...
        val expandRegex = "expand\\((.*?)\\)".toRegex()
        expandRegex.find(expression)?.let { match ->
            var inner = match.groupValues[1]
            if (inner.startsWith("foil(") || inner.startsWith("sq_bin_plus(") || inner.startsWith("sq_bin_minus(") || inner.startsWith("diff_sq(") || inner.startsWith("diff_cub(")) {
                return preprocessExpression(inner, isGraph)
            }
            val nums = parseNumberList(inner)
            if (nums.isEmpty()) return "0"
            return expandLinearFactors(nums, isGraph)
        }

        // rule(exponent_rule_mult(a, m, n))
        val ruleRegex = "rule\\((.*?)\\)".toRegex()
        ruleRegex.find(expression)?.let { match ->
            val inner = match.groupValues[1]
            return preprocessExpression(inner, isGraph)
        }

        // result(fractional_exp(a, n, d))
        val resultRegex = "result\\((.*?)\\)".toRegex()
        resultRegex.find(expression)?.let { match ->
            val inner = match.groupValues[1]
            return preprocessExpression(inner, isGraph)
        }

        // Perfect Square Trinomial Identity: (a+b)^2 = a^2 + 2ab + b^2
        if (expression.contains("(a+b)^2=a^2+2ab+b^2")) {
            return if (isGraph) "a^2 + 2*a*b + b^2" else "(a+b)² = a² + 2ab + b²"
        }

        // foil(a, b, c, d)
        val foilRegex = "foil\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        foilRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val bVal = match.groupValues[2]
            val cVal = match.groupValues[3]; val dVal = match.groupValues[4]
            
            if (aVal == "x" || bVal == "x" || cVal == "x" || dVal == "x") {
                // Return symbolic FOIL if possible
                return "(ax+b)(cx+d) expansion"
            }
            
            val a = aVal.toDouble(); val b = bVal.toDouble()
            val c = cVal.toDouble(); val d = dVal.toDouble()
            val ac = a * c; val adbc = a * d + b * c; val bd = b * d
            if (isGraph) return "(${ac})*x^2 + (${adbc})*x + (${bd})"
            return "${formatIndianStyle(ac)}x² + (${formatIndianStyle(adbc)})x + ${formatIndianStyle(bd)}"
        }

        // foil3d(a, b) -> (x+a)(y+b)
        val foil3dRegex = "foil3d\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        foil3dRegex.find(expression)?.let { match ->
            val a = match.groupValues[1].toDouble(); val b = match.groupValues[2].toDouble()
            if (isGraph) return "(x + $a)*(y + $b)"
            return "(x + ${formatIndianStyle(a)})(y + ${formatIndianStyle(b)}) = xy + ${formatIndianStyle(b)}x + ${formatIndianStyle(a)}y + ${formatIndianStyle(a*b)}"
        }

        // diff_sq(a, b)
        val diffSqRegex = "diff_sq\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        diffSqRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val bVal = match.groupValues[2]
            
            if (isGraph) {
                if (aVal == "x") {
                    val b = bVal.toDoubleOrNull() ?: 1.0
                    return "x^2 - ${b * b}"
                }
                if (bVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 1.0
                    return "${a * a} - x^2"
                }
            }
            
            val a = if (aVal == "x") "x" else formatIndianStyle(aVal.toDouble())
            val b = if (bVal == "x") "x" else formatIndianStyle(bVal.toDouble())
            return "($a - $b)($a + $b)"
        }

        // diff_cub(a, b)
        val diffCubRegex = "diff_cub\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        diffCubRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val bVal = match.groupValues[2]
            
            if (isGraph) {
                if (aVal == "x") {
                    val b = bVal.toDoubleOrNull() ?: 1.0
                    return "x^3 - ${b * b * b}"
                }
                if (bVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 1.0
                    return "${a * a * a} - x^3"
                }
            }
            
            val a = if (aVal == "x") "x" else formatIndianStyle(aVal.toDouble())
            val b = if (bVal == "x") "x" else formatIndianStyle(bVal.toDouble())
            val a2 = if (aVal == "x") "x^2" else formatIndianStyle(aVal.toDouble().pow(2))
            val ab = if (aVal == "x" && bVal == "x") "x^2" else if (aVal == "x") "${b}*x" else if (bVal == "x") "${a}*x" else formatIndianStyle(aVal.toDouble() * bVal.toDouble())
            val b2 = if (bVal == "x") "x^2" else formatIndianStyle(bVal.toDouble().pow(2))
            return "($a - $b)($a2 + $ab + $b2)"
        }

        // arith_seq(a, d, n)
        val arithRegex = "arith_seq\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        arithRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val dVal = match.groupValues[2]; val nVal = match.groupValues[3]
            
            if (aVal == "x") {
                val d = dVal.toDoubleOrNull() ?: 1.0; val n = nVal.toDoubleOrNull() ?: 1.0
                return "x + (${n - 1}) * $d"
            }
            if (dVal == "x") {
                val a = aVal.toDoubleOrNull() ?: 0.0; val n = nVal.toDoubleOrNull() ?: 1.0
                return "$a + (x * (${n - 1}))"
            }
            if (nVal == "x") {
                val a = aVal.toDoubleOrNull() ?: 0.0; val d = dVal.toDoubleOrNull() ?: 1.0
                if (isGraph) return "$a + (x - 1) * $d"
                return "a_n = $a + (n - 1) * $d"
            }
            
            val a = aVal.toDouble(); val d = dVal.toDouble(); val n = nVal.toDouble()
            val res = a + (n - 1) * d
            return "a_$n = ${formatIndianStyle(a)} + (${formatIndianStyle(n)} - 1) * ${formatIndianStyle(d)} = ${formatIndianStyle(res)}"
        }

        // fib(n)
        val fibRegex = "fib\\((x|\\d+)\\)".toRegex()
        fibRegex.find(expression)?.let { match ->
            val nVal = match.groupValues[1]
            if (nVal == "x") {
                // Binet's Formula for continuous Fibonacci-like plot
                val phi = (1 + sqrt(5.0)) / 2.0
                val psi = (1 - sqrt(5.0)) / 2.0
                return "( ($phi^x - $psi^x) / sqrt(5) )"
            }
            val n = nVal.toInt()
            return calculateFib(n).toString()
        }

        // cosacosb(A, B) -> cos(A-B) + cos(A+B)
        val cosacosbRegex = "cosacosb\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        cosacosbRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val bVal = match.groupValues[2]
            
            if (isGraph) {
                // If graphing, we can't easily plot 2-var trig as 1D unless one is constant
                if (aVal == "x") {
                    val b = bVal.toDoubleOrNull() ?: 0.0
                    return "cos(x - $b) + cos(x + $b)"
                }
                if (bVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 0.0
                    return "cos($a - x) + cos($a + x)"
                }
                return "0"
            }
            
            val a = if (aVal == "x") "A" else formatIndianStyle(aVal.toDouble())
            val b = if (bVal == "x") "B" else formatIndianStyle(bVal.toDouble())
            
            val ambValText = if (aVal != "x" && bVal != "x") formatIndianStyle(aVal.toDouble() - bVal.toDouble()) else "$a - $b"
            val apbValText = if (aVal != "x" && bVal != "x") formatIndianStyle(aVal.toDouble() + bVal.toDouble()) else "$a + $b"
            
            return "2cos($a)cos($b) = cos($ambValText) + cos($apbValText)"
        }

        // fact(n)
        val factRegex = "fact\\((x|\\d+)\\)".toRegex()
        factRegex.find(expression)?.let { match ->
            val nVal = match.groupValues[1]
            if (nVal == "x") {
                // Use Stirling's approximation for x! when graphing
                // x! ≈ sqrt(2*pi*x) * (x/e)^x
                return "(sqrt(2 * pi * x) * (x / e)^x)"
            }
            val n = nVal.toInt()
            return calculateFactorial(n).toString()
        }

        // comb(n, r)
        val combRegex = "comb\\((x|\\d+),(x|\\d+)\\)".toRegex()
        combRegex.find(expression)?.let { match ->
            val nVal = match.groupValues[1]
            val rVal = match.groupValues[2]
            
            if (nVal == "x" || rVal == "x") {
                // Return formula for graphing using factorial (which uses Stirling)
                val n = if (nVal == "x") "x" else nVal
                val r = if (rVal == "x") "x" else rVal
                val nmr = if (nVal == "x" && rVal == "x") "0" else if (nVal == "x") "(x - $r)" else if (rVal == "x") "($n - x)" else "0"
                return "fact($n) / (fact($r) * fact($nmr))"
            }
            
            val n = nVal.toInt(); val r = rVal.toInt()
            return calculateCombination(n, r).toString()
        }

        // binom_prob(n, k, p)
        val binomRegex = "binom_prob\\((x|\\d+),(x|\\d+),(-?\\d+\\.?\\d*)\\)".toRegex()
        binomRegex.find(expression)?.let { match ->
            val nVal = match.groupValues[1]; val kVal = match.groupValues[2]
            val p = match.groupValues[3].toDouble()
            
            if (nVal == "x" || kVal == "x") {
                // Return formula for graphing using Stirling's approximation
                val n = if (nVal == "x") "x" else nVal
                val k = if (kVal == "x") "x" else kVal
                return "(fact($n) / (fact($k) * fact($n - $k))) * $p^$k * (1 - $p)^($n - $k)"
            }
            
            val n = nVal.toInt(); val k = kVal.toInt()
            val nCr = calculateCombination(n, k).toDouble()
            return (nCr * p.pow(k) * (1 - p).pow(n - k)).toString()
        }

        // completing_sq(a, b, c)
        val compSqRegex = "completing_sq\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        compSqRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val bVal = match.groupValues[2]; val cVal = match.groupValues[3]
            
            if (aVal == "x" || bVal == "x" || cVal == "x") {
                 return "a(x-h)^2 + k form"
            }
            
            val a = aVal.toDouble(); val b = bVal.toDouble(); val c = cVal.toDouble()
            val h = -b / (2 * a); val k = c - (b * b) / (4 * a)
            if (isGraph) return "$a * (x - $h)^2 + $k"
            val hStr = if (h >= 0) "- ${formatIndianStyle(h)}" else "+ ${formatIndianStyle(abs(h))}"
            return "y = ${formatIndianStyle(a)}(x $hStr)² + ${formatIndianStyle(k)}"
        }

        // demoivre(r, theta, n) -> r^n (cos(n*theta) + i sin(n*theta))
        val demoivreRegex = "demoivre\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        demoivreRegex.find(expression)?.let { match ->
            val rVal = match.groupValues[1]; val tVal = match.groupValues[2]; val nVal = match.groupValues[3]
            
            if (isGraph) {
                // Return real part for graphing
                return "($rVal)^($nVal) * cos(($nVal) * ($tVal))"
            }
            
            if (rVal == "x" || tVal == "x" || nVal == "x") return "De Moivre's Theorem Identity"
            
            val r = rVal.toDouble(); val theta = tVal.toDouble(); val n = nVal.toDouble()
            val rn = r.pow(n); val nTheta = n * theta
            val real = rn * cos(Math.toRadians(nTheta))
            val imag = rn * sin(Math.toRadians(nTheta))
            val reStr = formatIndianStyle(real)
            val imStr = formatIndianStyle(abs(imag))
            
            val res = if (abs(imag) < 1e-9) reStr
            else if (real == 0.0) "${if (imag < 0) "-" else ""}${imStr}i"
            else "$reStr ${if (imag < 0) "-" else "+"} ${imStr}i"
            
            return "[${formatIndianStyle(r)}(cos ${formatIndianStyle(theta)} + i sin ${formatIndianStyle(theta)})]^$n = $res"
        }

        // Phase 3: Statistics, Probability, Finance
        val statsRegex = "(mean|geo_mean|har_mean|std_dev|variance)\\((.*?)\\)".toRegex()
        statsRegex.find(expression)?.let { match ->
            val type = match.groupValues[1]
            val nums = parseNumberList(match.groupValues[2])
            if (nums.isEmpty()) return "0"
            val res = when (type) {
                "mean" -> nums.average()
                "geo_mean" -> {
                    val prod = nums.fold(1.0) { acc, d -> acc * d }
                    if (prod < 0 && nums.size % 2 == 0) Double.NaN else prod.pow(1.0 / nums.size)
                }
                "har_mean" -> nums.size / nums.sumOf { 1.0 / it }
                "std_dev" -> {
                    val avg = nums.average()
                    sqrt(nums.sumOf { (it - avg).pow(2) } / nums.size)
                }
                "variance" -> {
                    val avg = nums.average()
                    nums.sumOf { (it - avg).pow(2) } / nums.size
                }
                else -> 0.0
            }
            return res.toString()
        }

        val normalDistRegex = "normal_dist\\((x|-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        normalDistRegex.find(expression)?.let { match ->
            val xVal = match.groupValues[1]
            val m = match.groupValues[2].toDouble(); val s = match.groupValues[3].toDouble()
            if (s == 0.0) return "Undefined (std=0)"
            if (xVal == "x") return "(1.0 / ($s * sqrt(2.0 * pi))) * exp(-0.5 * ((x - $m) / $s)^2)"
            val x = xVal.toDouble()
            return ((1.0 / (s * sqrt(2.0 * PI))) * exp(-0.5 * ((x - m) / s).pow(2))).toString()
        }

        val coinTossRegex = "coin_toss\\((\\d+),(\\d+)\\)".toRegex()
        coinTossRegex.find(expression)?.let { match ->
            val n = match.groupValues[1].toInt(); val k = match.groupValues[2].toInt()
            if (k > n || k < 0) return "0"
            return (calculateCombination(n, k).toDouble() * 0.5.pow(n)).toString()
        }

        val compoundIntRegex = "compound_int\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        compoundIntRegex.find(expression)?.let { match ->
            val p = match.groupValues[1].toDouble(); val r = match.groupValues[2].toDouble()
            val t = match.groupValues[3].toDouble(); val n = match.groupValues[4].toDouble()
            return (p * (1.0 + r / n).pow(n * t)).toString()
        }

        val loanBalRegex = "loan_balance\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        loanBalRegex.find(expression)?.let { match ->
            val p = match.groupValues[1].toDouble(); val r = match.groupValues[2].toDouble()
            val n = match.groupValues[3].toDouble(); val t = match.groupValues[4].toDouble(); val k = match.groupValues[5].toDouble()
            if (r == 0.0) return (p * (1.0 - k / (n * t))).toString()
            val rn = r / n
            val num = (1.0 + rn).pow(n * t) - (1.0 + rn).pow(k)
            val den = (1.0 + rn).pow(n * t) - 1.0
            return (p * (num / den)).toString()
        }

        val discountRegex = "discount\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        discountRegex.find(expression)?.let { match ->
            val price = match.groupValues[1].toDouble(); val rate = match.groupValues[2].toDouble()
            return (price * (1.0 - rate / 100.0)).toString()
        }

        val growthRegex = "growth\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        growthRegex.find(expression)?.let { match ->
            val p = match.groupValues[1].toDouble(); val r = match.groupValues[2].toDouble(); val t = match.groupValues[3].toDouble()
            return (p * exp(r * t)).toString()
        }

        // Phase 4: Higher Math & Conversions
        val binToDecRegex = "bin_to_dec\\(([01]+)\\)".toRegex()
        binToDecRegex.find(expression)?.let { match ->
            return binaryToDecimal(match.groupValues[1])
        }

        val decToBinRegex = "dec_to_bin\\((-?\\d+\\.?\\d*)\\)".toRegex()
        decToBinRegex.find(expression)?.let { match ->
            return decimalToBinary(match.groupValues[1].toDouble())
        }

        val det2Regex = "det2\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        det2Regex.find(expression)?.let { match ->
            val a = match.groupValues[1].toDouble(); val b = match.groupValues[2].toDouble()
            val c = match.groupValues[3].toDouble(); val d = match.groupValues[4].toDouble()
            return (a * d - b * c).toString()
        }

        val inv2Regex = "inv2\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        inv2Regex.find(expression)?.let { match ->
            val a = match.groupValues[1].toDouble(); val b = match.groupValues[2].toDouble()
            val c = match.groupValues[3].toDouble(); val d = match.groupValues[4].toDouble()
            val det = a * d - b * c
            if (abs(det) < 1e-12) return "Matrix is Singular"
            return "[[${formatIndianStyle(d/det)}, ${formatIndianStyle(-b/det)}], [${formatIndianStyle(-c/det)}, ${formatIndianStyle(a/det)}]]"
        }

        val ohmVRegex = "ohm_v\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        ohmVRegex.find(expression)?.let { match ->
            val i = match.groupValues[1].toDouble(); val r = match.groupValues[2].toDouble()
            return (i * r).toString()
        }

        val ohmIRegex = "ohm_i\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        ohmIRegex.find(expression)?.let { match ->
            val v = match.groupValues[1].toDouble(); val r = match.groupValues[2].toDouble()
            if (r == 0.0) return "Infinity"
            return (v / r).toString()
        }

        val ohmRRegex = "ohm_r\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        ohmRRegex.find(expression)?.let { match ->
            val v = match.groupValues[1].toDouble(); val i = match.groupValues[2].toDouble()
            if (i == 0.0) return "Infinity"
            return (v / i).toString()
        }

        val resParallelRegex = "res_parallel\\((.*?)\\)".toRegex()
        resParallelRegex.find(expression)?.let { match ->
            val nums = parseNumberList(match.groupValues[1])
            if (nums.isEmpty()) return "0"
            val invSum = nums.sumOf { 1.0 / it }
            return (1.0 / invSum).toString()
        }

        val schwRegex = "schwarzschild\\((-?\\d+\\.?\\d*)\\)".toRegex()
        schwRegex.find(expression)?.let { match ->
            val m = match.groupValues[1].toDouble()
            val g = 6.674e-11
            val cVal = 299792458.0
            return (2.0 * g * m / (cVal * cVal)).toString()
        }

        val timeDilRegex = "time_dilation\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        timeDilRegex.find(expression)?.let { match ->
            val t = match.groupValues[1].toDouble(); val v = match.groupValues[2].toDouble()
            val cVal = 299792458.0
            if (v >= cVal) return "Infinity"
            return (t / sqrt(1.0 - (v * v) / (cVal * cVal))).toString()
        }

        // Phase 5: New Formulas
        val totalCostRegex = "total_cost\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        totalCostRegex.find(expression)?.let { match ->
            val uVal = match.groupValues[1]; val pVal = match.groupValues[2]
            if (uVal == "x") {
                val p = pVal.toDoubleOrNull() ?: 1.0
                return "(x * $p)"
            }
            if (pVal == "x") {
                val u = uVal.toDoubleOrNull() ?: 1.0
                return "($u * x)"
            }
            val units = uVal.toDouble(); val price = pVal.toDouble()
            return (units * price).toString()
        }

        val distRTRegex = "dist_rt\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        distRTRegex.find(expression)?.let { match ->
            val rVal = match.groupValues[1]; val tVal = match.groupValues[2]
            if (rVal == "x") {
                val t = tVal.toDoubleOrNull() ?: 1.0
                return "(x * $t)"
            }
            if (tVal == "x") {
                val r = rVal.toDoubleOrNull() ?: 1.0
                return "($r * x)"
            }
            val rate = rVal.toDouble(); val time = tVal.toDouble()
            return (rate * time).toString()
        }

        val sqBinPlusRegex = "sq_bin_plus\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        sqBinPlusRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]
            val bVal = match.groupValues[2]
            val p2 = if (isGraph) "^2" else "²"
            
            if (aVal == "x") {
                val b = bVal.toDoubleOrNull() ?: 1.0
                val b2 = b * b
                val ab2 = 2 * b
                if (isGraph) return "x^2 + (${ab2})*x + (${b2})"
                return "(x + ${formatIndianStyle(b)})² = x² + ${formatIndianStyle(ab2)}x + ${formatIndianStyle(b2)}"
            }
            if (bVal == "x") {
                val a = aVal.toDoubleOrNull() ?: 1.0
                val a2 = a * a
                val ab2 = 2 * a
                if (isGraph) return "(${a2}) + (${ab2})*x + x^2"
                return "(${formatIndianStyle(a)} + x)² = ${formatIndianStyle(a2)} + ${formatIndianStyle(ab2)}x + x²"
            }
            
            val a = aVal.toDouble(); val b = bVal.toDouble()
            val a2 = a * a; val ab2 = 2 * a * b; val b2 = b * b
            if (isGraph) return "(${a2}) + (${ab2}) + (${b2})"
            return "(${formatIndianStyle(a)} + ${formatIndianStyle(b)})² = ${formatIndianStyle(a2)} + ${formatIndianStyle(ab2)} + ${formatIndianStyle(b2)}"
        }

        val sqBinMinusRegex = "sq_bin_minus\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        sqBinMinusRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]
            val bVal = match.groupValues[2]
            
            if (aVal == "x") {
                val b = bVal.toDoubleOrNull() ?: 1.0
                val b2 = b * b
                val ab2 = 2 * b
                if (isGraph) return "x^2 - (${ab2})*x + (${b2})"
                return "(x - ${formatIndianStyle(b)})² = x² - ${formatIndianStyle(ab2)}x + ${formatIndianStyle(b2)}"
            }
            if (bVal == "x") {
                val a = aVal.toDoubleOrNull() ?: 1.0
                val a2 = a * a
                val ab2 = 2 * a
                if (isGraph) return "(${a2}) - (${ab2})*x + x^2"
                return "(${formatIndianStyle(a)} - x)² = ${formatIndianStyle(a2)} - ${formatIndianStyle(ab2)}x + x²"
            }
            
            val a = aVal.toDouble(); val b = bVal.toDouble()
            val a2 = a * a; val ab2 = 2 * a * b; val b2 = b * b
            if (isGraph) return "(${a2}) - (${ab2}) + (${b2})"
            return "(${formatIndianStyle(a)} - ${formatIndianStyle(b)})² = ${formatIndianStyle(a2)} - ${formatIndianStyle(ab2)} + ${formatIndianStyle(b2)}"
        }

        val isPrimeRegex = "is_prime\\((x|\\d+)\\)".toRegex()
        isPrimeRegex.find(expression)?.let { match ->
            val nVal = match.groupValues[1]
            if (nVal == "x") {
                 // For graphing, return 1 if prime, 0 if not (discrete-like plot)
                 return "solve(is_prime(x))" 
            }
            val n = nVal.toInt()
            return if (isPrime(n)) "Prime" else "Not Prime"
        }

        val poissonRegex = "poisson\\((x|\\d+),(-?\\d+\\.?\\d*)\\)".toRegex()
        poissonRegex.find(expression)?.let { match ->
            val kVal = match.groupValues[1]
            val l = match.groupValues[2].toDouble()
            if (kVal == "x") return "(exp(-$l) * $l^x) / fact(x)"
            val k = kVal.toInt()
            return (exp(-l) * l.pow(k.toDouble()) / calculateFactorial(k)).toString()
        }

        val simpleIntRegex = "simple_int\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        simpleIntRegex.find(expression)?.let { match ->
            val p = match.groupValues[1].toDouble(); val r = match.groupValues[2].toDouble(); val t = match.groupValues[3].toDouble()
            return (p * r * t / 100.0).toString()
        }

        val zScoreRegex = "z_score\\((x|-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        zScoreRegex.find(expression)?.let { match ->
            val xVal = match.groupValues[1]
            val m = match.groupValues[2].toDouble(); val s = match.groupValues[3].toDouble()
            if (s == 0.0) return "Undefined (std=0)"
            if (xVal == "x") return "(x - $m) / $s"
            val x = xVal.toDouble()
            return ((x - m) / s).toString()
        }

        val rmsRegex = "rms\\((.*?)\\)".toRegex()
        rmsRegex.find(expression)?.let { match ->
            val nums = parseNumberList(match.groupValues[1])
            return calculateRMS(nums).toString()
        }

        val spRegex = "selling_price\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        spRegex.find(expression)?.let { match ->
            val cp = match.groupValues[1].toDouble(); val pp = match.groupValues[2].toDouble()
            return (cp * (1.0 + pp / 100.0)).toString()
        }

        val c2fRegex = "c_to_f\\((-?\\d+\\.?\\d*)\\)".toRegex()
        c2fRegex.find(expression)?.let { match ->
            val c = match.groupValues[1].toDouble()
            return (c * 9.0 / 5.0 + 32.0).toString()
        }

        val f2cRegex = "f_to_c\\((-?\\d+\\.?\\d*)\\)".toRegex()
        f2cRegex.find(expression)?.let { match ->
            val f = match.groupValues[1].toDouble()
            return ((f - 32.0) * 5.0 / 9.0).toString()
        }

        // pyth_triple(a, b, c)
        val pythRegex = "pyth_triple\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        pythRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val bVal = match.groupValues[2]; val cVal = match.groupValues[3]
            
            if (isGraph) {
                if (cVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 3.0; val b = bVal.toDoubleOrNull() ?: 4.0
                    return "sqrt($a^2 + $b^2)"
                }
                return "0"
            }
            
            if (aVal == "x" || bVal == "x" || cVal == "x") return "a^2 + b^2 = c^2 Check"
            
            val a = aVal.toDouble(); val b = bVal.toDouble(); val c = cVal.toDouble()
            val isTriple = abs(a * a + b * b - c * c) < 1e-9
            return if (isTriple) "True" else "False"
        }

        val z2pRegex = "z_to_p\\((-?\\d+\\.?\\d*)\\)".toRegex()
        z2pRegex.find(expression)?.let { match ->
            val z = match.groupValues[1].toDouble()
            return zToP(z).toString()
        }

        // Advanced Physics & Quantum
        val deBroglieRegex = "de_broglie\\((-?\\d+\\.?\\d*(?:e[+-]?\\d+)?),(x|-?\\d+\\.?\\d*(?:e[+-]?\\d+)?)\\)".toRegex()
        deBroglieRegex.find(expression)?.let { match ->
            val m = match.groupValues[1].toDouble(); val vVal = match.groupValues[2]
            if (vVal == "x") return "$H_PLANCK / ($m * x)"
            val v = vVal.toDouble()
            return (H_PLANCK / (m * v)).toString()
        }

        val photonEnergyRegex = "photon_energy\\((-?\\d+\\.?\\d*(?:e[+-]?\\d+)?)\\)".toRegex()
        photonEnergyRegex.find(expression)?.let { match ->
            val f = match.groupValues[1].toDouble()
            return (H_PLANCK * f).toString()
        }

        val uncertaintyPRegex = "uncertainty_p\\((-?\\d+\\.?\\d*(?:e[+-]?\\d+)?)\\)".toRegex()
        uncertaintyPRegex.find(expression)?.let { match ->
            val dx = match.groupValues[1].toDouble()
            return (H_BAR / (2.0 * dx)).toString()
        }

        val energyBoxRegex = "energy_box\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*(?:e[+-]?\\d+)?),(-?\\d+\\.?\\d*(?:e[+-]?\\d+)?)\\)".toRegex()
        energyBoxRegex.find(expression)?.let { match ->
            val n = match.groupValues[1].toDouble(); val L = match.groupValues[2].toDouble(); val m = match.groupValues[3].toDouble()
            return (n * n * H_PLANCK * H_PLANCK / (8.0 * m * L * L)).toString()
        }

        val entropyBoltRegex = "entropy_boltzmann\\((-?\\d+\\.?\\d*(?:e[+-]?\\d+)?)\\)".toRegex()
        entropyBoltRegex.find(expression)?.let { match ->
            val w = match.groupValues[1].toDouble()
            return (K_BOLTZMANN * ln(w)).toString()
        }

        val boltFactorRegex = "boltzmann_factor\\((-?\\d+\\.?\\d*(?:e[+-]?\\d+)?),(-?\\d+\\.?\\d*(?:e[+-]?\\d+)?)\\)".toRegex()
        boltFactorRegex.find(expression)?.let { match ->
            val E_val = match.groupValues[1].toDouble(); val T = match.groupValues[2].toDouble()
            return exp(-E_val / (K_BOLTZMANN * T)).toString()
        }

        val gravDilRegex = "grav_dilation\\((-?\\d+\\.?\\d*(?:e[+-]?\\d+)?),(-?\\d+\\.?\\d*(?:e[+-]?\\d+)?),(-?\\d+\\.?\\d*(?:e[+-]?\\d+)?)\\)".toRegex()
        gravDilRegex.find(expression)?.let { match ->
            val t = match.groupValues[1].toDouble(); val M = match.groupValues[2].toDouble(); val r = match.groupValues[3].toDouble()
            return (t * sqrt(1.0 - (2.0 * G_GRAV * M) / (r * C_LIGHT * C_LIGHT))).toString()
        }

        val lorentzRegex = "lorentz\\((x|-?\\d+\\.?\\d*(?:e[+-]?\\d+)?)\\)".toRegex()
        lorentzRegex.find(expression)?.let { match ->
            val vVal = match.groupValues[1]
            if (vVal == "x") return "1.0 / sqrt(1.0 - (x * x) / (${C_LIGHT * C_LIGHT}))"
            val v = vVal.toDouble()
            if (v >= C_LIGHT) return "Infinity"
            return (1.0 / sqrt(1.0 - (v * v) / (C_LIGHT * C_LIGHT))).toString()
        }

        // Education Helpers
        val slopeIntRegex = "slope_intercept\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        slopeIntRegex.find(expression)?.let { match ->
            val m = match.groupValues[1].toDouble(); val c = match.groupValues[2].toDouble()
            return "y = (${formatIndianStyle(m)})x + (${formatIndianStyle(c)})"
        }

        val expMultRegex = "exponent_rule_mult\\((.*?)\\)".toRegex()
        expMultRegex.find(expression)?.let { match ->
            val args = match.groupValues[1].split(",")
            if (args.size == 3) {
                val aVal = args[0].trim()
                val mVal = args[1].trim()
                val nVal = args[2].trim()
                
                if (aVal == "x") {
                    val m = mVal.toDoubleOrNull() ?: 1.0
                    val n = nVal.toDoubleOrNull() ?: 1.0
                    if (isGraph) return "x^(${m + n})"
                    return "x^${formatIndianStyle(m)} * x^${formatIndianStyle(n)} = x^${formatIndianStyle(m+n)}"
                }
                if (mVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 2.0
                    val n = nVal.toDoubleOrNull() ?: 1.0
                    if (isGraph) return "(${a})^(x + ${n})"
                    return "${formatIndianStyle(a)}^x * ${formatIndianStyle(a)}^${formatIndianStyle(n)} = ${formatIndianStyle(a)}^(x+${formatIndianStyle(n)})"
                }
                if (nVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 2.0
                    val m = mVal.toDoubleOrNull() ?: 1.0
                    if (isGraph) return "(${a})^(${m} + x)"
                    return "${formatIndianStyle(a)}^${formatIndianStyle(m)} * ${formatIndianStyle(a)}^x = ${formatIndianStyle(a)}^(${formatIndianStyle(m)}+x)"
                }
                
                val a = aVal.toDoubleOrNull() ?: 2.0
                val m = mVal.toDoubleOrNull() ?: 1.0
                val n = nVal.toDoubleOrNull() ?: 1.0
                val res = a.pow(m + n)
                if (isGraph) return res.toString()
                return "${formatIndianStyle(a)}^${formatIndianStyle(m)} * ${formatIndianStyle(a)}^${formatIndianStyle(n)} = ${formatIndianStyle(a)}^(${formatIndianStyle(m+n)}) = ${formatIndianStyle(res)}"
            }
            "0"
        }

        val triOppRegex = "right_tri_find_opp\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        triOppRegex.find(expression)?.let { match ->
            val angleVal = match.groupValues[1]; val hypVal = match.groupValues[2]
            if (isGraph) {
                if (angleVal == "x") {
                    val hyp = hypVal.toDoubleOrNull() ?: 1.0
                    return "$hyp * sin(x * pi / 180)"
                }
                if (hypVal == "x") {
                    val angle = angleVal.toDoubleOrNull() ?: 30.0
                    return "x * sin($angle * pi / 180)"
                }
            }
            if (angleVal == "x" || hypVal == "x") return "Opposite = Hypotenuse * sin(Angle)"
            val angle = angleVal.toDouble(); val hyp = hypVal.toDouble()
            return (hyp * sin(Math.toRadians(angle))).toString()
        }

        val triAdjRegex = "right_tri_find_adj\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        triAdjRegex.find(expression)?.let { match ->
            val angleVal = match.groupValues[1]; val hypVal = match.groupValues[2]
            if (isGraph) {
                if (angleVal == "x") {
                    val hyp = hypVal.toDoubleOrNull() ?: 1.0
                    return "$hyp * cos(x * pi / 180)"
                }
                if (hypVal == "x") {
                    val angle = angleVal.toDoubleOrNull() ?: 30.0
                    return "x * cos($angle * pi / 180)"
                }
            }
            if (angleVal == "x" || hypVal == "x") return "Adjacent = Hypotenuse * cos(Angle)"
            val angle = angleVal.toDouble(); val hyp = hypVal.toDouble()
            return (hyp * cos(Math.toRadians(angle))).toString()
        }

        // Double & Triple Angle Identities
        val sin2aRegex = "sin2a\\((x|-?\\d+\\.?\\d*)\\)".toRegex()
        sin2aRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]
            if (isGraph) return "2 * sin($aVal) * cos($aVal)"
            if (aVal == "x") return "sin(2A) = 2sin(A)cos(A)"
            val a = aVal.toDouble()
            return (2.0 * sin(Math.toRadians(a)) * cos(Math.toRadians(a))).toString()
        }
        
        val cos2aRegex = "cos2a\\((x|-?\\d+\\.?\\d*)\\)".toRegex()
        cos2aRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]
            if (isGraph) return "cos($aVal)^2 - sin($aVal)^2"
            if (aVal == "x") return "cos(2A) = cos(A)^2 - sin(A)^2"
            val a = aVal.toDouble()
            return (cos(Math.toRadians(a)).pow(2) - sin(Math.toRadians(a)).pow(2)).toString()
        }

        val tan2aRegex = "tan2a\\((x|-?\\d+\\.?\\d*)\\)".toRegex()
        tan2aRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]
            if (isGraph) return "2 * tan($aVal) / (1 - tan($aVal)^2)"
            if (aVal == "x") return "tan(2A) = 2tan(A)/(1-tan(A)^2)"
            val a = aVal.toDouble()
            val t = tan(Math.toRadians(a))
            return (2.0 * t / (1.0 - t * t)).toString()
        }

        val sin3aRegex = "sin3a\\((x|-?\\d+\\.?\\d*)\\)".toRegex()
        sin3aRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]
            if (isGraph) return "3 * sin($aVal) - 4 * sin($aVal)^3"
            if (aVal == "x") return "sin(3A) = 3sin(A) - 4sin(A)^3"
            val a = aVal.toDouble()
            val s = sin(Math.toRadians(a))
            return (3.0 * s - 4.0 * s * s * s).toString()
        }

        // Half Angle Identities
        val halfSinRegex = "half_sin\\((x|-?\\d+\\.?\\d*)\\)".toRegex()
        halfSinRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]
            if (isGraph) return "sqrt((1 - cos($aVal)) / 2)"
            if (aVal == "x") return "sin(A/2) = sqrt((1 - cos(A)) / 2)"
            val a = aVal.toDouble()
            return sqrt((1.0 - cos(Math.toRadians(a))) / 2.0).toString()
        }

        val halfCosRegex = "half_cos\\((x|-?\\d+\\.?\\d*)\\)".toRegex()
        halfCosRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]
            if (isGraph) return "sqrt((1 + cos($aVal)) / 2)"
            if (aVal == "x") return "cos(A/2) = sqrt((1 + cos(A)) / 2)"
            val a = aVal.toDouble()
            return sqrt((1.0 + cos(Math.toRadians(a))) / 2.0).toString()
        }

        // Law of Sines (Ratio): a/sin(A) = b/sin(B)
        val sineRuleRegex = "sine_rule\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        sineRuleRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val AVal = match.groupValues[2]
            val bVal = match.groupValues[3]; val BVal = match.groupValues[4]
            
            if (isGraph) return expression
            
            // Solver logic for one unknown 'x'
            return when {
                aVal == "x" -> {
                    val a_deg = AVal.toDouble(); val b = bVal.toDouble(); val b_deg = BVal.toDouble()
                    (b * sin(Math.toRadians(a_deg)) / sin(Math.toRadians(b_deg))).toString()
                }
                AVal == "x" -> {
                    val a = aVal.toDouble(); val b = bVal.toDouble(); val b_deg = BVal.toDouble()
                    Math.toDegrees(asin(a * sin(Math.toRadians(b_deg)) / b)).toString()
                }
                bVal == "x" -> {
                    val a = aVal.toDouble(); val a_deg = AVal.toDouble(); val b_deg = BVal.toDouble()
                    (a * sin(Math.toRadians(b_deg)) / sin(Math.toRadians(a_deg))).toString()
                }
                BVal == "x" -> {
                    val a = aVal.toDouble(); val a_deg = AVal.toDouble(); val b = bVal.toDouble()
                    Math.toDegrees(asin(b * sin(Math.toRadians(a_deg)) / a)).toString()
                }
                else -> "a/sin(A) = b/sin(B)"
            }
        }

        // Law of Cosines: c^2 = a^2 + b^2 - 2ab*cos(C)
        val cosRuleRegex = "cos_rule\\((x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*),(x|-?\\d+\\.?\\d*)\\)".toRegex()
        cosRuleRegex.find(expression)?.let { match ->
            val aVal = match.groupValues[1]; val bVal = match.groupValues[2]
            val cVal = match.groupValues[3]; val CDegVal = match.groupValues[4]
            
            if (isGraph) return expression
            
            return when {
                cVal == "x" -> {
                    val a = aVal.toDouble(); val b = bVal.toDouble(); val C = CDegVal.toDouble()
                    sqrt(a * a + b * b - 2 * a * b * cos(Math.toRadians(C))).toString()
                }
                CDegVal == "x" -> {
                    val a = aVal.toDouble(); val b = bVal.toDouble(); val c = cVal.toDouble()
                    Math.toDegrees(acos((a * a + b * b - c * c) / (2 * a * b))).toString()
                }
                aVal == "x" || bVal == "x" -> "Solve for side c or angle C"
                else -> "c² = a² + b² - 2ab*cos(C)"
            }
        }

        // College Physics & Chemistry
        val newtonFRegex = "newton_f\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        newtonFRegex.find(expression)?.let { match ->
            val m = match.groupValues[1].toDouble(); val a = match.groupValues[2].toDouble()
            return (m * a).toString()
        }

        val gasPRegex = "gas_p\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        gasPRegex.find(expression)?.let { match ->
            val v = match.groupValues[1].toDouble(); val n = match.groupValues[2].toDouble(); val t = match.groupValues[3].toDouble()
            return (n * R_GAS * t / v).toString()
        }

        val gasVRegex = "gas_v\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        gasVRegex.find(expression)?.let { match ->
            val p = match.groupValues[1].toDouble(); val n = match.groupValues[2].toDouble(); val t = match.groupValues[3].toDouble()
            return (n * R_GAS * t / p).toString()
        }

        val gibbsRegex = "gibbs\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        gibbsRegex.find(expression)?.let { match ->
            val dh = match.groupValues[1].toDouble(); val t = match.groupValues[2].toDouble(); val ds = match.groupValues[3].toDouble()
            return (dh - t * ds).toString()
        }

        val nernstRegex = "nernst\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        nernstRegex.find(expression)?.let { match ->
            val e0 = match.groupValues[1].toDouble(); val n = match.groupValues[2].toDouble()
            val q = match.groupValues[3].toDouble(); val t = match.groupValues[4].toDouble()
            return (e0 - (R_GAS * t / (n * F_FARADAY)) * ln(q)).toString()
        }

        val arrheniusRegex = "arrhenius\\((-?\\d+\\.?\\d*(?:e[+-]?\\d+)?),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        arrheniusRegex.find(expression)?.let { match ->
            val a = match.groupValues[1].toDouble(); val ea = match.groupValues[2].toDouble(); val t = match.groupValues[3].toDouble()
            return (a * exp(-ea / (R_GAS * t))).toString()
        }

        val fracExpRegex = "fractional_exp\\((.*?)\\)".toRegex()
        fracExpRegex.find(expression)?.let { match ->
            val args = match.groupValues[1].split(",")
            if (args.size == 3) {
                val aVal = args[0].trim()
                val nVal = args[1].trim()
                val dVal = args[2].trim()
                
                if (aVal == "x") {
                    val n = nVal.toDoubleOrNull() ?: 1.0
                    val d = dVal.toDoubleOrNull() ?: 2.0
                    if (isGraph) return "x^($n/$d)"
                    return "x^(${formatIndianStyle(n)}/${formatIndianStyle(d)}) = root_${formatIndianStyle(d)}(x^${formatIndianStyle(n)})"
                }
                if (nVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 2.0
                    val d = dVal.toDoubleOrNull() ?: 2.0
                    if (isGraph) return "($a)^(x/$d)"
                    return "${formatIndianStyle(a)}^(x/${formatIndianStyle(d)}) = root_${formatIndianStyle(d)}(${formatIndianStyle(a)}^x)"
                }
                if (dVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 2.0
                    val n = nVal.toDoubleOrNull() ?: 1.0
                    if (isGraph) return "($a)^($n/x)"
                    return "${formatIndianStyle(a)}^(${formatIndianStyle(n)}/x) = root_x(${formatIndianStyle(a)}^${formatIndianStyle(n)})"
                }
                
                val a = aVal.toDoubleOrNull() ?: 8.0
                val n = nVal.toDoubleOrNull() ?: 1.0
                val d = dVal.toDoubleOrNull() ?: 3.0
                val res = a.pow(n / d)
                if (isGraph) return res.toString()
                return "${formatIndianStyle(a)}^(${formatIndianStyle(n)}/${formatIndianStyle(d)}) = root_${formatIndianStyle(d)}(${formatIndianStyle(a)}^${formatIndianStyle(n)}) = ${formatIndianStyle(res)}"
            }
            "0"
        }

        // Strip common labels if present as prefix
        val labels = listOf("area(", "perimeter(", "circumference(", "diameter(", "diagonal(", "volume(", "surfacearea(", "lsa(", "tsa(", "csa(", "basearea(", "rectangular_prism(", "triangular_prism(", "pentagonal_prism(", "hexagonal_prism(", "ellipsoid(", "tangential_quad(", "square_pyramid(", "triangular_pyramid(", "pentagonal_pyramid(", "hexagonal_pyramid(", "distance(", "midpoint(", "centroid(", "circumcenter(", "slope(", "circleeq(", "c(", "c²(", "ratio(", "t(", "v(", "dv(", "rs(", "f(", "isp(", "thrust(", "t'(", "voltage(", "current(", "resistance(", "power(", "series(", "parallel(", "charge(", "energy(", "centralangle(", "arclength(", "sin(2a)", "cos(2a)", "tan(2a)", "sin(3a)", "cos(3a)", "tan(3a)", "sin(a/2)", "cos(a/2)", "tan(a/2)", "radianstodegrees(", "degreestoradians(", "asymptotes(", "hypotenuse(", "base(", "perpendicular(", "righttriangle(", "intersection(", "parallelogram(", "rhombus(", "kite(", "hexagon(", "octagon(", "x-intercept(", "y-intercept(", "interceptform(", "point-slope/gradient(", "vertex(", "focus(", "directrix(", "tangent(", "vertexform(", "slantheight(", "axisofsymmetry(", "rotation90(", "rotation180(", "rotation270(", "equilateral_tri(", "isosceles_tri(", "de_broglie(", "photon_energy(", "uncertainty_p(", "energy_box(", "entropy_boltzmann(", "boltzmann_factor(", "grav_dilation(", "lorentz(", "newton_f(", "gas_p(", "gas_v(", "gibbs(", "nernst(", "arrhenius(", "frustum_pyramid(", "frustum_cone(", "orthocenter(", "platonic_euler(", "lateral_area_cone(", "integral(", "derivative(", "newton(", "completing_sq(", "mean(", "geo_mean(", "har_mean(", "std_dev(", "variance(", "normal_dist(", "coin_toss(", "compound_int(", "loan_balance(", "discount(", "growth(", "bin_to_dec(", "dec_to_bin(", "det2(", "inv2(", "ohm_v(", "ohm_i(", "ohm_r(", "res_parallel(", "schwarzschild(", "time_dilation(", "total_cost(", "dist_rt(", "is_prime(", "poisson(", "simple_int(", "z_score(", "rms(", "selling_price(", "c_to_f(", "f_to_c(", "pyth_triple(", "z_to_p(", "slope_intercept(", "right_tri_find_opp(", "right_tri_find_adj(", "factor(", "rule(", "result(", "termn(", "fib(", "fact(", "comb(", "binom_prob(", "vertexForm(", "check(", "producttosum(", "powcomplex(", "opposite(", "adjacent(", "doubleAngle(", "tripleAngle(", "halfAngle(", "ratio(", "sine_rule(", "cos_rule(", "SineRule(", "CosineRule(", "3dplot(", "3dgraph(", "foil3d(", "expand(", "solve(", "result(" )
        for (l in labels) {
            if (expression.startsWith(l) && expression.endsWith(")")) {
                expression = expression.substring(l.length, expression.length - 1)
                break
            }
        }

        // For graphing, treat common geometry variables as 'x'
        // This is now mostly handled by the standardized regex above.
        // We only need to handle multi-character geometry variables here.
        if (isGraph) {
            expression = expression
                .replace("θ", "x")
                .replace("base", "x")
                .replace("perpendicular", "x")
                .replace("hypotenuse", "x")
                .replace("slant_h", "x")
        }

        // Handle equations like "x = (x-2)*(x-4)/4"
        if (expression.contains("=") && !expression.contains("quad") && !expression.contains("solve")) {
            val sides = expression.split("=")
            if (sides.size == 2 && sides[0].isNotEmpty() && sides[1].isNotEmpty()) {
                if (isGraph) {
                    // For graphing, we want the function part
                    expression = if (sides[0] == "y" || sides[0] == "x") sides[1] else "(${sides[0]})-(${sides[1]})"
                } else {
                    expression = "solve(${sides[0]}-(${sides[1]}))"
                }
            }
        }

        // Handle "quad(a, b, c)" syntax for solving ax^2 + bx + c = 0
        val quadRegex = "quad\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        val quadMatch = quadRegex.find(expression)
        if (quadMatch != null) {
            if (isGraph) return expression
            val a = quadMatch.groupValues[1].toDouble()
            val b = quadMatch.groupValues[2].toDouble()
            val c = quadMatch.groupValues[3].toDouble()
            val disc = b * b - 4 * a * c
            return if (disc >= 0) {
                val x1 = (-b + sqrt(disc)) / (2 * a)
                val x2 = (-b - sqrt(disc)) / (2 * a)
                "x1=${formatIndianStyle(x1)}, x2=${formatIndianStyle(x2)}"
            } else {
                val re = -b / (2 * a)
                val im = sqrt(-disc) / (2 * a)
                val c1 = Complex(re, im)
                val c2 = Complex(re, -im)
                "x1=$c1, x2=$c2"
            }
        }

        // Handle "cubic(a, b, c, d)" syntax for solving ax^3 + bx^2 + cx + d = 0
        val cubicRegex = "cubic\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        val cubicMatch = cubicRegex.find(expression)
        if (cubicMatch != null) {
            if (isGraph) return expression
            val a = cubicMatch.groupValues[1].toDouble()
            val b = cubicMatch.groupValues[2].toDouble()
            val c = cubicMatch.groupValues[3].toDouble()
            val d = cubicMatch.groupValues[4].toDouble()

            val roots = solveCubic(a, b, c, d)
            return if (roots.isEmpty()) "No Real Roots"
            else roots.mapIndexed { i, r -> "x${i+1}=${r}" }.joinToString(", ")
        }

        // Handle "quartic(a, b, c, d, e)" syntax for solving ax^4 + bx^3 + cx^2 + dx + e = 0
        val quarticRegex = "quartic\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        val quarticMatch = quarticRegex.find(expression)
        if (quarticMatch != null) {
            if (isGraph) return expression
            val a = quarticMatch.groupValues[1].toDouble()
            val b = quarticMatch.groupValues[2].toDouble()
            val c = quarticMatch.groupValues[3].toDouble()
            val d = quarticMatch.groupValues[4].toDouble()
            val e = quarticMatch.groupValues[5].toDouble()

            val roots = solveQuartic(a, b, c, d, e)
            return if (roots.isEmpty()) "No Real Roots"
            else roots.mapIndexed { i, r -> "x${i+1}=${r}" }.joinToString(", ")
        }

        // Handle "quintic(a, b, c, d, e, f)" syntax for solving ax^5 + bx^4 + cx^3 + dx^2 + ex + f = 0
        val quinticRegex = "quintic\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        val quinticMatch = quinticRegex.find(expression)
        if (quinticMatch != null) {
            if (isGraph) return expression
            val coeffs = (1..6).map { quinticMatch.groupValues[it].toDouble() }
            val roots = solveQuinticNumerical(coeffs)
            return if (roots.isEmpty()) "No Real Roots"
            else roots.mapIndexed { i, r -> "x${i+1}=${r}" }.joinToString(", ")
        }

        // Now safe to strip commas (thousand separators) for normal parsing, but avoid function arguments
        // Only strip comma if it's between digits (e.g. 1,000)
        expression = expression.replace("(\\d),(\\d{3})".toRegex(), "$1$2")

        // Handle "2m round 10 hole margin 3 inch" syntax
        val holeRegex = "(\\d+\\.?\\d*)m?round(\\d+)hole(?:margin(\\d+\\.?\\d*)(inch|m)?)?".toRegex()
        val holeMatch = holeRegex.find(expression)
        if (holeMatch != null) {
            if (isGraph) return expression
            val diameter = holeMatch.groupValues[1].toDouble()
            val sides = holeMatch.groupValues[2].toInt().coerceAtLeast(1)
            val angle = 180.0 / sides
            return "${diameter}*sin($angle)"
        }

        // Handle "10m pivot 45" syntax
        val pivotRegex = "(\\d+\\.?\\d*)m?pivot(\\d+\\.?\\d*|move)".toRegex()
        val pivotMatch = pivotRegex.find(expression)
        if (pivotMatch != null) {
            if (isGraph) return expression
            val radius = pivotMatch.groupValues[1].toDouble()
            val angleVal = pivotMatch.groupValues[2]
            if (angleVal == "move") return "0"
            val angle = angleVal.toDouble()
            return (radius * cos(Math.toRadians(angle))).toString() // Return X coordinate
        }

        // Handle "10 pivot 25 piston move" syntax
        val pistonRegex = "(\\d+\\.?\\d*)\\s*pivot.*?(\\d+\\.?\\d*)\\s*piston".toRegex()
        val pistonMatch = pistonRegex.find(expression)
        if (pistonMatch != null) {
            if (isGraph) return expression
            val r = pistonMatch.groupValues[1].toDouble()
            val l = pistonMatch.groupValues[2].toDouble()
            return (r + l).toString() // Max stroke length
        }

        // Handle "solve(x^2 - 10x + 8)" syntax
        val solveRegex = "solve\\((.*)\\)".toRegex()
        val solveMatch = solveRegex.find(expression)
        if (solveMatch != null) {
            if (isGraph) return expression
            val inner = solveMatch.groupValues[1]
            return solvePolynomialSmart(inner)
        }

        // Handle "spiral(diameter, holes, turns)" syntax
        if (expression.startsWith("spiral(")) {
            if (isGraph) return expression
            return "0"
        }

        // Handle "nested(diameter, sides, count, spacing)" syntax
        if (expression.startsWith("nested(")) {
            if (isGraph) return expression
            return "0"
        }

        // Handle generic shapes: rectangle(l, w), square(s), circle(r), triangle(b, h), trapezoid(a, b, h), polygon(d, n)
        val shapeRegex = "(rectangle|square|circle|triangle|trapezoid|polygon)\\((\\d+\\.?\\d*)(?:,\\s*(\\d+\\.?\\d*))?(?:,\\s*(\\d+\\.?\\d*))?\\)".toRegex()
        val shapeMatch = shapeRegex.find(expression)
        if (shapeMatch != null) {
            if (isGraph) return expression
            val type = shapeMatch.groupValues[1]
            val v1 = shapeMatch.groupValues[2].toDoubleOrNull() ?: 0.0
            val v2 = shapeMatch.groupValues[3].toDoubleOrNull() ?: 0.0
            val v3 = shapeMatch.groupValues[4].toDoubleOrNull() ?: 0.0
            return when (type) {
                "rectangle" -> (v1 * v2).toString()
                "square" -> (v1 * v1).toString()
                "circle" -> (PI * v1 * v1).toString()
                "triangle" -> (0.5 * v1 * v2).toString()
                "trapezoid" -> (0.5 * (v1 + v2) * v3).toString()
                "polygon" -> {
                    val n = v2.toInt().coerceAtLeast(3)
                    val r = v1 / 2.0
                    (0.5 * n * r * r * sin(2 * PI / n)).toString()
                }
                else -> "0"
            }
        }

        if (!isGraph) {
            expression = expression.replace("(?<![a-z])x(?![a-z])".toRegex(), "*")
        }
        expression = expression.replace("(\\d)(\\()".toRegex(), "$1*$2")
        expression = expression.replace("(\\))(\\d)".toRegex(), "$1*$2")
        expression = expression.replace("(\\))(\\()".toRegex(), "$1*$2")
        expression = expression.replace("([xy])(\\()".toRegex(), "$1*$2")
        expression = expression.replace("(\\))([xy])".toRegex(), "$1*$2")
        expression = expression.replace("(\\d)([a-z])".toRegex(), "$1*$2")
        expression = expression.replace("(\\))([a-z])".toRegex(), "$1*$2")
        expression = expression.replace("([xy])([a-z])".toRegex(), "$1*$2")

        return expression
    }

    fun numericalDerivative(expr: String, x: Double): Double {
        val h = 1e-7
        val f1 = evaluateFull(expr, true, x + h) as? Double ?: 0.0
        val f2 = evaluateFull(expr, true, x - h) as? Double ?: 0.0
        return (f1 - f2) / (2.0 * h)
    }

    fun numericalIntegral(expr: String, a: Double, b: Double): Double {
        val n = 100
        val h = (b - a) / n
        var sum = (evaluateFull(expr, true, a) as? Double ?: 0.0) + (evaluateFull(expr, true, b) as? Double ?: 0.0)
        for (i in 1 until n) {
            val x = a + i * h
            val f = evaluateFull(expr, true, x) as? Double ?: 0.0
            sum += if (i % 2 == 0) 2 * f else 4 * f
        }
        return sum * h / 3.0
    }

    private fun expandLinearFactors(roots: List<Double>, isGraph: Boolean = false): String {
        // Multiplies (x + r1)(x + r2)...(x + rn)
        var poly = doubleArrayOf(1.0) // Start with 1
        for (r in roots) {
            val nextPoly = DoubleArray(poly.size + 1)
            for (i in poly.indices) {
                nextPoly[i] += poly[i] * r // constant part
                nextPoly[i + 1] += poly[i] // x part
            }
            poly = nextPoly
        }
        
        val sb = StringBuilder()
        for (i in poly.indices.reversed()) {
            val coeff = poly[i]
            if (abs(coeff) < 1e-10) continue
            
            if (sb.isNotEmpty() && coeff > 0) sb.append(" + ")
            else if (coeff < 0) {
                if (sb.isEmpty()) sb.append("-") else sb.append(" - ")
            }
            
            val absCoeff = abs(coeff)
            if (absCoeff != 1.0 || i == 0) {
                if (isGraph) sb.append("($absCoeff)") else sb.append(formatIndianStyle(absCoeff))
            }
            
            if (i > 0) {
                if (isGraph && (absCoeff != 1.0 || i == 0)) sb.append("*")
                sb.append("x")
                if (i > 1) {
                    if (isGraph) {
                        sb.append("^$i")
                    } else {
                        val sup = when(i) { 2 -> "²"; 3 -> "³"; 4 -> "⁴"; 5 -> "⁵"; else -> "^$i" }
                        sb.append(sup)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun calculateFib(n: Int): Double {
        if (n <= 0) return 0.0
        if (n == 1) return 1.0
        var a = 0.0; var b = 1.0
        for (i in 2..n) { val temp = a + b; a = b; b = temp }
        return b
    }

    private fun calculateFactorial(n: Int): Double {
        if (n < 0) return Double.NaN
        var res = 1.0
        for (i in 2..n) res *= i
        return res
    }

    private fun calculateCombination(n: Int, r: Int): Long {
        if (r < 0 || r > n) return 0
        if (r == 0 || r == n) return 1
        var res = 1L
        val k = min(r, n - r)
        for (i in 1..k) res = res * (n - i + 1) / i
        return res
    }

    fun evaluatePreprocessed(expression: String, useRadians: Boolean = false, xValue: Double? = null, yValue: Double? = null): Double? {
        if (expression.isBlank()) return null
        return try {
            ExpressionParser(expression, useRadians, xValue, yValue).parse()
        } catch (e: Exception) {
            null
        }
    }

    private fun solvePolynomialSmart(expr: String): String {
        // Robust coefficient extraction via evaluation
        fun f(x: Double): Double = evaluatePreprocessed(expr, true, x) ?: 0.0

        val y0 = f(0.0)
        val y1 = f(1.0)
        val ym1 = f(-1.0)
        val y2 = f(2.0)

        // Try Quadratic: ax^2 + bx + c = 0
        val c = y0
        val a = (y1 + ym1 - 2 * c) / 2.0
        val b = y1 - c - a

        // Verify if it matches quadratic at x=2
        if (abs(y2 - (a * 4.0 + b * 2.0 + c)) < 1e-7) {
            if (abs(a) < 1e-11) {
                // Linear: bx + c = 0
                if (abs(b) < 1e-11) return if (abs(c) < 1e-11) "Infinite Solutions" else "No Solution"
                return "x=${formatIndianStyle(-c / b)}"
            }
            val disc = b * b - 4 * a * c
            return if (disc >= 0) {
                val x1 = (-b + sqrt(disc)) / (2 * a)
                val x2 = (-b - sqrt(disc)) / (2 * a)
                "x1=${formatIndianStyle(x1)}, x2=${formatIndianStyle(x2)}"
            } else {
                val re = -b / (2 * a)
                val im = sqrt(-disc) / (2 * a)
                "x1=${Complex(re, im)}, x2=${Complex(re, -im)}"
            }
        }

        // Try Cubic: ax^3 + bx^2 + cx + d = 0
        val ym2 = f(-2.0)
        val y3 = f(3.0)
        val d_c = y0
        // Using points 1, -1, 2 to find a, b, c for ax^3 + bx^2 + cx + d
        // f(1)-d = a + b + c
        // f(-1)-d = -a + b - c
        // (f(1)+f(-1)-2d)/2 = b  (quadratic part of cubic) -> No, this only works if a=0
        // Correct system for Cubic:
        // b = ((y1+ym1-2d) - (a+a))/2 -> No
        // Let's use simpler system:
        val v1 = y1 - d_c // a + b + c
        val vm1 = ym1 - d_c // -a + b - c
        val v2 = y2 - d_c // 8a + 4b + 2c
        // v1 + vm1 = 2b -> b = (v1 + vm1) / 2
        val b_c = (v1 + vm1) / 2.0
        // v1 - vm1 = 2a + 2c -> a + c = (v1 - vm1) / 2
        // v2 - 4b = 8a + 2c
        val b2c = v2 - 4.0 * b_c // 8a + 2c
        val ac = (v1 - vm1) / 2.0 // a + c
        // b2c - 2*ac = 6a
        val a_c = (b2c - 2.0 * ac) / 6.0
        val c_c = ac - a_c

        // Verify at x=3 or x=-2
        if (abs(y3 - (a_c * 27.0 + b_c * 9.0 + c_c * 3.0 + d_c)) < 1e-7) {
            val roots = solveCubic(a_c, b_c, c_c, d_c)
            return if (roots.isEmpty()) "No Real Roots" else roots.mapIndexed { i, r -> "x${i+1}=$r" }.joinToString(", ")
        }

        return "Equation too complex for auto-solver"
    }

    fun solveCubic(a: Double, b: Double, c: Double, d: Double): List<Complex> {
        if (abs(a) < 1e-11) return emptyList()

        val b_a = b / a
        val c_a = c / a
        val d_a = d / a

        val Q = (3.0 * c_a - b_a * b_a) / 9.0
        val R = (9.0 * b_a * c_a - 27.0 * d_a - 2.0 * b_a * b_a * b_a) / 54.0
        val D = Q * Q * Q + R * R

        val roots = mutableListOf<Complex>()
        if (D > 0) {
            val S = cbrt(R + sqrt(D))
            val T = cbrt(R - sqrt(D))
            roots.add(Complex(-b_a / 3.0 + (S + T), 0.0))
            roots.add(Complex(-b_a / 3.0 - (S + T) / 2.0, (S - T) * sqrt(3.0) / 2.0))
            roots.add(Complex(-b_a / 3.0 - (S + T) / 2.0, -(S - T) * sqrt(3.0) / 2.0))
        } else if (abs(D) < 1e-13) {
            val S = cbrt(R)
            roots.add(Complex(-b_a / 3.0 + 2.0 * S, 0.0))
            roots.add(Complex(-b_a / 3.0 - S, 0.0))
        } else {
            val theta = acos(R / sqrt(-Q * Q * Q))
            roots.add(Complex(2.0 * sqrt(-Q) * cos(theta / 3.0) - b_a / 3.0, 0.0))
            roots.add(Complex(2.0 * sqrt(-Q) * cos((theta + 2.0 * PI) / 3.0) - b_a / 3.0, 0.0))
            roots.add(Complex(2.0 * sqrt(-Q) * cos((theta + 4.0 * PI) / 3.0) - b_a / 3.0, 0.0))
        }
        return roots
    }

    fun solveQuartic(a: Double, b: Double, c: Double, d: Double, e: Double): List<Complex> {
        if (abs(a) < 1e-11) return solveCubic(b, c, d, e)

        val A = b / a
        val B = c / a
        val C = d / a
        val D = e / a

        val p = B - 3.0 * A * A / 8.0
        val q = C - A * B / 2.0 + A * A * A / 8.0
        val r = D - A * C / 4.0 + A * A * B / 16.0 - 3.0 * A * A * A * A / 256.0

        val roots = mutableListOf<Complex>()
        if (abs(q) < 1e-12) {
            val disc = p * p - 4.0 * r
            if (disc >= 0) {
                val z1 = (-p + sqrt(disc)) / 2.0
                val z2 = (-p - sqrt(disc)) / 2.0
                if (z1 >= 0) { roots.add(Complex(sqrt(z1))); roots.add(Complex(-sqrt(z1))) }
                else { roots.add(Complex(0.0, sqrt(-z1))); roots.add(Complex(0.0, -sqrt(-z1))) }
                if (z2 >= 0) { roots.add(Complex(sqrt(z2))); roots.add(Complex(-sqrt(z2))) }
                else { roots.add(Complex(0.0, sqrt(-z2))); roots.add(Complex(0.0, -sqrt(-z2))) }
            } else {
                val z_re = -p / 2.0
                val z_im = sqrt(-disc) / 2.0
                // sqrt of complex number w = z_re + i*z_im
                val mag = sqrt(z_re * z_re + z_im * z_im)
                val r1 = sqrt((mag + z_re) / 2.0)
                val i1 = sqrt((mag - z_re) / 2.0)
                roots.add(Complex(r1, i1)); roots.add(Complex(-r1, -i1))
                roots.add(Complex(r1, -i1)); roots.add(Complex(-r1, i1))
            }
        } else {
            val resRoots = solveCubic(1.0, 2.0 * p, p * p - 4.0 * r, -q * q)
            val z = resRoots.filter { abs(it.imag) < 1e-9 && it.real > 0 }.maxByOrNull { it.real } ?: resRoots.first()
            val sqrtZ = if (z.real >= 0 && abs(z.imag) < 1e-9) Complex(sqrt(z.real)) else {
                val mag = z.abs()
                val r1 = sqrt((mag + z.real) / 2.0)
                val i1 = (if (z.imag >= 0) 1 else -1) * sqrt((mag - z.real) / 2.0)
                Complex(r1, i1)
            }

            val p_z = Complex(p) + z
            val q_sz = Complex(q) / sqrtZ

            val q1_c = (p_z - q_sz) * Complex(0.5)
            val q2_c = (p_z + q_sz) * Complex(0.5)

            // solve x^2 + sqrtZ*x + q1_c = 0 and x^2 - sqrtZ*x + q2_c = 0
            roots.addAll(solveQuadraticComplex(Complex(1.0), sqrtZ, q1_c))
            roots.addAll(solveQuadraticComplex(Complex(1.0), sqrtZ * Complex(-1.0), q2_c))
        }
        return roots.map { Complex(it.real - A / 4.0, it.imag) }
    }

    private fun solveQuadraticComplex(a: Complex, b: Complex, c: Complex): List<Complex> {
        val disc = b * b - Complex(4.0) * a * c
        val sqrtD = sqrtComplex(disc)
        val x1 = (Complex(-b.real, -b.imag) + sqrtD) / (Complex(2.0) * a)
        val x2 = (Complex(-b.real, -b.imag) - sqrtD) / (Complex(2.0) * a)
        return listOf(x1, x2)
    }

    private fun sqrtComplex(z: Complex): Complex {
        val mag = z.abs()
        val r = sqrt((mag + z.real) / 2.0)
        val i = (if (z.imag >= 0) 1 else -1) * sqrt((mag - z.real) / 2.0)
        return Complex(r, i)
    }

    fun solveQuinticNumerical(coeffs: List<Double>): List<Complex> {
        val n = coeffs.size - 1
        if (n <= 0) return emptyList()
        val a = coeffs[0]
        val c = coeffs.map { it / a }

        // Initial guesses for Durand-Kerner (on unit circle with a bit of shift)
        val roots = MutableList(n) { i ->
            val angle = 2.0 * PI * i / n + 0.5
            Complex(cos(angle), sin(angle))
        }

        for (iter in 0..100) {
            var maxChange = 0.0
            for (i in 0 until n) {
                var polyVal = Complex(c[0]) // polyVal = c[0]*x^n + ... + c[n]
                for (j in 1..n) {
                    polyVal = polyVal * roots[i] + Complex(c[j])
                }

                var denom = Complex(1.0)
                for (j in 0 until n) {
                    if (i != j) denom *= (roots[i] - roots[j])
                }

                val change = polyVal / denom
                roots[i] = roots[i] - change
                maxChange = max(maxChange, change.abs())
            }
            if (maxChange < 1e-10) break
        }

        return roots.sortedWith(compareBy({ abs(it.imag) > 1e-9 }, { it.real }))
    }

    private fun cbrt(v: Double): Double = if (v >= 0) v.pow(1.0 / 3.0) else -(-v).pow(1.0 / 3.0)

    private fun parseNumberList(s: String): List<Double> {
        return s.split(',').mapNotNull { it.trim().toDoubleOrNull() }
    }

    private fun isPrime(n: Int): Boolean {
        if (n <= 1) return false
        if (n <= 3) return true
        if (n % 2 == 0 || n % 3 == 0) return false
        var i = 5
        while (i * i <= n) {
            if (n % i == 0 || n % (i + 2) == 0) return false
            i += 6
        }
        return true
    }

    private fun calculateRMS(list: List<Double>): Double {
        if (list.isEmpty()) return 0.0
        val squareSum = list.sumOf { it * it }
        return sqrt(squareSum / list.size)
    }

    private fun zToP(z: Double): Double {
        return 1.0 / (1.0 + exp(-0.07056 * z.pow(3.0) - 1.5976 * z))
    }

    private fun binaryToDecimal(binaryString: String): String {
        return try {
            val decimal = java.lang.Long.parseLong(binaryString, 2)
            formatIndianStyle(decimal.toDouble())
        } catch (e: Exception) {
            "Invalid Binary"
        }
    }

    private fun decimalToBinary(number: Double): String {
        return try {
            java.lang.Long.toBinaryString(number.toLong())
        } catch (e: Exception) {
            "Error"
        }
    }

    private class ExpressionParser(val str: String, val useRadians: Boolean, val xValue: Double?, val yValue: Double? = null) {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.code)) x += parseTerm()
                else if (eat('-'.code)) x -= parseTerm()
                else return x
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                if (eat('*'.code)) x *= parseFactor()
                else if (eat('/'.code)) x /= parseFactor()
                else return x
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) {
                while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else if (ch >= 'a'.code && ch <= 'z'.code || ch == 'π'.code || ch == '√'.code) {
                if (eat('√'.code)) {
                    x = sqrt(parseFactor())
                } else if (ch == 'π'.code) {
                    nextChar(); x = PI
                } else {
                    while (ch >= 'a'.code && ch <= 'z'.code || (ch >= '0'.code && ch <= '9'.code && pos > startPos)) nextChar()
                    val func = str.substring(startPos, pos)
                    if (func == "x" && xValue != null) {
                        x = xValue
                    } else if (func == "y") {
                        x = yValue ?: xValue ?: 0.0
                    } else if (func == "pi") {
                        x = PI
                    } else if (func == "e") {
                        x = E
                    } else {
                        val arg = parseFactor()
                        x = when (func) {
                            "sin" -> if (useRadians) sin(arg) else sin(Math.toRadians(arg))
                            "cos" -> if (useRadians) cos(arg) else cos(Math.toRadians(arg))
                            "tan" -> if (useRadians) tan(arg) else tan(Math.toRadians(arg))
                            "asin", "sin-1" -> if (useRadians) asin(arg) else Math.toDegrees(asin(arg))
                            "acos", "cos-1" -> if (useRadians) acos(arg) else Math.toDegrees(acos(arg))
                            "atan", "tan-1" -> if (useRadians) atan(arg) else Math.toDegrees(atan(arg))
                            "sinh" -> sinh(arg); "cosh" -> cosh(arg); "tanh" -> tanh(arg)
                            "sqrt" -> sqrt(arg); "log" -> log10(arg); "ln" -> ln(arg); "abs" -> abs(arg); "exp" -> exp(arg)
                            "fact" -> calculateFactorial(arg.toInt())
                            "pentagon" -> arg * sin(Math.toRadians(36.0))
                            "hexagon" -> arg * sin(Math.toRadians(30.0))
                            "octagon" -> arg * sin(Math.toRadians(22.5))
                            else -> 0.0
                        }
                    }
                }
            } else {
                return 0.0
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            if (eat('%'.code)) x /= 100.0
            return x
        }
    }
}
