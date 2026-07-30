package com.iqbal.gurmukhikeyboard50

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.*
import android.text.Layout

class NitnemEngine(private val context: Context, private val onDismiss: () -> Unit) {
    private val nitnemPrefs: SharedPreferences = context.getSharedPreferences("NitnemPrefs", Context.MODE_PRIVATE)
    private var savedSpeedLevel: Int = nitnemPrefs.getInt("saved_speed_level", 3)
    private var isSwitchingBani = false
    private var isLarivaarGlobal = false
    private var japjiScrollJob: Job? = null
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private var mainNitnemView: View? = null

    private var isDarkMode: Boolean
        get() = nitnemPrefs.getBoolean("is_dark_mode", false)
        set(value) = nitnemPrefs.edit().putBoolean("is_dark_mode", value).apply()
    private fun getBgColor() = if (isDarkMode) Color.parseColor("#000000") else Color.parseColor("#FFF3E0")
    private fun getTextColor() = if (isDarkMode) Color.WHITE else Color.parseColor("#3E2723")
    private fun getGurbaniNormalColor() = if (isDarkMode) Color.parseColor("#E0E0E0") else Color.parseColor("#B71C1C")
    private fun getGurbaniHighlightColor() = if (isDarkMode) Color.parseColor("#FFB74D") else Color.parseColor("#0D47A1")
    private fun getPageNumberColor() = if (isDarkMode) Color.parseColor("#FFB74D") else Color.parseColor("#0D47A1")
    private fun getStripColor() = if (isDarkMode) Color.parseColor("#121212") else Color.parseColor("#F5F5F5")


    private fun getFrameColor() = if (isDarkMode) Color.parseColor("#424242") else Color.parseColor("#8D6E63")
    private fun getFrameHighlightColor() = if (isDarkMode) Color.parseColor("#616161") else Color.parseColor("#D7CCC8")

    private fun createPhotoFrameDrawable(bgColor: Int): android.graphics.drawable.Drawable {
        val radius = 16.toPx().toFloat()
        val outerFrame = GradientDrawable().apply {
            setColor(bgColor)
            setStroke(12.toPx(), getFrameColor())
            cornerRadius = radius
        }
        val innerFrame = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(2.toPx(), getFrameHighlightColor())
            cornerRadius = radius - 4.toPx()
        }

        return android.graphics.drawable.LayerDrawable(arrayOf(outerFrame, innerFrame)).apply {
            setLayerInset(1, 10.toPx(), 10.toPx(), 10.toPx(), 10.toPx())
        }
    }

    data class BaniItem(val name: String, val folder: String)
    data class PdfBook(val name: String, val url: String, val fileName: String)

    fun stopMusic() {
        japjiScrollJob?.cancel()
        japjiScrollJob = null
    }

    private fun getCustomTypeface(): Typeface {
        val fontFileName = "NotoSerifGurmukhi-Regular.ttf"
        return try {
            Typeface.createFromAsset(context.assets, "fonts/$fontFileName")
        } catch (e: Exception) {
            try {
                Typeface.createFromAsset(context.assets, "fonts/AKHAR.TTF")
            } catch (e2: Exception) { Typeface.DEFAULT }
        }
    }

    private fun setupDialogWindow(dial: AlertDialog, focusable: Boolean = true, anchorView: View?) {
        dial.window?.let { window ->
            if (context !is android.app.Activity) {
                if (focusable) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                val token = anchorView?.windowToken
                if (token != null) {
                    window.attributes.token = token
                    window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
                } else {
                    @Suppress("DEPRECATION")
                    window.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                }
            }
        }
    }

    private fun applyPanelTheme() {
        mainNitnemView?.let { view ->
            val recyclerView = view.findViewById<RecyclerView>(R.id.nitnem_recycler_view)
            val closeBtn = view.findViewById<ImageButton>(R.id.btn_close_nitnem)
            val themeBtn = view.findViewById<ImageButton>(R.id.btn_theme_nitnem)
            val titleTv = view.findViewById<TextView>(R.id.nitnem_title)

            view.background = createPhotoFrameDrawable(getBgColor())

            val titleContainer = titleTv?.parent as? LinearLayout
            titleContainer?.let { container ->
                val gd = GradientDrawable().apply {
                    setColor(getFrameColor())
                    val r = 16.toPx().toFloat()
                    setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
                }
                container.background = gd
                container.setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
                val lp = container.layoutParams as? LinearLayout.LayoutParams
                lp?.setMargins(0, 0, 0, 0)

                // Split title properly to handle more than 2 words (e.g., "ਆਸਾ ਦੀ ਵਾਰ")
                val fullTitle = "ਨਿਤਨੇਮ ਅਤੇ ਬਾਣੀਆਂ"
                val parts = fullTitle.split(" ")
                if (parts.size >= 2) {
                    val mid = (parts.size + 1) / 2
                    titleTv?.text = parts.take(mid).joinToString(" ")
                    titleTv?.gravity = Gravity.START
                    // Try to find if we already added the right part
                    var rightTv = container.findViewWithTag<TextView>("right_title")
                    if (rightTv == null) {
                        rightTv = TextView(context).apply {
                            tag = "right_title"
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                            gravity = Gravity.END
                            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
                            typeface = getCustomTypeface()
                            textSize = 18f
                        }
                        container.addView(rightTv, container.indexOfChild(titleTv) + 1)
                    }
                    rightTv.text = parts.drop(mid).joinToString(" ")
                }
            }

            titleTv?.setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            titleTv?.typeface = getCustomTypeface()
            titleTv?.textSize = 18f

            val searchBtn = mainNitnemView?.findViewById<ImageButton>(R.id.btn_search_nitnem)
            searchBtn?.setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            themeBtn?.setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            closeBtn?.setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))

            recyclerView?.setPadding(12.toPx(), 12.toPx(), 12.toPx(), 100.toPx())
            recyclerView?.clipToPadding = false
            recyclerView?.adapter?.notifyDataSetChanged()
        }
    }

    fun setupNitnemPanel(nitnemView: View) {
        this.mainNitnemView = nitnemView
        nitnemView.setPadding(0, 0, 0, 0)
        val recyclerView = nitnemView.findViewById<RecyclerView>(R.id.nitnem_recycler_view)
        val closeBtn = nitnemView.findViewById<ImageButton>(R.id.btn_close_nitnem)
        val themeBtn = nitnemView.findViewById<ImageButton>(R.id.btn_theme_nitnem)
        val searchBtn = nitnemView.findViewById<ImageButton>(R.id.btn_search_nitnem)

        applyPanelTheme()

        themeBtn?.setOnClickListener {
            showNitnemSettingsDialog(nitnemView)
        }

        searchBtn?.setOnClickListener {
            showGurbaniSearchDialog(nitnemView)
        }

        closeBtn?.setOnClickListener { stopMusic(); onDismiss() }

        val defaultList = mutableListOf(
            BaniItem("ਗੁਰਬਾਣੀ ਖੋਜ (ਸਰਚ)", "search_gurbani"),
            BaniItem("ਜਪੁਜੀ ਸਾਹਿਬ", "japji_sahib"), BaniItem("ਸ਼ਬਦ ਹਜਾਰੇ", "shabad-hazare"), BaniItem("ਜਾਪੁ ਸਾਹਿਬ", "jap_sahib"),
            BaniItem("ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ", "swaye"), BaniItem("ਸਵੱਯੇ (ਦੀਨਨ ਕੀ)", "savaiye-deenan"), BaniItem("ਚੌਪਈ ਸਾਹਿਬ", "chaupai"),
            BaniItem("ਅਨੰਦ ਸਾਹਿਬ", "anand_sahib"), BaniItem("ਆਸਾ ਦੀ ਵਾਰ", "asa-di-var"), BaniItem("ਬਾਰਹ ਮਾਹਾ", "barah-maha"),
            BaniItem("ਰਹਿਰਾਸ ਸਾਹਿਬ", "rehras_sahib"), BaniItem("ਸੋਹਿਲਾ ਸਾਹਿਬ", "sohila_sahib"), BaniItem("ਆਰਤੀ", "aarti"),
            BaniItem("ਸਲੋਕ ਮਃ ੯", "salok-m-9"), BaniItem("ਸ਼ਬਦ ਪਾ ੧੦", "shabad-p-10"), BaniItem("ਦੁਖ ਭੰਜਨੀ ਸਾਹਿਬ", "dukh-bhanjani-sahib"),
            BaniItem("ਰਖਿਆ ਦੇ ਸ਼ਬਦ", "rakhya-de-shabad"), BaniItem("ਅਰਦਾਸ", "ardas"), BaniItem("ਅਕਾਲ ਉਸਤਤਿ", "akal-ustat"),
            BaniItem("ਸੁਖਮਨੀ ਸਾਹਿਬ", "sukhmani_sahib"), BaniItem("ਵਾਰਾਂ ਭਾਈ ਗੁਰਦਾਸ ਜੀ", "vaara_bhai_gurdas"),
            BaniItem("ਕਬਿੱਤ ਸਵੱਯੇ ਭਾਈ ਗੁਰਦਾਸ ਜੀ", "kabit_sawaye_bhai_gurdas"),
            BaniItem("ਬਹੁਮੁੱਲੀਆਂ ਪੁਸਤਕਾਂ (PDF)", "pdf_books"),
            BaniItem("ਗੁਰੂ ਸਾਹਿਬਾਨ: ਮਹੱਤਵਪੂਰਨ ਤਾਰੀਖਾਂ", "gurus_info"),
            BaniItem("ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ", "guru_granth_sahib"),
            BaniItem("ਸਿੱਖੀ (ਵਿਚਾਰ ਚਰਚਾ)", "sikhi")
        )
        val savedOrder = nitnemPrefs.getString("bani_order", null)
        val displayList = if (savedOrder != null) {
            val orderFolders = savedOrder.split(","); val newList = mutableListOf<BaniItem>()
            orderFolders.forEach { folder -> defaultList.find { it.folder == folder }?.let { newList.add(it) } }
            defaultList.forEach { item -> if (!newList.contains(item)) newList.add(item) }; newList
        } else defaultList

        val mainAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val btn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.toPx(), 0, 4.toPx()) }
                    setBackgroundResource(R.drawable.rounded_button_bg); setTextColor(Color.WHITE); setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx()); transformationMethod = null; typeface = getCustomTypeface(); textSize = 16f
                }; return object : RecyclerView.ViewHolder(btn) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = displayList[position]; (holder.itemView as Button).apply {
                    text = item.name; typeface = getCustomTypeface()
                    setOnClickListener {
                        when (item.folder) {
                            "search_gurbani" -> showGurbaniSearchDialog(nitnemView)
                            "guru_granth_sahib" -> showRagasDialog(nitnemView)
                            "vaara_bhai_gurdas" -> showVaaraPartsDialog(nitnemView)
                            "kabit_sawaye_bhai_gurdas" -> showKabitPartsDialog(nitnemView)
                            "pdf_books" -> showBooksDialog(nitnemView)
                            "gurus_info" -> showGurusInfoDialog(nitnemView)
                            else -> showGurbaniDialog(item.name, item.folder, nitnemView)
                        }
                    }
                }
            }
            override fun getItemCount() = displayList.size
        }

        recyclerView.setPadding(0, 0, 0, 100.toPx())
        recyclerView.clipToPadding = false
        recyclerView.layoutManager = LinearLayoutManager(context); recyclerView.adapter = mainAdapter

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = vh.bindingAdapterPosition; val toPos = target.bindingAdapterPosition
                Collections.swap(displayList, fromPos, toPos); mainAdapter.notifyItemMoved(fromPos, toPos); return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh); val order = displayList.joinToString(",") { it.folder }
                nitnemPrefs.edit().putString("bani_order", order).apply()
            }
        }).attachToRecyclerView(recyclerView)
    }

    private fun showRagasDialog(anchorView: View?) {
        val ragas = listOf(
            BaniItem("ਜਪੁਜੀ ਸਾਹਿਬ, ਰਹਿਰਾਸ ਤੇ ਸੋਹਿਲਾ ਅੰਗ ੧ ਤੋਂ ੧੩", "japji_sahib"),
            BaniItem("੧. ਸਿਰੀਰਾਗੁ-ਅੰਗ ੧੪ ਤੋਂ ੩੭", "sri_raag"),
            BaniItem("੧. ਸਿਰੀਰਾਗੁ-ਅੰਗ ੩੮ ਤੋਂ ੬੬", "sri_raag_bhag_pehla"),
            BaniItem("੧. ਸਿਰੀਰਾਗੁ-ਅੰਗ ੬੭ ਤੋਂ ੯੩", "sri_raag_bhag_duja"),
            BaniItem("੨. ਮਾਝ-ਅੰਗ ੯੪ ਤੋਂ ੧੨੪", "raag_maajh"),
            BaniItem("੨. ਮਾਝ-ਅੰਗ ੧੨੫ ਤੋਂ ੧੫੦", "raag_maajh_bhag_pehla"),
            BaniItem("੩. ਗਉੜੀ-ਅੰਗ ੧੫੧ ਤੋਂ ੨੦੦", "raag_gauri"),
            BaniItem("੩. ਗਉੜੀ-ਅੰਗ ੨੦੧ ਤੋਂ ੨੪੮", "raag_gauri_bhag_pehla"),
            BaniItem("੩. ਗਉੜੀ-ਅੰਗ ੨੪੯ ਤੋਂ ੨੯੬", "raag_gauri_bhag_duja"),
            BaniItem("੩. ਗਉੜੀ-ਅੰਗ ੨੯੭ ਤੋਂ ੩੪੬", "raag_gauri_bhag_teeja"),
            BaniItem("੪. ਆਸਾ ਅੰਗ ੩੪੭ ਤੋਂ ੩੮੨", "raag_asa"),
            BaniItem("੪. ਆਸਾ-ਅੰਗ ੩੮੩ ਤੋਂ ੪੧੬", "raag_asa_bhag_pehla"),
            BaniItem("੪. ਆਸਾ-ਅੰਗ ੪੧੭ ਤੋਂ ੪੫੨", "raag_asa_bhag_duja"),
            BaniItem("੪. ਆਸਾ-ਅੰਗ ੪੫੩ ਤੋਂ ੪੮੮", "raag_asa_bhag_teeja"),
            BaniItem("੫. ਗੂਜਰੀ-ਅੰਗ ੪੮੯ ਤੋਂ ੫੨੬", "raag_gujri"),
            BaniItem("੬. ਦੇਵਗੰਧਾਰੀ-ਅੰਗ ੫੨੭ ਤੋਂ ੫੩੬", "raag_devgandhari"),
            BaniItem("੭. ਬਿਹਾਗੜਾ-ਅੰਗ ੫੩੭ ਤੋਂ ੫੫੬", "raag_bihaagra"),
            BaniItem("੮. ਵਡਹੰਸੁ-ਅੰਗ ੫੫੭ ਤੋਂ ੫੯੪", "raag_wadahans"),
            BaniItem("੯. ਸੋਰਠਿ-ਅੰਗ ੫੯੫ ਤੋਂ ੬੫੯", "raag_sorath"),
            BaniItem("੧੦. ਧਨਾਸਰੀ-ਅੰਗ ੬੬੦ ਤੋਂ ੬੯੫", "raag_dhanasari"),
            BaniItem("੧੧. ਜੈਤਸਰੀ-ਅੰਗ ੬੬੦ ਤੋਂ ੭੧੦", "raag_jaitsari"),
            BaniItem("੧੨. ਟੋਡੀ-ਅੰਗ ੭੧੧ ਤੋਂ ੭੧੮", "raag_todi"),
            BaniItem("੧੩. ਬੈਰਾੜੀ-ਅੰਗ ੭੧੯ ਤੋਂ ੭੨੦", "raag_bairari"),
            BaniItem("੧੪. ਤਿਲੰਗ-ਅੰਗ ੭੨੧ ਤੋਂ ੭੨੭", "raag_tilang"),
            BaniItem("੧੫. ਸੂਹੀ-ਅੰਗ ੭੨੮ ਤੋਂ ੭੯੪", "raag_suhi"),
            BaniItem("੧੬. ਬਿਲਾਵਲੁ-ਅੰਗ ੭੯੫ ਤੋਂ ੮੩੦", "raag_bilaval"),
            BaniItem("੧੬. ਬਿਲਾਵਲੁ-ਅੰਗ ੮੩੧ ਤੋਂ ੮੫੮", "raag_bilaval_bhag_pehla"),
            BaniItem("੧੭. ਗੋਂਡ-ਅੰਗ ੮੫੯ ਤੋਂ ੮੭੫", "raag_gond"),
            BaniItem("੧੮. ਰਾਮਕਲੀ-ਅੰਗ ੮੭੬ ਤੋਂ ੯੧੨", "raag_ramkali"),
            BaniItem("੧੮. ਰਾਮਕਲੀ-ਅੰਗ ੯੧੩ ਤੋਂ ੯੩੮", "raag_ramkali_bhag_pehla"),
            BaniItem("੧੮. ਰਾਮਕਲੀ-ਅੰਗ ੯੩੯ ਤੋਂ ੯੭੪", "raag_ramkali_bhag_duja"),
            BaniItem("੧੯. ਨਟ ਨਾਰਾਇਣ-ਅੰਗ ੯੭੫ ਤੋਂ ੯੮੩", "raag_nat_narayan"),
            BaniItem("੨੦. ਮਾਲੀ ਗਉੜਾ-ਅੰਗ ੯੮੪ ਤੋਂ ੯੮੮", "raag_maali_gaura"),
            BaniItem("੨੧. ਮਾਰੂ-ਅੰਗ ੯੮੯ ਤੋਂ ੧੦੨੦", "raag_maru"),
            BaniItem("੨੧. ਮਾਰੂ-ਅੰਗ ੧੦੨੧ ਤੋਂ ੧੦੭੬", "raag_maru_bhag_pehla"),
            BaniItem("੨੧. ਮਾਰੂ-ਅੰਗ ੧੦੭੭ ਤੋਂ ੧੧੦੬", "raag_maru_bhag_duja"),
            BaniItem("੨੨. ਤੁਖਾਰੀ-ਅੰਗ ੧੧੦੭ ਤੋਂ ੧੧੧੭", "raag_tukhari"),
            BaniItem("੨੩. ਕੇਦਾਰਾ-ਅੰਗ ੧੧੧੮ ਤੋਂ ੧੧੨੪", "raag_kedara"),
            BaniItem("੨੪. ਭੈਰਉ-ਅੰਗ ੧੧੨੫ ਤੋਂ ੧੧੭੦", "raag_bhairao"),
            BaniItem("25. ਬਸੰਤੁ-ਅੰਗ ੧੧੭੧ ਤੋਂ ੧੧੯੬", "raag_basant"),
            BaniItem("੨੬. ਸਾਰੰਗ-ਅੰਗ ੧੧੯੭ ਤੋਂ ੧੨੫੩", "raag_sarang"),
            BaniItem("੨੭. ਮਲਾਰ-ਅੰਗ ੧੨੫੪ ਤੋਂ ੧੨੯੩", "raag_malhar"),
            BaniItem("੨੮. ਕਾਨੜਾ-ਅੰਗ ੧੨੯੪ ਤੋਂ ੧੩੧੮", "raag_kanara"),
            BaniItem("੨੯. ਕਲਿਆਨ-ਅੰਗ ੧੩੧੯ ਤੋਂ ੧੩੨੬", "raag_kalyan"),
            BaniItem("੩੦. ਪ੍ਰਭਾਤੀ-ਅੰਗ ੧੩੨੭ ਤੋਂ ੧੩੫੧", "raag_prabhati"),
            BaniItem("੩੧. ਜੈਜਾਵੰਤੀ-ਅੰਗ ੧੩੫੨ ਤੋਂ ੧੩੫੩", "raag_jaijavanti"),
            BaniItem("ਸਲੋਕ ਸਹਸਕ੍ਰਿਤੀ-ਅੰਗ ੧੩੫੪ ਤੋਂ ੧੩੬੪", "salok_sahskriti"),
            BaniItem("ਸਲੋਕ ਕਬੀਰ ਜੀ-ਅੰਗ ੧੩੬੫ ਤੋਂ ੧੩੭੭", "salok_kabir_ji"),
            BaniItem("ਸਲੋਕ ਫਰੀਦ ਜੀ-ਅੰਗ ੧੩੭੮ ਤੋਂ ੧੩੮੪", "salok_farid_ji"),
            BaniItem("ਸਵਇਏ-ਅੰਗ ੧੩੮੫ ਤੋਂ ੧੪੦੯", "swayye_ggs"),
            BaniItem("ਸਲੋਕ ਵਾਰਾਂ ਤੇ ਵਧੀਕ-ਅੰਗ ੧੪੧੦ ਤੋਂ ੧੪੨੬", "salok_vara_te_vadhik"),
            BaniItem("ਸਲੋਕ ਮਹਲਾ ੯-ਅੰਗ ੧੪੨੭ ਤੋਂ ੧੪੩੦", "salok_m_9")
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(14.toPx(), 14.toPx(), 14.toPx(), 14.toPx())
        }

        val header = RelativeLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            val gd = GradientDrawable().apply {
                setColor(getFrameColor())
                val r = 16.toPx().toFloat()
                setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            }
            background = gd
            // Reduced top padding as title is now split and avoids center camera
            setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
        }

        val titleTv = TextView(context).apply {
            text = "ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ (ਰਾਗ ਅਨੁਸਾਰ)"
            textSize = 20f
            setTextColor(getTextColor())
            typeface = getCustomTypeface()
            val lp = RelativeLayout.LayoutParams(-2, -2)
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL)
            layoutParams = lp
        }
        header.addView(titleTv)

        val closeBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(getTextColor())
            val lp = RelativeLayout.LayoutParams(48.toPx(), 48.toPx())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams = lp
        }
        header.addView(closeBtn)
        root.addView(header)

        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            setPadding(16.toPx(), 0, 16.toPx(), 120.toPx())
            clipToPadding = false
        }

        val navSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0)
            setBackgroundColor(getBgColor())
        }

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val btn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.toPx(), 0, 4.toPx()) }
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    setTextColor(Color.WHITE)
                    setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
                    transformationMethod = null
                    typeface = getCustomTypeface()
                    textSize = 14f
                }
                return object : RecyclerView.ViewHolder(btn) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = ragas[position]
                (holder.itemView as Button).apply {
                    text = item.name
                    setOnClickListener {
                        val path = "guru_granth_sahib/${item.folder}"
                        showGurbaniDialog(item.name, path, anchorView)
                    }
                }
            }
            override fun getItemCount() = ragas.size
        }
        root.addView(rv)
        root.addView(navSpacer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navBarHeight = systemBars.bottom
            val actualNavHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navSpacer.layoutParams.height = actualNavHeight
            navSpacer.requestLayout()
            insets
        }

        setupDialogWindow(dial, anchorView = anchorView)
        dial.show()
    }

    private fun showVaaraPartsDialog(anchorView: View?) {
        val parts = listOf(
            BaniItem("ਵਾਰ ੧ ਤੋਂ ੭", "vaara_bhai_gurdas/part1"),
            BaniItem("ਵਾਰ ੮ ਤੋਂ ੧੪", "vaara_bhai_gurdas/part2"),
            BaniItem("ਵਾਰ ੧੫ ਤੋਂ ੨੧", "vaara_bhai_gurdas/part3"),
            BaniItem("ਵਾਰ ੨੨ ਤੋਂ ੨੮", "vaara_bhai_gurdas/part4"),
            BaniItem("ਵਾਰ ੨੯ ਤੋਂ ੩੫", "vaara_bhai_gurdas/part5"),
            BaniItem("ਵਾਰ ੩੬ ਤੋਂ ੪੧", "vaara_bhai_gurdas/part6")
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(14.toPx(), 14.toPx(), 14.toPx(), 14.toPx())
        }

        val header = RelativeLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            val gd = GradientDrawable().apply {
                setColor(getFrameColor())
                val r = 16.toPx().toFloat()
                setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            }
            background = gd
            setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
        }

        val titleTv = TextView(context).apply {
            text = "ਵਾਰਾਂ ਭਾਈ ਗੁਰਦਾਸ ਜੀ (੬ ਭਾਗ)"
            textSize = 20f
            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            typeface = getCustomTypeface()
            val lp = RelativeLayout.LayoutParams(-2, -2)
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL)
            layoutParams = lp
        }
        header.addView(titleTv)

        val closeBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            val lp = RelativeLayout.LayoutParams(48.toPx(), 48.toPx())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams = lp
        }
        header.addView(closeBtn)
        root.addView(header)

        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            setPadding(16.toPx(), 0, 16.toPx(), 120.toPx())
            clipToPadding = false
        }

        val navSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0)
            setBackgroundColor(getBgColor())
        }

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val btn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.toPx(), 0, 4.toPx()) }
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    setTextColor(Color.WHITE)
                    setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
                    transformationMethod = null
                    typeface = getCustomTypeface()
                    textSize = 16f
                }
                return object : RecyclerView.ViewHolder(btn) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = parts[position]
                (holder.itemView as Button).apply {
                    text = item.name
                    setOnClickListener {
                        showGurbaniDialog(item.name, item.folder, anchorView)
                    }
                }
            }
            override fun getItemCount() = parts.size
        }
        root.addView(rv)
        root.addView(navSpacer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val actualNavHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navSpacer.layoutParams.height = actualNavHeight
            navSpacer.requestLayout()
            insets
        }

        setupDialogWindow(dial, anchorView = anchorView)
        dial.show()
    }

    private fun showNitnemSettingsDialog(anchorView: View?) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(14.toPx(), 14.toPx(), 14.toPx(), 14.toPx())
        }

        val header = RelativeLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            val gd = GradientDrawable().apply {
                setColor(getFrameColor())
                val r = 16.toPx().toFloat()
                setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            }
            background = gd
            setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
        }

        val titleTv = TextView(context).apply {
            text = "ਨਿਤਨੇਮ ਸੈਟਿੰਗਜ਼"
            textSize = 20f
            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            typeface = getCustomTypeface()
            val lp = RelativeLayout.LayoutParams(-2, -2)
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL)
            layoutParams = lp
        }
        header.addView(titleTv)

        val closeBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            val lp = RelativeLayout.LayoutParams(48.toPx(), 48.toPx())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams = lp
        }
        header.addView(closeBtn)
        root.addView(header)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.toPx(), 16.toPx(), 16.toPx(), 100.toPx())
        }

        val themeOption = Button(context).apply {
            text = if (isDarkMode) "ਲਾਈਟ ਮੋਡ (Light Mode)" else "ਡਾਰਕ ਮੋਡ (Dark Mode)"
            setBackgroundResource(R.drawable.rounded_button_bg)
            setTextColor(Color.WHITE)
            typeface = getCustomTypeface()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12.toPx() }
            setOnClickListener {
                isDarkMode = !isDarkMode
                applyPanelTheme()
                text = if (isDarkMode) "ਲਾਈਟ ਮੋਡ (Light Mode)" else "ਡਾਰਕ ਮੋਡ (Dark Mode)"
            }
        }
        content.addView(themeOption)

        val searchOption = Button(context).apply {
            text = "ਗੁਰਬਾਣੀ ਖੋਜ (ਪਹਿਲੇ ਅੱਖਰ ਨਾਲ)"
            setBackgroundResource(R.drawable.rounded_button_bg)
            setTextColor(Color.WHITE)
            typeface = getCustomTypeface()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12.toPx() }
            setOnClickListener {
                showGurbaniSearchDialog(anchorView)
            }
        }
        content.addView(searchOption)

        root.addView(content)
        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }
        setupDialogWindow(dial, anchorView = anchorView)
        dial.show()
    }

    private fun showKabitPartsDialog(anchorView: View?) {
        val parts = listOf(
            BaniItem("ਭਾਗ ੧ (ਕਬਿੱਤ ੧ ਤੋਂ ੧੦੯)", "kabit_sawaye_bhai_gurdas/part1"),
            BaniItem("ਭਾਗ ੨ (ਕਬਿੱਤ ੧੧੦ ਤੋਂ ੨੧੮)", "kabit_sawaye_bhai_gurdas/part2"),
            BaniItem("ਭਾਗ ੩ (ਕਬਿੱਤ ੨੧੯ ਤੋਂ ੩੨੭)", "kabit_sawaye_bhai_gurdas/part3"),
            BaniItem("ਭਾਗ ੪ (ਕਬਿੱਤ ੩੨੮ ਤੋਂ ੪੩੬)", "kabit_sawaye_bhai_gurdas/part4"),
            BaniItem("ਭਾਗ ੫ (ਕਬਿੱਤ ੪੩੭ ਤੋਂ ੫੪੫)", "kabit_sawaye_bhai_gurdas/part5"),
            BaniItem("ਭਾਗ ੬ (ਕਬਿੱਤ ੫੪੬ ਤੋਂ ੬੫੮)", "kabit_sawaye_bhai_gurdas/part6")
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(14.toPx(), 14.toPx(), 14.toPx(), 14.toPx())
        }

        val header = RelativeLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            val gd = GradientDrawable().apply {
                setColor(getFrameColor())
                val r = 16.toPx().toFloat()
                setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            }
            background = gd
            setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
        }

        val titleTv = TextView(context).apply {
            text = "ਕਬਿੱਤ ਸਵੱਯੇ ਭਾਈ ਗੁਰਦਾਸ ਜੀ (੬ ਭਾਗ)"
            textSize = 20f
            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            typeface = getCustomTypeface()
            val lp = RelativeLayout.LayoutParams(-2, -2)
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL)
            layoutParams = lp
        }
        header.addView(titleTv)

        val closeBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            val lp = RelativeLayout.LayoutParams(48.toPx(), 48.toPx())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams = lp
        }
        header.addView(closeBtn)
        root.addView(header)

        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            setPadding(16.toPx(), 0, 16.toPx(), 120.toPx())
            clipToPadding = false
        }

        val navSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0)
            setBackgroundColor(getBgColor())
        }

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val btn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.toPx(), 0, 4.toPx()) }
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    setTextColor(Color.WHITE)
                    setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
                    transformationMethod = null
                    typeface = getCustomTypeface()
                    textSize = 16f
                }
                return object : RecyclerView.ViewHolder(btn) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = parts[position]
                (holder.itemView as Button).apply {
                    text = item.name
                    setOnClickListener {
                        showGurbaniDialog(item.name, item.folder, anchorView)
                    }
                }
            }
            override fun getItemCount() = parts.size
        }
        root.addView(rv)
        root.addView(navSpacer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val actualNavHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navSpacer.layoutParams.height = actualNavHeight
            navSpacer.requestLayout()
            insets
        }

        setupDialogWindow(dial, anchorView = anchorView)
        dial.show()
    }

    private fun showGurusInfoDialog(anchorView: View?) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(14.toPx(), 14.toPx(), 14.toPx(), 14.toPx())
        }

        val header = RelativeLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            val gd = GradientDrawable().apply {
                setColor(getFrameColor())
                val r = 16.toPx().toFloat()
                setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            }
            background = gd
            setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
        }

        val titleTv = TextView(context).apply {
            text = "ਗੁਰੂ ਸਾਹਿਬਾਨ: ਮਹੱਤਵਪੂਰਨ ਤਾਰੀਖਾਂ"
            textSize = 20f
            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            typeface = getCustomTypeface()
            val lp = RelativeLayout.LayoutParams(-2, -2)
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL)
            layoutParams = lp
        }
        header.addView(titleTv)

        val closeBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            val lp = RelativeLayout.LayoutParams(48.toPx(), 48.toPx())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams = lp
        }
        header.addView(closeBtn)
        root.addView(header)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            setPadding(16.toPx(), 16.toPx(), 16.toPx(), 100.toPx())
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addInfo(text: String, isHeader: Boolean = false) {
            contentLayout.addView(TextView(context).apply {
                this.text = text
                textSize = if (isHeader) 18f else 16f
                setTextColor(if (isDarkMode) Color.parseColor("#E0E0E0") else Color.parseColor("#3E2723"))
                typeface = getCustomTypeface()
                if (isHeader) setTypeface(null, Typeface.BOLD)
                setPadding(0, 8.toPx(), 0, if (isHeader) 4.toPx() else 2.toPx())
            })
        }

        addInfo("੧. ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੧ ਵੈਸਾਖ (੧੪ ਅਪ੍ਰੈਲ), ੨੭ ਮਾਰਚ ੧੪੬੯")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੮ ਅੱਸੂ (੨੨ ਸਤੰਬਰ), ੭ ਸਤੰਬਰ ੧੫੩੯")

        addInfo("੨. ਗੁਰੂ ਅੰਗਦ ਦੇਵ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੫ ਵੈਸਾਖ (੧੮ ਅਪ੍ਰੈਲ), ੩੧ ਮਾਰਚ ੧੫੦੪")
        addInfo("ਗੁਰਗੱਦੀ: ੪ ਅੱਸੂ (੧੮ ਸਤੰਬਰ), ੩ ਸਤੰਬਰ ੧੫੩੯")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੩ ਵੈਸਾਖ (੧੬ ਅਪ੍ਰੈਲ), ੨੯ ਮਾਰਚ ੧੫੫੨")

        addInfo("੩. ਗੁਰੂ ਅਮਰ ਦਾਸ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੯ ਜੇਠ (੨੩ ਮਈ), ੫ ਮਈ ੧੪੭੯")
        addInfo("ਗੁਰਗੱਦੀ: ੩ ਵੈਸਾਖ (੧੬ ਅਪ੍ਰੈਲ), ੨੯ ਮਾਰਚ ੧੫੫੨")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੨ ਅੱਸੂ (੧੬ ਸਤੰਬਰ), ੧ ਸਤੰਬਰ ੧੫੭੪")

        addInfo("੪. ਗੁਰੂ ਰਾਮਦਾਸ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੨੫ ਅੱਸੂ (੯ ਅਕਤੂਬਰ), ੨੪ ਸਤੰਬਰ ੧੫੩੪")
        addInfo("ਗੁਰਗੱਦੀ: ੨ ਅੱਸੂ (੧੬ ਸਤੰਬਰ), ੧ ਸਤੰਬਰ ੧੫੭੪")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੨ ਅੱਸੂ (੧੬ ਸਤੰਬਰ), ੧ ਸਤੰਬਰ ੧੫੮੧")

        addInfo("੫. ਗੁਰੂ ਅਰਜਨ ਦੇਵ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੧੯ ਵੈਸਾਖ (੨ ਮਈ), ੧੫ ਅਪ੍ਰੈਲ ੧੫੬੩")
        addInfo("ਗੁਰਗੱਦੀ: ੨ ਅੱਸੂ (੧੬ ਸਤੰਬਰ), ੧ ਸਤੰਬਰ ੧੫੮੧")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੨ ਹਾੜ (੧੬ ਜੂਨ), ੩੦ ਮਈ ੧੬੦੬")

        addInfo("੬. ਗੁਰੂ ਹਰਿਗੋਬਿੰਦ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੨੧ ਹਾੜ (੫ ਜੁਲਾਈ), ੧੯ ਜੂਨ ੧੫੯੫")
        addInfo("ਗੁਰਗੱਦੀ: ੨੮ ਜੇਠ (੧੧ ਜੂਨ), ੨੫ ਮਈ ੧੬੦੬")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੬ ਚੇਤ (੧੯ ਮਾਰਚ), ੩ ਮਾਰਚ ੧੬੪੪")

        addInfo("੭. ਗੁਰੂ ਹਰਿ ਰਾਇ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੧੯ ਮਾਘ (੩੧ ਜਨਵਰੀ), ੧੬ ਜਨਵਰੀ ੧੬੩੦")
        addInfo("ਗੁਰਗੱਦੀ: ੧ ਚੇਤ (੧੪ ਮਾਰਚ), ੨੭ ਫਰਵਰੀ ੧੬੪੪")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੬ ਕੱਤਕ (੨੦ ਅਕਤੂਬਰ), ੬ ਅਕਤੂਬਰ ੧੬੬੧")

        addInfo("੮. ਗੁਰੂ ਹਰਿ ਕ੍ਰਿਸ਼ਨ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੮ ਸਾਵਣ (੨੩ ਜੁਲਾਈ), ੭ ਜੁਲਾਈ ੧੬੫੬")
        addInfo("ਗੁਰਗੱਦੀ: ੬ ਕੱਤਕ (੨੦ ਅਕਤੂਬਰ), ੬ ਅਕਤੂਬਰ ੧੬੬੧")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੩ ਵੈਸਾਖ (੧੬ ਅਪ੍ਰੈਲ), ੩੦ ਮਾਰਚ ੧੬੬੪")

        addInfo("੯. ਗੁਰੂ ਤੇਗ ਬਹਾਦਰ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੫ ਵੈਸਾਖ (੧੮ ਅਪ੍ਰੈਲ), ੧ ਅਪ੍ਰੈਲ ੧੬੨੧")
        addInfo("ਗੁਰਗੱਦੀ: ੩ ਵੈਸਾਖ (੧੬ ਅਪ੍ਰੈਲ), ੩੦ ਮਾਰਚ ੧੬੬੪")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੧੧ ਮੱਘਰ (੨੪ ਨਵੰਬਰ), ੧੧ ਨਵੰਬਰ ੧੬੭੫")

        addInfo("੧੦. ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ", true)
        addInfo("ਪ੍ਰਕਾਸ਼: ੨੩ ਪੋਹ (੫ ਜਨਵਰੀ), ੨੨ ਦਸੰਬਰ ੧੬੬੬")
        addInfo("ਗੁਰਗੱਦੀ: ੧੧ ਮੱਘਰ (੨੪ ਨਵੰਬਰ), ੧੧ ਨਵੰਬਰ ੧੬੭੫")
        addInfo("ਜੋਤੀ ਜੋਤਿ: ੭ ਕੱਤਕ (੨੧ ਅਕਤੂਬਰ), ੭ ਅਕਤੂਬਰ ੧੭੦੮")

        addInfo("\nਹੋਰ ਮਹੱਤਵਪੂਰਨ ਤਾਰੀਖਾਂ", true)
        addInfo("ਪਹਿਲਾ ਪ੍ਰਕਾਸ਼ ਆਦਿ ਗ੍ਰੰਥ: ੧੭ ਭਾਦੋਂ (੧ ਸਤੰਬਰ), ੧੬ ਅਗਸਤ ੧੬੦੪")
        addInfo("ਸੰਪੂਰਨਤਾ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ: ੧੫ ਭਾਦੋਂ (੩੦ ਅਗਸਤ), ੧੫ ਅਗਸਤ ੧੭੦੬")
        addInfo("ਗੁਰਗੱਦੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ: ੬ ਕੱਤਕ (੨੦ ਅਕਤੂਬਰ), ੬ ਅਕਤੂਬਰ ੧੭੦੮")
        addInfo("ਸਿਰਜਣਾ ਸ੍ਰੀ ਅਕਾਲ ਤਖਤ ਸਾਹਿਬ: ੧੮ ਹਾੜ (੨ ਜੁਲਾਈ), ੧੫ ਜੂਨ ੧੬੦੬")

        scroll.addView(contentLayout)
        root.addView(scroll)

        val navSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0)
            setBackgroundColor(getBgColor())
        }
        root.addView(navSpacer)

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val actualNavHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navSpacer.layoutParams.height = actualNavHeight
            navSpacer.requestLayout()
            insets
        }

        setupDialogWindow(dial, anchorView = anchorView)
        dial.show()
    }

    private fun showBooksDialog(anchorView: View?) {
        val defaultBooks = listOf(
            PdfBook("ਸ੍ਰੀ ਦਸਮ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ", "https://archive.org/download/dasam-guru-granth-sahib-hazur-sahib_202306/Dasam%20Guru%20Granth%20Sahib%20Hazur%20Sahib.pdf", "dasam_granth.pdf"),
            PdfBook("ਸ੍ਰੀ ਸਰਬ ਲੋਹ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ", "https://archive.org/download/sampooran-sri-sarbloh-granth-sahib/Sampooran%20Sri%20Sarbloh%20Granth%20Sahib.pdf", "sarbloh_granth.pdf"),
            PdfBook("ਗੁਰੂ ਨਾਨਕ ਚਮਤਕਾਰ (ਭਾਗ ੧) - ਭਾਈ ਵੀਰ ਸਿੰਘ", "https://archive.org/download/SriGuruNanakChamatkar-Vol1/SriGuruNanakChamatkar-Vol1.pdf", "nanak_chamatkar_1.pdf"),
            PdfBook("ਗੁਰੂ ਨਾਨਕ ਚਮਤਕਾਰ (ਭਾਗ ੨) - ਭਾਈ ਵੀਰ ਸਿੰਘ", "https://archive.org/download/SriGuruNanakChamatkar-Vol2/SriGuruNanakChamatkar-Vol2.pdf", "nanak_chamatkar_2.pdf"),
            PdfBook("Atlas: Travels of Guru Nanak (ਐਟਲਸ ਉਦਾਸੀਆਂ)", "https://archive.org/download/AtlasTravelsOfGuruNanak/Atlas%20-%20Travels%20Of%20Guru%20Nanak.pdf", "nanak_travels_atlas.pdf"),
            PdfBook("ਬਾਬੇ ਦੀ ਬਗ਼ਦਾਦ ਫੇਰੀ (ਮੁਲਾਕਾਤਾਂ)", "https://archive.org/download/GURU_NANAK_DEV_JI_550_YEARS/Babe_di_Bagdad_Pheri_Prithipal_S_Kulwant_S.pdf", "nanak_baghdad.pdf"),
            PdfBook("ਪੁਰਾਤਨ ਜਨਮ ਸਾਖੀ (ਸੰਪਾਦਿਤ ਭਾਈ ਵੀਰ ਸਿੰਘ)", "https://archive.org/download/puratan-janamsakhi-guru-nanak/Puratan%20Janamsakhi%20Guru%20Nanak.pdf", "puratan_janamsakhi_edit.pdf"),
            PdfBook("Travels of Guru Nanak (Dr. S.S. Kohli)", "https://archive.org/download/GuruNanakDevJi/Guru%20Nanak%20Dev%20Ji.pdf", "nanak_travels_kohli.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਭਾਗ ੧: ਦਸ ਗੁਰੂ ਸਾਹਿਾਨ)", "https://archive.org/download/TwareekhGuruKhalsa-GianiGianSingh-Part1/Twareekh%20Guru%20Khalsa%20-%20Giani%20Gian%20Singh%20-%20Part%201.pdf", "twarikh_guru_khalsa_1.pdf"),
            PdfBook("ਸਿੱਖ ਇਤਿਹਾਸ ਭਾਗ ੧ (ਪ੍ਰੋ. ਕਰਤਾਰ ਸਿੰਘ)", "https://archive.org/download/SikhItihasPart1KartarSingh/Sikh%20Itihas%20Part%201%20-%20Kartar%20Singh.pdf", "sikh_itihas_1.pdf"),
            PdfBook("ਸਿੱਖ ਇਤਿਹਾਸ ਭਾਗ ੨ (ਪ੍ਰੋ. ਕਰਤਾਰ ਸਿੰਘ)", "https://archive.org/download/SikhItihasPart2KartarSingh/Sikh%20Itihas%20Part%202%20-%20Kartar%20Singh.pdf", "sikh_itihas_2.pdf"),
            PdfBook("ਸ੍ਰੀ ਕਲਗੀਧਰ ਚਮਤਕਾਰ (ਭਾਗ ੧) - ਭਾਈ ਵੀਰ ਸਿੰਘ", "https://archive.org/download/SriKalgidharChamatkar-Volume1/SriKalgidharChamatkar-Volume1.pdf", "kalgidhar_chamatkar_1.pdf"),
            PdfBook("ਸ੍ਰੀ ਕਲਗੀਧਰ ਚਮਤਕਾਰ (ਭਾਗ ੨) - ਭਾਈ ਵੀਰ ਸਿੰਘ", "https://archive.org/download/SriKalgidharChamatkar-Vol2/SriKalgidharChamatkar-Vol2.pdf", "kalgidhar_chamatkar_2.pdf"),
            PdfBook("ਬਚਿੱਤਰ ਨਾਟਕ (ਸਟੀਕ) - ਡਾ. ਹਰਭਜਨ ਸਿੰਘ", "https://archive.org/download/BachitarNatak_201905/Bachitar%20Natak.pdf", "bachittar_natak_steek.pdf"),
            PdfBook("ਗੁਰ ਬਾਲਮ ਸਾਖੀਆਂ (ਸ੍ਰੀ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ)", "https://archive.org/download/SachitarJeevanSakhianGuruGobindSinghJi/SachitarJeevanSakhianGuruGobindSinghJi.pdf", "gur_balam_sakhian.pdf"),
            PdfBook("ਜ਼ਫ਼ਰਨਾਮਾ (ਸਟੀਕ - ਸੰਤ ਅਮੀਰ ਸਿੰਘ)", "https://archive.org/download/zafarnama-steek-as/zafarnama-steek-as.pdf", "zafarnama_steek_as.pdf"),
            PdfBook("ਜੀਵਨ ਬ੍ਰਿਤਾਂਤ ਸ੍ਰੀ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ - ਭਾਈ ਵੀਰ ਸਿੰਘ", "https://archive.org/download/JeevanBritantSriGuruGobindSinghJi/JeevanBritantSriGuruGobindSinghJi.pdf", "life_guru_gobind_singh_vir.pdf"),
            PdfBook("ਦਸ਼ਮੇਸ਼ ਪ੍ਰਕਾਸ਼ (ਇਤਿਹਾਸ ਪਾਤਸ਼ਾਹੀ ੧੦)", "https://archive.org/download/DashmeshParkashKartarSingh/Dashmesh%20Parkash%20-%20Kartar%20Singh.pdf", "dashmesh_parkash.pdf"),
            PdfBook("ਜੀਵਨ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ (ਪ੍ਰੋ. ਕਰਤਾਰ ਸਿੰਘ)", "https://archive.org/download/LifeOfGuruNanakDev-ProfKartarSingh/Life%20of%20Guru%20Nanak%20Dev%20-%20Prof%20Kartar%20Singh.pdf", "life_guru_nanak_kartar.pdf"),
            PdfBook("ਸ੍ਰੀ ਅਸ਼ਟ ਗੁਰ ਚਮਤਕਾਰ (ਭਾਗ ੧ & ੨)", "https://archive.org/download/SriAshtGuruChamatkar-Part1And2/Sri%20Asht%20Guru%20Chamatkar%20-%20Part%201%20And%202.pdf", "asht_gur_chamatkar_1.pdf"),
            PdfBook("ਸ੍ਰੀ ਅਸ਼ਟ ਗੁਰ ਚਮਤਕਾਰ (ਭਾਗ ੩)", "https://archive.org/download/SriAshtGuruChamatkar-Part3/Sri%20Asht%20Guru%20Chamatkar%20-%20Part%203.pdf", "asht_gur_chamatkar_3.pdf"),
            PdfBook("ਸੂਰਜ ਪ੍ਰਕਾਸ਼ (ਸ੍ਰੀ ਗੁਰ ਪ੍ਰਤਾਪ ਸੂਰਜ ਗ੍ਰੰਥ)", "https://archive.org/download/gur_pratap_suraj_granth/gur_pratap_suraj_granth.pdf", "suraj_parkash.pdf"),

            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੧", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume1/SriGuruGranthSahibDarpan-Volume1.pdf", "ggs_darpan_1.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੨", "https://archive.org/download/SriGuruGranthSahibDarpan/SriGuruGranthSahibDarpan.pdf", "ggs_darpan_2.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੩", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume3/SriGuruGranthSahibDarpan-Volume3.pdf", "ggs_darpan_3.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੪", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume4/SriGuruGranthSahibDarpan-Volume4.pdf", "ggs_darpan_4.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੫", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume5/SriGuruGranthSahibDarpan-Volume5.pdf", "ggs_darpan_5.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੬", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume6/SriGuruGranthSahibDarpan-Volume6.pdf", "ggs_darpan_6.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੭", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume7/SriGuruGranthSahibDarpan-Volume7.pdf", "ggs_darpan_7.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੮", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume8/SriGuruGranthSahibDarpan-Volume8.pdf", "ggs_darpan_8.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੯", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume9/SriGuruGranthSahibDarpan-Volume9.pdf", "ggs_darpan_9.pdf"),
            PdfBook("ਗੁਰੂ ਗ੍ਰੰਥ ਦਰਪਣ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - ਭਾਗ ੧੦", "https://archive.org/download/SriGuruGranthSahibDarpan-Volume10/SriGuruGranthSahibDarpan-Volume10.pdf", "ggs_darpan_10.pdf"),

            PdfBook("ਵਾਰਾਂ ਭਾਈ ਗੁਰਦਾਸ ਜੀ (ਸਟੀਕ)", "https://archive.org/download/VaraanBhaiGurdasSteek-PanditNarainSingh/VaraanBhaiGurdasSteek-PanditNarainSingh.pdf", "vaaran_bhai_gurdas.pdf"),
            PdfBook("ਕਬਿੱਤ ਸਵੱਯੇ ਭਾਈ ਗੁਰਦਾਸ ਜੀ (ਸਟੀਕ)", "https://archive.org/download/KabitSavaiyeBhaiGurdasJiSteekPart1/KabitSavaiyeBhaiGurdasJiSteekPart1.pdf", "kabit_sawaiye.pdf"),
            PdfBook("ਬੰਦਗੀ ਨਾਮਾ (ਰਘਬੀਰ ਸਿੰਘ ਬੀਰ)", "https://archive.org/download/BandgiNama_116/BandgiNama_116.pdf", "bandgi_nama.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ)", "https://archive.org/download/TwareekhGuruKhalsa01/TwareekhGuruKhalsa01.pdf", "twarikh_guru_khalsa.pdf"),
            PdfBook("ਮਹਾਨ ਕੋਸ਼ (ਭਾਈ ਕਾਨ੍ਹ ਸਿੰਘ ਨਾਭਾ)", "https://archive.org/download/dli.language.0726/dli.language.0726.pdf", "mahan_kosh.pdf"),
            PdfBook("ਪ੍ਰਾਚੀਨ ਪੰਥ ਪ੍ਰਕਾਸ਼ (ਰਤਨ ਸਿੰਘ ਭੰਗੂ)", "https://archive.org/download/prachin-panth-prakash/Prachin%20Panth%20Prakash.pdf", "prachin_panth_parkash.pdf"),
            PdfBook("ਸ੍ਰੀ ਗੁਰ ਪੰਥ ਪ੍ਰਕਾਸ਼ (ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ)", "https://archive.org/download/PanthParkash1/Panth_Parkash_1.pdf", "panth_parkash_gian_singh.pdf"),
            PdfBook("ਗੁਰ ਬਿਲਾਸ ਪਾਤਸ਼ਾਹੀ ੬", "https://archive.org/download/gurbilas-patshhai-6/gurbilas-patshhai-6.pdf", "gur_bilas_p6.pdf"),
            PdfBook("ਗੁਰ ਬਿਲਾਸ ਪਾਤਸ਼ਾਹੀ ੧੦ (ਕੋਇਰ ਸਿੰਘ)", "https://archive.org/download/gurbilas-patshahi-10/gurbilas-patshahi-10.pdf", "gur_bilas_p10.pdf"),
            PdfBook("ਸਿੱਖ ਰਹਿਤ ਮਰਯਾਦਾ", "https://archive.org/download/RehatMaryada/RehatMaryada.pdf", "rehat_maryada.pdf"),
            PdfBook("ਰਹਿਤਨਾਮੇ (ਪਿਆਰਾ ਸਿੰਘ ਪਦਮ)", "https://archive.org/download/Rehatnaamey-PiaraSinghPadam/Rehatnaamey-PiaraSinghPadam.pdf", "rehatnamay.pdf"),
            PdfBook("ਜ਼ਫ਼ਰਨਾਮਾ (ਸਟੀਕ)", "https://archive.org/download/zafarnama-steek/zafarnama-steek.pdf", "zafarnama_steek.pdf"),
            PdfBook("ਗੁਰਬਾਣੀ ਵਿਆਕਰਨ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ)", "https://archive.org/download/gurbani-viakaran/gurbani-viakaran.pdf", "gurbani_viyakaran.pdf"),
            PdfBook("ਹਮ ਹਿੰਦੂ ਨਹੀਂ (ਭਾਈ ਕਾਨ੍ਹ ਸਿੰਘ ਨਾਭਾ)", "https://archive.org/download/hum-hindu-nahin-bhai-kahn-singh-nabha/hum-hindu-nahin-bhai-kahn-singh-nabha.pdf", "ham_hindu_nahin.pdf"),
            PdfBook("ਗੁਰਮਤ ਮਾਰਤੰਡ (ਭਾਈ ਕਾਨ੍ਹ ਸਿੰਘ ਨਾਭਾ)", "https://archive.org/download/GurmatMartand1/GurmatMartand1.pdf", "gurmat_martand.pdf"),
            PdfBook("ਪਰਾਸ਼ਰਪ੍ਰਸ਼ਨ (ਸਿਰਦਾਰ ਕਪੂਰ ਸਿੰਘ)", "https://archive.org/download/Parasaraprasna/Parasaraprasna.pdf", "parasharprasna.pdf"),
            PdfBook("ਸਾਚੀ ਸਾਖੀ (ਸਿਰਦਾਰ ਕਪੂਰ ਸਿੰਘ)", "https://archive.org/download/SachiSakhi/SachiSakhi.pdf", "sachi_sakhi.pdf"),
            PdfBook("ਅਰਦਾਸ ਸ਼ਕਤੀ (ਰਘਬੀਰ ਸਿੰਘ ਬੀਰ)", "https://archive.org/download/ArdasShakti/ArdasShakti.pdf", "ardas_shakti.pdf"),
            PdfBook("ਗੁਰਮੁਖ ਸਿੱਖਿਆ (ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ)", "https://archive.org/download/GurmukhSikhya/GurmukhSikhya.pdf", "gurmukh_sikhia.pdf"),
            PdfBook("ਪੁਰਾਤਨ ਜਨਮ ਸਾਖੀ", "https://archive.org/download/PuratanJanamSakhiSriGuruNanakDevJi/Puratan%20Janam%20Sakhi%20Sri%20Guru%20Nanak%20Dev%20Ji.pdf", "puratan_janamsakhi.pdf"),
            PdfBook("ਜਨਮ ਸਾਖੀ ਭਾਈ ਬਾਲਾ", "https://archive.org/download/JanamSakhiBhaiBala/Janam%20Sakhi%20Bhai%20Bala.pdf", "bhai_bala_janamsakhi.pdf"),
            PdfBook("The Sikh Religion (Macauliffe) - Vol 1", "https://archive.org/download/thesikhreligion01macauoft/thesikhreligion01macauoft.pdf", "macauliffe_vol1.pdf"),
            PdfBook("ਬ੍ਰਹਮ ਕਵਚ (ਸਟੀਕ)", "https://archive.org/download/BrahamKavach/Braham%20Kavach.pdf", "brahm_kavach.pdf"),
            PdfBook("ਬਾਬਾ ਦੀਪ ਸਿੰਘ ਜੀ (ਜੀਵਨੀ)", "https://archive.org/download/sachitar-jeevan-baba-deep-singh-ji-shaheed/Sachitar%20Jeevan%20Baba%20Deep%20Singh%20Ji%20Shaheed.pdf", "baba_deep_singh.pdf"),
            PdfBook("ਭਾਈ ਮਨੀ ਸਿੰਘ ਜੀ (ਜੀਵਨੀ)", "https://archive.org/download/bhai-mani-singh/Bhai%20Mani%20Singh.pdf", "bhai_mani_singh.pdf"),
            PdfBook("ਸੌ ਸਾਖੀ (ਪ੍ਰਾਚੀਨ)", "https://archive.org/download/PracheenSauSakhi/PracheenSauSakhi.pdf", "sau_sakhi.pdf"),
            PdfBook("ਬੰਸਾਵਲੀਨਾਮਾ ਦਸਾਂ ਪਾਤਸ਼ਾਹੀਆਂ ਕਾ (ਕੇਸਰ ਸਿੰਘ ਛਿੱਬਰ)", "https://archive.org/download/bansawali-nama/Bansawali%20Nama%20Dasan%20Patshahian%20Ka.pdf", "bansavalinama.pdf"),
            PdfBook("ਸ੍ਰੀ ਗੁਰ ਸੋਭਾ (ਸੈਨਾਪਤਿ)", "https://archive.org/download/SriGurSobhaByDrGandaSingh/Sri%20Gur%20Sobha%20-%20Dr.%20Ganda%20Singh.pdf", "gur_sobha.pdf"),
            PdfBook("ਪ੍ਰੇਮ ਸੁਮਾਰਗ ਗ੍ਰੰਥ", "https://archive.org/download/PremSumaragGranth/Prem%20Sumarag%20Granth.pdf", "prem_sumarag.pdf"),
            PdfBook("ਸਿਮਰਨ ਮਹਿਮਾ (ਰਘਬੀਰ ਸਿੰਘ ਬੀਰ)", "https://archive.org/download/simran-mahima/Simran%20Mahima.pdf", "simran_mahima.pdf"),
            PdfBook("ਖ਼ਾਲਸਈ ਸ਼ਾਨ (ਰਘਬੀਰ ਸਿੰਘ ਬੀਰ)", "https://archive.org/download/KhalsaiShaan/Khalsai%20Shaan%20-%20Raghbir%20Singh%20Bir.pdf", "khalsai_shaan.pdf"),
            PdfBook("ਗੁਰਮਤ ਨਿਰਣਯ (ਭਾਈ ਜੋਧ ਸਿੰਘ)", "https://archive.org/download/GurmatNirnay/Gurmat%20Nirnay%20-%20Bhai%20Jodh%20Singh.pdf", "gurmat_nirnay.pdf"),
            PdfBook("Life of Guru Nanak Dev Ji (Kartar Singh)", "https://archive.org/download/in.ernet.dli.2015.514006/in.ernet.dli.2015.514006.pdf", "life_guru_nanak.pdf"),
            PdfBook("Life of Guru Gobind Singh Ji (Kartar Singh)", "https://archive.org/download/dli.ernet.54319/dli.ernet.54319.pdf", "life_guru_gobind_singh.pdf"),
            PdfBook("ਗੁਰਬਾਣੀ ਪਾਠ ਦਰਸ਼ਨ (ਗਿਆਨੀ ਗੁਰਬਚਨ ਸਿੰਘ)", "https://archive.org/download/gurbani-path-darshan_202105/Gurbani%20Path%20Darshan.pdf", "path_darshan.pdf"),
            PdfBook("ਗੁਰਮਤ ਬਿਬੇਕ (ਭਾਈ ਰਣਧੀਰ ਸਿੰਘ)", "https://archive.org/download/GurmatBibek/Gurmat%20Bibek.pdf", "gurmat_bibek.pdf"),
            PdfBook("ਮਹਾਰਾਜਾ ਰਣਜੀਤ ਸਿੰਘ (ਜੀਵਨੀ)", "https://archive.org/download/maharajaranjitsi0000harb/maharajaranjitsi0000harb.pdf", "ranjit_singh.pdf"),
            PdfBook("ਹਰੀ ਸਿੰਘ ਨਲਵਾ (ਜੀਵਨੀ)", "https://archive.org/download/HariSinghNalwa-ChampionOfTheKhalsaji/Hari%20Singh%20Nalwa%20-%20Champion%20of%20the%20Khalsaji.pdf", "hari_singh_nalwa.pdf"),
            PdfBook("ਅਕਾਲੀ ਫੂਲਾ ਸਿੰਘ (ਜੀਵਨੀ)", "https://archive.org/download/AkaliPhulaSingh/Akali%20Phula%20Singh.pdf", "akali_phula_singh.pdf"),
            PdfBook("ਜੱਸਾ ਸਿੰਘ ਆਹਲੂਵਾਲੀਆ (ਜੀਵਨੀ)", "https://archive.org/download/SardarJassaSinghAhluwalia-English-Dr.GandaSingh/Sardar%20Jassa%20Singh%20Ahluwalia%20-%20English%20-%20Dr.%20Ganda%20Singh.pdf", "jassa_singh_ahluwalia.pdf"),
            PdfBook("Essays in Sikhism (Teja Singh)", "https://archive.org/download/EssaysInSikhism-TejaSingh/Essays%20In%20Sikhism%20-%20Teja%20Singh.pdf", "essays_sikhism.pdf"),
            PdfBook("Philosophy of Sikhism (Sher Singh)", "https://archive.org/download/dli.csl.4786/dli.csl.4786.pdf", "philosophy_sikhism.pdf"),
            PdfBook("The Heritage of the Sikhs (Harbans Singh)", "https://archive.org/download/the-heritage-of-the-sikhs/The%20Heritage%20of%20the%20Sikhs%20-%20Harbans%20Singh.pdf", "heritage_sikhs.pdf"),
            PdfBook("ਸਿੱਖ, ਸਿੱਖੀ ਤੇ ਸਿਧਾਂਤ - ਡਾ. ਤਾਰਨ ਸਿੰਘ", "https://archive.org/download/SikhSikhiTeSidhant/SikhSikhiTeSidhant.pdf", "sikh_sikhi_sidhant.pdf"),
            PdfBook("ਸਿੱਖ ਧਰਮ ਮੂਲ ਸਿਧਾਂਤ - ਰੂਪ ਸਿੰਘ", "https://archive.org/download/sikh-dharam-mool-sidhant/sikh-dharam-mool-sidhant.pdf", "sikh_dharam_sidhant.pdf"),
            PdfBook("ਸ਼ਬਦ-ਗੁਰੂ ਦਾ ਸਿੱਖ ਸਿਧਾਂਤ", "https://archive.org/download/Shabad-GuruDaSikhSidhant/Shabad-Guru%20Da%20Sikh%20Sidhant.pdf", "shabad_guru_sidhant.pdf"),
            PdfBook("ਸਿੱਖ ਧਰਮ ਫਿਲਾਸਫੀ - ਗੰਗਾ ਸਿੰਘ", "https://archive.org/download/sikh-dharam-philosophy/sikh-dharam-philosophy.pdf", "sikh_dharam_philosophy.pdf"),
            PdfBook("ਗੁਰੂ ਨਾਨਕ ਦਾ ਕੁਦਰਤ ਸਿਧਾਂਤ", "https://archive.org/download/HarpalSinghPannu-13/HarpalSinghPannu-13.pdf", "nanak_kudrat_sidhant.pdf"),
            PdfBook("ਜੇਲ੍ਹ ਚਿੱਠੀਆਂ - ਭਾਈ ਰਣਧੀਰ ਸਿੰਘ", "https://archive.org/download/JailChithiyanByBhaiRandhirSingh/JailChithiyanByBhaiRandhirSingh.pdf", "jail_chithiyan.pdf"),
            PdfBook("ਸਿੱਖੀ ਸਿਦਕ ਤੇ ਧਰਮ ਰੱਖਿਆ - ਭਾਈ ਰਣਧੀਰ ਸਿੰਘ", "http://www.bsrstrust.org/wp-content/uploads/2012/10/Sikhi-Sidak-Te-Dharam-Rakhea.pdf", "sikhi_sidak.pdf"),
            PdfBook("ਜੀਵਨ ਸਫਰ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/JeevanSafarGianiSantSinghJiMaskeen/Jeevan%20Safar%20-%20Giani%20Sant%20Singh%20Ji%20Maskeen.pdf", "maskeen_jeevan_safar.pdf"),
            PdfBook("ਚੌਥਾ ਪਦ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "http://www.gurmatveechar.com/books/Punjabi_Books/Chautha.Pad.by.Giani.Sant.Singh.Maskeen.(GurmatVeechar.com).pdf", "maskeen_chautha_pad.pdf"),
            PdfBook("ਤੀਜਾ ਨੇਤਰ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-02/maskin-ji-de-lecture-02.pdf", "maskeen_teeja_netar.pdf"),
            PdfBook("ਗੁਰਮਤਿ ਸਿਧਾਂਤ (ਭਾਗ ੧) - ਮਹਾਰਾਜ ਸਾਵਣ ਸਿੰਘ", "https://archive.org/download/gurmat-sidhant-1/gurmat-sidhant-1.pdf", "gurmat_sidhant_1.pdf"),
            PdfBook("ਪ੍ਰਭੂ ਸਿਮਰਨ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/prabhu-simran/prabhu-simran.pdf", "maskeen_simran.pdf"),
            PdfBook("ਖਟ ਦਰਸ਼ਨ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-02/maskin-ji-de-lecture-02.pdf", "maskeen_khat_darshan.pdf"),
            PdfBook("ਬ੍ਰਹਮ ਗਿਆਨ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/BrahmGyanGianiSantSinghMaskeen/Brahm%20Gyan%20-%20Giani%20Sant%20Singh%20Maskeen.pdf", "maskeen_brahm_gian.pdf"),
            PdfBook("ਸੁੱਖ ਦੁੱਖ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-01/maskin-ji-de-lecture-01.pdf", "maskeen_sukh_dukh.pdf"),
            PdfBook("ਜਪੁ ਨੀਸਾਣ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-02/maskin-ji-de-lecture-02.pdf", "maskeen_jap_neesan.pdf"),
            PdfBook("ਪੰਜ ਖੰਡ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/JeevanSafarGianiSantSinghJiMaskeen/Jeevan%20Safar%20-%20Giani%20Sant%20Singh%20Ji%20Maskeen.pdf", "maskeen_panj_khand.pdf"),
            PdfBook("ਧਰਮ ਤੇ ਮਨੁੱਖ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-01/maskin-ji-de-lecture-01.pdf", "maskeen_dharam_manukh.pdf"),
            PdfBook("ਪੰਜ ਕਕਾਰਾਂ ਦਾ ਮਹੱਤਵ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-02/maskin-ji-de-lecture-02.pdf", "maskeen_5k_mahatav.pdf"),
            PdfBook("ਗਿਆਨ ਦਾ ਸਾਗਰ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/JeevanSafarGianiSantSinghJiMaskeen/Jeevan%20Safar%20-%20Giani%20Sant%20Singh%20Ji%20Maskeen.pdf", "maskeen_gian_sagar.pdf")
        )

        val items = mutableListOf<Any>()
        items.add("ACTION_IMPORT")
        items.addAll(defaultBooks)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(14.toPx(), 14.toPx(), 14.toPx(), 14.toPx())
        }

        val header = RelativeLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            val gd = GradientDrawable().apply {
                setColor(getFrameColor())
                val r = 16.toPx().toFloat()
                setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            }
            background = gd
            setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
        }

        val titleTv = TextView(context).apply {
            text = "ਬਹੁਮੁੱਲੀਆਂ ਪੁਸਤਕਾਂ (PDF)"
            textSize = 20f
            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            typeface = getCustomTypeface()
            val lp = RelativeLayout.LayoutParams(-2, -2)
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL)
            layoutParams = lp
        }
        header.addView(titleTv)

        val closeBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            val lp = RelativeLayout.LayoutParams(48.toPx(), 48.toPx())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams = lp
        }
        header.addView(closeBtn)
        root.addView(header)

        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            setPadding(16.toPx(), 0, 16.toPx(), 120.toPx())
            clipToPadding = false
        }

        val navSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0)
            setBackgroundColor(getBgColor())
        }

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            private val TYPE_ACTION = 0
            private val TYPE_HEADER = 1
            private val TYPE_BOOK = 2

            override fun getItemViewType(position: Int): Int {
                return when (val item = items[position]) {
                    is String -> if (item == "ACTION_IMPORT") TYPE_ACTION else TYPE_HEADER
                    else -> TYPE_BOOK
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return when (viewType) {
                    TYPE_ACTION -> {
                        val btn = Button(context).apply {
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8.toPx(), 0, 8.toPx()) }
                            setBackgroundResource(R.drawable.rounded_button_bg)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                            setTextColor(Color.WHITE)
                            setPadding(12.toPx(), 12.toPx(), 12.toPx(), 12.toPx())
                            transformationMethod = null
                            typeface = getCustomTypeface()
                            textSize = 18f
                            text = "ਆਪਣੀ ਪੁਸਤਕ ਅਪਲੋਡ ਕਰੋ (Import PDF)"
                        }
                        object : RecyclerView.ViewHolder(btn) {}
                    }
                    TYPE_HEADER -> {
                        val tv = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12.toPx(), 0, 4.toPx()) }
                            setTextColor(getTextColor())
                            textSize = 18f
                            typeface = Typeface.create(getCustomTypeface(), Typeface.BOLD)
                            setPadding(4.toPx(), 4.toPx(), 4.toPx(), 4.toPx())
                        }
                        object : RecyclerView.ViewHolder(tv) {}
                    }
                    else -> {
                        val btn = Button(context).apply {
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.toPx(), 0, 4.toPx()) }
                            setBackgroundResource(R.drawable.rounded_button_bg)
                            setTextColor(Color.WHITE)
                            setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
                            transformationMethod = null
                            typeface = getCustomTypeface()
                            textSize = 16f
                        }
                        object : RecyclerView.ViewHolder(btn) {}
                    }
                }
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (val item = items[position]) {
                    "ACTION_IMPORT" -> {
                        holder.itemView.setOnClickListener {
                            if (context is NitnemActivity) {
                                context.openFilePicker()
                                dial.dismiss()
                            } else {
                                Toast.makeText(context, "ਇਹ ਸਹੂਲਤ ਸਿਰਫ ਮੁੱਖ ਐਪ ਵਿੱਚ ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    is String -> {
                        (holder.itemView as TextView).text = item
                    }
                    is PdfBook -> {
                        (holder.itemView as Button).apply {
                            val isDownloaded = PdfManager.isDownloaded(context, item.fileName)
                            text = if (isDownloaded) "${item.name} (ਪੜ੍ਹੋ)" else "${item.name} (ਡਾਊਨਲੋਡ)"
                            setOnClickListener {
                                if (PdfManager.isDownloaded(context, item.fileName)) {
                                    PdfManager.openPdf(context, item.fileName)
                                } else {
                                    PdfManager.downloadPdf(context, item.url, item.name, item.fileName)
                                    Toast.makeText(context, "ਡਾਊਨਲੋਡ ਸ਼ੁਰੂ ਹੋਇਆ... ਕੁਝ ਸਮੇਂ ਬਾਅਦ ਦੁਬਾਰਾ ਚੈੱਕ ਕਰੋ", Toast.LENGTH_LONG).show()
                                    dial.dismiss()
                                }
                            }
                        }
                    }
                    is BookEntity -> {
                        (holder.itemView as Button).apply {
                            text = "${item.name} (ਪੜ੍ਹੋ)"
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#5D4037"))
                            setOnClickListener {
                                PdfManager.openPdf(context, item.path) // path is absolute for custom books
                            }
                            setOnLongClickListener {
                                AlertDialog.Builder(context)
                                    .setTitle("Delete Book")
                                    .setMessage("ਕੀ ਤੁਸੀਂ ਇਸ ਪੁਸਤਕ ਨੂੰ ਹਟਾਉਣਾ ਚਾਹੁੰਦੇ ਹੋ?")
                                    .setPositiveButton("ਹਾਂ") { _, _ ->
                                        uiScope.launch {
                                            AppDatabase.getDatabase(context).bookDao().deleteBook(item.id)
                                        }
                                    }
                                    .setNegativeButton("ਨਹੀਂ", null)
                                    .show()
                                true
                            }
                        }
                    }
                }
            }
            override fun getItemCount() = items.size
        }
        rv.adapter = adapter

        uiScope.launch {
            AppDatabase.getDatabase(context).bookDao().getAllBooks().collect { customBooks ->
                val newItems = mutableListOf<Any>()
                newItems.add("ACTION_IMPORT")
                if (customBooks.isNotEmpty()) {
                    newItems.add("ਮੇਰੀਆਂ ਪੁਸਤਕਾਂ (My Books)")
                    newItems.addAll(customBooks)
                    newItems.add("ਇਤਿਹਾਸਕ ਪੁਸਤਕਾਂ (Historical Books)")
                }
                newItems.addAll(defaultBooks)
                items.clear()
                items.addAll(newItems)
                adapter.notifyDataSetChanged()
            }
        }

        root.addView(rv)
        root.addView(navSpacer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val actualNavHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navSpacer.layoutParams.height = actualNavHeight
            navSpacer.requestLayout()
            insets
        }

        setupDialogWindow(dial, anchorView = anchorView)
        dial.show()
    }

    fun showGurbaniDialog(title: String, folderName: String, anchorView: View?) {
        showContentDialog(title = title, folderName = folderName, singleLine = null, anchorView = anchorView)
    }

    fun showShabadDialog(shabadLine: String, anchorView: View?) {
        showContentDialog(title = "ਸ਼ਬਦ", folderName = null, singleLine = shabadLine, anchorView = anchorView)
    }

    @SuppressLint("ClickableViewAccessibility", "WrongConstant")
    private fun showContentDialog(
        title: String, 
        folderName: String?, 
        singleLine: String?, 
        preFetchedLines: List<String>? = null,
        prevShabadId: String? = null,
        nextShabadId: String? = null,
        anchorView: View?
    ) {
        try {
            var isAutoScrolling = true
            var isBeingTouched = false
            var currentTextSize = 28f
            var isFirstLoad = true

            val dynamicColor = GurbaniSearchHelper.DynamicColor(getGurbaniNormalColor())
            val highlightColor = GurbaniSearchHelper.DynamicColor(getGurbaniHighlightColor())
            var currentPageOffsets: Map<String, Int> = emptyMap()

            val contentKey = folderName ?: (if (singleLine != null) "line_${singleLine.hashCode()}" else if (preFetchedLines != null) "pref_lines_${preFetchedLines.hashCode()}" else "unknown")

            isSwitchingBani = false

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = createPhotoFrameDrawable(getBgColor())
                setPadding(0, 0, 0, 0)
            }

            val headerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val gd = GradientDrawable().apply {
                    setColor(getFrameColor())
                    val r = 16.toPx().toFloat()
                    setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
                }
                background = gd
                setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }

            val parts = title.split(" ")
            if (parts.size >= 2) {
                val mid = (parts.size + 1) / 2
                val leftTv = TextView(context).apply {
                    text = parts.take(mid).joinToString(" ")
                    textSize = 18f
                    setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
                    typeface = getCustomTypeface()
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    gravity = Gravity.START
                }
                val rightTv = TextView(context).apply {
                    text = parts.drop(mid).joinToString(" ")
                    textSize = 18f
                    setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
                    typeface = getCustomTypeface()
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    gravity = Gravity.END
                }
                headerLayout.addView(leftTv)
                headerLayout.addView(rightTv)
            } else {
                val centerTv = TextView(context).apply {
                    text = title
                    textSize = 18f
                    setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
                    typeface = getCustomTypeface()
                    layoutParams = LinearLayout.LayoutParams(-1, -2)
                    gravity = Gravity.CENTER
                }
                headerLayout.addView(centerTv)
            }
            root.addView(headerLayout)
            val scroll = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
                setPadding(12.toPx(), 12.toPx(), 12.toPx(), 12.toPx())
                isVerticalScrollBarEnabled = true
                isFillViewport = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                isFocusable = false
                isFocusableInTouchMode = false
            }

            val tv = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(10.toPx(), 120.toPx(), 10.toPx(), 250.toPx())
                textSize = currentTextSize
                setTextColor(getGurbaniNormalColor())
                setLineSpacing(0f, 1.0f)
                gravity = Gravity.CENTER_HORIZONTAL
                typeface = getCustomTypeface()
                setElegantTextHeight(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
                }
                isFocusable = false
                isFocusableInTouchMode = false
            }; scroll.addView(tv)
            root.addView(scroll)

            val horizScroll = HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                setBackgroundColor(getStripColor())
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                post { fullScroll(View.FOCUS_RIGHT) }
            }
            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setPadding(8.toPx(), 0, 8.toPx(), 0)
            }

            val btnLarivaar = Button(context).apply {
                text = "ਲੜੀਵਾਰ"
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(if (isLarivaarGlobal) Color.parseColor("#4CAF50") else Color.parseColor("#EF6C00"))
                setTextColor(Color.WHITE)
                setAllCaps(false)
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()).apply { marginEnd = 8.toPx() }
            }

            val nextInfo = if (folderName != null) getNextBani(folderName) else null
            val prevInfo = if (folderName != null) getPrevBani(folderName) else null

            val btnPrev = if (prevShabadId != null || prevInfo != null) Button(context).apply {
                text = if (prevShabadId != null) "⇜ ਪਿਛਲਾ ਸ਼ਬਦ" else prevInfo?.first
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF6C00"))
                setTextColor(Color.WHITE)
                setAllCaps(false)
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()).apply { marginEnd = 8.toPx() }
            } else null

            val btnNext = if (nextShabadId != null || nextInfo != null) Button(context).apply {
                text = if (nextShabadId != null) "ਅਗਲਾ ਸ਼ਬਦ ⇝" else nextInfo?.first
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF6C00"))
                setTextColor(Color.WHITE)
                setAllCaps(false)
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()).apply { marginEnd = 8.toPx() }
            } else null

            val speedContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val gd = GradientDrawable().apply {
                    setColor(Color.parseColor("#424242"))
                    cornerRadius = 18.toPx().toFloat()
                }
                background = gd
                setPadding(8.toPx(), 0, 8.toPx(), 0)
                layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()).apply { marginEnd = 8.toPx() }
            }

            val btnSpeedDown = TextView(context).apply {
                text = "—"
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                isClickable = true
                isFocusable = true
                setPadding(8.toPx(), 0, 8.toPx(), 0)
            }

            val speedIndicator = TextView(context).apply {
                text = "$savedSpeedLevel"
                setTextColor(Color.YELLOW)
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setPadding(8.toPx(), 0, 8.toPx(), 0)
            }

            val btnSpeedUp = TextView(context).apply {
                text = "+"
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                isClickable = true
                isFocusable = true
                setPadding(8.toPx(), 0, 8.toPx(), 0)
            }

            speedContainer.addView(btnSpeedDown)
            speedContainer.addView(speedIndicator)
            speedContainer.addView(btnSpeedUp)

            val btnTop = Button(context).apply { text = "TOP"; setBackgroundResource(R.drawable.rounded_button_bg); backgroundTintList = ColorStateList.valueOf(Color.DKGRAY); setTextColor(Color.WHITE); textSize = 10f; layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()).apply { marginEnd = 8.toPx() } }
            val btnPage = Button(context).apply { text = "PAGE"; setBackgroundResource(R.drawable.rounded_button_bg); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#673AB7")); setTextColor(Color.WHITE); textSize = 10f; layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()).apply { marginEnd = 8.toPx() } }
            val btnTheme = Button(context).apply { text = if (isDarkMode) "LIGHT" else "DARK"; setBackgroundResource(R.drawable.rounded_button_bg); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#607D8B")); setTextColor(Color.WHITE); textSize = 10f; layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()).apply { marginEnd = 8.toPx() } }
            val btnClose = Button(context).apply { text = "ਬੰਦ"; setBackgroundResource(R.drawable.rounded_button_bg); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3E2723")); setTextColor(Color.WHITE); setAllCaps(false); textSize = 10f; layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()) }

            fun updateContent() {
                japjiScrollJob?.cancel()
                japjiScrollJob = uiScope.launch {
                    val lines = if (preFetchedLines != null) {
                        preFetchedLines
                    } else if (folderName != null) {
                        val rawParagraphs = withContext(Dispatchers.IO) { GurbaniSearchHelper.getGurbaniLines(context, folderName) }
                        if (rawParagraphs.isEmpty()) listOf("ਪਾਠ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ") else GurbaniSearchHelper.splitIntoSentences(rawParagraphs)
                    } else if (singleLine != null) {
                        listOf(singleLine)
                    } else listOf("No Content")

                    val (spannable, pageOffsets) = GurbaniSearchHelper.getGurbaniSpannable(lines, isLarivaarGlobal, folderName ?: "", dynamicColor = dynamicColor, highlightColor = highlightColor, customTypeface = getCustomTypeface(), pageNumberColor = getPageNumberColor())
                    currentPageOffsets = pageOffsets
                    tv.text = GurbaniUIUtils.applyIkOnkarToSpannable(context, spannable, tv.textSize, isLarivaarGlobal)

                    // Restore scroll position only on first load
                    if (isFirstLoad) {
                        val savedY = nitnemPrefs.getInt("last_pos_$contentKey", 0)
                        if (savedY > 0) {
                            delay(300) // Give more time for measurement
                            scroll.post { scroll.scrollTo(0, savedY) }
                        }
                        isFirstLoad = false
                    }

                    if (lines.size > 1) {
                        delay(100)
                        while (isActive) {
                            if (isAutoScrolling && !isBeingTouched) scroll.scrollBy(0, 1)
                            delay(when(savedSpeedLevel) {
                                1 -> 85L; 2 -> 65L; 3 -> 50L; 4 -> 40L; 5 -> 30L;
                                6 -> 22L; 7 -> 15L; 8 -> 10L; 9 -> 5L; 10 -> 2L;
                                else -> 40L
                            })
                        }
                    }
                }
            }

            scroll.viewTreeObserver.addOnScrollChangedListener {
                val y = scroll.scrollY
                if (!isSwitchingBani && !isFirstLoad && y > 0) {
                    nitnemPrefs.edit().putInt("last_pos_$contentKey", y).apply()
                }
            }

            val navSpacer = View(context).apply { layoutParams = LinearLayout.LayoutParams(-1, 0); setBackgroundColor(getStripColor()) }

            btnTheme.setOnClickListener {
                isDarkMode = !isDarkMode
                root.background = createPhotoFrameDrawable(getBgColor())

                headerLayout.apply {
                    val gd = GradientDrawable().apply {
                        setColor(getFrameColor())
                        val r = 16.toPx().toFloat()
                        setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
                    }
                    background = gd
                    setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
                }

                for (i in 0 until headerLayout.childCount) {
                    (headerLayout.getChildAt(i) as? TextView)?.setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
                }

                horizScroll.setBackgroundColor(getStripColor())
                navSpacer.setBackgroundColor(getStripColor())
                tv.setTextColor(getGurbaniNormalColor())
                dynamicColor.color = getGurbaniNormalColor()
                highlightColor.color = getGurbaniHighlightColor()
                btnTheme.text = if (isDarkMode) "LIGHT" else "DARK"

                btnPrev?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF6C00"))
                btnNext?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF6C00"))
                btnLarivaar.backgroundTintList = ColorStateList.valueOf(if (isLarivaarGlobal) Color.parseColor("#4CAF50") else Color.parseColor("#EF6C00"))
                btnPage.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#673AB7"))
                btnTheme.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#607D8B"))
                btnTop.backgroundTintList = ColorStateList.valueOf(Color.DKGRAY)
                btnClose.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3E2723"))

                this@NitnemEngine.applyPanelTheme()
                updateContent()
            }

            val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    currentTextSize *= detector.scaleFactor; currentTextSize = currentTextSize.coerceIn(14f, 75f); tv.textSize = currentTextSize; updateContent(); return true
                }
            })

            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (horizScroll.visibility == View.VISIBLE) {
                        horizScroll.visibility = View.GONE
                        navSpacer.visibility = View.GONE
                        isAutoScrolling = true
                    } else {
                        horizScroll.visibility = View.VISIBLE
                        navSpacer.visibility = View.VISIBLE
                        isAutoScrolling = false
                    }
                    return true
                }
            })

            val touchListener = View.OnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> isBeingTouched = true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isBeingTouched = false
                }
                false
            }
            scroll.setOnTouchListener(touchListener)
            tv.setOnTouchListener(touchListener)

            btnLarivaar.setOnClickListener {
                isLarivaarGlobal = !isLarivaarGlobal
                btnLarivaar.backgroundTintList = ColorStateList.valueOf(if (isLarivaarGlobal) Color.parseColor("#4CAF50") else Color.parseColor("#EF6C00"))
                updateContent()
            }
            btnTop.setOnClickListener { scroll.fullScroll(View.FOCUS_UP) }

            btnSpeedDown.setOnClickListener {
                if (savedSpeedLevel > 1) {
                    savedSpeedLevel--
                    nitnemPrefs.edit().putInt("saved_speed_level", savedSpeedLevel).apply()
                    speedIndicator.text = "$savedSpeedLevel"
                    updateContent()
                }
            }
            btnSpeedUp.setOnClickListener {
                if (savedSpeedLevel < 10) {
                    savedSpeedLevel++
                    nitnemPrefs.edit().putInt("saved_speed_level", savedSpeedLevel).apply()
                    speedIndicator.text = "$savedSpeedLevel"
                    updateContent()
                }
            }

            btnPage.setOnClickListener {
                val input = EditText(context).apply {
                    hint = "ਅੰਗ ਨੰਬਰ ਭਰੋ (1-1430)"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                }
                AlertDialog.Builder(context)
                    .setTitle("ਅੰਗ ਨੰਬਰ")
                    .setView(input)
                    .setPositiveButton("ਠੀਕ") { _, _ ->
                        val pStr = GurbaniSearchHelper.toGurmukhi(input.text.toString())
                        val offset = currentPageOffsets[pStr]
                        if (offset != null) {
                            tv.layout?.let { layout ->
                                val line = layout.getLineForOffset(offset)
                                val y = layout.getLineTop(line)
                                scroll.smoothScrollTo(0, y)
                            }
                        } else {
                            Toast.makeText(context, "ਅੰਗ ਨਹੀਂ ਲੱਭਿਆ", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("ਰੱਦ ਕਰੋ", null)
                    .show()
            }

            controls.addView(btnLarivaar)
            if (btnPrev != null) controls.addView(btnPrev)
            if (btnNext != null) controls.addView(btnNext)
            controls.addView(btnPage)
            controls.addView(btnTheme)
            controls.addView(speedContainer)
            controls.addView(btnTop)
            controls.addView(btnClose)

            horizScroll.addView(controls)
            root.addView(horizScroll)

            root.addView(navSpacer)
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                val actualNavHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                navSpacer.layoutParams.height = actualNavHeight
                navSpacer.requestLayout()
                insets
            }

            val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
            btnClose.setOnClickListener { dial.dismiss() }
            setupDialogWindow(dial, anchorView = anchorView)
            dial.show()
            dial.window?.let { window ->
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            btnNext?.setOnClickListener {
                isSwitchingBani = true
                dial.dismiss()
                if (nextShabadId != null) {
                    uiScope.launch {
                        val resp = withContext(Dispatchers.IO) { GurbaniSearchHelper.fetchFullShabad(nextShabadId) }
                        if (resp.lines.isNotEmpty()) {
                            showContentDialog(
                                title = resp.lines.getOrNull(0) ?: "ਸ਼ਬਦ", 
                                folderName = null, 
                                singleLine = null, 
                                preFetchedLines = resp.lines, 
                                prevShabadId = resp.prevId, 
                                nextShabadId = resp.nextId, 
                                anchorView = anchorView
                            )
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post { showGurbaniDialog(nextInfo!!.first, nextInfo.second, anchorView) }
                }
            }
            btnPrev?.setOnClickListener {
                isSwitchingBani = true
                dial.dismiss()
                if (prevShabadId != null) {
                    uiScope.launch {
                        val resp = withContext(Dispatchers.IO) { GurbaniSearchHelper.fetchFullShabad(prevShabadId) }
                        if (resp.lines.isNotEmpty()) {
                            showContentDialog(
                                title = resp.lines.getOrNull(0) ?: "ਸ਼ਬਦ", 
                                folderName = null, 
                                singleLine = null, 
                                preFetchedLines = resp.lines, 
                                prevShabadId = resp.prevId, 
                                nextShabadId = resp.nextId, 
                                anchorView = anchorView
                            )
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post { showGurbaniDialog(prevInfo!!.first, prevInfo.second, anchorView) }
                }
            }
            dial.setOnDismissListener {
                if (!isSwitchingBani) {
                    nitnemPrefs.edit().putInt("last_pos_$contentKey", scroll.scrollY).apply()
                }
                japjiScrollJob?.cancel()
            }
            updateContent()
        } catch (e: Exception) {
            Log.e("Gurbani", "Error showing Gurbani dialog")
            Toast.makeText(context, "ਪਾਠ ਖੋਲ੍ਹਣ ਵਿੱਚ ਦਿੱਕਤ ਆਈ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getNextBani(folder: String): Pair<String, String>? {
        return when(folder) {
            "japji_sahib" -> Pair("ਸ਼ਬਦ ਹਜਾਰੇ", "shabad-hazare")
            "shabad-hazare" -> Pair("ਜਾਪੁ ਸਾਹਿਬ", "jap_sahib")
            "jap_sahib" -> Pair("ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ", "swaye")
            "swaye" -> Pair("ਸਵੱਯੇ (ਦੀਨਨ ਕੀ)", "savaiye-deenan")
            "savaiye-deenan" -> Pair("ਚੌਪਈ ਸਾਹਿਬ", "chaupai")
            "chaupai" -> Pair("ਅਨੰਦ ਸਾਹਿਬ", "anand_sahib")
            "anand_sahib" -> Pair("ਆਸਾ ਦੀ ਵਾਰ", "asa-di-var")
            "asa-di-var" -> Pair("ਬਾਰਹ ਮਾਹਾ", "barah-maha")
            "barah-maha" -> Pair("ਰਹਿਰਾਸ ਸਾਹਿਬ", "rehras_sahib")
            "rehras_sahib" -> Pair("ਸੋਹਿਲਾ ਸਾਹਿਬ", "sohila_sahib")
            "sohila_sahib" -> Pair("ਆਰਤੀ", "aarti")
            "aarti" -> Pair("ਸਲੋਕ ਮਃ ੯", "salok-m-9")
            "salok-m-9" -> Pair("ਸ਼ਬਦ ਪਾ ੧੦", "shabad-p-10")
            "shabad-p-10" -> Pair("ਦੁਖ ਭੰਜਨੀ ਸਾਹਿਬ", "dukh-bhanjani-sahib")
            "dukh-bhanjani-sahib" -> Pair("ਰਖਿਆ ਦੇ ਸ਼ਬਦ", "rakhya-de-shabad")
            "rakhya-de-shabad" -> Pair("ਅਰਦਾਸ", "ardas")
            "ardas" -> Pair("ਅਕਾਲ ਉਸਤਤਿ", "akal-ustat")
            "akal-ustat" -> Pair("ਸੁਖਮਨੀ ਸਾਹਿਬ", "sukhmani_sahib")
            "sukhmani_sahib" -> Pair("ਵਾਰ ੧ ਤੋਂ ੭", "vaara_bhai_gurdas/part1")
            "vaara_bhai_gurdas/part1" -> Pair("ਵਾਰ ੮ ਤੋਂ ੧੪", "vaara_bhai_gurdas/part2")
            "vaara_bhai_gurdas/part2" -> Pair("ਵਾਰ ੧੫ ਤੋਂ ੨੧", "vaara_bhai_gurdas/part3")
            "vaara_bhai_gurdas/part3" -> Pair("ਵਾਰ ੨੨ ਤੋਂ ੨੮", "vaara_bhai_gurdas/part4")
            "vaara_bhai_gurdas/part4" -> Pair("ਵਾਰ ੨੯ ਤੋਂ ੩੫", "vaara_bhai_gurdas/part5")
            "vaara_bhai_gurdas/part5" -> Pair("ਵਾਰ ੩੬ ਤੋਂ ੪੧", "vaara_bhai_gurdas/part6")
            "vaara_bhai_gurdas/part6" -> Pair("ਭਾਗ ੧ (ਕਬਿੱਤ ੧-੧੦੯)", "kabit_sawaye_bhai_gurdas/part1")
            "kabit_sawaye_bhai_gurdas/part1" -> Pair("ਭਾਗ ੨ (ਕਬਿੱਤ ੧੧੦-੨੧੮)", "kabit_sawaye_bhai_gurdas/part2")
            "kabit_sawaye_bhai_gurdas/part2" -> Pair("ਭਾਗ ੩ (ਕਬਿੱਤ ੨੧੯-੩੨੭)", "kabit_sawaye_bhai_gurdas/part3")
            "kabit_sawaye_bhai_gurdas/part3" -> Pair("ਭਾਗ ੪ (ਕਬਿੱਤ ੩੨੮-੪੩੬)", "kabit_sawaye_bhai_gurdas/part4")
            "kabit_sawaye_bhai_gurdas/part4" -> Pair("ਭਾਗ ੫ (ਕਬਿੱਤ ੪੩੭-੫੪੫)", "kabit_sawaye_bhai_gurdas/part5")
            "kabit_sawaye_bhai_gurdas/part5" -> Pair("ਭਾਗ ੬ (ਕਬਿੱਤ ੫੪੬-੬੫੮)", "kabit_sawaye_bhai_gurdas/part6")
            "kabit_sawaye_bhai_gurdas/part6" -> Pair("ਸਿੱਖੀ (ਵਿਚਾਰ ਚਰਚਾ)", "sikhi")
            "kabit_sawaye_bhai_gurdas" -> Pair("ਸਿੱਖੀ (ਵਿਚਾਰ ਚਰਚਾ)", "sikhi")
            "vaara_bhai_gurdas" -> Pair("ਭਾਗ ੧ (ਕਬਿੱਤ ੧-੧੦੯)", "kabit_sawaye_bhai_gurdas/part1")
            "sikhi" -> Pair("ਸਿੱਖੀ (ਵਿਚਾਰ ਚਰਚਾ)", "sikhi")
            else -> null
        }
    }

    private fun getPrevBani(folder: String): Pair<String, String>? {
        return when(folder) {
            "shabad-hazare" -> Pair("ਜਪੁਜੀ ਸਾਹਿਬ", "japji_sahib")
            "jap_sahib" -> Pair("ਸ਼ਬਦ ਹਜਾਰੇ", "shabad-hazare")
            "swaye" -> Pair("ਜਾਪੁ ਸਾਹਿਬ", "jap_sahib")
            "savaiye-deenan" -> Pair("ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ", "swaye")
            "anand_sahib" -> Pair("ਚੌਪਈ ਸਾਹਿਬ", "chaupai")
            "asa-di-var" -> Pair("ਅਨੰਦ ਸਾਹਿਬ", "anand_sahib")
            "barah-maha" -> Pair("ਆਸਾ ਦੀ ਵਾਰ", "asa-di-var")
            "chaupai" -> Pair("ਸਵੱਯੇ (ਦੀਨਨ ਕੀ)", "savaiye-deenan")
            "rehras_sahib" -> Pair("ਬਾਰਹ ਮਾਹਾ", "barah-maha")
            "sohila_sahib" -> Pair("ਰਹਿਰਾਸ ਸਾਹਿਬ", "rehras_sahib")
            "aarti" -> Pair("ਸੋਹਿਲਾ ਸਾਹਿਬ", "sohila_sahib")
            "salok-m-9" -> Pair("ਆਰਤੀ", "aarti")
            "shabad-p-10" -> Pair("ਸਲੋਕ ਮਃ ੯", "salok-m-9")
            "dukh-bhanjani-sahib" -> Pair("ਸ਼ਬਦ ਪਾ ੧੦", "shabad-p-10")
            "rakhya-de-shabad" -> Pair("ਦੁਖ ਭੰਜਨੀ ਸਾਹਿਬ", "dukh-bhanjani-sahib")
            "ardas" -> Pair("ਰਖਿਆ ਦੇ ਸ਼ਬਦ", "rakhya-de-shabad")
            "akal-ustat" -> Pair("ਅਰਦਾਸ", "ardas")
            "sukhmani_sahib" -> Pair("ਅਕਾਲ ਉਸਤਤਿ", "akal-ustat")
            "vaara_bhai_gurdas/part1" -> Pair("ਸੁਖਮਨੀ ਸਾਹਿਬ", "sukhmani_sahib")
            "vaara_bhai_gurdas/part2" -> Pair("ਵਾਰ ੧ ਤੋਂ ੭", "vaara_bhai_gurdas/part1")
            "vaara_bhai_gurdas/part3" -> Pair("ਵਾਰ ੮ ਤੋਂ ੧੪", "vaara_bhai_gurdas/part2")
            "vaara_bhai_gurdas/part4" -> Pair("ਵਾਰ ੧੫ ਤੋਂ ੨੧", "vaara_bhai_gurdas/part3")
            "vaara_bhai_gurdas/part5" -> Pair("ਵਾਰ ੨੨ ਤੋਂ ੨੮", "vaara_bhai_gurdas/part4")
            "vaara_bhai_gurdas/part6" -> Pair("ਵਾਰ ੨੯ ਤੋਂ ੩੫", "vaara_bhai_gurdas/part5")
            "vaara_bhai_gurdas" -> Pair("ਸੁਖਮਨੀ ਸਾਹਿਬ", "sukhmani_sahib")
            "kabit_sawaye_bhai_gurdas/part1" -> Pair("ਵਾਰ ੩੬ ਤੋਂ ੪੧", "vaara_bhai_gurdas/part6")
            "kabit_sawaye_bhai_gurdas/part2" -> Pair("ਭਾਗ ੧ (ਕਬਿੱਤ ੧-੧੦੯)", "kabit_sawaye_bhai_gurdas/part1")
            "kabit_sawaye_bhai_gurdas/part3" -> Pair("ਭਾਗ ੨ (ਕਬਿੱਤ ੧੧੦-੨੧੮)", "kabit_sawaye_bhai_gurdas/part2")
            "kabit_sawaye_bhai_gurdas/part4" -> Pair("ਭਾਗ ੩ (ਕਬਿੱਤ ੨੧੯-੩੨੭)", "kabit_sawaye_bhai_gurdas/part3")
            "kabit_sawaye_bhai_gurdas/part5" -> Pair("ਭਾਗ ੪ (ਕਬਿੱਤ ੩੨੮-੪੩੬)", "kabit_sawaye_bhai_gurdas/part4")
            "kabit_sawaye_bhai_gurdas/part6" -> Pair("ਭਾਗ ੫ (ਕਬਿੱਤ ੪੩੭-੫੪੫)", "kabit_sawaye_bhai_gurdas/part5")
            "kabit_sawaye_bhai_gurdas" -> Pair("ਵਾਰ ੩੬ ਤੋਂ ੪੧", "vaara_bhai_gurdas/part6")
            "sikhi" -> Pair("ਭਾਗ ੬ (ਕਬਿੱਤ ੫੪੬-੬੫੮)", "kabit_sawaye_bhai_gurdas/part6")
            else -> null
        }
    }

    private fun showGurbaniSearchDialog(anchorView: View?) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(14.toPx(), 14.toPx(), 14.toPx(), 14.toPx())
        }

        val header = RelativeLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            val gd = GradientDrawable().apply {
                setColor(getFrameColor())
                val r = 16.toPx().toFloat()
                setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            }
            background = gd
            setPadding(16.toPx(), 18.toPx(), 16.toPx(), 16.toPx())
        }

        val titleTv = TextView(context).apply {
            text = "ਗੁਰਬਾਣੀ ਖੋਜ (ਪਹਿਲੇ ਅੱਖਰ)"
            textSize = 20f
            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            typeface = getCustomTypeface()
            val lp = RelativeLayout.LayoutParams(-2, -2)
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL)
            layoutParams = lp
        }
        header.addView(titleTv)

        val closeBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            val lp = RelativeLayout.LayoutParams(48.toPx(), 48.toPx())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams = lp
        }
        header.addView(closeBtn)
        root.addView(header)

        val searchContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.toPx(), 16.toPx(), 16.toPx(), 8.toPx())
            gravity = Gravity.CENTER_VERTICAL
        }

        val input = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            hint = "ਅੱਖਰ ਲਿਖੋ (ਜਿਵੇਂ: ਮ ਮ ਲ)"
            setTextColor(getTextColor())
            setHintTextColor(if (isDarkMode) Color.GRAY else Color.LTGRAY)
            typeface = getCustomTypeface()
        }
        searchContainer.addView(input)

        val searchExecBtn = Button(context).apply {
            text = "ਖੋਜੋ"
            setBackgroundResource(R.drawable.rounded_button_bg)
            setTextColor(Color.WHITE)
            typeface = getCustomTypeface()
        }
        searchContainer.addView(searchExecBtn)
        root.addView(searchContainer)

        val progress = ProgressBar(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(48.toPx(), 48.toPx()).apply {
                gravity = Gravity.CENTER
                topMargin = 20.toPx()
            }
        }
        root.addView(progress)

        val resultsList = mutableListOf<GurbaniSearchHelper.SearchItem>()
        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            setPadding(16.toPx(), 8.toPx(), 16.toPx(), 120.toPx())
            clipToPadding = false
        }

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val btn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.toPx(), 0, 4.toPx()) }
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    setTextColor(Color.WHITE)
                    setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
                    transformationMethod = null
                    typeface = getCustomTypeface()
                    textSize = 14f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                return object : RecyclerView.ViewHolder(btn) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = resultsList[position]
                (holder.itemView as Button).apply {
                    text = item.text
                    setOnClickListener {
                        progress.visibility = View.VISIBLE
                        uiScope.launch {
                            val resp = withContext(Dispatchers.IO) {
                                GurbaniSearchHelper.fetchFullShabad(item.shabadId)
                            }
                            progress.visibility = View.GONE
                            if (resp.lines.isNotEmpty()) {
                                showContentDialog(
                                    title = item.text, 
                                    folderName = null, 
                                    singleLine = null, 
                                    preFetchedLines = resp.lines, 
                                    prevShabadId = resp.prevId, 
                                    nextShabadId = resp.nextId, 
                                    anchorView = anchorView
                                )
                            } else {
                                Toast.makeText(context, "ਪੂਰਾ ਸ਼ਬਦ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            override fun getItemCount() = resultsList.size
        }
        rv.adapter = adapter
        root.addView(rv)

        val navSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0)
            setBackgroundColor(getBgColor())
        }
        root.addView(navSpacer)

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        var searchJob: Job? = null
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                val query = s?.toString() ?: ""
                if (query.length < 1) {
                    resultsList.clear()
                    adapter.notifyDataSetChanged()
                    return
                }
                searchJob = uiScope.launch {
                    delay(300) // Debounce for 300ms
                    progress.visibility = View.VISIBLE
                    val results = withContext(Dispatchers.IO) {
                        GurbaniSearchHelper.searchGurbani(query)
                    }
                    resultsList.clear()
                    resultsList.addAll(results)
                    adapter.notifyDataSetChanged()
                    progress.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchExecBtn.setOnClickListener {
            val query = input.text.toString()
            if (query.isBlank()) return@setOnClickListener

            progress.visibility = View.VISIBLE
            rv.visibility = View.GONE
            uiScope.launch {
                val results = withContext(Dispatchers.IO) {
                    GurbaniSearchHelper.searchGurbani(query)
                }
                resultsList.clear()
                resultsList.addAll(results)
                adapter.notifyDataSetChanged()
                progress.visibility = View.GONE
                rv.visibility = View.VISIBLE
                if (results.isEmpty()) {
                    Toast.makeText(context, "ਕੋਈ ਨਤੀਜਾ ਨਹੀਂ ਮਿਲਿਆ", Toast.LENGTH_SHORT).show()
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val actualNavHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navSpacer.layoutParams.height = actualNavHeight
            navSpacer.requestLayout()
            insets
        }

        setupDialogWindow(dial, anchorView = anchorView)
        dial.show()
    }

    private fun Int.toPx(): Int = (this * context.resources.displayMetrics.density).toInt()
}
