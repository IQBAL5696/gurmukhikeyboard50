package com.iqbal.gurmukhikeyboard50

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class EquationLibraryAdapter(
    private val onSelect: (SavedEquation) -> Unit,
    private val onDelete: (SavedEquation) -> Unit
) : ListAdapter<SavedEquation, EquationLibraryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_equation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.equation_label)
        private val equation: TextView = view.findViewById(R.id.equation_text)
        private val deleteBtn: ImageButton = view.findViewById(R.id.delete_equation_button)

        fun bind(item: SavedEquation) {
            label.text = item.label
            equation.text = item.equation
            itemView.setOnClickListener { onSelect(item) }
            deleteBtn.setOnClickListener { onDelete(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SavedEquation>() {
        override fun areItemsTheSame(oldItem: SavedEquation, newItem: SavedEquation) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SavedEquation, newItem: SavedEquation) = oldItem == newItem
    }
}
