package com.iqbal.gurmukhikeyboard50

import android.annotation.SuppressLint
import android.app.DownloadManager
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
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.text.Layout
import androidx.core.view.WindowCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class NitnemEngine(private val context: Context, private val onDismiss: () -> Unit) {
    private val nitnemPrefs: SharedPreferences = context.getSharedPreferences("NitnemPrefs", Context.MODE_PRIVATE)
    private var savedSpeedLevel: Int = nitnemPrefs.getInt("saved_speed_level", 3)
    private var isSwitchingBani = false
    private var isLarivaarGlobal = false
    private var japjiScrollJob: Job? = null
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var isAudioPlaying = false
    private var audioProgressJob: Job? = null

    fun stopMusic() {
        japjiScrollJob?.cancel()
        japjiScrollJob = null
        audioProgressJob?.cancel()
        audioProgressJob = null
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            Log.e("NitnemEngine", "Error releasing LoudnessEnhancer", e)
        }
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            }
        } catch (e: Exception) {
            Log.e("NitnemEngine", "Error stopping mediaPlayer", e)
        }
        mediaPlayer = null
        isAudioPlaying = false
    }

    private fun getAudioUrlsForBani(folderName: String?): List<String> {
        return when (folderName) {
            "japji_sahib" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/01_Japji_Sahib.mp3",
                "https://media.sikhnet.com/Japji_Sahib.mp3",
                "https://archive.org/download/JapjiSahibAudio/JapjiSahib.mp3"
            )
            "jap_sahib" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/02_Jaap_Sahib.mp3",
                "https://media.sikhnet.com/Jaap_Sahib.mp3"
            )
            "swaye" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/03_Tav_Prasad_Savaiye.mp3",
                "https://media.sikhnet.com/Tav_Prasad_Savaiye.mp3"
            )
            "chaupai" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/04_Chaupai_Sahib.mp3",
                "https://media.sikhnet.com/Chaupai_Sahib.mp3"
            )
            "anand_sahib" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/05_Anand_Sahib.mp3",
                "https://media.sikhnet.com/Anand_Sahib.mp3"
            )
            "rehras_sahib" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/06_Rehras_Sahib.mp3",
                "https://media.sikhnet.com/Rehras_Sahib.mp3"
            )
            "sohila_sahib" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/07_Sohila_Sahib.mp3",
                "https://media.sikhnet.com/Sohila_Sahib.mp3"
            )
            "sukhmani_sahib" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/Sukhmani_Sahib.mp3"
            )
            "asa-di-var" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/Asa_Di_Var.mp3"
            )
            "dukh-bhanjani-sahib" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/Dukh_Bhanjani_Sahib.mp3"
            )
            "ardas" -> listOf(
                "https://archive.org/download/NitnemAudioHighQuality/Ardas.mp3"
            )
            else -> emptyList()
        }
    }

    private fun getAudioUrlForBani(folderName: String?): String? {
        return getAudioUrlsForBani(folderName).firstOrNull()
    }

    private fun formatAudioTime(millis: Int): String {
        if (millis <= 0) return "00:00"
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun getNitnemWebUrl(folderName: String?): String? {
        return when (folderName) {
            "japji_sahib" -> "https://nitnemsahib.com/japji-sahib-in-punjabi-gurmukhi/"
            "jap_sahib" -> "https://nitnemsahib.com/jaap-sahib-in-punjabi-gurmukhi/"
            "swaye" -> "https://nitnemsahib.com/tav-prasad-savaiye-in-punjabi-gurmukhi/"
            "chaupai" -> "https://nitnemsahib.com/chaupai-sahib-in-punjabi-gurmukhi/"
            "anand_sahib" -> "https://nitnemsahib.com/anand-sahib-in-punjabi-gurmukhi/"
            else -> null
        }
    }

    private fun getLocalAudioFile(folderName: String): File {
        val fileName = "${folderName}_audio.mp3"
        return File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
    }

    private fun hasAssetAudio(folderName: String?): Boolean {
        if (folderName == null) return false
        return try {
            val afd = context.assets.openFd("audio/${folderName}.mp3")
            afd.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun startAudioProgressTracker(seekBar: SeekBar, tvTime: TextView) {
        audioProgressJob?.cancel()
        audioProgressJob = uiScope.launch {
            while (isActive && mediaPlayer != null && isAudioPlaying) {
                try {
                    val current = mediaPlayer?.currentPosition ?: 0
                    val total = mediaPlayer?.duration ?: 0
                    if (total > 0) {
                        seekBar.max = total
                        seekBar.progress = current
                        tvTime.text = "${formatAudioTime(current)} / ${formatAudioTime(total)}"
                    }
                } catch (e: Exception) {
                    break
                }
                delay(1000)
            }
        }
    }

    private fun getCustomTypeface(): Typeface {
        val fontFileName = "NotoSerifGurmukhi-Regular.ttf"
        return try {
            Typeface.createFromAsset(context.assets, "fonts/$fontFileName")
        } catch (e: Exception) {
            try {
                Typeface.createFromAsset(context.assets, "fonts/AKHAR.ttf.TTF")
            } catch (e2: Exception) { Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        }
    }

    private fun setupDialogWindow(dial: AlertDialog, focusable: Boolean = true, anchorView: View?, rootView: View? = null) {
        dial.window?.let { window ->
            // Allow content to extend behind system bars, we will handle insets manually
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Set window background color and status bar style based on Nitnem theme
            window.setBackgroundDrawable(ColorDrawable(getBgColor()))

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDarkMode
            insetsController.isAppearanceLightNavigationBars = !isDarkMode

            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

            if (rootView != null) {
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    // Set bottom padding for the ROOT of the dialog to avoid navigation buttons
                    // This will move everything UP by the navigation bar height
                    // We don't set top padding here to allow frame to touch the top edge
                    v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
                    insets
                }
            }

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
            // Set padding to ensure children don't cover the frame's border (12dp stroke)
            view.setPadding(0, 0, 0, 12.toPx())

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

                // Centered title for the front page
                val fullTitle = "ਨਿਤਨੇਮ ਅਤੇ ਬਾਣੀਆਂ"
                titleTv?.text = fullTitle
                titleTv?.gravity = Gravity.CENTER
                titleTv?.textSize = 18f

                // Ensure the right part (if exists from previous loads) is removed
                val rightTv = container.findViewWithTag<View>("right_title")
                if (rightTv != null) {
                    container.removeView(rightTv)
                }
            }

            titleTv?.setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            titleTv?.typeface = getCustomTypeface()
            titleTv?.textSize = 18f

            val searchBtn = mainNitnemView?.findViewById<ImageButton>(R.id.btn_search_nitnem)
            searchBtn?.setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            themeBtn?.setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
            closeBtn?.setColorFilter(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))

            // Adjust RecyclerView container to be inside the frame (12dp stroke + 4dp gap = 16dp)
            recyclerView?.parent?.let { parent ->
                val lp = (parent as? View)?.layoutParams as? LinearLayout.LayoutParams
                lp?.setMargins(16.toPx(), 0, 16.toPx(), 0)
            }

            // Adjust promo container to be inside the frame
            mainNitnemView?.findViewById<LinearLayout>(R.id.promo_container)?.let { promo ->
                val lp = promo.layoutParams as? LinearLayout.LayoutParams
                lp?.setMargins(16.toPx(), 0, 16.toPx(), 16.toPx())
            }

            recyclerView?.adapter?.notifyDataSetChanged()
        }
    }

    fun setupNitnemPanel(nitnemView: View) {
        this.mainNitnemView = nitnemView
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
            BaniItem("🔊 ਜਪੁਜੀ ਸਾਹਿਬ (ਆਵਾਜ਼ ਨਾਲ)", "japji_sahib"),  BaniItem("🔊 ਜਾਪੁ ਸਾਹਿਬ (ਆਵਾਜ਼ ਨਾਲ)", "jap_sahib"),BaniItem("🔊 ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ (ਆਵਾਜ਼ ਨਾਲ)","swaye"), BaniItem("🔊 ਚੌਪਈ ਸਾਹਿਬ (ਆਵਾਜ਼ ਨਾਲ)", "chaupai"),
            BaniItem("🔊 ਅਨੰਦ ਸਾਹਿਬ (ਆਵਾਜ਼ ਨਾਲ)", "anand_sahib"), BaniItem("🔊 ਆਸਾ ਦੀ ਵਾਰ (ਆਵਾਜ਼ ਨਾਲ)", "asa-di-var"), BaniItem("ਬਾਰਹ ਮਾਹਾ", "barah-maha"),
            BaniItem("🔊 ਰਹਿਰਾਸ ਸਾਹਿਬ (ਆਵਾਜ਼ ਨਾਲ)", "rehras_sahib"), BaniItem("🔊 ਸੋਹਿਲਾ ਸਾਹਿਬ (ਆਵਾਜ਼ ਨਾਲ)", "sohila_sahib"), BaniItem("🔊 ਆਰਤੀ (ਆਵਾਜ਼ ਨਾਲ)", "aarti"),
            BaniItem("🔊 ਸੁਖਮਨੀ ਸਾਹਿਬ (ਆਵਾਜ਼ ਨਾਲ)", "sukhmani_sahib"), BaniItem("🔊 ਦੁਖ ਭੰਜਨੀ ਸਾਹਿਬ (ਆਵਾਜ਼ ਨਾਲ)", "dukh-bhanjani-sahib"),
            BaniItem("ਸਲੋਕ ਮਃ ੯", "salok-m-9"), BaniItem("ਸ਼ਬਦ ਪਾ ੧੦", "shabad-p-10"),
            BaniItem("ਰਖਿਆ ਦੇ ਸ਼ਬਦ", "rakhya-de-shabad"), BaniItem("ਅਰਦਾਸ", "ardas"), BaniItem("ਅਕਾਲ ਉਸਤਤਿ", "akal-ustat"),   BaniItem("ਸਵੱਯੇ (ਦੀਨਨ ਕੀ)", "savaiye-deenan"),
            BaniItem("ਵਾਰਾਂ ਭਾਈ ਗੁਰਦਾਸ ਜੀ", "vaara_bhai_gurdas"),
            BaniItem("ਕਬਿੱਤ ਸਵੱਯੇ ਭਾਈ ਗੁਰਦਾਸ ਜੀ", "kabit_sawaye_bhai_gurdas"),
            BaniItem("ਗੁਰੂ ਸਾਹਿਬਾਨ: ਮਹੱਤਵਪੂਰਨ ਤਾਰੀਖਾਂ", "gurus_info"),
            BaniItem("ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ", "guru_granth_sahib"),
            BaniItem("ਸ੍ਰੀ ਦਸਮ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ", "dasam_granth"),
            BaniItem("ਸਿੱਖੀ (ਵਿਚਾਰ ਚਰਚਾ)", "sikhi"),
            BaniItem("ਗੁਰਬਾਣੀ ਖੋਜ (ਸਰਚ)", "search_gurbani"),
            BaniItem("ਜੀਵਨ ਸਿੱਖਿਆ (Gurbani Lessons)", "gurbani_lessons"),
            BaniItem("ਪੁਸਤਕ ਸੰਖੇਪ (Book Summaries)", "book_summaries"),
            BaniItem("ਬਹੁਮੁੱਲੀਆਂ ਪੁਸਤਕਾਂ (PDF)", "pdf_books")
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
                    setBackgroundResource(R.drawable.rounded_button_bg); setTextColor(Color.WHITE); setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx()); transformationMethod = null
                    val tf = getCustomTypeface()
                    typeface = tf
                    setTypeface(tf, Typeface.NORMAL)
                    paint.isFakeBoldText = false
                    textSize = 16f
                }; return object : RecyclerView.ViewHolder(btn) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = displayList[position]; (holder.itemView as Button).apply {
                    text = item.name
                    val tf = getCustomTypeface()
                    typeface = tf
                    setTypeface(tf, Typeface.NORMAL)
                    paint.isFakeBoldText = false
                    setOnClickListener {
                        when (item.folder) {
                            "search_gurbani" -> showGurbaniSearchDialog(nitnemView)
                            "gurbani_lessons" -> showGurbaniLessonsDialog(nitnemView)
                            "book_summaries" -> showBookSummariesDialog(nitnemView)
                            "guru_granth_sahib" -> showRagasDialog(nitnemView)
                            "dasam_granth" -> showDasamGranthDialog(nitnemView)
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

        recyclerView.setPadding(0, 0, 0, 140.toPx())
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

        val parentRoot = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(0, 0, 0, 14.toPx())
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        parentRoot.addView(root)

        ViewCompat.setOnApplyWindowInsetsListener(parentRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
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

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(parentRoot).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val btn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.toPx(), 0, 4.toPx()) }
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    setTextColor(Color.WHITE)
                    setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
                    transformationMethod = null
                    val tf = getCustomTypeface()
                    typeface = tf
                    setTypeface(tf, Typeface.NORMAL)
                    paint.isFakeBoldText = false
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
        setupDialogWindow(dial, anchorView = anchorView, rootView = parentRoot)
        dial.show()
    }

    private fun showDasamGranthDialog(anchorView: View?) {
        val banis = listOf(
            BaniItem("ਜਾਪੁ ਸਾਹਿਬ", "jap_sahib"),
            BaniItem("ਅਕਾਲ ਉਸਤਤਿ", "akal-ustat"),
            BaniItem("ਸ਼ਬਦ ਹਜਾਰੇ ਪਾਤਸ਼ਾਹੀ ੧੦", "shabad-p-10"),
            BaniItem("ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ (ਸ੍ਰਾਵਗ ਸੁਧ)", "swaye"),
            BaniItem("ਸਵੱਯੇ (ਦੀਨਨ ਕੀ)", "savaiye-deenan"),
            BaniItem("ਚੌਪਈ ਸਾਹਿਬ", "chaupai"),
            BaniItem("ਹੋਰ ਬਾਣੀਆਂ (ਸਰਚ ਕਰੋ)", "search_dasam")
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
            text = "ਸ੍ਰੀ ਦਸਮ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ"
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
            setPadding(16.toPx(), 8.toPx(), 16.toPx(), 120.toPx())
            clipToPadding = false
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
                    val tf = getCustomTypeface()
                    typeface = tf
                    setTypeface(tf, Typeface.NORMAL)
                    paint.isFakeBoldText = false
                    textSize = 14f
                }
                return object : RecyclerView.ViewHolder(btn) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = banis[position]
                (holder.itemView as Button).apply {
                    text = item.name
                    setOnClickListener {
                        if (item.folder == "search_dasam") {
                            dial.dismiss()
                            showGurbaniSearchDialog(anchorView)
                        } else {
                            showGurbaniDialog(item.name, item.folder, anchorView)
                        }
                    }
                }
            }
            override fun getItemCount() = banis.size
        }
        root.addView(rv)
        setupDialogWindow(dial, anchorView = anchorView, rootView = root)
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
                    val tf = getCustomTypeface()
                    typeface = tf
                    setTypeface(tf, Typeface.NORMAL)
                    paint.isFakeBoldText = false
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
        setupDialogWindow(dial, anchorView = anchorView, rootView = root)
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
        setupDialogWindow(dial, anchorView = anchorView, rootView = root)
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
                    val tf = getCustomTypeface()
                    typeface = tf
                    setTypeface(tf, Typeface.NORMAL)
                    paint.isFakeBoldText = false
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
        setupDialogWindow(dial, anchorView = anchorView, rootView = root)
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
                val tf = getCustomTypeface()
                typeface = tf
                setTypeface(tf, Typeface.NORMAL)
                paint.isFakeBoldText = false
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

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        setupDialogWindow(dial, anchorView = anchorView, rootView = root)
        dial.show()
    }

    fun showBooksDialog(anchorView: View? = null) {
        val defaultBooks = listOf(
            // --- Giani Gian Singh Collection (Azure Vidhia CDN) ---
            PdfBook("ਰਾਜ ਖਾਲਸਾ (ਭਾਗ ੧) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Raj%20Khalsa%20Part%20I.pdf", "raj_khalsa_1.pdf"),
            PdfBook("ਰਾਜ ਖਾਲਸਾ (ਭਾਗ ੨) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Raj%20Khalsa%20Part%20II.pdf", "raj_khalsa_2.pdf"),
            PdfBook("ਸ਼ਮਸ਼ੇਰ ਖਾਲਸਾ (ਭਾਗ ੧) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Shamsher%20Khalsa%20Part%20I.pdf", "shamsher_khalsa_1.pdf"),
            PdfBook("ਸ਼ਮਸ਼ੇਰ ਖਾਲਸਾ (ਭਾਗ ੨) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Shamsher%20Khalsa%20Part%20II.pdf", "shamsher_khalsa_2.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੧) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2001).pdf", "twarikh_patshahi_01.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੨) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2002).pdf", "twarikh_patshahi_02.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੩) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2003).pdf", "twarikh_patshahi_03.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੪) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2004).pdf", "twarikh_patshahi_04.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੫) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2005).pdf", "twarikh_patshahi_05.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੬) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2006).pdf", "twarikh_patshahi_06.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੭) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2007).pdf", "twarikh_patshahi_07.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੮) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2008).pdf", "twarikh_patshahi_08.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੦੯) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2009).pdf", "twarikh_patshahi_09.pdf"),
            PdfBook("ਤਵਾਰੀਖ ਗੁਰੂ ਖਾਲਸਾ (ਪਾਤਸ਼ਾਹੀ ੧੦) - ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Giani%20Gian%20Singh/Twarikh%20Guru%20Khalsa%20(Patshahi%2010).pdf", "twarikh_patshahi_10.pdf"),

            // --- Vidhia.com Collection ---
            PdfBook("ਜਪੁਜੀ ਸਾਹਿਬ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Jap_Ji_Sahib_-_Gurmukhi.pdf", "vidhia_japji_sahib.pdf"),
            PdfBook("ਜਾਪੁ ਸਾਹਿਬ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Jaap_Sahib_-_Gurmukhi.pdf", "vidhia_jaap_sahib.pdf"),
            PdfBook("ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Tva_Prasad_Svaye_-_Gurmukhi.pdf", "vidhia_tva_prasad_svaye.pdf"),
            PdfBook("ਚੌਪਈ ਸਾਹਿਬ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Chaupai_Sahib.pdf", "vidhia_chaupai_sahib.pdf"),
            PdfBook("ਆਨੰਦੁ ਸਾਹਿਬ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Anand_Sahib_-_Gurmukhi.pdf", "vidhia_anand_sahib.pdf"),
            PdfBook("ਰਹਿਰਾਸਿ ਸਾਹਿਬ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Rehiras_Sahib_-_Gurmukhi.pdf", "vidhia_rehiras_sahib.pdf"),
            PdfBook("ਰਹਿਰਾਸਿ ਸਾਹਿਬ (ਸੰਪੂਰਨ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Rehiras_Sahib_-_Sampuran.pdf", "vidhia_rehiras_sampuran.pdf"),
            PdfBook("ਸੋਹਿਲਾ ਸਾਹਿਬ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Sohila_Sahib_-_Gurmukhi.pdf", "vidhia_sohila_sahib.pdf"),
            PdfBook("ਸੋਹਿਲਾ ਸਾਹਿਬ (ਟ੍ਰਾਂਸਲਿਟਰੇਸ਼ਨ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Sohila_Sahib_with_Transliteration.pdf", "vidhia_sohila_transliteration.pdf"),
            PdfBook("ਆਸਾ ਦੀ ਵਾਰ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Asa_Di_Var_-_Gurmukhi.pdf", "vidhia_asa_di_var.pdf"),
            PdfBook("ਸੁਖਮਨੀ ਸਾਹਿਬ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Sukhmani_Sahib_-_Gurmukhi.pdf", "vidhia_sukhmani_sahib.pdf"),
            PdfBook("ਸੁਖਮਨਾ ਸਾਹਿਬ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Sukhmana_Sahib_-_Gurmukhi.pdf", "vidhia_sukhmana_sahib.pdf"),
            PdfBook("ਦੁਖ ਭੰਜਨੀ ਸਾਹਿਬ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Dukh_Bhanjani_Sahib.pdf", "vidhia_dukh_bhanjani.pdf"),
            PdfBook("ਸ਼ਬਦ ਹਜ਼ਾਰੇ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Shabad_Hazarey_-_Gurmukhi.pdf", "vidhia_shabad_hazarey.pdf"),
            PdfBook("ਬਾਰਹ ਮਾਹਾ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Baareh_Maahaa_-_Gurmukhi.pdf", "vidhia_baareh_maahaa.pdf"),
            PdfBook("ਨਿਤਨੇਮ (SGPC) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Nitnem_(SGPC).pdf", "vidhia_nitnem_sgpc.pdf"),
            PdfBook("ਨਿਤਨੇਮ ਸਰਲ ਸਟੀਕ (ਪ੍ਰੋ. ਸਾਹਿਬ ਸਿੰਘ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Professor_Sahib_Singh/Nitnem%20Steek-Prof%20Sahib%20Singh-Punjabi.pdf", "vidhia_nitnem_sahib_singh.pdf"),
            PdfBook("ਨਿਤਨੇਮ ਸਰਲ ਸਟੀਕ (ਭਾਈ ਜੋਗਿੰਦਰ ਸਿੰਘ ਤਲਵਾੜਾ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bhai_Joginder_Singh_Ji_Talwara/Nitnem-Saral-Steek-Punjabi.pdf", "vidhia_nitnem_talwara.pdf"),
            PdfBook("ਜਪੁਜੀ ਸਾਹਿਬ ਸਰਲ ਸਟੀਕ (ਭਾਈ ਜੋਗਿੰਦਰ ਸਿੰਘ ਤਲਵਾੜਾ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bhai_Joginder_Singh_Ji_Talwara/Jap-Ji-Sahib-Saral-Steek-Punjabi.pdf", "vidhia_japji_talwara.pdf"),
            PdfBook("ਪੰਜ ਗ੍ਰੰਥੀ (ਪੰਜਾਬੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Panj_Granthi-Punjabi.pdf", "vidhia_panj_granthi.pdf"),
            PdfBook("ਦਸ ਗ੍ਰੰਥੀ (SGPC) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Das_Granthi_(SGPC).pdf", "vidhia_das_granthi_sgpc.pdf"),
            PdfBook("ਦਸ ਗ੍ਰੰਥੀ ਪੋਥੀ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Das_Granthi_Pothi.pdf", "vidhia_das_granthi_pothi.pdf"),
            PdfBook("ਚੰਡੀ ਦੀ ਵਾਰ ਸਟੀਕ (ਪੰਡਿਤ ਨਰਾਇਣ ਸਿੰਘ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Chandi_Di_Vaar_Steek_-_Pandit_Narain_Singh_Ji.pdf", "vidhia_chandi_di_vaar.pdf"),
            PdfBook("ਬਚਿੱਤਰ ਨਾਟਕ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Bachitar_Natak-Punjabi.pdf", "vidhia_bachitar_natak.pdf"),
            PdfBook("ਅੰਮ੍ਰਿਤ ਕੀਰਤਨ (ਖਾਲਸਾ ਬ੍ਰਦਰਜ਼) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Amrit_Keertan_(Khalsa_Brothers).pdf", "vidhia_amrit_keertan.pdf"),
            PdfBook("ਸ੍ਰੀ ਸਰਬਲੋਹ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਸਟੀਕ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Complete-Sri-Sarbloh-Granth-Sahib-Ji-Steek.pdf", "vidhia_sarbloh_steek.pdf"),
            PdfBook("ਵਾਰਾਂ ਭਾਈ ਗੁਰਦਾਸ ਜੀ (ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Vaaraan_Bhai_Gurdaas_Ji_-_Gurmukhi.pdf", "vidhia_vaaraan_bhai_gurdaas.pdf"),
            PdfBook("ਉਗ੍ਰਦੰਤੀ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Ugardanti.pdf", "vidhia_ugardanti.pdf"),
            PdfBook("ਫ਼ਤੇਹਨਾਮਾ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Fatehnama.pdf", "vidhia_fatehnama.pdf"),
            PdfBook("ਜ਼ਫ਼ਰਨਾਮਾ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Zafarnama.pdf", "vidhia_zafarnama.pdf"),
            PdfBook("ਕਾਮ ਬਾਰੇ ਸ਼ਬਦ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Kaam_Baare_Shabad.pdf", "vidhia_kaam_baare_shabad.pdf"),
            PdfBook("ਸ੍ਰੀ ਦਸਮ ਗ੍ਰੰਥ ਸਾਹਿਬ ਟੀਕਾ (ਭਾਗ ੧ - ਗਿਆਨੀ ਬਿਸ਼ਨ ਸਿੰਘ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Sri_Dasam_Granth_Sahib_Teeka_Part_1_-_Giani_Bishan_Singh_Ji.pdf", "vidhia_dasam_teeka_1.pdf"),
            PdfBook("ਸ੍ਰੀ ਦਸਮ ਗ੍ਰੰਥ ਸਾਹਿਬ ਟੀਕਾ (ਭਾਗ ੨ - ਗਿਆਨੀ ਬਿਸ਼ਨ ਸਿੰਘ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Bani/Sri_Dasam_Granth_Sahib_Teeka_Part_2_-_Giani_Bishan_Singh_Ji.pdf", "vidhia_dasam_teeka_2.pdf"),
            PdfBook("ਗੁਰਮਤਿ ਰਹਿਤ ਮਰਯਾਦਾ (ਦਮਦਮੀ ਟਕਸਾਲ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Rehat_Maryada/Gurmat_Rehat_Maryada_-_Damdami_Taksal.pdf", "vidhia_rehat_taksal.pdf"),
            PdfBook("ਸਿੱਖ ਰਹਿਤ ਮਰਯਾਦਾ (SGPC ਗੁਰਮੁਖੀ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Rehat_Maryada/SGPC_Sikh_Rehat_Maryada_-_Gurmukhi.pdf", "vidhia_rehat_sgpc.pdf"),
            PdfBook("ਸਿੱਖ ਧਰਮ ਮਰਯਾਦਾ (ਬੁੱਢਾ ਦਲ) - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Rehat_Maryada/Sikh_Dharam_Maryada_-_Buddha_Dal.pdf", "vidhia_rehat_buddha_dal.pdf"),
            PdfBook("ਰਹਿਤਨਾਮਾ ਭਾਈ ਚੌਪਾ ਸਿੰਘ ਜੀ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Rehat_Maryada/Rahitnama_Bhai_Chaupa_Singh_Ji_-_With_Commentary_by_Amrit_Pal_Singh_Amrit.pdf", "vidhia_rehat_chaupa_singh.pdf"),
            PdfBook("ਰਹਿਤਨਾਮਾ ਪ੍ਰਹਲਾਦ ਸਿੰਘ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Rehat_Maryada/Rehatnama_-_Prahlad_Singh.pdf", "vidhia_rehat_prahlad_singh.pdf"),
            PdfBook("ਰਹਿਤਨਾਮਾ ਭਾਈ ਦੇਸਾ ਸਿੰਘ ਜੀ - Vidhia.com", "https://gurbani-audios-c4abhzghhnccd5gj.z01.azurefd.net/database/vidhia.com/Rehat_Maryada/Rehatnama_Bhai_Desa_Singh_Ji.pdf", "vidhia_rehat_desa_singh.pdf"),

            // --- Historical & General Collection ---
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
            PdfBook("ਗੁਰ ਬਾਲਮ ਸਾਖੀਆਂ (ਸ੍ਰੀ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ)", "https://archive.org/download/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk.pdf", "gur_balam_sakhian.pdf"),
            PdfBook("ਜ਼ਫ਼ਰਨਾਮਾ (ਸਟੀਕ - ਸੰਤ ਅਮੀਰ ਸਿੰਘ)", "https://archive.org/download/zafarnama-steek-as/zafarnama-steek-as.pdf", "zafarnama_steek_as.pdf"),
            PdfBook("ਜੀਵਨ ਬ੍ਰਿਤਾਂਤ ਸ੍ਰੀ ਗੁਰੂ ਗੋਬਿੰਦ ਸਿੰਘ ਜੀ - ਭਾਈ ਵੀਰ ਸਿੰਘ", "https://archive.org/download/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk.pdf", "life_guru_gobind_singh_vir.pdf"),
            PdfBook("ਦਸ਼ਮੇਸ਼ ਪ੍ਰਕਾਸ਼ (ਇਤਿਹਾਸ ਪਾਤਸ਼ਾਹੀ ੧੦)", "https://archive.org/download/DashmeshParkashKartarSingh/Dashmesh%20Parkash%20-%20Kartar%20Singh.pdf", "dashmesh_parkash.pdf"),
            PdfBook("ਜੀਵਨ ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ (ਪ੍ਰੋ. ਕਰਤਾਰ ਸਿੰਘ)", "https://archive.org/download/LifeOfGuruNanakDev-ProfKartarSingh/Life%20of%20Guru%20Nanak%20Dev%20-%20Prof%20Kartar%20Singh.pdf", "life_guru_nanak_kartar.pdf"),
            PdfBook("ਸ੍ਰੀ ਅਸ਼ਟ ਗੁਰ ਚਮਤਕਾਰ (ਭਾਗ ੧ & ੨)", "https://archive.org/download/SriAshtGuruChamatkar-Part1And2/Sri%20Asht%20Guru%20Chamatkar%20-%20Part%201%20And%202.pdf", "asht_gur_chamatkar_1.pdf"),
            PdfBook("ਸ੍ਰੀ ਅਸ਼ਟ ਗੁਰ ਚਮਤਕਾਰ (ਭਾਗ ੩)", "https://archive.org/download/SriAshtGuruChamatkar-Part3/Sri%20Asht%20Guru%20Chamatkar%20-%20Part%203.pdf", "asht_gur_chamatkar_3.pdf"),
            PdfBook("ਸੂਰਜ ਪ੍ਰਕਾਸ਼ (ਸ੍ਰੀ ਗੁਰ ਪ੍ਰਤਾਪ ਸੂਰਜ ਗ੍ਰੰਥ)", "https://archive.org/download/gur_pratap_suraj_granth/gur_pratap_suraj_granth.pdf", "suraj_parkash.pdf"),
            PdfBook("ਸ੍ਰੀ ਗੁਰੂ ਪੰਥ ਪ੍ਰਕਾਸ਼ (ਰਤਨ ਸਿੰਘ ਭੰਗੂ)", "https://archive.org/download/prachin-panth-prakash/Prachin%20Panth%20Prakash.pdf", "panth_parkash_rsb.pdf"),
            PdfBook("ਨਵੀਨ ਪੰਥ ਪ੍ਰਕਾਸ਼ (ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ) ਭਾਗ ੧", "https://archive.org/download/PanthParkash1/Panth_Parkash_1.pdf", "naveen_panth_parkash_1.pdf"),
            PdfBook("ਨਵੀਨ ਪੰਥ ਪ੍ਰਕਾਸ਼ (ਗਿਆਨੀ ਗਿਆਨ ਸਿੰਘ) ਭਾਗ ੨", "https://archive.org/download/PanthParkash2/Panth_Parkash_2.pdf", "naveen_panth_parkash_2.pdf"),
            PdfBook("ਗਿਆਨ ਰਤਨਾਵਲੀ (ਭਾਈ ਮਨੀ ਸਿੰਘ ਵਾਲੀ ਜਨਮਸਾਖੀ)", "https://archive.org/download/bhai-mani-singh-vali-janam-sakhi/Bhai%20Mani%20Singh%20vali%20Janam%20Sakhi.pdf", "gyan_ratnavali.pdf"),
            PdfBook("ਗੁਰ ਬਿਲਾਸ ਪਾਤਸ਼ਾਹੀ ੬", "https://archive.org/download/gur-bilas-patshahi-6/gur-bilas-patshahi-6.pdf", "gur_bilas_p6_direct.pdf"),
            PdfBook("ਗੁਰ ਬਿਲਾਸ ਪਾਤਸ਼ਾਹੀ ੧੦ (ਕੋਇਰ ਸਿੰਘ)", "https://archive.org/download/gurbilas-patshahi-10/gurbilas-patshahi-10.pdf", "gur_bilas_p10_koir.pdf"),

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
            PdfBook("ਕਬਿੱਤ ਸਵੱਯੇ ਭਾਈ ਗੁਰਦਾਸ ਜੀ (ਸਟੀਕ)", "https://archive.org/download/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk.pdf", "kabit_sawaiye.pdf"),
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
            PdfBook("ਪੁਰਾਤਨ ਜਨਮ ਸਾਖੀ", "https://archive.org/download/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk/Puratan%20Janam%20Sakhi%20Sri%20Guru%20Nanak%20Dev%20Ji.pdf", "puratan_janamsakhi.pdf"),
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
            PdfBook("ਜੀਵਨ ਸਫਰ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk/Jeevan%20Safar%20-%20Giani%20Sant%20Singh%20Ji%20Maskeen.pdf", "maskeen_jeevan_safar.pdf"),
            PdfBook("ਚੌਥਾ ਪਦ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "http://www.gurmatveechar.com/books/Punjabi_Books/Chautha.Pad.by.Giani.Sant.Singh.Maskeen.(GurmatVeechar.com).pdf", "maskeen_chautha_pad.pdf"),
            PdfBook("ਤੀਜਾ ਨੇਤਰ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-02/maskin-ji-de-lecture-02.pdf", "maskeen_teeja_netar.pdf"),
            PdfBook("ਗੁਰਮਤਿ ਸਿਧਾਂਤ (ਭਾਗ ੧) - ਮਹਾਰਾਜ ਸਾਵਣ ਸਿੰਘ", "https://archive.org/download/gurmat-sidhant-1/gurmat-sidhant-1.pdf", "gurmat_sidhant_1.pdf"),
            PdfBook("ਪ੍ਰਭੂ ਸਿਮਰਨ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/prabhu-simran/prabhu-simran.pdf", "maskeen_simran.pdf"),
            PdfBook("ਖਟ ਦਰਸ਼ਨ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-02/maskin-ji-de-lecture-02.pdf", "maskeen_khat_darshan.pdf"),
            PdfBook("ਬ੍ਰਹਮ ਗਿਆਨ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/BrahmGyanGianiSantSinghMaskeen/Brahm%20Gyan%20-%20Giani%20Sant%20Singh%20Maskeen.pdf", "maskeen_brahm_gian.pdf"),
            PdfBook("ਸੁੱਖ ਦੁੱਖ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-01/maskin-ji-de-lecture-01.pdf", "maskeen_sukh_dukh.pdf"),
            PdfBook("ਜਪੁ ਨੀਸਾਣ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-02/maskin-ji-de-lecture-02.pdf", "maskeen_jap_neesan.pdf"),
            PdfBook("ਪੰਜ ਖੰਡ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk/Jeevan%20Safar%20-%20Giani%20Sant%20Singh%20Ji%20Maskeen.pdf", "maskeen_panj_khand.pdf"),
            PdfBook("ਧਰਮ ਤੇ ਮਨੁੱਖ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-01/maskin-ji-de-lecture-01.pdf", "maskeen_dharam_manukh.pdf"),
            PdfBook("ਪੰਜ ਕਕਾਰਾਂ ਦਾ ਮਹੱਤਵ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/maskin-ji-de-lecture-02/maskin-ji-de-lecture-02.pdf", "maskeen_5k_mahatav.pdf"),
            PdfBook("ਗਿਆਨ ਦਾ ਸਾਗਰ - ਗਿਆਨੀ ਸੰਤ ਸਿੰਘ ਮਸਕੀਨ", "https://archive.org/download/8uTi1sedxgq99QqPm7rkGM4XBdcEimToW9L5DKFffgHk/Jeevan%20Safar%20-%20Giani%20Sant%20Singh%20Ji%20Maskeen.pdf", "maskeen_gian_sagar.pdf")
        )

        val items = mutableListOf<Any>()
        val filteredItems = mutableListOf<Any>()
        items.add("ACTION_IMPORT")
        items.add("ACTION_SEARCH_ONLINE")
        items.addAll(defaultBooks)
        filteredItems.addAll(items)

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

        // Search Bar
        val searchBox = EditText(context).apply {
            hint = "ਪੁਸਤਕ ਲੱਭੋ..."
            setTextColor(getTextColor())
            setHintTextColor(if (isDarkMode) Color.GRAY else Color.LTGRAY)
            typeface = getCustomTypeface()
            textSize = 16f
            setPadding(12.toPx(), 12.toPx(), 12.toPx(), 12.toPx())
            background = GradientDrawable().apply {
                setColor(if (isDarkMode) Color.parseColor("#333333") else Color.parseColor("#F5F5F5"))
                cornerRadius = 8.toPx().toFloat()
                setStroke(1.toPx(), if (isDarkMode) Color.GRAY else Color.LTGRAY)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(16.toPx(), 8.toPx(), 16.toPx(), 8.toPx())
            }
        }
        root.addView(searchBox)

        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            setPadding(16.toPx(), 0, 16.toPx(), 120.toPx())
            clipToPadding = false
        }

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(root).create()
        closeBtn.setOnClickListener { dial.dismiss() }

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            private val TYPE_ACTION = 0
            private val TYPE_HEADER = 1
            private val TYPE_BOOK = 2
            private val TYPE_SEARCH_ONLINE = 3

            override fun getItemViewType(position: Int): Int {
                return when (val item = filteredItems[position]) {
                    "ACTION_IMPORT" -> TYPE_ACTION
                    "ACTION_SEARCH_ONLINE" -> TYPE_SEARCH_ONLINE
                    is String -> TYPE_HEADER
                    else -> TYPE_BOOK
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return when (viewType) {
                    TYPE_ACTION -> {
                        val btn = Button(context).apply {
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8.toPx(), 0, 4.toPx()) }
                            setBackgroundResource(R.drawable.rounded_button_bg)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                            setTextColor(Color.WHITE)
                            setPadding(12.toPx(), 12.toPx(), 12.toPx(), 12.toPx())
                            transformationMethod = null
                            typeface = getCustomTypeface()
                            textSize = 16f
                            text = "ਪੁਸਤਕ ਅਪਲੋਡ ਕਰੋ (Import PDF)"
                        }
                        object : RecyclerView.ViewHolder(btn) {}
                    }
                    TYPE_SEARCH_ONLINE -> {
                        val btn = Button(context).apply {
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.toPx(), 0, 8.toPx()) }
                            setBackgroundResource(R.drawable.rounded_button_bg)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
                            setTextColor(Color.WHITE)
                            setPadding(12.toPx(), 12.toPx(), 12.toPx(), 12.toPx())
                            transformationMethod = null
                            typeface = getCustomTypeface()
                            textSize = 16f
                            text = "ਹੋਰ ਪੁਸਤਕਾਂ ਲੱਭੋ (Search Online)"
                        }
                        object : RecyclerView.ViewHolder(btn) {}
                    }
                    TYPE_HEADER -> {
                        val tv = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12.toPx(), 0, 4.toPx()) }
                            setTextColor(getTextColor())
                            textSize = 18f
                            val tf = getCustomTypeface()
                            typeface = tf
                            setTypeface(tf, Typeface.NORMAL)
                            paint.isFakeBoldText = false
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
                when (val item = filteredItems[position]) {
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
                    "ACTION_SEARCH_ONLINE" -> {
                        holder.itemView.setOnClickListener {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://archive.org/details/discoversikhism?query=punjabi+books"))
                            context.startActivity(intent)
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
            override fun getItemCount() = filteredItems.size
        }
        rv.adapter = adapter

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.lowercase(Locale.ROOT) ?: ""
                filteredItems.clear()
                if (query.isEmpty()) {
                    filteredItems.addAll(items)
                } else {
                    filteredItems.add("ACTION_IMPORT")
                    filteredItems.add("ACTION_SEARCH_ONLINE")
                    items.forEach { item ->
                        val name = when (item) {
                            is PdfBook -> item.name.lowercase(Locale.ROOT)
                            is BookEntity -> item.name.lowercase(Locale.ROOT)
                            else -> ""
                        }
                        if (name.contains(query)) {
                            filteredItems.add(item)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        uiScope.launch {
            AppDatabase.getDatabase(context).bookDao().getAllBooks().collect { customBooks ->
                val newItems = mutableListOf<Any>()
                newItems.add("ACTION_IMPORT")
                newItems.add("ACTION_SEARCH_ONLINE")
                if (customBooks.isNotEmpty()) {
                    newItems.add("ਮੇਰੀਆਂ ਪੁਸਤਕਾਂ (My Books)")
                    newItems.addAll(customBooks)
                    newItems.add("ਇਤਿਹਾਸਕ ਪੁਸਤਕਾਂ (Historical Books)")
                }
                newItems.addAll(defaultBooks)
                items.clear()
                items.addAll(newItems)

                // Refresh filtered items based on search box
                val query = searchBox.text.toString().lowercase(Locale.ROOT)
                filteredItems.clear()
                if (query.isEmpty()) {
                    filteredItems.addAll(items)
                } else {
                    filteredItems.add("ACTION_IMPORT")
                    filteredItems.add("ACTION_SEARCH_ONLINE")
                    items.forEach { item ->
                        val name = when (item) {
                            is PdfBook -> item.name.lowercase(Locale.ROOT)
                            is BookEntity -> item.name.lowercase(Locale.ROOT)
                            else -> ""
                        }
                        if (name.contains(query)) {
                            filteredItems.add(item)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
            }
        }

        root.addView(rv)
        setupDialogWindow(dial, anchorView = anchorView, rootView = root)
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

            val parentRoot = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = createPhotoFrameDrawable(getBgColor())
                setPadding(0, 0, 0, 14.toPx())
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            }
            parentRoot.addView(root)

            ViewCompat.setOnApplyWindowInsetsListener(parentRoot) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, 0, bars.right, bars.bottom)
                insets
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

            val centerTv = TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#FFF3E0"))
                val tf = getCustomTypeface()
                typeface = tf
                setTypeface(tf, Typeface.NORMAL)
                paint.isFakeBoldText = false
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                gravity = Gravity.CENTER
            }
            headerLayout.addView(centerTv)
            root.addView(headerLayout)

            val audioUrl = getAudioUrlForBani(folderName)
            if (audioUrl != null) {
                val audioBarContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val gd = GradientDrawable().apply {
                        setColor(if (isDarkMode) Color.parseColor("#1E272C") else Color.parseColor("#E0F2F1"))
                        setStroke(1.toPx(), if (isDarkMode) Color.parseColor("#37474F") else Color.parseColor("#80CBC4"))
                        cornerRadius = 12.toPx().toFloat()
                    }
                    background = gd
                    setPadding(10.toPx(), 6.toPx(), 10.toPx(), 6.toPx())
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(12.toPx(), 8.toPx(), 12.toPx(), 4.toPx())
                    }
                }

                val horizAudioScroll = HorizontalScrollView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2)
                    isHorizontalScrollBarEnabled = false
                    isFillViewport = true
                }

                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = FrameLayout.LayoutParams(-2, -2)
                }

                val tvAudioStatus = TextView(context).apply {
                    text = "00:00 / 00:00"
                    setTextColor(if (isDarkMode) Color.parseColor("#FFD54F") else Color.parseColor("#00695C"))
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = 8.toPx() }
                }

                var isLoudnessBoostEnabled = nitnemPrefs.getBoolean("audio_boost_enabled", true)
                var audioSpeed = nitnemPrefs.getFloat("audio_playback_speed", 1.0f)
                val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

                fun formatSpeedText(speed: Float): String = "${speed}x"

                val btnPlayAudio = Button(context).apply {
                    text = "▶"
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0288D1"))
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    setAllCaps(false)
                    setPadding(3.toPx(), 0, 3.toPx(), 0)
                    layoutParams = LinearLayout.LayoutParams(-2, 26.toPx()).apply { marginEnd = 2.toPx() }
                }

                val btnBoostAudio = Button(context).apply {
                    text = if (isLoudnessBoostEnabled) "2x" else "1x"
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    backgroundTintList = ColorStateList.valueOf(if (isLoudnessBoostEnabled) Color.parseColor("#2E7D32") else Color.parseColor("#757575"))
                    setTextColor(Color.WHITE)
                    textSize = 9.5f
                    setAllCaps(false)
                    setPadding(3.toPx(), 0, 3.toPx(), 0)
                    layoutParams = LinearLayout.LayoutParams(-2, 26.toPx()).apply { marginEnd = 2.toPx() }
                    setOnClickListener {
                        isLoudnessBoostEnabled = !isLoudnessBoostEnabled
                        nitnemPrefs.edit().putBoolean("audio_boost_enabled", isLoudnessBoostEnabled).apply()
                        text = if (isLoudnessBoostEnabled) "2x" else "1x"
                        backgroundTintList = ColorStateList.valueOf(if (isLoudnessBoostEnabled) Color.parseColor("#2E7D32") else Color.parseColor("#757575"))
                        try {
                            loudnessEnhancer?.setTargetGain(if (isLoudnessBoostEnabled) 1200 else 0)
                            loudnessEnhancer?.enabled = isLoudnessBoostEnabled
                        } catch (e: Exception) {
                            Log.e("NitnemEngine", "Error updating LoudnessEnhancer gain", e)
                        }
                    }
                }

                val btnSpeedAudio = Button(context).apply {
                    text = formatSpeedText(audioSpeed)
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    backgroundTintList = ColorStateList.valueOf(if (audioSpeed > 1.0f) Color.parseColor("#673AB7") else Color.parseColor("#607D8B"))
                    setTextColor(Color.WHITE)
                    textSize = 9.5f
                    setAllCaps(false)
                    setPadding(3.toPx(), 0, 3.toPx(), 0)
                    layoutParams = LinearLayout.LayoutParams(-2, 26.toPx()).apply { marginEnd = 2.toPx() }
                    setOnClickListener {
                        val currentIdx = speeds.indexOf(audioSpeed)
                        val nextIdx = if (currentIdx == -1 || currentIdx >= speeds.size - 1) 0 else currentIdx + 1
                        audioSpeed = speeds[nextIdx]
                        nitnemPrefs.edit().putFloat("audio_playback_speed", audioSpeed).apply()
                        text = formatSpeedText(audioSpeed)
                        backgroundTintList = ColorStateList.valueOf(if (audioSpeed > 1.0f) Color.parseColor("#673AB7") else Color.parseColor("#607D8B"))

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null && isAudioPlaying) {
                            try {
                                val params = mediaPlayer!!.playbackParams
                                params.speed = audioSpeed
                                mediaPlayer!!.playbackParams = params
                            } catch (e: Exception) {
                                Log.e("NitnemEngine", "Error setting audio speed", e)
                            }
                        }
                    }
                }

                val btnStopAudio = Button(context).apply {
                    text = "⏹"
                    setBackgroundResource(R.drawable.rounded_button_bg)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    setAllCaps(false)
                    setPadding(3.toPx(), 0, 3.toPx(), 0)
                    layoutParams = LinearLayout.LayoutParams(-2, 26.toPx()).apply { marginEnd = 2.toPx() }
                }

                topRow.addView(btnPlayAudio)
                topRow.addView(btnBoostAudio)
                topRow.addView(btnSpeedAudio)
                topRow.addView(btnStopAudio)
                topRow.addView(tvAudioStatus)

                horizAudioScroll.addView(topRow)

                val seekBarAudio = SeekBar(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4.toPx() }
                    max = 100
                    progress = 0
                }

                audioBarContainer.addView(horizAudioScroll)
                audioBarContainer.addView(seekBarAudio)
                root.addView(audioBarContainer)

                btnPlayAudio.setOnClickListener {
                    if (isAudioPlaying) {
                        try {
                            mediaPlayer?.pause()
                        } catch (e: Exception) { Log.e("NitnemEngine", "Pause error", e) }
                        isAudioPlaying = false
                        audioProgressJob?.cancel()
                        btnPlayAudio.text = "▶"
                        tvAudioStatus.text = "ਰੋਕੀ ਗਈ"
                    } else {
                        if (mediaPlayer == null) {
                            tvAudioStatus.text = "ਲੋਡ ਹੋ ਰਹੀ ਹੈ..."
                            btnPlayAudio.isEnabled = false
                            uiScope.launch(Dispatchers.IO) {
                                try {
                                    val mp = MediaPlayer().apply {
                                        setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .build()
                                        )
                                        if (hasAssetAudio(folderName)) {
                                            val afd = context.assets.openFd("audio/${folderName}.mp3")
                                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                            afd.close()
                                        } else {
                                            val localFile = getLocalAudioFile(folderName ?: "")
                                            val playUri = if (localFile.exists() && localFile.length() > 0) Uri.fromFile(localFile) else Uri.parse(audioUrl)
                                            setDataSource(context, playUri)
                                        }
                                        setOnPreparedListener { preparedMp ->
                                            uiScope.launch(Dispatchers.Main) {
                                                btnPlayAudio.isEnabled = true
                                                try {
                                                    preparedMp.setVolume(1.0f, 1.0f)
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                                        try {
                                                            loudnessEnhancer?.release()
                                                            val le = android.media.audiofx.LoudnessEnhancer(preparedMp.audioSessionId)
                                                            le.setTargetGain(if (isLoudnessBoostEnabled) 1200 else 0)
                                                            le.enabled = isLoudnessBoostEnabled
                                                            loudnessEnhancer = le
                                                        } catch (e: Exception) {
                                                            Log.e("NitnemEngine", "LoudnessEnhancer error", e)
                                                        }
                                                    }
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                        try {
                                                            val params = preparedMp.playbackParams
                                                            params.speed = audioSpeed
                                                            preparedMp.playbackParams = params
                                                        } catch (e: Exception) {
                                                            Log.e("NitnemEngine", "PlaybackParams speed error", e)
                                                        }
                                                    }
                                                    preparedMp.start()
                                                    isAudioPlaying = true
                                                    btnPlayAudio.text = "⏸"
                                                    seekBarAudio.max = preparedMp.duration
                                                    startAudioProgressTracker(seekBarAudio, tvAudioStatus)
                                                } catch (e: Exception) {
                                                    Log.e("NitnemEngine", "Start error", e)
                                                }
                                            }
                                        }
                                        setOnCompletionListener {
                                            uiScope.launch(Dispatchers.Main) {
                                                isAudioPlaying = false
                                                btnPlayAudio.text = "▶"
                                                seekBarAudio.progress = 0
                                                tvAudioStatus.text = "00:00 / ${formatAudioTime(duration)}"
                                            }
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            uiScope.launch(Dispatchers.Main) {
                                                btnPlayAudio.isEnabled = true
                                                btnPlayAudio.text = "▶"
                                                tvAudioStatus.text = "ਸਮੱਸਿਆ ਆਈ"
                                                Toast.makeText(context, "ਆਵਾਜ਼ ਚਲਾਉਣ 'ਚ ਸਮੱਸਿਆ ਆਈ", Toast.LENGTH_SHORT).show()
                                            }
                                            true
                                        }
                                        prepareAsync()
                                    }
                                    mediaPlayer = mp
                                } catch (e: Exception) {
                                    Log.e("NitnemEngine", "MediaPlayer init error", e)
                                    uiScope.launch(Dispatchers.Main) {
                                        btnPlayAudio.isEnabled = true
                                        tvAudioStatus.text = "ਲੋਡ ਫੇਲ"
                                    }
                                }
                            }
                        } else {
                            try {
                                mediaPlayer?.start()
                                isAudioPlaying = true
                                btnPlayAudio.text = "⏸"
                                mediaPlayer?.let { startAudioProgressTracker(seekBarAudio, tvAudioStatus) }
                            } catch (e: Exception) {
                                Log.e("NitnemEngine", "Resume error", e)
                            }
                        }
                    }
                }

                btnStopAudio.setOnClickListener {
                    stopMusic()
                    btnPlayAudio.text = "▶"
                    btnPlayAudio.isEnabled = true
                    seekBarAudio.progress = 0
                    tvAudioStatus.text = "00:00 / 00:00"
                }

                seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser && mediaPlayer != null) {
                            try {
                                mediaPlayer?.seekTo(progress)
                            } catch (e: Exception) { Log.e("NitnemEngine", "Seek error", e) }
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            val scroll = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply {
                    setMargins(12.toPx(), 0, 12.toPx(), 0)
                }
                setPadding(0, 12.toPx(), 0, 12.toPx())
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
                setPadding(10.toPx(), 20.toPx(), 10.toPx(), 80.toPx())
                textSize = currentTextSize
                setTextColor(getGurbaniNormalColor())
                setLineSpacing(0f, 1.0f)
                gravity = Gravity.CENTER_HORIZONTAL
                val tf = getCustomTypeface()
                typeface = tf
                setTypeface(tf, Typeface.NORMAL)
                paint.isFakeBoldText = false
                setElegantTextHeight(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
                }
                isFocusable = false
                isFocusableInTouchMode = false
            }; scroll.addView(tv)
            root.addView(scroll)

            val horizScroll = HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(12.toPx(), 0, 12.toPx(), 12.toPx())
                }
                setBackgroundColor(getStripColor())
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                post { fullScroll(View.FOCUS_RIGHT) }
            }
            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setPadding(8.toPx(), 4.toPx(), 8.toPx(), 0)
            }

            var showArthGlobal = nitnemPrefs.getBoolean("show_arth_global", true)
            var showEnglishGlobal = nitnemPrefs.getBoolean("show_english_global", false)
            var updateContentFunc: (() -> Unit)? = null

            val btnLarivaar = Button(context).apply {
                text = "ਲੜੀਵਾਰ"
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(if (isLarivaarGlobal) Color.parseColor("#4CAF50") else Color.parseColor("#EF6C00"))
                setTextColor(Color.WHITE)
                setAllCaps(false)
                textSize = 9.5f
                setPadding(4.toPx(), 1.toPx(), 4.toPx(), 1.toPx())
                layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() }
            }

            val btnArth = Button(context).apply {
                text = if (showArthGlobal) "ਅਰਥ On" else "ਅਰਥ Off"
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(if (showArthGlobal) Color.parseColor("#E65100") else Color.parseColor("#757575"))
                setTextColor(Color.WHITE)
                setAllCaps(false)
                textSize = 9.5f
                setPadding(4.toPx(), 1.toPx(), 4.toPx(), 1.toPx())
                layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() }
                setOnClickListener {
                    showArthGlobal = !showArthGlobal
                    nitnemPrefs.edit().putBoolean("show_arth_global", showArthGlobal).apply()
                    text = if (showArthGlobal) "ਅਰਥ On" else "ਅਰਥ Off"
                    backgroundTintList = ColorStateList.valueOf(if (showArthGlobal) Color.parseColor("#E65100") else Color.parseColor("#757575"))
                    updateContentFunc?.invoke()
                }
            }

            val btnEnglish = Button(context).apply {
                text = if (showEnglishGlobal) "ENG On" else "ENG Off"
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(if (showEnglishGlobal) Color.parseColor("#1565C0") else Color.parseColor("#757575"))
                setTextColor(Color.WHITE)
                setAllCaps(false)
                textSize = 9.5f
                setPadding(4.toPx(), 1.toPx(), 4.toPx(), 1.toPx())
                layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() }
                setOnClickListener {
                    showEnglishGlobal = !showEnglishGlobal
                    nitnemPrefs.edit().putBoolean("show_english_global", showEnglishGlobal).apply()
                    text = if (showEnglishGlobal) "ENG On" else "ENG Off"
                    backgroundTintList = ColorStateList.valueOf(if (showEnglishGlobal) Color.parseColor("#1565C0") else Color.parseColor("#757575"))
                    updateContentFunc?.invoke()
                }
            }

            val nextInfo = if (folderName != null) getNextBani(folderName) else null
            val prevInfo = if (folderName != null) getPrevBani(folderName) else null

            val btnPrev = if (prevShabadId != null || prevInfo != null) Button(context).apply {
                text = if (prevShabadId != null) "⇜ ਸ਼ਬਦ" else prevInfo?.first
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF6C00"))
                setTextColor(Color.WHITE)
                setAllCaps(false)
                textSize = 9.5f
                setPadding(4.toPx(), 1.toPx(), 4.toPx(), 1.toPx())
                layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() }
            } else null

            val btnNext = if (nextShabadId != null || nextInfo != null) Button(context).apply {
                text = if (nextShabadId != null) "ਸ਼ਬਦ ⇝" else nextInfo?.first
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF6C00"))
                setTextColor(Color.WHITE)
                setAllCaps(false)
                textSize = 9.5f
                setPadding(4.toPx(), 1.toPx(), 4.toPx(), 1.toPx())
                layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() }
            } else null

            val speedContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val gd = GradientDrawable().apply {
                    setColor(Color.parseColor("#424242"))
                    cornerRadius = 14.toPx().toFloat()
                }
                background = gd
                setPadding(4.toPx(), 0, 4.toPx(), 0)
                layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() }
            }

            val btnSpeedDown = TextView(context).apply {
                text = "—"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                isClickable = true
                isFocusable = true
                setPadding(6.toPx(), 0, 6.toPx(), 0)
            }

            val speedIndicator = TextView(context).apply {
                text = "$savedSpeedLevel"
                setTextColor(Color.YELLOW)
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(4.toPx(), 0, 4.toPx(), 0)
            }

            val btnSpeedUp = TextView(context).apply {
                text = "+"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                isClickable = true
                isFocusable = true
                setPadding(6.toPx(), 0, 6.toPx(), 0)
            }

            speedContainer.addView(btnSpeedDown)
            speedContainer.addView(speedIndicator)
            speedContainer.addView(btnSpeedUp)

            val btnTop = Button(context).apply { text = "TOP"; setBackgroundResource(R.drawable.rounded_button_bg); backgroundTintList = ColorStateList.valueOf(Color.DKGRAY); setTextColor(Color.WHITE); textSize = 9.5f; setPadding(4.toPx(), 1.toPx(), 4.toPx(), 1.toPx()); layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() } }
            val btnPage = Button(context).apply { text = "PAGE"; setBackgroundResource(R.drawable.rounded_button_bg); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#673AB7")); setTextColor(Color.WHITE); textSize = 9.5f; setPadding(4.toPx(), 1.toPx(), 4.toPx(), 1.toPx()); layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() } }
            val btnTheme = Button(context).apply { text = if (isDarkMode) "LIGHT" else "DARK"; setBackgroundResource(R.drawable.rounded_button_bg); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#607D8B")); setTextColor(Color.WHITE); textSize = 9.5f; setPadding(4.toPx(), 1.toPx(), 4.toPx(), 1.toPx()); layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()).apply { marginEnd = 4.toPx() } }
            val btnClose = Button(context).apply { text = "✕"; setBackgroundResource(R.drawable.rounded_button_bg); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3E2723")); setTextColor(Color.WHITE); setAllCaps(false); textSize = 11f; setPadding(6.toPx(), 1.toPx(), 6.toPx(), 1.toPx()); layoutParams = LinearLayout.LayoutParams(-2, 28.toPx()) }

            fun updateContent() {
                updateContentFunc = { updateContent() }
                japjiScrollJob?.cancel()
                japjiScrollJob = uiScope.launch {
                    val lines = if (preFetchedLines != null) {
                        preFetchedLines
                    } else if (folderName != null) {
                        val rawParagraphs = withContext(Dispatchers.IO) { GurbaniSearchHelper.getGurbaniLines(context, folderName) }
                        if (rawParagraphs.isEmpty()) listOf("ਪਾਠ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ") else {
                            val sentences = GurbaniSearchHelper.splitIntoSentences(rawParagraphs)
                            sentences.map { line ->
                                val localArth = GurbaniMeaningHelper.getLineMeanings(context, line)
                                if (localArth.isNotEmpty()) line + localArth else line
                            }
                        }
                    } else if (singleLine != null) {
                        val localArth = GurbaniMeaningHelper.getLineMeanings(context, singleLine)
                        listOf(if (localArth.isNotEmpty()) singleLine + localArth else singleLine)
                    } else listOf("No Content")

                    val (spannable, pageOffsets) = GurbaniSearchHelper.getGurbaniSpannable(lines, isLarivaarGlobal, folderName ?: "", showArth = showArthGlobal, showEnglish = showEnglishGlobal, dynamicColor = dynamicColor, highlightColor = highlightColor, customTypeface = getCustomTypeface(), pageNumberColor = getPageNumberColor())
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

            btnTheme.setOnClickListener {
                isDarkMode = !isDarkMode
                root.background = createPhotoFrameDrawable(getBgColor())
                root.setPadding(0, 0, 0, 14.toPx())

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
                tv.setTextColor(getGurbaniNormalColor())
                dynamicColor.color = getGurbaniNormalColor()
                highlightColor.color = getGurbaniHighlightColor()
                btnTheme.text = if (isDarkMode) "LIGHT" else "DARK"

                btnPrev?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF6C00"))
                btnNext?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF6C00"))
                btnLarivaar.backgroundTintList = ColorStateList.valueOf(if (isLarivaarGlobal) Color.parseColor("#4CAF50") else Color.parseColor("#EF6C00"))
                btnArth.backgroundTintList = ColorStateList.valueOf(if (showArthGlobal) Color.parseColor("#E65100") else Color.parseColor("#757575"))
                btnEnglish.backgroundTintList = ColorStateList.valueOf(if (showEnglishGlobal) Color.parseColor("#1565C0") else Color.parseColor("#757575"))
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
                        isAutoScrolling = true
                    } else {
                        horizScroll.visibility = View.VISIBLE
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

            val webUrl = getNitnemWebUrl(folderName)
            val btnWebPage = if (webUrl != null) Button(context).apply {
                text = "🌐 nitnemsahib.com"
                setBackgroundResource(R.drawable.rounded_button_bg)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
                setTextColor(Color.WHITE)
                textSize = 10f
                setAllCaps(false)
                layoutParams = LinearLayout.LayoutParams(-2, 36.toPx()).apply { marginEnd = 8.toPx() }
                setOnClickListener {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                    } catch (e: Exception) {
                        Toast.makeText(context, "ਵੈੱਬਸਾਈਟ ਖੋਲ੍ਹਣ 'ਚ ਅਸਮਰੱਥ", Toast.LENGTH_SHORT).show()
                    }
                }
            } else null

            controls.addView(btnLarivaar)
            controls.addView(btnArth)
            controls.addView(btnEnglish)
            if (btnPrev != null) controls.addView(btnPrev)
            if (btnNext != null) controls.addView(btnNext)
            if (btnWebPage != null) controls.addView(btnWebPage)
            controls.addView(btnPage)
            controls.addView(btnTheme)
            controls.addView(speedContainer)
            controls.addView(btnTop)
            controls.addView(btnClose)

            horizScroll.addView(controls)
            root.addView(horizScroll)

            val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(parentRoot).create()
            btnClose.setOnClickListener { dial.dismiss() }
            setupDialogWindow(dial, anchorView = anchorView, rootView = parentRoot)
            dial.show()
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
        val parentRoot = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(0, 0, 0, 14.toPx())
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        parentRoot.addView(root)

        ViewCompat.setOnApplyWindowInsetsListener(parentRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
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
                    val tf = getCustomTypeface()
                    typeface = tf
                    setTypeface(tf, Typeface.NORMAL)
                    paint.isFakeBoldText = false
                    textSize = 14f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                return object : RecyclerView.ViewHolder(btn) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = resultsList[position]
                (holder.itemView as Button).apply {
                    text = item.text
                    val tf = getCustomTypeface()
                    typeface = tf
                    setTypeface(tf, Typeface.NORMAL)
                    paint.isFakeBoldText = false
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

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(parentRoot).create()
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

        setupDialogWindow(dial, anchorView = anchorView, rootView = parentRoot)
        dial.show()
    }

    private fun showGurbaniLessonsDialog(anchorView: View?) {
        val parentRoot = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(0, 0, 0, 14.toPx())
        }
        parentRoot.addView(root)

        ViewCompat.setOnApplyWindowInsetsListener(parentRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
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
            text = "ਜੀਵਨ ਸਿੱਖਿਆ (Gurbani Lessons)"
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

        data class Lesson(val verse: String, val meaning: String, val lesson: String, val source: String)
        val lessonsList = try {
            val json = context.assets.open("daily_educational_verses.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                Lesson(obj.getString("verse"), obj.getString("meaning"), obj.getString("lesson"), obj.getString("source"))
            }
        } catch (e: Exception) { emptyList<Lesson>() }

        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            setPadding(16.toPx(), 12.toPx(), 16.toPx(), 100.toPx())
            clipToPadding = false
        }

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8.toPx(), 0, 8.toPx()) }
                    val gd = GradientDrawable().apply {
                        setColor(if (isDarkMode) Color.parseColor("#1A237E") else Color.parseColor("#FFF9C4"))
                        cornerRadius = 12.toPx().toFloat()
                        setStroke(2.toPx(), getFrameColor())
                    }
                    background = gd
                    setPadding(16.toPx(), 16.toPx(), 16.toPx(), 16.toPx())
                }
                return object : RecyclerView.ViewHolder(card) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = lessonsList[position]
                (holder.itemView as LinearLayout).apply {
                    removeAllViews()
                    addView(TextView(context).apply {
                        text = item.verse; textSize = 18f; setTextColor(if (isDarkMode) Color.YELLOW else Color.parseColor("#B71C1C"))
                        typeface = getCustomTypeface(); gravity = Gravity.CENTER
                    })
                    addView(TextView(context).apply {
                        text = "ਅਰਥ: ${item.meaning}"; textSize = 14f; setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
                        typeface = getCustomTypeface(); setPadding(0, 8.toPx(), 0, 0)
                    })
                    addView(TextView(context).apply {
                        text = "ਸਿੱਖਿਆ: ${item.lesson}"; textSize = 15f; setTextColor(if (isDarkMode) Color.GREEN else Color.parseColor("#1B5E20"))
                        typeface = getCustomTypeface(); setPadding(0, 4.toPx(), 0, 0); setTypeface(null, Typeface.BOLD)
                    })
                    addView(TextView(context).apply {
                        text = item.source; textSize = 12f; setTextColor(Color.GRAY)
                        typeface = getCustomTypeface(); gravity = Gravity.END; setPadding(0, 4.toPx(), 0, 0)
                    })
                }
            }
            override fun getItemCount() = lessonsList.size
        }
        root.addView(rv)

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(parentRoot).create()
        closeBtn.setOnClickListener { dial.dismiss() }
        setupDialogWindow(dial, anchorView = anchorView, rootView = parentRoot)
        dial.show()
    }

    private fun showBookSummariesDialog(anchorView: View?) {
        val parentRoot = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPhotoFrameDrawable(getBgColor())
            setPadding(0, 0, 0, 14.toPx())
        }
        parentRoot.addView(root)

        ViewCompat.setOnApplyWindowInsetsListener(parentRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
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
            text = "ਪੁਸਤਕ ਸੰਖੇਪ (Book Summaries)"
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

        data class BookSummary(
            val title: String,
            val author: String,
            val category: String,
            val introduction: String,
            val coreIdea: String,
            val keyPoints: List<String>,
            val lessons: String,
            val fullText: String
        )

        val bookList = try {
            val json = context.assets.open("book_summaries.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                val pointsArr = obj.optJSONArray("key_points")
                val pointsList = mutableListOf<String>()
                if (pointsArr != null) {
                    for (j in 0 until pointsArr.length()) {
                        pointsList.add(pointsArr.getString(j))
                    }
                }
                BookSummary(
                    obj.optString("title", ""),
                    obj.optString("author", ""),
                    obj.optString("category", ""),
                    obj.optString("introduction", ""),
                    obj.optString("core_idea", ""),
                    pointsList,
                    obj.optString("lessons", ""),
                    obj.optString("full_text", "")
                )
            }
        } catch (e: Exception) { emptyList<BookSummary>() }

        val expandedSet = mutableSetOf<Int>()

        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            setPadding(16.toPx(), 12.toPx(), 16.toPx(), 100.toPx())
            clipToPadding = false
        }

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8.toPx(), 0, 8.toPx()) }
                    val gd = GradientDrawable().apply {
                        setColor(if (isDarkMode) Color.parseColor("#1A237E") else Color.parseColor("#FFF9C4"))
                        cornerRadius = 12.toPx().toFloat()
                        setStroke(2.toPx(), getFrameColor())
                    }
                    background = gd
                    setPadding(16.toPx(), 16.toPx(), 16.toPx(), 16.toPx())
                }
                return object : RecyclerView.ViewHolder(card) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = bookList[position]
                val isExpanded = expandedSet.contains(position)
                (holder.itemView as LinearLayout).apply {
                    removeAllViews()

                    // Title
                    addView(TextView(context).apply {
                        text = item.title
                        textSize = 19f
                        setTextColor(if (isDarkMode) Color.YELLOW else Color.parseColor("#B71C1C"))
                        typeface = getCustomTypeface()
                        gravity = Gravity.CENTER
                        setTypeface(null, Typeface.BOLD)
                    })

                    // Author & Category
                    if (item.author.isNotEmpty() || item.category.isNotEmpty()) {
                        addView(TextView(context).apply {
                            text = "${item.author}${if (item.category.isNotEmpty()) " • " + item.category else ""}"
                            textSize = 13f
                            setTextColor(if (isDarkMode) Color.LTGRAY else Color.parseColor("#424242"))
                            typeface = getCustomTypeface()
                            gravity = Gravity.CENTER
                            setPadding(0, 4.toPx(), 0, 8.toPx())
                        })
                    }

                    // Introduction
                    if (item.introduction.isNotEmpty()) {
                        addView(TextView(context).apply {
                            text = "ਭੂਮਿਕਾ / ਮੂਲ ਵਿਚਾਰ:\n${item.introduction}"
                            textSize = 14f
                            setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
                            typeface = getCustomTypeface()
                            setPadding(0, 4.toPx(), 0, 8.toPx())
                        })
                    }

                    // Key Points
                    if (item.keyPoints.isNotEmpty()) {
                        addView(TextView(context).apply {
                            text = "ਮੁੱਖ ਨੁਕਤੇ:\n" + item.keyPoints.joinToString("\n") { "• $it" }
                            textSize = 14f
                            setTextColor(if (isDarkMode) Color.parseColor("#80D8FF") else Color.parseColor("#0D47A1"))
                            typeface = getCustomTypeface()
                            setPadding(0, 4.toPx(), 0, 8.toPx())
                        })
                    }

                    // Lessons / Message
                    if (item.lessons.isNotEmpty()) {
                        addView(TextView(context).apply {
                            text = "ਮੁੱਖ ਸੰਦੇਸ਼:\n${item.lessons}"
                            textSize = 15f
                            setTextColor(if (isDarkMode) Color.GREEN else Color.parseColor("#1B5E20"))
                            typeface = getCustomTypeface()
                            setPadding(0, 4.toPx(), 0, 8.toPx())
                            setTypeface(null, Typeface.BOLD)
                        })
                    }

                    // Full text view if expanded
                    if (isExpanded && item.fullText.isNotEmpty()) {
                        addView(TextView(context).apply {
                            text = "\n📋 ਪੂਰਾ ਵੇਰਵਾ / ਪਲਾਨ:\n\n${item.fullText}"
                            textSize = 14f
                            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#212121"))
                            typeface = getCustomTypeface()
                            setPadding(0, 8.toPx(), 0, 8.toPx())
                        })
                    }

                    // Expand / Collapse button
                    if (item.fullText.isNotEmpty()) {
                        addView(Button(context).apply {
                            text = if (isExpanded) "ਘੱਟ ਵੇਖੋ (Collapse)" else "📖 ਪੂਰਾ ਵੇਰਵਾ / 30 ਦਿਨਾਂ ਪਲਾਨ ਵੇਖੋ"
                            textSize = 13f
                            setTextColor(Color.WHITE)
                            setBackgroundResource(R.drawable.rounded_button_bg)
                            setOnClickListener {
                                if (isExpanded) expandedSet.remove(position) else expandedSet.add(position)
                                rv.adapter?.notifyItemChanged(position)
                            }
                        })
                    }
                }
            }

            override fun getItemCount() = bookList.size
        }
        root.addView(rv)

        val dial = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).setView(parentRoot).create()
        closeBtn.setOnClickListener { dial.dismiss() }
        setupDialogWindow(dial, anchorView = anchorView, rootView = parentRoot)
        dial.show()
    }

    private fun Int.toPx(): Int = (this * context.resources.displayMetrics.density).toInt()
}
