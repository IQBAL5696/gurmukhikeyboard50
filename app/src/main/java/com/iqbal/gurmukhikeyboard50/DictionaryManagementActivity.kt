package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictionaryManagementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var dictionaryAdapter: DictionaryAdapter
    private lateinit var btnClearAll: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary_management)

        recyclerView = findViewById(R.id.dictionary_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        btnClearAll = findViewById(R.id.btn_clear_all)
        btnClearAll.setOnClickListener {
            showClearAllConfirmation()
        }

        loadDictionaryWords()
    }

    private fun loadDictionaryWords() {
        CoroutineScope(Dispatchers.IO).launch {
            val words = DictionaryHelper.getAllDictionaryWords(this@DictionaryManagementActivity)
            withContext(Dispatchers.Main) {
                dictionaryAdapter = DictionaryAdapter(words.toMutableList()) { word ->
                    deleteWord(word)
                }
                recyclerView.adapter = dictionaryAdapter
            }
        }
    }

    private fun showClearAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("ਸਭ ਸਾਫ਼ ਕਰੋ?")
            .setMessage("ਕੀ ਤੁਸੀਂ ਡਿਕਸ਼ਨਰੀ ਵਿੱਚੋਂ ਸਾਰੇ ਸਿੱਖੇ ਹੋਏ ਸ਼ਬਦ ਹਟਾਉਣਾ ਚਾਹੁੰਦੇ ਹੋ?")
            .setPositiveButton("ਹਾਂ, ਸਾਫ਼ ਕਰੋ") { _, _ ->
                clearAllWords()
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ", null)
            .show()
    }

    private fun clearAllWords() {
        CoroutineScope(Dispatchers.IO).launch {
            val dbHelper = DatabaseHelper(this@DictionaryManagementActivity)
            dbHelper.deleteAllLearnedWords()
            withContext(Dispatchers.Main) {
                loadDictionaryWords()
                Toast.makeText(this@DictionaryManagementActivity, "ਡਿਕਸ਼ਨਰੀ ਸਾਫ਼ ਕਰ ਦਿੱਤੀ ਗਈ ਹੈ।", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteWord(word: String) {
        CoroutineScope(Dispatchers.IO).launch {
            DictionaryHelper.deleteWordFromDictionary(this@DictionaryManagementActivity, word)
            withContext(Dispatchers.Main) {
                dictionaryAdapter.removeWord(word)
                Toast.makeText(this@DictionaryManagementActivity, "ਸ਼ਬਦ ਹਟਾ ਦਿੱਤਾ ਗਿਆ ਹੈ।", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
