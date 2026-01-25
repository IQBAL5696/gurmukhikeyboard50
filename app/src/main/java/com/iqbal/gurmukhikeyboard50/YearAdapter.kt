package com.iqbal.gurmukhikeyboard50

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class YearAdapter(
    private val years: List<Int>,
    private var selectedYear: Int,
    private val onYearSelected: (Int) -> Unit
) : RecyclerView.Adapter<YearAdapter.YearViewHolder>() {

    inner class YearViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val yearText: TextView = view.findViewById(R.id.yearText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): YearViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_year, parent, false)
        return YearViewHolder(view)
    }

    override fun onBindViewHolder(holder: YearViewHolder, position: Int) {
        val year = years[position]
        // Use toGurmukhiYear to correctly display BC/AD era
        holder.yearText.text = NanakshahiCalendar.toGurmukhiYear(year)

        if (year == selectedYear) {
            holder.yearText.setBackgroundResource(R.drawable.bg_year_selected)
            holder.yearText.setTextColor(Color.WHITE)
        } else {
            holder.yearText.setBackgroundColor(Color.parseColor("#33000000"))
            holder.yearText.setTextColor(Color.BLACK)
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
