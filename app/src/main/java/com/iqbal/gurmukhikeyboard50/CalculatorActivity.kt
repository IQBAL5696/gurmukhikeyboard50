package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CalculatorActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private var currentInput = ""
    private var currentOperator = ""
    private var operand1: Double? = null
    private val history = ArrayList<Calculation>()
    private val HISTORY_REQUEST_CODE = 1

    companion object {
        const val ACTION_CALCULATOR_RESULT = "com.iqbal.gurmukhikeyboard50.CALCULATOR_RESULT"
        const val EXTRA_RESULT = "result"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator)

        display = findViewById(R.id.calculator_display)

        val checkButton: ImageButton = findViewById(R.id.button_check)
        checkButton.setOnClickListener {
            val intent = Intent(ACTION_CALCULATOR_RESULT)
            intent.putExtra(EXTRA_RESULT, display.text.toString())
            sendBroadcast(intent)
            finish()
        }

        val buttons = listOf<Button>(
            findViewById(R.id.button_0),
            findViewById(R.id.button_1),
            findViewById(R.id.button_2),
            findViewById(R.id.button_3),
            findViewById(R.id.button_4),
            findViewById(R.id.button_5),
            findViewById(R.id.button_6),
            findViewById(R.id.button_7),
            findViewById(R.id.button_8),
            findViewById(R.id.button_9),
            findViewById(R.id.button_dot),
            findViewById(R.id.button_add),
            findViewById(R.id.button_subtract),
            findViewById(R.id.button_multiply),
            findViewById(R.id.button_divide),
            findViewById(R.id.button_equals),
            findViewById(R.id.button_clear),
            findViewById(R.id.button_percent),
            findViewById(R.id.button_backspace),
            findViewById(R.id.button_tax_5),
            findViewById(R.id.button_tax_18),
            findViewById(R.id.button_history)
        )

        for (button in buttons) {
            button.setOnClickListener { onButtonClick(it) }
        }
    }

    private fun onButtonClick(view: View) {
        val button = view as Button
        val buttonText = button.text.toString()

        when {
            buttonText.matches(Regex("[0-9.]")) -> {
                currentInput += buttonText
                display.text = currentInput
            }
            buttonText.matches(Regex("[+*/-]")) -> {
                if (currentInput.isNotEmpty()) {
                    operand1 = currentInput.toDouble()
                    currentInput = ""
                }
                currentOperator = buttonText
            }
            buttonText == "=" -> {
                if (currentInput.isNotEmpty() && currentOperator.isNotEmpty() && operand1 != null) {
                    val operand2 = currentInput.toDouble()
                    val result = calculate(operand1!!, operand2, currentOperator)
                    val expression = "$operand1 $currentOperator $operand2"
                    history.add(Calculation(expression, result.toString()))
                    display.text = result.toString()
                    currentInput = result.toString()
                    operand1 = null
                    currentOperator = ""
                }
            }
            buttonText == "Clear" -> {
                currentInput = ""
                currentOperator = ""
                operand1 = null
                display.text = ""
            }
            buttonText == "%" -> {
                if (currentInput.isNotEmpty()) {
                    val operand2 = currentInput.toDouble()
                    if (operand1 != null && currentOperator.isNotEmpty()) {
                        val finalOperand2 = if (currentOperator == "+" || currentOperator == "-") {
                            (operand2 / 100.0) * operand1!!
                        } else { // for * and /
                            operand2 / 100.0
                        }
                        val result = calculate(operand1!!, finalOperand2, currentOperator)
                        val expression = "$operand1 $currentOperator $operand2%"
                        history.add(Calculation(expression, result.toString()))
                        display.text = result.toString()
                        currentInput = result.toString()
                        operand1 = null
                        currentOperator = ""
                    } else {
                        val result = operand2 / 100.0
                        currentInput = result.toString()
                        display.text = currentInput
                    }
                }
            }
            buttonText == "⌫" -> {
                if (currentInput.isNotEmpty()) {
                    currentInput = currentInput.substring(0, currentInput.length - 1)
                    display.text = currentInput
                }
            }
            buttonText == "5% Tax" -> {
                calculateTax(5.0)
            }
            buttonText == "18% Tax" -> {
                calculateTax(18.0)
            }
            buttonText == "History" -> {
                val intent = Intent(this, CalculatorHistoryActivity::class.java)
                intent.putExtra("history", history)
                startActivityForResult(intent, HISTORY_REQUEST_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HISTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data?.getBooleanExtra("history_cleared", false) == true) {
                history.clear()
            }
        }
    }

    private fun calculate(operand1: Double, operand2: Double, operator: String): Double {
        return when (operator) {
            "+" -> operand1 + operand2
            "-" -> operand1 - operand2
            "*" -> operand1 * operand2
            "/" -> operand1 / operand2
            else -> 0.0
        }
    }

    private fun calculateTax(taxRate: Double) {
        if (currentInput.isNotEmpty()) {
            val value = currentInput.toDouble()
            val taxAmount = value * (taxRate / 100)
            val total = value + taxAmount
            val expression = "$value + $taxRate%"
            history.add(Calculation(expression, total.toString()))
            currentInput = total.toString()
            display.text = currentInput
        }
    }
}