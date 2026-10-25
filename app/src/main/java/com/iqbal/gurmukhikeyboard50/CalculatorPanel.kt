package com.iqbal.gurmukhikeyboard50

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
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs

class CalculatorPanel(private val context: Context, private val onDismiss: () -> Unit) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.calculator_panel_layout, null)

    private val display: EditText = view.findViewById(R.id.calculator_display)
    private val stepsDisplay: TextView = view.findViewById(R.id.calculator_steps)
    private val formulaDisplay: TextView = view.findViewById(R.id.calculator_formula)
    private val memoryIndicator: TextView = view.findViewById(R.id.memory_indicator)
    private val historyRecyclerView: RecyclerView = view.findViewById(R.id.history_recycler_view_main)
    private val scientificRow1: View = view.findViewById(R.id.scientific_row_1)
    private val scientificRow2: View = view.findViewById(R.id.scientific_row_2)
    private val scientificRow3: View = view.findViewById(R.id.scientific_row_3)
    private val scientificRow4: View = view.findViewById(R.id.scientific_row_4)

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
    private lateinit var historyAdapter: HistoryAdapter

    init {
        setupUI()
    }

    private fun setupUI() {
        display.showSoftInputOnFocus = false

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

        view.findViewById<View>(R.id.graph_calculator_button)?.setOnClickListener {
            showGraphDialog()
        }

        applyCalculatorTheme()
        setupNumbers()
        setupScientificListeners()
        updateMemoryIndicator()
        updateScientificVisibility()
        updateDisplay()
        updateStepsDisplay()
        display.post { display.setSelection(display.text.length) }
    }

    private fun setupNumbers() {
        val numbers = mapOf(
            R.id.button_0 to "0", R.id.button_1 to "1", R.id.button_2 to "2",
            R.id.button_3 to "3", R.id.button_4 to "4", R.id.button_5 to "5",
            R.id.button_6 to "6", R.id.button_7 to "7", R.id.button_8 to "8",
            R.id.button_9 to "9", R.id.button_00 to "00"
        )

        for ((id, value) in numbers) {
            view.findViewById<Button>(id)?.setOnClickListener {
                provideFeedback()
                appendNumber(value)
            }
        }

        view.findViewById<Button>(R.id.button_dot)?.setOnClickListener {
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

        view.findViewById<Button>(R.id.button_add)?.setOnClickListener { provideFeedback(); setOperator("+") }
        view.findViewById<Button>(R.id.button_subtract)?.setOnClickListener { provideFeedback(); setOperator("-") }
        view.findViewById<Button>(R.id.button_multiply)?.setOnClickListener { provideFeedback(); setOperator("×") }
        view.findViewById<Button>(R.id.button_divide)?.setOnClickListener { provideFeedback(); setOperator("÷") }

        view.findViewById<Button>(R.id.button_percent)?.setOnClickListener { provideFeedback(); applyPercentage() }
        view.findViewById<Button>(R.id.button_gst_minus)?.setOnClickListener { provideFeedback(); showGstMinusMenu(it) }
        view.findViewById<Button>(R.id.button_gst_5)?.setOnClickListener { provideFeedback(); applyGST(BigDecimal("5.0")) }
        view.findViewById<Button>(R.id.button_gst_12)?.setOnClickListener { provideFeedback(); applyGST(BigDecimal("12.0")) }
        view.findViewById<Button>(R.id.button_gst_18)?.setOnClickListener { provideFeedback(); applyGST(BigDecimal("18.0")) }
        view.findViewById<Button>(R.id.button_clear)?.setOnClickListener { provideFeedback(); clear() }
        view.findViewById<Button>(R.id.button_correct)?.setOnClickListener { provideFeedback(); backspace() }
        view.findViewById<Button>(R.id.button_equals)?.setOnClickListener {
            provideFeedback()
            val hasVariables = currentInput.contains("x") || currentInput.contains("y")
            if (hasVariables && !currentInput.contains("=")) {
                appendCharacter("=")
            } else {
                calculateResult(true)
            }
        }
        view.findViewById<Button>(R.id.button_equals)?.setOnLongClickListener {
            provideFeedback()
            appendCharacter("=")
            true
        }

        view.findViewById<Button>(R.id.button_plus_minus)?.setOnClickListener {
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

        view.findViewById<Button>(R.id.button_gt)?.setOnClickListener {
            provideFeedback()
            display.setText("GT = ${formatValue(grandTotal)}")
            currentInput = grandTotal.stripTrailingZeros().toPlainString()
            isNewOperation = true
        }

        view.findViewById<Button>(R.id.button_m_plus)?.setOnClickListener {
            provideFeedback()
            val value = getActiveValue()
            memoryValue = memoryValue.add(value)
            isNewOperation = true
            updateMemoryIndicator()
        }
        view.findViewById<Button>(R.id.button_m_minus)?.setOnClickListener {
            provideFeedback()
            val value = getActiveValue()
            memoryValue = memoryValue.subtract(value)
            isNewOperation = true
            updateMemoryIndicator()
        }
        view.findViewById<Button>(R.id.button_mrc)?.setOnClickListener {
            provideFeedback()
            currentInput = memoryValue.stripTrailingZeros().toPlainString()
            isNewOperation = false
            historyExpression = formatValue(memoryValue)
            updateDisplay()
            display.setSelection(display.text.length)
        }
    }

    private fun setupScientificListeners() {
        view.findViewById<Button>(R.id.button_deg_rad)?.let { btn ->
            val savedMode = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("calc_use_radians", false)
            useRadians = savedMode
            btn.text = if (useRadians) "RAD" else "DEG"
            btn.setOnClickListener {
                provideFeedback()
                useRadians = !useRadians
                btn.text = if (useRadians) "RAD" else "DEG"
                PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("calc_use_radians", useRadians).apply()
            }
        }

        view.findViewById<View>(R.id.button_2nd)?.setOnClickListener {
            provideFeedback()
            isSecondSet = !isSecondSet
            val color = if (isSecondSet) Color.parseColor("#4696C2") else Color.parseColor("#212121")
            (it as? Button)?.backgroundTintList = ColorStateList.valueOf(color)

            view.findViewById<Button>(R.id.button_sin)?.text = if (isSecondSet) "asin" else "sin"
            view.findViewById<Button>(R.id.button_cos)?.text = if (isSecondSet) "acos" else "cos"
            view.findViewById<Button>(R.id.button_tan)?.text = if (isSecondSet) "atan" else "tan"
        }

        view.findViewById<View>(R.id.button_parenthesis_open)?.setOnClickListener { provideFeedback(); appendCharacter("(") }
        view.findViewById<View>(R.id.button_parenthesis_close)?.setOnClickListener { provideFeedback(); appendCharacter(")") }
        view.findViewById<View>(R.id.button_sqrt)?.setOnClickListener { provideFeedback(); appendScientificFunction("sqrt") }
        view.findViewById<View>(R.id.button_square)?.setOnClickListener { applyUnary { it.multiply(it) } }
        view.findViewById<View>(R.id.button_pi)?.setOnClickListener { provideFeedback(); currentInput = Math.PI.toString(); updateDisplay(); display.setSelection(display.text.length) }
        view.findViewById<View>(R.id.button_e)?.setOnClickListener { provideFeedback(); currentInput = Math.E.toString(); updateDisplay(); display.setSelection(display.text.length) }
        view.findViewById<View>(R.id.button_power)?.setOnClickListener { provideFeedback(); setOperator("^") }

        view.findViewById<View>(R.id.button_sin)?.setOnClickListener { provideFeedback(); appendScientificFunction(if (isSecondSet) "asin" else "sin") }
        view.findViewById<View>(R.id.button_cos)?.setOnClickListener { provideFeedback(); appendScientificFunction(if (isSecondSet) "acos" else "cos") }
        view.findViewById<View>(R.id.button_tan)?.setOnClickListener { provideFeedback(); appendScientificFunction(if (isSecondSet) "atan" else "tan") }
        view.findViewById<View>(R.id.button_log)?.setOnClickListener { provideFeedback(); appendScientificFunction("log") }
        view.findViewById<View>(R.id.button_ln)?.setOnClickListener { provideFeedback(); appendScientificFunction("ln") }

        view.findViewById<View>(R.id.button_pentagon)?.setOnClickListener { provideFeedback(); appendScientificFunction("pentagon") }
        view.findViewById<View>(R.id.button_hexagon)?.setOnClickListener { provideFeedback(); appendScientificFunction("hexagon") }
        view.findViewById<View>(R.id.button_factorial)?.setOnClickListener { provideFeedback(); appendCharacter("!") }
        view.findViewById<View>(R.id.button_sinh)?.setOnClickListener { provideFeedback(); appendScientificFunction("sinh") }
        view.findViewById<View>(R.id.button_cosh)?.setOnClickListener { provideFeedback(); appendScientificFunction("cosh") }
        view.findViewById<View>(R.id.button_tanh)?.setOnClickListener { provideFeedback(); appendScientificFunction("tanh") }

        view.findViewById<View>(R.id.button_layout)?.setOnClickListener { provideFeedback(); showLayoutMenu(it) }
        view.findViewById<View>(R.id.button_geometry)?.setOnClickListener { provideFeedback(); showGeometryMenuDialog() }

        view.findViewById<View>(R.id.button_x_var)?.setOnClickListener { provideFeedback(); appendCharacter("x") }
        view.findViewById<View>(R.id.button_y_var)?.setOnClickListener { provideFeedback(); appendCharacter("y") }
        view.findViewById<View>(R.id.button_solve)?.setOnClickListener { provideFeedback(); appendCharacter("solve(") }
        view.findViewById<View>(R.id.button_3d_mode)?.setOnClickListener { provideFeedback(); appendCharacter("3d(") }
        view.findViewById<View>(R.id.button_polynomial)?.setOnClickListener { provideFeedback(); showPolynomialMenu(it) }
        view.findViewById<View>(R.id.button_spiral)?.setOnClickListener { provideFeedback(); appendCharacter("spiral(") }
        view.findViewById<View>(R.id.button_nested)?.setOnClickListener { provideFeedback(); appendCharacter("nested(") }
        view.findViewById<View>(R.id.button_animation)?.setOnClickListener { provideFeedback(); showAnimationMenu(it) }
        view.findViewById<View>(R.id.button_comma)?.setOnClickListener { provideFeedback(); appendCharacter(",") }
        view.findViewById<View>(R.id.button_ans)?.setOnClickListener { provideFeedback(); appendCharacter(lastValue.stripTrailingZeros().toPlainString()) }
    }

    private fun showPolynomialMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(context, view)
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

    private fun showAnimationMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(context, view)
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
        val popup = androidx.appcompat.widget.PopupMenu(context, view)
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

    private fun showGeometryMenuDialog() {
        val groups = arrayOf("Algebra & Sequences", "Trigonometry", "2D Shapes", "3D Shapes", "Coordinate & Analytical", "Advanced Triangles", "Calculus & Analysis", "Statistics & Probability", "Finance & Business", "Higher Math & Engineering", "Advanced Physics & Quantum", "College Physics & Chemistry")
        AlertDialog.Builder(context)
            .setTitle("Formula Library")
            .setItems(groups) { _, which ->
                when (which) {
                    0 -> showSubCategoryDialog("Algebra & Sequences", arrayOf("FOIL (ax+b)(cx+d)", "(a+b)² Expansion", "(a-b)² Expansion", "Exponent Mult Rule", "Fractional Exponent", "Difference of Squares", "Difference of Cubes", "Arithmetic Sequence", "Fibonacci Number", "Factorial (n!)", "Combination (nCr)", "Binomial Probability", "Completing the Square", "Prime Number Check", "Pythagorean Triple"))
                    1 -> showSubCategoryDialog("Trigonometry", arrayOf("2cosAcosB Identity", "De Moivre's Theorem", "Find Opposite Side", "Find Adjacent Side", "Double Angle sin(2A)", "Double Angle cos(2A)", "Double Angle tan(2A)", "Triple Angle sin(3A)", "Half Angle Formulas", "Law of Sines", "Law of Cosines"))
                    2 -> showSubCategoryDialog("2D Shapes", arrayOf("Rectangle", "Square", "Triangle", "Equilateral Triangle", "Isosceles Triangle", "Annulus", "Circle", "Ellipse", "Trapezoid", "Tangential Quadrilateral", "Parallelogram", "Rhombus", "Kite", "Hexagon", "Octagon", "Regular Polygon", "Polygon (Shoelace)"))
                    3 -> showSubCategoryDialog("3D Shapes", arrayOf("Rectangular Prism", "Triangular Prism", "Pentagonal Prism", "Hexagonal Prism", "Cube", "Cylinder", "Sphere", "Hemisphere", "Cone", "Ellipsoid", "Square Pyramid", "Triangular Pyramid", "Pentagonal Pyramid", "Hexagonal Pyramid", "Frustum of Pyramid", "Frustum of Cone", "Solid of Revolution"))
                    4 -> showSubCategoryDialog("Coordinate & Analytical", arrayOf("Distance", "Midpoint", "Centroid", "Circumcenter", "Orthocenter", "Slope", "Slope-Intercept Form", "Rate of Change", "Rotations", "Circle Equation", "Point to Line Distance", "Asymptotes", "Line Equation", "Line Relationships", "Intersection", "Parabola Analysis"))
                    5 -> showSubCategoryDialog("Advanced Triangles", arrayOf("Pythagorean Theorem", "Heron's Formula"))
                    6 -> showSubCategoryDialog("Calculus & Analysis", arrayOf("Arc Length", "Tangent Line", "Asymptotes", "Derivative (f'(x))", "Integral (int(f(x),a,b))", "Newton's Method", "Mean Value Theorem"))
                    7 -> showSubCategoryDialog("Statistics & Probability", arrayOf("Arithmetic Mean", "Average Deviation", "Standard Deviation", "Variance", "Root Mean Square", "Binomial Distribution", "Poisson Distribution", "Normal Distribution", "Coin Toss Probability", "Z-Score", "Z to P-Value", "Bayes Theorem"))
                    8 -> showSubCategoryDialog("Finance & Business", arrayOf("Total Cost (units * price)", "Simple Interest", "Compound Interest", "Daily Compound Interest", "Selling Price", "Exponential Growth", "Exponential Decay", "Discount Formula"))
                    9 -> showSubCategoryDialog("Higher Math & Engineering", arrayOf("Distance (d=rt)", "Ohm's Law", "DC Power", "Resistors (Parallel)", "Charge & Energy", "Celsius to Fahrenheit", "Fahrenheit to Celsius", "Keplers Third Law", "Vis-Viva Orbit Speed", "Rocket Equation (Delta-v)", "Schwarzschild Radius", "Time Dilation", "Binary to Decimal", "Decimal to Binary"))
                    10 -> showSubCategoryDialog("Advanced Physics & Quantum", arrayOf("De Broglie Wavelength", "Photon Energy (E=hf)", "Heisenberg Uncertainty", "Particle in a Box", "Boltzmann Entropy", "Boltzmann Factor", "Gravitational Time Dilation", "Lorentz Factor"))
                    11 -> showSubCategoryDialog("College Physics & Chemistry", arrayOf("Newton's 2nd Law (F=ma)", "Ideal Gas (Find P)", "Ideal Gas (Find V)", "Gibbs Free Energy", "Nernst Equation", "Arrhenius Equation"))
                }
            }
            .show()
    }

    private fun showSubCategoryDialog(title: String, items: Array<String>) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(items) { _, which ->
                showShapeFormulaDialog(items[which])
            }
            .show()
    }

    private fun showShapeFormulaDialog(shape: String) {
        val formulas = when (shape) {
            "FOIL (ax+b)(cx+d)" -> arrayOf("Expand (foil(a, b, c, d))")
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
            "Find Opposite Side" -> arrayOf("Opposite (opposite(right_tri_find_opp(angle, hyp)))")
            "Find Adjacent Side" -> arrayOf("Adjacent (adjacent(right_tri_find_adj(angle, hyp)))")
            "Find Opposite Side" -> arrayOf("Opposite (opposite(right_tri_find_opp(angle, hyp)))")
            "Find Adjacent Side" -> arrayOf("Adjacent (adjacent(right_tri_find_adj(angle, hyp)))")
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
            "Right Triangle" -> arrayOf("Hypotenuse (sqrt(Base^2 + Perpendicular^2))", "Area (1/2 * Base * Perpendicular)", "Perimeter (Base + Perpendicular + Hypotenuse)")
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
            
            "Cuboid" -> arrayOf("CSA (2*h*(l + b))", "TSA (2*(l*b + b*h + h*l))", "Volume (l * b * h)")
            "Rectangular Prism" -> arrayOf("Base Area (b * l)", "Surface Area (2 * (b*l + l*h + h*b))", "Volume (l * b * h)")
            "Triangular Prism" -> arrayOf("Base Area (1/2 * a * b)", "Surface Area (a*b + 3*b*h)", "Volume (1/2 * a*b*h)")
            "Pentagonal Prism" -> arrayOf("Base Area (5/2 * a * b)", "Surface Area (5*a*b + 5*b*h)", "Volume (5/2 * a*b*h)")
            "Hexagonal Prism" -> arrayOf("Base Area (3 * a * b)", "Surface Area (6*a*b + 6*b*h)", "Volume (3 * a*b*h)")
            "Cube" -> arrayOf("Surface Area (6 * a^2)", "LSA (4 * a^2)", "Volume (a^3)")
            "Cylinder" -> arrayOf("CSA (2 * π * r * h)", "TSA (2 * π * r * (r + h))", "Volume (π * r^2 * h)")
            "Sphere" -> arrayOf("Surface Area (4 * π * r^2)", "Diameter (2 * r)", "Volume (4/3 * π * r^3)")
            "Hemisphere" -> arrayOf("CSA (2 * π * r^2)", "TSA (3 * π * r^2)", "Volume (2/3 * π * r^3)")
            "Cone" -> arrayOf("CSA (π * r * l)", "TSA (π * r * (l + r))", "Volume (1/3 * π * r^2 * h)", "Slant Height (sqrt(r^2 + h^2))")
            "Ellipsoid" -> arrayOf("Volume (4/3 * π * r1 * r2 * r3)")
            "Square Pyramid" -> arrayOf("LSA (2 * b * s)", "TSA (b^2 + 2 * b * s)", "Volume (1/3 * b^2 * h)")
            "Triangular Pyramid" -> arrayOf("LSA (3/2 * b * s)", "TSA (AreaBase + LSA)", "Volume (1/3 * AreaBase * h)")
            "Pentagonal Pyramid" -> arrayOf("LSA (5/2 * b * s)", "TSA (AreaBase + LSA)", "Volume (1/3 * AreaBase * h)")
            "Hexagonal Pyramid" -> arrayOf("LSA (3 * b * s)", "TSA (AreaBase + LSA)", "Volume (1/3 * AreaBase * h)")
            "Frustum of Pyramid" -> arrayOf("Volume (frustum_pyramid(a, b, h))")
            "Frustum of Cone" -> arrayOf("Volume (frustum_cone(r1, r2, h))")
            "Right Prism" -> arrayOf("CSA (Perimeter * h)", "TSA (CSA + 2 * AreaOneEnd)", "Volume (AreaBase * h)")
            "Right Pyramid" -> arrayOf("LSA (0.5 * Perimeter * slant_h)", "TSA (LSA + AreaBase)", "Volume (1/3 * AreaBase * h)")
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

            "Calculus & Analysis" -> arrayOf("Arc Length (L)", "Tangent Line", "Asymptotes", "Derivative (f'(x))", "Integral (int)", "Newton's Method", "Mean Value Theorem")
            "Arc Length" -> arrayOf("L (int(sqrt(1 + f'(x)²), a, b))")
            "Derivative (f'(x))" -> arrayOf("At point x (derivative(f(x), x))")
            "Integral (int(f(x),a,b))" -> arrayOf("Definite (integral(f(x), a, b))")
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
            "Newton's 2nd Law (F=ma)" -> arrayOf("Force (newton_f(m, a))")
            "Ideal Gas (Find P)" -> arrayOf("Pressure (gas_p(V, n, T))")
            "Ideal Gas (Find V)" -> arrayOf("Volume (gas_v(P, n, T))")
            "Gibbs Free Energy" -> arrayOf("dG (gibbs(dH, T, dS))")
            "Nernst Equation" -> arrayOf("E (nernst(E0, n, Q, T))")
            "Arrhenius Equation" -> arrayOf("Rate k (arrhenius(A, Ea, T))")
            else -> emptyArray()
        }

        AlertDialog.Builder(context)
            .setTitle("$shape Formulas")
            .setItems(formulas) { _, which ->
                val fullFormula = formulas[which]
                val label = fullFormula.substringBefore(" (").replace(" ", "")
                val formula = fullFormula.substringAfter(" (").substringBeforeLast(")")
                
                showFormulaSolverDialog(shape, label, formula)
            }
            .show()
    }

    private fun showFormulaSolverDialog(shape: String, label: String, formula: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_formula_solver, null)
        val container = dialogView.findViewById<android.widget.LinearLayout>(R.id.inputs_container)
        container.removeAllViews()

        val knownFuncs = listOf("sqrt", "sin", "cos", "tan", "asin", "acos", "atan", "abs", "log", "ln", "int", "pi", "e", "x", "y")
        val variables = "\\b[a-zA-Z][a-zA-Z0-9]*\\b".toRegex().findAll(formula)
            .map { it.value }
            .filter { it.lowercase() !in knownFuncs }
            .distinct()
            .toList()

        val inputFields = mutableMapOf<String, EditText>()
        for (v in variables) {
            val tv = TextView(context)
            tv.text = "Enter $v:"
            tv.setTextColor(Color.BLACK)
            tv.setPadding(20, 20, 20, 0)
            container.addView(tv)
            
            val et = EditText(context)
            et.hint = v
            et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            container.addView(et)
            inputFields[v] = et
        }

        val builder = AlertDialog.Builder(context)
            .setTitle("$label Solver")
            .setView(dialogView)
            .setPositiveButton("Solve") { _, _ ->
                var substituted = formula
                for ((v, et) in inputFields) {
                    val value = et.text.toString().ifEmpty { "1" }
                    substituted = substituted.replace(v, value)
                }

                val finalExpr = "$label($substituted)".replace("π", "pi").replace("²", "^2").replace("³", "^3")
                calculateFromFormula(finalExpr, label)
            }
            
        // Add Graph button
        val is3D = formula.contains("3d") || label.lowercase().contains("3d")
        builder.setNeutralButton(if (is3D) "3D Graph" else "Graph") { _, _ ->
            var substituted = formula
            for ((v, et) in inputFields) {
                val value = et.text.toString().ifEmpty { "1" }
                substituted = substituted.replace(v, value)
            }
            val graphExpr = "$label($substituted)".replace("π", "pi").replace("²", "^2").replace("³", "^3")
            currentInput = graphExpr
            showGraphDialog()
        }

        builder.setNegativeButton("Cancel", null).show()
    }

    private fun calculateFromFormula(expression: String, label: String) {
        val result = CalculatorHelper.evaluateFull(expression, useRadians)
        if (result != null) {
            val resultStr = formatValue(BigDecimal(result.toString()))
            addHistoryItem("Formula: $label", expression)
            addHistoryItem("Result", resultStr)
            currentInput = result.toString()
            updateDisplay()
        } else {
            Toast.makeText(context, "Calculation Error", Toast.LENGTH_SHORT).show()
        }
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

        // Bypass formatting for formulas, commands, and strings with letters or math operators
        val bypass = listOf("(", ")", "=", ",", "^", "*", "/", "+", "×", "÷")
        if (input.any { it.isLetter() } || bypass.any { input.contains(it) }) return input

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isIndian = prefs.getBoolean("calc_indian_format", true)
        val formatted = if (isIndian) CalculatorHelper.formatIndianStyleString(input) else CalculatorHelper.formatInternationalStyleString(input)
        if (input.endsWith(".")) return "$formatted."
        return formatted
    }

    private fun getRawCursorPosition(): Int {
        val formatted = display.text.toString()
        val cursor = display.selectionStart
        if (cursor <= 0) return 0

        // If display matches raw input exactly (e.g. formula mode), cursor position is raw
        if (formatted == currentInput) return cursor

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

        if (finalVal.replace(".", "").length >= 100) return

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

    private fun calculateResult(isFinal: Boolean) {
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
            operator = ""; historyExpression = resultStr; isNewOperation = true; updateDisplay(); display.setSelection(display.text.length); return
        }

        if (currentInput.isEmpty() && !hasKeyword) return

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
            val v2 = currentInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
            try {
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

        if (result == null) return

        val opForHistory = when(operator) { "×" -> "*"; "÷" -> "/"; else -> operator }

        historyExpression = if (hasKeyword) {
            formatRawInput(currentInput)
        } else if (historyExpression.isEmpty()) {
            if (fullExpression.contains("(")) fullExpression else "${formatValue(lastValue)}$opForHistory${formatRawInput(currentInput)}"
        } else {
            if (fullExpression.contains("(")) fullExpression else "$historyExpression$opForHistory${formatRawInput(currentInput)}"
        }

        val resultStr = if (result is String) result else formatValue(result as BigDecimal)
        if (isFinal) {
            addHistoryItem(historyExpression.replace(" ", ""), resultStr)
            if (result is BigDecimal) {
                grandTotal = grandTotal.add(result)
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
        historyAdapter.notifyItemInserted(historyList.size - 1)
        updateStepsDisplay()
        historyRecyclerView.post {
            historyRecyclerView.scrollToPosition(historyList.size - 1)
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
        display.setSelection(display.text.length)
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
        scientificRow3.visibility = if (isEnabled) View.VISIBLE else View.GONE
        scientificRow4.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }

    private fun updateDisplay() {
        val length = currentInput.length
        val textSizeSp = when {
            length <= 10 -> 36f
            length <= 15 -> 30f
            length <= 25 -> 24f
            length <= 35 -> 20f
            else -> 18f
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

    private fun clear() { grandTotal = BigDecimal.ZERO; currentInput = ""; lastValue = BigDecimal.ZERO; operator = ""; isNewOperation = true; historyExpression = ""; historyList.clear(); historyAdapter.notifyDataSetChanged(); updateStepsDisplay(); updateDisplay(); display.setSelection(display.text.length) }
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

    private fun showGraphDialog() {
        val formula = formulaDisplay.text.toString().trim().replace("=", "")
        val currentExpr = if (formula.contains("sin") || formula.contains("cos") || formula.contains("tan") || formula.contains("x") || formula.contains("pentagon")) {
            formula
        } else if (currentInput.isNotEmpty()) {
            currentInput
        } else {
            formula
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_graph, null)
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
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

    private fun applyCalculatorTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
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
        val headerTextColor = Color.WHITE

        view.findViewById<View>(R.id.calculator_root)?.setBackgroundColor(bgColor)
        view.findViewById<View>(R.id.calculator_display_area)?.setBackgroundColor(displayColor)

        view.findViewById<TextView>(R.id.calculator_title)?.setTextColor(headerTextColor)
        view.findViewById<ImageButton>(R.id.settings_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)
        view.findViewById<ImageButton>(R.id.graph_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)
        view.findViewById<ImageButton>(R.id.close_calculator_button)?.imageTintList = ColorStateList.valueOf(headerTextColor)

        display.setTextColor(displayTextMain)
        memoryIndicator.setTextColor(displayTextMain)

        val historyColor = if (isBlue || isGreen || isPurple || isOrange) Color.WHITE else Color.parseColor("#333333")
        val historyExprColor = if (isBlue || isGreen || isPurple || isOrange) Color.parseColor("#B2EBF2") else Color.GRAY
        historyAdapter.updateColors(historyColor, historyExprColor)

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
        val operators = listOf(R.id.button_add, R.id.button_subtract, R.id.button_multiply, R.id.button_divide, R.id.button_equals, R.id.button_plus_minus, R.id.button_percent, R.id.button_check, R.id.button_correct, R.id.button_gt, R.id.button_m_plus, R.id.button_m_minus, R.id.button_mrc, R.id.button_sqrt, R.id.button_square, R.id.button_pi, R.id.button_e, R.id.button_power, R.id.button_sin, R.id.button_cos, R.id.button_tan, R.id.button_log, R.id.button_ln, R.id.button_parenthesis_open, R.id.button_parenthesis_close, R.id.button_2nd, R.id.button_deg_rad, R.id.button_factorial, R.id.button_sinh, R.id.button_cosh, R.id.button_tanh, R.id.button_layout, R.id.button_geometry, R.id.button_x_var, R.id.button_y_var, R.id.button_solve, R.id.button_polynomial, R.id.button_spiral, R.id.button_nested, R.id.button_animation, R.id.button_ans, R.id.button_comma)
        val gsts = listOf(R.id.button_gst_5, R.id.button_gst_12, R.id.button_gst_18, R.id.button_gst_minus)

        digits.forEach { id -> view.findViewById<Button>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(digitColor); it.setTextColor(textColor) } }
        operators.forEach { id -> view.findViewById<Button>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(operatorColor); it.setTextColor(textColor) } }
        gsts.forEach { id -> view.findViewById<Button>(id)?.let { it.backgroundTintList = ColorStateList.valueOf(gstColor); it.setTextColor(Color.WHITE) } }
        view.findViewById<Button>(R.id.button_clear)?.let { it.backgroundTintList = ColorStateList.valueOf(acColor); it.setTextColor(Color.WHITE) }
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
