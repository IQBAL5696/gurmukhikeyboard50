package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class CalculatorHistoryActivity : AppCompatActivity() {

    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private var history: ArrayList<Calculation>? = null
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator_history)

        val root = findViewById<android.view.View>(R.id.history_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        @Suppress("DEPRECATION")
        history = intent.getSerializableExtra("history") as? ArrayList<Calculation>

        updateTitleCount()

        historyRecyclerView = findViewById(R.id.history_recycler_view)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        
        history?.let { list ->
            historyAdapter = HistoryAdapter(list) { selected ->
                showCorrectionDialog(selected, list.indexOf(selected))
            }
            historyRecyclerView.adapter = historyAdapter
        }

        findViewById<Button>(R.id.button_clear_history).setOnClickListener {
            history?.clear()
            historyAdapter.notifyDataSetChanged()
            updateTitleCount()
            sendUpdateAndFinish(true)
        }

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e2.x - e1.x > 150 && abs(velocityX) > 200) {
                    sendUpdateAndFinish()
                    return true
                }
                return false
            }
        })
    }

    private fun updateTitleCount() {
        findViewById<TextView>(R.id.tv_history_title).text = "ਕੁੱਲ ਕੈਲਕੁਲੇਸ਼ਨ: ${history?.size ?: 0}"
    }

    private fun showCorrectionDialog(calc: Calculation, position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_correct_calc, null)
        val editText = dialogView.findViewById<EditText>(R.id.edit_expression)
        val tvPreview = dialogView.findViewById<TextView>(R.id.tv_preview_result)
        
        editText.setText(calc.expression)
        editText.setSelection(editText.text.length)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newExpr = s.toString()
                val evalResult = CalculatorHelper.evaluate(newExpr)
                if (evalResult != null && evalResult.contains("=")) {
                    val res = evalResult.split("=").last()
                    tvPreview.text = "= $res"
                } else {
                    tvPreview.text = "ਗਲਤ ਇਕੁਏਸ਼ਨ"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        editText.addTextChangedListener(watcher)
        
        tvPreview.text = "= ${calc.result}"

        AlertDialog.Builder(this)
            .setTitle("ਕੈਲਕੁਲੇਸ਼ਨ ਸੋਧੋ")
            .setView(dialogView)
            .setPositiveButton("ਅਪਡੇਟ ਕਰੋ") { _, _ ->
                val newExpr = editText.text.toString()
                val evalResult = CalculatorHelper.evaluate(newExpr)
                if (evalResult != null && evalResult.contains("=")) {
                    val newResult = evalResult.split("=").last()
                    val updatedCalc = Calculation(newExpr, newResult)
                    history?.set(position, updatedCalc)
                    historyAdapter.notifyItemChanged(position)
                    sendUpdateAndFinish()
                } else {
                    Toast.makeText(this, "ਸੋਧ ਸਹੀ ਨਹੀਂ ਹੋਈ!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("ਕੈਲਕੁਲੇਟਰ 'ਚ ਵਰਤੋ") { _, _ ->
                val broadcastIntent = Intent("com.iqbal.gurmukhikeyboard50.CALC_UPDATE")
                broadcastIntent.putExtra("selected_result", calc.result)
                broadcastIntent.putExtra("updated_history", history)
                sendBroadcast(broadcastIntent)

                val resultIntent = Intent()
                resultIntent.putExtra("selected_result", calc.result)
                resultIntent.putExtra("updated_history", history)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ", null)
            .show()
    }

    private fun sendUpdateAndFinish(isCleared: Boolean = false) {
        val broadcastIntent = Intent("com.iqbal.gurmukhikeyboard50.CALC_UPDATE")
        broadcastIntent.putExtra("history_cleared", isCleared)
        broadcastIntent.putExtra("updated_history", history)
        sendBroadcast(broadcastIntent)

        val resultIntent = Intent()
        resultIntent.putExtra("history_cleared", isCleared)
        resultIntent.putExtra("updated_history", history)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(ev!!)
        return super.dispatchTouchEvent(ev)
    }
}
