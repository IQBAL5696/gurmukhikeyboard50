package com.iqbal.gurmukhikeyboard50

import kotlin.math.abs

object CalculatorHelper {
    // ਹੁਣ ਇਹ Percentage (%) ਨੂੰ ਵੀ ਸਮਝੇਗਾ, ਜੋ ਬਿਜਨੇਸ ਲਈ ਬਹੁਤ ਲਾਹੇਵੰਦ ਹੈ
    private val regex = Regex("""(\d+\.?\d*)\s*([\+\-\*/])\s*(\d+\.?\d*)\s*(%?)\s*=?$""")

    fun evaluate(text: String): String? {
        val match = regex.find(text.trim()) ?: return null
        val (num1Str, operator, num2Str, percentSign) = match.destructured
        
        val n1 = num1Str.toDoubleOrNull() ?: return null
        val n2 = num2Str.toDoubleOrNull() ?: return null
        val isPercent = percentSign.isNotEmpty()
        
        val result = when (operator) {
            "+" -> if (isPercent) n1 + (n1 * n2 / 100) else n1 + n2
            "-" -> if (isPercent) n1 - (n1 * n2 / 100) else n1 - n2
            "*" -> if (isPercent) (n1 * n2 / 100) else n1 * n2
            "/" -> if (n2 != 0.0) n1 / n2 else null
            else -> null
        } ?: return null
        
        val finalResult = if (result % 1.0 == 0.0) result.toLong().toString() else "%.2f".format(result)
        return "=$finalResult"
    }
}
