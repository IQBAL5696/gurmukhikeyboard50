package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearnedWordsActivity : AppCompatActivity() {

    private lateinit var adapter: LearnedWordsAdapter
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var rvWords: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learned_words)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        databaseHelper = DatabaseHelper(this)
        rvWords = findViewById(R.id.rv_learned_words)
        tvEmpty = findViewById(R.id.tv_empty_state)

        rvWords.layoutManager = LinearLayoutManager(this)
        adapter = LearnedWordsAdapter(mutableListOf()) { word ->
            deleteWord(word)
        }
        rvWords.adapter = adapter

        loadWords()
    }

    private fun loadWords() {
        lifecycleScope.launch {
            val words = withContext(Dispatchers.IO) {
                // Fetch words from Room database
                AppDatabase.getDatabase(this@LearnedWordsActivity).userWordDao().getAllWords()
            }
            if (words.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rvWords.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rvWords.visibility = View.VISIBLE
                adapter.updateWords(words)
            }
        }
    }

    private fun deleteWord(word: UserWord) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@LearnedWordsActivity).userWordDao().delete(word)
            }
            Toast.makeText(this@LearnedWordsActivity, "ਸ਼ਬਦ ਮਿਟਾ ਦਿੱਤਾ ਗਿਆ", Toast.LENGTH_SHORT).show()
            loadWords()
        }
    }
}
