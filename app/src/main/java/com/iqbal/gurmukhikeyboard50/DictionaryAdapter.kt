package com.iqbal.gurmukhikeyboard50

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DictionaryAdapter(
    private val words: MutableList<String>,
    private val onDeleteClickListener: (String) -> Unit
) : RecyclerView.Adapter<DictionaryAdapter.WordViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.dictionary_item_layout, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val word = words[position]
        holder.wordTextView.text = word
        holder.deleteButton.setOnClickListener {
            onDeleteClickListener(word)
        }
    }

    override fun getItemCount(): Int = words.size

    fun removeWord(word: String) {
        val index = words.indexOf(word)
        if (index != -1) {
            words.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val wordTextView: TextView = itemView.findViewById(R.id.word_text_view)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
    }
}
