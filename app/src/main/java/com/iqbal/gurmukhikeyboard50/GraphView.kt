package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.preference.PreferenceManager
import kotlin.math.*

class GraphView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val gridPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val axisPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#7EB1FF")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val pointPaint = Paint().apply {
        color = Color.parseColor("#7EB1FF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pointLabelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.LTGRAY
        textSize = 24f
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val circlePaint = Paint().apply {
        color = Color.parseColor("#FF69B4")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        isAntiAlias = true
    }

    private val annotationPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 30f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    }

    private val annotationLinePaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var equation: String? = null
    private var preprocessedEquation: String? = null
    private var useRadians: Boolean = false
    private var geometricMode = false
    private var milestoneMode = false
    private var milestoneX = 0f
    private var milestoneY = 0f
    private var milestoneLabel = ""
    private var milestoneAmplitude = 0.0
    private var triangleMode = false
    private var triBase = 0f
    private var triHeight = 0f
    private var triHypotenuse = 0f
    private var triAngle = 0f
    private var triFunc = ""
    private var triOp = String()
    private var isHoleMode = false
    private var pivotMode = false
    private var pivotRadius = 0f
    private var pivotAngle = 0f
    private var isAnimating = false
    private var animationAngle = 0f
    private var rotationSpeed = 2f
    private var scanMode = false
    private var scanDirection = 1
    private var flyMode = false
    private var flyTime = 0f
    private var pistonMode = false
    private var pistonCrank = 0f
    private var pistonRod = 0f
    private var quadMode = false
    private var quadA = 0f
    private var quadB = 0f
    private var quadC = 0f
    private var solveMode = false
    private var solveExpr = ""
    private var mergedTrigMode = false

    private var parabolaMode = false
    private var parabolaA = 0.0
    private var parabolaB = 0.0
    private var parabolaC = 0.0
    private var parabolaVertex = PointF()
    private var parabolaRoots = mutableListOf<Double>()
    private var parabolaComplexRoots = mutableListOf<Complex>()
    private var parabolaTitle = ""

    private var cubicMode = false
    private var cubicA = 0.0
    private var cubicB = 0.0
    private var cubicC = 0.0
    private var cubicD = 0.0
    private var cubicRoots = mutableListOf<Double>()
    private var cubicComplexRoots = mutableListOf<Complex>()

    private var quarticMode = false
    private var quarticA = 0.0
    private var quarticB = 0.0
    private var quarticC = 0.0
    private var quarticD = 0.0
    private var quarticE = 0.0
    private var quarticRoots = mutableListOf<Double>()
    private var quarticComplexRoots = mutableListOf<Complex>()
    private var quarticTurningPoints = mutableListOf<PointF>()

    private var quinticMode = false
    private var quinticA = 0.0
    private var quinticB = 0.0
    private var quinticC = 0.0
    private var quinticD = 0.0
    private var quinticE = 0.0
    private var quinticF = 0.0
    private var quinticRoots = mutableListOf<Double>()
    private var quinticComplexRoots = mutableListOf<Complex>()
    private var quinticTurningPoints = mutableListOf<PointF>()

    private var spiralMode = false
    private var spiralDiameter = 10f
    private var spiralHoles = 20
    private var spiralTurns = 3f

    private var nestedMode = false
    private var nestedDiameter = 5f
    private var nestedSides = 6
    private var nestedCount = 4
    private var nestedSpacing = 0.5f

    private var binomialMode = false
    private var binomialA = 1f
    private var binomialB = 1f
    private var binomialIsMinus = false

    private var mode3D = false
    private var surfaceMode = false
    private var rotationX = -25f
    private var rotationY = 45f

    private var isTraceEnabled = false
    private var traceX: Double? = null
    private var traceY: Double? = null

    enum class ScienceMode { NONE, PROJECTILE, REFRACTION, NOISE, BEZIER }
    private var currentScienceMode = ScienceMode.NONE

    private var projectileV0 = 50.0
    private var projectileAngle = 45.0
    private var projectileTime = 0f
    private var isAirResistanceEnabled = false

    private var refractionIndexN1 = 1.5
    private var refractionIndexN2 = 1.0

    private var noiseSeed = 0L
    private var noiseTime = 0f
    private var noiseBitmap: Bitmap? = null

    private var bezierPoints = mutableListOf<PointF>()
    private var draggingPointIndex = -1

    data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    data class Point3D(val x: Float, val y: Float, val z: Float)

    private fun project(p: Point3D): PointF {
        val radX = Math.toRadians(rotationX.toDouble())
        val radY = Math.toRadians(rotationY.toDouble())

        // Rotation around Y axis
        val x1 = p.x * cos(radY) - p.z * sin(radY)
        val z1 = p.x * sin(radY) + p.z * cos(radY)

        // Rotation around X axis
        val y2 = p.y * cos(radX) - z1 * sin(radX)
        val z2 = p.y * sin(radX) + z1 * cos(radX)

        // Perspective (optional, or just orthographic for now)
        // val factor = 1000f / (1000f + z2)
        return PointF(x1.toFloat() * scaleX, y2.toFloat() * scaleY)
    }

    private var holeMargin = 0f
    private var polygonSides = 3
    private var shapeSize = 10f
    private var shapeWidth = 0f
    private var shapeHeight = 0f
    private var shapeSideA = 0f
    private var shapeSideB = 0f
    private var shapeType = ""

    private var scaleX = 100f
    private var scaleY = 100f
    private var offsetX = 0f
    private var offsetY = 0f

    private var pendingEquation: String? = null
    private var pendingRadians: Boolean = false

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            var newScale = scaleX * factor
            newScale = newScale.coerceIn(1f, 5000f)
            scaleX = newScale
            scaleY = newScale
            invalidate()
            return true
        }
    })

    fun setEquation(eq: String, radians: Boolean) {
        if (width == 0 || height == 0) {
            pendingEquation = eq
            pendingRadians = radians
            return
        }
        pendingEquation = null

        this.equation = eq
        this.useRadians = radians
        preprocessedEquation = null
        mode3D = false
        surfaceMode = false
        rotationX = -25f
        rotationY = 45f
        milestoneMode = false
        geometricMode = false
        isHoleMode = false
        triangleMode = false
        mergedTrigMode = false
        pivotMode = false
        pistonMode = false
        quadMode = false
        solveMode = false
        parabolaMode = false
        cubicMode = false
        quarticMode = false
        quinticMode = false
        spiralMode = false
        nestedMode = false
        binomialMode = false
        scanMode = false
        flyMode = false
        currentScienceMode = ScienceMode.NONE
        noiseBitmap = null
        stopAnimation()

        // Reset Styles
        axisPaint.color = Color.WHITE
        gridPaint.color = Color.DKGRAY
        gridPaint.pathEffect = null
        textPaint.color = Color.LTGRAY
        labelPaint.color = Color.WHITE

        val lowerEqRaw = eq.lowercase().replace(" ", "").replace("×", "*").replace("÷", "/")

        // Strip common labels if present as prefix
        var lowerEq = lowerEqRaw
        val labels = listOf("area(", "perimeter(", "circumference(", "diameter(", "diagonal(", "volume(", "surfacearea(", "lsa(", "tsa(", "csa(", "basearea(", "rectangular_prism(", "triangular_prism(", "pentagonal_prism(", "hexagonal_prism(", "ellipsoid(", "tangential_quad(", "square_pyramid(", "triangular_pyramid(", "pentagonal_pyramid(", "hexagonal_pyramid(", "distance(", "midpoint(", "centroid(", "circumcenter(", "slope(", "circleeq(", "c(", "c²(", "ratio(", "t(", "v(", "dv(", "rs(", "f(", "isp(", "thrust(", "t'(", "voltage(", "current(", "resistance(", "power(", "series(", "parallel(", "charge(", "energy(", "centralangle(", "arclength(", "sin(2a)", "cos(2a)", "tan(2a)", "sin(3a)", "cos(3a)", "tan(3a)", "sin(a/2)", "cos(a/2)", "tan(a/2)", "radianstodegrees(", "degreestoradians(", "asymptotes(", "hypotenuse(", "base(", "perpendicular(", "righttriangle(", "intersection(", "parallelogram(", "rhombus(", "kite(", "hexagon(", "octagon(", "x-intercept(", "y-intercept(", "interceptform(", "point-slope/gradient(", "vertex(", "focus(", "directrix(", "tangent(", "vertexform(", "slantheight(", "axisofsymmetry(", "rotation90(", "rotation180(", "rotation270(", "equilateral_tri(", "isosceles_tri(", "de_broglie(", "photon_energy(", "uncertainty_p(", "energy_box(", "entropy_boltzmann(", "boltzmann_factor(", "grav_dilation(", "lorentz(", "newton_f(", "gas_p(", "gas_v(", "gibbs(", "nernst(", "arrhenius(", "bin_to_dec(", "dec_to_bin(", "det2(", "inv2(", "ohm_v(", "ohm_i(", "ohm_r(", "res_parallel(", "schwarzschild(", "time_dilation(", "total_cost(", "dist_rt(", "is_prime(", "poisson(", "simple_int(", "z_score(", "rms(", "selling_price(", "c_to_f(", "f_to_c(", "pyth_triple(", "z_to_p(", "slope_intercept(", "right_tri_find_opp(", "right_tri_find_adj(", "termn(", "binom_prob(", "check(", "producttosum(", "powcomplex(", "opposite(", "adjacent(", "doubleangle(", "tripleangle(", "halfangle(", "ratio(", "3d(")
        for (l in labels) {
            if (lowerEq.startsWith(l) && lowerEq.endsWith(")")) {
                if (l == "3d(") mode3D = true
                lowerEq = lowerEq.substring(l.length, lowerEq.length - 1)
                break
            }
        }

        // Special 3D shape checks
        val prism3DRegex = "(cube|prism|pyramid|cylinder3d|cone3d|sphere)\\((\\d+\\.?\\d*)(?:,\\s*(\\d+\\.?\\d*))?(?:,\\s*(\\d+\\.?\\d*))?\\)".toRegex()
        val prismMatch = prism3DRegex.find(lowerEq)
        if (prismMatch != null) {
            mode3D = true
            geometricMode = true
            shapeType = prismMatch.groupValues[1]
            shapeWidth = prismMatch.groupValues[2].toFloat()
            shapeHeight = if (prismMatch.groupValues.size > 3) prismMatch.groupValues[3].toFloatOrNull() ?: shapeWidth else shapeWidth
            shapeSize = if (prismMatch.groupValues.size > 4) prismMatch.groupValues[4].toFloatOrNull() ?: shapeWidth else shapeWidth // Depth or sides

            val maxDim = max(shapeWidth, max(shapeHeight, shapeSize))
            scaleX = (width.coerceAtLeast(100) / (maxDim * 2.5f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = 0f
            offsetY = 0f
            invalidate()
            return
        }

        // 1. Perimeter Visualizer (Rectangle): e.g. "2*(5+5)" or "Perimeter(2*(5+5))"
        val periRegex = ".*2[*×]\\(?(\\d+\\.?\\d*)[+×](\\d+\\.?\\d*)\\)?.*".toRegex()
        val periMatch = periRegex.find(lowerEqRaw)
        if (periMatch != null) {
            geometricMode = true
            shapeType = "perimeter_rect"
            shapeWidth = periMatch.groupValues[1].toFloat()
            shapeHeight = periMatch.groupValues[2].toFloat()

            val displaySize = max(shapeWidth, shapeHeight).coerceAtLeast(1f)
            scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = -width / 4f
            offsetY = height / 4f

            preprocessedEquation = null
            invalidate()
            return
        }

        // 2. Triangle Perimeter: e.g. "4+5+6" or "Perimeter(4+5+6)"
        val triPeriRegex = ".*(\\d+\\.?\\d*)[+×](\\d+\\.?\\d*)[+×](\\d+\\.?\\d*).*".toRegex()
        val triPeriMatch = triPeriRegex.find(lowerEqRaw)
        val isLiteralABC = lowerEqRaw.contains("a+b+c")

        if (triPeriMatch != null || isLiteralABC) {
            geometricMode = true
            shapeType = "perimeter_tri"
            if (triPeriMatch != null) {
                shapeSideA = triPeriMatch.groupValues[1].toFloat() // side a
                shapeSideB = triPeriMatch.groupValues[2].toFloat() // side b
                shapeHeight = triPeriMatch.groupValues[3].toFloat() // side c (base)
            } else {
                shapeSideA = 3f; shapeSideB = 4f; shapeHeight = 5f
            }

            val a = shapeSideA
            val b = shapeSideB
            val c = shapeHeight

            val displaySize = max(a, max(b, c)).coerceAtLeast(1f)
            scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = -width / 4f
            offsetY = height / 4f

            preprocessedEquation = null
            invalidate()
            return
        }

        // 3. Circumference Visualizer: e.g. "2 * pi * 5"
        val circumRegex = ".*(?:2[*×](?:π|3\\.14\\d*)[*×](\\d+\\.?\\d*)|(?:π|3\\.14\\d*)[*×]2[*×](\\d+\\.?\\d*)|2[*×](\\d+\\.?\\d*)[*×](?:π|3\\.14\\d*)).*".toRegex()
        val circumMatch = circumRegex.find(lowerEqRaw)
        if (circumMatch != null) {
            geometricMode = true
            shapeType = "circle"
            val r = (circumMatch.groupValues[1].ifEmpty { circumMatch.groupValues[2].ifEmpty { circumMatch.groupValues[3] } }).toFloatOrNull() ?: 5f
            shapeSize = r * 2f

            scaleX = (width.coerceAtLeast(100) / (shapeSize * 1.8f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = 0f
            offsetY = 0f

            preprocessedEquation = null
            invalidate()
            return
        }

        // 4. 3D Shape Cross-Sections: e.g. "volume(1/3*pi*2^2*9)"
        val coneRegex = ".*1/3[*×](?:π|3\\.14\\d*)[*×](\\d+\\.?\\d*)\\^2[*×](\\d+\\.?\\d*).*".toRegex()
        val cylinderRegex = ".*(?:π|3\\.14\\d*)[*×](\\d+\\.?\\d*)\\^2[*×](\\d+\\.?\\d*).*".toRegex()
        val sphereRegex = ".*4/3[*×](?:π|3\\.14\\d*)[*×](\\d+\\.?\\d*)\\^3.*".toRegex()

        val coneMatch = coneRegex.find(lowerEqRaw)
        val cylinderMatch = cylinderRegex.find(lowerEqRaw)
        val sphereMatch = sphereRegex.find(lowerEqRaw)

        // 5. Binomial Proof: sq_bin_plus(a, b)
        val binPlusRegex = "sq_bin_plus\\((\\d+\\.?\\d*),(\\d+\\.?\\d*)\\)".toRegex()
        val binMinusRegex = "sq_bin_minus\\((\\d+\\.?\\d*),(\\d+\\.?\\d*)\\)".toRegex()
        val binPlusMatch = binPlusRegex.find(lowerEqRaw)
        val binMinusMatch = binMinusRegex.find(lowerEqRaw)

        // 6. Triangle Area Proof: area(1/2*b*h)
        val triAreaProofRegex = ".*1/2[*×](\\d+\\.?\\d*)[*×](\\d+\\.?\\d*).*".toRegex()
        val triAreaProofMatch = triAreaProofRegex.find(lowerEqRaw)

        // 12. Rectangle Area Proof: area(l*b)
        val rectAreaRegex = "area\\((\\d+\\.?\\d*)[*×](\\d+\\.?\\d*)\\)".toRegex()
        val rectAreaMatch = rectAreaRegex.find(lowerEqRaw)
        if (rectAreaMatch != null) {
            geometricMode = true
            shapeType = "rectangle"
            shapeWidth = rectAreaMatch.groupValues[1].toFloat()
            shapeHeight = rectAreaMatch.groupValues[2].toFloat()

            val displaySize = max(shapeWidth, shapeHeight).coerceAtLeast(1f)
            scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = -width / 4f
            offsetY = height / 4f

            preprocessedEquation = null
            invalidate()
            return
        }

        if (triAreaProofMatch != null) {
            geometricMode = true
            shapeType = "triangle"
            shapeWidth = triAreaProofMatch.groupValues[1].toFloat()
            shapeHeight = triAreaProofMatch.groupValues[2].toFloat()

            val displaySize = max(shapeWidth, shapeHeight).coerceAtLeast(1f)
            scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = -width / 4f
            offsetY = height / 4f

            preprocessedEquation = null
            invalidate()
            return
        }

        // 9. Find Adjacent Side Triangle
        val triAdjFindRegex = ".*right_tri_find_adj\\((\\d+\\.?\\d*),(\\d+\\.?\\d*)\\).*".toRegex()
        val triAdjFindMatch = triAdjFindRegex.find(lowerEqRaw)
        if (triAdjFindMatch != null) {
            geometricMode = true
            shapeType = "perimeter_tri"
            val angle = triAdjFindMatch.groupValues[1].toDouble()
            val hyp = triAdjFindMatch.groupValues[2].toDouble()

            shapeHeight = (hyp * cos(Math.toRadians(angle))).toFloat() // Adjacent (base c)
            shapeSideB = hyp.toFloat() // Hypotenuse (side b)
            shapeSideA = (hyp * sin(Math.toRadians(angle))).toFloat() // Opposite (side a)

            val displaySize = max(shapeSideB, max(shapeHeight, shapeSideA)).coerceAtLeast(1f)
            scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = -width / 4f
            offsetY = height / 4f

            preprocessedEquation = null
            invalidate()
            return
        }

        // 11. Law of Cosines Visualization
        val cosRuleVizRegex = ".*cos_rule\\((\\d+\\.?\\d*|x),(\\d+\\.?\\d*|x),(\\d+\\.?\\d*|x),(\\d+\\.?\\d*|x)\\).*".toRegex()
        val cosRuleMatch = cosRuleVizRegex.find(lowerEqRaw)
        if (cosRuleMatch != null) {
            val aStr = cosRuleMatch.groupValues[1]; val bStr = cosRuleMatch.groupValues[2]
            val cStr = cosRuleMatch.groupValues[3]; val CDegStr = cosRuleMatch.groupValues[4]

            try {
                val a: Double; val b: Double; val c: Double
                if (cStr == "x") {
                    a = aStr.toDouble(); b = bStr.toDouble()
                    val C = CDegStr.toDouble()
                    c = sqrt(a * a + b * b - 2 * a * b * cos(Math.toRadians(C)))
                } else if (CDegStr == "x") {
                    a = aStr.toDouble(); b = bStr.toDouble(); c = cStr.toDouble()
                } else {
                    a = aStr.toDouble(); b = bStr.toDouble(); c = cStr.toDouble()
                }

                geometricMode = true
                shapeType = "perimeter_tri"
                shapeSideA = a.toFloat()
                shapeSideB = b.toFloat()
                shapeHeight = c.toFloat() // base

                val displaySize = max(shapeSideA, max(shapeSideB, shapeHeight)).coerceAtLeast(1f)
                scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
                scaleY = scaleX
                offsetX = -width / 4f
                offsetY = height / 4f
                preprocessedEquation = null
                invalidate()
                return
            } catch (e: Exception) {}
        }

        // 10. Law of Sines Visualization
        val sineRuleVizRegex = ".*sine_rule\\((\\d+\\.?\\d*|x),(\\d+\\.?\\d*|x),(\\d+\\.?\\d*|x),(\\d+\\.?\\d*|x)\\).*".toRegex()
        val sineRuleMatch = sineRuleVizRegex.find(lowerEqRaw)
        if (sineRuleMatch != null) {
            val aStr = sineRuleMatch.groupValues[1]; val ADegStr = sineRuleMatch.groupValues[2]
            val bStr = sineRuleMatch.groupValues[3]; val BDegStr = sineRuleMatch.groupValues[4]

            // Try to solve for all 3 sides to draw triangle
            val a: Double; val A: Double; val b: Double; val B: Double
            try {
                if (aStr == "x") {
                    A = ADegStr.toDouble(); b = bStr.toDouble(); B = BDegStr.toDouble()
                    a = b * sin(Math.toRadians(A)) / sin(Math.toRadians(B))
                } else if (ADegStr == "x") {
                    a = aStr.toDouble(); b = bStr.toDouble(); B = BDegStr.toDouble()
                    A = Math.toDegrees(asin(a * sin(Math.toRadians(B)) / b))
                } else if (bStr == "x") {
                    a = aStr.toDouble(); A = ADegStr.toDouble(); B = BDegStr.toDouble()
                    b = a * sin(Math.toRadians(B)) / sin(Math.toRadians(A))
                } else if (BDegStr == "x") {
                    a = aStr.toDouble(); A = ADegStr.toDouble(); b = bStr.toDouble()
                    B = Math.toDegrees(asin(b * sin(Math.toRadians(A)) / a))
                } else {
                    a = aStr.toDouble(); A = ADegStr.toDouble(); b = bStr.toDouble(); B = BDegStr.toDouble()
                }

                val C = 180.0 - (A + B)
                val c = a * sin(Math.toRadians(C)) / sin(Math.toRadians(A))

                geometricMode = true
                shapeType = "perimeter_tri"
                shapeSideA = a.toFloat()
                shapeSideB = b.toFloat()
                shapeHeight = c.toFloat() // base

                val displaySize = max(shapeSideA, max(shapeSideB, shapeHeight)).coerceAtLeast(1f)
                scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
                scaleY = scaleX
                offsetX = -width / 4f
                offsetY = height / 4f
                preprocessedEquation = null
                invalidate()
                return
            } catch (e: Exception) {}
        }

        // 8. Find Opposite Side Triangle
        val triOppFindRegex = ".*right_tri_find_opp\\((\\d+\\.?\\d*),(\\d+\\.?\\d*)\\).*".toRegex()
        val triOppFindMatch = triOppFindRegex.find(lowerEqRaw)
        if (triOppFindMatch != null) {
            geometricMode = true
            shapeType = "perimeter_tri"
            val angle = triOppFindMatch.groupValues[1].toDouble()
            val hyp = triOppFindMatch.groupValues[2].toDouble()

            shapeHeight = (hyp * cos(Math.toRadians(angle))).toFloat() // Adjacent (base c)
            shapeSideB = hyp.toFloat() // Hypotenuse (side b)
            shapeSideA = (hyp * sin(Math.toRadians(angle))).toFloat() // Opposite (side a)

            val displaySize = max(shapeSideB, max(shapeHeight, shapeSideA)).coerceAtLeast(1f)
            scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = -width / 4f
            offsetY = height / 4f

            preprocessedEquation = null
            invalidate()
            return
        }

        // 7. Pythagorean Triple Visualization
        val pythTripleRegex = ".*pyth_triple\\((\\d+\\.?\\d*),(\\d+\\.?\\d*),(\\d+\\.?\\d*)\\).*".toRegex()
        val pythTripleMatch = pythTripleRegex.find(lowerEqRaw)
        if (pythTripleMatch != null) {
            geometricMode = true
            shapeType = "perimeter_tri" // Reuse this for side-labeled triangle
            shapeSideA = pythTripleMatch.groupValues[3].toFloat() // Hypotenuse (c)
            shapeSideB = pythTripleMatch.groupValues[1].toFloat() // side a
            shapeHeight = pythTripleMatch.groupValues[2].toFloat() // side b

            val a = shapeSideB
            val b = shapeHeight
            val c = shapeSideA

            val displaySize = max(a, max(b, c)).coerceAtLeast(1f)
            scaleX = (width.coerceAtLeast(100) / (displaySize * 1.8f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = -width / 4f
            offsetY = height / 4f

            preprocessedEquation = null
            invalidate()
            return
        }

        if (binPlusMatch != null || binMinusMatch != null) {
            binomialMode = true
            binomialIsMinus = binMinusMatch != null
            val match = binPlusMatch ?: binMinusMatch!!
            binomialA = match.groupValues[1].toFloat()
            binomialB = match.groupValues[2].toFloat()

            val total = if (binomialIsMinus) binomialA else binomialA + binomialB
            scaleX = (width.coerceAtLeast(100) / (total * 1.5f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = -width / 4f
            offsetY = height / 4f
            invalidate()
            return
        }

        if (coneMatch != null || cylinderMatch != null || sphereMatch != null) {
            geometricMode = true
            if (coneMatch != null) {
                shapeType = "cone_cross_section"
                shapeSideA = coneMatch.groupValues[1].toFloat() // radius
                shapeHeight = coneMatch.groupValues[2].toFloat() // height
            } else if (cylinderMatch != null) {
                shapeType = "cylinder_cross_section"
                shapeSideA = cylinderMatch.groupValues[1].toFloat() // radius
                shapeHeight = cylinderMatch.groupValues[2].toFloat() // height
            } else {
                shapeType = "sphere_cross_section"
                shapeSideA = sphereMatch!!.groupValues[1].toFloat() // radius
                shapeHeight = shapeSideA
            }

            val r = shapeSideA
            val h = shapeHeight
            val displaySize = max(r * 2, h).coerceAtLeast(1f)
            scaleX = (width.coerceAtLeast(100) / (displaySize * 1.5f)).coerceIn(20f, 1000f)
            scaleY = scaleX
            offsetX = 0f
            offsetY = height / 4f

            preprocessedEquation = null
            invalidate()
            return
        }

        parabolaTitle = eq // Default title

        // Automated Scan Mode: scan(sin(x))
        if (lowerEq.startsWith("scan(")) {
            val inner = eq.substringAfter("(").substringBeforeLast(")")
            scanMode = true
            isTraceEnabled = true
            isAnimating = true
            traceX = 0.0
            preprocessedEquation = CalculatorHelper.preprocessExpression(inner, useRadians)
            startAnimationLoop()
            return
        }

        // Science Simulations
        if (lowerEq.startsWith("physics(")) {
            currentScienceMode = ScienceMode.PROJECTILE
            isAnimating = true
            projectileTime = 0f
            isAirResistanceEnabled = lowerEq.contains("drag")
            startAnimationLoop()
            invalidate()
            return
        }
        if (lowerEq.startsWith("refract(")) {
            currentScienceMode = ScienceMode.REFRACTION
            val parts = lowerEq.substringAfter("(").substringBefore(")").split(",")
            refractionIndexN1 = parts[0].toDoubleOrNull() ?: 1.5
            refractionIndexN2 = 1.0
            invalidate()
            return
        }
        if (lowerEq.startsWith("noise(")) {
            currentScienceMode = ScienceMode.NOISE
            noiseSeed = System.currentTimeMillis()
            isAnimating = true
            noiseTime = 0f
            startAnimationLoop()
            invalidate()
            return
        }
        if (lowerEq.startsWith("bezier(")) {
            currentScienceMode = ScienceMode.BEZIER
            bezierPoints.clear()
            bezierPoints.add(PointF(-4f, -2f))
            bezierPoints.add(PointF(-2f, 4f))
            bezierPoints.add(PointF(2f, -4f))
            bezierPoints.add(PointF(4f, 2f))
            invalidate()
            return
        }

        // Fly-through Mode: fly(cos(x))
        if (lowerEq.startsWith("fly(")) {
            val inner = eq.substringAfter("(").substringBeforeLast(")")
            flyMode = true
            isAnimating = true
            flyTime = 0f
            preprocessedEquation = CalculatorHelper.preprocessExpression(inner, useRadians)
            startAnimationLoop()
            return
        }

        // Check for spiral command: e.g. "spiral(10, 20, 3)"
        if (lowerEq.contains("spiral(")) {
            val parts = lowerEq.substringAfter("(").substringBefore(")").split(",")
            if (parts.size >= 3) {
                spiralMode = true
                spiralDiameter = parts[0].toFloatOrNull() ?: 10f
                spiralHoles = parts[1].toIntOrNull() ?: 20
                spiralTurns = parts[2].toFloatOrNull() ?: 3f

                scaleX = (width.coerceAtLeast(100) / (spiralDiameter * 1.5f)).coerceIn(10f, 500f)
                scaleY = scaleX
                offsetX = 0f
                offsetY = 0f
                applyParabolaStyle()
                invalidate()
                return
            }
        }

        // Check for nested command: e.g. "nested(5, 6, 4, 0.5)"
        if (lowerEq.contains("nested(")) {
            val parts = lowerEq.substringAfter("(").substringBefore(")").split(",")
            if (parts.size >= 4) {
                nestedMode = true
                nestedDiameter = parts[0].toFloatOrNull() ?: 5f
                nestedSides = parts[1].toIntOrNull() ?: 6
                nestedCount = parts[2].toIntOrNull() ?: 4
                nestedSpacing = parts[3].toFloatOrNull() ?: 0.5f

                scaleX = (width.coerceAtLeast(100) / (nestedDiameter * 1.5f)).coerceIn(10f, 500f)
                scaleY = scaleX
                offsetX = 0f
                offsetY = 0f
                applyParabolaStyle()
                invalidate()
                return
            }
        }

        // Check for quintic command: e.g. "quintic(1, -5, 5, 5, -5, -1)"
        if (lowerEq.contains("quintic")) {
            val parts = lowerEq.substringAfter("(").substringBefore(")").split(",")
            if (parts.size == 6) {
                quinticMode = true
                quinticA = parts[0].toDoubleOrNull() ?: 0.0
                quinticB = parts[1].toDoubleOrNull() ?: 0.0
                quinticC = parts[2].toDoubleOrNull() ?: 0.0
                quinticD = parts[3].toDoubleOrNull() ?: 0.0
                quinticE = parts[4].toDoubleOrNull() ?: 0.0
                quinticF = parts[5].toDoubleOrNull() ?: 0.0

                val allRoots = CalculatorHelper.solveQuinticNumerical(listOf(quinticA, quinticB, quinticC, quinticD, quinticE, quinticF))
                quinticRoots.clear()
                quinticComplexRoots.clear()
                for (r in allRoots) {
                    if (abs(r.imag) < 1e-9) quinticRoots.add(r.real) else quinticComplexRoots.add(r)
                }
                calculateQuinticTurningPoints()

                val rangeX = if (quinticRoots.size >= 2) abs(quinticRoots.last() - quinticRoots.first()).coerceAtLeast(4.0) else 8.0
                scaleX = (width.coerceAtLeast(100) / (rangeX * 2.0f)).toFloat().coerceIn(30f, 300f)
                scaleY = scaleX
                offsetX = -(quinticRoots.average().toFloat() * scaleX)
                offsetY = 0f
                applyParabolaStyle()
                invalidate()
                return
            }
        }

        // Check for quartic command: e.g. "quartic(1, 0, -5, 0, 4)"
        if (lowerEq.contains("quartic")) {
            val parts = lowerEq.substringAfter("(").substringBefore(")").split(",")
            if (parts.size == 5) {
                quarticMode = true
                quarticA = parts[0].toDoubleOrNull() ?: 0.0
                quarticB = parts[1].toDoubleOrNull() ?: 0.0
                quarticC = parts[2].toDoubleOrNull() ?: 0.0
                quarticD = parts[3].toDoubleOrNull() ?: 0.0
                quarticE = parts[4].toDoubleOrNull() ?: 0.0

                val allRoots = CalculatorHelper.solveQuartic(quarticA, quarticB, quarticC, quarticD, quarticE)
                quarticRoots.clear()
                quarticComplexRoots.clear()
                for (r in allRoots) {
                    if (abs(r.imag) < 1e-9) quarticRoots.add(r.real) else quarticComplexRoots.add(r)
                }
                calculateQuarticTurningPoints()

                // Auto Scale
                val rangeX = if (quarticRoots.size >= 2) abs(quarticRoots.last() - quarticRoots.first()).coerceAtLeast(4.0) else 8.0
                scaleX = (width.coerceAtLeast(100) / (rangeX * 2.0f)).toFloat().coerceIn(30f, 300f)
                scaleY = scaleX
                offsetX = -(quarticRoots.average().toFloat() * scaleX)
                offsetY = 0f
                applyParabolaStyle()
                invalidate()
                return
            }
        }

        // Check for cubic command
        if (lowerEq.contains("cubic")) {
            val parts = lowerEq.substringAfter("(").substringBefore(")").split(",")
            if (parts.size == 4) {
                cubicMode = true
                cubicA = parts[0].toDoubleOrNull() ?: 0.0
                cubicB = parts[1].toDoubleOrNull() ?: 0.0
                cubicC = parts[2].toDoubleOrNull() ?: 0.0
                cubicD = parts[3].toDoubleOrNull() ?: 0.0

                val allRoots = CalculatorHelper.solveCubic(cubicA, cubicB, cubicC, cubicD)
                cubicRoots.clear()
                cubicComplexRoots.clear()
                for (r in allRoots) {
                    if (abs(r.imag) < 1e-9) cubicRoots.add(r.real) else cubicComplexRoots.add(r)
                }

                val rangeX = if (cubicRoots.size >= 2) abs(cubicRoots.last() - cubicRoots.first()).coerceAtLeast(4.0) else 8.0
                scaleX = (width.coerceAtLeast(100) / (rangeX * 2.0f)).toFloat().coerceIn(30f, 300f)
                val yAtRootOffset = abs(cubicA * (rangeX/4.0).pow(3))
                scaleY = if (yAtRootOffset > 50) (height / (yAtRootOffset * 1.5f)).toFloat().coerceIn(scaleX / 10f, scaleX) else scaleX
                offsetX = -(cubicRoots.average().toFloat() * scaleX)
                offsetY = 0f
                applyParabolaStyle()
                invalidate()
                return
            }
        }

        val quadRegex = "quad\\((-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)\\)".toRegex()
        val quadMatch = quadRegex.find(lowerEq)
        if (quadMatch != null) {
            parabolaMode = true
            parabolaA = quadMatch.groupValues[1].toDouble()
            parabolaB = quadMatch.groupValues[2].toDouble()
            parabolaC = quadMatch.groupValues[3].toDouble()

            val vx = -parabolaB / (2.0 * parabolaA)
            val vy = parabolaA * vx * vx + parabolaB * vx + parabolaC
            parabolaVertex = PointF(vx.toFloat(), vy.toFloat())

            parabolaRoots.clear()
            parabolaComplexRoots.clear()
            val disc = parabolaB * parabolaB - 4.0 * parabolaA * parabolaC
            if (disc >= 0) {
                parabolaRoots.add((-parabolaB + sqrt(disc)) / (2.0 * parabolaA))
                parabolaRoots.add((-parabolaB - sqrt(disc)) / (2.0 * parabolaA))
            } else {
                val re = -parabolaB / (2.0 * parabolaA)
                val im = sqrt(-disc) / (2.0 * parabolaA)
                parabolaComplexRoots.add(Complex(re, im))
                parabolaComplexRoots.add(Complex(re, -im))
            }

            // Auto Scale
            val rangeX = if (disc >= 0) {
                abs(parabolaRoots[0] - parabolaRoots[1]).coerceAtLeast(2.0)
            } else 4.0

            scaleX = (width.coerceAtLeast(100) / (rangeX * 2.5f)).toFloat().coerceIn(40f, 400f)
            scaleY = scaleX
            offsetX = -vx.toFloat() * scaleX
            offsetY = vy.toFloat() * scaleY - height / 6f

            applyParabolaStyle()
            invalidate()
            return
        }

        // Check for solve command: e.g. "solve(x^2 - 10x + 8)"
        val solveRegex = "solve\\((.*)\\)".toRegex()
        val solveMatch = solveRegex.find(lowerEq)
        if (solveMatch != null) {
            solveMode = true
            solveExpr = solveMatch.groupValues[1]
            scaleX = 50f
            scaleY = 50f
            offsetX = 0f
            offsetY = 0f
            invalidate()
            return
        }

        // 0. Check for merged trig command: e.g. "2.887 / (cos(30) * tan(30))" or "2.887 / sin(30)"
        val mergedRegex = "(\\d+\\.?\\d*)/\\(?cos\\(?(\\d+\\.?\\d*)\\)?\\*tan\\(?(\\d+\\.?\\d*)\\)?\\)?".toRegex()
        val simpleHypRegex = "(\\d+\\.?\\d*)/sin\\(?(\\d+\\.?\\d*)\\)?".toRegex()

        val mergedMatch = mergedRegex.find(lowerEq)
        val simpleHypMatch = simpleHypRegex.find(lowerEq)

        if (mergedMatch != null || simpleHypMatch != null) {
            triangleMode = true
            mergedTrigMode = true
            triHeight = if (mergedMatch != null) mergedMatch.groupValues[1].toFloat() else simpleHypMatch!!.groupValues[1].toFloat()
            triAngle = if (mergedMatch != null) mergedMatch.groupValues[2].toFloat() else simpleHypMatch!!.groupValues[2].toFloat()

            val angleRad = Math.toRadians(triAngle.toDouble())
            triBase = (triHeight / tan(angleRad)).toFloat()
            triHypotenuse = (triHeight / sin(angleRad)).toFloat()
            triFunc = if (mergedMatch != null) "merged" else "sin"

            scaleX = (width.coerceAtLeast(100) / (max(triBase, triHeight) * 1.8f)).coerceAtMost(500f)
            scaleY = scaleX
            offsetX = -width / 5f
            offsetY = height / 5f
            invalidate()
            return
        }

        // Check for piston command: e.g. "10 pivot 25 piston move" or "10 pivot move 25 piston"
        val pistonRegex = "(\\d+\\.?\\d*)\\s*pivot.*?(\\d+\\.?\\d*)\\s*piston".toRegex()
        val pistonMatch = pistonRegex.find(lowerEq)
        if (pistonMatch != null) {
            pistonMode = true
            pistonCrank = pistonMatch.groupValues[1].toFloat()
            pistonRod = pistonMatch.groupValues[2].toFloat().coerceAtLeast(pistonCrank * 1.5f)

            if (lowerEq.contains("move")) {
                isAnimating = true
                // Keep existing animationAngle if already running
                startAnimationLoop()
            } else {
                animationAngle = 0f
                isAnimating = false
            }

            scaleX = (width.coerceAtLeast(100) / (pistonCrank + pistonRod) / 1.5f).coerceAtMost(500f)
            scaleY = scaleX
            offsetX = 0f
            offsetY = height / 4f
            invalidate()
            return
        }

        // Check for pivot command: e.g. "10m pivot 45" or "10m pivot move"
        val pivotRegex = "(\\d+\\.?\\d*)m?pivot(\\d+\\.?\\d*|move)".toRegex()
        val pivotMatch = pivotRegex.find(lowerEq)
        if (pivotMatch != null) {
            pivotMode = true
            pivotRadius = pivotMatch.groupValues[1].toFloat()
            val angleVal = pivotMatch.groupValues[2]

            if (angleVal == "move") {
                isAnimating = true
                // Keep current animationAngle
                startAnimationLoop()
            } else {
                animationAngle = angleVal.toFloat()
                isAnimating = false
            }

            scaleX = (width.coerceAtLeast(100) / (pivotRadius * 2.5f)).coerceAtMost(500f)
            scaleY = scaleX
            offsetX = 0f
            offsetY = 0f
            invalidate()
            return
        }

        // 1. Check for geometric shapes
        val polyRegex = "(pentagon|hexagon|octagon|polygon|chord|rectangle|square|circle|triangle|trapezoid)\\((\\d+\\.?\\d*)(?:,\\s*(\\d+\\.?\\d*))?(?:,\\s*(\\d+\\.?\\d*))?\\)".toRegex()
        val holeRegex = "(\\d+\\.?\\d*)m?round(\\d+)hole(?:margin(\\d+\\.?\\d*)(inch|m)?)?".toRegex()

        val polyMatch = polyRegex.find(lowerEq)
        val holeMatch = holeRegex.find(lowerEq)

        // 2. Check for milestone trig point: e.g. "10*sin(72)" or "5/cos(30)"
        val msRegex1 = "(\\d+\\.?\\d*)([*×/])?(sin|cos|tan)\\(?(-?\\d+\\.?\\d*)\\)?".toRegex()
        val msRegex2 = "(sin|cos|tan)\\(?(-?\\d+\\.?\\d*)\\)?([*×/])?(\\d+\\.?\\d*)".toRegex()

        val match1 = msRegex1.find(lowerEq)
        val match2 = msRegex2.find(lowerEq)

        if (polyMatch != null || holeMatch != null) {
            geometricMode = true
            isHoleMode = holeMatch != null
            holeMargin = 0f

            if (holeMatch != null) {
                shapeType = "hole_pattern"
                shapeSize = holeMatch.groupValues[1].toFloat()
                polygonSides = holeMatch.groupValues[2].toInt().coerceAtLeast(1)
                val marginVal = holeMatch.groupValues[3].toFloatOrNull() ?: 0f
                val marginUnit = holeMargin.toString() // Placeholder
                holeMargin = if (lowerEq.contains("inch")) marginVal * 0.0254f else marginVal
            } else {
                shapeType = polyMatch!!.groupValues[1]
                val val1 = polyMatch.groupValues[2].toFloat()
                val val2 = if (polyMatch.groupValues.size > 3) polyMatch.groupValues[3].toFloatOrNull() ?: 0f else 0f
                val val3 = if (polyMatch.groupValues.size > 4) polyMatch.groupValues[4].toFloatOrNull() ?: 0f else 0f

                when (shapeType) {
                    "rectangle" -> {
                        shapeWidth = val1
                        shapeHeight = val2
                        shapeSize = max(val1, val2)
                    }
                    "square" -> {
                        shapeWidth = val1
                        shapeHeight = val1
                        shapeSize = val1
                    }
                    "circle" -> {
                        shapeSize = val1 * 2 // treat input as radius
                    }
                    "triangle" -> {
                        shapeWidth = val1 // base
                        shapeHeight = val2 // height
                        shapeSize = max(val1, val2)
                    }
                    "trapezoid" -> {
                        shapeSideA = val1 // top
                        shapeSideB = val2 // bottom
                        shapeHeight = val3 // height
                        shapeWidth = max(val1, val2)
                        shapeSize = max(shapeWidth, shapeHeight)
                    }
                    "pentagon" -> { polygonSides = 5; shapeSize = val1 }
                    "hexagon" -> { polygonSides = 6; shapeSize = val1 }
                    "octagon" -> { polygonSides = 8; shapeSize = val1 }
                    "polygon" -> { polygonSides = val2.toInt().coerceAtLeast(3); shapeSize = val1 }
                    "chord" -> { polygonSides = val2.toInt().coerceAtLeast(3); shapeSize = val1 }
                }
            }
            preprocessedEquation = null

            // Set scale and offset
            val displaySize = if (shapeType == "rectangle" || shapeType == "triangle") max(shapeWidth, shapeHeight) else shapeSize
            scaleX = (width.coerceAtLeast(100) / (displaySize + holeMargin * 2.5f)).coerceIn(50f, 500f)
            scaleY = scaleX

            if (isHoleMode && holeMargin > 0) {
                // Shift view so (0,0) corner is visible
                offsetX = -width / 2.5f
                offsetY = height / 3f
            } else {
                offsetX = 0f
                offsetY = 0f
            }
            invalidate()
            return
        } else if ((match1 != null || match2 != null) && !radians) {
            val (amplitude, op, func, angle) = if (match1 != null) {
                val operator = if (match1.groupValues[2].isEmpty()) "*" else match1.groupValues[2]
                Quad(match1.groupValues[1].toDouble(), operator, match1.groupValues[3], match1.groupValues[4].toDouble())
            } else {
                val operator = if (match2!!.groupValues[3].isEmpty()) "*" else match2.groupValues[3]
                Quad(match2.groupValues[4].toDouble(), operator, match2.groupValues[1], match2.groupValues[2].toDouble())
            }

            // Detect hole layout from trig expression (e.g. 2 * sin(18))
            val isHolePattern = func == "sin" && angle > 0 && abs(180.0 / angle - round(180.0 / angle)) < 0.001
            if (isHolePattern) {
                geometricMode = true
                isHoleMode = true
                shapeType = "hole_pattern"
                shapeSize = amplitude.toFloat()
                polygonSides = round(180.0 / angle).toInt()
                preprocessedEquation = null

                scaleX = (width.coerceAtLeast(100) / (shapeSize * 1.5f)).coerceIn(50f, 500f)
                scaleY = scaleX
                offsetX = 0f
                offsetY = 0f
                invalidate()
                return
            }

            // Triangle detection for Tan/Cos/Sin
            if (func == "tan" || func == "cos" || func == "sin") {
                triangleMode = true
                val inputNum = amplitude.toFloat()
                triAngle = angle.toFloat()
                triFunc = func
                triOp = op

                val angleRad = Math.toRadians(angle)
                when (func) {
                    "tan" -> {
                        if (op == "/") {
                            // Formula: Base = Height / tan(Angle)
                            triHeight = inputNum
                            triBase = (triHeight / tan(angleRad)).toFloat()
                            triHypotenuse = sqrt(triBase * triBase + triHeight * triHeight).toFloat()
                        } else {
                            // Formula: Height = Base * tan(Angle)
                            triBase = inputNum
                            triHeight = (triBase * tan(angleRad)).toFloat()
                            triHypotenuse = sqrt(triBase * triBase + triHeight * triHeight).toFloat()
                        }
                    }
                    "cos" -> {
                        if (op == "/") {
                            // Formula: Diagonal = Base / cos(Angle)
                            triBase = inputNum
                            triHypotenuse = (triBase / cos(angleRad)).toFloat()
                            triHeight = sqrt(triHypotenuse * triHypotenuse - triBase * triBase).toFloat()
                        } else {
                            // Formula: Base = Diagonal * cos(Angle)
                            triHypotenuse = inputNum
                            triBase = (triHypotenuse * cos(angleRad)).toFloat()
                            triHeight = sqrt(triHypotenuse * triHypotenuse - triBase * triBase).toFloat()
                        }
                    }
                    "sin" -> {
                        if (op == "/") {
                            // Formula: Diagonal = Height / sin(Angle)
                            triHeight = inputNum
                            triHypotenuse = (triHeight / sin(angleRad)).toFloat()
                            triBase = sqrt(triHypotenuse * triHypotenuse - triHeight * triHeight).toFloat()
                        } else {
                            // Formula: Height = Diagonal * sin(Angle)
                            triHypotenuse = inputNum
                            triHeight = (triHypotenuse * sin(angleRad)).toFloat()
                            triBase = sqrt(triHypotenuse * triHypotenuse - triHeight * triHeight).toFloat()
                        }
                    }
                }

                scaleX = (width.coerceAtLeast(100) / (max(triBase, triHeight) * 1.5f)).coerceAtMost(500f)
                scaleY = scaleX
                offsetX = -width / 4f
                offsetY = height / 4f
                invalidate()
                return
            }

            milestoneMode = true
            milestoneAmplitude = amplitude
            milestoneX = angle.toFloat()

            val angleRad = Math.toRadians(angle)
            milestoneY = (milestoneAmplitude * when(func) {
                "sin" -> sin(angleRad)
                "cos" -> cos(angleRad)
                "tan" -> tan(angleRad)
                else -> 0.0
            }).toFloat()

            milestoneLabel = "Point: (${angle.toInt()}°, ${String.format("%.2f", milestoneY)})"

            val waveExpr = "${milestoneAmplitude}*$func(x)"
            this.preprocessedEquation = CalculatorHelper.preprocessExpression(waveExpr, true)

            // Auto-scale to show 0-360 range
            scaleX = width.coerceAtLeast(100) / 450f
            scaleY = height.coerceAtLeast(100) / (milestoneAmplitude.toFloat() * 2.8f)
            offsetX = -width / 2.5f
            offsetY = 0f
            invalidate()
            return
        } else {
            val lowerEqClean = lowerEq.replace(" ", "")
            val hasX = lowerEqClean.contains("x")
            val hasY = lowerEqClean.contains("y")

            if (hasX && hasY && !geometricMode) {
                surfaceMode = true
                mode3D = true
                this.preprocessedEquation = CalculatorHelper.preprocessExpression(eq, true)
                scaleX = width / 12f
                scaleY = scaleX
                offsetX = 0f
                offsetY = 0f
                invalidate()
                return
            }

            // Detect variables including geometry shorthand
            val hasVariables = lowerEqClean.contains("x") || lowerEqClean.contains("y") ||
                    lowerEqClean.contains("s") || lowerEqClean.contains("r") ||
                    lowerEqClean.contains("h") || lowerEqClean.contains("l") ||
                    lowerEqClean.contains("w") || lowerEqClean.contains("d") ||
                    lowerEqClean.contains("b1") || lowerEqClean.contains("b2") ||
                    lowerEqClean.contains("d1") || lowerEqClean.contains("d2") ||
                    lowerEqClean.contains("r1") || lowerEqClean.contains("r2") ||
                    lowerEqClean.contains("r3") ||
                    lowerEqClean.contains("slant_h")

            val evalRes = if (hasVariables) null else CalculatorHelper.evaluateFull(eq, radians)
            val evalConst = if (evalRes is Double) evalRes else null
            if (evalConst != null && !evalConst.isNaN() && !evalConst.isInfinite()) {
                val sin36 = sin(Math.toRadians(36.0))
                val diameter = evalConst / sin36
                val roundedD = round(diameter * 10) / 10.0
                // Only trigger shape if constant is non-zero and matches
                if (abs(diameter) > 0.01 && abs(diameter - roundedD) < 0.001) {
                    geometricMode = true
                    polygonSides = 5
                    shapeSize = roundedD.toFloat()
                    preprocessedEquation = null
                } else {
                    this.preprocessedEquation = CalculatorHelper.preprocessExpression(eq, true)
                }
            } else {
                this.preprocessedEquation = CalculatorHelper.preprocessExpression(eq, true)
            }
        }

        if (geometricMode) {
            scaleX = (width.coerceAtLeast(100) / (shapeSize * 1.5f)).coerceIn(50f, 500f)
            scaleY = scaleX
        } else {
            val lowerEqRawLocal = equation?.lowercase()?.replace(" ", "") ?: ""
            val isGeoFunc = lowerEqRawLocal.startsWith("area") || lowerEqRawLocal.startsWith("volume") || lowerEqRawLocal.startsWith("surfacearea")

            if (isGeoFunc) {
                scaleX = width.coerceAtLeast(100) / 15f // Show 0 to 15 on X
                // For Y, we need a smaller scale because these functions grow fast
                // e.g. 6s^2 at s=10 is 600.
                scaleY = height.coerceAtLeast(100) / 800f
                offsetX = -width / 2.5f // Shift to see positive quadrant
                offsetY = height / 3f
            } else {
                scaleX = width.coerceAtLeast(100) / 10f
                scaleY = scaleX
                offsetX = 0f
                offsetY = 0f
            }
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pendingEquation?.let { setEquation(it, pendingRadians) }
    }

    private fun applyParabolaStyle() {
        axisPaint.color = Color.BLACK
        gridPaint.color = Color.LTGRAY
        gridPaint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        textPaint.color = Color.DKGRAY
        labelPaint.color = Color.BLACK
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isAnimating) {
            if (scanMode) {
                val cx = width / 2f + offsetX
                val startX = (-cx / scaleX).toDouble()
                val endX = ((width - cx) / scaleX).toDouble()
                val speed = (endX - startX).coerceAtLeast(10.0) * 0.005

                traceX = (traceX ?: startX) + (scanDirection * speed)
                if (traceX!! >= endX) {
                    traceX = endX
                    scanDirection = -1
                } else if (traceX!! <= startX) {
                    traceX = startX
                    scanDirection = 1
                }
                updateTrace(traceX!!)
                postInvalidateOnAnimation()
            } else if (currentScienceMode == ScienceMode.PROJECTILE) {
                projectileTime += 0.05f
                if (projectileTime > 10f) projectileTime = 10f // Stop at ground
                postInvalidateOnAnimation()
            } else if (currentScienceMode == ScienceMode.NOISE) {
                noiseTime += 0.05f
                generateNoiseBitmap()
                postInvalidateOnAnimation()
            } else if (flyMode) {
                flyTime += 0.02f
                offsetX -= 4f * (scaleX / 100f)
                offsetY = sin(flyTime.toDouble()).toFloat() * 40f
                postInvalidateOnAnimation()
            } else if (isAnimating) {
                animationAngle += rotationSpeed
                postInvalidateOnAnimation()
            }
        }

        val centerX = width / 2f + offsetX
        val centerY = height / 2f + offsetY

        if (mode3D || parabolaMode || cubicMode || quarticMode || quinticMode || spiralMode || nestedMode || flyMode || scanMode || currentScienceMode != ScienceMode.NONE) {
            canvas.drawColor(Color.parseColor("#F8F9FA")) // Off-white background
        } else {
            canvas.drawColor(Color.parseColor("#121212"))
        }

        drawGrid(canvas, centerX, centerY)
        drawAxes(canvas, centerX, centerY)

        if (mode3D) {
            draw3DAxes(canvas, centerX, centerY)
        }

        when (currentScienceMode) {
            ScienceMode.PROJECTILE -> drawProjectileMotion(canvas, centerX, centerY)
            ScienceMode.REFRACTION -> drawRefraction(canvas, centerX, centerY)
            ScienceMode.NOISE -> drawNoiseMap(canvas, centerX, centerY)
            ScienceMode.BEZIER -> drawBezierSimulation(canvas, centerX, centerY)
            ScienceMode.NONE -> {
                if (surfaceMode) {
                    draw3DSurface(canvas, centerX, centerY)
                } else if (mode3D) {
                    draw3DGeometric(canvas, centerX, centerY)
                } else if (spiralMode) {
                    drawSpiralLayout(canvas, centerX, centerY)
                } else if (nestedMode) {
                    drawNestedPolygons(canvas, centerX, centerY)
                } else if (binomialMode) {
                    drawBinomialGeometric(canvas, centerX, centerY)
                } else if (quinticMode) {
                    drawQuinticEnhanced(canvas, centerX, centerY)
                } else if (quarticMode) {
                    drawQuarticEnhanced(canvas, centerX, centerY)
                } else if (cubicMode) {
                    drawCubicEnhanced(canvas, centerX, centerY)
                } else if (parabolaMode) {
                    drawParabolaEnhanced(canvas, centerX, centerY)
                } else if (geometricMode) {
                    drawGeometricShape(canvas, centerX, centerY)
                } else if (triangleMode) {
                    drawTriangle(canvas, centerX, centerY)
                } else if (pivotMode) {
                    drawPivot(canvas, centerX, centerY)
                } else if (pistonMode) {
                    drawPiston(canvas, centerX, centerY)
                } else if (quadMode) {
                    drawQuadratic(canvas, centerX, centerY)
                } else if (solveMode) {
                    drawSolve(canvas, centerX, centerY)
                } else {
                    preprocessedEquation?.let { eq ->
                        drawGraph(canvas, eq, centerX, centerY)
                    }
                }
            }
        }

        if (flyMode) {
            drawFlyOverlay(canvas)
        }
        if (scanMode) {
            drawScanOverlay(canvas)
        }

        if (isTraceEnabled) {
            drawTrace(canvas, centerX, centerY)
        }
    }

    private fun drawScanOverlay(canvas: Canvas) {
        val paint = Paint().apply {
            color = Color.parseColor("#44000000")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 100f, paint)

        val textPaintScan = Paint().apply {
            color = Color.CYAN
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("AUTOMATED ROOT/PEAK SCAN ACTIVE", width/2f, 65f, textPaintScan)
    }

    private fun drawBinomialGeometric(canvas: Canvas, centerX: Float, centerY: Float) {
        val a = binomialA * scaleX
        val b = binomialB * scaleX

        val paint = Paint().apply {
            strokeWidth = 4f
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        if (!binomialIsMinus) {
            // (a+b)^2 Proof
            // Part a^2 (Top-Left)
            paint.color = Color.parseColor("#BBDEFB") // Blue
            canvas.drawRect(centerX, centerY - a, centerX + a, centerY, paint)
            canvas.drawText("a²", centerX + a/2, centerY - a/2 + 10, textPaint)

            // Part ab (Top-Right)
            paint.color = Color.parseColor("#C8E6C9") // Green
            canvas.drawRect(centerX + a, centerY - a, centerX + a + b, centerY, paint)
            canvas.drawText("ab", centerX + a + b/2, centerY - a/2 + 10, textPaint)

            // Part ab (Bottom-Left)
            paint.color = Color.parseColor("#C8E6C9") // Green
            canvas.drawRect(centerX, centerY, centerX + a, centerY + b, paint)
            canvas.drawText("ab", centerX + a/2, centerY + b/2 + 10, textPaint)

            // Part b^2 (Bottom-Right)
            paint.color = Color.parseColor("#FFF9C4") // Yellow
            canvas.drawRect(centerX + a, centerY, centerX + a + b, centerY + b, paint)
            canvas.drawText("b²", centerX + a + b/2, centerY + b/2 + 10, textPaint)

            // Labels on sides
            textPaint.color = Color.WHITE
            canvas.drawText("a", centerX + a/2, centerY - a - 15, textPaint)
            canvas.drawText("b", centerX + a + b/2, centerY - a - 15, textPaint)
            canvas.drawText("a", centerX - 25, centerY - a/2 + 10, textPaint)
            canvas.drawText("b", centerX - 25, centerY + b/2 + 10, textPaint)
        } else {
            // (a-b)^2 Proof
            // Large square side 'a'
            paint.color = Color.parseColor("#BBDEFB") // Blue
            canvas.drawRect(centerX, centerY, centerX + a, centerY + a, paint)

            // Sub-square (a-b)^2
            val amb = (binomialA - binomialB) * scaleX
            paint.color = Color.parseColor("#64B5F6") // Darker Blue
            canvas.drawRect(centerX, centerY, centerX + amb, centerY + amb, paint)
            canvas.drawText("(a-b)²", centerX + amb/2, centerY + amb/2 + 10, textPaint)

            // Removed pieces
            paint.color = Color.parseColor("#EF9A9A") // Reddish
            canvas.drawRect(centerX + amb, centerY, centerX + a, centerY + amb, paint)
            canvas.drawText("b(a-b)", centerX + amb + b/2, centerY + amb/2 + 10, textPaint)

            canvas.drawRect(centerX, centerY + amb, centerX + amb, centerY + a, paint)
            canvas.drawText("b(a-b)", centerX + amb/2, centerY + amb + b/2 + 10, textPaint)

            paint.color = Color.parseColor("#FFCCBC") // Orange
            canvas.drawRect(centerX + amb, centerY + amb, centerX + a, centerY + a, paint)
            canvas.drawText("b²", centerX + amb + b/2, centerY + amb + b/2 + 10, textPaint)

            // Labels
            textPaint.color = Color.WHITE
            canvas.drawText("a", centerX + a/2, centerY - 15, textPaint)
            canvas.drawText("a", centerX - 25, centerY + a/2 + 10, textPaint)
        }
    }

    private fun drawFlyOverlay(canvas: Canvas) {
        val paint = Paint().apply {
            color = Color.parseColor("#44000000")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 100f, paint)

        val textPaintFly = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("GRAPH FLY-THROUGH MODE ACTIVE", width/2f, 65f, textPaintFly)
    }

    private fun drawTrace(canvas: Canvas, centerX: Float, centerY: Float) {
        val tx = traceX ?: return
        val ty = traceY ?: return

        val px = (centerX + tx * scaleX).toFloat()
        val py = (centerY - ty * scaleY).toFloat()

        val tracePaint = Paint().apply {
            color = Color.parseColor("#FF4081")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
        canvas.drawLine(px, 0f, px, height.toFloat(), tracePaint)
        canvas.drawLine(0f, py, width.toFloat(), py, tracePaint)

        val dotPaint = Paint().apply {
            color = Color.parseColor("#FF4081")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(px, py, 12f, dotPaint)

        // Draw coordinate box
        val boxPaint = Paint().apply { color = Color.parseColor("#E91E63") }
        val textPaintTrace = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val label = "X: ${String.format("%.3f", tx)}, Y: ${String.format("%.3f", ty)}"
        val bounds = Rect()
        textPaintTrace.getTextBounds(label, 0, label.length, bounds)

        val boxW = bounds.width() + 40f
        val boxH = bounds.height() + 30f
        var boxX = px + 20f
        var boxY = py - 40f

        if (boxX + boxW > width) boxX = px - boxW - 20f
        if (boxY < 0) boxY = py + 40f

        canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, 10f, 10f, boxPaint)
        canvas.drawText(label, boxX + 20f, boxY + boxH - 15f, textPaintTrace)
    }

    private fun drawSpiralLayout(canvas: Canvas, centerX: Float, centerY: Float) {
        val maxRadius = spiralDiameter / 2f

        // Title
        val titlePaint = Paint().apply { color = Color.parseColor("#333333"); textSize = 32f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        canvas.drawText("Archimedean Spiral Hole Pattern", width / 2f, 60f, titlePaint)

        // Draw Spiral Curve
        val path = Path()
        val totalTheta = 2.0 * PI * spiralTurns
        val spiralPaint = Paint(linePaint).apply { color = Color.parseColor("#00B8D4"); strokeWidth = 4f; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }

        for (i in 0..1000) {
            val theta = totalTheta * i / 1000.0
            val r = (maxRadius * theta / totalTheta).toFloat()
            val px = (centerX + r * cos(theta) * scaleX).toFloat()
            val py = (centerY - r * sin(theta) * scaleY).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, spiralPaint)

        // Draw Holes
        val n = spiralHoles
        for (i in 0 until n) {
            val theta = totalTheta * i / n
            val r = (maxRadius * theta / totalTheta).toFloat()
            val px = (centerX + r * cos(theta) * scaleX).toFloat()
            val py = (centerY - r * sin(theta) * scaleY).toFloat()
            canvas.drawCircle(px, py, 10f, pointPaint)
        }
    }

    private fun drawNestedPolygons(canvas: Canvas, centerX: Float, centerY: Float) {
        canvas.drawText("Nested Geometric Polygon Layout", width / 2f, 60f, Paint(textPaint).apply { color = Color.parseColor("#333333"); textSize = 32f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD })

        for (idx in 0 until nestedCount) {
            val r = (nestedDiameter / 2f - idx * nestedSpacing)
            if (r <= 0) break
            val rPx = r * scaleX
            val path = Path()
            val n = nestedSides
            for (i in 0 until n) {
                val angle = 2 * PI * i / n
                val vx = centerX + rPx * cos(angle).toFloat()
                val vy = centerY - rPx * sin(angle).toFloat()
                if (i == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
            }
            path.close()
            val p = Paint(linePaint).apply { color = if (idx % 2 == 0) Color.parseColor("#00B8D4") else Color.parseColor("#FF4081"); strokeWidth = 3f }
            canvas.drawPath(path, p)
        }
    }

    private fun calculateQuinticTurningPoints() {
        quinticTurningPoints.clear()
        // Derivative: 5ax^4 + 4bx^3 + 3cx^2 + 2dx + e = 0
        val allRoots = CalculatorHelper.solveQuartic(5.0 * quinticA, 4.0 * quinticB, 3.0 * quinticC, 2.0 * quinticD, quinticE)
        for (r in allRoots) {
            if (abs(r.imag) < 1e-9) {
                val x = r.real
                val y = quinticA * x.pow(5.0) + quinticB * x.pow(4.0) + quinticC * x.pow(3.0) + quinticD * x.pow(2.0) + quinticE * x + quinticF
                quinticTurningPoints.add(PointF(x.toFloat(), y.toFloat()))
            }
        }
    }

    private fun drawQuinticEnhanced(canvas: Canvas, centerX: Float, centerY: Float) {
        val path = Path()
        var first = true
        val startX = ((-centerX - 100) / scaleX).toDouble()
        val endX = ((width - centerX + 100) / scaleX).toDouble()
        val step = (endX - startX) / 1000.0

        for (i in 0..1000) {
            val x = startX + i * step
            val y = quinticA * x.pow(5.0) + quinticB * x.pow(4.0) + quinticC * x.pow(3.0) + quinticD * x.pow(2.0) + quinticE * x + quinticF
            val px = (centerX + x * scaleX).toFloat()
            val py = (centerY - y * scaleY).toFloat()
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)

        for (r in quinticRoots) {
            val px = (centerX + r * scaleX).toFloat()
            canvas.drawCircle(px, centerY, 12f, pointPaint)
            canvas.drawText("x=${String.format("%.2f", r)}", px + 10, centerY - 20, pointLabelPaint)
        }
        drawQuinticAnalysisBox(canvas)
    }

    private fun drawQuinticAnalysisBox(canvas: Canvas) {
        val paint = Paint().apply { color = Color.parseColor("#E6000000"); style = Paint.Style.FILL }
        canvas.drawRoundRect(20f, 20f, 600f, 260f, 15f, 15f, paint)
        val tp = Paint(textPaint).apply { color = Color.WHITE; textSize = 26f }
        canvas.drawText("QUINIC FUNCTION ANALYSIS", 40f, 60f, tp.apply { typeface = Typeface.DEFAULT_BOLD; color = Color.CYAN })
        canvas.drawText("Roots: ${quinticRoots.size} Real, ${quinticComplexRoots.size} Complex", 40f, 100f, tp)
        canvas.drawText("Turning Points: ${quinticTurningPoints.size}", 40f, 140f, tp)
        if (quinticTurningPoints.isNotEmpty()) {
            val peak = quinticTurningPoints.maxByOrNull { it.y }
            canvas.drawText("Global Max (Visible): ${String.format("%.2f", peak?.y)}", 40f, 180f, tp)
        }
    }

    private fun drawQuarticEnhanced(canvas: Canvas, centerX: Float, centerY: Float) {
        val path = Path()
        var first = true
        val startX = ((-centerX - 100) / scaleX).toDouble()
        val endX = ((width - centerX + 100) / scaleX).toDouble()
        val step = (endX - startX) / 1000.0

        for (i in 0..1000) {
            val x = startX + i * step
            val y = quarticA * x.pow(4.0) + quarticB * x.pow(3.0) + quarticC * x.pow(2.0) + quarticD * x + quarticE
            val px = (centerX + x * scaleX).toFloat()
            val py = (centerY - y * scaleY).toFloat()
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)

        for (r in quarticRoots) {
            val px = (centerX + r * scaleX).toFloat()
            canvas.drawCircle(px, centerY, 12f, pointPaint)
            canvas.drawText("x=${String.format("%.2f", r)}", px + 10, centerY - 20, pointLabelPaint)
        }
        drawQuarticVerificationBox(canvas)
    }

    private fun drawQuarticVerificationBox(canvas: Canvas) {
        val paint = Paint().apply { color = Color.parseColor("#E6000000"); style = Paint.Style.FILL }
        canvas.drawRoundRect(20f, 20f, 580f, 240f, 15f, 15f, paint)
        val tp = Paint(textPaint).apply { color = Color.WHITE; textSize = 26f }
        canvas.drawText("QUARTIC VERIFICATION", 40f, 60f, tp.apply { typeface = Typeface.DEFAULT_BOLD; color = Color.CYAN })
        canvas.drawText("Real Roots: ${quarticRoots.joinToString { String.format("%.2f", it) }}", 40f, 100f, tp)
        canvas.drawText("Turning Points: ${quarticTurningPoints.size}", 40f, 140f, tp)
        if (quarticTurningPoints.isNotEmpty()) {
            val minPt = quarticTurningPoints.minByOrNull { it.y }
            canvas.drawText("Minimum Value: ${String.format("%.2f", minPt?.y)}", 40f, 180f, tp)
        }
    }

    private fun drawCubicEnhanced(canvas: Canvas, centerX: Float, centerY: Float) {
        val path = Path()
        var first = true
        val startX = ((-centerX - 100) / scaleX).toDouble()
        val endX = ((width - centerX + 100) / scaleX).toDouble()
        val step = (endX - startX) / 1000.0

        for (i in 0..1000) {
            val x = startX + i * step
            val y = cubicA * x.pow(3.0) + cubicB * x.pow(2.0) + cubicC * x + cubicD
            val px = (centerX + x * scaleX).toFloat()
            val py = (centerY - y * scaleY).toFloat()
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)

        for (r in cubicRoots) {
            val px = (centerX + r * scaleX).toFloat()
            canvas.drawCircle(px, centerY, 12f, pointPaint)
            canvas.drawText("x=${String.format("%.2f", r)}", px + 10, centerY - 20, pointLabelPaint)
        }
        drawCubicVerificationBox(canvas)
    }

    private fun drawCubicVerificationBox(canvas: Canvas) {
        val paint = Paint().apply { color = Color.parseColor("#E6000000"); style = Paint.Style.FILL }
        canvas.drawRoundRect(20f, 20f, 550f, 220f, 15f, 15f, paint)
        val tp = Paint(textPaint).apply { color = Color.WHITE; textSize = 26f }
        canvas.drawText("CUBIC ANALYSIS", 40f, 60f, tp.apply { typeface = Typeface.DEFAULT_BOLD; color = Color.CYAN })
        canvas.drawText("Real Roots: ${cubicRoots.size}", 40f, 100f, tp)
        canvas.drawText("Inflection: x=${String.format("%.2f", -cubicB / (3 * cubicA))}", 40f, 140f, tp)
    }

    private fun drawParabolaEnhanced(canvas: Canvas, centerX: Float, centerY: Float) {
        val path = Path()
        var first = true
        val startX = ((-centerX - 100) / scaleX).toDouble()
        val endX = ((width - centerX + 100) / scaleX).toDouble()
        val step = (endX - startX) / 500.0

        for (i in 0..500) {
            val x = startX + i * step
            val y = parabolaA * x * x + parabolaB * x + parabolaC
            val px = (centerX + x * scaleX).toFloat()
            val py = (centerY - y * scaleY).toFloat()
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)

        // Draw vertex
        val vpx = (centerX + parabolaVertex.x * scaleX)
        val vpy = (centerY - parabolaVertex.y * scaleY)
        canvas.drawCircle(vpx, vpy, 15f, pointPaint)
        canvas.drawText("Vertex (${String.format("%.1f", parabolaVertex.x)}, ${String.format("%.1f", parabolaVertex.y)})", vpx + 20, vpy - 20, pointLabelPaint)

        // Draw roots
        for (r in parabolaRoots) {
            val px = (centerX + r * scaleX).toFloat()
            canvas.drawCircle(px, centerY, 12f, pointPaint)
        }
        drawParabolaVerificationBox(canvas)
    }

    private fun drawParabolaVerificationBox(canvas: Canvas) {
        val paint = Paint().apply { color = Color.parseColor("#E6000000"); style = Paint.Style.FILL }
        canvas.drawRoundRect(20f, 20f, 520f, 220f, 15f, 15f, paint)
        val tp = Paint(textPaint).apply { color = Color.WHITE; textSize = 26f }
        canvas.drawText("PARABOLA ANALYSIS", 40f, 60f, tp.apply { typeface = Typeface.DEFAULT_BOLD; color = Color.CYAN })
        canvas.drawText("A=${String.format("%.2f", parabolaA)}, B=${String.format("%.2f", parabolaB)}, C=${String.format("%.2f", parabolaC)}", 40f, 100f, tp)
        canvas.drawText("Roots: ${if (parabolaRoots.isNotEmpty()) parabolaRoots.size else "Complex"}", 40f, 140f, tp)
        canvas.drawText("Vertex: (${String.format("%.2f", parabolaVertex.x)}, ${String.format("%.2f", parabolaVertex.y)})", 40f, 180f, tp)
    }

    private fun drawQuadratic(canvas: Canvas, centerX: Float, centerY: Float) {
        val path = Path()
        var first = true
        for (i in -100..100) {
            val x = i / 10f
            val y = quadA * x * x + quadB * x + quadC
            val px = centerX + x * scaleX
            val py = centerY - y * scaleY
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)
    }

    private fun drawSolve(canvas: Canvas, centerX: Float, centerY: Float) {
        val path = Path()
        var first = true
        for (i in -100..100) {
            val x = i / 10f
            val y = CalculatorHelper.evaluatePreprocessed(solveExpr, useRadians, x.toDouble()) ?: 0.0
            val px = centerX + x.toFloat() * scaleX
            val py = (centerY - y * scaleY).toFloat()
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)
    }

    fun toggleAnimation(): Boolean {
        isAnimating = !isAnimating
        if (isAnimating) startAnimationLoop()
        return isAnimating
    }

    private fun startAnimationLoop() {
        if (!isAnimating) return
        postInvalidateOnAnimation()
    }

    private fun stopAnimation() {
        isAnimating = false
    }

    fun setRotationSpeed(speed: Float) {
        rotationSpeed = speed
    }

    fun setTraceEnabled(enabled: Boolean) {
        isTraceEnabled = enabled
        if (!enabled) {
            traceX = null
            traceY = null
        }
        invalidate()
    }

    fun setMode3D(enabled: Boolean) {
        this.mode3D = enabled
        if (enabled) {
            rotationX = -25f
            rotationY = 45f
        }
        invalidate()
    }

    private fun drawPivot(canvas: Canvas, centerX: Float, centerY: Float) {
        val rad = pivotRadius * scaleX
        val angleRad = Math.toRadians(animationAngle.toDouble())
        val px = centerX + rad * cos(angleRad).toFloat()
        val py = centerY - rad * sin(angleRad).toFloat()

        canvas.drawCircle(centerX, centerY, rad, circlePaint)
        canvas.drawLine(centerX, centerY, px, py, linePaint)
        canvas.drawCircle(px, py, 15f, pointPaint)

        val label = "Point: (${String.format("%.2f", pivotRadius * cos(angleRad))}, ${String.format("%.2f", pivotRadius * sin(angleRad))})"
        canvas.drawText(label, px + 20, py - 20, pointLabelPaint)

        if (isAnimating) {
            animationAngle += rotationSpeed
            postInvalidateOnAnimation()
        }
    }

    private fun drawPiston(canvas: Canvas, centerX: Float, centerY: Float) {
        val currentAngle = animationAngle
        val radCrank = pistonCrank * scaleX
        val radRod = pistonRod * scaleX
        val angleRad = Math.toRadians(currentAngle.toDouble())

        // Crank pin position
        val pinX = centerX + radCrank * cos(angleRad).toFloat()
        val pinY = centerY - radCrank * sin(angleRad).toFloat()

        // Piston position on vertical center line
        val dx = pinX - centerX
        val h = sqrt(max(0f, radRod * radRod - dx * dx))
        val pistonY = pinY - h

        // Draw Crank Circle
        canvas.drawCircle(centerX, centerY, radCrank, circlePaint)

        // Draw Crank Arm
        val crankPaint = Paint(linePaint).apply { color = Color.RED; strokeWidth = 8f }
        canvas.drawLine(centerX, centerY, pinX, pinY, crankPaint)

        // Draw Connecting Rod
        val rodPaint = Paint(linePaint).apply { color = Color.CYAN; strokeWidth = 6f }
        canvas.drawLine(pinX, pinY, centerX, pistonY, rodPaint)

        // Draw Piston Head
        val pistonWidth = 80f
        val pistonHeight = 60f
        val pistonPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL }
        canvas.drawRect(centerX - pistonWidth/2, pistonY - pistonHeight/2, centerX + pistonWidth/2, pistonY + pistonHeight/2, pistonPaint)

        // Labels
        val labelPaintPiston = Paint(textPaint).apply { color = Color.WHITE; textSize = 24f }
        val strokePos = (centerY - pistonY) / scaleY
        canvas.drawText("Stroke Height = ${String.format("%.3f", strokePos)}m", centerX + pistonWidth/2 + 20f, pistonY, labelPaintPiston)
        canvas.drawText("Angle = ${currentAngle.toInt() % 360}°", centerX - radCrank - 100f, centerY, labelPaintPiston)

        // Draw HUD for Piston
        val hudPaint = Paint().apply { color = Color.parseColor("#E6000000"); style = Paint.Style.FILL }
        val hudTextPaint = Paint().apply { color = Color.CYAN; textSize = 26f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val titlePaint = Paint(hudTextPaint).apply { color = Color.WHITE; textSize = 30f; typeface = Typeface.DEFAULT_BOLD }

        val hudRect = RectF(20f, 20f, width - 20f, 220f)
        canvas.drawRoundRect(hudRect, 10f, 10f, hudPaint)

        canvas.drawText("Piston & Crankshaft Simulation", 60f, 80f, titlePaint)
        canvas.drawText("CRANK RADIUS: ${String.format("%.2f", pistonCrank)}m", 60f, 130f, hudTextPaint)
        canvas.drawText("ROD LENGTH  : ${String.format("%.2f", pistonRod)}m", 60f, 175f, hudTextPaint)

        val angleDisp = (currentAngle % 360).toInt()
        canvas.drawText("CRANK ANGLE : $angleDisp°", width - 400f, 130f, hudTextPaint)
        canvas.drawText("STROKE POS  : ${String.format("%.3f", strokePos)}m", width - 400f, 175f, hudTextPaint)

        if (isAnimating) {
            animationAngle += rotationSpeed
            postInvalidateOnAnimation()
        }
    }

    private fun drawTriangle(canvas: Canvas, centerX: Float, centerY: Float) {
        val bPx = triBase * scaleX
        val hPx = triHeight * scaleY

        // Vertices
        val x1 = centerX
        val y1 = centerY
        val x2 = centerX + bPx
        val y2 = centerY
        val x3 = centerX + bPx
        val y3 = centerY - hPx

        // Draw Triangle Path
        val trianglePaint = Paint(linePaint).apply {
            color = Color.parseColor("#00B8D4") // Teal color like in the photo
            strokeWidth = 5f
        }
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
            lineTo(x3, y3)
            close()
        }
        canvas.drawPath(path, trianglePaint)

        // Draw Labels
        val labelPaintTri = Paint(textPaint).apply {
            color = Color.WHITE
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }

        // Base label
        canvas.drawText("Base = ${String.format("%.3f", triBase)} m", centerX + bPx/2, centerY + 50f, labelPaintTri)

        // Height label
        val heightLabelPaint = Paint(labelPaintTri).apply {
            textAlign = Paint.Align.LEFT
        }
        val hInputText = if (mergedTrigMode) " (Known Input)" else ""
        canvas.drawText("Height = ${String.format("%.3f", triHeight)} m$hInputText", x2 + 20f, centerY - hPx/2, heightLabelPaint)

        // Hypotenuse label
        canvas.save()
        val hypAngle = Math.toDegrees(atan2(hPx, bPx).toDouble()).toFloat()
        canvas.rotate(-hypAngle, centerX + bPx/2, centerY - hPx/2)
        val hypPaint = Paint(labelPaintTri).apply { color = Color.parseColor("#FF4081") }
        canvas.drawText("Diagonal = ${String.format("%.3f", triHypotenuse)} m", centerX + bPx/2, centerY - hPx/2 - 25f, hypPaint)
        canvas.restore()

        // Angle arc
        val arcRadius = 70f
        val rect = RectF(x1 - arcRadius, y1 - arcRadius, x1 + arcRadius, y1 + arcRadius)
        val arcPaint = Paint(annotationLinePaint).apply {
            color = Color.parseColor("#FF4081")
            strokeWidth = 3f
        }
        canvas.drawArc(rect, 0f, -triAngle, false, arcPaint)
        canvas.drawText("${triAngle.toInt()}°", x1 + arcRadius + 25f, y1 - 15f, labelPaintTri)

        // Right angle symbol
        val rs = 30f
        canvas.drawLine(x2 - rs, y2, x2 - rs, y2 - rs, axisPaint)
        canvas.drawLine(x2, y2 - rs, x2 - rs, y2 - rs, axisPaint)

        if (mergedTrigMode) {
            drawMergedVerificationBox(canvas)
        } else {
            canvas.drawText("Final Layout Plan (All Sides Calculated)", width/2f, 60f, labelPaintTri)
        }
    }

    private fun drawMergedVerificationBox(canvas: Canvas) {
        val paint = Paint().apply { color = Color.parseColor("#CC000000"); style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, width.toFloat(), 100f, paint)
        val tp = Paint(textPaint).apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER }
        val info = "Input Angle: ${triAngle.toInt()}°, Input Height: ${String.format("%.2f", triHeight)}m"
        canvas.drawText(info, width / 2f, 65f, tp)
    }

    private fun drawGrid(canvas: Canvas, centerX: Float, centerY: Float) {
        val xStepUnits = getXStepUnits()
        val yStepUnits = getYStepUnits()

        val startXUnits = floor(-centerX / scaleX / xStepUnits) * xStepUnits
        val endXUnits = ceil((width - centerX) / scaleX / xStepUnits) * xStepUnits

        var x = startXUnits
        while (x <= endXUnits) {
            val px = (centerX + x * scaleX).toFloat()
            canvas.drawLine(px, 0f, px, height.toFloat(), gridPaint)
            x += xStepUnits
        }

        val startYUnits = floor(-centerY / scaleY / yStepUnits) * yStepUnits
        val endYUnits = ceil((height - centerY) / scaleY / yStepUnits) * yStepUnits

        var y = startYUnits
        while (y <= endYUnits) {
            val py = (centerY - y * scaleY).toFloat()
            canvas.drawLine(0f, py, width.toFloat(), py, gridPaint)
            y += yStepUnits
        }
    }

    private fun getXStepUnits(): Double {
        val baseStep = when {
            milestoneMode -> 45.0 // For trig milestones
            scaleX > 500 -> 0.1; scaleX > 200 -> 0.5; scaleX > 50 -> 1.0; scaleX > 20 -> 2.0; scaleX > 10 -> 5.0; else -> 20.0
        }
        var finalStep = baseStep
        while (finalStep * scaleX < 40) finalStep *= 2.0
        return finalStep
    }

    private fun getYStepUnits(): Double {
        val baseStep = when {
            milestoneMode -> (milestoneAmplitude / 2.0).coerceAtLeast(0.5)
            scaleY > 500 -> 0.1; scaleY > 200 -> 0.5; scaleY > 50 -> 1.0; scaleY > 20 -> 2.0; scaleY > 10 -> 5.0; else -> 20.0
        }
        var finalStep = baseStep
        while (finalStep * scaleY < 40) finalStep *= 2.0
        return finalStep
    }

    private fun drawAxes(canvas: Canvas, centerX: Float, centerY: Float) {
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, axisPaint)
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), axisPaint)

        if (centerX in 0f..width.toFloat() && centerY in 0f..height.toFloat()) {
            canvas.drawText("0", centerX + 5, centerY + 30, textPaint)
        }

        val xStepUnits = getXStepUnits()
        val yStepUnits = getYStepUnits()

        val minXUnits = -centerX / scaleX
        val maxXUnits = (width - centerX) / scaleX

        var xVal = ceil(minXUnits / xStepUnits) * xStepUnits
        while (xVal <= maxXUnits) {
            if (abs(xVal) > 1e-9) {
                val px = (centerX + xVal * scaleX).toFloat()
                canvas.drawLine(px, centerY - 10, px, centerY + 10, axisPaint)
                canvas.drawText(String.format("%.1f", xVal).trimEnd('0').trimEnd('.'), px - 15, centerY + 35, textPaint)
            }
            xVal += xStepUnits
        }

        val minYUnits = (centerY - height) / scaleY
        val maxYUnits = centerY / scaleY

        var yVal = ceil(minYUnits / yStepUnits) * yStepUnits
        while (yVal <= maxYUnits) {
            if (abs(yVal) > 1e-9) {
                val py = (centerY - yVal * scaleY).toFloat()
                canvas.drawLine(centerX - 10, py, centerX + 10, py, axisPaint)
                val yLabel = String.format("%.1f", yVal).trimEnd('0').trimEnd('.')
                canvas.drawText(yLabel, centerX + 15, py + 10, textPaint)
            }
            yVal += yStepUnits
        }

        if (geometricMode) {
            canvas.drawText("X Coordinate", width / 2f, height - 30f, labelPaint)
            canvas.save()
            canvas.rotate(-90f, 50f, height / 2f)
            canvas.drawText("Y Coordinate", 50f, height / 2f, labelPaint)
            canvas.restore()
        } else if (milestoneMode) {
            canvas.drawText("Angle (x) in Degrees", width / 2f, height - 30f, labelPaint)
            canvas.save()
            canvas.rotate(-90f, 50f, height / 2f)
            canvas.drawText("Value (y)", 50f, height / 2f, labelPaint)
            canvas.restore()
        } else if (!parabolaMode) {
            canvas.drawText("X", width - 30f, centerY - 15f, labelPaint)
            canvas.drawText("Y", centerX + 25f, 30f, labelPaint)
        }
    }

    private fun drawGeometricShape(canvas: Canvas, centerX: Float, centerY: Float) {
        val radius = shapeSize / 2f
        val pxRadius = radius * scaleX

        // Offset center if margin is present (reference from corner 0,0)
        val drawCenterX = if (isHoleMode) centerX + (radius + holeMargin) * scaleX else centerX
        val drawCenterY = if (isHoleMode) centerY - (radius + holeMargin) * scaleY else centerY

        var area = 0.0
        var perimeter = 0.0

        when (shapeType) {
            "perimeter_rect" -> {
                val wPx = shapeWidth * scaleX
                val hPx = shapeHeight * scaleY

                // Draw rectangle from origin (centerX, centerY)
                val left = centerX
                val top = centerY - hPx
                val right = centerX + wPx
                val bottom = centerY

                canvas.drawRect(left, top, right, bottom, linePaint)

                // Draw dots at corners
                canvas.drawCircle(left, bottom, 12f, pointPaint)
                canvas.drawCircle(right, bottom, 12f, pointPaint)
                canvas.drawCircle(right, top, 12f, pointPaint)
                canvas.drawCircle(left, top, 12f, pointPaint)

                // Draw labels "Width = W" and "Height = H"
                val labelPaintP = Paint(textPaint).apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
                canvas.drawText("Width = ${String.format("%.1f", shapeWidth).trimEnd('0').trimEnd('.')}", centerX + wPx/2, centerY + 50f, labelPaintP)

                val vLabelPaint = Paint(labelPaintP).apply { textAlign = Paint.Align.LEFT }
                canvas.drawText("Height = ${String.format("%.1f", shapeHeight).trimEnd('0').trimEnd('.')}", right + 20f, centerY - hPx/2, vLabelPaint)

                area = (shapeWidth * shapeHeight).toDouble()
                perimeter = 2.0 * (shapeWidth + shapeHeight)
            }
            "perimeter_tri" -> {
                val a = shapeSideA.toDouble()
                val b = shapeSideB.toDouble()
                val c = shapeHeight.toDouble() // base side

                // Law of Cosines: a^2 = b^2 + c^2 - 2bc cos(A)
                val cosA = (b * b + c * c - a * a) / (2 * b * c)
                val x = b * cosA
                val y = sqrt(max(0.0, b * b - x * x))

                val cPx = c.toFloat() * scaleX
                val xPx = x.toFloat() * scaleX
                val yPx = y.toFloat() * scaleY

                val path = Path()
                path.moveTo(centerX, centerY)
                path.lineTo(centerX + cPx, centerY)
                path.lineTo(centerX + xPx, centerY - yPx)
                path.close()
                canvas.drawPath(path, linePaint)

                // Vertices
                canvas.drawCircle(centerX, centerY, 12f, pointPaint)
                canvas.drawCircle(centerX + cPx, centerY, 12f, pointPaint)
                canvas.drawCircle(centerX + xPx, centerY - yPx, 12f, pointPaint)

                // Labels
                // Labels with a, b, c and digits
                val labelPaintTri = Paint(textPaint).apply { color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
                canvas.drawText("c = ${String.format("%.1f", c)}", centerX + cPx / 2, centerY + 40f, labelPaintTri)
                canvas.drawText("b = ${String.format("%.1f", b)}", centerX + xPx / 2 - 40f, centerY - yPx / 2, labelPaintTri)
                canvas.drawText("a = ${String.format("%.1f", a)}", centerX + (xPx + cPx) / 2 + 40f, centerY - yPx / 2, labelPaintTri)

                val s = (a + b + c) / 2.0
                area = sqrt(max(0.0, s * (s - a) * (s - b) * (s - c)))
                perimeter = a + b + c
            }
            "cone_cross_section" -> {
                val r = shapeSideA
                val h = shapeHeight
                val rPx = r * scaleX
                val hPx = h * scaleY

                val path = Path()
                path.moveTo(centerX - rPx, centerY)
                path.lineTo(centerX + rPx, centerY)
                path.lineTo(centerX, centerY - hPx)
                path.close()
                canvas.drawPath(path, linePaint)

                canvas.drawCircle(centerX - rPx, centerY, 12f, pointPaint)
                canvas.drawCircle(centerX + rPx, centerY, 12f, pointPaint)
                canvas.drawCircle(centerX, centerY - hPx, 12f, pointPaint)

                val labelPaint3D = Paint(textPaint).apply { color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
                canvas.drawText("r = ${String.format("%.1f", r)}", centerX + rPx / 2, centerY + 40f, labelPaint3D)
                canvas.drawText("h = ${String.format("%.1f", h)}", centerX + 60f, centerY - hPx / 2, labelPaint3D.apply { textAlign = Paint.Align.LEFT })

                area = (1.0 / 3.0) * PI * r * r * h // Volume
                perimeter = 0.0
            }
            "cylinder_cross_section" -> {
                val r = shapeSideA
                val h = shapeHeight
                val rPx = r * scaleX
                val hPx = h * scaleY
                val left = centerX - rPx
                val top = centerY - hPx
                val right = centerX + rPx
                val bottom = centerY

                canvas.drawRect(left, top, right, bottom, linePaint)
                canvas.drawCircle(left, bottom, 12f, pointPaint)
                canvas.drawCircle(right, bottom, 12f, pointPaint)
                canvas.drawCircle(right, top, 12f, pointPaint)
                canvas.drawCircle(left, top, 12f, pointPaint)

                val labelPaint3D = Paint(textPaint).apply { color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
                canvas.drawText("r = ${String.format("%.1f", r)}", centerX, centerY + 40f, labelPaint3D)
                canvas.drawText("h = ${String.format("%.1f", h)}", right + 20f, centerY - hPx / 2, labelPaint3D.apply { textAlign = Paint.Align.LEFT })

                area = PI * r * r * h // Volume
                perimeter = 0.0
            }
            "sphere_cross_section" -> {
                val r = shapeSideA
                val rPx = r * scaleX
                canvas.drawCircle(centerX, centerY, rPx, linePaint)
                canvas.drawCircle(centerX, centerY, 12f, pointPaint)

                val labelPaint3D = Paint(textPaint).apply { color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
                canvas.drawLine(centerX, centerY, centerX + rPx, centerY, annotationLinePaint)
                canvas.drawText("r = ${String.format("%.1f", r)}", centerX + rPx / 2, centerY - 15f, labelPaint3D)

                area = (4.0 / 3.0) * PI * r * r * r // Volume
                perimeter = 0.0
            }
            "rectangle", "square" -> {
                val wPx = shapeWidth * scaleX
                val hPx = shapeHeight * scaleY
                val left = drawCenterX - wPx / 2f
                val top = drawCenterY - hPx / 2f
                canvas.drawRect(left, top, left + wPx, top + hPx, linePaint)
                area = (shapeWidth * shapeHeight).toDouble()
                perimeter = 2.0 * (shapeWidth + shapeHeight)
            }
            "triangle" -> {
                val bPx = shapeWidth * scaleX
                val hPx = shapeHeight * scaleY

                // Draw half-rectangle (dashed) to show proof
                val dashPaint = Paint(linePaint).apply {
                    pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                    color = Color.GRAY
                    strokeWidth = 3f
                }
                canvas.drawRect(drawCenterX - bPx / 2f, drawCenterY - hPx / 2f, drawCenterX + bPx / 2f, drawCenterY + hPx / 2f, dashPaint)

                val path = Path()
                path.moveTo(drawCenterX - bPx / 2f, drawCenterY + hPx / 2f)
                path.lineTo(drawCenterX + bPx / 2f, drawCenterY + hPx / 2f)
                path.lineTo(drawCenterX - bPx / 2f, drawCenterY - hPx / 2f) // Right-angled style for clearer proof
                path.close()
                canvas.drawPath(path, linePaint)

                // Area and side labels
                val textPaint = Paint().apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER }
                canvas.drawText("b = ${String.format("%.1f", shapeWidth)}", drawCenterX, drawCenterY + hPx / 2f + 40f, textPaint)
                canvas.drawText("h = ${String.format("%.1f", shapeHeight)}", drawCenterX - bPx / 2f - 40f, drawCenterY, textPaint)
                canvas.drawText("Area = 1/2 * b * h", drawCenterX, drawCenterY + hPx / 2f + 75f, textPaint)
                canvas.drawText("(Half of Rectangle)", drawCenterX, drawCenterY + hPx / 2f + 110f, textPaint)

                area = 0.5 * shapeWidth * shapeHeight
                val hyp = sqrt(shapeWidth.toDouble().pow(2) + shapeHeight.toDouble().pow(2))
                perimeter = shapeWidth + shapeHeight + hyp
            }
            "trapezoid" -> {
                val aPx = shapeSideA * scaleX
                val bPx = shapeSideB * scaleX
                val hPx = shapeHeight * scaleY
                val path = Path()
                path.moveTo(drawCenterX - bPx / 2f, drawCenterY + hPx / 2f)
                path.lineTo(drawCenterX + bPx / 2f, drawCenterY + hPx / 2f)
                path.lineTo(drawCenterX + aPx / 2f, drawCenterY - hPx / 2f)
                path.lineTo(drawCenterX - aPx / 2f, drawCenterY - hPx / 2f)
                path.close()
                canvas.drawPath(path, linePaint)
                area = 0.5 * (shapeSideA + shapeSideB) * shapeHeight
                val slant = sqrt(((shapeSideB - shapeSideA) / 2.0).pow(2.0) + shapeHeight.toDouble().pow(2.0))
                perimeter = shapeSideA + shapeSideB + 2.0 * slant
            }
            "circle" -> {
                canvas.drawCircle(drawCenterX, drawCenterY, pxRadius, linePaint)
                area = PI * radius * radius
                perimeter = 2.0 * PI * radius
            }
            else -> {
                if (isHoleMode) {
                    canvas.drawCircle(drawCenterX, drawCenterY, pxRadius, circlePaint)
                    area = PI * radius * radius
                    perimeter = 2.0 * PI * radius
                }

                val path = Path()
                val n = polygonSides
                val vertices = mutableListOf<PointF>()
                for (i in 0 until n) {
                    val angle = 2 * PI * i / n
                    val vx = drawCenterX + pxRadius * cos(angle).toFloat()
                    val vy = drawCenterY - pxRadius * sin(angle).toFloat()
                    if (i == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
                    vertices.add(PointF(vx, vy))
                }
                path.close()
                canvas.drawPath(path, if (isHoleMode) circlePaint else linePaint)

                if (!isHoleMode) {
                    area = 0.5 * n * radius * radius * sin(2 * PI / n)
                    perimeter = n * 2.0 * radius * sin(PI / n)
                }

                val holePaint = Paint(pointPaint).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
                for (i in vertices.indices) {
                    val v = vertices[i]
                    if (isHoleMode) {
                        canvas.drawCircle(v.x, v.y, 10f, pointPaint)
                        canvas.drawCircle(v.x, v.y, 20f, holePaint)
                    } else {
                        canvas.drawCircle(v.x, v.y, 10f, pointPaint)
                    }
                }
            }
        }

        // Draw HUD with area and perimeter
        val hudPaint = Paint().apply { color = Color.parseColor("#E6000000"); style = Paint.Style.FILL }
        val hudTextPaint = Paint().apply { color = Color.CYAN; textSize = 28f; isAntiAlias = true; typeface = Typeface.MONOSPACE }

        var hudHeight = 200f
        if (shapeType == "circle" || isHoleMode || shapeType.endsWith("cross_section")) hudHeight = 280f

        val hudRect = RectF(20f, 20f, width - 20f, hudHeight)
        canvas.drawRoundRect(hudRect, 10f, 10f, hudPaint)

        if (shapeType == "perimeter_rect") {
            val titlePaint = Paint(hudTextPaint).apply { color = Color.WHITE; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
            val wStr = String.format("%.1f", shapeWidth).trimEnd('0').trimEnd('.')
            val hStr = String.format("%.1f", shapeHeight).trimEnd('0').trimEnd('.')
            val pStr = String.format("%.1f", perimeter).trimEnd('0').trimEnd('.')
            canvas.drawText("Perimeter Visualization: 2 * ($wStr + $hStr) = $pStr units", 60f, 90f, titlePaint)
            val subtitlePaint = Paint(hudTextPaint).apply { color = Color.LTGRAY; textSize = 24f }
            canvas.drawText("SHAPE: RECTANGLE / SQUARE", 60f, 135f, subtitlePaint)
            canvas.drawText("AREA: ${String.format("%.2f", area)} units²", 60f, 180f, subtitlePaint)
        } else if (shapeType == "perimeter_tri") {
            val titlePaint = Paint(hudTextPaint).apply { color = Color.WHITE; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
            val aStr = String.format("%.1f", shapeSideA).trimEnd('0').trimEnd('.')
            val bStr = String.format("%.1f", shapeSideB).trimEnd('0').trimEnd('.')
            val cStr = String.format("%.1f", shapeHeight).trimEnd('0').trimEnd('.')
            val pStr = String.format("%.1f", perimeter).trimEnd('0').trimEnd('.')
            canvas.drawText("Perimeter Visualization: $aStr + $bStr + $cStr = $pStr units", 60f, 90f, titlePaint)
            val subtitlePaint = Paint(hudTextPaint).apply { color = Color.LTGRAY; textSize = 24f }
            canvas.drawText("SHAPE: SCALENE TRIANGLE", 60f, 135f, subtitlePaint)
            canvas.drawText("AREA: ${String.format("%.2f", area)} units²", 60f, 180f, subtitlePaint)
        } else if (shapeType == "circle" || isHoleMode) {
            val isCircum = equation?.lowercase()?.contains("circum") == true
            val titlePaint = Paint(hudTextPaint).apply { color = Color.WHITE; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
            val rStr = String.format("%.2f", radius).trimEnd('0').trimEnd('.')
            val pStr = String.format("%.2f", perimeter).trimEnd('0').trimEnd('.')

            if (isHoleMode) {
                canvas.drawText("Bolt Hole Circle Layout", 60f, 90f, titlePaint)
                val subtitlePaint = Paint(hudTextPaint).apply { color = Color.LTGRAY; textSize = 24f }
                canvas.drawText("TOTAL HOLES: $polygonSides", 60f, 135f, subtitlePaint)
                canvas.drawText("BOLT CIRCLE DIAMETER: ${String.format("%.2f", radius * 2)} units", 60f, 180f, subtitlePaint)
                val n = polygonSides
                val chord = if (n > 0) 2.0 * radius * sin(PI / n) else 0.0
                canvas.drawText("HOLE SPACING (CHORD): ${String.format("%.3f", chord)} units", 60f, 225f, subtitlePaint)
            } else {
                if (isCircum) canvas.drawText("Circumference Visualization: 2 * π * $rStr = $pStr units", 60f, 90f, titlePaint)
                else canvas.drawText("Circle Analysis", 60f, 90f, titlePaint)
                val subtitlePaint = Paint(hudTextPaint).apply { color = Color.LTGRAY; textSize = 24f }
                canvas.drawText("SHAPE: CIRCLE", 60f, 135f, subtitlePaint)
                canvas.drawText("AREA: ${String.format("%.3f", area)} units²", 60f, 180f, subtitlePaint)
                canvas.drawText("CIRCUMFERENCE: $pStr units", 60f, 225f, subtitlePaint)
            }
        } else if (shapeType.endsWith("cross_section")) {
            val titlePaint = Paint(hudTextPaint).apply { color = Color.WHITE; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
            val subtitlePaint = Paint(hudTextPaint).apply { color = Color.LTGRAY; textSize = 24f }
            val rStr = String.format("%.1f", shapeSideA).trimEnd('0').trimEnd('.')
            val hStr = String.format("%.1f", shapeHeight).trimEnd('0').trimEnd('.')
            val vStr = String.format("%.2f", area).trimEnd('0').trimEnd('.')
            val title = when (shapeType) {
                "cone_cross_section" -> "Cone Volume Visualization"
                "cylinder_cross_section" -> "Cylinder Volume Visualization"
                else -> "Sphere Volume Visualization"
            }
            canvas.drawText(title, 60f, 90f, titlePaint)
            val shapeName = when (shapeType) {
                "cone_cross_section" -> "CONE (CROSS-SECTION)"
                "cylinder_cross_section" -> "CYLINDER (CROSS-SECTION)"
                else -> "SPHERE (CROSS-SECTION)"
            }
            canvas.drawText("SHAPE: $shapeName", 60f, 135f, subtitlePaint)
            canvas.drawText("RADIUS: $rStr units", 60f, 180f, subtitlePaint)
            if (shapeType != "sphere_cross_section") canvas.drawText("HEIGHT: $hStr units", 300f, 180f, subtitlePaint)
            canvas.drawText("VOLUME: $vStr units³", 60f, 225f, subtitlePaint.apply { color = Color.CYAN })
        } else {
            canvas.drawText("SHAPE: ${shapeType.uppercase()}", 60f, 90f, hudTextPaint)
            canvas.drawText("AREA: ${String.format("%.3f", area)} m²", 60f, 135f, hudTextPaint)
            canvas.drawText("PERI: ${String.format("%.3f", perimeter)} m", 60f, 180f, hudTextPaint)
        }

        if (shapeType == "circle" || isHoleMode) {
            canvas.drawLine(drawCenterX, drawCenterY, drawCenterX + pxRadius, drawCenterY, annotationLinePaint)
            val label = if (isHoleMode) "R = ${String.format("%.2f", radius)} (BCD/2)" else "R = ${String.format("%.2f", radius)}"
            canvas.drawText(label, drawCenterX + pxRadius / 2, drawCenterY - 15, annotationPaint)
        }
    }

    private fun calculateQuarticTurningPoints() {
        quarticTurningPoints.clear()
        val allRoots = CalculatorHelper.solveCubic(4.0 * quarticA, 3.0 * quarticB, 2.0 * quarticC, quarticD)
        for (r in allRoots) {
            if (abs(r.imag) < 1e-9) {
                val x = r.real
                val y = quarticA * x.pow(4.0) + quarticB * x.pow(3.0) + quarticC * x.pow(2.0) + quarticD * x + quarticE
                quarticTurningPoints.add(PointF(x.toFloat(), y.toFloat()))
            }
        }
    }

    private fun drawLegend(canvas: Canvas) {
        val legendX = width - 250f
        val legendY = 150f
        canvas.drawCircle(legendX, legendY, 12f, pointPaint)
        canvas.drawText("Key Point", legendX + 30f, legendY + 10f, pointLabelPaint)
    }

    private fun drawGraph(canvas: Canvas, eq: String, centerX: Float, centerY: Float) {
        val path = Path()
        var first = true
        val startX = (-centerX / scaleX).toDouble()
        val endX = ((width - centerX) / scaleX).toDouble()
        val maxPoints = 1500
        val step = ((endX - startX) / maxPoints).coerceAtLeast(0.001)

        var currentX = startX
        while (currentX <= endX) {
            val y = CalculatorHelper.evaluatePreprocessed(eq, useRadians, currentX)
            if (y != null && !y.isNaN() && !y.isInfinite()) {
                val px = (centerX + currentX * scaleX).toFloat()
                val py = (centerY - y * scaleY).toFloat()
                if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
            } else first = true
            currentX += step
        }
        canvas.drawPath(path, linePaint)

        val lowerEqRaw = equation?.lowercase()?.replace(" ", "") ?: ""
        val isGeoFunc = lowerEqRaw.startsWith("area") || lowerEqRaw.startsWith("volume") || lowerEqRaw.startsWith("surfacearea")
        val isTrigFunc = lowerEqRaw.startsWith("sin") || lowerEqRaw.startsWith("cos") || lowerEqRaw.startsWith("tan") || lowerEqRaw.contains("angle")

        if (isGeoFunc || isTrigFunc) {
            val hPaint = Paint().apply { color = Color.parseColor("#E6000000"); style = Paint.Style.FILL }
            val hTextPaint = Paint().apply { color = Color.CYAN; textSize = 28f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
            val titleP = Paint(hTextPaint).apply { color = Color.WHITE; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
            canvas.drawRoundRect(20f, 20f, width - 20f, 180f, 10f, 10f, hPaint)
            val title = when {
                lowerEqRaw.startsWith("surfacearea") -> "Surface Area Analysis"
                lowerEqRaw.startsWith("volume") -> "Volume Analysis"
                isTrigFunc -> "Trigonometry & Angle Analysis"
                else -> "Area Analysis"
            }
            canvas.drawText(title, 60f, 80f, titleP)
            canvas.drawText("FUNCTION: f(x) = ${equation ?: ""}", 60f, 130f, hTextPaint)

            if (isTrigFunc) {
                canvas.drawText("Angle / Input (x)", width / 2f, height - 30f, labelPaint)
            } else {
                canvas.drawText("Dimension / Side Length (x)", width / 2f, height - 30f, labelPaint)
            }
            canvas.save(); canvas.rotate(-90f, 50f, height / 2f); canvas.drawText("Calculated Result (y)", 50f, height / 2f, labelPaint); canvas.restore()
        }

        if (milestoneMode) {
            val px = (centerX + milestoneX * scaleX).toFloat()
            val py = (centerY - milestoneY * scaleY).toFloat()
            canvas.drawCircle(px, py, 12f, pointPaint)
            canvas.drawText(milestoneLabel, px + 15f, py - 25f, pointLabelPaint)
            val title = "Graph with Milestone Point"
            canvas.drawText(title, width/2f, 60f, labelPaint)
        }

        val intStart = ceil(startX).toInt()
        val intEnd = floor(endX).toInt()
        if (milestoneMode || scaleX < 150f || intEnd - intStart > 50) return

        var lastLabelX = -1000f
        for (ix in intStart..intEnd) {
            val y = CalculatorHelper.evaluatePreprocessed(eq, useRadians, ix.toDouble())
            if (y != null && !y.isNaN() && !y.isInfinite()) {
                val px = (centerX + ix * scaleX).toFloat()
                val py = (centerY - y * scaleY).toFloat()
                if (px - lastLabelX > 100f) {
                    canvas.drawCircle(px, py, 8f, pointPaint)
                    canvas.drawText("($ix, ${String.format("%.1f", y).trimEnd('0').trimEnd('.')})", px + 12f, py - 12f, pointLabelPaint)
                    lastLabelX = px
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val centerX = width / 2f + offsetX
        val centerY = height / 2f + offsetY
        scaleGestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (isTraceEnabled && event.pointerCount == 1) {
                    updateTrace(((event.x - centerX) / scaleX).toDouble())
                } else if (event.action == MotionEvent.ACTION_MOVE) {
                    if (mode3D && event.pointerCount == 1) {
                        rotationY += (event.x - lastTouchX) * 0.5f
                        rotationX -= (event.y - lastTouchY) * 0.5f
                    } else {
                        offsetX += event.x - lastTouchX
                        offsetY += event.y - lastTouchY
                    }
                }
                lastTouchX = event.x; lastTouchY = event.y; invalidate()
            }
        }
        return true
    }

    private fun draw3DGeometric(canvas: Canvas, centerX: Float, centerY: Float) {
        val paint = Paint(linePaint).apply {
            color = Color.parseColor("#1A237E") // Deep Indigo for visibility on white
            strokeWidth = 5f
        }
        val hiddenPaint = Paint(paint).apply {
            color = Color.GRAY
            alpha = 100
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

        when (shapeType) {
            "cube", "prism" -> {
                val w = shapeWidth / 2f
                val h = shapeHeight / 2f
                val d = shapeSize / 2f

                val vertices = listOf(
                    Point3D(-w, -h, -d), Point3D(w, -h, -d), Point3D(w, h, -d), Point3D(-w, h, -d),
                    Point3D(-w, -h, d), Point3D(w, -h, d), Point3D(w, h, d), Point3D(-w, h, d)
                )
                val proj = vertices.map { project(it).apply { offset(centerX, centerY) } }

                val edges = listOf(
                    0 to 1, 1 to 2, 2 to 3, 3 to 0,
                    4 to 5, 5 to 6, 6 to 7, 7 to 4,
                    0 to 4, 1 to 5, 2 to 6, 3 to 7
                )
                edges.forEach { (s, e) -> canvas.drawLine(proj[s].x, proj[s].y, proj[e].x, proj[e].y, paint) }

                val labelPaint = Paint().apply { color = Color.BLACK; textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
                canvas.drawText("w=${shapeWidth}", proj[5].x - 40f, proj[5].y + 40f, labelPaint)
                canvas.drawText("h=${shapeHeight}", proj[5].x + 20f, proj[5].y - 20f, labelPaint)
                canvas.drawText("d=${shapeSize}", proj[1].x + 20f, proj[1].y, labelPaint)
            }
            "pyramid" -> {
                val w = shapeWidth / 2f
                val h = shapeHeight / 2f
                val d = shapeSize / 2f
                val base = listOf(Point3D(-w, h, -d), Point3D(w, h, -d), Point3D(w, h, d), Point3D(-w, h, d))
                val apex = Point3D(0f, -h, 0f)
                val projBase = base.map { project(it).apply { offset(centerX, centerY) } }
                val projApex = project(apex).apply { offset(centerX, centerY) }
                for (i in 0..3) {
                    val next = (i + 1) % 4
                    canvas.drawLine(projBase[i].x, projBase[i].y, projBase[next].x, projBase[next].y, paint)
                    canvas.drawLine(projBase[i].x, projBase[i].y, projApex.x, projApex.y, paint)
                }
            }
            "cylinder3d", "cone3d" -> {
                val r = shapeWidth / 2f
                val h = shapeHeight / 2f
                val segments = 24
                val bottomCircle = (0 until segments).map { i ->
                    val angle = 2 * PI * i / segments
                    Point3D(r * cos(angle).toFloat(), h, r * sin(angle).toFloat())
                }.map { project(it).apply { offset(centerX, centerY) } }

                val topCircle = if (shapeType == "cylinder3d") {
                    (0 until segments).map { i ->
                        val angle = 2 * PI * i / segments
                        Point3D(r * cos(angle).toFloat(), -h, r * sin(angle).toFloat())
                    }.map { project(it).apply { offset(centerX, centerY) } }
                } else {
                    val apex = project(Point3D(0f, -h, 0f)).apply { offset(centerX, centerY) }
                    List(segments) { apex }
                }

                for (i in 0 until segments) {
                    val next = (i + 1) % segments
                    canvas.drawLine(bottomCircle[i].x, bottomCircle[i].y, bottomCircle[next].x, bottomCircle[next].y, paint)
                    canvas.drawLine(topCircle[i].x, topCircle[i].y, topCircle[next].x, topCircle[next].y, paint)
                    if (i % 6 == 0) canvas.drawLine(bottomCircle[i].x, bottomCircle[i].y, topCircle[i].x, topCircle[i].y, paint)
                }
            }
            "sphere" -> {
                val r = shapeWidth / 2f
                val segments = 16
                for (j in 0 until segments) {
                    val lat1 = PI * (j - segments / 2) / segments
                    val lat2 = PI * (j + 1 - segments / 2) / segments
                    for (i in 0 until segments) {
                        val lon = 2 * PI * i / segments
                        val p1 = project(Point3D(r * cos(lat1).toFloat() * cos(lon).toFloat(), r * sin(lat1).toFloat(), r * cos(lat1).toFloat() * sin(lon).toFloat())).apply { offset(centerX, centerY) }
                        val p2 = project(Point3D(r * cos(lat2).toFloat() * cos(lon).toFloat(), r * sin(lat2).toFloat(), r * cos(lat2).toFloat() * sin(lon).toFloat())).apply { offset(centerX, centerY) }
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                    }
                }
                // Draw horizontal rings
                for (j in 1 until segments) {
                    val lat = PI * (j - segments / 2) / segments
                    for (i in 0 until segments) {
                        val lon1 = 2 * PI * i / segments
                        val lon2 = 2 * PI * (i + 1) / segments
                        val p1 = project(Point3D(r * cos(lat).toFloat() * cos(lon1).toFloat(), r * sin(lat).toFloat(), r * cos(lat).toFloat() * sin(lon1).toFloat())).apply { offset(centerX, centerY) }
                        val p2 = project(Point3D(r * cos(lat).toFloat() * cos(lon2).toFloat(), r * sin(lat).toFloat(), r * cos(lat).toFloat() * sin(lon2).toFloat())).apply { offset(centerX, centerY) }
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                    }
                }
            }
            "triangle", "perimeter_tri" -> {
                val a = shapeSideA.toDouble()
                val b = shapeSideB.toDouble()
                val c = shapeHeight.toDouble() // base side

                val drawC = if (c == 0.0) shapeWidth.toDouble() else c
                val drawB = if (b == 0.0) sqrt((shapeWidth/2.0).pow(2) + shapeHeight.toDouble().pow(2)) else b
                val drawA = if (a == 0.0) drawB else a

                val cosAngle = (drawB * drawB + drawC * drawC - drawA * drawA) / (2 * drawB * drawC)
                val vx = (drawB * cosAngle).toFloat()
                val vy = sqrt(max(0.0, drawB * drawB - vx * vx)).toFloat()

                // Draw a 3D Prism for the triangle to make it visible
                val depth = 1f // Small extrusion for visibility
                val base = listOf(Point3D(0f, 0f, -depth), Point3D(drawC.toFloat(), 0f, -depth), Point3D(vx, -vy, -depth))
                val top = listOf(Point3D(0f, 0f, depth), Point3D(drawC.toFloat(), 0f, depth), Point3D(vx, -vy, depth))

                val projBase = base.map { project(it).apply { offset(centerX, centerY) } }
                val projTop = top.map { project(it).apply { offset(centerX, centerY) } }

                for (i in 0..2) {
                    val next = (i + 1) % 3
                    canvas.drawLine(projBase[i].x, projBase[i].y, projBase[next].x, projBase[next].y, paint)
                    canvas.drawLine(projTop[i].x, projTop[i].y, projTop[next].x, projTop[next].y, paint)
                    canvas.drawLine(projBase[i].x, projBase[i].y, projTop[i].x, projTop[i].y, paint)
                }

                val labelPaint = Paint().apply { color = Color.BLACK; textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
                if (shapeType == "perimeter_tri") {
                    canvas.drawText("a=${String.format("%.1f", drawA)}", (projTop[1].x + projTop[2].x)/2 + 20, (projTop[1].y + projTop[2].y)/2, labelPaint)
                    canvas.drawText("b=${String.format("%.1f", drawB)}", (projTop[0].x + projTop[2].x)/2 - 40, (projTop[0].y + projTop[2].y)/2, labelPaint)
                    canvas.drawText("c=${String.format("%.1f", drawC)}", (projTop[0].x + projTop[1].x)/2, projTop[0].y + 40, labelPaint)
                }
            }
            "rectangle", "square", "perimeter_rect" -> {
                val w = shapeWidth / 2f
                val h = shapeHeight / 2f
                val d = 1f // small depth
                val vertices = listOf(
                    Point3D(-w, -h, -d), Point3D(w, -h, -d), Point3D(w, h, -d), Point3D(-w, h, -d),
                    Point3D(-w, -h, d), Point3D(w, -h, d), Point3D(w, h, d), Point3D(-w, h, d)
                )
                val proj = vertices.map { project(it).apply { offset(centerX, centerY) } }
                val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
                edges.forEach { (s, e) -> canvas.drawLine(proj[s].x, proj[s].y, proj[e].x, proj[e].y, paint) }
            }
        }

        val rotateTextPaint = Paint().apply { color = Color.parseColor("#BF360C"); textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("Drag to rotate 3D view", width/2f, height - 80f, rotateTextPaint)
    }

    private fun draw3DAxes(canvas: Canvas, centerX: Float, centerY: Float) {
        val size = 5f
        val xEnd = project(Point3D(size, 0f, 0f)).apply { offset(centerX, centerY) }
        val yEnd = project(Point3D(0f, -size, 0f)).apply { offset(centerX, centerY) }
        val zEnd = project(Point3D(0f, 0f, size)).apply { offset(centerX, centerY) }
        val origin = project(Point3D(0f, 0f, 0f)).apply { offset(centerX, centerY) }

        val paint = Paint().apply { strokeWidth = 3f; style = Paint.Style.STROKE }

        paint.color = Color.RED
        canvas.drawLine(origin.x, origin.y, xEnd.x, xEnd.y, paint)
        canvas.drawText("X", xEnd.x + 10, xEnd.y, paint)

        paint.color = Color.GREEN
        canvas.drawLine(origin.x, origin.y, yEnd.x, yEnd.y, paint)
        canvas.drawText("Y", yEnd.x, yEnd.y - 10, paint)

        paint.color = Color.BLUE
        canvas.drawLine(origin.x, origin.y, zEnd.x, zEnd.y, paint)
        canvas.drawText("Z", zEnd.x + 10, zEnd.y + 10, paint)
    }

    private fun draw3DSurface(canvas: Canvas, centerX: Float, centerY: Float) {
        val eq = preprocessedEquation ?: return
        val range = 8.0
        val steps = 25
        val stepSize = (2 * range) / steps

        val paint = Paint(linePaint).apply { strokeWidth = 2f; alpha = 200 }

        val grid = Array(steps + 1) { Array(steps + 1) { Point3D(0f, 0f, 0f) } }

        for (i in 0..steps) {
            val x = -range + i * stepSize
            for (j in 0..steps) {
                val y = -range + j * stepSize
                val z = CalculatorHelper.evaluatePreprocessed(eq, useRadians, x, y) ?: 0.0
                // Map z=f(x,y) to Point3D(x, f(x,y), y). Invert f(x,y) because y axis is down
                grid[i][j] = Point3D(x.toFloat(), (-z).toFloat(), y.toFloat())
            }
        }

        for (i in 0..steps) {
            for (j in 0 until steps) {
                val p1 = project(grid[i][j]).apply { offset(centerX, centerY) }
                val p2 = project(grid[i][j+1]).apply { offset(centerX, centerY) }

                val zAvg = -((grid[i][j].y + grid[i][j+1].y) / 2f)
                paint.color = getSurfaceColor(zAvg)
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)

                if (i < steps) {
                    val p3 = project(grid[i][j]).apply { offset(centerX, centerY) }
                    val p4 = project(grid[i+1][j]).apply { offset(centerX, centerY) }
                    val zAvg2 = -((grid[i][j].y + grid[i+1][j].y) / 2f)
                    paint.color = getSurfaceColor(zAvg2)
                    canvas.drawLine(p3.x, p3.y, p4.x, p4.y, paint)
                }
            }
        }

        val rotateTextPaint = Paint().apply { color = Color.YELLOW; textSize = 24f; textAlign = Paint.Align.CENTER }
        canvas.drawText("Drag to rotate Surface Plot", width/2f, height - 80f, rotateTextPaint)
    }

    private fun getSurfaceColor(z: Float): Int {
        val norm = (z + 10f) / 20f
        val hue = (240f - norm.coerceIn(0f, 1f) * 240f)
        return Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))
    }

    private fun updateTrace(tx: Double) {
        var snappedX = tx
        // Snap to Y-axis if close
        if (abs(tx) < 0.2) snappedX = 0.0

        val ty = if (parabolaMode) parabolaA * snappedX * snappedX + parabolaB * snappedX + parabolaC
        else if (cubicMode) cubicA * snappedX.pow(3.0) + cubicB * snappedX.pow(2.0) + cubicC * snappedX + cubicD
        else if (quarticMode) quarticA * snappedX.pow(4.0) + quarticB * snappedX.pow(3.0) + quarticC * snappedX.pow(2.0) + quarticD * snappedX + quarticE
        else if (quinticMode) quinticA * snappedX.pow(5.0) + quinticB * snappedX.pow(4.0) + quinticC * snappedX.pow(3.0) + quinticD * snappedX.pow(2.0) + quinticE * snappedX + quinticF
        else preprocessedEquation?.let { CalculatorHelper.evaluatePreprocessed(it, useRadians, snappedX) } ?: 0.0

        // Snap to X-axis if close
        if (abs(ty) < 0.2) {
            traceX = snappedX
            traceY = 0.0
        } else {
            traceX = snappedX
            traceY = ty
        }
    }

    private fun drawProjectileMotion(canvas: Canvas, centerX: Float, centerY: Float) {
        val g = 9.8; val c = 0.2; val m = 1.0; val angleRad = Math.toRadians(projectileAngle)
        val path = Path()
        var first = true
        for (i in 0..200) {
            val t = i * 0.05
            val x: Double; val y: Double
            if (isAirResistanceEnabled) {
                x = (m * (projectileV0 * cos(angleRad)) / c) * (1 - exp(-c * t / m))
                y = (m / c) * (projectileV0 * sin(angleRad) + m * g / c) * (1 - exp(-c * t / m)) - (m * g / c) * t
            } else {
                x = projectileV0 * cos(angleRad) * t
                y = projectileV0 * sin(angleRad) * t - 0.5 * g * t * t
            }
            if (y < -5.0) break
            val px = centerX + x.toFloat() * scaleX; val py = centerY - y.toFloat() * scaleY
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
        }
        canvas.drawPath(path, Paint(linePaint).apply { pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) })
        val curX: Double; val curYPos: Double
        val t = projectileTime.toDouble()
        if (isAirResistanceEnabled) {
            curX = (m * (projectileV0 * cos(angleRad)) / c) * (1 - exp(-c * t / m))
            curYPos = (m / c) * (projectileV0 * sin(angleRad) + m * g / c) * (1 - exp(-c * t / m)) - (m * g / c) * t
        } else {
            curX = projectileV0 * cos(angleRad) * t
            curYPos = projectileV0 * sin(angleRad) * t - 0.5 * g * t * t
        }
        canvas.drawCircle(centerX + curX.toFloat() * scaleX, centerY - curYPos.toFloat() * scaleY, 15f, pointPaint)
        drawHUD(canvas)
    }

    private fun drawRefraction(canvas: Canvas, centerX: Float, centerY: Float) {
        val p1 = Paint().apply { color = Color.BLUE; strokeWidth = 5f }
        val p2 = Paint().apply { color = Color.RED; strokeWidth = 5f }
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, axisPaint)
        val inAngle = 45.0; val inRad = Math.toRadians(inAngle)
        val startX = centerX - 200f; val startY = centerY - 200f
        canvas.drawLine(startX, startY, centerX, centerY, p1)
        val sinTheta2 = refractionIndexN1 * sin(inRad) / 1.0
        if (sinTheta2 <= 1.0) {
            val outRad = asin(sinTheta2)
            val endX = centerX + 200f * sin(outRad).toFloat(); val endY = centerY + 200f * cos(outRad).toFloat()
            canvas.drawLine(centerX, centerY, endX, endY, p2)
        }
        drawHUD(canvas)
    }

    private fun drawNoiseMap(canvas: Canvas, centerX: Float, centerY: Float) {
        noiseBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        drawHUD(canvas)
    }

    private fun generateNoiseBitmap() {
        val w = 100; val h = 100
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(noiseSeed)
        for (x in 0 until w) for (y in 0 until h) {
            val noise = (random.nextFloat() * 255).toInt()
            bitmap.setPixel(x, y, Color.rgb(noise, noise, noise))
        }
        noiseBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun drawHUD(canvas: Canvas) {
        val hudP = Paint().apply { color = Color.parseColor("#CC000000"); style = Paint.Style.FILL }
        val hudB = Paint().apply { color = Color.CYAN; strokeWidth = 2f; style = Paint.Style.STROKE }
        val textP = Paint().apply { color = Color.WHITE; textSize = 26f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val titleP = Paint().apply { color = Color.CYAN; textSize = 30f; isAntiAlias = true; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val margin = 40f; val padding = 30f; val hudW = 500f; var hudH = 220f
        if (currentScienceMode == ScienceMode.REFRACTION) hudH = 280f
        if (currentScienceMode == ScienceMode.BEZIER) hudH = 320f
        val rect = RectF(margin, margin, margin + hudW, margin + hudH)
        canvas.drawRoundRect(rect, 20f, 20f, hudP); canvas.drawRoundRect(rect, 20f, 20f, hudB)
        var curY = margin + padding + 10f
        when (currentScienceMode) {
            ScienceMode.PROJECTILE -> {
                canvas.drawText("SYSTEM: NEWTON_GRAVITY", margin + padding, curY, titleP); curY += 45f
                canvas.drawText("VELOCITY: ${projectileV0}m/s", margin + padding, curY, textP); curY += 40f
                canvas.drawText("DRAG: ${if (isAirResistanceEnabled) "ENABLED" else "DISABLED"}", margin + padding, curY, Paint(textP).apply { color = if (isAirResistanceEnabled) Color.GREEN else Color.RED })
            }
            ScienceMode.REFRACTION -> {
                canvas.drawText("SYSTEM: SNELLS_OPTICS", margin + padding, curY, titleP); curY += 45f
                canvas.drawText("MEDIA_N1: ${String.format("%.2f", refractionIndexN1)}", margin + padding, curY, textP)
            }
            else -> {}
        }
    }

    private fun drawBezierSimulation(canvas: Canvas, centerX: Float, centerY: Float) {
        val p = Paint(linePaint).apply { strokeWidth = 4f; color = Color.YELLOW }
        for (i in 0 until bezierPoints.size - 1) {
            val p1 = bezierPoints[i]; val p2 = bezierPoints[i+1]
            canvas.drawLine(centerX + p1.x * scaleX, centerY - p1.y * scaleY, centerX + p2.x * scaleX, centerY - p2.y * scaleY, p)
        }
        val path = Path(); var first = true
        for (i in 0..100) {
            val t = i / 100f; val x = (1-t).pow(3)*bezierPoints[0].x + 3*(1-t).pow(2)*t*bezierPoints[1].x + 3*(1-t)*t.pow(2)*bezierPoints[2].x + t.pow(3)*bezierPoints[3].x
            val y = (1-t).pow(3)*bezierPoints[0].y + 3*(1-t).pow(2)*t*bezierPoints[1].y + 3*(1-t)*t.pow(2)*bezierPoints[2].y + t.pow(3)*bezierPoints[3].y
            val px = centerX + x * scaleX; val py = centerY - y * scaleY
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)
        for (pt in bezierPoints) canvas.drawCircle(centerX + pt.x * scaleX, centerY - pt.y * scaleY, 15f, pointPaint)
        drawHUD(canvas)
    }
}
