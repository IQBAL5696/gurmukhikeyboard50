package com.iqbal.gurmukhikeyboard50

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(private val history: List<Calculation>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calculation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val calculation = history[position]
        holder.expression.text = calculation.expression
        holder.result.text = calculation.result
    }

    override fun getItemCount() = history.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val expression: TextView = itemView.findViewById(R.id.expression)
        val result: TextView = itemView.findViewById(R.id.result)
    }
}