package com.iqbal.gurmukhikeyboard50

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class NanakshahiCalendarPanel(
    private val context: Context,
    private val onDismiss: () -> Unit
) {
    private var isInitializingSpinners = false
    private var spinnerDay: Spinner? = null; private var spinnerMonth: Spinner? = null; private var spinnerYear: Spinner? = null
    private var yearSpinner: Spinner? = null; private var monthSpinnerTop: Spinner? = null; private var prevMonthButton: ImageButton? = null; private var nextMonthButton: ImageButton? = null; private var locationDisplayText: TextView? = null; private var sunTimesDisplayText: TextView? = null
    private lateinit var calendarAdapter: MonthlyCalendarAdapter
    private var calendarRecycler: RecyclerView? = null
    private var monthlyHighlightsText: TextView? = null; private var calculationResultText: TextView? = null; private var startDateForCalc: Calendar? = null
    private val todayCal: Calendar = Calendar.getInstance()
    private val yearsList = (-10000..100000).toList()
    private var currentLocation: NanakshahiCalendar.LocationConfig = NanakshahiCalendar.LocationConfig.AMRITSAR
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var bottomControls: View? = null

    val view: View

    init {
        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.nanakshahi_calendar_panel, null)
        setupNanakshahiCalendarPanel()
        requestLocationUpdate()
    }

    private fun setupNanakshahiCalendarPanel() {
        spinnerDay = view.findViewById(R.id.spinnerDay); spinnerMonth = view.findViewById(R.id.spinnerMonth); spinnerYear = view.findViewById(R.id.spinnerYear)
        yearSpinner = view.findViewById(R.id.year_spinner); monthSpinnerTop = view.findViewById(R.id.month_spinner_top); prevMonthButton = view.findViewById(R.id.prev_month_button); nextMonthButton = view.findViewById(R.id.next_month_button); locationDisplayText = view.findViewById(R.id.location_display_text); sunTimesDisplayText = view.findViewById(R.id.sun_times_display_text); calendarRecycler = view.findViewById(R.id.calendarRecycler); monthlyHighlightsText = view.findViewById(R.id.monthly_highlights_text); calculationResultText = view.findViewById(R.id.nanakshahi_calendar_output_text)
        bottomControls = view.findViewById(R.id.bottom_controls)

        bottomControls?.visibility = View.GONE

        view.findViewById<ImageButton>(R.id.close_calendar_button)?.setOnClickListener { onDismiss() }
        view.findViewById<Button>(R.id.calculateDateButton)?.setOnClickListener { calculateDiff() }
        view.findViewById<Button>(R.id.findDateButton)?.setOnClickListener { showDateFinderDialog() }
        view.findViewById<Button>(R.id.btn_events)?.setOnClickListener { showEventsDialog() }
        view.findViewById<Button>(R.id.btn_today)?.setOnClickListener { 
            updateUIFromDate(todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH))
            bottomControls?.visibility = View.VISIBLE
        }
        
        view.findViewById<Button>(R.id.btn_hukamnama)?.setOnClickListener { openHukamnamaWebsite() }
        
        prevMonthButton?.setOnClickListener { changeMonth(-1) }
        nextMonthButton?.setOnClickListener { changeMonth(1) }
        locationDisplayText?.setOnClickListener { requestLocationUpdate() }
        
        view.findViewById<Switch>(R.id.mode_toggle_switch)?.apply {
            isChecked = NanakshahiCalendar.currentMode == NanakshahiCalendar.CalculationMode.FIXED
            setOnCheckedChangeListener { _, isChecked -> NanakshahiCalendar.currentMode = if (isChecked) NanakshahiCalendar.CalculationMode.FIXED else NanakshahiCalendar.CalculationMode.ASTRONOMICAL; refreshUI() }
        }

        setupMonthlyCalendarRecycler(); setupNanakshahiSpinners(); setupTopYearSpinner(); setupTopMonthSpinner()
        updateUIFromDate(todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH), showDetails = false)
    }

    private fun openHukamnamaWebsite() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gurbaninow.com/hukamnama"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "ਵੈੱਬਸਾਈਟ ਖੋਲ੍ਹਣ ਵਿੱਚ ਦਿੱਕਤ ਆਈ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeMonth(increment: Int) {
        val currentYear = getSelectedYear(); val currentMonth = (monthSpinnerTop?.selectedItemPosition ?: todayCal.get(Calendar.MONTH)) + 1
        var newYear = currentYear; var newMonth = currentMonth + increment
        if (newMonth > 12) { newMonth = 1; newYear++ } else if (newMonth < 1) { newMonth = 12; newYear-- }
        if (newYear in yearsList[0]..yearsList.last()) {
            updateUIFromDate(newYear, newMonth, (spinnerDay?.selectedItem as? Int) ?: 1, showDetails = false)
            bottomControls?.visibility = View.GONE
        }
    }

    private fun calculateDiff() {
        val year = getSelectedYear(); val month = (monthSpinnerTop?.selectedItemPosition ?: 0) + 1; val day = (spinnerDay?.selectedItem as? Int) ?: 1
        if (startDateForCalc == null) { startDateForCalc = Calendar.getInstance().apply { set(year, month - 1, day) }; calculationResultText?.text = "ਪਹਿਲੀ ਤਾਰੀਖ ਚੁਣੀ ਗਈ। ਹੁਣ ਦੂਜੀ ਚੁਣ ਕੇ ਦੁਬਾਰਾ ਦਬਾਓ।" }
        else { val endCal = Calendar.getInstance().apply { set(year, month - 1, day) }; val diff = NanakshahiCalendar.calculateDateDifference(startDateForCalc!!, endCal); calculationResultText?.text = "ਅੰਤਰ: ${NanakshahiCalendar.toGurmukhiNumber(diff.years)} ਸਾਲ, ${NanakshahiCalendar.toGurmukhiNumber(diff.months)} ਮਹੀਨੇ, ${NanakshahiCalendar.toGurmukhiNumber(diff.days)} ਦਿਨ"; startDateForCalc = null }
    }

    private fun showEventsDialog() {
        try {
            val currentYear = getSelectedYear(); val month = (monthSpinnerTop?.selectedItemPosition ?: todayCal.get(Calendar.MONTH)) + 1; val day = (spinnerDay?.selectedItem as? Int) ?: todayCal.get(Calendar.DAY_OF_MONTH)
            val nsYear = if (month > 3 || (month == 3 && day >= 14)) currentYear - 1468 else currentYear - 1469
            
            val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("ਗੁਰਪੁਰਬ ਸੂਚੀ (ਸੰਮਤ ${NanakshahiCalendar.toGurmukhiYear(nsYear)})")
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
            val progress = ProgressBar(context, null, android.R.attr.progressBarStyleLarge).apply { layoutParams = LinearLayout.LayoutParams(100, 100).apply { gravity = android.view.Gravity.CENTER } }
            val scroll = ScrollView(context); val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            root.addView(progress); root.addView(scroll); scroll.addView(listContainer)
            builder.setView(root).setPositiveButton("ਠੀਕ ਹੈ", null)
            val dialog = builder.create()
            
            dialog.window?.let { window ->
                window.attributes.token = view.windowToken
                window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
            }
            dialog.show()

            uiScope.launch {
                val events = withContext(Dispatchers.Default) { NanakshahiCalendar.getSgpcGurpurabs(context, nsYear) }
                progress.visibility = View.GONE
                if (events.isEmpty()) { listContainer.addView(TextView(context).apply { text = "ਕੋਈ ਡੇਟਾ ਨਹੀਂ ਮਿਲਿਆ।"; setPadding(16, 16, 16, 16) }) }
                else {
                    for (event in events) {
                        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 16); gravity = android.view.Gravity.CENTER_VERTICAL }
                        val info = TextView(context).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); text = "${event.name}\n(${NanakshahiCalendar.toGurmukhiNumber(event.day)} ${event.month})"; textSize = 16f; setTextColor(event.gurpurabColor ?: Color.BLACK) }
                        val btnJump = ImageButton(context).apply { setImageResource(android.R.drawable.ic_menu_today); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { event.gregDate?.let { updateUIFromDate(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH), showDetails = true) }; dialog.dismiss() } }
                        val btnShare = ImageButton(context).apply { setImageResource(android.R.drawable.ic_menu_share); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { shareEvent(event) } }
                        row.addView(info); row.addView(btnJump); row.addView(btnShare); listContainer.addView(row)
                        listContainer.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1); setBackgroundColor(Color.LTGRAY) })
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "ਡਾਇਲਾਗ ਖੋਲ੍ਹਣ ਵਿੱਚ ਦਿੱਕਤ ਆਈ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareEvent(event: NanakshahiCalendar.Gurpurab) {
        val shareText = "🎉 *${event.name}*\n📅 ਤਾਰੀਖ: ${NanakshahiCalendar.toGurmukhiNumber(event.day)} ${event.month}\n📍 ਕੈਲੰਡਰ: ਨਾਨਕਸ਼ਾਹੀ\n\n_ਜੀ ਆਇਆਂ ਨੂੰ - ਗੁਰਮੁਖੀ ਕੀਬੋਰਡ_"
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdate() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION); val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!(lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            }
            LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc -> if (loc != null) updateLocation(loc.latitude, loc.longitude) }
        } else {
            if (context is Activity) ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
            else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    private fun updateLocation(lat: Double, lon: Double) { currentLocation = NanakshahiCalendar.LocationConfig(lat, lon); try { val addresses = Geocoder(context, Locale("pa")).getFromLocation(lat, lon, 1); locationDisplayText?.text = "📍 ਲੋਕੇਸ਼ਨ: ${addresses?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: "ਮੌਜੂਦਾ ਸਥਾਨ"}" } catch (e: Exception) { locationDisplayText?.text = "📍 ਲੋਕੇਸ਼ਨ: ($lat, $lon)" }; refreshUI() }
    private fun getSelectedYear(): Int { val pos = yearSpinner?.selectedItemPosition ?: return todayCal.get(Calendar.YEAR); return if (pos in yearsList.indices) yearsList[pos] else todayCal.get(Calendar.YEAR) }
    private fun refreshUI() { updateUIFromDate(getSelectedYear(), (monthSpinnerTop?.selectedItemPosition ?: 0) + 1, (spinnerDay?.selectedItem as? Int) ?: 1, showDetails = bottomControls?.visibility == View.VISIBLE) }

    private fun setupTopMonthSpinner() {
        val months = listOf("ਜਨਵਰੀ", "ਫਰਵਰੀ", "ਮਾਰਚ", "ਅਪ੍ਰੈਲ", "ਮਈ", "ਜੂਨ", "ਜੁਲਾਈ", "ਅਗਸਤ", "ਸਤੰਬਰ", "ਅਕਤੂਬਰ", "ਨਵੰਬਰ", "ਦਸੰਬਰ")
        monthSpinnerTop?.adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, months) {
            override fun getView(p: Int, c: View?, parent: ViewGroup): View { val v = super.getView(p, c, parent) as TextView; v.textSize = 18f; v.setTypeface(v.typeface, android.graphics.Typeface.BOLD); return v }
            override fun getDropDownView(p: Int, c: View?, parent: ViewGroup): View { val v = super.getDropDownView(p, c, parent) as TextView; v.setPadding(16, 16, 16, 16); return v }
        }
        monthSpinnerTop?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { if (!isInitializingSpinners) refreshUI() }; override fun onNothingSelected(p0: AdapterView<*>?) {} }
    }

    private fun setupTopYearSpinner() {
        yearSpinner?.adapter = object : ArrayAdapter<Int>(context, android.R.layout.simple_spinner_item, yearsList) {
            override fun getView(pos: Int, v: View?, parent: ViewGroup): View {
                val tv = super.getView(pos, v, parent) as TextView
                tv.textSize = 18f
                tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
                tv.text = NanakshahiCalendar.toGurmukhiYear(getItem(pos) ?: 0)
                return tv
            }
            override fun getDropDownView(pos: Int, v: View?, parent: ViewGroup): View {
                val tv = super.getDropDownView(pos, v, parent) as TextView
                tv.text = NanakshahiCalendar.toGurmukhiYear(getItem(pos) ?: 0)
                tv.setPadding(16, 16, 16, 16)
                return tv
            }
        }
        yearSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { if (!isInitializingSpinners) refreshUI() }; override fun onNothingSelected(p0: AdapterView<*>?) {} }
    }

    private fun setupMonthlyCalendarRecycler() {
        calendarRecycler?.layoutManager = GridLayoutManager(context, 7)
        calendarAdapter = MonthlyCalendarAdapter(context, listOf()) { cell -> if (cell.day != null) updateUIFromDate(getSelectedYear(), (monthSpinnerTop?.selectedItemPosition ?: 0) + 1, cell.day, showDetails = true) }
        calendarRecycler?.adapter = calendarAdapter
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean { if (e1 == null) return false; val dx = e2.x - e1.x; if (abs(dx) > 100 && abs(vx) > 100) { changeMonth(if (dx > 0) -1 else 1); return true }; return false }
        })
        calendarRecycler?.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    private fun showDateFinderDialog() {
        try {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_date_finder, null)
            val monthSpinner = dialogView.findViewById<Spinner>(R.id.month_spinner); val yearRecycler = dialogView.findViewById<RecyclerView>(R.id.year_grid_recycler)
            val curYear = getSelectedYear(); val curMonth = (monthSpinnerTop?.selectedItemPosition ?: 0) + 1
            monthSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("ਜਨਵਰੀ", "ਫਰਵਰੀ", "ਮਾਰਚ", "ਅਪ੍ਰੈਲ", "ਮਈ", "ਜੂਨ", "ਜੁਲਾਈ", "ਅਗਸਤ", "ਸਤੰਬਰ", "ਅਕਤੂਬਰ", "ਨਵੰਬਰ", "ਦਸੰਬਰ")); monthSpinner.setSelection(curMonth - 1)
            var selYear = curYear; yearRecycler.layoutManager = GridLayoutManager(context, 3); yearRecycler.adapter = YearAdapter(yearsList, selYear) { selYear = it }
            val yIdx = yearsList.indexOf(curYear); if (yIdx != -1) yearRecycler.scrollToPosition(yIdx)
            val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setView(dialogView).setPositiveButton("ਠੀਕ ਹੈ", null).setNegativeButton("ਰੱਦ ਕਰੋ", null)
            val dialog = builder.create()
            
            dialog.window?.let { window ->
                window.attributes.token = view.windowToken
                window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
            }
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { updateUIFromDate(selYear, monthSpinner.selectedItemPosition + 1, 1, showDetails = false); dialog.dismiss() } }
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(context, "ਡਾਇਲਾਗ ਖੋਲ੍ਹਣ ਵਿੱਚ ਦਿੱਕਤ ਆਈ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNanakshahiSpinners() {
        isInitializingSpinners = true; spinnerYear?.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, yearsList); spinnerMonth?.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, (1..12).toList())
        val listener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(p0: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (!isInitializingSpinners) refreshUI() }; override fun onNothingSelected(p0: AdapterView<*>?) {} }
        spinnerDay?.onItemSelectedListener = listener; spinnerMonth?.onItemSelectedListener = listener; spinnerYear?.onItemSelectedListener = listener; isInitializingSpinners = false
    }

    private fun updateDaysSpinner(year: Int, month: Int) {
        val curDay = (spinnerDay?.selectedItem as? Int) ?: 1; val cal = Calendar.getInstance().apply { set(if (year <= 0) year + 1 else year, month - 1, 1) }; val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        spinnerDay?.adapter = object : ArrayAdapter<Int>(context, android.R.layout.simple_spinner_item, (1..max).toList()) {
            override fun getView(p: Int, c: View?, parent: ViewGroup): View { val v = super.getView(p, c, parent) as TextView; v.text = NanakshahiCalendar.toGurmukhiNumber(getItem(p) ?: 1); return v }
            override fun getDropDownView(p: Int, c: View?, parent: ViewGroup): View { val v = super.getDropDownView(p, c, parent) as TextView; v.text = NanakshahiCalendar.toGurmukhiNumber(getItem(p) ?: 1); return v }
        }
        spinnerDay?.setSelection((curDay.coerceIn(1, max)) - 1)
    }

    fun updateUIFromDate(year: Int, month: Int, day: Int, showDetails: Boolean = true) {
        isInitializingSpinners = true
        val yIdx = yearsList.indexOf(year); if (yIdx != -1) { spinnerYear?.setSelection(yIdx); yearSpinner?.setSelection(yIdx) }
        spinnerMonth?.setSelection(month - 1); monthSpinnerTop?.setSelection(month - 1); updateDaysSpinner(year, month)
        val max = spinnerDay?.adapter?.count ?: 0; spinnerDay?.setSelection(if (day <= max) day - 1 else 0)
        if (showDetails) bottomControls?.visibility = View.VISIBLE
        updateConvertedDate(day, month, year)
        isInitializingSpinners = false
    }

    private fun updateConvertedDate(day: Int, month: Int, year: Int) {
        val full = NanakshahiCalendar.convert(context, day, month, year, currentLocation); val data = NanakshahiCalendar.generateMonthlyCalendar(context, month, year, currentLocation); calendarAdapter.updateData(data)
        val h = mutableListOf<String>(); var s: Int? = null; var p: Int? = null; var m: Int? = null
        for (cell in data) if (cell.day != null) { if (cell.isSangrand) s = cell.day; if (cell.isPunia) p = cell.day; if (cell.isMasaya) m = cell.day }
        if (s != null) h.add("🌾 ਸੰਕਰਾਂਤ: ${NanakshahiCalendar.toGurmukhiNumber(s)}"); if (p != null) h.add("🌕 ਪੁੰਨਿਆ: ${NanakshahiCalendar.toGurmukhiNumber(p)}"); if (m != null) h.add("🌑 ਮੱਸਿਆ: ${NanakshahiCalendar.toGurmukhiNumber(m)}")
        data.find { it.day == day }?.gurpurabName?.let { h.add("🎉 $it") }; monthlyHighlightsText?.text = h.joinToString("   "); calculationResultText?.text = full

        // Update Sun Times
        updateSunTimes(day, month, year)
    }

    private fun updateSunTimes(day: Int, month: Int, year: Int) {
        val cal = Calendar.getInstance(NanakshahiCalendar.currentTimeZone).apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val jd = julianDay(cal)
        val sunriseJd = NanakshahiCalendar.calculateSunriseJD(jd, currentLocation.lat, currentLocation.lon)
        val sunsetJd = NanakshahiCalendar.calculateSunsetJD(jd, currentLocation.lat, currentLocation.lon)
        
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
            timeZone = NanakshahiCalendar.currentTimeZone
        }
        
        val sunriseStr = timeFormat.format(Date(jdToMillis(sunriseJd)))
        val sunsetStr = timeFormat.format(Date(jdToMillis(sunsetJd)))
        
        sunTimesDisplayText?.text = "☀️ ਸੂਰਜ ਚੜ੍ਹਨਾ: $sunriseStr | 🌙 ਸੂਰਜ ਡੁੱਬਣਾ: $sunsetStr"
    }

    private fun jdToMillis(jd: Double): Long = ((jd - 2440587.5) * 86400000.0).toLong()
    private fun julianDay(cal: Calendar): Double {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = cal.timeInMillis }
        val y = utcCal.get(Calendar.YEAR)
        val m = utcCal.get(Calendar.MONTH) + 1
        val d = utcCal.get(Calendar.DAY_OF_MONTH) + (utcCal.get(Calendar.HOUR_OF_DAY) + utcCal.get(Calendar.MINUTE) / 60.0 + utcCal.get(Calendar.SECOND) / 3600.0) / 24.0
        
        var year = y
        var month = m
        if (month <= 2) { year -= 1; month += 12 }
        val isGregorian = (year > 1752) || (year == 1752 && month > 9) || (year == 1752 && month == 9 && d >= 14.0)
        return if (isGregorian) {
            val a = year / 100
            val b = 2 - a + (a / 4)
            Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + d + b - 1524.5
        } else {
            Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + d - 1524.5
        }
    }
}
