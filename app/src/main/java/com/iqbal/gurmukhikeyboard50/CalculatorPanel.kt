package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs

class CalculatorPanel(private val context: Context, private val onDismiss: () -> Unit) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.calculator_panel_layout, null)

    private val display: TextView = view.findViewById(R.id.calculator_display)
    private val displayScrollView: HorizontalScrollView = view.findViewById(R.id.display_scroll_view)
    private val stepsDisplay: TextView = view.findViewById(R.id.calculator_steps)
    private val formulaDisplay: TextView = view.findViewById(R.id.calculator_formula)
    private val memoryIndicator: TextView = view.findViewById(R.id.memory_indicator)
    private val historyRecyclerView: RecyclerView = view.findViewById(R.id.history_recycler_view_main)
    private val scientificRow1: View = view.findViewById(R.id.scientific_row_1)
    private val scientificRow2: View = view.findViewById(R.id.scientific_row_2)

    private var currentInput = ""
    private var lastValue = BigDecimal.ZERO
    private var operator = ""
    private var isNewOperation = true
    private var memoryValue = BigDecimal.ZERO
    private var grandTotal = BigDecimal.ZERO
    private var historyList = ArrayList<Calculation>()
    private var historyExpression = ""
    private val dfSimple = DecimalFormat("#.##########")
    private lateinit var historyAdapter: HistoryAdapter

    init {
        setupUI()
    }

    private fun setupUI() {
        historyRecyclerView.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        historyAdapter = HistoryAdapter(historyList) { selected ->
            setResult(selected.result)
        }
        historyRecyclerView.adapter = historyAdapter

        view.findViewById<View>(R.id.close_calculator_button)?.setOnClickListener { onDismiss() }
        view.findViewById<View>(R.id.settings_calculator_button)?.setOnClickListener {
            showQuickSettingsDialog()
        }

        applyCalculatorTheme()
        setupNumbers()
        setupScientificListeners()
        updateMemoryIndicator()
        updateScientificVisibility()
        updateDisplay()
        updateStepsDisplay()
    }

    private fun setupNumbers() {
        val numbers = mapOf(
            R.id.button_0 to "0", R.id.button_1 to "1", R.id.button_2 to "2",
            R.id.button_3 to "3", R.id.button_4 to "4", R.id.button_5 to "5",
            R.id.button_6 to "6", R.id.button_7 to "7", R.id.button_8 to "8",
            R.id.button_9 to "9", R.id.button_00 to "00"
        )

        for ((id, value) in numbers) {
            view.findViewById<MaterialButton>(id)?.setOnClickListener {
                provideFeedback()
                appendNumber(value)
                updateDisplay()
            }
        }

        view.findViewById<MaterialButton>(R.id.button_dot)?.setOnClickListener {
            provideFeedback()
            if (!currentInput.contains(".")) {
                if (currentInput.isEmpty()) currentInput = "0"
                currentInput += "."
                updateDisplay()
            }
        }

        view.findViewById<MaterialButton>(R.id.button_add)?.setOnClickListener { provideFeedback(); setOperator("+") }
        view.findViewById<MaterialButton>(R.id.button_subtract)?.setOnClickListener { provideFeedback(); setOperator("-") }
        view.findViewById<MaterialButton>(R.id.button_multiply)?.setOnClickListener { provideFeedback(); setOperator("×") }
        view.findViewById<MaterialButton>(R.id.button_divide)?.setOnClickListener { provideFeedback(); setOperator("÷") }

        view.findViewById<MaterialButton>(R.id.button_percent)?.setOnClickListener { provideFeedback(); applyPercentage() }
        view.findViewById<MaterialButton>(R.id.button_gst_minus)?.setOnClickListener { provideFeedback(); showGstMinusMenu(it) }
        view.findViewById<MaterialButton>(R.id.button_gst_5)?.setOnClickListener { provideFeedback(); applyGST(BigDecimal("5.0")) }
        view.findViewById<MaterialButton>(R.id.button_gst_12)?.setOnClickListener { provideFeedback(); applyGST(BigDecimal("12.0")) }
        view.findViewById<MaterialButton>(R.id.button_gst_18)?.setOnClickListener { provideFeedback(); applyGST(BigDecimal("18.0")) }
        view.findViewById<MaterialButton>(R.id.button_clear)?.setOnClickListener { provideFeedback(); clear() }
        view.findViewById<MaterialButton>(R.id.button_correct)?.setOnClickListener { provideFeedback(); backspace() }
        view.findViewById<MaterialButton>(R.id.button_equals)?.setOnClickListener { provideFeedback(); calculateResult(true) }

        view.findViewById<MaterialButton>(R.id.button_plus_minus)?.setOnClickListener {
            provideFeedback()
            if (currentInput.isNotEmpty()) {
                val value = currentInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (value.signum() != 0) {
                    currentInput = value.negate().stripTrailingZeros().toPlainString()
                    updateDisplay()
                }
            } else if (operator.isEmpty() && lastValue.signum() != 0) {
                lastValue = lastValue.negate()
                currentInput = lastValue.stripTrailingZeros().toPlainString()
                isNewOperation = false
                updateDisplay()
            }
        }

        view.findViewById<MaterialButton>(R.id.button_gt)?.setOnClickListener {
            provideFeedback()
            display.text = "GT = ${formatValue(grandTotal)}"
            currentInput = grandTotal.stripTrailingZeros().toPlainString()
            isNewOperation = true
        }

        view.findViewById<MaterialButton>(R.id.button_m_plus)?.setOnClickListener {
            provideFeedback()
            val value = getActiveValue()
            memoryValue = memoryValue.add(value)
            isNewOperation = true
            updateMemoryIndicator()
        }
        view.findViewById<MaterialButton>(R.id.button_m_minus)?.setOnClickListener {
            provideFeedback()
            val value = getActiveValue()
            memoryValue = memoryValue.subtract(value)
            isNewOperation = true
            updateMemoryIndicator()
        }
        view.findViewById<MaterialButton>(R.id.button_mrc)?.setOnClickListener {
            provideFeedback()
            currentInput = memoryValue.stripTrailingZeros().toPlainString()
            isNewOperation = false
            historyExpression = formatValue(memoryValue)
            updateDisplay()
        }
    }

    private fun setupScientificListeners() {
        view.findViewById<View>(R.id.button_sqrt)?.setOnClickListener { applyUnary { BigDecimal(kotlin.math.sqrt(it.toDouble())) } }
        view.findViewById<View>(R.id.button_square)?.setOnClickListener { applyUnary { it.multiply(it) } }
        view.findViewById<View>(R.id.button_pi)?.setOnClickListener { provideFeedback(); currentInput = Math.PI.toString(); updateDisplay() }
        view.findViewById<View>(R.id.button_e)?.setOnClickListener { provideFeedback(); currentInput = Math.E.toString(); updateDisplay() }
        view.findViewById<View>(R.id.button_power)?.setOnClickListener { provideFeedback(); setOperator("^") }
        
        view.findViewById<View>(R.id.button_sin)?.setOnClickListener { applyUnary { BigDecimal(kotlin.math.sin(Math.toRadians(it.toDouble()))) } }
        view.findViewById<View>(R.id.button_cos)?.setOnClickListener { applyUnary { BigDecimal(kotlin.math.cos(Math.toRadians(it.toDouble()))) } }
        view.findViewById<View>(R.id.button_tan)?.setOnClickListener { applyUnary { BigDecimal(kotlin.math.tan(Math.toRadians(it.toDouble()))) } }
        view.findViewById<View>(R.id.button_log)?.setOnClickListener { applyUnary { BigDecimal(kotlin.math.log10(it.toDouble())) } }
        view.findViewById<View>(R.id.button_ln)?.setOnClickListener { applyUnary { BigDecimal(kotlin.math.ln(it.toDouble())) } }
    }

    private fun applyUnary(operation: (BigDecimal) -> BigDecimal) {
        provideFeedback()
        val value = getActiveValue()
        try {
            val result = operation(value)
            currentInput = result.stripTrailingZeros().toPlainString()
            isNewOperation = true
            updateDisplay()
        } catch (e: Exception) {
            display.text = "Error"
        }
    }

    fun handleExternalUpdate(data: Intent) {
        if (data.getBooleanExtra("history_cleared", false)) {
            historyList.clear()
            grandTotal = BigDecimal.ZERO
        } else {
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val updatedHistory = data.getSerializableExtra("updated_history") as? ArrayList<Calculation>
            if (updatedHistory != null) {
                historyList.clear()
                historyList.addAll(updatedHistory.takeLast(20))
                recalculateGrandTotal(updatedHistory)
            }

            val selectedResult = data.getStringExtra("selected_result")
            if (selectedResult != null) {
                setResult(selectedResult)
            }
        }
        historyAdapter.notifyDataSetChanged()
        updateStepsDisplay()
    }

    private fun recalculateGrandTotal(fullHistory: ArrayList<Calculation>) {
        grandTotal = BigDecimal.ZERO
        for (item in fullHistory) {
            grandTotal = grandTotal.add(item.result.replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
    }

    fun setResult(result: String) {
        currentInput = result.replace(",", "")
        lastValue = currentInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
        operator = ""
        isNewOperation = false
        historyExpression = formatValue(lastValue)
        updateDisplay()
    }

    private fun formatValue(value: BigDecimal): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val valueStr = value.stripTrailingZeros().toPlainString()
        return if (prefs.getBoolean("calc_indian_format", true)) {
            CalculatorHelper.formatIndianStyleString(valueStr)
        } else {
            CalculatorHelper.formatInternationalStyleString(valueStr)
        }
    }

    private fun formatRawInput(input: String): String {
        if (input.isEmpty()) return "0"
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isIndian = prefs.getBoolean("calc_indian_format", true)
        val formatted = if (isIndian) CalculatorHelper.formatIndianStyleString(input) else CalculatorHelper.formatInternationalStyleString(input)
        if (input.endsWith(".")) return "$formatted."
        return formatted
    }

    private fun appendNumber(num: String) {
        if (isNewOperation) { 
            currentInput = if (num == "00") "0" else num
            isNewOperation = false
            return
        }

        // Prevent multiple leading zeros
        if (currentInput == "0") {
            if (num == "00" || num == "0") return
            currentInput = num
            return
        }

        // Limit to 100 digits to maintain precision and layout
        if (currentInput.replace(".", "").length >= 100) return

        currentInput += num
    }

    private fun setOperator(op: String) {
        if (currentInput.isNotEmpty() || operator.isNotEmpty()) {
            if (currentInput.isNotEmpty() && operator.isNotEmpty()) {
                calculateResult(false)
            } else if (currentInput.isNotEmpty()) {
                lastValue = currentInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                historyExpression = formatValue(lastValue)
            }
            operator = op
            currentInput = ""
            isNewOperation = true
            updateDisplay()
        }
    }

    private fun calculateResult(isFinal: Boolean) {
        if (operator.isEmpty()) return
        if (currentInput.isEmpty() && isFinal) {
            val resultStr = formatValue(lastValue)
            val cleanHistoryExpr = historyExpression.replace(" ", "")
            if (cleanHistoryExpr.isNotEmpty() && cleanHistoryExpr != resultStr) {
                addHistoryItem(cleanHistoryExpr, resultStr)
            }
            operator = ""; historyExpression = resultStr; isNewOperation = true; updateDisplay(); return
        }
        if (currentInput.isEmpty()) return
        val v2 = currentInput.toBigDecimalOrNull() ?: return
        val result = try {
            when (operator) {
                "+" -> lastValue.add(v2)
                "-" -> lastValue.subtract(v2)
                "×" -> lastValue.multiply(v2)
                "÷" -> if (v2.signum() != 0) lastValue.divide(v2, 10, RoundingMode.HALF_UP) else BigDecimal.ZERO
                "^" -> BigDecimal(Math.pow(lastValue.toDouble(), v2.toDouble()))
                else -> return
            }
        } catch (e: ArithmeticException) { BigDecimal.ZERO }

        val opForHistory = when(operator) { "×" -> "*"; "÷" -> "/"; else -> operator }
        historyExpression = if (historyExpression.isEmpty()) "${formatValue(lastValue)}$opForHistory${formatRawInput(currentInput)}" else "$historyExpression$opForHistory${formatRawInput(currentInput)}"
        val resultStr = formatValue(result)
        if (isFinal) {
            addHistoryItem(historyExpression.replace(" ", ""), resultStr)
            grandTotal = grandTotal.add(result)
            currentInput = result.stripTrailingZeros().toPlainString()
            operator = ""
            historyExpression = resultStr
            isNewOperation = true
            updateDisplay()
        } else {
            lastValue = result
            currentInput = ""
            isNewOperation = true
            updateDisplay()
        }
    }

    private fun addHistoryItem(expression: String, result: String) {
        historyList.add(Calculation("$expression=", result))
        historyAdapter.notifyItemInserted(historyList.size - 1)
        updateStepsDisplay()
        // Ensure the list scrolls to the bottom to show the latest calculation
        historyRecyclerView.post {
            historyRecyclerView.scrollToPosition(historyList.size - 1)
        }
    }

    private fun applyPercentage() {
        if (currentInput.isNotEmpty()) {
            val value = currentInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val result = when (operator) {
                "+", "-" -> lastValue.multiply(value.divide(BigDecimal("100"), 10, RoundingMode.HALF_UP))
                "×", "÷" -> value.divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)
                else -> value.divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)
            }
            currentInput = result.stripTrailingZeros().toPlainString()
            if (operator.isNotEmpty()) calculateResult(true) else { isNewOperation = true; historyExpression = formatValue(result); updateDisplay() }
        }
    }

    private fun applyGST(percentage: BigDecimal) {
        val value = getActiveValue()
        if (value.signum() == 0) return

        val gstAmount = value.multiply(percentage.divide(BigDecimal("100"), 10, RoundingMode.HALF_UP))
        val result = value.add(gstAmount)

        val resultStr = formatValue(result)
        val gstExpression = "${formatValue(value)}+${percentage.stripTrailingZeros().toPlainString()}%GST"

        addHistoryItem(gstExpression, resultStr)

        grandTotal = grandTotal.add(result)
        currentInput = result.stripTrailingZeros().toPlainString()
        operator = ""
        historyExpression = resultStr
        isNewOperation = true
        updateDisplay()
    }

    private fun showGstMinusMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(context, view)
        popup.menu.add("5% GST-")
        popup.menu.add("12% GST-")
        popup.menu.add("18% GST-")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "5% GST-" -> applyNegativeGST(BigDecimal("5.0"))
                "12% GST-" -> applyNegativeGST(BigDecimal("12.0"))
                "18% GST-" -> applyNegativeGST(BigDecimal("18.0"))
            }
            true
        }
        popup.show()
    }

    private fun applyNegativeGST(percentage: BigDecimal) {
        val value = getActiveValue()
        if (value.signum() == 0) return

        val divisor = BigDecimal.ONE.add(percentage.divide(BigDecimal("100"), 10, RoundingMode.HALF_UP))
        val originalValue = value.divide(divisor, 10, RoundingMode.HALF_UP)

        val resultStr = formatValue(originalValue)
        val gstExpression = "${formatValue(value)}-${percentage.stripTrailingZeros().toPlainString()}%GST"

        addHistoryItem(gstExpression, resultStr)

        grandTotal = grandTotal.add(originalValue)
        currentInput = originalValue.stripTrailingZeros().toPlainString()
        operator = ""
        historyExpression = resultStr
        isNewOperation = true
        updateDisplay()
    }

    private fun getActiveValue(): BigDecimal {
        val dText = display.text.toString()
        return if (dText.contains("=")) dText.split("=").last().trim().replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO else currentInput.replace(",", "").toBigDecimalOrNull() ?: lastValue
    }

    private fun updateMemoryIndicator() { memoryIndicator.text = if (memoryValue.signum() != 0) "M=${formatValue(memoryValue)}" else "" }
    private fun updateStepsDisplay() { stepsDisplay.text = String.format(Locale.US, "%02d", historyList.size) }
    
    private fun updateScientificVisibility() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isEnabled = prefs.getBoolean("scientific_mode", false)
        scientificRow1.visibility = if (isEnabled) View.VISIBLE else View.GONE
        scientificRow2.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }

    private fun updateDisplay() {
        val length = currentInput.replace(".", "").length
        val textSizeSp = when {
            length <= 10 -> 52f
            length <= 15 -> 42f
            else -> 30f
        }
        display.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        if (operator.isNotEmpty()) {
            formulaDisplay.text = "$historyExpression $operator"
            display.text = if (currentInput.isEmpty()) formatValue(lastValue) else formatRawInput(currentInput)
        } else {
            formulaDisplay.text = ""
            display.text = if (currentInput.isEmpty()) "0" else formatRawInput(currentInput)
        }

        // Auto-scroll to the right so the latest digit is visible
        displayScrollView.post {
            displayScrollView.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun clear() { grandTotal = BigDecimal.ZERO; currentInput = ""; lastValue = BigDecimal.ZERO; operator = ""; isNewOperation = true; historyExpression = ""; historyList.clear(); historyAdapter.notifyDataSetChanged(); updateStepsDisplay(); updateDisplay() }
    private fun backspace() {
        if (display.text.toString().contains("GT")) { display.text = "0"; currentInput = ""; historyExpression = ""; return }
        if (currentInput.isNotEmpty()) { currentInput = currentInput.dropLast(1); updateDisplay() }
        else if (operator.isNotEmpty()) { operator = ""; currentInput = lastValue.stripTrailingZeros().toPlainString(); isNewOperation = false; updateDisplay() }
    }

    private fun showQuickSettingsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_calculator_settings, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        val switchVibrate = dialogView.findViewById<SwitchMaterial>(R.id.switch_vibrate)
        val switchSound = dialogView.findViewById<SwitchMaterial>(R.id.switch_sound)
        val switchIndian = dialogView.findViewById<SwitchMaterial>(R.id.switch_indian_format)
        val switchScientific = dialogView.findViewById<SwitchMaterial>(R.id.switch_scientific_mode)
        val themeGroup = dialogView.findViewById<RadioGroup>(R.id.theme_radio_group)
        val btnDone = dialogView.findViewById<View>(R.id.btn_done)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        switchVibrate.isChecked = prefs.getBoolean("vibrate_on_keypress", true)
        switchSound.isChecked = prefs.getBoolean("sound_on_keypress", true)
        switchIndian.isChecked = prefs.getBoolean("calc_indian_format", true)
        switchScientific.isChecked = prefs.getBoolean("scientific_mode", false)

        val currentTheme = prefs.getString("calc_theme", "dark")
        when (currentTheme) {
            "saffron" -> dialogView.findViewById<RadioButton>(R.id.radio_theme_saffron).isChecked = true
            "white" -> dialogView.findViewById<RadioButton>(R.id.radio_theme_white).isChecked = true
            "blue" -> dialogView.findViewById<RadioButton>(R.id.radio_theme_blue).isChecked = true
            else -> dialogView.findViewById<RadioButton>(R.id.radio_theme_dark).isChecked = true
        }

        switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibrate_on_keypress", isChecked).apply()
        }

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound_on_keypress", isChecked).apply()
        }

        switchIndian.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("calc_indian_format", isChecked).apply()
            updateDisplay()
        }

        switchScientific.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("scientific_mode", isChecked).apply()
            updateScientificVisibility()
        }

        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radio_theme_saffron -> "saffron"
                R.id.radio_theme_white -> "white"
                R.id.radio_theme_blue -> "blue"
                else -> "dark"
            }
            prefs.edit().putString("calc_theme", newTheme).apply()
            applyCalculatorTheme()
        }

        btnDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun applyCalculatorTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val theme = prefs.getString("calc_theme", "dark")

        val isSaffron = theme == "saffron"
        val isWhite = theme == "white"
        val isBlue = theme == "blue"

        val displayColor = when {
            isSaffron -> Color.parseColor("#FFF5E1")
            isWhite -> Color.parseColor("#F5F5F5")
            isBlue -> Color.parseColor("#001F3F")
            else -> Color.parseColor("#B2D3C2")
        }
        val bgColor = when {
            isSaffron -> Color.parseColor("#FFF5E1")
            isWhite -> Color.parseColor("#F5F5F5")
            isBlue -> Color.parseColor("#001F3F")
            else -> Color.parseColor("#121212")
        }

        val digitColor = when {
            isSaffron -> Color.parseColor("#FFCC66")
            isWhite -> Color.parseColor("#FFFFFF")
            isBlue -> Color.parseColor("#0074D9")
            else -> Color.parseColor("#424242")
        }
        val operatorColor = when {
            isSaffron -> Color.parseColor("#FF9933")
            isWhite -> Color.parseColor("#D6D6D6")
            isBlue -> Color.parseColor("#0056b3")
            else -> Color.parseColor("#212121")
        }
        val gstColor = when {
            isSaffron -> Color.parseColor("#E65100")
            isWhite -> Color.parseColor("#1976D2")
            isBlue -> Color.parseColor("#00BCD4")
            else -> Color.parseColor("#4696C2")
        }
        val acColor = when {
            isSaffron -> Color.parseColor("#BF360C")
            isWhite -> Color.parseColor("#D32F2F")
            isBlue -> Color.parseColor("#FF4136")
            else -> Color.parseColor("#2BB07A")
        }

        val textColor = if (isWhite) Color.BLACK else Color.WHITE
        val displayTextMain = when {
            isWhite -> Color.BLACK
            isSaffron -> Color.parseColor("#3E2723")
            isBlue -> Color.WHITE
            else -> Color.parseColor("#222222")
        }
        val headerTextColor = when {
            isWhite || isSaffron -> Color.parseColor("#5D4037")
            else -> Color.WHITE
        }

        view.findViewById<View>(R.id.calculator_root)?.setBackgroundColor(bgColor)
        view.findViewById<View>(R.id.calculator_display_area)?.setBackgroundColor(displayColor)

        view.findViewById<TextView>(R.id.calculator_title)?.setTextColor(headerTextColor)
        view.findViewById<ImageButton>(R.id.settings_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)
        view.findViewById<ImageButton>(R.id.close_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)

        display.setTextColor(displayTextMain)
        memoryIndicator.setTextColor(displayTextMain)

        val historyColor = if (isBlue) Color.WHITE else if (isWhite) Color.BLACK else Color.parseColor("#333333")
        val historyExprColor = if (isBlue) Color.parseColor("#B2EBF2") else Color.GRAY
        historyAdapter.updateColors(historyColor, historyExprColor)

        stepsDisplay.setTextColor(when {
            isWhite || isSaffron -> Color.parseColor("#5D4037")
            isBlue -> Color.parseColor("#80DEEA")
            else -> Color.parseColor("#333333")
        })
        formulaDisplay.setTextColor(when {
            isWhite || isSaffron -> Color.parseColor("#8D6E63")
            isBlue -> Color.parseColor("#B2EBF2")
            else -> Color.parseColor("#555555")
        })

        // Update all buttons
        val digits = listOf(R.id.button_0, R.id.button_1, R.id.button_2, R.id.button_3, R.id.button_4, R.id.button_5, R.id.button_6, R.id.button_7, R.id.button_8, R.id.button_9, R.id.button_00, R.id.button_dot)
        val operators = listOf(R.id.button_add, R.id.button_subtract, R.id.button_multiply, R.id.button_divide, R.id.button_equals, R.id.button_plus_minus, R.id.button_percent, R.id.button_check, R.id.button_correct, R.id.button_gt, R.id.button_m_plus, R.id.button_m_minus, R.id.button_mrc, R.id.button_sqrt, R.id.button_square, R.id.button_pi, R.id.button_e, R.id.button_power, R.id.button_sin, R.id.button_cos, R.id.button_tan, R.id.button_log, R.id.button_ln)
        val gsts = listOf(R.id.button_gst_5, R.id.button_gst_12, R.id.button_gst_18, R.id.button_gst_minus)

        digits.forEach { id -> view.findViewById<MaterialButton>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(digitColor); it.setTextColor(textColor) } }
        operators.forEach { id -> view.findViewById<MaterialButton>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(operatorColor); it.setTextColor(textColor) } }
        gsts.forEach { id -> view.findViewById<MaterialButton>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(gstColor); it.setTextColor(Color.WHITE) } }
        view.findViewById<MaterialButton>(R.id.button_clear)?.let { it.backgroundTintList = ColorStateList.valueOf(acColor); it.setTextColor(Color.WHITE) }
    }

    private fun provideFeedback() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val vibrateOn = prefs.getBoolean("vibrate_on_keypress", true)
        val soundOn = prefs.getBoolean("sound_on_keypress", true)

        if (vibrateOn) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val intensity = prefs.getInt("vibration_intensity", 30)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(intensity.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(intensity.toLong())
            }
        }

        if (soundOn) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volume = prefs.getInt("sound_volume", 5) / 100f
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume)
        }
    }
}
