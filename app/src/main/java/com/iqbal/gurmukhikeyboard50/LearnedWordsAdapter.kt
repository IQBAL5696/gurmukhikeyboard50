package com.iqbal.gurmukhikeyboard50

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LearnedWordsAdapter(
    private var words: MutableList<UserWord>,
    private val onDeleteClick: (UserWord) -> Unit
) : RecyclerView.Adapter<LearnedWordsAdapter.WordViewHolder>() {

    class WordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvWord: TextView = view.findViewById(R.id.tv_word_text)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_word)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_learned_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val word = words[position]
        holder.tvWord.text = word.word
        holder.btnDelete.setOnClickListener { onDeleteClick(word) }
    }

    override fun getItemCount() = words.size

    fun updateWords(newWords: List<UserWord>) {
        words.clear()
        words.addAll(newWords)
        notifyDataSetChanged()
    }
}
