package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PhrasesPanel(
    private val context: Context,
    private val onPhraseSelected: (String) -> Unit,
    private val onClose: () -> Unit
) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.phrases_panel_layout, null)
    private val recyclerView: RecyclerView = view.findViewById(R.id.phrases_recycler_view)
    private val closeButton: ImageButton = view.findViewById(R.id.phrases_panel_close)
    private val btnManageShortcuts: Button = view.findViewById(R.id.btn_manage_shortcuts)

    private var adapter: PhrasesAdapter? = null

    init {
        recyclerView.layoutManager = LinearLayoutManager(context)
        refreshShortcuts()

        closeButton.setOnClickListener {
            onClose()
        }

        btnManageShortcuts.setOnClickListener {
            try {
                val intent = Intent(context, ShortcutsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshShortcuts() {
        val shortcutsMap = ShortcutsManager.getAllShortcuts(context)
        val items = shortcutsMap.map { (key, value) -> ShortcutItem(key, value) }
        adapter = PhrasesAdapter(items) { selectedValue ->
            onPhraseSelected(selectedValue)
        }
        recyclerView.adapter = adapter
    }

    data class ShortcutItem(val key: String, val value: String)

    private class PhrasesAdapter(
        private val items: List<ShortcutItem>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<PhrasesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvValue: TextView = view.findViewById(android.R.id.text1)
            val tvKey: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvValue.text = item.value
            holder.tvKey.text = "ਸ਼ੌਰਟਕਟ: ${item.key}"
            holder.itemView.setOnClickListener {
                onClick(item.value)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}