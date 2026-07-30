package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView

class MonthlyCalendarAdapter(
    private val context: Context,
    private var calendarDays: List<NanakshahiCalendar.MonthlyDayCell>,
    private val onDayClick: (NanakshahiCalendar.MonthlyDayCell) -> Unit
) : RecyclerView.Adapter<MonthlyCalendarAdapter.DayViewHolder>() {

    private var customTypeface: Typeface? = null
    private var isBoldMode = false

    init {
        loadCustomFont()
    }

    private fun loadCustomFont() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val fontFileName = sharedPrefs.getString("pref_keyboard_font", "AKHAR.ttf.TTF")
        isBoldMode = fontFileName == "default_bold"

        if (fontFileName == "default") {
            customTypeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            return
        }
        if (fontFileName == "default_bold") {
            customTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            return
        }

        try {
            customTypeface = try {
                Typeface.createFromAsset(context.assets, "fonts/$fontFileName")
            } catch (e: Exception) {
                val altName = if (fontFileName?.endsWith(".ttf", true) == true) {
                    if (fontFileName.endsWith(".ttf")) fontFileName.replace(".ttf", ".TTF")
                    else fontFileName.replace(".TTF", ".ttf")
                } else fontFileName
                try {
                    Typeface.createFromAsset(context.assets, "fonts/$altName")
                } catch (e2: Exception) {
                    Typeface.DEFAULT
                }
            }
        } catch (e: Exception) {
            customTypeface = Typeface.DEFAULT
        }
    }

    inner class DayViewHolder(val dayCell: View) : RecyclerView.ViewHolder(dayCell)

    fun getCalendarDays(): List<NanakshahiCalendar.MonthlyDayCell> = calendarDays

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = calendarDays[position]
        val textView = holder.dayCell.findViewById<TextView>(R.id.dayText)
        val indicator = holder.dayCell.findViewById<View>(R.id.eventIndicator)

        if (day.isEmpty) {
            textView.text = ""
            textView.background = null
            indicator?.visibility = View.GONE
            holder.dayCell.isClickable = false
            return
        }

        textView.text = day.displayText
        holder.dayCell.isClickable = true

        // ਦਿਨਾਂ (Days) ਲਈ ਹਮੇਸ਼ਾ ਡਿਫੌਲਟ ਫੌਂਟ ਵਰਤੋ ਤਾਂ ਜੋ ਅੰਗਰੇਜ਼ੀ ਅੱਖਰ/ਨੰਬਰ ਸਹੀ ਦਿਖਾਈ ਦੇਣ
        textView.typeface = if (isBoldMode) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        textView.paint.isFakeBoldText = isBoldMode

        textView.setBackgroundResource(R.drawable.bg_calendar_day)
        val background = textView.background as? GradientDrawable
        val themeOrange = Color.parseColor("#EF6C00")
        val sundayRed = Color.parseColor("#D32F2F")
        val lightSundayRed = Color.parseColor("#FFCDD2")
        val defaultStrokeWidth = 3
        val eventStrokeWidth = 5

        background?.setStroke(defaultStrokeWidth, themeOrange)

        if (!day.isCurrentMonth) {
            textView.setTextColor(Color.parseColor("#9E9E9E"))
            background?.setColor(Color.parseColor("#F5F5F5"))
            background?.setAlpha(150)
        } else {
            textView.setTextColor(Color.BLACK)
            background?.setColor(Color.parseColor("#FFFDE7"))
            background?.setAlpha(255)
            if (position % 7 == 0) {
                textView.setTextColor(sundayRed)
                background?.setStroke(defaultStrokeWidth, sundayRed)
                background?.setColor(lightSundayRed)
            }
        }

        indicator?.visibility = View.GONE
        if (day.isToday) {
            background?.setColor(themeOrange)
            background?.setStroke(0, Color.TRANSPARENT)
            background?.setAlpha(255)
            textView.setTextColor(Color.WHITE)
            holder.dayCell.setOnClickListener { onDayClick(day) }
            return
        }

        if (day.isCurrentMonth) {
            val isSunday = (position % 7 == 0)
            when {
                day.gurpurabName != null -> {
                    val color = day.gurpurabColor ?: themeOrange
                    background?.setColor(if (isSunday) lightSundayRed else Color.parseColor("#FFF3E0"))
                    background?.setStroke(eventStrokeWidth, color)
                    textView.setTextColor(color)
                }
                day.isSangrand -> {
                    val color = Color.parseColor("#2E7D32")
                    background?.setColor(if (isSunday) lightSundayRed else Color.parseColor("#E8F5E9"))
                    background?.setStroke(eventStrokeWidth, color)
                    textView.setTextColor(color)
                }
                day.isPunia -> {
                    val color = Color.parseColor("#1565C0")
                    background?.setColor(if (isSunday) lightSundayRed else Color.parseColor("#E3F2FD"))
                    background?.setStroke(eventStrokeWidth, color)
                    textView.setTextColor(color)
                }
                day.isMasaya -> {
                    val color = Color.parseColor("#7B1FA2")
                    background?.setColor(if (isSunday) lightSundayRed else Color.parseColor("#F3E5F5"))
                    background?.setStroke(eventStrokeWidth, color)
                    textView.setTextColor(color)
                }
            }
        }
        holder.dayCell.setOnClickListener { onDayClick(day) }
    }

    override fun getItemCount() = calendarDays.size

    fun updateData(newDays: List<NanakshahiCalendar.MonthlyDayCell>) {
        loadCustomFont() // Reload font in case it changed
        this.calendarDays = newDays
        notifyDataSetChanged()
    }
}
