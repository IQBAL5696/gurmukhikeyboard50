package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CalculatorHistoryActivity : AppCompatActivity() {

    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private var history: ArrayList<Calculation>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator_history)

        history = intent.getSerializableExtra("history") as? ArrayList<Calculation>

        historyRecyclerView = findViewById(R.id.history_recycler_view)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        history?.let {
            historyAdapter = HistoryAdapter(it)
            historyRecyclerView.adapter = historyAdapter
        }

        val clearHistoryButton: Button = findViewById(R.id.button_clear_history)
        clearHistoryButton.setOnClickListener {
            history?.clear()
            historyAdapter.notifyDataSetChanged()
            val resultIntent = Intent()
            resultIntent.putExtra("history_cleared", true)
            setResult(Activity.RESULT_OK, resultIntent)
        }
    }
}