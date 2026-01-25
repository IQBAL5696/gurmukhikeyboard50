package com.iqbal.gurmukhikeyboard50

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmojiAdapter(
    private var items: List<Any>,
    private val onEmojiClicked: (String) -> Unit,
    private val isHeaderFn: (Any) -> Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EMOJI = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (isHeaderFn(items[position])) VIEW_TYPE_HEADER else VIEW_TYPE_EMOJI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.emoji_header_layout, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.emoji_item_layout, parent, false)
            EmojiViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is String) {
            holder.headerTextView.text = item
        } else if (holder is EmojiViewHolder && item is String) {
            holder.emojiTextView.text = item
            holder.itemView.setOnClickListener { onEmojiClicked(item) }
        }
    }

    override fun getItemCount(): Int = items.size

    fun isHeader(position: Int): Boolean {
        return getItemViewType(position) == VIEW_TYPE_HEADER
    }

    fun updateEmojis(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerTextView: TextView = itemView.findViewById(R.id.emoji_header_text)
    }

    class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiTextView: TextView = itemView.findViewById(R.id.emoji_text_view)
    }
}
