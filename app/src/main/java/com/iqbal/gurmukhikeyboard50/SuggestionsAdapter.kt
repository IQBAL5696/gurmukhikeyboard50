package com.iqbal.gurmukhikeyboard50

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SuggestionsAdapter(
    private var suggestions: List<String>,
    private val listener: (String) -> Unit,
    private val longPressListener: (String) -> Unit
) : RecyclerView.Adapter<SuggestionsAdapter.SuggestionViewHolder>() {

    companion object {
        private const val TAG = "SuggestionsAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.suggestion_item_layout, parent, false)
        return SuggestionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        holder.bind(suggestion, listener, longPressListener)
    }

    override fun getItemCount(): Int = suggestions.size

    fun updateSuggestions(newSuggestions: List<String>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }

    class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.suggestion_text_view)

        fun bind(suggestion: String, listener: (String) -> Unit, longPressListener: (String) -> Unit) {
            textView.text = suggestion
            itemView.setOnClickListener { listener(suggestion) }
            itemView.setOnLongClickListener {
                Log.d(TAG, "Long pressed: $suggestion")
                longPressListener(suggestion)
                true
            }
        }
    }
}
