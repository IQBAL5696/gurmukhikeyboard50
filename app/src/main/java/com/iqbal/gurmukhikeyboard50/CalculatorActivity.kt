package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*
import kotlin.math.*

class CalculatorActivity : AppCompatActivity() {

    private lateinit var display: EditText
    private lateinit var stepsDisplay: TextView
    private lateinit var formulaDisplay: TextView
    private lateinit var memoryIndicator: TextView
    private lateinit var historyRecyclerViewMain: RecyclerView
    private lateinit var historyAdapterMain: HistoryAdapter
    private lateinit var scientificRow1: View
    private lateinit var scientificRow2: View
    private lateinit var scientificRow3: View
    private lateinit var scientificRow4: View
    private lateinit var updateHelper: UpdateHelper

    private var currentInput = ""
    private var lastValue = BigDecimal.ZERO
    private var operator = ""
    private var isNewOperation = true
    private var memoryValue = BigDecimal.ZERO
    private var grandTotal = BigDecimal.ZERO
    private var historyList = ArrayList<Calculation>()
    private var historyExpression = ""
    private var isSecondSet = false
    private var useRadians = false

    private val dfSimple = DecimalFormat("#.##########")

    private val historyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data?.getBooleanExtra("history_cleared", false) == true) {
                historyList.clear()
                grandTotal = BigDecimal.ZERO
            } else {
                @Suppress("UNCHECKED_CAST", "DEPRECATION")
                val updatedHistory = data?.getSerializableExtra("updated_history") as? ArrayList<Calculation>
                if (updatedHistory != null) {
                    historyList.clear()
                    historyList.addAll(if (updatedHistory.size > 7) updatedHistory.takeLast(7) else updatedHistory)
                    recalculateGrandTotal()
                }

                val selectedResult = data?.getStringExtra("selected_result")
                if (selectedResult != null) {
                    currentInput = selectedResult.replace(",", "")
                    lastValue = currentInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    operator = ""
                    isNewOperation = false
                    historyExpression = formatValue(lastValue)
                    updateDisplay()
                }
            }
            updateStepsDisplay()
            historyAdapterMain.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator)

        updateHelper = UpdateHelper(this)
        updateHelper.checkForUpdate()

        val root = findViewById<View>(R.id.calculator_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        display = findViewById(R.id.calculator_display)
        display.showSoftInputOnFocus = false

        stepsDisplay = findViewById(R.id.calculator_steps)
        formulaDisplay = findViewById(R.id.calculator_formula)
        memoryIndicator = findViewById(R.id.memory_indicator)
        scientificRow1 = findViewById(R.id.scientific_row_1)
        scientificRow2 = findViewById(R.id.scientific_row_2)
        scientificRow3 = findViewById(R.id.scientific_row_3)
        scientificRow4 = findViewById(R.id.scientific_row_4)

        historyRecyclerViewMain = findViewById(R.id.history_recycler_view_main)
        historyRecyclerViewMain.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        historyAdapterMain = HistoryAdapter(historyList) { selected ->
            currentInput = selected.result.replace(",", "")
            lastValue = currentInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
            operator = ""
            isNewOperation = false
            historyExpression = formatValue(lastValue)
            updateDisplay()
        }
        historyRecyclerViewMain.adapter = historyAdapterMain

        applyCalculatorTheme()
        setupEventListeners()
        setupScientificListeners()
        updateMemoryIndicator()
        updateScientificVisibility()
        updateDisplay()
        updateStepsDisplay()
        display.post { display.setSelection(display.text.length) }

        stepsDisplay.setOnClickListener { openHistory() }

        findViewById<View>(R.id.settings_calculator_button)?.setOnClickListener {
            showQuickSettingsDialog()
        }

        findViewById<View>(R.id.graph_calculator_button)?.setOnClickListener {
            showGraphDialog()
        }

        findViewById<View>(R.id.save_equation_button)?.setOnClickListener {
            showSaveEquationDialog()
        }

        findViewById<View>(R.id.library_calculator_button)?.setOnClickListener {
            showEquationLibraryDialog()
        }


        findViewById<View>(R.id.steps_calculator_button)?.setOnClickListener {
            showStepByStepDialog()
        }
    }

    private fun showGraphDialog() {
        // Sync currentInput with display text if needed
        val displayText = display.text.toString().replace(",", "")
        val currentExpr = if (currentInput.isNotEmpty()) currentInput else displayText.trim()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_graph, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .create()

        val graphView = dialogView.findViewById<GraphView>(R.id.graph_view)
        val equationText = dialogView.findViewById<TextView>(R.id.graph_equation_text)
        val closeBtn = dialogView.findViewById<View>(R.id.close_graph_button)
        val animControls = dialogView.findViewById<View>(R.id.animation_controls)
        val speedSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.speed_slider)
        val toggleBtn = dialogView.findViewById<ImageButton>(R.id.animation_toggle_button)
        val traceBtn = dialogView.findViewById<Button>(R.id.trace_toggle_button)
        val mode3dBtn = dialogView.findViewById<Button>(R.id.mode_3d_toggle_button)

        var isTraceOn = false
        traceBtn.setOnClickListener {
            isTraceOn = !isTraceOn
            graphView.setTraceEnabled(isTraceOn)
            traceBtn.text = if (isTraceOn) "Trace: On" else "Trace: Off"
            traceBtn.backgroundTintList = ColorStateList.valueOf(if (isTraceOn) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        }

        var is3DOn = currentExpr.lowercase().contains("3d(")
        if (is3DOn) {
            mode3dBtn.text = "3D: On"
            mode3dBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E91E63"))
        }

        mode3dBtn.setOnClickListener {
            is3DOn = !is3DOn
            graphView.setMode3D(is3DOn)
            mode3dBtn.text = if (is3DOn) "3D: On" else "3D: Off"
            mode3dBtn.backgroundTintList = ColorStateList.valueOf(if (is3DOn) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        }

        // Don't strip '=' if it's an equation like 'x = ...'
        val isEquation = currentExpr.contains("=") && (currentExpr.contains("x") || currentExpr.contains("y"))
        var displayExpr = if (isEquation) currentExpr else currentExpr.replace("=", "").trim()

        // If it's a full identity like (x-2)^2 = x^2-4x+4, extract RHS for plotting
        if (isEquation && displayExpr.count { it == '=' } == 1) {
            val parts = displayExpr.split("=")
            if (parts[0].trim() == "y") {
                displayExpr = parts[1].trim()
            } else if (parts[1].trim().contains("x") || parts[1].trim().contains("y")) {
                // Keep it, or if it's identity like LHS=RHS, take LHS
                displayExpr = parts[0].trim()
            }
        }

        // Strip wrappers for graph text display
        if (displayExpr.startsWith("result(")) displayExpr = displayExpr.substring(7, displayExpr.length - 1)
        if (displayExpr.startsWith("expand(")) displayExpr = displayExpr.substring(7, displayExpr.length - 1)
        if (displayExpr.startsWith("rule(")) displayExpr = displayExpr.substring(5, displayExpr.length - 1)
        if (displayExpr.startsWith("factor(")) displayExpr = displayExpr.substring(7, displayExpr.length - 1)
        if (displayExpr.startsWith("check(")) displayExpr = displayExpr.substring(6, displayExpr.length - 1)
        if (displayExpr.startsWith("vertexForm(")) displayExpr = displayExpr.substring(11, displayExpr.length - 1)
        if (displayExpr.startsWith("producttosum(")) displayExpr = displayExpr.substring(13, displayExpr.length - 1)
        if (displayExpr.startsWith("powcomplex(")) displayExpr = displayExpr.substring(11, displayExpr.length - 1)
        if (displayExpr.startsWith("opposite(")) displayExpr = displayExpr.substring(9, displayExpr.length - 1)
        if (displayExpr.startsWith("adjacent(")) displayExpr = displayExpr.substring(9, displayExpr.length - 1)
        if (displayExpr.startsWith("doubleAngle(")) displayExpr = displayExpr.substring(12, displayExpr.length - 1)
        if (displayExpr.startsWith("tripleAngle(")) displayExpr = displayExpr.substring(12, displayExpr.length - 1)
        if (displayExpr.startsWith("halfAngle(")) displayExpr = displayExpr.substring(10, displayExpr.length - 1)
        if (displayExpr.startsWith("ratio(")) displayExpr = displayExpr.substring(6, displayExpr.length - 1)
        if (displayExpr.startsWith("SineRule(")) displayExpr = displayExpr.substring(9, displayExpr.length - 1)
        if (displayExpr.startsWith("CosineRule(")) displayExpr = displayExpr.substring(11, displayExpr.length - 1)
        if (displayExpr.startsWith("c²=")) displayExpr = displayExpr.substring(3, displayExpr.length - 1)

        // Special check for binomial geometry
        val isBinomial = currentExpr.lowercase().contains("sq_bin_") || currentExpr.contains("(a+b)^2=a^2+2ab+b^2")
        val graphEq = if (isBinomial) {
            if (currentExpr.contains("(a+b)^2=a^2+2ab+b^2")) {
                "sq_bin_plus(3, 2)" // Default values for identity visual
            } else {
                val start = currentExpr.lowercase().indexOf("sq_bin_")
                val end = currentExpr.indexOf(")", start) + 1
                if (end > start) currentExpr.substring(start, end) else displayExpr
            }
        } else {
            displayExpr
        }

        equationText.text = if (displayExpr.isNotEmpty()) {
            if (isEquation) displayExpr else "y = $displayExpr"
        } else {
            "Type an equation (e.g. sin(x)) or shape (e.g. pentagon(5))"
        }

        if (graphEq.isNotEmpty()) {
            graphView.setEquation(graphEq, useRadians)

            if (displayExpr.lowercase().contains("move")) {
                animControls.visibility = View.VISIBLE
                speedSlider.addOnChangeListener { _, value, _ ->
                    graphView.setRotationSpeed(value)
                }
                toggleBtn.setOnClickListener {
                    val isRunning = graphView.toggleAnimation()
                    toggleBtn.setImageResource(if (isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                }
            }
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        updateHelper.handleOnResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateHelper.handleActivityResult(requestCode, resultCode, data)
    }

    private fun formatValue(value: BigDecimal): String {
        if (value.isInfinite()) return "Infinity"
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val valueStr = value.stripTrailingZeros().toPlainString()
        return if (prefs.getBoolean("calc_indian_format", true)) {
            CalculatorHelper.formatIndianStyleString(valueStr)
        } else {
            CalculatorHelper.formatInternationalStyleString(valueStr)
        }
    }

    private fun showQuickSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_calculator_settings, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val switchVibrate = dialogView.findViewById<SwitchMaterial>(R.id.switch_vibrate)
        val switchSound = dialogView.findViewById<SwitchMaterial>(R.id.switch_sound)
        val switchIndian = dialogView.findViewById<SwitchMaterial>(R.id.switch_indian_format)
        val switchScientific = dialogView.findViewById<SwitchMaterial>(R.id.switch_scientific_mode)
        val themeGroup = dialogView.findViewById<RadioGroup>(R.id.theme_radio_group)
        val btnDone = dialogView.findViewById<View>(R.id.btn_done)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        switchVibrate.isChecked = prefs.getBoolean("vibrate_on_keypress", true)
        switchSound.isChecked = prefs.getBoolean("sound_on_keypress", true)
        switchIndian.isChecked = prefs.getBoolean("calc_indian_format", true)
        switchScientific.isChecked = prefs.getBoolean("scientific_mode", false)

        val currentTheme = prefs.getString("calc_theme", "dark")
        when (currentTheme) {
            "blue" -> dialogView.findViewById<RadioButton>(R.id.radio_theme_blue).isChecked = true
            "green" -> dialogView.findViewById<RadioButton>(R.id.radio_theme_green).isChecked = true
            "purple" -> dialogView.findViewById<RadioButton>(R.id.radio_theme_purple).isChecked = true
            "orange" -> dialogView.findViewById<RadioButton>(R.id.radio_theme_orange).isChecked = true
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
                R.id.radio_theme_blue -> "blue"
                R.id.radio_theme_green -> "green"
                R.id.radio_theme_purple -> "purple"
                R.id.radio_theme_orange -> "orange"
                else -> "dark"
            }
            prefs.edit().putString("calc_theme", newTheme).apply()
            applyCalculatorTheme()
        }

        btnDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateScientificVisibility() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isEnabled = prefs.getBoolean("scientific_mode", false)
        scientificRow1.visibility = if (isEnabled) View.VISIBLE else View.GONE
        scientificRow2.visibility = if (isEnabled) View.VISIBLE else View.GONE
        scientificRow3.visibility = if (isEnabled) View.VISIBLE else View.GONE
        scientificRow4.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }

    private fun setupScientificListeners() {
        findViewById<Button>(R.id.button_deg_rad)?.let { btn ->
            val savedMode = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("calc_use_radians", false)
            useRadians = savedMode
            btn.text = if (useRadians) "RAD" else "DEG"
            btn.setOnClickListener {
                provideFeedback()
                useRadians = !useRadians
                btn.text = if (useRadians) "RAD" else "DEG"
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("calc_use_radians", useRadians).apply()
            }
        }

        findViewById<View>(R.id.button_2nd)?.setOnClickListener {
            provideFeedback()
            isSecondSet = !isSecondSet
            val color = if (isSecondSet) Color.parseColor("#4696C2") else Color.parseColor("#212121")
            (it as? Button)?.backgroundTintList = ColorStateList.valueOf(color)

            findViewById<Button>(R.id.button_sin)?.text = if (isSecondSet) "asin" else "sin"
            findViewById<Button>(R.id.button_cos)?.text = if (isSecondSet) "acos" else "cos"
            findViewById<Button>(R.id.button_tan)?.text = if (isSecondSet) "atan" else "tan"
        }

        findViewById<View>(R.id.button_parenthesis_open)?.setOnClickListener { provideFeedback(); appendCharacter("(") }
        findViewById<View>(R.id.button_parenthesis_close)?.setOnClickListener { provideFeedback(); appendCharacter(")") }
        findViewById<View>(R.id.button_sqrt)?.setOnClickListener { provideFeedback(); appendScientificFunction("sqrt") }
        findViewById<View>(R.id.button_square)?.setOnClickListener { applyUnary { it.multiply(it) } }
        findViewById<View>(R.id.button_pi)?.setOnClickListener { provideFeedback(); currentInput = Math.PI.toString(); updateDisplay(); display.setSelection(display.text.length) }
        findViewById<View>(R.id.button_e)?.setOnClickListener { provideFeedback(); currentInput = Math.E.toString(); updateDisplay(); display.setSelection(display.text.length) }
        findViewById<View>(R.id.button_power)?.setOnClickListener { provideFeedback(); setOperator("^") }

        findViewById<View>(R.id.button_sin)?.setOnClickListener { provideFeedback(); appendScientificFunction(if (isSecondSet) "asin" else "sin") }
        findViewById<View>(R.id.button_cos)?.setOnClickListener { provideFeedback(); appendScientificFunction(if (isSecondSet) "acos" else "cos") }
        findViewById<View>(R.id.button_tan)?.setOnClickListener { provideFeedback(); appendScientificFunction(if (isSecondSet) "atan" else "tan") }
        findViewById<View>(R.id.button_log)?.setOnClickListener { provideFeedback(); appendScientificFunction("log") }
        findViewById<View>(R.id.button_ln)?.setOnClickListener { provideFeedback(); appendScientificFunction("ln") }

        findViewById<View>(R.id.button_factorial)?.setOnClickListener { provideFeedback(); appendCharacter("!") }
        findViewById<View>(R.id.button_sinh)?.setOnClickListener { provideFeedback(); appendScientificFunction("sinh") }
        findViewById<View>(R.id.button_cosh)?.setOnClickListener { provideFeedback(); appendScientificFunction("cosh") }
        findViewById<View>(R.id.button_tanh)?.setOnClickListener { provideFeedback(); appendScientificFunction("tanh") }

        findViewById<View>(R.id.button_layout)?.setOnClickListener { provideFeedback(); showLayoutMenu(it) }
        findViewById<View>(R.id.button_geometry)?.setOnClickListener { provideFeedback(); showGeometryMenuDialog() }

        findViewById<View>(R.id.button_x_var)?.setOnClickListener { provideFeedback(); appendCharacter("x") }
        findViewById<View>(R.id.button_y_var)?.setOnClickListener { provideFeedback(); appendCharacter("y") }
        findViewById<View>(R.id.button_solve)?.setOnClickListener { provideFeedback(); appendCharacter("solve(") }
        findViewById<View>(R.id.button_3d_mode)?.setOnClickListener { provideFeedback(); appendCharacter("3d(") }
        findViewById<View>(R.id.button_polynomial)?.setOnClickListener { provideFeedback(); showPolynomialMenu(it) }
        findViewById<View>(R.id.button_spiral)?.setOnClickListener { provideFeedback(); appendCharacter("spiral(") }
        findViewById<View>(R.id.button_nested)?.setOnClickListener { provideFeedback(); appendCharacter("nested(") }
        findViewById<View>(R.id.button_animation)?.setOnClickListener { provideFeedback(); showAnimationMenu(it) }
        findViewById<View>(R.id.button_comma)?.setOnClickListener { provideFeedback(); appendCharacter(",") }
        findViewById<View>(R.id.button_save_scientific)?.setOnClickListener { provideFeedback(); showSaveEquationDialog() }
        findViewById<View>(R.id.button_ans)?.setOnClickListener { provideFeedback(); appendCharacter(lastValue.stripTrailingZeros().toPlainString()) }
    }

    private fun showPolynomialMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, view)
        popup.menu.add("Quadratic (x²)")
        popup.menu.add("Cubic (x³)")
        popup.menu.add("Quartic (x⁴)")
        popup.menu.add("Quintic (x⁵)")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Quadratic (x²)" -> appendCharacter("quad(")
                "Cubic (x³)" -> appendCharacter("cubic(")
                "Quartic (x⁴)" -> appendCharacter("quartic(")
                "Quintic (x⁵)" -> appendCharacter("quintic(")
            }
            true
        }
        popup.show()
    }

    private fun showGeometryMenuDialog() {
        val groups = arrayOf("Algebra & Sequences", "Trigonometry", "2D Shapes", "3D Shapes", "Coordinate & Analytical", "Advanced Triangles", "Calculus & Analysis", "Statistics & Probability", "Finance & Business", "Higher Math & Engineering", "Advanced Physics & Quantum", "College Physics & Chemistry")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Formula Library")

        // Setup Search UI
        val searchInput = EditText(this).apply {
            hint = "Search formulas..."
            setPadding(40, 20, 40, 20)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(searchInput)
        }
        builder.setView(container)

        // Pre-create the dialog so we can access its ListView
        val dialog = builder.setItems(groups) { _, which ->
            when (which) {
                0 -> showSubCategoryDialog("Algebra & Sequences", arrayOf("FOIL (ax+b)(cx+d)", "3D FOIL (x+a)(y+b)", "(a+b)² Expansion", "(a-b)² Expansion", "Exponent Mult Rule", "Fractional Exponent", "Difference of Squares", "Difference of Cubes", "Arithmetic Sequence", "Fibonacci Number", "Factorial (n!)", "Combination (nCr)", "Binomial Probability", "Completing the Square", "Perfect Square Trinomial", "Prime Number Check", "Pythagorean Triple", "Cubic Equation (Cardano)"))
                1 -> showSubCategoryDialog("Trigonometry", arrayOf("2cosAcosB Identity", "De Moivre's Theorem", "Find Opposite Side", "Find Adjacent Side", "Double Angle sin(2A)", "Double Angle cos(2A)", "Double Angle tan(2A)", "Triple Angle sin(3A)", "Half Angle Formulas", "Law of Sines", "Law of Cosines"))
                2 -> showSubCategoryDialog("2D Shapes", arrayOf("Rectangle", "Square", "Triangle", "Equilateral Triangle", "Isosceles Triangle", "Annulus", "Circle", "Ellipse", "Trapezoid", "Tangential Quadrilateral", "Parallelogram", "Rhombus", "Kite", "Hexagon", "Octagon", "Regular Polygon", "Polygon (Shoelace)"))
                3 -> showSubCategoryDialog("3D Shapes", arrayOf("Rectangular Prism", "Triangular Prism", "Pentagonal Prism", "Hexagonal Prism", "Cube", "Cylinder", "Sphere", "Hemisphere", "Cone", "Ellipsoid", "Square Pyramid", "Triangular Pyramid", "Pentagonal Pyramid", "Hexagonal Pyramid", "Frustum of Pyramid", "Frustum of Cone", "Solid of Revolution"))
                4 -> showSubCategoryDialog("Coordinate & Analytical", arrayOf("Distance", "Midpoint", "Centroid", "Circumcenter", "Orthocenter", "Slope", "Slope-Intercept Form", "Rate of Change", "Rotations", "Circle Equation", "Point to Line Distance", "Asymptotes", "Line Equation", "Line Relationships", "Intersection", "Parabola Analysis"))
                5 -> showSubCategoryDialog("Advanced Triangles", arrayOf("Pythagorean Theorem", "Heron's Formula"))
                6 -> showSubCategoryDialog("Calculus & Analysis", arrayOf("Arc Length", "Tangent Line", "Asymptotes", "Derivative (f'(x))", "Integral (int)", "Newton's Method", "Mean Value Theorem"))
                7 -> showSubCategoryDialog("Statistics & Probability", arrayOf("Arithmetic Mean", "Average Deviation", "Standard Deviation", "Variance", "Root Mean Square", "Binomial Distribution", "Poisson Distribution", "Normal Distribution", "Coin Toss Probability", "Z-Score", "Z to P-Value", "Bayes Theorem"))
                8 -> showSubCategoryDialog("Finance & Business", arrayOf("Total Cost (units * price)", "Simple Interest", "Compound Interest", "Daily Compound Interest", "Selling Price", "Exponential Growth", "Exponential Decay", "Discount Formula"))
                9 -> showSubCategoryDialog("Higher Math & Engineering", arrayOf("Distance (d=rt)", "Ohm's Law", "DC Power", "Resistors (Parallel)", "Charge & Energy", "Celsius to Fahrenheit", "Fahrenheit to Celsius", "Keplers Third Law", "Vis-Viva Orbit Speed", "Rocket Equation (Delta-v)", "Schwarzschild Radius", "Time Dilation", "Binary to Decimal", "Decimal to Binary"))
                10 -> showSubCategoryDialog("Advanced Physics & Quantum", arrayOf("De Broglie Wavelength", "Photon Energy (E=hf)", "Heisenberg Uncertainty", "Particle in a Box", "Boltzmann Entropy", "Boltzmann Factor", "Gravitational Time Dilation", "Lorentz Factor"))
                11 -> showSubCategoryDialog("College Physics & Chemistry", arrayOf("Newton's 2nd Law (F=ma)", "Ideal Gas (Find P)", "Ideal Gas (Find V)", "Gibbs Free Energy", "Nernst Equation", "Arrhenius Equation"))
            }
        }.create()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                if (query.isNotEmpty()) {
                    val formulaMap = mapOf(
                        "Algebra" to arrayOf("FOIL (ax+b)(cx+d)", "3D FOIL (x+a)(y+b)", "(a+b)² Expansion", "(a-b)² Expansion", "Exponent Mult Rule", "Fractional Exponent", "Difference of Squares", "Difference of Cubes", "Arithmetic Sequence", "Fibonacci Number", "Factorial (n!)", "Combination (nCr)", "Binomial Probability", "Completing the Square", "Perfect Square Trinomial", "Prime Number Check", "Pythagorean Triple"),
                        "Trigonometry" to arrayOf("2cosAcosB Identity", "De Moivre's Theorem", "Find Opposite Side", "Find Adjacent Side", "Double Angle sin(2A)", "Double Angle cos(2A)", "Double Angle tan(2A)", "Triple Angle sin(3A)", "Half Angle Formulas", "Law of Sines", "Law of Cosines"),
                        "2D Shapes" to arrayOf("Rectangle", "Square", "Triangle", "Equilateral Triangle", "Isosceles Triangle", "Annulus", "Circle", "Ellipse", "Trapezoid", "Tangential Quadrilateral", "Parallelogram", "Rhombus", "Kite", "Hexagon", "Octagon", "Regular Polygon", "Polygon (Shoelace)"),
                        "3D Shapes" to arrayOf("Rectangular Prism", "Triangular Prism", "Pentagonal Prism", "Hexagonal Prism", "Cube", "Cylinder", "Sphere", "Hemisphere", "Cone", "Ellipsoid", "Square Pyramid", "Triangular Pyramid", "Pentagonal Pyramid", "Hexagonal Pyramid", "Frustum of Pyramid", "Frustum of Cone", "Solid of Revolution"),
                        "Coordinate" to arrayOf("Distance", "Midpoint", "Centroid", "Circumcenter", "Orthocenter", "Slope", "Slope-Intercept Form", "Rate of Change", "Rotations", "Circle Equation", "Point to Line Distance", "Asymptotes", "Line Equation", "Line Relationships", "Intersection", "Parabola Analysis"),
                        "Calculus" to arrayOf("Arc Length", "Tangent Line", "Asymptotes", "Derivative (f'(x))", "Integral (int)", "Newton's Method", "Mean Value Theorem"),
                        "Statistics" to arrayOf("Arithmetic Mean", "Average Deviation", "Standard Deviation", "Variance", "Root Mean Square", "Binomial Distribution", "Poisson Distribution", "Normal Distribution", "Coin Toss Probability", "Z-Score", "Z to P-Value", "Bayes Theorem"),
                        "Finance" to arrayOf("Total Cost (units * price)", "Simple Interest", "Compound Interest", "Daily Compound Interest", "Selling Price", "Exponential Growth", "Exponential Decay", "Discount Formula"),
                        "Higher Math" to arrayOf("Distance (d=rt)", "Ohm's Law", "DC Power", "Resistors (Parallel)", "Charge & Energy", "Celsius to Fahrenheit", "Fahrenheit to Celsius", "Keplers Third Law", "Vis-Viva Orbit Speed", "Rocket Equation (Delta-v)", "Schwarzschild Radius", "Time Dilation", "Binary to Decimal", "Decimal to Binary"),
                        "Quantum" to arrayOf("De Broglie Wavelength", "Photon Energy (E=hf)", "Heisenberg Uncertainty", "Particle in a Box", "Boltzmann Entropy", "Boltzmann Factor", "Gravitational Time Dilation", "Lorentz Factor"),
                        "Physics/Chemistry" to arrayOf("Newton's 2nd Law (F=ma)", "Ideal Gas (Find P)", "Ideal Gas (Find V)", "Gibbs Free Energy", "Nernst Equation", "Arrhenius Equation")
                    )

                    val filtered = mutableListOf<String>()
                    for ((cat, items) in formulaMap) {
                        for (item in items) {
                            if (item.lowercase().contains(query)) filtered.add(item)
                        }
                    }

                    if (filtered.isNotEmpty() && query.length > 1) {
                        // Show search results in a new sub-dialog for instant access
                        val results = filtered.toTypedArray()
                        dialog.dismiss()
                        AlertDialog.Builder(this@CalculatorActivity)
                            .setTitle("Search Results")
                            .setItems(results) { _, which ->
                                showShapeFormulaDialog(results[which])
                            }
                            .setOnCancelListener { dialog.show() }
                            .show()
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog.show()
    }

    private fun showSubCategoryDialog(title: String, items: Array<String>) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                showShapeFormulaDialog(items[which])
            }
            .show()
    }

    private fun showShapeFormulaDialog(shape: String) {
        val formulas = when (shape) {
            "FOIL (ax+b)(cx+d)" -> arrayOf("Expand (foil(a, b, c, d))")
            "3D FOIL (x+a)(y+b)" -> arrayOf("3D Plot (foil3d(a, b))")
            "(a+b)² Expansion" -> arrayOf("Expand (sq_bin_plus(a, b))")
            "(a-b)² Expansion" -> arrayOf("Expand (sq_bin_minus(a, b))")
            "Exponent Mult Rule" -> arrayOf("Rule (exponent_rule_mult(a, m, n))")
            "Fractional Exponent" -> arrayOf("Result (fractional_exp(a, n, d))")
            "Difference of Squares" -> arrayOf("Factor (diff_sq(a, b))")
            "Difference of Cubes" -> arrayOf("Factor (diff_cub(a, b))")
            "Arithmetic Sequence" -> arrayOf("Term n (arith_seq(a, d, n))")
            "Fibonacci Number" -> arrayOf("Result (fib(n))")
            "Factorial (n!)" -> arrayOf("Result (fact(n))")
            "Combination (nCr)" -> arrayOf("Result (comb(n, r))")
            "Binomial Probability" -> arrayOf("P(X=k) (binom_prob(n, k, p))")
            "Completing the Square" -> arrayOf("Vertex Form (completing_sq(a, b, c))")
            "Perfect Square Trinomial" -> arrayOf("Factor ((a+b)^2 = a^2+2ab+b^2)")
            "Prime Number Check" -> arrayOf("Check (is_prime(n))")
            "Pythagorean Triple" -> arrayOf("Check (pyth_triple(a, b, c))")
            "Cubic Equation (Cardano)" -> arrayOf("Solve (solve(x^3 + px + q = 0))")

            "2cosAcosB Identity" -> arrayOf("ProductToSum (cosacosb(A, B))")
            "De Moivre's Theorem" -> arrayOf("PowComplex (demoivre(r, theta, n))")
            "Find Opposite Side" -> arrayOf("Opposite (right_tri_find_opp(angle, hyp))")
            "Find Adjacent Side" -> arrayOf("Adjacent (right_tri_find_adj(angle, hyp))")
            "Double Angle sin(2A)" -> arrayOf("DoubleAngle (sin2a(A))")
            "Double Angle cos(2A)" -> arrayOf("DoubleAngle (cos2a(A))")
            "Double Angle tan(2A)" -> arrayOf("DoubleAngle (tan2a(A))")
            "Triple Angle sin(3A)" -> arrayOf("TripleAngle (sin3a(A))")
            "Half Angle Formulas" -> arrayOf("HalfAngle (half_sin(A))", "HalfAngle (half_cos(A))")

            "Rectangle" -> arrayOf("Area (l * b)", "Perimeter (2 * (l + b))", "Diagonal (sqrt(l^2 + b^2))")
            "Square" -> arrayOf("Area (a^2)", "Perimeter (4 * a)", "Diagonal (a * sqrt(2))")
            "Triangle" -> arrayOf("Area (1/2 * b * h)", "Perimeter (a + b + c)")
            "Equilateral Triangle" -> arrayOf("Area (sqrt(3)/4 * a^2)", "Perimeter (3 * a)", "Altitude (sqrt(3)/2 * a)")
            "Isosceles Triangle" -> arrayOf("Area (1/2 * b * sqrt(a^2 - b^2/4))", "Perimeter (2*a + b)")
            "Annulus" -> arrayOf("Area (π * (R^2 - r^2))")
            "Circle" -> arrayOf("Area (π * r^2)", "Circumference (2 * π * r)", "Diameter (2 * r)")
            "Ellipse" -> arrayOf("Area (π * r1 * r2)", "Perimeter (2 * π * sqrt((r1^2 + r2^2) / 2))")
            "Trapezoid" -> arrayOf("Area (1/2 * (b1 + b2) * h)", "Perimeter (a + b + c + d)")
            "Tangential Quadrilateral" -> arrayOf("Area (sqrt(a*b*c*d))", "Area (r * s)")
            "Parallelogram" -> arrayOf("Area (b * h)", "Perimeter (2 * (a + b))")
            "Rhombus" -> arrayOf("Area (1/2 * d1 * d2)", "Perimeter (4 * a)")
            "Kite" -> arrayOf("Area (1/2 * d1 * d2)", "Perimeter (2 * (a + b))")
            "Hexagon" -> arrayOf("Area (3/2 * sqrt(3) * a^2)", "Perimeter (6 * a)")
            "Octagon" -> arrayOf("Area (2 * a^2 * (1 + sqrt(2)))", "Perimeter (8 * a)")
            "Regular Polygon" -> arrayOf("Area (1/2 * n * sin(360/n) * s^2)", "Interior Angle ((n-2)*180 / n)", "Sum of Angles ((n-2)*180)", "Area (polygon(diameter, sides))")

            "Rectangular Prism" -> arrayOf("Base Area (b * l)", "Surface Area (2 * (b*l + l*h + h*b))", "Volume (l * b * h)", "3D Graph (prism(l, h, w))")
            "Triangular Prism" -> arrayOf("Base Area (1/2 * a * b)", "Surface Area (a*b + 3*b*h)", "Volume (1/2 * a*b*h)", "3D Graph (3d(prism(a, h, b)))")
            "Pentagonal Prism" -> arrayOf("Base Area (5/2 * a * b)", "Surface Area (5*a*b + 5*b*h)", "Volume (5/2 * a*b*h)")
            "Hexagonal Prism" -> arrayOf("Base Area (3 * a * b)", "Surface Area (6*a*b + 6*b*h)", "Volume (3 * a*b*h)")
            "Cube" -> arrayOf("Surface Area (6 * a^2)", "LSA (4 * a^2)", "Volume (a^3)", "3D Graph (cube(a))")
            "Cylinder" -> arrayOf("CSA (2 * π * r * h)", "TSA (2 * π * r * (r + h))", "Volume (π * r^2 * h)", "3D Graph (cylinder3d(r, h))")
            "Sphere" -> arrayOf("Surface Area (4 * π * r^2)", "Diameter (2 * r)", "Volume (4/3 * π * r^3)", "3D Graph (3d(sphere(r)))")
            "Hemisphere" -> arrayOf("CSA (2 * π * r^2)", "TSA (3 * π * r^2)", "Volume (2/3 * π * r^3)")
            "Cone" -> arrayOf("CSA (π * r * l)", "TSA (π * r * (l + r))", "Volume (1/3 * π * r^2 * h)", "Slant Height (sqrt(r^2 + h^2))", "3D Graph (cone3d(r, h))")
            "Ellipsoid" -> arrayOf("Volume (4/3 * π * r1 * r2 * r3)")
            "Square Pyramid" -> arrayOf("LSA (2 * b * s)", "TSA (b^2 + 2 * b * s)", "Volume (1/3 * b^2 * h)", "3D Graph (pyramid(b, h, b))")
            "Triangular Pyramid" -> arrayOf("LSA (3/2 * b * s)", "TSA (AreaBase + LSA)", "Volume (1/3 * AreaBase * h)", "3D Graph (3d(pyramid(b, h, b)))")

            "Pentagonal Pyramid" -> arrayOf("LSA (5/2 * b * s)", "TSA (AreaBase + LSA)", "Volume (1/3 * AreaBase * h)")
            "Hexagonal Pyramid" -> arrayOf("LSA (3 * b * s)", "TSA (AreaBase + LSA)", "Volume (1/3 * AreaBase * h)")
            "Frustum of Pyramid" -> arrayOf("Volume (frustum_pyramid(a, b, h))")
            "Frustum of Cone" -> arrayOf("Volume (frustum_cone(r1, r2, h))")
            "Right Prism" -> arrayOf("CSA (Perimeter * h)", "TSA (CSA + 2 * AreaOneEnd)", "Volume (AreaBase * h)")
            "Right Pyramid" -> arrayOf("CSA (1/2 * Perimeter * slant_h)", "TSA (CSA + AreaBase)", "Volume (1/3 * AreaBase * h)")
            "Rectangular Prism" -> arrayOf("Volume (l * w * h)", "Surface Area (2*(l*w + l*h + w*h))")
            "Solid of Revolution" -> arrayOf("Disk Method (π * int(f(x)^2, a, b))")

            "Distance" -> arrayOf("Distance (distance(x1,y1,x2,y2))")
            "Midpoint" -> arrayOf("Midpoint (midpoint(x1,y1,x2,y2))")
            "Centroid" -> arrayOf("Centroid (centroid(x1,y1,x2,y2,x3,y3))")
            "Circumcenter" -> arrayOf("Circumcenter (circumcenter(x1,y1,x2,y2,x3,y3))")
            "Orthocenter" -> arrayOf("Orthocenter (orthocenter(x1,y1,x2,y2,x3,y3))")
            "Slope" -> arrayOf("Slope (slope(x1,y1,x2,y2))", "X-Intercept (set y=0)", "Y-Intercept (set x=0)", "Intercept Form (x/a + y/b = 1)")
            "Slope-Intercept Form" -> arrayOf("Equation (slope_intercept(m, c))")
            "Rate of Change" -> arrayOf("Slope (Δy / Δx)", "Average Rate ((f(b)-f(a))/(b-a))")
            "Circle Equation" -> arrayOf("Circle Eq ((x-h)^2 + (y-k)^2 = r^2)")
            "Point to Line Distance" -> arrayOf("Distance (abs(Ax0 + By0 + C) / sqrt(A^2 + B^2))")
            "Polygon (Shoelace)" -> arrayOf("Area (0.5 * abs(sum(xi*yi+1 - yi*xi+1)))")

            "Arithmetic Mean" -> arrayOf("Result (mean(a,b,c...))")
            "Geometric Mean" -> arrayOf("Result (geo_mean(a,b,c...))")
            "Harmonic Mean" -> arrayOf("Result (har_mean(a,b,c...))")
            "Standard Deviation" -> arrayOf("Result (std_dev(a,b,c...))")
            "Variance" -> arrayOf("Result (variance(a,b,c...))")
            "Root Mean Square" -> arrayOf("Result (rms(a,b,c...))")
            "Normal Distribution" -> arrayOf("PDF (normal_dist(x, mean, std))")
            "Poisson Distribution" -> arrayOf("P(X=k) (poisson(k, lambda))")
            "Coin Toss Probability" -> arrayOf("P(k heads) (coin_toss(n, k))")
            "Z-Score" -> arrayOf("Result (z_score(x, mean, std))")
            "Z to P-Value" -> arrayOf("Approx P (z_to_p(z))")
            "Total Cost (units * price)" -> arrayOf("Result (total_cost(units, price))")
            "Compound Interest" -> arrayOf("Amount A (compound_int(P, r, t, n))")
            "Simple Interest" -> arrayOf("Interest (simple_int(P, r, t))")
            "Selling Price" -> arrayOf("SP (selling_price(CP, profit))")
            "Discount Formula" -> arrayOf("Final Price (discount(price, rate))")
            "Exponential Growth" -> arrayOf("Amount (growth(P, r, t))")

            "Pythagorean Theorem" -> arrayOf("Hypotenuse (sqrt(Base^2 + Perpendicular^2))", "Base (sqrt(Hypotenuse^2 - Perpendicular^2))", "Perpendicular (sqrt(Hypotenuse^2 - Base^2))")
            "Heron's Formula" -> arrayOf("Area (sqrt(s(s-a)(s-b)(s-c)))")
            "Law of Sines" -> arrayOf("SineRule (sine_rule(a, A, b, B))")
            "Law of Cosines" -> arrayOf("CosineRule (cos_rule(a, b, c, C))")

            "Arc Length" -> arrayOf("L (int(sqrt(1 + f'(x)²), a, b))")
            "Derivative (f'(x))" -> arrayOf("At point x (derivative(f(x), x))")
            "Integral (int)" -> arrayOf("Definite (integral(f(x), a, b))")
            "Newton's Method" -> arrayOf("Iteration (newton(f(x), x0))")
            "Mean Value Theorem" -> arrayOf("Result (f'(c) = (f(b)-f(a))/(b-a))")
            "Haversine (Earth Dist)" -> arrayOf("d (2R * asin(sqrt(sin²(dphi/2) + cos(phi1)*cos(phi2)*sin²(dlambda/2))))")

            "Keplers Third Law" -> arrayOf("T (sqrt(4 * pi^2 * a^3 / (G * (M + m))))")
            "Vis-Viva Orbit Speed" -> arrayOf("v (sqrt(mu * (2/r - 1/a)))")
            "Rocket Equation (Delta-v)" -> arrayOf("Dv (ve * ln(m0 / mf))")
            "Small-Angle Formula" -> arrayOf("Size D (alpha * d / 206265)")
            "Rayleigh Criterion" -> arrayOf("Resolution (1.22 * lambda / D)")
            "Schwarzschild Radius" -> arrayOf("Rs (2 * G * M / c^2)")
            "Hubble Law" -> arrayOf("Velocity v (H0 * d)")
            "Escape Velocity" -> arrayOf("ve (sqrt(2 * G * M / R))")
            "Circular Orbit Speed" -> arrayOf("vc (sqrt(G * M / R))")
            "Gravitational Force" -> arrayOf("F (G * M1 * M2 / r^2)")
            "Specific Impulse" -> arrayOf("Isp (ve / g0)")
            "Thrust Equation" -> arrayOf("Thrust (mdot * ve)")
            "Time Dilation" -> arrayOf("t' (t / sqrt(1 - v^2 / c^2))")
            "Light Travel Time" -> arrayOf("t (distance / c)")
            "Light-Year to KM" -> arrayOf("Distance (years * c * 31536000)")
            "Redshift Velocity" -> arrayOf("v (c * ((z+1)^2 - 1) / ((z+1)^2 + 1))")

            "Distance (d=rt)" -> arrayOf("d (dist_rt(r, t))")
            "Binary to Decimal" -> arrayOf("Convert (bin_to_dec(string))")
            "Decimal to Binary" -> arrayOf("Convert (dec_to_bin(n))")
            "Inverse Matrix" -> arrayOf("2x2 Inverse (inv2(a,b,c,d))")

            "Ohm's Law" -> arrayOf("Voltage (ohm_v(I, R))", "Current (ohm_i(V, R))", "Resistance (ohm_r(V, I))")
            "Resistors (Parallel)" -> arrayOf("Req (res_parallel(r1, r2, ...))")
            "Celsius to Fahrenheit" -> arrayOf("F (c_to_f(C))")
            "Fahrenheit to Celsius" -> arrayOf("C (f_to_c(F))")
            "DC Power" -> arrayOf("Power (V * I)", "Power (I^2 * R)", "Power (V^2 / R)")
            "Resistors (Series/Parallel)" -> arrayOf("Series (R1 + R2 + R3)", "Parallel (1 / (1/R1 + 1/R2))", "Parallel 3 (1 / (1/R1 + 1/R2 + 1/R3))")
            "Charge & Energy" -> arrayOf("Charge (I * t)", "Energy (V * Q)", "Energy (P * t)")

            "Circle Angles" -> arrayOf("Central Angle (s / r)", "Arc Length (θ * r)", "Radius (s / θ)")
            "Double Angle Identities" -> arrayOf("sin(2A) (2 * sin(A) * cos(A))", "cos(2A) (cos(A)^2 - sin(A)^2)", "tan(2A) (2 * tan(A) / (1 - tan(A)^2))")
            "Triple Angle Identities" -> arrayOf("sin(3A) (3 * sin(A) - 4 * sin(A)^3)", "cos(3A) (4 * cos(A)^3 - 3 * cos(A))", "tan(3A) ((3 * tan(A) - tan(A)^3) / (1 - 3 * tan(A)^2))")
            "Half Angle Identities" -> arrayOf("sin(A/2) (sqrt((1 - cos(A)) / 2))", "cos(A/2) (sqrt((1 + cos(A)) / 2))", "tan(A/2) ((1 - cos(A)) / sin(A))")
            "Angle Conversions" -> arrayOf("Radians to Degrees (rad * 180 / π)", "Degrees to Radians (deg * π / 180)")
            "Rotations" -> arrayOf("Rotation 90° ((-y, x))", "Rotation 180° ((-x, -y))", "Rotation 270° ((y, -x))")
            "Asymptotes" -> arrayOf("General Analysis (asymptotes(num/den))", "Horizontal (HA: lim x->inf f(x))", "Vertical (VA: den(x)=0)", "Slant / Oblique (Slant: num/den division)")
            "Line Equation" -> arrayOf("Point-Slope / Gradient (y - y1 = m(x - x1))", "Slope-Intercept (y = mx + c)", "Two-Point (y-y1 = ((y2-y1)/(x2-x1))*(x-x1))")
            "Line Relationships" -> arrayOf("Perpendicular (m1 * m2 = -1)", "Parallel (m1 = m2)", "Parallel Line (y - y1 = m(x - x1))")
            "Intersection" -> arrayOf("Point of Intersection ([(b1c2-b2c1)/(a1b2-a2b1), (c1a2-c2a1)/(a1b2-a2b1)])")
            "Parabola Analysis" -> arrayOf("Vertex ([-b/2a, (4ac-b^2)/4a])", "Vertex Form (y = a(x-h)^2 + k)", "Axis of Symmetry (x = -b / (2 * a))", "Focus ([-b/2a, (4ac-b^2+1)/4a])", "Directrix (y = (4ac-b^2-1)/4a)")
            "De Broglie Wavelength" -> arrayOf("Lambda (de_broglie(m, v))")
            "Photon Energy (E=hf)" -> arrayOf("Energy (photon_energy(f))")
            "Heisenberg Uncertainty" -> arrayOf("dp (uncertainty_p(dx))")
            "Particle in a Box" -> arrayOf("En (energy_box(n, L, m))")
            "Boltzmann Entropy" -> arrayOf("S (entropy_boltzmann(w))")
            "Boltzmann Factor" -> arrayOf("Factor (boltzmann_factor(E, T))")
            "Gravitational Time Dilation" -> arrayOf("t' (grav_dilation(t, M, r))")
            "Lorentz Factor" -> arrayOf("Gamma (lorentz(v))")
            "Newton's 2nd Law (F=ma)" -> arrayOf("Force (newton_f(m, a))")
            "Ideal Gas (Find P)" -> arrayOf("Pressure (gas_p(V, n, T))")
            "Ideal Gas (Find V)" -> arrayOf("Volume (gas_v(P, n, T))")
            "Gibbs Free Energy" -> arrayOf("dG (gibbs(dH, T, dS))")
            "Nernst Equation" -> arrayOf("E (nernst(E0, n, Q, T))")
            "Arrhenius Equation" -> arrayOf("Rate k (arrhenius(A, Ea, T))")
            else -> emptyArray()
        }

        AlertDialog.Builder(this)
            .setTitle("$shape Formulas")
            .setItems(formulas) { _, which ->
                val fullFormula = formulas[which]
                val label = fullFormula.substringBefore(" (").replace(" ", "")
                val formula = fullFormula.substringAfter(" (").substringBeforeLast(")")
                currentInput = "$label($formula)".replace("π", "π").replace("²", "^2").replace("³", "^3")
                isNewOperation = false
                updateDisplay()
                display.setSelection(display.text.length)
            }
            .show()
    }

    private fun showFormulaSolverDialog(shape: String, label: String, formula: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_formula_solver, null)
        val container = dialogView.findViewById<android.widget.LinearLayout>(R.id.inputs_container)
        container.removeAllViews()

        // Extract variables (words starting with letter, not known functions)
        val knownFuncs = listOf("sqrt", "sin", "cos", "tan", "asin", "acos", "atan", "abs", "log", "ln", "int")
        val variables = "\\b[a-zA-Z][a-zA-Z0-9]*\\b".toRegex().findAll(formula)
            .map { it.value }
            .filter { it !in knownFuncs && it != "x" && it != "y" && it != "pi" && it != "e" }
            .distinct()
            .toList()

        val inputFields = mutableMapOf<String, EditText>()
        for (v in variables) {
            val tv = TextView(this)
            tv.text = "Enter $v:"
            tv.setTextColor(Color.BLACK)
            tv.setPadding(20, 20, 20, 0)
            container.addView(tv)

            val et = EditText(this)
            et.hint = v
            et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            container.addView(et)
            inputFields[v] = et
        }

        AlertDialog.Builder(this)
            .setTitle("Solve $shape: $label")
            .setView(dialogView)
            .setPositiveButton("Solve Step-by-Step") { _, _ ->
                var substituted = formula
                for ((v, et) in inputFields) {
                    val value = et.text.toString().ifEmpty { "0" }
                    substituted = substituted.replace(v, value)
                }

                val result = CalculatorHelper.evaluateFull(substituted, useRadians)
                if (result != null) {
                    val resultStr = formatValue(BigDecimal(result.toString()))

                    // Show steps in history
                    addHistoryItem("Formula: $label", formula)
                    addHistoryItem("Substitution", substituted)
                    addHistoryItem("Result", resultStr)

                    currentInput = result.toString()
                    updateDisplay()
                } else {
                    Toast.makeText(this, "Calculation Error", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAnimationMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, view)
        popup.menu.add("Auto Scan")
        popup.menu.add("Fly-through")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Auto Scan" -> appendCharacter("scan(")
                "Fly-through" -> appendCharacter("fly(")
            }
            true
        }
        popup.show()
    }

    private fun showLayoutMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, view)
        popup.menu.add("Round")
        popup.menu.add("Hole")
        popup.menu.add("Margin")
        popup.menu.add("Inch")
        popup.menu.add("m (Unit)")
        popup.menu.add("Pivot")
        popup.menu.add("Move")
        popup.menu.add("Piston")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Round" -> appendCharacter(" round ")
                "Hole" -> appendCharacter(" hole ")
                "Margin" -> appendCharacter(" margin ")
                "Inch" -> appendCharacter(" inch ")
                "m (Unit)" -> appendCharacter(" m ")
                "Pivot" -> appendCharacter(" pivot ")
                "Move" -> appendCharacter(" move ")
                "Piston" -> appendCharacter(" piston ")
            }
            true
        }
        popup.show()
    }

    private fun appendScientificFunction(func: String) {
        if (currentInput.isNotEmpty() && (currentInput.last().isDigit() || currentInput.last() == ')')) {
            appendCharacter("×$func(")
        } else {
            appendCharacter("$func(")
        }
    }

    private fun appendCharacter(char: String) {
        var processedChar = char
        val rawPos = getRawCursorPosition()

        // Auto-insert multiplication if needed
        if (char == "(" && rawPos > 0 && rawPos <= currentInput.length && currentInput[rawPos - 1].isDigit()) {
            processedChar = "×$char"
        }

        if (isNewOperation) {
            currentInput = processedChar
            isNewOperation = false
            updateDisplay()
            display.setSelection(display.text.length)
        } else {
            val left = currentInput.substring(0, rawPos.coerceAtMost(currentInput.length))
            val right = currentInput.substring(rawPos.coerceAtMost(currentInput.length))
            currentInput = left + processedChar + right
            updateDisplay()
            val newPos = getFormattedCursorPosition(rawPos + processedChar.length)
            display.setSelection(newPos.coerceAtMost(display.text.length))
        }
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
            display.setText("Error")
        }
    }

    private fun applyCalculatorTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("calc_theme", "dark")

        val isBlue = theme == "blue"
        val isGreen = theme == "green"
        val isPurple = theme == "purple"
        val isOrange = theme == "orange"

        val displayColor = when {
            isBlue -> Color.parseColor("#001F3F")
            isGreen -> Color.parseColor("#2E7D32")
            isPurple -> Color.parseColor("#6A1B9A")
            isOrange -> Color.parseColor("#F57C00")
            else -> Color.parseColor("#B2D3C2")
        }
        val bgColor = when {
            isBlue -> Color.parseColor("#001F3F")
            isGreen -> Color.parseColor("#1B5E20")
            isPurple -> Color.parseColor("#4A148C")
            isOrange -> Color.parseColor("#E65100")
            else -> Color.parseColor("#121212")
        }

        val digitColor = when {
            isBlue -> Color.parseColor("#0074D9")
            isGreen -> Color.parseColor("#4CAF50")
            isPurple -> Color.parseColor("#9C27B0")
            isOrange -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#424242")
        }
        val operatorColor = when {
            isBlue -> Color.parseColor("#0056b3")
            isGreen -> Color.parseColor("#2E7D32")
            isPurple -> Color.parseColor("#7B1FA2")
            isOrange -> Color.parseColor("#EF6C00")
            else -> Color.parseColor("#212121")
        }
        val gstColor = when {
            isBlue -> Color.parseColor("#00BCD4")
            isGreen -> Color.parseColor("#81C784")
            isPurple -> Color.parseColor("#BA68C8")
            isOrange -> Color.parseColor("#FFB74D")
            else -> Color.parseColor("#4696C2")
        }
        val acColor = when {
            isBlue -> Color.parseColor("#FF4136")
            isGreen -> Color.parseColor("#C62828")
            isPurple -> Color.parseColor("#E91E63")
            isOrange -> Color.parseColor("#BF360C")
            else -> Color.parseColor("#2BB07A")
        }

        val textColor = Color.WHITE
        val displayTextMain = when {
            isBlue || isGreen || isPurple || isOrange -> Color.WHITE
            else -> Color.parseColor("#222222")
        }
        val headerTextColor = if (isBlue || isGreen || isPurple || isOrange) Color.WHITE else Color.parseColor("#333333")

        findViewById<View>(R.id.calculator_root)?.setBackgroundColor(bgColor)
        findViewById<View>(R.id.calculator_display_area)?.setBackgroundColor(displayColor)

        findViewById<TextView>(R.id.calculator_title)?.setTextColor(headerTextColor)
        findViewById<ImageButton>(R.id.settings_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)
        findViewById<ImageButton>(R.id.graph_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)
        findViewById<ImageButton>(R.id.save_equation_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)
        findViewById<ImageButton>(R.id.library_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)
        findViewById<ImageButton>(R.id.steps_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)

        display.setTextColor(displayTextMain)
        memoryIndicator.setTextColor(displayTextMain)

        val historyColor = if (isBlue || isGreen || isPurple || isOrange) Color.WHITE else Color.parseColor("#333333")
        val historyExprColor = if (isBlue || isGreen || isPurple || isOrange) Color.parseColor("#B2EBF2") else Color.GRAY
        historyAdapterMain.updateColors(historyColor, historyExprColor)

        stepsDisplay.setTextColor(when {
            isBlue || isGreen || isPurple || isOrange -> Color.parseColor("#80DEEA")
            else -> Color.parseColor("#333333")
        })
        formulaDisplay.setTextColor(when {
            isBlue || isGreen || isPurple || isOrange -> Color.parseColor("#B2EBF2")
            else -> Color.parseColor("#555555")
        })

        // Update all buttons
        val digits = listOf(R.id.button_0, R.id.button_1, R.id.button_2, R.id.button_3, R.id.button_4, R.id.button_5, R.id.button_6, R.id.button_7, R.id.button_8, R.id.button_9, R.id.button_00, R.id.button_dot)
        val operators = listOf(R.id.button_add, R.id.button_subtract, R.id.button_multiply, R.id.button_divide, R.id.button_equals, R.id.button_plus_minus, R.id.button_percent, R.id.button_check, R.id.button_correct, R.id.button_gt, R.id.button_m_plus, R.id.button_m_minus, R.id.button_mrc, R.id.button_sqrt, R.id.button_square, R.id.button_pi, R.id.button_e, R.id.button_power, R.id.button_sin, R.id.button_cos, R.id.button_tan, R.id.button_log, R.id.button_ln, R.id.button_parenthesis_open, R.id.button_parenthesis_close, R.id.button_2nd, R.id.button_deg_rad, R.id.button_factorial, R.id.button_sinh, R.id.button_cosh, R.id.button_tanh, R.id.button_layout, R.id.button_geometry, R.id.button_x_var, R.id.button_y_var, R.id.button_solve, R.id.button_polynomial, R.id.button_spiral, R.id.button_nested, R.id.button_animation, R.id.button_ans, R.id.button_save_scientific, R.id.button_comma)
        val gsts = listOf(R.id.button_gst_5, R.id.button_gst_12, R.id.button_gst_18, R.id.button_gst_minus)

        digits.forEach { id -> findViewById<Button>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(digitColor); it.setTextColor(textColor) } }
        operators.forEach { id -> findViewById<Button>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(operatorColor); it.setTextColor(textColor) } }
        gsts.forEach { id -> findViewById<Button>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(gstColor); it.setTextColor(Color.WHITE) } }
        findViewById<Button>(R.id.button_clear)?.let { it.backgroundTintList = ColorStateList.valueOf(acColor); it.setTextColor(Color.WHITE) }
    }

    private fun setupEventListeners() {
        val numbers = mapOf(
            R.id.button_0 to "0", R.id.button_1 to "1", R.id.button_2 to "2",
            R.id.button_3 to "3", R.id.button_4 to "4", R.id.button_5 to "5",
            R.id.button_6 to "6", R.id.button_7 to "7", R.id.button_8 to "8",
            R.id.button_9 to "9", R.id.button_00 to "00"
        )

        for ((id, value) in numbers) {
            findViewById<Button>(id).setOnClickListener {
                provideFeedback()
                appendNumber(value)
            }
        }

        findViewById<Button>(R.id.button_dot).setOnClickListener {
            provideFeedback()
            if (isNewOperation) {
                currentInput = "0."
                isNewOperation = false
                updateDisplay()
                display.setSelection(display.text.length)
                return@setOnClickListener
            }

            // Check if the current number being typed already has a dot
            val lastPart = currentInput.split(Regex("[+\\-×÷^()\\*\\s]")).last()
            val canAddDot = !lastPart.contains(".")

            if (canAddDot) {
                if (currentInput.isEmpty() || (!currentInput.last().isDigit() && currentInput.last() != '.')) {
                    appendCharacter("0.")
                } else {
                    appendCharacter(".")
                }
            }
        }

        findViewById<Button>(R.id.button_add).setOnClickListener { provideFeedback(); setOperator("+") }
        findViewById<Button>(R.id.button_subtract).setOnClickListener { provideFeedback(); setOperator("-") }
        findViewById<Button>(R.id.button_multiply).setOnClickListener { provideFeedback(); setOperator("×") }
        findViewById<Button>(R.id.button_divide).setOnClickListener { provideFeedback(); setOperator("÷") }

        findViewById<Button>(R.id.button_percent).setOnClickListener {
            provideFeedback()
            applyPercentage()
        }

        findViewById<Button>(R.id.button_gst_minus).setOnClickListener {
            provideFeedback()
            showGstMinusMenu(it)
        }

        findViewById<Button>(R.id.button_gst_5).setOnClickListener {
            provideFeedback()
            applyGST(BigDecimal("5.0"))
        }

        findViewById<Button>(R.id.button_gst_12).setOnClickListener {
            provideFeedback()
            applyGST(BigDecimal("12.0"))
        }

        findViewById<Button>(R.id.button_gst_18).setOnClickListener {
            provideFeedback()
            applyGST(BigDecimal("18.0"))
        }

        findViewById<Button>(R.id.button_clear).setOnClickListener { provideFeedback(); clear() }
        findViewById<Button>(R.id.button_correct).setOnClickListener { provideFeedback(); backspace() }
        findViewById<Button>(R.id.button_equals).setOnClickListener {
            provideFeedback()
            val hasVariables = currentInput.contains("x") || currentInput.contains("y")
            if (hasVariables && !currentInput.contains("=")) {
                appendCharacter("=")
            } else {
                calculateResult(true)
            }
        }
        findViewById<Button>(R.id.button_equals).setOnLongClickListener {
            provideFeedback()
            appendCharacter("=")
            true
        }

        findViewById<Button>(R.id.button_check).setOnClickListener { provideFeedback(); openHistory() }

        findViewById<Button>(R.id.button_plus_minus).setOnClickListener {
            provideFeedback()
            if (currentInput.isNotEmpty()) {
                val value = currentInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (value.signum() != 0) {
                    currentInput = value.negate().stripTrailingZeros().toPlainString()
                    updateDisplay()
                    display.setSelection(display.text.length)
                }
            } else if (operator.isEmpty() && lastValue.signum() != 0) {
                lastValue = lastValue.negate()
                currentInput = lastValue.stripTrailingZeros().toPlainString()
                isNewOperation = false
                updateDisplay()
                display.setSelection(display.text.length)
            }
        }

        findViewById<Button>(R.id.button_gt).setOnClickListener {
            provideFeedback()
            display.setText("GT = ${formatValue(grandTotal)}")
            currentInput = grandTotal.stripTrailingZeros().toPlainString()
            isNewOperation = true
            historyExpression = formatValue(grandTotal)
        }

        findViewById<Button>(R.id.button_m_plus).setOnClickListener {
            provideFeedback()
            val value = getActiveValue()
            memoryValue = memoryValue.add(value)
            isNewOperation = true
            updateMemoryIndicator()
        }
        findViewById<Button>(R.id.button_m_minus).setOnClickListener {
            provideFeedback()
            val value = getActiveValue()
            memoryValue = memoryValue.subtract(value)
            isNewOperation = true
            updateMemoryIndicator()
        }
        findViewById<Button>(R.id.button_mrc).setOnClickListener {
            provideFeedback()
            currentInput = memoryValue.stripTrailingZeros().toPlainString()
            isNewOperation = false
            historyExpression = formatValue(memoryValue)
            updateDisplay()
            display.setSelection(display.text.length)
        }
    }

    private fun applyPercentage() {
        if (currentInput.isNotEmpty()) {
            // Append % to the current input if it's not already there
            val exprWithPercent = if (currentInput.endsWith("%")) currentInput else currentInput + "%"

            val result = CalculatorHelper.evaluateFull(exprWithPercent, useRadians)
            if (result != null && (result is Double || result is BigDecimal)) {
                val resVal = if (result is Double) BigDecimal(result.toString()) else result as BigDecimal
                currentInput = resVal.stripTrailingZeros().toPlainString()
                isNewOperation = true
                updateDisplay()
                display.setSelection(display.text.length)
            }
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
        display.setSelection(display.text.length)
    }

    private fun showGstMinusMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, view)
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

        // Formula: OriginalValue = InclusiveValue / (1 + percentage / 100)
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
        display.setSelection(display.text.length)
    }

    private fun openHistory() {
        val intent = Intent(this, CalculatorHistoryActivity::class.java)
        intent.putExtra("history", historyList)
        historyLauncher.launch(intent)
    }

    private fun recalculateGrandTotal() {
        grandTotal = BigDecimal.ZERO
        for (item in historyList) {
            grandTotal = grandTotal.add(item.result.replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
    }

    private fun getActiveValue(): BigDecimal {
        val dText = display.text.toString()
        return if (dText.contains("=")) {
            dText.split("=").last().trim().replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
        } else {
            currentInput.replace(",", "").toBigDecimalOrNull() ?: lastValue
        }
    }

    private fun updateMemoryIndicator() {
        memoryIndicator.text = if (memoryValue.signum() != 0) "M=${formatValue(memoryValue)}" else ""
    }

    private fun updateStepsDisplay() {
        stepsDisplay.text = String.format("%02d", historyList.size)
    }

    private fun updateDisplay() {
        val length = currentInput.length
        val textSizeSp = when {
            length <= 10 -> 40f
            length <= 15 -> 34f
            length <= 25 -> 28f
            length <= 35 -> 22f
            else -> 20f
        }
        display.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        if (operator.isNotEmpty()) {
            formulaDisplay.text = "$historyExpression $operator"
            display.setText(if (currentInput.isEmpty()) formatValue(lastValue) else formatRawInput(currentInput))
        } else {
            // Real-time calculation preview
            if (currentInput.isNotEmpty()) {
                val hasOperator = currentInput.any { it in "+-×÷*/^%" }
                if (hasOperator) {
                    val res = CalculatorHelper.evaluateFull(currentInput, useRadians)
                    if (res != null) {
                        val preview = if (res is Double) {
                            if (res.isNaN() || res.isInfinite()) ""
                            else "= " + formatValue(BigDecimal(res.toString()))
                        } else if (res is BigDecimal) {
                            "= " + formatValue(res)
                        } else {
                            ""
                        }
                        formulaDisplay.text = preview
                    } else {
                        formulaDisplay.text = ""
                    }
                } else {
                    formulaDisplay.text = ""
                }
            } else {
                formulaDisplay.text = ""
            }
            display.setText(if (currentInput.isEmpty()) "0" else formatRawInput(currentInput))
        }
    }

    private fun formatRawInput(input: String): String {
        if (input.isEmpty()) return "0"

        // Bypass formatting for formulas, commands, and strings with letters or math operators
        val bypass = listOf("(", ")", "=", ",", "^", "*", "/", "+", "×", "÷")
        if (input.any { it.isLetter() } || bypass.any { input.contains(it) }) return input

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isIndian = prefs.getBoolean("calc_indian_format", true)
        val formatted = if (isIndian) CalculatorHelper.formatIndianStyleString(input) else CalculatorHelper.formatInternationalStyleString(input)
        if (input.endsWith(".")) return "$formatted."
        return formatted
    }

    private fun tryComputeResult(): BigDecimal? {
        if (currentInput.isEmpty()) return null
        val v2 = currentInput.toBigDecimalOrNull() ?: return null
        return try {
            when (operator) {
                "+" -> lastValue.add(v2)
                "-" -> lastValue.subtract(v2)
                "×" -> lastValue.multiply(v2)
                "÷" -> if (v2.signum() != 0) lastValue.divide(v2, 10, RoundingMode.HALF_UP) else null
                "^" -> BigDecimal(Math.pow(lastValue.toDouble(), v2.toDouble()))
                else -> null
            }
        } catch (e: ArithmeticException) { null }
    }

    private fun calculateResult(isFinal: Boolean = false) {
        val keywords = listOf("(", "round", "hole", "margin", "inch", "m", "+", "-", "×", "÷", "*", "/", "^")
        val hasKeyword = keywords.any { currentInput.lowercase().contains(it) }

        if (operator.isEmpty() && !hasKeyword) return

        val fullExpression = if (historyExpression.isEmpty() || hasKeyword) currentInput else "$historyExpression$operator$currentInput"

        if (currentInput.isEmpty() && isFinal && !hasKeyword) {
            val resultStr = formatValue(lastValue)
            val cleanHistoryExpr = historyExpression.replace(" ", "")
            if (cleanHistoryExpr.isNotEmpty() && cleanHistoryExpr != resultStr) {
                addHistoryItem(cleanHistoryExpr, resultStr)
            }
            operator = ""
            historyExpression = resultStr
            isNewOperation = true
            updateDisplay()
            display.setSelection(display.text.length)
            return
        }

        if (currentInput.isEmpty() && !hasKeyword) return

        // Use full evaluator for complex expressions, layout commands, or simple expressions with operators
        val result: Any? = if (hasKeyword || fullExpression.contains("(")) {
            val res = CalculatorHelper.evaluateFull(fullExpression, useRadians)
            if (res != null) {
                if (res is Double) {
                    if (res.isNaN() || res.isInfinite()) {
                        display.setText("Wrong Number")
                        return
                    }
                    BigDecimal(res.toString())
                } else res
            } else null
        } else {
            tryComputeResult()
        }

        if (result == null) return

        val opForHistory = when(operator) { "×" -> "*"; "÷" -> "/"; else -> operator }

        // For layout commands and unified formulas, keep the command in history
        historyExpression = if (hasKeyword) {
            formatRawInput(currentInput)
        } else if (historyExpression.isEmpty()) {
            "${formatValue(lastValue ?: BigDecimal.ZERO)}$opForHistory${formatRawInput(currentInput)}"
        } else {
            "$historyExpression$opForHistory${formatRawInput(currentInput)}"
        }

        val resultStr = if (result is String) result else formatValue(result as BigDecimal)

        if (isFinal) {
            val cleanHistoryExpr = historyExpression.replace(" ", "")
            addHistoryItem(cleanHistoryExpr, resultStr)

            if (result is BigDecimal) {
                grandTotal = (grandTotal ?: BigDecimal.ZERO).add(result)
                currentInput = result.stripTrailingZeros().toPlainString()
            } else {
                currentInput = result.toString()
            }
            operator = ""
            historyExpression = resultStr
            isNewOperation = true
            updateDisplay()
            display.setSelection(display.text.length)
        } else {
            if (result is BigDecimal) {
                lastValue = result
            }
            currentInput = ""
            isNewOperation = true
            updateDisplay()
            display.setSelection(display.text.length)
        }
    }

    private fun addHistoryItem(expression: String, result: String) {
        historyList.add(Calculation("$expression=", result))
        historyAdapterMain.notifyItemInserted(historyList.size - 1)
        updateStepsDisplay()
        historyRecyclerViewMain.scrollToPosition(historyList.size - 1)
    }

    private fun getRawCursorPosition(): Int {
        val formatted = display.text.toString()
        val cursor = display.selectionStart
        if (cursor <= 0) return 0

        if (formatted == currentInput) return cursor

        // Count non-comma characters before cursor
        var rawPos = 0
        for (i in 0 until cursor.coerceAtMost(formatted.length)) {
            if (formatted[i] != ',') rawPos++
        }
        return rawPos
    }

    private fun getFormattedCursorPosition(rawPos: Int): Int {
        val formatted = if (operator.isNotEmpty() && currentInput.isEmpty()) formatValue(lastValue)
        else if (currentInput.isEmpty()) "0"
        else formatRawInput(currentInput)
        if (rawPos <= 0) return 0

        if (formatted == currentInput) return rawPos

        var currentRaw = 0
        for (i in 0 until formatted.length) {
            if (formatted[i] != ',') {
                currentRaw++
                if (currentRaw == rawPos) return i + 1
            }
        }
        return formatted.length
    }

    private fun appendNumber(num: String) {
        if (isNewOperation) {
            currentInput = if (num == "00") "0" else num
            isNewOperation = false
            updateDisplay()
            display.setSelection(display.text.length)
            return
        }

        // Prevent multiple leading zeros
        if (currentInput == "0" && (num == "00" || num == "0")) return

        val rawPos = getRawCursorPosition()
        val left = currentInput.substring(0, rawPos.coerceAtMost(currentInput.length))
        val right = currentInput.substring(rawPos.coerceAtMost(currentInput.length))

        val newVal = if (currentInput == "0" && num != "00") num else left + num + right

        // Remove leading zero if it's like "01" (but keep "0.")
        val finalVal = if (newVal.startsWith("0") && newVal.length > 1 && newVal[1] != '.') {
            newVal.substring(1)
        } else {
            newVal
        }

        if (finalVal.replace(".", "").length >= 1000) return

        currentInput = finalVal
        updateDisplay()
        val newPos = getFormattedCursorPosition(rawPos + num.length)
        display.setSelection(newPos.coerceAtMost(display.text.length))
    }

    private fun setOperator(op: String) {
        val operators = "+-×÷*/^%"
        var rawPos = getRawCursorPosition()

        var tempInput = currentInput
        if (rawPos > 0 && rawPos <= tempInput.length && tempInput[rawPos - 1] in operators) {
            tempInput = tempInput.substring(0, rawPos - 1) + tempInput.substring(rawPos)
            rawPos--
        } else if (rawPos < tempInput.length && tempInput[rawPos] in operators) {
            tempInput = tempInput.substring(0, rawPos) + tempInput.substring(rawPos + 1)
        }

        if (isNewOperation && tempInput.isNotEmpty()) {
            isNewOperation = false
        }

        val left = tempInput.substring(0, rawPos.coerceAtMost(tempInput.length))
        val right = tempInput.substring(rawPos.coerceAtMost(tempInput.length))
        currentInput = left + op + right
        isNewOperation = false

        updateDisplay()
        val newPos = getFormattedCursorPosition(rawPos + op.length)
        display.setSelection(newPos.coerceAtMost(display.text.length))
    }

    private fun clear() {
        grandTotal = BigDecimal.ZERO
        currentInput = ""
        lastValue = BigDecimal.ZERO
        operator = ""
        isNewOperation = true
        historyExpression = ""
        historyList.clear()
        historyAdapterMain.notifyDataSetChanged()
        updateStepsDisplay()
        updateDisplay()
        display.setSelection(display.text.length)
    }

    private fun backspace() {
        if (display.text.toString().contains("GT")) {
            display.setText("0")
            currentInput = ""
            historyExpression = ""
            return
        }

        if (isNewOperation) {
            currentInput = ""
            isNewOperation = false
            updateDisplay()
            return
        }

        val rawPos = getRawCursorPosition()
        if (rawPos > 0 && currentInput.isNotEmpty()) {
            val left = currentInput.substring(0, rawPos - 1)
            val right = currentInput.substring(rawPos.coerceAtMost(currentInput.length))
            currentInput = left + right
            updateDisplay()
            val newPos = getFormattedCursorPosition(rawPos - 1)
            display.setSelection(newPos.coerceAtMost(display.text.length))
        } else if (operator.isNotEmpty() && currentInput.isEmpty()) {
            operator = ""
            currentInput = lastValue.stripTrailingZeros().toPlainString()
            isNewOperation = false
            updateDisplay()
            display.setSelection(display.text.length)
        }
    }

    private fun provideFeedback() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val vibrateOn = prefs.getBoolean("vibrate_on_keypress", true)
        val soundOn = prefs.getBoolean("sound_on_keypress", true)

        if (vibrateOn) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val intensity = prefs.getInt("vibration_intensity", 30)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(intensity.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(intensity.toLong())
            }
        }

        if (soundOn) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volume = prefs.getInt("sound_volume", 5) / 100f
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume)
        }
    }

    private fun BigDecimal.isInfinite(): Boolean = false // BigDecimal does not have infinity

    private fun showSaveEquationDialog() {
        val equation = currentInput.ifEmpty { formulaDisplay.text.toString().trim() }
        if (equation.isEmpty() || equation == "0") {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.hint = "Enter Label (e.g. Area of Circle)"
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(50, 20, 50, 20)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Save Equation")
            .setMessage("Equation: $equation")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().ifEmpty { "Saved Equation" }
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@CalculatorActivity).savedEquationDao()
                        .insert(SavedEquation(label = label, equation = equation))
                }
                Toast.makeText(this, "Equation saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEquationLibraryDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_equation_library, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .create()

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.library_recycler_view)
        val closeBtn = dialogView.findViewById<View>(R.id.close_library_button)

        val adapter = EquationLibraryAdapter(
            onSelect = { saved ->
                currentInput = saved.equation
                isNewOperation = false
                updateDisplay()
                display.setSelection(display.text.length)
                dialog.dismiss()
            },
            onDelete = { saved ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@CalculatorActivity).savedEquationDao().delete(saved)
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@CalculatorActivity).savedEquationDao().getAll().collect { list ->
                adapter.submitList(list)
            }
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }


    private fun showStepByStepDialog() {
        val expr = if (currentInput.isNotEmpty()) currentInput else formulaDisplay.text.toString().replace("=", "").trim()
        if (expr.isEmpty() || expr == "0") {
            Toast.makeText(this, "No expression to solve", Toast.LENGTH_SHORT).show()
            return
        }

        val steps = calculateGenericSteps(expr)
        val stepsStr = steps.joinToString("\n\n")

        AlertDialog.Builder(this)
            .setTitle("Step-by-Step Solution")
            .setMessage(stepsStr)
            .setPositiveButton("Add to History") { _, _ ->
                for (s in steps) {
                    addHistoryItem("Step", s)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun calculateQuadraticSteps(expr: String): List<String> {
        val steps = mutableListOf<String>()
        var cleanExpr = expr.replace("=0", "").replace("power[x,2]", "x²").replace("x^2", "x²").trim()

        // Handle solve(expr) wrapper
        if (cleanExpr.startsWith("solve(")) {
            cleanExpr = cleanExpr.substringAfter("solve(").substringBeforeLast(")")
        }

        // Handle quad(a,b,c) wrapper
        if (cleanExpr.startsWith("quad(")) {
            val parts = cleanExpr.substringAfter("quad(").substringBeforeLast(")").split(",")
            if (parts.size == 3) {
                val a = parts[0].toDoubleOrNull() ?: 1.0
                val b = parts[1].toDoubleOrNull() ?: 0.0
                val c = parts[2].toDoubleOrNull() ?: 0.0
                return generateQuadSteps(a, b, c, "${formatStepValue(a)}x² + ${formatStepValue(b)}x + ${formatStepValue(c)}")
            }
        }

        // Extract coefficients using points logic on the inner expression
        fun f(x: Double): Double {
            val eval = CalculatorHelper.evaluateFull(cleanExpr, true, x)
            return if (eval is Double) eval else 0.0
        }

        val c = f(0.0)
        val y1 = f(1.0)
        val ym1 = f(-1.0)
        val a = (y1 + ym1 - 2 * c) / 2.0
        val b = y1 - c - a

        if (abs(a) < 1e-9) return emptyList() // Not quadratic

        return generateQuadSteps(a, b, c, cleanExpr.replace("x^2", "x²"))
    }

    private fun generateQuadSteps(a: Double, b: Double, c: Double, originalExpr: String): List<String> {
        val steps = mutableListOf<String>()
        steps.add("Let's solve the quadratic equation step by step:\n\n$originalExpr = 0")

        steps.add("Step 1: Identify coefficients\nCompare with the standard form:\nax² + bx + c = 0\n\nSo,\na = ${formatStepValue(a)}\nb = ${formatStepValue(b)}\nc = ${formatStepValue(c)}")

        steps.add("Step 2: Use the Quadratic Formula\nx = [-b ± √(b² - 4ac)] / 2a\n\nSubstitute the values:\nx = [-(${formatStepValue(b)}) ± √(${formatStepValue(b)}² - 4(${formatStepValue(a)})(${formatStepValue(c)}))] / 2(${formatStepValue(a)})")

        val disc = b * b - 4 * a * c
        steps.add("Step 3: Calculate the Discriminant\nD = b² - 4ac\nD = (${formatStepValue(b)})² - 4(${formatStepValue(a)})(${formatStepValue(c)})\nD = ${formatStepValue(b * b)} - ${formatStepValue(4 * a * c)}\nD = ${formatStepValue(disc)}")

        if (disc >= 0) {
            val sqrtD = sqrt(disc)
            steps.add("Step 4: Find the Square Root\n√${formatStepValue(disc)} ≈ ${String.format(Locale.US, "%.3f", sqrtD)}")

            val x1 = (-b + sqrtD) / (2 * a)
            val x2 = (-b - sqrtD) / (2 * a)

            steps.add("Step 5: Find the Two Roots\nx = [-${formatStepValue(b)} + ${String.format(Locale.US, "%.3f", sqrtD)}] / ${formatStepValue(2 * a)}\nx ≈ ${String.format(Locale.US, "%.3f", -b + sqrtD)} / ${formatStepValue(2 * a)}\nx ≈ ${String.format(Locale.US, "%.3f", x1)}\n\nand\n\nx = [-${formatStepValue(b)} - ${String.format(Locale.US, "%.3f", sqrtD)}] / ${formatStepValue(2 * a)}\nx ≈ ${String.format(Locale.US, "%.3f", -b - sqrtD)} / ${formatStepValue(2 * a)}\nx ≈ ${String.format(Locale.US, "%.3f", x2)}")

            steps.add("Final Answer:\n┌───────────────────────────┐\n  x ≈ ${String.format(Locale.US, "%.3f", x1)}\n  x ≈ ${String.format(Locale.US, "%.3f", x2)}\n└───────────────────────────┘")
        } else {
            val re = -b / (2 * a)
            val im = sqrt(-disc) / (2 * a)
            steps.add("Step 4: Find the Roots (Complex)\nSince D < 0, roots are imaginary:\nx = ${formatStepValue(re)} ± ${String.format(Locale.US, "%.3f", im)}i")
            steps.add("Final Answer:\n┌───────────────────────────┐\n  x = ${formatStepValue(re)} + ${String.format(Locale.US, "%.3f", im)}i\n  x = ${formatStepValue(re)} - ${String.format(Locale.US, "%.3f", im)}i\n└───────────────────────────┘")
        }

        // Graph View Analysis
        val vx = -b / (2 * a)
        val vy = a * vx * vx + b * vx + c
        val direction = if (a > 0) "upward" else "downward"

        var graphInfo = "Graph View\n\nFor the parabola y = ${originalExpr}\n\nKey points:\n"
        graphInfo += "• Opens $direction (because a = ${formatStepValue(a)}${if (a>0) ">0" else "<0"})\n"

        if (disc >= 0) {
            graphInfo += "• X-intercepts (roots):\n  (${String.format(Locale.US, "%.3f", (-b+sqrt(disc))/(2*a))}, 0)\n  (${String.format(Locale.US, "%.3f", (-b-sqrt(disc))/(2*a))}, 0)\n"
        } else {
            graphInfo += "• X-intercepts: No Real Intercepts\n"
        }
        graphInfo += "• Y-intercept: (0, ${formatStepValue(c)})\n\n"

        graphInfo += "Vertex:\nx = -b / 2a\nx = -${formatStepValue(b)} / 2(${formatStepValue(a)}) = ${formatStepValue(vx)}\n"
        graphInfo += "y = ${formatStepValue(a)}(${formatStepValue(vx)})² + ${formatStepValue(b)}(${formatStepValue(vx)}) + ${formatStepValue(c)}\n"
        graphInfo += "y = ${formatStepValue(a * vx * vx)} + ${formatStepValue(b * vx)} + ${formatStepValue(c)}\ny = ${formatStepValue(vy)}\n\n"
        graphInfo += "Vertex: [${formatStepValue(vx)}, ${formatStepValue(vy)}]"
        steps.add(graphInfo)

        // Visual Text Sketch
        val sketch = buildString {
            append("Sketch:\n")
            append("      y\n")
            append("      ^\n")
            if (a > 0) { // Upward
                append(" 10 | \n")
                append("  5 | \n")
                append("  ${formatStepValue(c)} |           ● (0, ${formatStepValue(c)})\n")
                if (disc >= 0) {
                    val x1 = (-b - sqrt(disc)) / (2 * a)
                    val x2 = (-b + sqrt(disc)) / (2 * a)
                    append("  0 +----●-------------------●-------------> x\n")
                    append("      ${String.format(Locale.US, "%.1f", x1)}             ${String.format(Locale.US, "%.1f", x2)}\n")
                    append("      |\n")
                    append(" -20 | \n")
                    append(" -40 |           ▼ Vertex (${formatStepValue(vx)}, ${formatStepValue(vy)})\n")
                } else {
                    append("  0 +--------------------------------------> x\n")
                    append("      |  \\     /\n")
                    append("      |   \\___/  Vertex (${formatStepValue(vx)}, ${formatStepValue(vy)})\n")
                }
            } else { // Downward
                append("      ▲ Vertex (${formatStepValue(vx)}, ${formatStepValue(vy)})\n")
                append("      |\n")
                if (disc >= 0) {
                    val x1 = (-b - sqrt(disc)) / (2 * a)
                    val x2 = (-b + sqrt(disc)) / (2 * a)
                    append("  0 +----●-------------------●-------------> x\n")
                    append("      ${String.format(Locale.US, "%.1f", x1)}             ${String.format(Locale.US, "%.1f", x2)}\n")
                } else {
                    append("  0 +--------------------------------------> x\n")
                }
                append(" -${formatStepValue(abs(c))} |           ● (0, ${formatStepValue(c)})\n")
            }
        }
        steps.add(sketch)

        val summary = if (disc >= 0) {
            "The graph crosses the x-axis at x ≈ ${String.format(Locale.US, "%.3f", (-b-sqrt(disc))/(2*a))} and x ≈ ${String.format(Locale.US, "%.3f", (-b+sqrt(disc))/(2*a))}, reaches its ${if(a>0) "minimum" else "maximum"} at (${formatStepValue(vx)}, ${formatStepValue(vy)}), and then ${if(a>0) "rises" else "falls"} again."
        } else {
            "The graph does not cross the x-axis, reaches its ${if(a>0) "minimum" else "maximum"} at (${formatStepValue(vx)}, ${formatStepValue(vy)}), and then ${if(a>0) "rises" else "falls"} again."
        }
        steps.add(summary)

        return steps
    }

    private fun calculateCubicSteps(expr: String): List<String> {
        val steps = mutableListOf<String>()
        var cleanExpr = expr.replace("=0", "").replace("x^3", "x³").replace("x^2", "x²").trim()

        if (cleanExpr.startsWith("cubic(")) {
            val parts = cleanExpr.substringAfter("cubic(").substringBeforeLast(")").split(",")
            if (parts.size == 4) {
                val a = parts[0].toDoubleOrNull() ?: 1.0
                val b = parts[1].toDoubleOrNull() ?: 0.0
                val c = parts[2].toDoubleOrNull() ?: 0.0
                val d = parts[3].toDoubleOrNull() ?: 0.0
                return generateCubicSteps(a, b, c, d, "${formatStepValue(a)}x³ + ${formatStepValue(b)}x² + ${formatStepValue(c)}x + ${formatStepValue(d)}")
            }
        }

        // Extract coefficients via evaluation for general cubic ax^3 + bx^2 + cx + d
        fun f(x: Double): Double {
            val eval = CalculatorHelper.evaluateFull(cleanExpr, true, x)
            return if (eval is Double) eval else 0.0
        }

        val d = f(0.0)
        val y1 = f(1.0)
        val ym1 = f(-1.0)
        val y2 = f(2.0)

        // System:
        // a + b + c = y1 - d
        // -a + b - c = ym1 - d
        // 8a + 4b + 2c = y2 - d
        val v1 = y1 - d
        val vm1 = ym1 - d
        val v2 = y2 - d

        val b_coeff = (v1 + vm1) / 2.0
        val ac_sum = v1 - b_coeff // a + c
        // 8a + 2c = v2 - 4b
        val a8c2 = v2 - 4.0 * b_coeff
        // c = ac_sum - a
        // 8a + 2(ac_sum - a) = a8c2 -> 6a + 2*ac_sum = a8c2
        val a_coeff = (a8c2 - 2.0 * ac_sum) / 6.0
        val c_coeff = ac_sum - a_coeff

        return generateCubicSteps(a_coeff, b_coeff, c_coeff, d, cleanExpr)
    }

    private fun generateCubicSteps(a: Double, b: Double, c: Double, d: Double, originalExpr: String): List<String> {
        val steps = mutableListOf<String>()
        steps.add("Solving Cubic Equation:\n$originalExpr = 0")

        steps.add("Step 1: Identify coefficients\nCompare with standard form ax³ + bx² + cx + d = 0:\n  a = ${formatStepValue(a)}\n  b = ${formatStepValue(b)}\n  c = ${formatStepValue(c)}\n  d = ${formatStepValue(d)}")

        // Check for depressed cubic form: x^3 + px + q = 0 (when a=1, b=0)
        if (abs(a - 1.0) < 1e-9 && abs(b) < 1e-9) {
            val p = c
            val q = d
            steps.add("Step 2: Apply Cardano's Method\nSince the equation is in depressed form x³ + px + q = 0:\n  p = ${formatStepValue(p)}\n  q = ${formatStepValue(q)}")
            steps.add("Formula: x = root_3(-q/2 + sqrt(q²/4 + p³/27)) + root_3(-q/2 - sqrt(q²/4 + p³/27))")

            val q2_4 = q * q / 4.0
            val p3_27 = p * p * p / 27.0
            val disc = q2_4 + p3_27

            steps.add("Calculation:\n  q²/4 = ${formatStepValue(q2_4)}\n  p³/27 = ${formatStepValue(p3_27)}\n  Discriminant (Δ) = ${formatStepValue(disc)}")

            if (disc >= 0) {
                val u = Math.cbrt(-q/2.0 + sqrt(disc))
                val v = Math.cbrt(-q/2.0 - sqrt(disc))
                steps.add("  u = root_3(${formatStepValue(-q/2.0 + sqrt(disc))}) = ${formatStepValue(u)}")
                steps.add("  v = root_3(${formatStepValue(-q/2.0 - sqrt(disc))}) = ${formatStepValue(v)}")
                steps.add("  Real Root x = u + v = ${formatStepValue(u + v)}")
            } else {
                steps.add("Discriminant is negative (Δ < 0). There are three real roots. Using trigonometric form...")
            }
        } else {
            steps.add("Step 2: Check for rational roots (Rational Root Theorem)\nPossible roots are factors of d/a. Testing simple values...")
        }

        val roots = CalculatorHelper.solveCubic(a, b, c, d)
        val realRoots = roots.filter { abs(it.imag) < 1e-9 }.map { it.real }

        if (realRoots.isNotEmpty() && steps.none { it.contains("Cardano") }) {
            steps.add("Step 3: Found real roots via numerical solving:\n" + realRoots.joinToString("\n") { "  x ≈ ${formatStepValue(it)}" })
        }

        steps.add("Final Answer:\n" + roots.mapIndexed { i, r -> "  x${i + 1} = $r" }.joinToString("\n"))

        // Graph Analysis
        steps.add("Graph Analysis\n\nFor y = $originalExpr\n• Y-Intercept: (0, ${formatStepValue(d)})\n" +
                "• End Behavior: ${if (a > 0) "Falls left, Rises right" else "Rises left, Falls right"}\n" +
                "• Key Real Point: (${formatStepValue(realRoots.firstOrNull() ?: 0.0)}, 0)")

        // Textual Sketch
        if (realRoots.isNotEmpty()) {
            val r1 = realRoots.first()
            val sketch = buildString {
                append("Sketch:\n")
                append("      y\n")
                append("      ^\n")
                append("      |          ● (0, ${formatStepValue(d)})\n")
                append(" -----●----------+-----> x\n")
                append("    ${formatStepValue(r1)}\n")
                append("      |\n")
            }
            steps.add(sketch)
        }

        return steps
    }

    private fun calculateGenericSteps(expression: String): List<String> {
        val resultList = mutableListOf<String>()
        // Use isGraph=true to PRESERVE 'x' variable for polynomial detection
        val preprocessed = CalculatorHelper.preprocessExpression(expression, true)
        var current = preprocessed.replace(" ", "").replace("×", "*").replace("÷", "/")

        // Detect Formula Function (e.g. area_circle(5))
        val formulaSteps = calculateFormulaSteps(expression)
        if (formulaSteps.isNotEmpty()) return formulaSteps

        // Detect Solve wrapper
        if (current.startsWith("solve(")) {
            val inner = current.substringAfter("solve(").substringBeforeLast(")")
            return calculateGenericSteps(inner)
        }

        // Detect Cubic Equation
        if (current.contains("x^3") || current.contains("x³") || current.startsWith("cubic(")) {
            val cubicSteps = calculateCubicSteps(current)
            if (cubicSteps.isNotEmpty()) return cubicSteps
        }

        // Detect Quadratic Equation
        if (current.contains("x^2") || current.contains("x*x") || current.contains("x²") || current.startsWith("quad(")) {
            val quadSteps = calculateQuadraticSteps(current)
            if (quadSteps.isNotEmpty()) return quadSteps
        }

        // Check if expression is already solved
        if (current.contains("x1=") || current.contains("x2=")) {
            val original = formulaDisplay.text.toString().trim()
            if (original.isNotEmpty()) {
                val preOriginal = CalculatorHelper.preprocessExpression(original)
                val quadSteps = calculateQuadraticSteps(preOriginal)
                if (quadSteps.isNotEmpty()) return quadSteps
            }
        }

        resultList.add("Initial Expression: $current")

        // 1. Handle Constants
        val withConstants = current.replace("pi", "3.14159").replace("e", "2.71828")
        if (withConstants != current) {
            current = withConstants
            resultList.add("Substitute Constants: $current")
        }

        // Real-time calculation check: If it has business percentage (e.g. 100+10%), show that specific logic
        if (expression.contains("+") && expression.contains("%")) {
            val parts = expression.split("+")
            if (parts.size == 2) {
                val a = parts[0].trim().toDoubleOrNull()
                val b = parts[1].trim().removeSuffix("%").toDoubleOrNull()
                if (a != null && b != null) {
                    resultList.add("Business Percentage: $a + ($a * $b / 100)")
                    resultList.add("= $a + ${a * b / 100.0}")
                    resultList.add("= ${a + (a * b / 100.0)}")
                }
            }
        }

        var iterations = 0
        while (iterations < 10) { // Safety limit
            // 2. Handle Parentheses (innermost)
            val parenRegex = "\\(([^()]+)\\)".toRegex()
            val parenMatch = parenRegex.find(current)
            if (parenMatch != null) {
                val inner = parenMatch.groupValues[1]
                val res = CalculatorHelper.evaluateFull(inner, useRadians)
                if (res != null && res is Double) {
                    val resStr = formatStepValue(res)
                    current = current.replaceFirst("($inner)", resStr)
                    resultList.add("Solve Parentheses ($inner): $current")
                    iterations++
                    continue
                }
            }

            // 3. Handle MD (Multiplication / Division)
            val mdRegex = "(-?\\d+\\.?\\d*)[*/](-?\\d+\\.?\\d*)".toRegex()
            val mdMatch = mdRegex.find(current)
            if (mdMatch != null) {
                val op = mdMatch.value
                val res = CalculatorHelper.evaluateFull(op, useRadians)
                if (res != null && res is Double) {
                    val resStr = formatStepValue(res)
                    current = current.replaceFirst(op, resStr)
                    resultList.add("Multiply/Divide ($op): $current")
                    iterations++
                    continue
                }
            }

            // 4. Handle AS (Addition / Subtraction)
            val asRegex = "(-?\\d+\\.?\\d*)[+\\-](-?\\d+\\.?\\d*)".toRegex()
            val asMatch = asRegex.find(current)
            if (asMatch != null) {
                val op = asMatch.value
                val res = CalculatorHelper.evaluateFull(op, useRadians)
                if (res != null && res is Double) {
                    val resStr = formatStepValue(res)
                    current = current.replaceFirst(op, resStr)
                    resultList.add("Add/Subtract ($op): $current")
                    iterations++
                    continue
                }
            }

            break
        }

        // Final result verification
        val finalResult = CalculatorHelper.evaluateFull(expression, useRadians)
        if (finalResult != null && finalResult is Double) {
            val finalStr = formatValue(BigDecimal(finalResult.toString()))
            if (resultList.last().contains(finalStr) == false) {
                resultList.add("Final Result: $finalStr")
            }
        }

        return resultList.distinct()
    }

    private fun formatStepValue(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    private fun calculateFormulaSteps(expression: String): List<String> {
        val steps = mutableListOf<String>()
        val low = expression.lowercase().replace(" ", "")

        // 0. Nested EXPAND(FOIL(...))
        if (low.startsWith("expand(foil(")) {
            val inner = low.substringAfter("expand(foil(").substringBeforeLast("))")
            val nums = inner.split(",").mapNotNull { it.toDoubleOrNull() }
            if (nums.size == 4) {
                steps.add("Expanding 4 factors in 'x':\n(x + ${formatStepValue(nums[0])})(x + ${formatStepValue(nums[1])})(x + ${formatStepValue(nums[2])})(x + ${formatStepValue(nums[3])})")
                steps.add("Note: This is a 1D polynomial. To see a 3D surface graph, you need to use both 'x' and 'y'.")

                val k1 = nums[0]; val k2 = nums[1]; val k3 = nums[2]; val k4 = nums[3]
                steps.add("Step 1: Multiply first two factors\n(x + ${formatStepValue(k1)})(x + ${formatStepValue(k2)}) = x² + ${formatStepValue(k1 + k2)}x + ${formatStepValue(k1 * k2)}")
                steps.add("Step 2: Multiply second two factors\n(x + ${formatStepValue(k3)})(x + ${formatStepValue(k4)}) = x² + ${formatStepValue(k3 + k4)}x + ${formatStepValue(k3 * k4)}")
                steps.add("Step 3: Multiply resulting quadratics\n(x² + ${formatStepValue(k1 + k2)}x + ${formatStepValue(k1 * k2)})(x² + ${formatStepValue(k3 + k4)}x + ${formatStepValue(k3 * k4)})")

                val res = CalculatorHelper.evaluateFull("expand($inner)")
                steps.add("Final Result:\n$res")
                return steps
            }
        }

        // 0.1 3D FOIL (x+a)(y+b)
        if (low.startsWith("foil3d(")) {
            val args = low.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 2) {
                val a = args[0].toDoubleOrNull() ?: 1.0
                val b = args[1].toDoubleOrNull() ?: 2.0
                steps.add("Goal: Expand and Plot (x + $a)(y + $b)")
                steps.add("Step 1: Distribute terms\nx(y + $b) + $a(y + $b)")
                steps.add("Step 2: Multiply out\nxy + ${b}x + ${a}y + ${a * b}")
                steps.add("✅ Result: z = xy + ${b}x + ${a}y + ${a * b}")
                steps.add("Graphing: Since this uses both 'x' and 'y', the app will render a 3D Surface Plot.")
                return steps
            }
        }

        // 1. Finance - Simple Interest
        if (low.startsWith("simple_int(")) {
            val args = low.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val p = args[0].toDoubleOrNull() ?: 0.0
                val r = args[1].toDoubleOrNull() ?: 0.0
                val t = args[2].toDoubleOrNull() ?: 0.0
                steps.add("Formula: Simple Interest = (P * R * T) / 100")
                steps.add("Step 1: Identify Values\n  Principal (P) = ${formatStepValue(p)}\n  Rate (R) = ${formatStepValue(r)}%\n  Time (T) = ${formatStepValue(t)}")
                steps.add("Step 2: Multiply Values\n  Numerator = ${formatStepValue(p)} * ${formatStepValue(r)} * ${formatStepValue(t)} = ${formatStepValue(p * r * t)}")
                steps.add("Step 3: Divide by 100\n  Interest = ${formatStepValue(p * r * t)} / 100 = ${formatStepValue(p * r * t / 100.0)}")
                steps.add("Final Result: ${formatStepValue(p * r * t / 100.0)}")
            }
        }

        // 2. Statistics - Mean
        else if (low.startsWith("mean(")) {
            val nums = low.substringAfter("(").substringBeforeLast(")").split(",").mapNotNull { it.toDoubleOrNull() }
            if (nums.isNotEmpty()) {
                steps.add("Formula: Mean = Σx / n")
                steps.add("Step 1: Sum all numbers\n  Sum = ${nums.joinToString(" + ")} = ${formatStepValue(nums.sum())}")
                steps.add("Step 2: Count numbers\n  Count (n) = ${nums.size}")
                steps.add("Step 3: Divide Sum by Count\n  Mean = ${formatStepValue(nums.sum())} / ${nums.size} = ${formatStepValue(nums.average())}")
                steps.add("Final Result: ${formatStepValue(nums.average())}")
            }
        }

        // 3. Physics - Photon Energy
        else if (low.startsWith("photon_energy(")) {
            val f = low.substringAfter("(").substringBeforeLast(")").toDoubleOrNull() ?: 0.0
            val h = 6.62607015e-34
            steps.add("Formula: E = h * f")
            steps.add("Step 1: Identify Values\n  Planck's Constant (h) = $h\n  Frequency (f) = ${formatStepValue(f)} Hz")
            steps.add("Step 2: Calculate Energy\n  E = ($h) * (${formatStepValue(f)})")
            steps.add("Final Result: ${h * f} Joules")
        }

        // 4. Geometry - Distance Formula
        else if (low.startsWith("distance(")) {
            val args = low.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 4) {
                val x1 = args[0].toDouble(); val y1 = args[1].toDouble()
                val x2 = args[2].toDouble(); val y2 = args[3].toDouble()
                steps.add("Formula: d = √((x2-x1)² + (y2-y1)²)")
                steps.add("Step 1: Subtract coordinates\n  Δx = ${formatStepValue(x2)} - ${formatStepValue(x1)} = ${formatStepValue(x2-x1)}\n  Δy = ${formatStepValue(y2)} - ${formatStepValue(y1)} = ${formatStepValue(y2-y1)}")
                steps.add("Step 2: Square differences\n  Δx² = ${formatStepValue((x2-x1)*(x2-x1))}\n  Δy² = ${formatStepValue((y2-y1)*(y2-y1))}")
                steps.add("Step 3: Add squares\n  Sum = ${formatStepValue((x2-x1).pow(2) + (y2-y1).pow(2))}")
                steps.add("Step 4: Take Square Root\n  d = √(${formatStepValue((x2-x1).pow(2) + (y2-y1).pow(2))}) = ${formatStepValue(sqrt((x2-x1).pow(2) + (y2-y1).pow(2)))}")
                steps.add("Final Result: ${formatStepValue(sqrt((x2-x1).pow(2) + (y2-y1).pow(2)))}")
            }
        }

        // 5. Exponent Multiplication Rule: a^m * a^n = a^(m+n)
        else if (low.startsWith("exponent_rule_mult(") || low.contains("rule(exponent_rule_mult(")) {
            val inner = if (low.contains("rule(")) low.substringAfter("rule(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val aStr = args[0]; val mStr = args[1]; val nStr = args[2]
                val a = aStr.toDoubleOrNull() ?: 2.0
                val m = mStr.toDoubleOrNull() ?: 3.0
                val n = nStr.toDoubleOrNull() ?: 4.0

                steps.add("The Exponent Rule for Multiplication says:\na^m × a^n = a^(m+n)")
                steps.add("Rule: When multiplying powers with the same base, add the exponents.")

                if (aStr != "x" && mStr != "x" && nStr != "x") {
                    steps.add("Example with Digits (a=$a, m=$m, n=$n):")
                    steps.add("Step 1: Calculate individually\n  ${formatStepValue(a)}^${formatStepValue(m)} = ${formatStepValue(a.pow(m))}\n  ${formatStepValue(a)}^${formatStepValue(n)} = ${formatStepValue(a.pow(n))}")
                    steps.add("Step 2: Multiply results\n  ${formatStepValue(a.pow(m))} × ${formatStepValue(a.pow(n))} = ${formatStepValue(a.pow(m) * a.pow(n))}")
                    steps.add("Step 3: Using the rule\n  ${formatStepValue(a)}^(${formatStepValue(m)} + ${formatStepValue(n)}) = ${formatStepValue(a)}^${formatStepValue(m + n)} = ${formatStepValue(a.pow(m + n))}")
                    steps.add("✅ Both methods give the same answer: ${formatStepValue(a.pow(m + n))}")
                }

                val base = if (aStr == "x") 2.0 else a
                steps.add("Visual Expansion (Example with base $base):\n" +
                        "${formatStepValue(base)}^3 = $base × $base × $base\n" +
                        "${formatStepValue(base)}^4 = $base × $base × $base × $base\n" +
                        "Multiplying: ($base × $base × $base)($base × $base × $base × $base)\n" +
                        "= $base^7 = ${formatStepValue(base.pow(7))}")

                steps.add("Simple Graph Visualization (y = $base^x):\n" +
                        "y\n↑\n16 |               *\n 8 |           *\n 4 |       *\n 2 |    *\n 1 | *\n 0 +----------------→ x\n     0 1 2 3 4\nAs the exponent increases, the value grows rapidly.")
            }
        }

        // 6. Normal Distribution PDF
        else if (low.startsWith("normal_dist(")) {
            val args = low.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val x = args[0].toDoubleOrNull()
                val m = args[1].toDouble(); val s = args[2].toDouble()
                if (x != null) {
                    steps.add("Formula: P(x) = (1 / (σ√(2π))) * e^(-0.5 * ((x-μ)/σ)²)")
                    steps.add("Step 1: Identify Parameters\n  Value (x) = ${formatStepValue(x)}\n  Mean (μ) = ${formatStepValue(m)}\n  Std Dev (σ) = ${formatStepValue(s)}")
                    val z = (x - m) / s
                    steps.add("Step 2: Calculate Z-score\n  Z = (x - μ) / σ = (${formatStepValue(x)} - ${formatStepValue(m)}) / ${formatStepValue(s)} = ${formatStepValue(z)}")
                    val expPart = exp(-0.5 * z * z)
                    steps.add("Step 3: Calculate Exponential Part\n  e^(-0.5 * Z²) = ${formatStepValue(expPart)}")
                    val normalizer = 1.0 / (s * sqrt(2 * PI))
                    steps.add("Step 4: Calculate Normalization Constant\n  1 / (σ√(2π)) = ${formatStepValue(normalizer)}")
                    steps.add("Final Result: ${formatStepValue(normalizer * expPart)}")
                }
            }
        }

        // 7. Binomial Expansion (a-b)²
        else if (low.startsWith("sq_bin_minus(") || low.startsWith("expand(sq_bin_minus(")) {
            val inner = if (low.startsWith("expand(")) low.substringAfter("expand(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 2) {
                val aStr = args[0]; val bStr = args[1]
                val a = aStr.toDoubleOrNull() ?: 5.0
                val b = bStr.toDoubleOrNull() ?: 3.0

                steps.add("Goal: Expand (${if (aStr=="x") "x" else formatStepValue(a)} - ${if (bStr=="x") "x" else formatStepValue(b)})²")
                steps.add("The square of a binomial difference is:\n(a - b)²")

                steps.add("Geometric Proof (How the formula is made):")
                steps.add("Imagine a large square with side length 'a'.")
                steps.add("Total Area = a²")
                steps.add("We want to find the area of a square with side (a - b).")
                steps.add("To do this, we remove two rectangles of area 'ab' and then")
                steps.add("add back 'b²' because it was subtracted twice.")
                steps.add("Total Area = a² - ab - ab + b²")
                steps.add("Total Area = a² - 2ab + b²")

                steps.add("Visual Square Breakdown:")
                steps.add("      a-b      b")
                steps.add("   ┌───────┬───────┐")
                steps.add("a-b│ (a-b)²│  b(a-b)│")
                steps.add("   ├───────┼───────┤")
                steps.add(" b │ b(a-b)│  b²   │")
                steps.add("   └───────┴───────┘")
                steps.add("The large square is side 'a'. (a-b)² = a² - 2b(a-b) - b²")

                steps.add("✅ Final Formula:\n(a - b)² = a² - 2ab + b²")

                if (aStr != "x" && bStr != "x") {
                    steps.add("Example with Numbers (a=$a, b=$b):\nLeft side: ($a - $b)² = ${formatStepValue((a-b).pow(2))}\nRight side: ${formatStepValue(a)}² - 2(${formatStepValue(a)})(${formatStepValue(b)}) + ${formatStepValue(b)}²\n= ${formatStepValue(a*a)} - ${formatStepValue(2*a*b)} + ${formatStepValue(b*b)}\n= ${formatStepValue(a*a - 2*a*b + b*b)}")
                }

                val vertex = if (aStr == "x") b else if (bStr == "x") a else b
                steps.add("Function Graph Visualization:\ny = (x - $vertex)²\nThis is a U-shaped parabola.")
            }
        }

        // 8. Binomial Expansion (a+b)²
        else if (low.startsWith("sq_bin_plus(") || low.startsWith("expand(sq_bin_plus(")) {
            val inner = if (low.startsWith("expand(")) low.substringAfter("expand(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 2) {
                val aStr = args[0]; val bStr = args[1]
                val a = aStr.toDoubleOrNull() ?: 5.0
                val b = bStr.toDoubleOrNull() ?: 3.0

                steps.add("Goal: Expand (${if (aStr=="x") "x" else formatStepValue(a)} + ${if (bStr=="x") "x" else formatStepValue(b)})²")
                steps.add("The square of a binomial sum is:\n(a + b)²")

                steps.add("Geometric Proof (How the formula is made):")
                steps.add("Imagine a large square with side length (a + b).")
                steps.add("The total area is (a + b) × (a + b) = (a + b)².")
                steps.add("Now, divide this square into 4 parts:")
                steps.add("1. A square with side 'a' → Area = a²")
                steps.add("2. A square with side 'b' → Area = b²")
                steps.add("3. Two rectangles with sides 'a' and 'b' → Area = a×b + a×b = 2ab")
                steps.add("Total Area = a² + 2ab + b²")

                steps.add("Visual Square Breakdown:")
                steps.add("      a        b")
                steps.add("   ┌───────┬───────┐")
                steps.add(" a │  a²   │  ab   │")
                steps.add("   ├───────┼───────┤")
                steps.add(" b │  ab   │  b²   │")
                steps.add("   └───────┴───────┘")

                steps.add("✅ Final Formula:\n(a + b)² = a² + 2ab + b²")

                if (aStr != "x" && bStr != "x") {
                    steps.add("Example with Numbers (a=$a, b=$b):\nLeft side: ($a + $b)² = ${formatStepValue((a+b).pow(2))}\nRight side: ${formatStepValue(a)}² + 2(${formatStepValue(a)})(${formatStepValue(b)}) + ${formatStepValue(b)}²\n= ${formatStepValue(a*a)} + ${formatStepValue(2*a*b)} + ${formatStepValue(b*b)}\n= ${formatStepValue(a*a + 2*a*b + b*b)}")
                }

                val vertex = if (aStr == "x") -b else if (bStr == "x") -a else -b
                steps.add("Function Graph Visualization:\ny = (x + ${abs(vertex)})²\nThis is a U-shaped parabola.")
            }
        }

        // 9. Fractional Exponents: a^(n/d) = root_d(a^n)
        else if (low.startsWith("fractional_exp(") || low.contains("result(fractional_exp(")) {
            val inner = if (low.contains("result(")) low.substringAfter("result(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val aStr = args[0]; val nStr = args[1]; val dStr = args[2]
                val a = aStr.toDoubleOrNull() ?: 8.0
                val n = nStr.toDoubleOrNull() ?: 1.0
                val d = dStr.toDoubleOrNull() ?: 3.0

                steps.add("A fractional exponent means:\na^(n/d) = root_d(a^n)")
                steps.add("Rule: a^(n/d) = (root_d(a))^n\nwhere a = base, n = numerator, d = denominator")

                if (aStr != "x" && nStr != "x" && dStr != "x") {
                    steps.add("Example Illustration:")
                    steps.add("${formatStepValue(a)}^(${formatStepValue(n)}/${formatStepValue(d)})\n= (root_${formatStepValue(d)}(${formatStepValue(a)}))^${formatStepValue(n)}")
                    val root = a.pow(1.0/d)
                    steps.add("= (${formatStepValue(root)})^${formatStepValue(n)}")
                    steps.add("= ${formatStepValue(a.pow(n/d))}")

                    steps.add("\nAdditional Examples:")
                    steps.add("8^(1/3) = root_3(8) = 2")
                    steps.add("16^(3/2) = (√16)^3 = 4^3 = 64")
                    steps.add("27^(2/3) = (root_3(27))^2 = 3^2 = 9")
                }
            }
        }

        // 10. Rectangle Area Geometric Proof
        else if (low.startsWith("area(") && (low.contains("*") || low.contains("×"))) {
            val inner = low.substringAfter("area(").substringBeforeLast(")")
            val parts = inner.split("*", "×")
            val l = parts.getOrNull(0)?.toDoubleOrNull() ?: 5.0
            val b = parts.getOrNull(1)?.toDoubleOrNull() ?: 3.0

            steps.add("Formula: Area of a Rectangle = Length × Breadth (l × b)")
            steps.add("Geometric Explanation:")
            steps.add("The area represents the total number of unit squares that fit inside the rectangle.")

            steps.add("Visual Illustration ($l × $b):")
            val grid = buildString {
                append("   ┌" + "─".repeat(l.toInt().coerceAtMost(10)) + "┐\n")
                for (i in 0 until b.toInt().coerceAtMost(5)) {
                    append("   │" + ".".repeat(l.toInt().coerceAtMost(10)) + "│\n")
                }
                append("   └" + "─".repeat(l.toInt().coerceAtMost(10)) + "┘")
            }
            steps.add(grid)

            steps.add("\nCalculation:")
            steps.add("Area = $l × $b = ${formatStepValue(l * b)}")
            steps.add("\n✅ Final Result: ${formatStepValue(l * b)}")
        }

        // 11. Triangle Area Geometric Proof
        else if (low.contains("1/2*b*h") || low.startsWith("triangle(")) {
            val inner = if (low.startsWith("area(")) low.substringAfter("area(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")

            // Try to extract numbers from 1/2*b*h format
            val b: Double
            val h: Double
            if (low.contains("1/2*")) {
                val parts = low.substringAfter("1/2*").split("*", "×", "/", "=")
                b = parts.getOrNull(0)?.toDoubleOrNull() ?: 5.0
                h = parts.getOrNull(1)?.toDoubleOrNull() ?: 3.0
            } else {
                b = args.getOrNull(0)?.toDoubleOrNull() ?: 5.0
                h = args.getOrNull(1)?.toDoubleOrNull() ?: 3.0
            }

            steps.add("Formula: Area of a Triangle = 1/2 × Base × Height")
            steps.add("Geometric Proof (How the formula is made):")
            steps.add("1. Imagine a rectangle with side lengths 'b' (base) and 'h' (height).")
            steps.add("2. The area of this rectangle is Base × Height (b × h).")
            steps.add("3. If you draw a diagonal line across the rectangle, it splits the rectangle into two equal triangles.")
            steps.add("4. Since the triangle is exactly half of the rectangle, its area must be 1/2 of (Base × Height).")

            steps.add("Visual Illustration:")
            steps.add("   ┌───────────────┐")
            steps.add("   │ ◢           . │")
            steps.add(" h │   ◢       .   │")
            steps.add("   │     ◢   .     │")
            steps.add("   └───────────────┘")
            steps.add("           b")
            steps.add("The shaded triangle is half of the rectangle area.")

            steps.add("\nCalculation for b=$b, h=$h:")
            steps.add("Area = 1/2 × $b × $h = ${formatStepValue(0.5 * b * h)}")
        }

        // 11. Triangle Perimeter Proof
        else if (low.contains("a+b+c") || low.startsWith("perimeter(")) {
            val inner = if (low.startsWith("perimeter(")) low.substringAfter("(").substringBeforeLast(")") else low
            val args = inner.split("+", "×", "*", ",")

            val a = args.getOrNull(0)?.toDoubleOrNull() ?: 3.0
            val b = args.getOrNull(1)?.toDoubleOrNull() ?: 4.0
            val c = args.getOrNull(2)?.toDoubleOrNull() ?: 5.0

            steps.add("Formula: Perimeter of a Triangle = a + b + c")
            steps.add("The perimeter is the total distance around the outside of the triangle.")

            steps.add("Visual Side Labels:")
            steps.add("        /\\")
            steps.add("     b /  \\ a")
            steps.add("      /____\\")
            steps.add("         c")

            steps.add("\nCalculation with digits:")
            steps.add("Step 1: Identify sides")
            steps.add("  a = ${formatStepValue(a)}")
            steps.add("  b = ${formatStepValue(b)}")
            steps.add("  c = ${formatStepValue(c)}")

            steps.add("Step 2: Add all sides")
            steps.add("  Perimeter = $a + $b + $c = ${formatStepValue(a + b + c)}")
            steps.add("Final Result: ${formatStepValue(a + b + c)}")
        }

        // 12. Arithmetic Sequence Proof
        else if (low.startsWith("arith_seq(") || low.contains("termn(")) {
            val inner = if (low.contains("termn(")) low.substringAfter("termn(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val a = args[0].toDoubleOrNull() ?: 1.0
                val d = args[1].toDoubleOrNull() ?: 1.0
                val n = args[2].toDoubleOrNull() ?: 1.0

                steps.add("Formula: n-th term of an Arithmetic Sequence")
                steps.add("a_n = a + (n - 1)d")
                steps.add("Where:")
                steps.add("  a = First term")
                steps.add("  d = Common difference")
                steps.add("  n = Term number")

                steps.add("\nCalculation for digits:")
                steps.add("Step 1: Identify values")
                steps.add("  a = $a, d = $d, n = $n")

                steps.add("Step 2: Apply formula")
                steps.add("  a_$n = $a + ($n - 1) * $d")
                steps.add("  a_$n = $a + (${n - 1}) * $d")
                steps.add("  a_$n = $a + ${(n - 1) * d}")
                steps.add("  a_$n = ${a + (n - 1) * d}")

                steps.add("\n✅ Final Result: ${formatStepValue(a + (n - 1) * d)}")
            }
        }

        // 13. Fibonacci Sequence Steps
        else if (low.startsWith("fib(") || low.contains("fib(n)")) {
            val inner = if (low.contains("fib(n)")) low.substringAfter("fib(n)").substringBeforeLast(")") else low
            val nVal = inner.substringAfter("(").substringBeforeLast(")").toDoubleOrNull() ?: 10.0
            val n = nVal.toInt()

            steps.add("Formula: Fibonacci Number Fib(n)")
            steps.add("The Fibonacci sequence starts with 0 and 1, and each subsequent number is the sum of the previous two.")
            steps.add("Definition: F(n) = F(n-1) + F(n-2)")

            if (n >= 0 && n <= 50) {
                steps.add("\nCalculation for n = $n:")
                var a = 0L; var b = 1L
                val seq = mutableListOf<Long>()
                for (i in 0..n) {
                    seq.add(a)
                    val temp = a + b
                    a = b
                    b = temp
                }
                steps.add("Step 1: List numbers up to n")
                steps.add("  " + seq.take(15).joinToString(", ") + (if(n > 14) "..." else ""))
                steps.add("\n✅ Result: Fib($n) = ${seq.last()}")
            } else {
                steps.add("\nResult: Fib($n) is too large to list steps.")
            }
        }

        // 14. Ideal Gas Law (Find P)
        else if (low.startsWith("gas_p(")) {
            val args = low.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val v = args[0].toDouble(); val n = args[1].toDouble(); val t = args[2].toDouble()
                val r = 8.314462618
                steps.add("Formula: P = (nRT) / V")
                steps.add("Step 1: Identify Constants\n  Ideal Gas Constant (R) = $r")
                steps.add("Step 2: Multiply Numerator\n  n * R * T = ${formatStepValue(n)} * $r * ${formatStepValue(t)} = ${formatStepValue(n * r * t)}")
                steps.add("Step 3: Divide by Volume\n  P = ${formatStepValue(n * r * t)} / ${formatStepValue(v)} = ${formatStepValue(n * r * t / v)}")
                steps.add("Final Result: ${formatStepValue(n * r * t / v)} Pa")
            }
        }

        // 10. Expansion - FOIL & General Expand
        else if (low.startsWith("foil(")) {
            val args = low.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 4) {
                val a = args[0].toDouble(); val b = args[1].toDouble()
                val c = args[2].toDouble(); val d = args[3].toDouble()
                steps.add("FOIL Expansion: (${formatStepValue(a)}x + ${formatStepValue(b)})(${formatStepValue(c)}x + ${formatStepValue(d)})")
                steps.add("Step 1: First terms\n  ${formatStepValue(a)}x * ${formatStepValue(c)}x = ${formatStepValue(a * c)}x²")
                steps.add("Step 2: Outer terms\n  ${formatStepValue(a)}x * ${formatStepValue(d)} = ${formatStepValue(a * d)}x")
                steps.add("Step 3: Inner terms\n  ${formatStepValue(b)} * ${formatStepValue(c)}x = ${formatStepValue(b * c)}x")
                steps.add("Step 4: Last terms\n  ${formatStepValue(b)} * ${formatStepValue(d)} = ${formatStepValue(b * d)}")
                steps.add("Step 5: Combine like terms\n  ${formatStepValue(a * c)}x² + (${formatStepValue(a * d)} + ${formatStepValue(b * c)})x + ${formatStepValue(b * d)}")
                steps.add("Final Result: ${formatStepValue(a * c)}x² + ${formatStepValue(a * d + b * c)}x + ${formatStepValue(b * d)}")
            }
        }
        else if (low.startsWith("expand(")) {
            val nums = low.substringAfter("(").substringBeforeLast(")").split(",").mapNotNull { it.toDoubleOrNull() }
            if (nums.size >= 2) {
                steps.add("Expanding linear factors: " + nums.joinToString("") { "(x + ${formatStepValue(it)})" })

                // Show steps for multiplying them in pairs
                if (nums.size == 4) {
                    val k1 = nums[0]; val k2 = nums[1]; val k3 = nums[2]; val k4 = nums[3]
                    steps.add("Step 1: Multiply first two factors\n  (x + ${formatStepValue(k1)})(x + ${formatStepValue(k2)}) = x² + ${formatStepValue(k1 + k2)}x + ${formatStepValue(k1 * k2)}")
                    steps.add("Step 2: Multiply second two factors\n  (x + ${formatStepValue(k3)})(x + ${formatStepValue(k4)}) = x² + ${formatStepValue(k3 + k4)}x + ${formatStepValue(k3 * k4)}")

                    val q1 = doubleArrayOf(k1 * k2, k1 + k2, 1.0)
                    val q2 = doubleArrayOf(k3 * k4, k3 + k4, 1.0)

                    steps.add("Step 3: Multiply resulting quadratics\n  (x² + ${formatStepValue(k1 + k2)}x + ${formatStepValue(k1 * k2)})(x² + ${formatStepValue(k3 + k4)}x + ${formatStepValue(k3 * k4)})")
                    steps.add("Distributing first term:\n  x²(x² + ${formatStepValue(k3 + k4)}x + ${formatStepValue(k3 * k4)}) = x⁴ + ${formatStepValue(k3 + k4)}x³ + ${formatStepValue(k3 * k4)}x²")
                    steps.add("Distributing second term:\n  ${formatStepValue(k1 + k2)}x(x² + ${formatStepValue(k3 + k4)}x + ${formatStepValue(k3 * k4)}) = ${formatStepValue(k1 + k2)}x³ + ${formatStepValue((k1 + k2) * (k3 + k4))}x² + ${formatStepValue((k1 + k2) * k3 * k4)}x")
                    steps.add("Distributing third term:\n  ${formatStepValue(k1 * k2)}(x² + ${formatStepValue(k3 + k4)}x + ${formatStepValue(k3 * k4)}) = ${formatStepValue(k1 * k2)}x² + ${formatStepValue(k1 * k2 * (k3 + k4))}x + ${formatStepValue(k1 * k2 * k3 * k4)}")

                    val finalPoly = CalculatorHelper.evaluateFull(expression)
                    steps.add("Step 4: Combine all like terms")
                    steps.add("Final Result: $finalPoly")
                } else {
                    val finalPoly = CalculatorHelper.evaluateFull(expression)
                    steps.add("Step 1: Distribute and multiply all terms successively.")
                    steps.add("Final Result: $finalPoly")
                }
            }
        }

        // 14. Factorial Steps
        else if (low.startsWith("fact(") || low.contains("result(fact(")) {
            val inner = if (low.contains("result(")) low.substringAfter("result(").substringBeforeLast(")") else low
            val nVal = inner.substringAfter("(").substringBeforeLast(")").toDoubleOrNull() ?: 5.0
            val n = nVal.toInt()

            steps.add("Formula: Factorial n!")
            steps.add("The factorial of a non-negative integer n is the product of all positive integers less than or equal to n.")
            steps.add("Definition: n! = n × (n-1) × (n-2) × ... × 1")

            if (n >= 0 && n <= 20) {
                steps.add("\nCalculation for n = $n:")
                val factors = (n downTo 1).toList()
                if (factors.isEmpty()) {
                    steps.add("0! is defined as 1.")
                    steps.add("\n✅ Result: 0! = 1")
                } else {
                    steps.add("Step 1: Multiply all integers from $n down to 1")
                    steps.add("  " + factors.joinToString(" × "))
                    val res = (1..n).fold(1L) { acc, i -> acc * i }
                    steps.add("\n✅ Result: $n! = $res")
                }
            } else if (n < 0) {
                steps.add("Error: Factorial is not defined for negative numbers.")
            } else {
                steps.add("\nResult: $n! is very large. Use scientific notation.")
                val res = CalculatorHelper.evaluateFull(inner)
                steps.add("Result: $res")
            }
        }

        // 15. Combination (nCr) Steps
        else if (low.startsWith("comb(") || low.contains("result(comb(")) {
            val inner = if (low.contains("result(")) low.substringAfter("result(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 2) {
                val nVal = args[0].toDoubleOrNull() ?: 10.0
                val rVal = args[1].toDoubleOrNull() ?: 3.0
                val n = nVal.toInt(); val r = rVal.toInt()

                steps.add("Formula: Combination (nCr)")
                steps.add("nCr = n! / (r! × (n - r)!)")
                steps.add("Where n is the total items and r is the number of items chosen.")

                if (n >= 0 && r >= 0 && r <= n && n <= 30) {
                    steps.add("\nCalculation for n=$n, r=$r:")
                    steps.add("Step 1: Calculate Factorials")
                    steps.add("  n! = $n! = ${CalculatorHelper.evaluateFull("fact($n)")}")
                    steps.add("  r! = $r! = ${CalculatorHelper.evaluateFull("fact($r)")}")
                    steps.add("  (n - r)! = ${n - r}! = ${CalculatorHelper.evaluateFull("fact(${n - r})")}")

                    steps.add("Step 2: Apply Formula")
                    val den = (1..r).fold(1.0) { acc, i -> acc * i } * (1..(n-r)).fold(1.0) { acc, i -> acc * i }
                    val num = (1..n).fold(1.0) { acc, i -> acc * i }
                    steps.add("  nCr = $num / (${CalculatorHelper.evaluateFull("fact($r)")} × ${CalculatorHelper.evaluateFull("fact(${n - r})")})")
                    steps.add("  nCr = $num / $den")

                    val res = num / den
                    steps.add("\n✅ Final Result: nCr = ${formatStepValue(res)}")
                } else if (r > n) {
                    steps.add("Error: r cannot be greater than n.")
                } else {
                    val res = CalculatorHelper.evaluateFull(inner)
                    steps.add("\n✅ Result: nCr = $res")
                }
            }
        }

        // 16. Binomial Probability Steps
        else if (low.startsWith("binom_prob(") || low.contains("p(x=k")) {
            val inner = if (low.contains("p(x=k")) low.substringAfter("p(x=k").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val n = args[0].toDoubleOrNull() ?: 10.0
                val k = args[1].toDoubleOrNull() ?: 5.0
                val p = args[2].toDoubleOrNull() ?: 0.5

                steps.add("Formula: Binomial Probability P(X = k)")
                steps.add("P(X = k) = (nCk) × p^k × (1-p)^(n-k)")
                steps.add("Where:")
                steps.add("  n = Number of trials")
                steps.add("  k = Number of successes")
                steps.add("  p = Probability of success")

                steps.add("\nCalculation for n=$n, k=$k, p=$p:")
                steps.add("Step 1: Calculate Combinations (nCk)")
                val nCr = CalculatorHelper.evaluateFull("comb($n, $k)")
                steps.add("  $n C $k = $nCr")

                steps.add("Step 2: Calculate Power of p")
                val pk = p.pow(k)
                steps.add("  $p^$k = ${formatStepValue(pk)}")

                steps.add("Step 3: Calculate Power of (1-p)")
                val q = 1 - p
                val qnk = q.pow(n - k)
                steps.add("  (1 - $p)^($n - $k) = $q^${n-k} = ${formatStepValue(qnk)}")

                steps.add("Step 4: Multiply all parts")
                val finalRes = nCr.toString().toDouble() * pk * qnk
                steps.add("  Result = $nCr × ${formatStepValue(pk)} × ${formatStepValue(qnk)}")

                steps.add("\n✅ Final Result: P(X = $k) = ${formatStepValue(finalRes)}")
            }
        }

        // 17. Completing the Square Steps
        else if (low.startsWith("completing_sq(") || low.contains("vertexform(")) {
            val inner = if (low.contains("vertexform(")) low.substringAfter("vertexform(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val a = args[0].toDoubleOrNull() ?: 1.0
                val b = args[1].toDoubleOrNull() ?: 2.0
                val c = args[2].toDoubleOrNull() ?: 1.0

                steps.add("Goal: Rewrite $a*x² + $b*x + $c in Vertex Form")
                steps.add("Formula: y = a(x - h)² + k")
                steps.add("Where:")
                steps.add("  h = -b / (2a)")
                steps.add("  k = c - (b² / 4a)")

                steps.add("\nStep 1: Calculate h")
                val h = -b / (2 * a)
                steps.add("  h = -($b) / (2 * $a) = ${formatStepValue(h)}")

                steps.add("Step 2: Calculate k")
                val k = c - (b * b) / (4 * a)
                steps.add("  k = $c - ($b² / (4 * $a)) = ${formatStepValue(k)}")

                val hStr = if (h >= 0) "- ${formatStepValue(h)}" else "+ ${formatStepValue(abs(h))}"
                steps.add("\n✅ Vertex Form:")
                steps.add("  y = ${formatStepValue(a)}(x $hStr)² + ${formatStepValue(k)}")
                steps.add("  Vertex (h, k) = (${formatStepValue(h)}, ${formatStepValue(k)})")

                steps.add("\nGraphing: This forms a parabola with vertex at (${formatStepValue(h)}, ${formatStepValue(k)}).")
            }
        }

        // 18. Perfect Square Trinomial Steps
        else if (low.contains("(a+b)^2=a^2+2ab+b^2")) {
            steps.add("Formula: Perfect Square Trinomial Identity")
            steps.add("(a + b)² = a² + 2ab + b²")

            steps.add("\nProof by Expansion:")
            steps.add("Step 1: Write (a + b)² as a product")
            steps.add("  (a + b)² = (a + b)(a + b)")

            steps.add("Step 2: Distribute (FOIL)")
            steps.add("  (a + b)(a + b) = a(a + b) + b(a + b)")
            steps.add("  = (a × a) + (a × b) + (b × a) + (b × b)")
            steps.add("  = a² + ab + ab + b²")

            steps.add("Step 3: Combine like terms")
            steps.add("  = a² + 2ab + b²")

            steps.add("\n✅ Identity Proven: (a + b)² = a² + 2ab + b²")

            steps.add("\nGeometric Insight:")
            steps.add("This is the area of a square with side length (a+b).")
            steps.add("Check the Graph button to see the geometric breakdown!")
        }

        // 19. Prime Number Check Steps
        else if (low.startsWith("is_prime(") || low.contains("check(is_prime(")) {
            val inner = if (low.contains("check(")) low.substringAfter("check(").substringBeforeLast(")") else low
            val nVal = inner.substringAfter("(").substringBeforeLast(")").toDoubleOrNull() ?: 7.0
            val n = nVal.toInt()

            steps.add("Goal: Check if $n is a Prime Number")
            steps.add("A Prime Number is a natural number greater than 1 that has no positive divisors other than 1 and itself.")

            if (n <= 1) {
                steps.add("\nResult: $n is not prime.")
                steps.add("Reason: Prime numbers must be greater than 1.")
            } else {
                steps.add("\nStep-by-Step Check:")
                steps.add("1. Is $n divisible by 2 or 3?")
                if (n > 3 && (n % 2 == 0 || n % 3 == 0)) {
                    val divisor = if (n % 2 == 0) 2 else 3
                    steps.add("   Yes, $n is divisible by $divisor.")
                    steps.add("\n✅ Final Result: $n is NOT a Prime Number.")
                } else {
                    steps.add("   No.")
                    steps.add("2. Test divisors from 5 up to √$n (≈ ${formatStepValue(sqrt(n.toDouble()))})")

                    var isPrime = true
                    var divisorFound = -1
                    if (n > 3) {
                        var i = 5
                        while (i * i <= n) {
                            if (n % i == 0) { isPrime = false; divisorFound = i; break }
                            if (n % (i + 2) == 0) { isPrime = false; divisorFound = i + 2; break }
                            i += 6
                        }
                    }

                    if (isPrime) {
                        steps.add("   No divisors found in range.")
                        steps.add("\n✅ Final Result: $n is a PRIME Number.")
                    } else {
                        steps.add("   Found divisor: $divisorFound")
                        steps.add("\n✅ Final Result: $n is NOT a Prime Number.")
                    }
                }
            }
        }

        // 20. Pythagorean Triple Steps
        else if (low.startsWith("pyth_triple(") || low.contains("check(pyth_triple(")) {
            val inner = if (low.contains("check(")) low.substringAfter("check(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val a = args[0].toDoubleOrNull() ?: 3.0
                val b = args[1].toDoubleOrNull() ?: 4.0
                val c = args[2].toDoubleOrNull() ?: 5.0

                steps.add("Goal: Check if ($a, $b, $c) is a Pythagorean Triple")
                steps.add("Formula: a² + b² = c²")
                steps.add("A Pythagorean Triple consists of three positive integers a, b, and c, such that a² + b² = c².")

                steps.add("\nStep 1: Square each number")
                steps.add("  a² = $a² = ${formatStepValue(a * a)}")
                steps.add("  b² = $b² = ${formatStepValue(b * b)}")
                steps.add("  c² = $c² = ${formatStepValue(c * c)}")

                steps.add("\nStep 2: Add a² and b²")
                steps.add("  a² + b² = ${formatStepValue(a * a)} + ${formatStepValue(b * b)} = ${formatStepValue(a * a + b * b)}")

                steps.add("\nStep 3: Compare with c²")
                val lhs = a * a + b * b
                val rhs = c * c
                if (abs(lhs - rhs) < 1e-9) {
                    steps.add("  ${formatStepValue(lhs)} = ${formatStepValue(rhs)}")
                    steps.add("\n✅ Result: ($a, $b, $c) is a VALID Pythagorean Triple.")
                } else {
                    steps.add("  ${formatStepValue(lhs)} ≠ ${formatStepValue(rhs)}")
                    steps.add("\n✅ Result: ($a, $b, $c) is NOT a Pythagorean Triple.")
                }

                steps.add("\nGeometric Proof:")
                steps.add("If valid, these numbers represent the side lengths of a RIGHT TRIANGLE.")
                steps.add("Check the Graph button to see the triangle visualization!")
            }
        }

        // 21. Product to Sum Identity Steps
        else if (low.startsWith("cosacosb(") || low.contains("producttosum(")) {
            val inner = if (low.contains("producttosum(")) low.substringAfter("producttosum(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 2) {
                val aVal = args[0].toDoubleOrNull() ?: 60.0
                val bVal = args[1].toDoubleOrNull() ?: 30.0

                steps.add("Formula: Product to Sum Identity")
                steps.add("2cos(A)cos(B) = cos(A - B) + cos(A + B)")

                steps.add("\nCalculation for A=$aVal, B=$bVal:")
                steps.add("Step 1: Calculate (A - B)")
                steps.add("  A - B = $aVal - $bVal = ${aVal - bVal}")

                steps.add("Step 2: Calculate (A + B)")
                steps.add("  A + B = $aVal + $bVal = ${aVal + bVal}")

                steps.add("Step 3: Apply the identity")
                steps.add("  2cos($aVal)cos($bVal) = cos(${aVal - bVal}) + cos(${aVal + bVal})")

                val resA = cos(Math.toRadians(aVal - bVal))
                val resB = cos(Math.toRadians(aVal + bVal))
                steps.add("  = ${formatStepValue(resA)} + ${formatStepValue(resB)}")
                steps.add("  = ${formatStepValue(resA + resB)}")

                steps.add("\n✅ Final Result: ${formatStepValue(resA + resB)}")
            }
        }

        // 22. De Moivre's Theorem Steps
        else if (low.startsWith("demoivre(") || low.contains("powcomplex(")) {
            val inner = if (low.contains("powcomplex(")) low.substringAfter("powcomplex(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 3) {
                val r = args[0].toDoubleOrNull() ?: 1.0
                val theta = args[1].toDoubleOrNull() ?: 45.0
                val n = args[2].toDoubleOrNull() ?: 2.0

                steps.add("Formula: De Moivre's Theorem")
                steps.add("[r(cos θ + i sin θ)]ⁿ = rⁿ(cos(nθ) + i sin(nθ))")

                steps.add("\nCalculation for r=$r, θ=$theta°, n=$n:")
                steps.add("Step 1: Calculate rⁿ")
                val rn = r.pow(n)
                steps.add("  $r^$n = ${formatStepValue(rn)}")

                steps.add("Step 2: Calculate nθ")
                val nTheta = n * theta
                steps.add("  $n × $theta° = $nTheta°")

                steps.add("Step 3: Apply the theorem")
                steps.add("  Result = ${formatStepValue(rn)}(cos($nTheta°) + i sin($nTheta°))")

                val real = rn * cos(Math.toRadians(nTheta))
                val imag = rn * sin(Math.toRadians(nTheta))
                steps.add("  = ${formatStepValue(rn)}(${formatStepValue(cos(Math.toRadians(nTheta)))} + i ${formatStepValue(sin(Math.toRadians(nTheta)))})")

                val resStr = if (abs(imag) < 1e-9) formatStepValue(real)
                else if (real == 0.0) "${formatStepValue(imag)}i"
                else "${formatStepValue(real)} + ${formatStepValue(imag)}i"

                steps.add("\n✅ Final Result: $resStr")
            }
        }

        // 23. Find Opposite Side Steps
        else if (low.startsWith("right_tri_find_opp(") || low.contains("opposite(")) {
            val inner = if (low.contains("opposite(")) low.substringAfter("opposite(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 2) {
                val angle = args[0].toDoubleOrNull() ?: 30.0
                val hyp = args[1].toDoubleOrNull() ?: 10.0

                steps.add("Formula: Find Opposite Side (Right Triangle)")
                steps.add("Opposite = Hypotenuse × sin(Angle)")

                steps.add("\nCalculation for Angle = $angle°, Hypotenuse = $hyp:")
                steps.add("Step 1: Convert Angle to Radians (if needed)")
                val rad = Math.toRadians(angle)
                steps.add("  Radians = $angle × π / 180 = ${formatStepValue(rad)}")

                steps.add("Step 2: Calculate sin(Angle)")
                val s = sin(rad)
                steps.add("  sin($angle°) = ${formatStepValue(s)}")

                steps.add("Step 3: Multiply by Hypotenuse")
                val res = hyp * s
                steps.add("  Opposite = $hyp × ${formatStepValue(s)} = ${formatStepValue(res)}")

                steps.add("\n✅ Final Result: Opposite Side = ${formatStepValue(res)}")

                steps.add("\nGeometric Insight:")
                steps.add("The 'Opposite' is the side directly across from the given angle in a right triangle.")
            }
        }

        // 24. Find Adjacent Side Steps
        else if (low.startsWith("right_tri_find_adj(") || low.contains("adjacent(")) {
            val inner = if (low.contains("adjacent(")) low.substringAfter("adjacent(").substringBeforeLast(")") else low
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 2) {
                val angle = args[0].toDoubleOrNull() ?: 30.0
                val hyp = args[1].toDoubleOrNull() ?: 10.0

                steps.add("Formula: Find Adjacent Side (Right Triangle)")
                steps.add("Adjacent = Hypotenuse × cos(Angle)")

                steps.add("\nCalculation for Angle = $angle°, Hypotenuse = $hyp:")
                steps.add("Step 1: Convert Angle to Radians (if needed)")
                val rad = Math.toRadians(angle)
                steps.add("  Radians = $angle × π / 180 = ${formatStepValue(rad)}")

                steps.add("Step 2: Calculate cos(Angle)")
                val c = cos(rad)
                steps.add("  cos($angle°) = ${formatStepValue(c)}")

                steps.add("Step 3: Multiply by Hypotenuse")
                val res = hyp * c
                steps.add("  Adjacent = $hyp × ${formatStepValue(c)} = ${formatStepValue(res)}")

                steps.add("\n✅ Final Result: Adjacent Side = ${formatStepValue(res)}")

                steps.add("\nGeometric Insight:")
                steps.add("The 'Adjacent' is the side next to the given angle (and not the hypotenuse).")
            }
        }

        // 25. Double Angle sin(2A) Steps
        else if (low.startsWith("sin2a(") || low.contains("sin(2a)")) {
            val inner = if (low.contains("sin(2a)")) low.substringAfter("sin(2a)").substringBeforeLast(")") else low
            val aVal = inner.substringAfter("(").substringBeforeLast(")").toDoubleOrNull() ?: 30.0

            steps.add("Formula: Double Angle Identity for Sine")
            steps.add("sin(2A) = 2 sin(A) cos(A)")

            steps.add("\nCalculation for A = $aVal°:")
            steps.add("Step 1: Calculate sin(A) and cos(A)")
            val s = sin(Math.toRadians(aVal))
            val c = cos(Math.toRadians(aVal))
            steps.add("  sin($aVal°) = ${formatStepValue(s)}")
            steps.add("  cos($aVal°) = ${formatStepValue(c)}")

            steps.add("Step 2: Apply the formula")
            val res = 2.0 * s * c
            steps.add("  sin(2 × $aVal°) = 2 × ${formatStepValue(s)} × ${formatStepValue(c)}")
            steps.add("  = ${formatStepValue(res)}")

            steps.add("\n✅ Final Result: sin(${2*aVal}°) = ${formatStepValue(res)}")

            val direct = sin(Math.toRadians(2 * aVal))
            steps.add("\nVerification: sin(${2*aVal}°) directly is ${formatStepValue(direct)}")
        }

        // 26. Double Angle cos(2A) Steps
        else if (low.startsWith("cos2a(") || low.contains("cos(2a)")) {
            val inner = if (low.contains("cos(2a)")) low.substringAfter("cos(2a)").substringBeforeLast(")") else low
            val aVal = inner.substringAfter("(").substringBeforeLast(")").toDoubleOrNull() ?: 30.0

            steps.add("Formula: Double Angle Identity for Cosine")
            steps.add("cos(2A) = cos²(A) - sin²(A)")

            steps.add("\nCalculation for A = $aVal°:")
            steps.add("Step 1: Calculate sin(A) and cos(A)")
            val s = sin(Math.toRadians(aVal))
            val c = cos(Math.toRadians(aVal))
            steps.add("  sin($aVal°) = ${formatStepValue(s)}")
            steps.add("  cos($aVal°) = ${formatStepValue(c)}")

            steps.add("Step 2: Square the results")
            steps.add("  cos²($aVal°) = ${formatStepValue(c * c)}")
            steps.add("  sin²($aVal°) = ${formatStepValue(s * s)}")

            steps.add("Step 3: Subtract sin²(A) from cos²(A)")
            val res = c * c - s * s
            steps.add("  cos(2 × $aVal°) = ${formatStepValue(c * c)} - ${formatStepValue(s * s)}")
            steps.add("  = ${formatStepValue(res)}")

            steps.add("\n✅ Final Result: cos(${2*aVal}°) = ${formatStepValue(res)}")
        }

        // 27. Half Angle sin(A/2) Steps
        else if (low.startsWith("half_sin(") || low.contains("sin(a/2)")) {
            val inner = if (low.contains("sin(a/2)")) low.substringAfter("sin(a/2)").substringBeforeLast(")") else low
            val aVal = inner.substringAfter("(").substringBeforeLast(")").toDoubleOrNull() ?: 60.0

            steps.add("Formula: Half Angle Identity for Sine")
            steps.add("sin(A / 2) = ±√[(1 - cos A) / 2]")

            steps.add("\nCalculation for A = $aVal°:")
            steps.add("Step 1: Calculate cos(A)")
            val c = cos(Math.toRadians(aVal))
            steps.add("  cos($aVal°) = ${formatStepValue(c)}")

            steps.add("Step 2: Subtract from 1")
            val diff = 1.0 - c
            steps.add("  1 - ${formatStepValue(c)} = ${formatStepValue(diff)}")

            steps.add("Step 3: Divide by 2")
            val div = diff / 2.0
            steps.add("  ${formatStepValue(diff)} / 2 = ${formatStepValue(div)}")

            steps.add("Step 4: Take the square root")
            val res = sqrt(div)
            steps.add("  √${formatStepValue(div)} = ${formatStepValue(res)}")

            steps.add("\n✅ Final Result: sin(${aVal/2}°) = ${formatStepValue(res)}")

            val direct = sin(Math.toRadians(aVal / 2.0))
            steps.add("\nVerification: sin(${aVal/2}°) directly is ${formatStepValue(direct)}")
        }

        // 28. Law of Sines (Sine Rule) Steps
        else if (low.startsWith("sine_rule(") || low.contains("sinerule(") || low.contains("ratio(")) {
            val inner = when {
                low.contains("sinerule(") -> low.substringAfter("sinerule(").substringBeforeLast(")")
                low.contains("ratio(") -> low.substringAfter("ratio(").substringBeforeLast(")")
                else -> low
            }
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 4) {
                val aStr = args[0]; val aDegStr = args[1]
                val bStr = args[2]; val bDegStr = args[3]

                steps.add("Formula: Law of Sines (Sine Rule)")
                steps.add("a / sin(A) = b / sin(B)")
                steps.add("Where a, b are sides and A, B are opposite angles.")

                steps.add("\nGiven Values:")
                steps.add("  Side a = $aStr, Angle A = $aDegStr°")
                steps.add("  Side b = $bStr, Angle B = $bDegStr°")

                steps.add("\nStep 1: Set up the proportion")
                steps.add("  ($aStr) / sin($aDegStr°) = ($bStr) / sin($bDegStr°)")

                val res = CalculatorHelper.evaluateFull(inner)
                steps.add("\nStep 2: Solve for the unknown (x)")
                steps.add("  x = $res")

                steps.add("\n✅ Final Result: $res")
            }
        }

        // 29. Law of Cosines (Cosine Rule) Steps
        else if (low.startsWith("cos_rule(") || low.contains("cosinerule(") || low.contains("c²=")) {
            val inner = when {
                low.contains("cosinerule(") -> low.substringAfter("cosinerule(").substringBeforeLast(")")
                low.contains("c²=") -> low.substringAfter("c²=").substringBeforeLast(")")
                else -> low
            }
            val args = inner.substringAfter("(").substringBeforeLast(")").split(",")
            if (args.size == 4) {
                val aVal = args[0]; val bVal = args[1]; val cVal = args[2]; val cDegVal = args[3]

                steps.add("Formula: Law of Cosines (Cosine Rule)")
                steps.add("c² = a² + b² - 2ab × cos(C)")
                steps.add("Where a, b, c are sides and C is the angle opposite side c.")

                steps.add("\nGiven Values:")
                steps.add("  Side a = $aVal, Side b = $bVal")
                steps.add("  Side c = $cVal, Angle C = $cDegVal°")

                val res = CalculatorHelper.evaluateFull(inner)

                if (cVal == "x") {
                    val a = aVal.toDoubleOrNull() ?: 1.0; val b = bVal.toDoubleOrNull() ?: 1.0
                    val C = cDegVal.toDoubleOrNull() ?: 60.0
                    steps.add("\nStep 1: Square sides a and b")
                    steps.add("  a² = ${formatStepValue(a*a)}, b² = ${formatStepValue(b*b)}")
                    steps.add("\nStep 2: Calculate 2ab × cos(C)")
                    val term = 2 * a * b * cos(Math.toRadians(C))
                    steps.add("  2 × $a × $b × cos($C°) = ${formatStepValue(term)}")
                    steps.add("\nStep 3: Apply the formula")
                    val c2 = a*a + b*b - term
                    steps.add("  c² = ${formatStepValue(a*a)} + ${formatStepValue(b*b)} - ${formatStepValue(term)} = ${formatStepValue(c2)}")
                    steps.add("\nStep 4: Take the square root")
                    steps.add("  c = √${formatStepValue(c2)} = $res")
                } else if (cDegVal == "x") {
                    steps.add("\nStep 1: Rearrange to solve for cos(C)")
                    steps.add("  cos(C) = (a² + b² - c²) / (2ab)")
                    steps.add("\nStep 2: Calculate the fraction")
                    steps.add("  cos(C) = (${aVal}² + ${bVal}² - ${cVal}²) / (2 × $aVal × $bVal)")
                    steps.add("\nStep 3: Take arccos of the result")
                    steps.add("  Angle C = arccos(...) = $res°")
                }

                steps.add("\n✅ Final Result: $res")
            }
        }

        // Add fallback message if it's a known function but not specific logic yet
        if (steps.isEmpty()) {
            val knownFuncs = listOf("area", "volume", "perimeter", "tsa", "csa", "lsa", "circumference")
            if (knownFuncs.any { low.contains(it) }) {
                steps.add("Step-by-Step for this formula is in development.")
                steps.add("General Approach: Substitute the given values into the standard geometric formula.")
                val res = CalculatorHelper.evaluateFull(expression)
                if (res != null) steps.add("Result: $res")
            }
        }

        return steps
    }
}