package com.iqbal.gurmukhikeyboard50

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClipboardAdapter(
    private var clipboardItems: List<ClipboardItem>,
    private val listener: (String) -> Unit,
    private val selectionChangedListener: (Boolean) -> Unit,
    private val onDeleteClicked: (Long) -> Unit,
    private val onPinClicked: (Long, Boolean) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    private val selectedItems = mutableSetOf<Long>()
    var isSelectionMode = false
        private set

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.clipboard_item_text)
        val checkBox: CheckBox = itemView.findViewById(R.id.clipboard_item_checkbox)
        val pinButton: ImageButton = itemView.findViewById(R.id.pin_item_button)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_single_item_button)

        init {
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(adapterPosition)
                } else {
                    listener(clipboardItems[adapterPosition].text)
                }
            }
            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    setSelectionMode(true)
                    toggleSelection(adapterPosition)
                }
                true
            }
            pinButton.setOnClickListener {
                val item = clipboardItems[adapterPosition]
                onPinClicked(item.id, !item.isPinned)
            }
            deleteButton.setOnClickListener {
                onDeleteClicked(clipboardItems[adapterPosition].id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.clipboard_item_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = clipboardItems[position]
        holder.textView.text = item.text
        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedItems.contains(item.id)
        
        // Show pinned status
        if (item.isPinned) {
            holder.pinButton.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            holder.pinButton.setImageResource(android.R.drawable.btn_star_big_off)
        }
    }

    override fun getItemCount(): Int = clipboardItems.size

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedItems.clear()
            }
            selectionChangedListener(enabled)
            notifyDataSetChanged()
        }
    }

    private fun toggleSelection(position: Int) {
        val id = clipboardItems[position].id
        if (selectedItems.contains(id)) {
            selectedItems.remove(id)
        } else {
            selectedItems.add(id)
        }
        if (selectedItems.isEmpty()) {
            setSelectionMode(false)
        }
        notifyItemChanged(position)
    }

    fun getSelectedItems(): List<Long> {
        return selectedItems.toList()
    }

    fun updateItems(newItems: List<ClipboardItem>) {
        clipboardItems = newItems
        notifyDataSetChanged()
    }
}
