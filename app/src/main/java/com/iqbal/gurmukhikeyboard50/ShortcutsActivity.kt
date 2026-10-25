package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ShortcutsActivity : AppCompatActivity() {

    private lateinit var etKey: EditText
    private lateinit var etValue: EditText
    private lateinit var btnAdd: Button
    private lateinit var lvShortcuts: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val shortcutList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcuts)

        etKey = findViewById(R.id.et_shortcut_key)
        etValue = findViewById(R.id.et_shortcut_value)
        btnAdd = findViewById(R.id.btn_add_shortcut)
        lvShortcuts = findViewById(R.id.lv_shortcuts)

        refreshList()

        btnAdd.setOnClickListener {
            val key = etKey.text.toString().trim()
            val value = etValue.text.toString().trim()

            if (key.isNotEmpty() && value.isNotEmpty()) {
                ShortcutsManager.addShortcut(this, key, value)
                etKey.setText(""); etValue.setText("")
                refreshList()
                Toast.makeText(this, "ਸ਼ੌਰਟਕਟ ਸੇਵ ਹੋ ਗਿਆ", Toast.LENGTH_SHORT).show()
            }
        }

        lvShortcuts.setOnItemClickListener { _, _, position, _ ->
            if (position in shortcutList.indices) {
                val item = shortcutList[position]
                val parts = item.split(" -> ", limit = 2)
                if (parts.size == 2) {
                    etKey.setText(parts[0])
                    etValue.setText(parts[1])
                }
            }
        }

        lvShortcuts.setOnItemLongClickListener { _, _, position, _ ->
            if (position in shortcutList.indices) {
                val item = shortcutList[position]
                val key = item.split(" -> ", limit = 2)[0]
                ShortcutsManager.deleteShortcut(this, key)
                refreshList()
                Toast.makeText(this, "ਮਿਟਾ ਦਿੱਤਾ ਗਿਆ", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun refreshList() {
        shortcutList.clear()
        val all = ShortcutsManager.getAllShortcuts(this)
        all.forEach { (k, v) -> shortcutList.add("$k -> $v") }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, shortcutList)
        lvShortcuts.adapter = adapter
    }
}
