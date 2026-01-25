package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.emoji2.text.EmojiCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.iqbal.CandidateView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.*

@Suppress("deprecation")
class MyKeyboardIME : InputMethodService(), SharedPreferences.OnSharedPreferenceChangeListener, VoiceRecognitionResultListener, TextToSpeech.OnInitListener {

    var kv: MyKeyboardView? = null
    var lastAlphabeticKeyboard: KeyboardType = KeyboardType.GURMUKHI
    var currentPanel: Int = ImeConstants.PANEL_KEYBOARD
    private var isGurbaniPlayerPlaying = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var keyboardInputContainer: FrameLayout? = null
    private var nanakshahiCalendarPanel: NanakshahiCalendarPanel? = null
    private var nanakshahiCalendarPanelContainer: View? = null
    private var translationPanelContainer: View? = null
    private var emojiPanelContainer: View? = null
    private var clipboardPanelLayout: View? = null
    private var voiceInputPopupContainer: View? = null
    private var expandedTopRowContainer: View? = null
    private var suggestionPanelRoot: View? = null
    private var candidateView: CandidateView? = null
    private var mainKeyboardLayout: View? = null
    private var fixedTopRowButtons: LinearLayout? = null
    
    internal lateinit var voiceInputManager: VoiceInputManager
    internal var translationInput: EditText? = null

    internal lateinit var sharedPreferences: SharedPreferences
    internal lateinit var keyboardManager: KeyboardManager
    internal lateinit var gurmukhiInputHandler: GurmukhiInputHandler
    internal lateinit var keyboardActionListener: MyKeyboardActionListener
    internal lateinit var translationManager: TranslationManager
    private lateinit var databaseHelper: DatabaseHelper
    private var predictionEngine: PredictionEngine? = null
    private var tts: TextToSpeech? = null
    private var clipboardAdapter: ClipboardAdapter? = null

    private var speechRate: Float = 1.0f

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        keyboardManager = KeyboardManager(applicationContext, sharedPreferences)
        databaseHelper = DatabaseHelper(this)

        // Initialize Transliteration Engine with Gurbani Dictionary
        TransliterationHelper.init(this)

        serviceScope.launch {
            withContext(Dispatchers.IO) {
                DictionaryHelper.convertParagraphToDictionary(applicationContext)
                predictionEngine = PredictionEngine(applicationContext)
            }
        }

        gurmukhiInputHandler = GurmukhiInputHandler { word, _ -> updateSuggestions(word) }
        voiceInputManager = VoiceInputManager(this, this)
        translationManager = TranslationManager(this)
        tts = TextToSpeech(this, this)
        EmojiCompat.init(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        voiceInputManager.stopVoiceRecognition()
        translationManager.close()
        tts?.stop(); tts?.shutdown()
        serviceScope.cancel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && currentPanel != ImeConstants.PANEL_KEYBOARD) {
            switchPanel(ImeConstants.PANEL_KEYBOARD); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputView(): View {
        val themeValue = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light")
        val themeResId = keyboardManager.getThemeResIdForValue(themeValue)
        val themedContext: Context = ContextThemeWrapper(this, themeResId)

        val oneHandedMode = sharedPreferences.getString("pref_one_handed_mode", "off")
        val totalWidth = resources.displayMetrics.widthPixels
        val kbWidth = if (oneHandedMode != "off" && oneHandedMode != "split") (totalWidth * 0.85).toInt() else totalWidth
        keyboardManager.loadAllKeyboards(themedContext, kbWidth)

        val root = LayoutInflater.from(themedContext).inflate(R.layout.input_view, null) as FrameLayout
        keyboardInputContainer = root
        mainKeyboardLayout = root.findViewById(R.id.mainKeyboardLayout)
        nanakshahiCalendarPanelContainer = root.findViewById(R.id.nanakshahi_calendar_panel_container)
        translationPanelContainer = root.findViewById(R.id.translation_panel_container)
        emojiPanelContainer = root.findViewById(R.id.emoji_panel_container)
        clipboardPanelLayout = root.findViewById(R.id.clipboard_panel_layout)
        voiceInputPopupContainer = root.findViewById(R.id.voice_input_popup_container)
        expandedTopRowContainer = root.findViewById(R.id.expanded_top_row_container)
        fixedTopRowButtons = root.findViewById(R.id.fixed_top_row_buttons)
        suggestionPanelRoot = root.findViewById(R.id.suggestion_panel_root)
        candidateView = suggestionPanelRoot?.findViewById(R.id.candidate_view)
        kv = root.findViewById(R.id.keyboardView)

        kv?.keyboard = keyboardManager.getCurrentKeyboard()
        updateKeyboardSettings()

        if (themeValue == "custom") {
            val file = File(filesDir, "custom_keyboard_bg.jpg")
            if (file.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) root.background = BitmapDrawable(resources, bitmap)
                    else root.setBackgroundColor(Color.LTGRAY)
                } catch (e: Exception) { root.setBackgroundColor(Color.LTGRAY) }
            } else root.setBackgroundColor(Color.LTGRAY)
        } else {
            val tv = TypedValue()
            if (themedContext.theme.resolveAttribute(R.attr.keyboardViewBackground, tv, true)) root.setBackgroundColor(tv.data)
            else root.setBackgroundColor(Color.WHITE)
        }

        candidateView?.setOnSuggestionClickListener { handleSuggestionClick(it) }
        candidateView?.setOnSuggestionLongClickListener { suggestion ->
            serviceScope.launch(Dispatchers.IO) {
                DictionaryHelper.deleteWordFromDictionary(applicationContext, suggestion)
                databaseHelper.deleteWord(suggestion)
                predictionEngine = PredictionEngine(applicationContext)
                withContext(Dispatchers.Main) { updateSuggestions("") }
            }
        }

        keyboardActionListener = MyKeyboardActionListener(this)
        kv?.setOnKeyboardActionListener(keyboardActionListener)

        customizeTopRowButtons(themedContext)
        nanakshahiCalendarPanel = NanakshahiCalendarPanel(themedContext) { switchPanel(ImeConstants.PANEL_KEYBOARD) }
        (nanakshahiCalendarPanelContainer as? FrameLayout)?.addView(nanakshahiCalendarPanel?.view)

        setupTranslationPanel(themedContext); setupEmojiPanel(themedContext); setupClipboardPanel(themedContext); setupExpandedTopRow(themedContext)

        root.findViewById<ImageButton>(R.id.btn_move_to_left).setOnClickListener { sharedPreferences.edit().putString("pref_one_handed_mode", "left").apply() }
        root.findViewById<ImageButton>(R.id.btn_move_to_right).setOnClickListener { sharedPreferences.edit().putString("pref_one_handed_mode", "right").apply() }
        root.findViewById<ImageButton>(R.id.btn_expand_left).setOnClickListener { sharedPreferences.edit().putString("pref_one_handed_mode", "off").apply() }
        root.findViewById<ImageButton>(R.id.btn_expand_right).setOnClickListener { sharedPreferences.edit().putString("pref_one_handed_mode", "off").apply() }

        applyOneHandedMode()
        switchPanel(ImeConstants.PANEL_KEYBOARD, forceRedraw = true)

        return root
    }

    internal fun launchSettings() {
        val intent = Intent(this, KeyboardSettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun launchAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun handleSuggestionClick(suggestion: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit(); ic.finishComposingText()
        val typedWord = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString()
        if (typedWord.isNotEmpty()) ic.deleteSurroundingText(typedWord.length, 0)
        if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.reset() else keyboardActionListener.englishWordBuffer.setLength(0)
        ic.commitText("$suggestion ", 1); ic.endBatchEdit(); updateSuggestions("")
        if (suggestion.isNotEmpty()) serviceScope.launch(Dispatchers.IO) { databaseHelper.addWord(suggestion.trim()) }
    }

    internal fun updateSuggestions(word: String) = (predictionEngine?.getSuggestions(word)?.toMutableList() ?: mutableListOf()).let { sugs -> (if (word.isEmpty()) currentInputConnection?.getTextBeforeCursor(50, 0)?.toString()?.trim()?.split(" ")?.lastOrNull()?.let { PunjabiNextWordLM.predict(it).takeIf { p -> p.isNotEmpty() }?.let { p -> (p + sugs).distinct().toMutableList() } } ?: sugs else sugs).let { final -> candidateView?.post { candidateView?.setSuggestions(final) } } }

    internal fun learnWord(word: String) {
        if (word.length >= 2) {
            serviceScope.launch(Dispatchers.IO) {
                DictionaryHelper.addWordToDictionary(applicationContext, word)
                databaseHelper.addWord(word)
                predictionEngine = PredictionEngine(applicationContext)
            }
        }
    }

    private fun setupEmojiPanel(context: Context) {
        val emojiRecyclerView = emojiPanelContainer?.findViewById<RecyclerView>(R.id.emoji_recycler_view)
        val emojiTabLayout = emojiPanelContainer?.findViewById<TabLayout>(R.id.emoji_category_tabs)
        val allEmojisWithHeaders = EmojiHelper.getAllEmojisWithHeaders(context)
        val emojiAdapter = EmojiAdapter(allEmojisWithHeaders, { onEmojiClicked(it) }, { it is String && EmojiData.categories.containsKey(it) })
        val layoutManager = GridLayoutManager(context, 8)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int): Int = if (emojiAdapter.isHeader(position)) 8 else 1 }
        emojiRecyclerView?.layoutManager = layoutManager; emojiRecyclerView?.adapter = emojiAdapter
        val categories = EmojiHelper.getEmojiCategories()
        for (category in categories) emojiTabLayout?.addTab(emojiTabLayout.newTab().setText(category))
        emojiTabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { val pos = EmojiHelper.getPositionForCategory(context, tab?.text.toString()); (emojiRecyclerView?.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(pos, 0) }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        emojiRecyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() { override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { super.onScrolled(recyclerView, dx, dy); val pos = (recyclerView.layoutManager as GridLayoutManager).findFirstVisibleItemPosition(); val category = EmojiHelper.getCategoryForPosition(context, pos); val tabIndex = categories.indexOf(category); if (tabIndex != -1 && emojiTabLayout?.selectedTabPosition != tabIndex) emojiTabLayout?.getTabAt(tabIndex)?.select() } })
        emojiPanelContainer?.findViewById<ImageButton>(R.id.emoji_panel_return_to_gurmukhi)?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }
        emojiPanelContainer?.findViewById<ImageButton>(R.id.emoji_panel_backspace)?.setOnClickListener { keyboardActionListener.onKey(Keyboard.KEYCODE_DELETE, null) }
    }

    private fun onEmojiClicked(emoji: String) { currentInputConnection?.commitText(emoji, 1); RecentEmojiManager.addEmoji(this, emoji) }

    private fun setupTranslationPanel(context: Context) {
        translationInput = translationPanelContainer?.findViewById<EditText>(R.id.translation_input_text)
        val translationOutput = translationPanelContainer?.findViewById<TextView>(R.id.translation_output_text)
        val sourceLanguageSpinner = translationPanelContainer?.findViewById<Spinner>(R.id.source_language_spinner)
        val targetLanguageSpinner = translationPanelContainer?.findViewById<Spinner>(R.id.target_language_spinner)
        val swapLanguagesButton = translationPanelContainer?.findViewById<ImageButton>(R.id.swap_languages_button)
        val speakTranslationButton = translationPanelContainer?.findViewById<ImageButton>(R.id.speak_translation_button)
        val voiceTranslateButton = translationPanelContainer?.findViewById<ImageButton>(R.id.voice_translate_button)
        val closeButton = translationPanelContainer?.findViewById<ImageButton>(R.id.close_translation_panel_button)
        
        val speedSeekBar = translationPanelContainer?.findViewById<SeekBar>(R.id.voice_speed_seekbar)

        val languageNames = translationManager.availableLanguages.map { it.name }
        val adapter = ArrayAdapter(context, R.layout.spinner_item, languageNames); adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        sourceLanguageSpinner?.adapter = adapter; targetLanguageSpinner?.adapter = adapter
        
        val onLanguageSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (parent?.id) { 
                    R.id.source_language_spinner -> translationManager.sourceLanguage = translationManager.availableLanguages[position]
                    R.id.target_language_spinner -> translationManager.targetLanguage = translationManager.availableLanguages[position] 
                }
                val inputText = translationInput?.text?.toString(); if (!inputText.isNullOrEmpty()) translationManager.translate(inputText, translationOutput)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        sourceLanguageSpinner?.onItemSelectedListener = onLanguageSelectedListener; targetLanguageSpinner?.onItemSelectedListener = onLanguageSelectedListener
        
        swapLanguagesButton?.setOnClickListener { translationManager.swapLanguages(); updateSpinnerSelections() }
        speedSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { speechRate = (progress / 100.0f).coerceIn(0.5f, 2.0f) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        speakTranslationButton?.setOnClickListener { 
            val textToSpeak = translationOutput?.text?.toString()
            if (!textToSpeak.isNullOrEmpty() && textToSpeak != "Translating...") speak(textToSpeak, translationManager.targetLanguage.speechCode)
            else Toast.makeText(this, "ਪਹਿਲਾਂ ਟੈਕਸਟ ਲਿਖੋ", Toast.LENGTH_SHORT).show()
        }
        
        voiceTranslateButton?.setOnClickListener { voiceInputManager.startVoiceRecognition(translationManager.sourceLanguage.speechCode) }
        closeButton?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }
        translationInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { translationManager.translate(s.toString(), translationOutput) }
        })
        updateSpinnerSelections()
    }

    private fun updateSpinnerSelections() {
        val srcS = translationPanelContainer?.findViewById<Spinner>(R.id.source_language_spinner)
        val tgtS = translationPanelContainer?.findViewById<Spinner>(R.id.target_language_spinner)
        val srcP = translationManager.availableLanguages.indexOf(translationManager.sourceLanguage)
        val tgtP = translationManager.availableLanguages.indexOf(translationManager.targetLanguage)
        if (srcP != -1) srcS?.setSelection(srcP)
        if (tgtP != -1) tgtS?.setSelection(tgtP)
    }

    private fun setupClipboardPanel(context: Context) {
        val recyclerView = clipboardPanelLayout?.findViewById<RecyclerView>(R.id.clipboard_recycler_view)
        val searchEditText = clipboardPanelLayout?.findViewById<EditText>(R.id.clipboard_search_edit_text)
        val backButton = clipboardPanelLayout?.findViewById<ImageButton>(R.id.back_to_keyboard_button)
        val backspaceButton = clipboardPanelLayout?.findViewById<ImageButton>(R.id.clipboard_backspace_button)

        recyclerView?.layoutManager = LinearLayoutManager(context)
        clipboardAdapter = ClipboardAdapter(emptyList(), { text ->
            currentInputConnection?.commitText(text, 1)
        }, { _ -> }, { id ->
            serviceScope.launch {
                databaseHelper.deleteClipboardItems(listOf(id))
                refreshClipboardHistory(searchEditText?.text?.toString())
            }
        }, { id, pinned ->
            serviceScope.launch {
                databaseHelper.updateClipboardPinned(id, pinned)
                refreshClipboardHistory(searchEditText?.text?.toString())
            }
        })
        recyclerView?.adapter = clipboardAdapter

        searchEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshClipboardHistory(s.toString()) }
        })

        backButton?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }
        backspaceButton?.setOnClickListener { keyboardActionListener.onKey(Keyboard.KEYCODE_DELETE, null) }
    }

    private fun setupExpandedTopRow(context: Context) {
        val closeBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.close_expanded_row)
        val selectAllBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_select_all)
        val copyBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_copy)
        val pasteBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_paste)
        val leftBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_cursor_left)
        val rightBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_cursor_right)
        val fontIncreaseBtn = expandedTopRowContainer?.findViewById<Button>(R.id.btn_font_increase)
        val fontDecreaseBtn = expandedTopRowContainer?.findViewById<Button>(R.id.btn_font_decrease)
        val gurbaniSearchBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_gurbani_search_shortcut)

        closeBtn?.setOnClickListener { expandedTopRowContainer?.visibility = View.GONE; fixedTopRowButtons?.visibility = View.VISIBLE }
        selectAllBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }
        copyBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.copy) }
        pasteBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.paste) }
        leftBtn?.setOnClickListener { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)) }
        rightBtn?.setOnClickListener { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)) }
        fontIncreaseBtn?.setOnClickListener { adjustFontSize(5) }
        fontDecreaseBtn?.setOnClickListener { adjustFontSize(-5) }
        gurbaniSearchBtn?.setOnClickListener { openGurbaniSearchWebsite() }
    }

    private fun adjustFontSize(delta: Int) {
        val currentSize = sharedPreferences.getInt("font_size_abs", 50)
        val newSize = (currentSize + delta).coerceIn(30, 80)
        sharedPreferences.edit().putInt("font_size_abs", newSize).apply()
        updateKeyboardSettings()
    }

    private fun applyOneHandedMode() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val mode = if (isLandscape) "off" else sharedPreferences.getString("pref_one_handed_mode", "off")
        val mainLayout = mainKeyboardLayout ?: return
        val spacerLeft = keyboardInputContainer?.findViewById<View>(R.id.one_handed_spacer_left)
        val spacerRight = keyboardInputContainer?.findViewById<View>(R.id.one_handed_spacer_right)

        when (mode) {
            "left" -> {
                mainLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 85f)
                spacerLeft?.visibility = View.GONE
                spacerRight?.apply {
                    visibility = View.VISIBLE
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 15f)
                }
            }
            "right" -> {
                mainLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 85f)
                spacerRight?.visibility = View.GONE
                spacerLeft?.apply {
                    visibility = View.VISIBLE
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 15f)
                }
            }
            else -> {
                mainLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 100f)
                spacerLeft?.visibility = View.GONE; spacerRight?.visibility = View.GONE
            }
        }
        mainLayout.requestLayout()
    }

    private fun customizeTopRowButtons(context: Context) {
        fixedTopRowButtons?.removeAllViews()
        val buttonOrder = listOf(
            ImeConstants.KEYCODE_EMOJI,
            ImeConstants.KEYCODE_GURBANI_PLAYER,
            ImeConstants.KEYCODE_VOICE_INPUT,
            ImeConstants.KEYCODE_LANGUAGE_SWITCH,
            ImeConstants.KEYCODE_TRANSLATE,
            ImeConstants.KEYCODE_GURBANI_SEARCH,
            -105, 
            ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL,
            ImeConstants.KEYCODE_SETTINGS
        )

        val tv = TypedValue(); context.theme.resolveAttribute(R.attr.iconColor, tv, true)
        val iconColor = if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) tv.data else Color.BLACK
        val inflater = LayoutInflater.from(context)

        for (keycode in buttonOrder) {
            val button = inflater.inflate(R.layout.top_row_button, fixedTopRowButtons, false) as ImageButton
            val config = getButtonConfig(keycode)
            button.setImageResource(config.first)
            button.imageTintList = ColorStateList.valueOf(iconColor)
            button.setOnClickListener { config.second() }
            fixedTopRowButtons?.addView(button)
        }
    }

    private fun getButtonConfig(keycode: Int): Pair<Int, () -> Unit> {
        return when (keycode) {
            ImeConstants.KEYCODE_EMOJI -> R.drawable.ic_emoji to { keyboardActionListener.onKey(ImeConstants.KEYCODE_EMOJI, null) }
            ImeConstants.KEYCODE_GURBANI_PLAYER -> { val icon = if (isGurbaniPlayerPlaying) R.drawable.ic_pause else R.drawable.ic_play; icon to { toggleGurbaniPlayer() } }
            ImeConstants.KEYCODE_VOICE_INPUT -> R.drawable.ic_mic to { keyboardActionListener.onKey(ImeConstants.KEYCODE_VOICE_INPUT, null) }
            ImeConstants.KEYCODE_LANGUAGE_SWITCH -> R.drawable.ic_language to { keyboardActionListener.onKey(ImeConstants.KEYCODE_LANGUAGE_SWITCH, null) }
            ImeConstants.KEYCODE_TRANSLATE -> R.drawable.ic_translate to { keyboardActionListener.onKey(ImeConstants.KEYCODE_TRANSLATE, null) }
            ImeConstants.KEYCODE_GURBANI_SEARCH -> R.drawable.ic_search to { openGurbaniSearchWebsite() }
            -105 -> R.drawable.outline_arrows_output_24 to { 
                fixedTopRowButtons?.visibility = View.GONE
                expandedTopRowContainer?.visibility = View.VISIBLE
            }
            ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL -> R.drawable.ic_calender to { keyboardActionListener.onKey(ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL, null) }
            ImeConstants.KEYCODE_SETTINGS -> R.drawable.ic_settings to { keyboardActionListener.onKey(ImeConstants.KEYCODE_SETTINGS, null) }
            else -> R.drawable.ic_keyboard to { }
        }
    }

    private fun openGurbaniSearchWebsite() {
        val ic = currentInputConnection
        var query = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString()
        if (query.isEmpty() && ic != null) {
            val textBefore = ic.getTextBeforeCursor(100, 0)
            if (!textBefore.isNullOrEmpty()) query = textBefore.toString().trim()
        }
        val baseUrl = "https://gurbaninow.com"
        val finalUrl = if (query.isNotEmpty()) { try { baseUrl + java.net.URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { "https://gurbaninow.com/" } } else "https://gurbaninow.com/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun toggleGurbaniPlayer() {
        isGurbaniPlayerPlaying = !isGurbaniPlayerPlaying
        val intent = Intent(this, GurbaniPlayerService::class.java).apply { action = if (isGurbaniPlayerPlaying) GurbaniPlayerService.ACTION_PLAY else GurbaniPlayerService.ACTION_PAUSE }
        startService(intent)
        val theme = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light")
        customizeTopRowButtons(ContextThemeWrapper(this, keyboardManager.getThemeResIdForValue(theme)))
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) { 
        if (key == ImeConstants.PREF_KEYBOARD_THEME || key == ImeConstants.PREF_KEY_HEIGHT || key == ImeConstants.PREF_KEY_GAP || key == "pref_one_handed_mode") setInputView(onCreateInputView())
        else if (key == "font_size_abs" || key == "popup_on_keypress") updateKeyboardSettings()
    }

    private fun updateKeyboardSettings() {
        val popupOn = sharedPreferences.getBoolean("popup_on_keypress", true)
        kv?.isPreviewEnabled = popupOn
        kv?.setKeyTextSize(sharedPreferences.getInt("font_size_abs", 50).toFloat())
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        updateKeyboardSettings(); updateEnterKey(editorInfo)
        val inputType = editorInfo?.inputType ?: 0
        if ((inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER) handleKeyboardSwitch(KeyboardType.NUMPAD)
        else if ((inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) handleKeyboardSwitch(KeyboardType.ENGLISH)
        else switchPanel(ImeConstants.PANEL_KEYBOARD, forceRedraw = true)
        kv?.post { kv?.requestLayout(); kv?.invalidate() }
        gurmukhiInputHandler.reset()
        if (::keyboardActionListener.isInitialized) keyboardActionListener.resetBuffers()
        refreshClipboardHistory(); applyOneHandedMode()
    }

    private fun updateEnterKey(editorInfo: EditorInfo?) {
        val kb = kv?.keyboard as? MyKeyboard ?: return
        val enterKey = kb.keys.find { it.codes.contains(Keyboard.KEYCODE_DONE) } as? MyKey ?: return
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val isWhatsApp = editorInfo?.packageName == "com.whatsapp"
        if (isWhatsApp) { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_return_arrow); enterKey.label = null }
        else {
            when (action) {
                EditorInfo.IME_ACTION_SEARCH -> { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_search); enterKey.label = null }
                EditorInfo.IME_ACTION_GO -> { enterKey.icon = null; enterKey.label = "Go" }
                else -> { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_return_arrow); enterKey.label = null }
            }
        }
        kv?.invalidateAllKeys()
    }

    internal fun switchPanel(panel: Int, forceRedraw: Boolean = false) {
        currentPanel = panel
        val isCalendar = panel == ImeConstants.PANEL_NANAKSHAHI_CALENDAR
        mainKeyboardLayout?.visibility = if (isCalendar) View.INVISIBLE else View.VISIBLE
        nanakshahiCalendarPanelContainer?.visibility = if (isCalendar) View.VISIBLE else View.GONE
        translationPanelContainer?.visibility = if (panel == ImeConstants.PANEL_TRANSLATION) View.VISIBLE else View.GONE
        emojiPanelContainer?.visibility = if (panel == ImeConstants.PANEL_EMOJI) View.VISIBLE else View.GONE
        clipboardPanelLayout?.visibility = if (panel == ImeConstants.PANEL_CLIPBOARD) View.VISIBLE else View.GONE
        if (forceRedraw) kv?.invalidateAllKeys()
    }

    internal fun handleKeyboardSwitch(newType: KeyboardType) {
        val kb = keyboardManager.switchKeyboard(newType)
        if (kb != null) { kv?.keyboard = kb; updateKeyboardSettings(); kv?.invalidateAllKeys() }
    }

    override fun onTextRecognized(text: String, isFinal: Boolean) {
        if (currentPanel == ImeConstants.PANEL_TRANSLATION) { if (isFinal) translationPanelContainer?.findViewById<EditText>(R.id.translation_input_text)?.setText(text) }
        else { if (isFinal) currentInputConnection.commitText(text, 1) }
    }

    override fun onListeningError(errorMessage: String) { if (errorMessage.contains("Missing RECORD_AUDIO permission")) launchAppSettings() else Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show(); voiceInputPopupContainer?.visibility = View.GONE }
    override fun onReadyForSpeech() { voiceInputPopupContainer?.visibility = View.VISIBLE }
    override fun onEndOfSpeech() { voiceInputPopupContainer?.visibility = View.GONE }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d("MyKeyboardIME", "TTS Initialized successfully")
        } else {
            Log.e("MyKeyboardIME", "TTS Initialization failed with status: $status")
        }
    }

    private fun speak(text: String, language: String) = tts?.run { val locale = if (language.contains("-")) (if (language.split("-").size >= 2) Locale(language.split("-")[0], language.split("-")[1]) else Locale(language.split("-")[0])) else Locale(language); this.language = locale; setSpeechRate(speechRate); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) voices?.takeIf { it.isNotEmpty() }?.let { v -> (v.find { it.name.lowercase().let { n -> n.contains("female") || n.contains("woman") } && it.locale.language == locale.language } ?: v.find { it.locale.language == locale.language })?.let { voice = it } }; speak(text, TextToSpeech.QUEUE_FLUSH, null, "") }

    private fun refreshClipboardHistory(query: String? = null) = serviceScope.launch { databaseHelper.getClipboardHistory(query, 50).let { items -> withContext(Dispatchers.Main) { clipboardAdapter?.updateItems(items) } } }
}
