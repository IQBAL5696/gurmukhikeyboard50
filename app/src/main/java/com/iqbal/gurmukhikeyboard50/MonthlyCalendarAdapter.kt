package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class MonthlyCalendarAdapter(
    private val context: Context,
    private var calendarDays: List<NanakshahiCalendar.MonthlyDayCell>,
    private val onDayClick: (NanakshahiCalendar.MonthlyDayCell) -> Unit
) : RecyclerView.Adapter<MonthlyCalendarAdapter.DayViewHolder>() {

    inner class DayViewHolder(val dayCell: View) : RecyclerView.ViewHolder(dayCell)

    fun getCalendarDays(): List<NanakshahiCalendar.MonthlyDayCell> = calendarDays

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
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

        // Reset background
        textView.setBackgroundResource(R.drawable.bg_calendar_day)
        val background = textView.background as? GradientDrawable
        
        // Define theme orange color
        val themeOrange = Color.parseColor("#EF6C00")
        val sundayRed = Color.parseColor("#D32F2F") // Material Red 700
        
        // Bold border thickness
        val defaultStrokeWidth = 3
        val eventStrokeWidth = 5
        
        // Default stroke for all cells
        background?.setStroke(defaultStrokeWidth, themeOrange)

        // Handle styling
        if (!day.isCurrentMonth) {
            textView.setTextColor(Color.parseColor("#9E9E9E")) // Dimmed text
            background?.setColor(Color.parseColor("#F5F5F5")) // Grayish for filler days
            background?.setAlpha(150)
            textView.typeface = Typeface.DEFAULT
        } else {
            textView.setTextColor(Color.BLACK)
            // Light cream background for all normal days to make it look "colorful"
            background?.setColor(Color.parseColor("#FFFDE7")) 
            background?.setAlpha(255)
            textView.typeface = Typeface.DEFAULT
            
            // 🚩 Check if it is SUNDAY
            if (day.gregCal?.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                textView.setTextColor(sundayRed)
                background?.setStroke(defaultStrokeWidth, sundayRed)
                background?.setColor(Color.parseColor("#FFEBEE")) // Very light red background
            }
        }
        
        indicator?.visibility = View.GONE

        // 🌟 TODAY STYLING: Solid theme color
        if (day.isToday) {
            background?.setColor(themeOrange)
            background?.setStroke(0, Color.TRANSPARENT)
            background?.setAlpha(255)
            textView.setTextColor(Color.WHITE)
            textView.typeface = Typeface.DEFAULT_BOLD
            holder.dayCell.setOnClickListener { onDayClick(day) }
            return
        }

        // Apply specific colors for events (current month only)
        if (day.isCurrentMonth) {
            when {
                day.gurpurabName != null -> {
                    val color = day.gurpurabColor ?: themeOrange
                    background?.setColor(Color.parseColor("#FFF3E0")) // Light orange background
                    background?.setStroke(eventStrokeWidth, color)
                    textView.setTextColor(color)
                    textView.typeface = Typeface.DEFAULT_BOLD
                }
                day.isSangrand -> {
                    val color = Color.parseColor("#2E7D32")
                    background?.setColor(Color.parseColor("#E8F5E9")) // Light green background
                    background?.setStroke(eventStrokeWidth, color)
                    textView.setTextColor(color)
                    textView.typeface = Typeface.DEFAULT_BOLD
                }
                day.isPunia -> {
                    val color = Color.parseColor("#1565C0")
                    background?.setColor(Color.parseColor("#E3F2FD")) // Light blue background
                    background?.setStroke(eventStrokeWidth, color)
                    textView.setTextColor(color)
                    textView.typeface = Typeface.DEFAULT_BOLD
                }
                day.isMasaya -> {
                    val color = Color.parseColor("#7B1FA2")
                    background?.setColor(Color.parseColor("#F3E5F5")) // Light purple background
                    background?.setStroke(eventStrokeWidth, color)
                    textView.setTextColor(color)
                    textView.typeface = Typeface.DEFAULT_BOLD
                }
            }
        }

        holder.dayCell.setOnClickListener {
            onDayClick(day)
        }
    }

    override fun getItemCount() = calendarDays.size

    fun updateData(newDays: List<NanakshahiCalendar.MonthlyDayCell>) {
        this.calendarDays = newDays
        notifyDataSetChanged()
    }
}
