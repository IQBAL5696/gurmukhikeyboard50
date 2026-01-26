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

class MonthlyCalendarAdapter(
    private val context: Context,
    private var calendarDays: List<NanakshahiCalendar.MonthlyDayCell>,
    private val onDayClick: (NanakshahiCalendar.MonthlyDayCell) -> Unit
) : RecyclerView.Adapter<MonthlyCalendarAdapter.DayViewHolder>() {

    inner class DayViewHolder(val dayCell: View) : RecyclerView.ViewHolder(dayCell)

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
        textView.setTextColor(Color.BLACK)
        textView.typeface = Typeface.DEFAULT

        // Default background color for cells
        background?.setColor(Color.parseColor("#FFFFFF"))
        background?.setStroke(1, Color.LTGRAY)

        // Hide indicator by default
        indicator?.visibility = View.GONE
        
        // 🌟 TODAY STYLING: Full colorful background
        if (day.isToday) {
            background?.setColor(Color.parseColor("#EF6C00")) // Solid Orange background
            background?.setStroke(0, Color.TRANSPARENT)
            textView.setTextColor(Color.WHITE) // White text for readability
            textView.typeface = Typeface.DEFAULT_BOLD
            
            holder.dayCell.setOnClickListener { onDayClick(day) }
            return // Skip further styling if it's today
        }

        // Apply colors and styling based on importance (for other days)
        when {
            day.gurpurabName != null -> {
                val color = day.gurpurabColor ?: Color.parseColor("#E65100")
                background?.setColor(Color.parseColor("#FFF3E0")) // Light orange background
                background?.setStroke(3, color)
                textView.setTextColor(color)
                textView.typeface = Typeface.DEFAULT_BOLD
            }
            day.isSangrand -> {
                val color = Color.parseColor("#2E7D32")
                background?.setColor(Color.parseColor("#E8F5E9")) // Light green background
                background?.setStroke(3, color)
                textView.setTextColor(color)
                textView.typeface = Typeface.DEFAULT_BOLD
            }
            day.isPunia -> {
                val color = Color.parseColor("#1565C0")
                background?.setColor(Color.parseColor("#E3F2FD"))
                background?.setStroke(3, color)
                textView.setTextColor(color)
            }
            day.isMasaya -> {
                val color = Color.parseColor("#7B1FA2")
                background?.setColor(Color.parseColor("#F3E5F5"))
                background?.setStroke(3, color)
                textView.setTextColor(color)
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
