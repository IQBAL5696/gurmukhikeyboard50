package com.iqbal.gurmukhikeyboard50

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.DynamicDrawableSpan
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.*

class NanakshahiCalendarPanel(private val context: Context, private val onDismiss: () -> Unit, private val onInsertDate: (String) -> Unit) {
    private var isInitializingSpinners = false
    private var isDesiMode = false
    private var spinnerDay: Spinner? = null
    private var spinnerMonth: Spinner? = null
    private var spinnerYear: Spinner? = null
    private var yearSpinner: Spinner? = null
    private var monthSpinnerTop: Spinner? = null
    private var calendarModeSpinner: Spinner? = null
    private var prevMonthButton: ImageButton? = null
    private var nextMonthButton: ImageButton? = null
    private var locationDisplayText: TextView? = null
    private lateinit var calendarAdapter: MonthlyCalendarAdapter
    private var calendarRecycler: RecyclerView? = null
    private var monthlyHighlightsText: TextView? = null
    private var calculationResultText: TextView? = null
    private var startDateForCalc: Calendar? = null
    private var infoPanelCard: CardView? = null
    private val todayCal: Calendar = Calendar.getInstance(NanakshahiCalendar.currentTimeZone)
    private val yearsList = (-2000..10000).toList()
    private var currentLocation: NanakshahiCalendar.LocationConfig = NanakshahiCalendar.LocationConfig.AMRITSAR
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private var bottomControls: View? = null
    val view: View
    private var updateJob: Job? = null
    private val nitnemEngine = NitnemEngine(context, onDismiss)

    init {
        view = LayoutInflater.from(context).inflate(R.layout.nanakshahi_calendar_panel, null)
        setupNanakshahiCalendarPanel()
        requestLocationUpdate()
    }

    private fun setupDialogWindow(dial: AlertDialog, focusable: Boolean = false) {
        dial.window?.let { window ->
            if (context !is Activity) {
                if (focusable) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                val token = view.windowToken
                if (token != null) {
                    window.attributes.token = token
                    window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
                } else {
                    window.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                }
            }
        }
    }

    private fun getCustomTypeface(): Typeface {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val fontFileName = sharedPrefs.getString("pref_keyboard_font", "AKHAR.TTF") ?: "AKHAR.TTF"
        if (fontFileName == "default") return Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        return try {
            Typeface.createFromAsset(context.assets, "fonts/$fontFileName")
        } catch (e: Exception) {
            try {
                val alt = if (fontFileName.lowercase().endsWith(".ttf")) fontFileName.uppercase() else fontFileName.lowercase()
                Typeface.createFromAsset(context.assets, "fonts/$alt")
            } catch (e2: Exception) { Typeface.DEFAULT }
        }
    }

    fun stopMusic() {
        nitnemEngine.stopMusic()
    }

    fun setupNitnemPanel(nitnemView: View) {
        nitnemEngine.setupNitnemPanel(nitnemView)
    }

    private fun setupNanakshahiCalendarPanel() {
        spinnerDay = view.findViewById(R.id.spinnerDay)
        spinnerMonth = view.findViewById(R.id.spinnerMonth)
        spinnerYear = view.findViewById(R.id.spinnerYear)
        yearSpinner = view.findViewById(R.id.year_spinner)
        monthSpinnerTop = view.findViewById(R.id.month_spinner_top)
        calendarModeSpinner = view.findViewById(R.id.calendar_mode_spinner)
        prevMonthButton = view.findViewById(R.id.prev_month_button)
        nextMonthButton = view.findViewById(R.id.next_month_button)
        locationDisplayText = view.findViewById(R.id.location_display_text)
        calendarRecycler = view.findViewById(R.id.calendarRecycler)
        monthlyHighlightsText = view.findViewById(R.id.monthly_highlights_text)
        calculationResultText = view.findViewById(R.id.nanakshahi_calendar_output_text)
        bottomControls = view.findViewById(R.id.bottom_controls)
        infoPanelCard = view.findViewById(R.id.info_panel_card)

        val customTf = getCustomTypeface()
        monthlyHighlightsText?.typeface = customTf
        calculationResultText?.typeface = customTf
        locationDisplayText?.typeface = customTf

        bottomControls?.visibility = View.VISIBLE
        infoPanelCard?.visibility = View.GONE

        view.findViewById<ImageButton>(R.id.close_calendar_button)?.setOnClickListener { onDismiss() }
        view.findViewById<Button>(R.id.calculateDateButton)?.setOnClickListener { calculateDiff() }
        view.findViewById<Button>(R.id.findDateButton)?.setOnClickListener { showDateFinderDialog() }
        view.findViewById<Button>(R.id.btn_events)?.setOnClickListener { showEventsDialog() }
        view.findViewById<Button>(R.id.btn_today)?.setOnClickListener {
            val now = Calendar.getInstance(NanakshahiCalendar.currentTimeZone)
            updateUIFromDate(NanakshahiCalendar.getAstronomicalYear(now), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH), details = true)
        }
        view.findViewById<Button>(R.id.btn_hukamnama)?.setOnClickListener { openHukamnamaWebsite() }

        monthlyHighlightsText?.setOnClickListener {
            val ay = getSelectedYear()
            val month = (monthSpinnerTop?.selectedItemPosition ?: 0) + 1
            val day = (spinnerDay?.selectedItem as? Int) ?: 1
            onInsertDate(NanakshahiCalendar.getShortNanakshahiDate(context, day, month, ay))
            Toast.makeText(context, "ਤਾਰੀਖ ਲਿਖੀ ਗਈ", Toast.LENGTH_SHORT).show()
        }

        prevMonthButton?.setOnClickListener { changeMonth(-1) }
        nextMonthButton?.setOnClickListener { changeMonth(1) }
        locationDisplayText?.setOnClickListener { showLocationSearchDialog() }

        val commonListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (!isInitializingSpinners) refreshUI() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        yearSpinner?.onItemSelectedListener = commonListener
        monthSpinnerTop?.onItemSelectedListener = commonListener
        spinnerDay?.onItemSelectedListener = commonListener
        spinnerMonth?.onItemSelectedListener = commonListener
        spinnerYear?.onItemSelectedListener = commonListener

        view.findViewById<Switch>(R.id.calendar_type_switch)?.apply {
            isChecked = isDesiMode
            setOnCheckedChangeListener { _, isChecked ->
                if (isInitializingSpinners) return@setOnCheckedChangeListener
                val dGreg = calendarAdapter.getCalendarDays().find { it.gregCal != null && it.isCurrentMonth }?.gregCal ?: todayCal
                uiScope.launch {
                    val tY = NanakshahiCalendar.getAstronomicalYear(dGreg); val tM = dGreg.get(Calendar.MONTH) + 1; val tD = dGreg.get(Calendar.DAY_OF_MONTH)
                    var fY = tY; var fM = tM; var fD = tD
                    if (isChecked) {
                        val res = withContext(Dispatchers.Default) {
                            val cal = dGreg.clone() as Calendar; cal.set(Calendar.DAY_OF_MONTH, 1)
                            var resY = tY; var resM = tM; var resD = tD
                            for (i in 0..31) {
                                val jd = NanakshahiCalendar.julianDay(cal)
                                if (NanakshahiCalendar.getSolarBikramiDate(this@NanakshahiCalendarPanel.context, jd, currentLocation).second == 1) {
                                    resY = NanakshahiCalendar.getAstronomicalYear(cal); resM = cal.get(Calendar.MONTH) + 1; resD = cal.get(Calendar.DAY_OF_MONTH); break
                                }
                                cal.add(Calendar.DAY_OF_MONTH, 1)
                            }
                            Triple(resY, resM, resD)
                        }
                        fY = res.first; fM = res.second; fD = res.third
                    }
                    isInitializingSpinners = true; isDesiMode = isChecked
                    setupTopYearSpinner(); setupTopMonthSpinner()
                    updateUIFromDate(fY, fM, fD, false)
                    isInitializingSpinners = false
                }
            }
        }

        setupCalendarModeSpinner()
        isInitializingSpinners = true
        setupMonthlyCalendarRecycler()
        setupNanakshahiSpinners()
        setupTopYearSpinner()
        setupTopMonthSpinner()
        updateUIFromDate(NanakshahiCalendar.getAstronomicalYear(todayCal), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH), false)
        isInitializingSpinners = false
    }

    private fun openHukamnamaWebsite() { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gurbaninow.com/hukamnama")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { Toast.makeText(context, "ਵੈੱਬਸਾਈਟ ਖੋਲ੍ਹਣ ਵਿੱਚ ਦਿੱਕਤ ਆਈ", Toast.LENGTH_SHORT).show() } }

    private fun showLocationSearchDialog() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        val etSearch = EditText(context).apply {
            hint = "ਸ਼ਹਿਰ ਦਾ ਨਾਮ ਲਿਖੋ (e.g. Amritsar)"
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            setPadding(16, 16, 16, 16)
        }
        val btnGps = Button(context).apply {
            text = "ਮੌਜੂਦਾ ਲੋਕੇਸ਼ਨ ਲੱਭੋ (GPS)"
            setBackgroundColor(Color.parseColor("#EF6C00"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 }
        }
        root.addView(etSearch); root.addView(btnGps)

        val dial = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("ਲੋਕੇਸ਼ਨ ਚੁਣੋ")
            .setView(root)
            .setPositiveButton("ਸਰਚ ਕਰੋ", null)
            .setNegativeButton("ਰੱਦ ਕਰੋ", null)
            .create()

        setupDialogWindow(dial, focusable = true)
        dial.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dial.setOnShowListener {
            etSearch.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)

            dial.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = etSearch.text.toString()
                if (query.isNotEmpty()) { searchLocationByName(query); dial.dismiss() }
                else Toast.makeText(context, "ਕਿਰਪਾ ਕਰਕੇ ਨਾਮ ਲਿਖੋ", Toast.LENGTH_SHORT).show()
            }
            btnGps.setOnClickListener { requestLocationUpdate(); dial.dismiss() }
        }
        dial.show()
    }

    private fun searchLocationByName(name: String) {
        uiScope.launch {
            try {
                val geocoder = Geocoder(context, Locale.ENGLISH)
                @Suppress("DEPRECATION") val results = withContext(Dispatchers.IO) { geocoder.getFromLocationName(name, 1) }
                if (results != null && results.isNotEmpty()) {
                    val loc = results[0]
                    updateLocation(loc.latitude, loc.longitude)
                    Toast.makeText(context, "ਲੋਕੇਸ਼ਨ ਮਿਲੀ: ${loc.locality ?: loc.adminArea ?: name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "ਲੋਕੇਸ਼ਨ ਨਹੀਂ ਮਿਲੀ। ਕਿਰਪਾ ਕਰਕੇ ਸਪੈਲਿੰਗ ਚੈੱਕ ਕਰੋ।", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ਸਰਚ ਕਰਨ ਵਿੱਚ ਦਿੱਕਤ ਆਈ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCalendarModeSpinner() { val adapter = ArrayAdapter.createFromResource(context, R.array.calendar_mode_entries, android.R.layout.simple_spinner_item); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); calendarModeSpinner?.adapter = adapter; calendarModeSpinner?.setSelection(when (NanakshahiCalendar.selectedSystem) { NanakshahiCalendar.CalendarSystem.MOOL_NANAKSHAHI -> 0; NanakshahiCalendar.CalendarSystem.BIKRAMI_DRIK -> 1; NanakshahiCalendar.CalendarSystem.BIKRAMI_SURYA -> 2; NanakshahiCalendar.CalendarSystem.BIKRAMI_LUNAR -> 3; NanakshahiCalendar.CalendarSystem.GREGORIAN -> 4 }); calendarModeSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (isInitializingSpinners) return;
        NanakshahiCalendar.selectedSystem = when (pos) { 0 -> NanakshahiCalendar.CalendarSystem.MOOL_NANAKSHAHI; 1 -> NanakshahiCalendar.CalendarSystem.BIKRAMI_DRIK; 2 -> NanakshahiCalendar.CalendarSystem.BIKRAMI_SURYA; 3 -> NanakshahiCalendar.CalendarSystem.BIKRAMI_LUNAR; 4 -> NanakshahiCalendar.CalendarSystem.GREGORIAN; else -> NanakshahiCalendar.CalendarSystem.BIKRAMI_DRIK }; refreshUI() }; override fun onNothingSelected(p: AdapterView<*>?) {} } }

    private fun changeMonth(inc: Int) { if (isDesiMode) { var mIdx = (monthSpinnerTop?.selectedItemPosition ?: 0) + inc; var y = getSelectedYear(); if (mIdx > 11) { mIdx = 0; y = y + 1 } else if (mIdx < 0) { mIdx = 11; y = y - 1 }; uiScope.launch { val start = withContext(Dispatchers.Default) { NanakshahiCalendar.findDesiMonthStart(y - 1468, NanakshahiCalendar.DESI_MONTHS[mIdx.coerceIn(0, 11)], context) }; start?.let { updateUIFromDate(NanakshahiCalendar.getAstronomicalYear(it), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH), false) } } } else { var nY = getSelectedYear(); var nM = (monthSpinnerTop?.selectedItemPosition ?: 0) + 1 + inc; if (nM > 12) { nM = 1; nY = nY + 1 } else if (nM < 1) { nM = 12; nY = nY - 1 }; updateUIFromDate(nY, nM, (spinnerDay?.selectedItem as? Int) ?: 1, false) } }
    private fun calculateDiff() { val y = getSelectedYear(); val m = (monthSpinnerTop?.selectedItemPosition ?: 0) + 1; val d = (spinnerDay?.selectedItem as? Int) ?: 1; val uiY = y; if (startDateForCalc == null) { startDateForCalc = NanakshahiCalendar.getCalendarInstance(uiY, m, d); showDetailsPopup("ਪਹਿਲੀ ਤਾਰੀਖ ਚੁਣੀ ਗਈ। ਹੁਣ ਦੂਜੀ ਚੁਣ ਕੇ ਦੁਬਾਰਾ ਦਬਾਓ।") } else { val diff = NanakshahiCalendar.calculateDateDifference(startDateForCalc!!, NanakshahiCalendar.getCalendarInstance(uiY, m, d)); showDetailsPopup("ਅੰਤਰ: ${NanakshahiCalendar.toGurmukhiNumber(diff.years)} ਸਾਲ, ${NanakshahiCalendar.toGurmukhiNumber(diff.months)} ਮਹੀਨੇ, ${NanakshahiCalendar.toGurmukhiNumber(diff.days)} ਦਿਨ"); startDateForCalc = null } }
    private fun showEventsDialog() { try { val y = getSelectedYear(); val m = (monthSpinnerTop?.selectedItemPosition ?: 0) + 1; val d = (spinnerDay?.selectedItem as? Int) ?: 1; val nsY = if (m > 3 || (m == 3 && d >= 14)) y - 1468 else y - 1469; val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("ਗੁਰਪੁਰਬ ਸੂਚੀ (ਸੰਮਤ ${NanakshahiCalendar.toGurmukhiNanakshahiYear(nsY)})"); val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }; val prog = ProgressBar(context, null, android.R.attr.progressBarStyleLarge).apply { layoutParams = LinearLayout.LayoutParams(100, 100).apply { gravity = android.view.Gravity.CENTER } }; val scroll = ScrollView(context); val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }; root.addView(prog); root.addView(scroll); scroll.addView(list); builder.setView(root).setPositiveButton("ਠੀਕ ਹੈ", null); val dial = builder.create(); setupDialogWindow(dial); dial.show(); uiScope.launch { val evs = withContext(Dispatchers.Default) { GurpurabData.getSgpcGurpurabs(context, nsY) }; prog.visibility = View.GONE; if (evs.isEmpty()) list.addView(TextView(context).apply { text = "ਕੋਈ ਡੇਟਾ ਨਹੀਂ ਮਿਲਿਆ।"; setPadding(16, 16, 16, 16) }) else evs.forEach { e -> val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 16); gravity = android.view.Gravity.CENTER_VERTICAL }; r.addView(TextView(context).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f); text = "${e.name}\n(${NanakshahiCalendar.toGurmukhiNumber(e.day)} ${e.month})"; textSize = 16f; setTextColor(e.gurpurabColor ?: Color.BLACK); typeface = getCustomTypeface() }); r.addView(ImageButton(context).apply { setImageResource(android.R.drawable.ic_menu_today); setBackgroundColor(0); setOnClickListener { e.gregDate?.let { updateUIFromDate(NanakshahiCalendar.getAstronomicalYear(it), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH), true) }; dial.dismiss() } }); r.addView(ImageButton(context).apply { setImageResource(android.R.drawable.ic_menu_share); setBackgroundColor(0); setOnClickListener { shareEvent(e) } }); list.addView(r); list.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(-1, 1); setBackgroundColor(Color.LTGRAY) }) } } } catch (e: Exception) { Toast.makeText(context, "ਡਾਇਲਾਗ ਖੋਲ੍ਹਣ ਵਿੱਚ ਦਿੱਕਤ ਆਈ", Toast.LENGTH_SHORT).show() } }
    private fun shareEvent(e: NanakshahiCalendar.Gurpurab) { val txt = "🎉 *${e.name}*\n📅 ਤਾਰੀਖ: ${NanakshahiCalendar.toGurmukhiNumber(e.day)} ${e.month}\n📍 ਕੈਲੰਡਰ: ਨਾਨਕਸ਼ਾਹੀ\n\n_ਜੀ ਆਇਆਂ ਨੂੰ - ਗੁਰਮੁਖੀ ਕੀਬੋਰਡ_"; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, txt); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    @SuppressLint("MissingPermission") private fun requestLocationUpdate() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == 0 || coarse == 0) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener {
                if (it != null) updateLocation(it.latitude, it.longitude)
                else fused.lastLocation.addOnSuccessListener { l -> if (l != null) updateLocation(l.latitude, l.longitude) else locationDisplayText?.text = "📍 ਲੋਕੇਸ਼ਨ ਨਹੀਂ ਮਿਲੀ" }
            }.addOnFailureListener { locationDisplayText?.text = "📍 ਲੋਕੇਸ਼ਨ ਫੇਲ੍ਹ: ${it.message}" }
        } else {
            locationDisplayText?.text = "📍 ਲੋਕੇਸ਼ਨ ਚੁਣੋ (ਕਲਿੱਕ ਕਰੋ)"
        }
    }
    private fun updateLocation(lat: Double, lon: Double) {
        currentLocation = NanakshahiCalendar.LocationConfig(lat, lon)
        uiScope.launch {
            try {
                val addrs = withContext(Dispatchers.IO) { Geocoder(context, Locale("pa")).getFromLocation(lat, lon, 1) }
                val cityName = addrs?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.featureName } ?: "ਮੌਜੂਦਾ ਸਥਾਨ"
                locationDisplayText?.text = "📍 $cityName"
            } catch (e: Exception) {
                locationDisplayText?.text = "📍 ($lat, $lon)"
            }
            refreshUI()
        }
    }
    private fun getSelectedYear(): Int { val pos = yearSpinner?.selectedItemPosition ?: return NanakshahiCalendar.getAstronomicalYear(todayCal); return if (pos in yearsList.indices) yearsList[pos] else NanakshahiCalendar.getAstronomicalYear(todayCal) }
    private fun refreshUI() { if (isInitializingSpinners) return; val y = getSelectedYear(); val mIdx = monthSpinnerTop?.selectedItemPosition ?: 0; uiScope.launch { if (isDesiMode) { val start = withContext(Dispatchers.Default) { NanakshahiCalendar.findDesiMonthStart(y - 1468, NanakshahiCalendar.DESI_MONTHS[mIdx], context) }; start?.let { updateUIFromDate(NanakshahiCalendar.getAstronomicalYear(it), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH), false) } } else updateUIFromDate(y, mIdx + 1, (spinnerDay?.selectedItem as? Int) ?: 1, false) } }
    private fun setupTopMonthSpinner() { val months = if (isDesiMode) NanakshahiCalendar.DESI_MONTHS else listOf("ਜਨਵਰੀ", "ਫਰਵਰੀ", "ਮਾਰਚ", "ਅਪ੍ਰੈਲ", "ਮਈ", "ਜੂਨ", "ਜੁਲਾਈ", "ਅਗਸਤ", "ਸਤੰਬਰ", "ਅਕਤੂਬਰ", "ਨਵੰਬਰ", "ਦਸੰਬਰ"); monthSpinnerTop?.adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, months) { override fun getView(p: Int, v: View?, parent: ViewGroup): View { val tv = super.getView(p, v, parent) as TextView; tv.textSize = 18f; tv.setTypeface(tv.typeface, Typeface.BOLD); return tv }; override fun getDropDownView(p: Int, v: View?, parent: ViewGroup): View { val tv = super.getDropDownView(p, v, parent) as TextView; tv.setPadding(16, 16, 16, 16); return tv } } }
    private fun setupTopYearSpinner() { yearSpinner?.adapter = object : ArrayAdapter<Int>(context, android.R.layout.simple_spinner_item, yearsList) { override fun getView(p: Int, v: View?, parent: ViewGroup): View { val tv = super.getView(p, v, parent) as TextView; tv.textSize = 18f; tv.setTypeface(tv.typeface, Typeface.BOLD); val y = getItem(p) ?: 0; tv.text = if (isDesiMode) "ਸੰਮਤ ${NanakshahiCalendar.toGurmukhiNanakshahiYear(y - 1468)}" else NanakshahiCalendar.toGurmukhiYear(y); return tv }; override fun getDropDownView(p: Int, v: View?, parent: ViewGroup): View { val tv = super.getDropDownView(p, v, parent) as TextView; val y = getItem(p) ?: 0; tv.text = if (isDesiMode) "ਸੰਮਤ ${NanakshahiCalendar.toGurmukhiNanakshahiYear(y - 1468)}" else NanakshahiCalendar.toGurmukhiYear(y); tv.setPadding(16, 16, 16, 16); return tv } } }
    private fun setupMonthlyCalendarRecycler() { calendarRecycler?.layoutManager = GridLayoutManager(context, 7); calendarAdapter = MonthlyCalendarAdapter(context, listOf()) { if (it.day != null && it.gregCal != null) { val g = it.gregCal!!; updateUIFromDate(NanakshahiCalendar.getAstronomicalYear(g), g.get(Calendar.MONTH) + 1, g.get(Calendar.DAY_OF_MONTH), true) } }; calendarRecycler?.adapter = calendarAdapter; val det = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() { override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean { if (e1 == null) return false; val dx = e2.x - e1.x; if (abs(dx) > 100 && abs(vx) > 100) { changeMonth(if (dx > 0) -1 else 1); return true }; return false } }); calendarRecycler?.setOnTouchListener { _, e -> det.onTouchEvent(e) } }
    private fun showDateFinderDialog() { try { val dv = LayoutInflater.from(context).inflate(R.layout.dialog_date_finder, null); val ms = dv.findViewById<Spinner>(R.id.month_spinner); val yr = dv.findViewById<RecyclerView>(R.id.year_grid_recycler); val ay = getSelectedYear(); val cM = (monthSpinnerTop?.selectedItemPosition ?: 0) + 1; ms.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("ਜਨਵਰੀ", "ਫਰਵਰੀ", "ਮਾਰਚ", "ਅਪ੍ਰੈਲ", "ਮਈ", "ਜੂਨ", "ਜੁਲਾਈ", "ਅਗਸਤ", "ਸਤੰਬਰ", "ਅਕਤੂਬਰ", "ਨਵੰਬਰ", "ਦਸੰਬਰ")); ms.setSelection(cM - 1); var sAY = ay; yr.layoutManager = GridLayoutManager(context, 6); val uiY = ay; yr.adapter = YearAdapter(yearsList, uiY) { sAY = it }; val yIdx = yearsList.indexOf(uiY); if (yIdx != -1) yr.scrollToPosition(yIdx); val dial = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setView(dv).setPositiveButton("ਠੀਕ ਹੈ", null).setNegativeButton("ਰੱਦ ਕਰੋ", null).create(); setupDialogWindow(dial); dial.setOnShowListener { dial.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { updateUIFromDate(sAY, ms.selectedItemPosition + 1, 1, true); dial.dismiss() } }; dial.show() } catch (e: Exception) { Toast.makeText(context, "ਡਾਇਲਾਗ ਖੋਲ੍ਹਣ ਵਿੱਚ ਦਿੱਕਤ ਆਈ", Toast.LENGTH_SHORT).show() } }
    private fun setupNanakshahiSpinners() { spinnerYear?.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, yearsList); spinnerMonth?.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, (1..12).toList()) }
    private fun updateDaysSpinner(y: Int, m: Int) { val cur = (spinnerDay?.selectedItem as? Int) ?: 1; val uiY = y; val cal = NanakshahiCalendar.getCalendarInstance(uiY, m, 1); val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH); spinnerDay?.adapter = object : ArrayAdapter<Int>(context, android.R.layout.simple_spinner_item, (1..max).toList()) { override fun getView(p: Int, c: View?, parent: ViewGroup): View { val v = super.getView(p, c, parent) as TextView; v.text = NanakshahiCalendar.toGurmukhiNumber(getItem(p) ?: 1); return v }; override fun getDropDownView(p: Int, c: View?, parent: ViewGroup): View { val v = super.getDropDownView(p, c, parent) as TextView; v.text = NanakshahiCalendar.toGurmukhiNumber(getItem(p) ?: 1); v.setPadding(16, 16, 16, 16); return v } }; spinnerDay?.setSelection((cur.coerceIn(1, max)) - 1) }
    fun updateUIFromDate(y: Int, m: Int, d: Int, details: Boolean = false) { val was = isInitializingSpinners; isInitializingSpinners = true; val uiY = y; if (isDesiMode) { val ns = NanakshahiCalendar.getNanakshahiDate(context, d, m, uiY); val nsMIdx = NanakshahiCalendar.getNanakshahiDate(context, d, m, uiY).let { NanakshahiCalendar.DESI_MONTHS.indexOf(it.month) }; if (nsMIdx != -1 && monthSpinnerTop?.selectedItemPosition != nsMIdx) monthSpinnerTop?.setSelection(nsMIdx); val tGY = ns.year + 1468; val uiGY = tGY; val nsYIdx = yearsList.indexOf(uiGY); if (nsYIdx != -1 && yearSpinner?.selectedItemPosition != nsYIdx) yearSpinner?.setSelection(nsYIdx) } else { val yIdx = yearsList.indexOf(uiY); if (yIdx != -1 && yearSpinner?.selectedItemPosition != yIdx) yearSpinner?.setSelection(yIdx); if (monthSpinnerTop?.selectedItemPosition != m - 1) monthSpinnerTop?.setSelection(m - 1) }; val yIdx = yearsList.indexOf(uiY); if (yIdx != -1 && spinnerYear?.selectedItemPosition != yIdx) spinnerYear?.setSelection(yIdx); if (spinnerMonth?.selectedItemPosition != m - 1) spinnerMonth?.setSelection(m - 1) ; updateDaysSpinner(y, m); val max = (spinnerDay?.adapter as? ArrayAdapter<Int>)?.count ?: 0; val tDP = if (d <= max && d > 0) d - 1 else 0; if (spinnerDay?.selectedItemPosition != tDP) spinnerDay?.setSelection(tDP); updateConvertedDate(d, m, y, details); isInitializingSpinners = was }
    private fun updateConvertedDate(d: Int, m: Int, y: Int, details: Boolean = false) { updateJob?.cancel(); updateJob = uiScope.launch { val res = withContext(Dispatchers.Default) { val uiY = y; val data = if (isDesiMode) { val ns = NanakshahiCalendar.getNanakshahiDate(context, d, m, uiY); NanakshahiCalendar.generateMonthlyCalendarDesi(context, ns.month, ns.year, currentLocation) } else NanakshahiCalendar.generateMonthlyCalendar(context, m, uiY, currentLocation); val full = NanakshahiCalendar.convert(context, d, m, uiY, currentLocation); val hi = mutableListOf<String>(); var s: Int? = null; var p: Int? = null; var ma: Int? = null; data.forEach { if (it.day != null && it.isCurrentMonth) { if (it.isSangrand) s = it.day; if (it.isPunia) p = it.day; if (it.isMasaya) ma = it.day } }; if (s != null) hi.add("🌾 ਸੰਕਰਾਂਤ: ${NanakshahiCalendar.toGurmukhiNumber(s!!)}"); if (p != null) hi.add("🌕 ਪੁੰਨਿਆ: ${NanakshahiCalendar.toGurmukhiNumber(p!!)}"); if (ma != null) hi.add("🌑 ਮੱਸਿਆ: ${NanakshahiCalendar.toGurmukhiNumber(ma!!)}"); 
        val nsD = NanakshahiCalendar.getNanakshahiDate(context, d, m, uiY); 
        GurpurabData.getSgpcGurpurabs(context, nsD.year)
            .filter { it.day == nsD.day && it.month == nsD.month }
            .forEach { hi.add("🎉 ${it.name}") }; 
        Triple(data, full, hi.distinct().joinToString("   ")) }; 
        calendarAdapter.updateData(res.first); monthlyHighlightsText?.text = res.third; calculationResultText?.text = ""
        if (details) { showDetailsPopup(res.second) } } }

    private fun showDetailsPopup(info: String) {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) ; setBackgroundColor(Color.WHITE) }
        val scroll = ScrollView(context).apply { layoutParams = LinearLayout.LayoutParams(-1, -2) }
        val tv = TextView(context).apply { text = info; textSize = 16f; setTextColor(Color.BLACK); setLineSpacing(0f, 1.2f); typeface = getCustomTypeface() }
        scroll.addView(tv); root.addView(scroll)
        val dial = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("ਤਾਰੀਖ ਦੀ ਜਾਣਕਾਰੀ").setView(root).setPositiveButton("ਠੀਕ ਹੈ", null).create()
        setupDialogWindow(dial); dial.show()
    }
}
