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

class YearAdapter(
    private val years: List<Int>,
    private var selectedYear: Int,
    private val onYearSelected: (Int) -> Unit
) : RecyclerView.Adapter<YearAdapter.YearViewHolder>() {

    private var customTypeface: Typeface? = null
    private var isBoldMode = false

    // Harmonious, Graceful Warm Palette
    private val colorPalette = listOf(
        "#FFF9F0", // Royal Ivory
        "#FFF3E0", // Soft Peach
        "#FFFDE7", // Creamy Yellow
        "#FFF8E1", // Light Amber
        "#FFF1E1"  // Warm Pearl
    )

    private val strokeColors = listOf(
        "#F5E6D3", "#FFE0B2", "#FFF9C4", "#FFECB3", "#F5DEC1"
    )

    inner class YearViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val yearText: TextView = view.findViewById(R.id.yearText)
    }

    private fun loadCustomFont(context: Context) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): YearViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_year, parent, false)
        if (customTypeface == null) {
            loadCustomFont(parent.context)
        }
        return YearViewHolder(view)
    }

    override fun onBindViewHolder(holder: YearViewHolder, position: Int) {
        val year = years[position]
        holder.yearText.text = NanakshahiCalendar.toGurmukhiYear(year)
        
        // Apply custom font
        customTypeface?.let { holder.yearText.typeface = it }
        holder.yearText.paint.isFakeBoldText = isBoldMode

        val shape = GradientDrawable()
        shape.cornerRadius = 28f // Extra rounded for elegance

        if (year == selectedYear) {
            // Selected: Premium Gold/Orange Gradient with Ring effect
            shape.colors = intArrayOf(
                Color.parseColor("#D84315"), // Deep Burnt Orange
                Color.parseColor("#EF6C00"), // Theme Orange
                Color.parseColor("#FF9800")  // Vibrant Gold
            )
            shape.orientation = GradientDrawable.Orientation.TL_BR
            shape.setStroke(6, Color.parseColor("#40FFFFFF")) // Soft White Inner Ring
            
            holder.yearText.background = shape
            holder.yearText.setTextColor(Color.WHITE)
            holder.yearText.elevation = 10f
            holder.yearText.scaleX = 1.1f
            holder.yearText.scaleY = 1.1f
        } else {
            // Unselected: Soft, Graceful Palette
            val colorIndex = position % colorPalette.size
            shape.setColor(Color.parseColor(colorPalette[colorIndex]))
            shape.setStroke(3, Color.parseColor(strokeColors[colorIndex]))
            
            holder.yearText.background = shape
            holder.yearText.setTextColor(Color.parseColor("#5D4037")) // Professional Brown
            holder.yearText.elevation = 3f
            holder.yearText.scaleX = 1.0f
            holder.yearText.scaleY = 1.0f
        }

        holder.itemView.setOnClickListener {
            val oldSelected = selectedYear
            if (oldSelected == year) return@setOnClickListener

            selectedYear = year
            val oldIndex = years.indexOf(oldSelected)
            if (oldIndex != -1) {
                notifyItemChanged(oldIndex)
            }
            notifyItemChanged(position)
            onYearSelected(year)
        }
    }

    override fun getItemCount(): Int = years.size
}
