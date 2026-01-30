package com.iqbal.gurmukhikeyboard50

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.emoji2.text.EmojiCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.tabs.TabLayout
import com.iqbal.CandidateView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
    private var aiAssistantPanelContainer: View? = null
    private var emojiPanelContainer: View? = null
    private var clipboardPanelLayout: View? = null
    private var voiceInputPopupContainer: View? = null
    private var expandedTopRowContainer: View? = null
    private var suggestionPanelRoot: View? = null
    private var candidateView: @Suppress("REDECLARATION") CandidateView? = null
    private var mainKeyboardLayout: View? = null
    private var fixedTopRowButtons: LinearLayout? = null

    internal lateinit var voiceInputManager: VoiceInputManager
    internal var translationInput: EditText? = null
    internal var aiPromptInput: EditText? = null

    internal lateinit var sharedPreferences: SharedPreferences
    internal lateinit var keyboardManager: KeyboardManager
    internal lateinit var gurmukhiInputHandler: GurmukhiInputHandler
    internal lateinit var keyboardActionListener: MyKeyboardActionListener
    internal lateinit var translationManager: TranslationManager
    private lateinit var aiHelper: AiHelper
    private lateinit var databaseHelper: DatabaseHelper
    private var predictionEngine: PredictionEngine? = null
    private var tts: TextToSpeech? = null
    private var clipboardAdapter: ClipboardAdapter? = null
    private var clipboardManager: ClipboardManager? = null

    private var speechRate: Float = 0.5f

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (sharedPreferences.getBoolean(ImeConstants.PREF_CLIPBOARD_HISTORY, true)) {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    serviceScope.launch(Dispatchers.IO) {
                        val history = databaseHelper.getClipboardHistory(null, 1)
                        if (history.isEmpty() || history[0].text != text) {
                            databaseHelper.addClipboardItem(text)
                            refreshClipboardHistory()
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        keyboardManager = KeyboardManager(applicationContext, sharedPreferences)
        databaseHelper = DatabaseHelper(this)
        TransliterationHelper.init(this)
        serviceScope.launch { withContext(Dispatchers.IO) { DictionaryHelper.convertParagraphToDictionary(applicationContext); predictionEngine = PredictionEngine(applicationContext) } }
        gurmukhiInputHandler = GurmukhiInputHandler { word, _ -> updateSuggestions(word) }
        voiceInputManager = VoiceInputManager(this, this)
        translationManager = TranslationManager(this)
        aiHelper = AiHelper(this)
        tts = TextToSpeech(this, this)
        EmojiCompat.init(this)

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onDestroy() { 
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        voiceInputManager.stopVoiceRecognition()
        translationManager.close()
        tts?.stop()
        tts?.shutdown()
        serviceScope.cancel() 
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean { if (keyCode == KeyEvent.KEYCODE_BACK && currentPanel != ImeConstants.PANEL_KEYBOARD) { switchPanel(ImeConstants.PANEL_KEYBOARD); return true }; return super.onKeyDown(keyCode, event) }

    override fun onCreateInputView(): View {
        val themeValue = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light")
        val themeResId = keyboardManager.getThemeResIdForValue(themeValue)
        val themedContext: Context = ContextThemeWrapper(this, themeResId)
        val totalWidth = resources.displayMetrics.widthPixels
        keyboardManager.loadAllKeyboards(themedContext, totalWidth)
        val root = LayoutInflater.from(themedContext).inflate(R.layout.input_view, null) as FrameLayout
        keyboardInputContainer = root; mainKeyboardLayout = root.findViewById(R.id.mainKeyboardLayout); nanakshahiCalendarPanelContainer = root.findViewById(R.id.nanakshahi_calendar_panel_container); translationPanelContainer = root.findViewById(R.id.translation_panel_container); aiAssistantPanelContainer = root.findViewById(R.id.ai_assistant_panel_container); emojiPanelContainer = root.findViewById(R.id.emoji_panel_container); clipboardPanelLayout = root.findViewById(R.id.clipboard_panel_layout); voiceInputPopupContainer = root.findViewById(R.id.voice_input_popup_container); expandedTopRowContainer = root.findViewById(R.id.expanded_top_row_container); fixedTopRowButtons = root.findViewById(R.id.fixed_top_row_buttons); suggestionPanelRoot = root.findViewById(R.id.suggestion_panel_root); candidateView = suggestionPanelRoot?.findViewById(R.id.candidate_view); kv = root.findViewById(R.id.keyboardView)
        kv?.keyboard = keyboardManager.getCurrentKeyboard(); updateKeyboardSettings()
        kv?.setService(this)
        candidateView?.setOnSuggestionClickListener { handleSuggestionClick(it) }
        keyboardActionListener = MyKeyboardActionListener(this); kv?.setOnKeyboardActionListener(keyboardActionListener); customizeTopRowButtons(themedContext); nanakshahiCalendarPanel = NanakshahiCalendarPanel(themedContext, { switchPanel(ImeConstants.PANEL_KEYBOARD) }, { dateStr -> currentInputConnection?.commitText(dateStr, 1) }); (nanakshahiCalendarPanelContainer as? FrameLayout)?.addView(nanakshahiCalendarPanel?.view); setupTranslationPanel(themedContext); setupAiAssistantPanel(themedContext); setupEmojiPanel(themedContext); setupClipboardPanel(themedContext); setupExpandedTopRow(themedContext)
        
        root.findViewById<ImageButton>(R.id.btn_move_to_left).setOnClickListener { 
            sharedPreferences.edit().putString("pref_one_handed_mode", "left").apply()
            applyOneHandedMode()
        }
        root.findViewById<ImageButton>(R.id.btn_move_to_right).setOnClickListener { 
            sharedPreferences.edit().putString("pref_one_handed_mode", "right").apply()
            applyOneHandedMode()
        }
        root.findViewById<ImageButton>(R.id.btn_expand_left).setOnClickListener { 
            sharedPreferences.edit().putString("pref_one_handed_mode", "off").apply()
            applyOneHandedMode()
        }
        root.findViewById<ImageButton>(R.id.btn_expand_right).setOnClickListener { 
            sharedPreferences.edit().putString("pref_one_handed_mode", "off").apply()
            applyOneHandedMode()
        }
        
        applyOneHandedMode(); applyCustomBackground(); return root
    }

    private fun applyCustomBackground() {
        val theme = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light")
        val imagePath = sharedPreferences.getString(ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE, null)
        val dimAmount = sharedPreferences.getInt(ImeConstants.PREF_BACKGROUND_DIM_AMOUNT, 50)

        if (theme == "custom" && !imagePath.isNullOrEmpty()) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imagePath, options)
                val reqWidth = resources.displayMetrics.widthPixels
                val reqHeight = resources.displayMetrics.heightPixels / 2
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                val bitmap = BitmapFactory.decodeFile(imagePath, options)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(resources, bitmap)
                    val dimColor = Color.argb((dimAmount * 2.55).toInt(), 0, 0, 0)
                    drawable.setColorFilter(dimColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                    mainKeyboardLayout?.background = drawable
                    suggestionPanelRoot?.setBackgroundColor(Color.TRANSPARENT)
                    fixedTopRowButtons?.setBackgroundColor(Color.TRANSPARENT)
                }
            } catch (e: Exception) {
                Log.e("MyKeyboardIME", "Error applying background", e)
            }
        } else {
            mainKeyboardLayout?.background = null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2; val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) { inSampleSize *= 2 }
        }
        return inSampleSize
    }

    private fun handleSuggestionClick(suggestion: String) {
        val ic = currentInputConnection ?: return
        when (suggestion) {
            "↶ Undo" -> { val now = SystemClock.uptimeMillis(); ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON)); ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON)); return }
            "↷ Redo" -> { val now = SystemClock.uptimeMillis(); ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON)); ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON)); return }
            "💬 WhatsApp" -> { val typedWord = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString(); if (typedWord.isNotEmpty()) WhatsAppHelper.openWhatsAppChat(this, typedWord); return }
        }
        ic.beginBatchEdit(); ic.finishComposingText()
        val typedWord = when (keyboardManager.currentKeyboardType) { KeyboardType.GURMUKHI -> gurmukhiInputHandler.getCurrentWord(); else -> keyboardActionListener.englishWordBuffer.toString() }
        if (typedWord.isNotEmpty()) ic.deleteSurroundingText(typedWord.length, 0)
        if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.reset() else keyboardActionListener.englishWordBuffer.setLength(0)
        val commitText = if (suggestion.startsWith("=")) suggestion.substring(1) else suggestion
        ic.commitText("$commitText ", 1); ic.endBatchEdit(); updateSuggestions("")
        if (suggestion.isNotEmpty() && !suggestion.startsWith("=") && suggestion != "💬 WhatsApp" && !suggestion.startsWith("📅")) serviceScope.launch(Dispatchers.IO) { databaseHelper.addWord(suggestion.trim()) }
    }

    internal fun updateSuggestions(word: String) {
        val suggestions = (predictionEngine?.getSuggestions(word)?.toMutableList() ?: mutableListOf())
        if (WhatsAppHelper.isPhoneNumber(word)) suggestions.add(0, "💬 WhatsApp")
        currentInputConnection?.let { ic -> val textBefore = ic.getTextBeforeCursor(20, 0)?.toString() ?: ""; CalculatorHelper.evaluate(textBefore)?.let { if (!suggestions.contains(it)) suggestions.add(0, it) } }
        EmojiSearchHelper.searchEmoji(word)?.let { if (!suggestions.contains(it)) suggestions.add(0, it) }
        PunjabiNumberConverter.convert(word)?.let { if (!suggestions.contains(it)) suggestions.add(0, it) }
        
        val loweredWord = word.lowercase()
        if (loweredWord == "date" || loweredWord == "today" || word == "ਤਾਰੀਖ" || word == "ਅੱਜ") {
            val now = Calendar.getInstance(NanakshahiCalendar.currentTimeZone)
            val dateStr = "📅 " + NanakshahiCalendar.getShortNanakshahiDate(this, now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR))
            suggestions.add(0, dateStr)
        }

        val finalSuggestions = if (word.isEmpty()) currentInputConnection?.getTextBeforeCursor(50, 0)?.toString()?.trim()?.split(" ")?.lastOrNull()?.let { lastWord -> PunjabiNextWordLM.predict(lastWord).takeIf { it.isNotEmpty() }?.let { (it + suggestions).distinct().toMutableList() } } ?: suggestions else suggestions
        candidateView?.post { candidateView?.setSuggestions(finalSuggestions) }
    }

    private fun customizeTopRowButtons(context: Context) {
        fixedTopRowButtons?.removeAllViews()
        val pinnedSet = sharedPreferences.getStringSet(ImeConstants.PREF_TOOLBAR_PINNED_ITEMS, null)
        val buttonOrder = if (pinnedSet == null || pinnedSet.isEmpty()) {
            listOf(ImeConstants.KEYCODE_EMOJI, ImeConstants.KEYCODE_GURBANI_PLAYER, ImeConstants.KEYCODE_VOICE_INPUT, ImeConstants.KEYCODE_LANGUAGE_SWITCH, ImeConstants.KEYCODE_TRANSLATE, ImeConstants.KEYCODE_AI_ASSISTANT, ImeConstants.KEYCODE_OCR, ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL, -105, ImeConstants.KEYCODE_SETTINGS)
        } else {
            val list = pinnedSet.map { mapPreferenceValueToKeyCode(it) }.toMutableList()
            list.add(-105) 
            if (!pinnedSet.contains("settings")) { list.add(ImeConstants.KEYCODE_SETTINGS) }
            list
        }
        val tv = TypedValue(); context.theme.resolveAttribute(R.attr.iconColor, tv, true); val iconColor = if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) tv.data else Color.BLACK
        val inflater = LayoutInflater.from(context)
        for (keycode in buttonOrder) { val button = inflater.inflate(R.layout.top_row_button, fixedTopRowButtons, false) as ImageButton; val config = getButtonConfig(keycode); button.setImageResource(config.first); button.imageTintList = ColorStateList.valueOf(iconColor); button.setOnClickListener { config.second() }; fixedTopRowButtons?.addView(button) }
    }
    
    private fun mapPreferenceValueToKeyCode(value: String): Int {
        return when (value) {
            "translate" -> ImeConstants.KEYCODE_TRANSLATE
            "ai" -> ImeConstants.KEYCODE_AI_ASSISTANT
            "emoji" -> ImeConstants.KEYCODE_EMOJI
            "mic" -> ImeConstants.KEYCODE_VOICE_INPUT
            "gurmukhi_search" -> ImeConstants.KEYCODE_GURBANI_SEARCH
            "gurbani" -> ImeConstants.KEYCODE_GURBANI_PLAYER
            "settings" -> ImeConstants.KEYCODE_SETTINGS
            "image_to_text" -> ImeConstants.KEYCODE_OCR
            "calendar" -> ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL
            else -> ImeConstants.KEYCODE_SETTINGS
        }
    }

    private fun getButtonConfig(keycode: Int): Pair<Int, () -> Unit> {
        return when (keycode) {
            ImeConstants.KEYCODE_EMOJI -> R.drawable.ic_emoji to { keyboardActionListener.onKey(ImeConstants.KEYCODE_EMOJI, null) }
            ImeConstants.KEYCODE_GURBANI_PLAYER -> { val icon = if (isGurbaniPlayerPlaying) R.drawable.ic_pause else R.drawable.ic_play; icon to { toggleGurbaniPlayer() } }
            ImeConstants.KEYCODE_VOICE_INPUT -> R.drawable.ic_mic to { keyboardActionListener.onKey(ImeConstants.KEYCODE_VOICE_INPUT, null) }
            ImeConstants.KEYCODE_LANGUAGE_SWITCH -> R.drawable.ic_language to { keyboardActionListener.onKey(ImeConstants.KEYCODE_LANGUAGE_SWITCH, null) }
            ImeConstants.KEYCODE_TRANSLATE -> R.drawable.ic_translate to { keyboardActionListener.onKey(ImeConstants.KEYCODE_TRANSLATE, null) }
            ImeConstants.KEYCODE_AI_ASSISTANT -> R.drawable.ic_ai to { keyboardActionListener.onKey(ImeConstants.KEYCODE_AI_ASSISTANT, null) }
            ImeConstants.KEYCODE_OCR -> R.drawable.ic_camera to { launchOCR() }
            ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL -> R.drawable.ic_calender to { keyboardActionListener.onKey(ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL, null) }
            -105 -> R.drawable.outline_arrows_output_24 to { fixedTopRowButtons?.visibility = View.GONE; expandedTopRowContainer?.visibility = View.VISIBLE }
            ImeConstants.KEYCODE_SETTINGS -> R.drawable.ic_settings to { keyboardActionListener.onKey(ImeConstants.KEYCODE_SETTINGS, null) }
            else -> R.drawable.ic_keyboard to { }
        }
    }

    private fun launchOCR() { val intent = Intent(this, OCRActivity::class.java); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (intent?.hasExtra("recognized_text") == true) { val text = intent.getStringExtra("recognized_text") ?: ""; currentInputConnection?.commitText(text, 1) }; return super.onStartCommand(intent, flags, startId) }

    internal fun launchSettings() { val intent = Intent(this, KeyboardSettingsActivity::class.java); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent) }
    private fun launchAppSettings() { val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); intent.data = Uri.fromParts("package", packageName, null); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent) }
    internal fun learnWord(word: String) { if (word.length >= 2) serviceScope.launch(Dispatchers.IO) { DictionaryHelper.addWordToDictionary(applicationContext, word); databaseHelper.addWord(word); predictionEngine = PredictionEngine(applicationContext) } }
    private fun setupEmojiPanel(context: Context) { val emojiRecyclerView = emojiPanelContainer?.findViewById<RecyclerView>(R.id.emoji_recycler_view); val emojiTabLayout = emojiPanelContainer?.findViewById<TabLayout>(R.id.emoji_category_tabs); val allEmojisWithHeaders = EmojiHelper.getAllEmojisWithHeaders(context); val emojiAdapter = EmojiAdapter(allEmojisWithHeaders, { onEmojiClicked(it) }, { it is String && EmojiData.categories.containsKey(it) }); val layoutManager = GridLayoutManager(context, 8); layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int): Int = if (emojiAdapter.isHeader(position)) 8 else 1 }; emojiRecyclerView?.layoutManager = layoutManager; emojiRecyclerView?.adapter = emojiAdapter; val categories = EmojiHelper.getEmojiCategories(); for (category in categories) emojiTabLayout?.addTab(emojiTabLayout.newTab().setText(category)); emojiTabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener { override fun onTabSelected(tab: TabLayout.Tab?) { val pos = EmojiHelper.getPositionForCategory(context, tab?.text.toString()); (emojiRecyclerView?.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(pos, 0) }; override fun onTabUnselected(tab: TabLayout.Tab?) {}; override fun onTabReselected(tab: TabLayout.Tab?) {} }); emojiRecyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() { override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { super.onScrolled(recyclerView, dx, dy); val pos = (recyclerView.layoutManager as GridLayoutManager).findFirstVisibleItemPosition(); val category = EmojiHelper.getCategoryForPosition(context, pos); val tabIndex = categories.indexOf(category); if (tabIndex != -1 && emojiTabLayout?.selectedTabPosition != tabIndex) emojiTabLayout?.getTabAt(tabIndex)?.select() } }); emojiPanelContainer?.findViewById<ImageButton>(R.id.emoji_panel_return_to_gurmukhi)?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }; emojiPanelContainer?.findViewById<ImageButton>(R.id.emoji_panel_backspace)?.setOnClickListener { keyboardActionListener.onKey(Keyboard.KEYCODE_DELETE, null) } }
    private fun onEmojiClicked(emoji: String) { currentInputConnection?.commitText(emoji, 1); RecentEmojiManager.addEmoji(this, emoji) }
    private fun setupTranslationPanel(context: Context) { translationInput = translationPanelContainer?.findViewById<EditText>(R.id.translation_input_text); val translationOutput = translationPanelContainer?.findViewById<TextView>(R.id.translation_output_text); val sourceLanguageSpinner = translationPanelContainer?.findViewById<Spinner>(R.id.source_language_spinner); val targetLanguageSpinner = translationPanelContainer?.findViewById<Spinner>(R.id.target_language_spinner) ; val swapLanguagesButton = translationPanelContainer?.findViewById<ImageButton>(R.id.swap_languages_button); val speakTranslationButton = translationPanelContainer?.findViewById<ImageButton>(R.id.speak_translation_button); val voiceTranslateButton = translationPanelContainer?.findViewById<ImageButton>(R.id.voice_translate_button); val closeButton = translationPanelContainer?.findViewById<ImageButton>(R.id.close_translation_panel_button); val speedSeekBar = translationPanelContainer?.findViewById<SeekBar>(R.id.voice_speed_seekbar); val languageNames = translationManager.availableLanguages.map { it.name }; val adapter = ArrayAdapter(context, R.layout.spinner_item, languageNames); adapter.setDropDownViewResource(R.layout.spinner_dropdown_item); sourceLanguageSpinner?.adapter = adapter; targetLanguageSpinner?.adapter = adapter; val onLanguageSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { when (parent?.id) { R.id.source_language_spinner -> translationManager.sourceLanguage = translationManager.availableLanguages[position]; R.id.target_language_spinner -> translationManager.targetLanguage = translationManager.availableLanguages[position] }; val inputText = translationInput?.text?.toString(); if (!inputText.isNullOrEmpty()) translationManager.translate(inputText, translationOutput) }; override fun onNothingSelected(parent: AdapterView<*>?) {} }; sourceLanguageSpinner?.onItemSelectedListener = onLanguageSelectedListener; targetLanguageSpinner?.onItemSelectedListener = onLanguageSelectedListener; swapLanguagesButton?.setOnClickListener { translationManager.swapLanguages(); updateSpinnerSelections() }; speedSeekBar?.progress = (speechRate * 100).toInt(); speedSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { speechRate = (progress / 100.0f).coerceIn(0.1f, 2.0f) }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} }); speakTranslationButton?.setOnClickListener { val textToSpeak = translationOutput?.text?.toString(); if (!textToSpeak.isNullOrEmpty() && textToSpeak != "Translating...") speak(textToSpeak, translationManager.targetLanguage.speechCode) else Toast.makeText(this, "ਪਹਿਲਾਂ ਟੈਕਸਟ ਲਿਖੋ", Toast.LENGTH_SHORT).show() }; voiceTranslateButton?.setOnClickListener { voiceInputManager.startVoiceRecognition(translationManager.sourceLanguage.speechCode) }; closeButton?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }; translationInput?.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}; override fun afterTextChanged(s: Editable?) { translationManager.translate(s.toString(), translationOutput) } }); updateSpinnerSelections() }
    
    private fun setupAiAssistantPanel(context: Context) {
        aiPromptInput = aiAssistantPanelContainer?.findViewById(R.id.ai_input_text)
        val btnAskAi = aiAssistantPanelContainer?.findViewById<ImageButton>(R.id.ai_answer_search_button)
        val btnCloseAi = aiAssistantPanelContainer?.findViewById<ImageButton>(R.id.ai_answer_back_button)
        val tvAnswer = aiAssistantPanelContainer?.findViewById<TextView>(R.id.ai_answer_text_view)
        val btnCopy = aiAssistantPanelContainer?.findViewById<ImageButton>(R.id.ai_answer_copy_button)
        val btnInsert = aiAssistantPanelContainer?.findViewById<ImageButton>(R.id.ai_answer_insert_button)
        val btnCorrect = aiAssistantPanelContainer?.findViewById<ImageButton>(R.id.ai_correct_word_button)
        val btnContinue = aiAssistantPanelContainer?.findViewById<ImageButton>(R.id.ai_continue_writing_button)

        btnAskAi?.setOnClickListener {
            val prompt = aiPromptInput?.text?.toString() ?: ""
            if (prompt.isNotBlank()) {
                serviceScope.launch {
                    tvAnswer?.text = "AI is thinking..."
                    val response = aiHelper.getAiResponse(prompt)
                    tvAnswer?.text = response
                }
            }
        }

        btnCorrect?.setOnClickListener {
            val prompt = aiPromptInput?.text?.toString() ?: ""
            if (prompt.isNotBlank()) {
                serviceScope.launch {
                    tvAnswer?.text = "Correcting..."
                    val response = aiHelper.getAiResponse("correct grammar: $prompt")
                    tvAnswer?.text = response
                }
            }
        }

        btnContinue?.setOnClickListener {
            val prompt = aiPromptInput?.text?.toString() ?: ""
            if (prompt.isNotBlank()) {
                serviceScope.launch {
                    tvAnswer?.text = "Writing..."
                    val response = aiHelper.getAiResponse("continue writing: $prompt")
                    tvAnswer?.text = response
                }
            }
        }

        btnCopy?.setOnClickListener {
            val text = tvAnswer?.text?.toString() ?: ""
            if (text.isNotEmpty() && text != "AI is thinking..." && text != "Correcting..." && text != "Writing...") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("AI Response", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "ਕਾਪੀ ਕੀਤਾ ਗਿਆ", Toast.LENGTH_SHORT).show()
            }
        }

        btnInsert?.setOnClickListener {
            val text = tvAnswer?.text?.toString() ?: ""
            if (text.isNotEmpty() && text != "AI is thinking..." && text != "Correcting..." && text != "Writing...") {
                currentInputConnection?.commitText(text, 1)
            }
        }

        btnCloseAi?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }
    }

    private fun updateSpinnerSelections() { val srcS = translationPanelContainer?.findViewById<Spinner>(R.id.source_language_spinner); val tgtS = translationPanelContainer?.findViewById<Spinner>(R.id.target_language_spinner); val srcP = translationManager.availableLanguages.indexOf(translationManager.sourceLanguage); val tgtP = translationManager.availableLanguages.indexOf(translationManager.targetLanguage); if (srcP != -1) srcS?.setSelection(srcP); if (tgtP != -1) tgtS?.setSelection(tgtP) }
    private fun setupClipboardPanel(context: Context) { val recyclerView = clipboardPanelLayout?.findViewById<RecyclerView>(R.id.clipboard_recycler_view); val searchEditText = clipboardPanelLayout?.findViewById<EditText>(R.id.clipboard_search_edit_text); val backButton = clipboardPanelLayout?.findViewById<ImageButton>(R.id.back_to_keyboard_button); val backspaceButton = clipboardPanelLayout?.findViewById<ImageButton>(R.id.clipboard_backspace_button); recyclerView?.layoutManager = LinearLayoutManager(context); clipboardAdapter = ClipboardAdapter(emptyList(), { currentInputConnection?.commitText(it, 1) }, { _ -> }, { serviceScope.launch { databaseHelper.deleteClipboardItems(listOf(it)); refreshClipboardHistory(searchEditText?.text?.toString()) } }, { id, pinned -> serviceScope.launch { databaseHelper.updateClipboardPinned(id, pinned); refreshClipboardHistory(searchEditText?.text?.toString()) } }); recyclerView?.adapter = clipboardAdapter; searchEditText?.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}; override fun afterTextChanged(s: Editable?) { refreshClipboardHistory(s.toString()) } }); backButton?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }; backspaceButton?.setOnClickListener { keyboardActionListener.onKey(Keyboard.KEYCODE_DELETE, null) } }
    private fun setupExpandedTopRow(context: Context) { val closeBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.close_expanded_row); val selectAllBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_select_all); val copyBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_copy); val pasteBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_paste) ; val leftBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_cursor_left); val rightBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_cursor_right); val fontIncreaseBtn = expandedTopRowContainer?.findViewById<Button>(R.id.btn_font_increase); val fontDecreaseBtn = expandedTopRowContainer?.findViewById<Button>(R.id.btn_font_decrease); val gurbaniSearchBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_gurbani_search_shortcut); closeBtn?.setOnClickListener { expandedTopRowContainer?.visibility = View.GONE; fixedTopRowButtons?.visibility = View.VISIBLE }; selectAllBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }; copyBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.copy) }; pasteBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.paste) }; leftBtn?.setOnClickListener { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)) }; rightBtn?.setOnClickListener { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)) }; fontIncreaseBtn?.setOnClickListener { adjustFontSize(5) }; fontDecreaseBtn?.setOnClickListener { adjustFontSize(-5) }; gurbaniSearchBtn?.setOnClickListener { openGurbaniSearchWebsite() } }
    
    private fun adjustFontSize(delta: Int) { 
        val currentSize = sharedPreferences.getInt(ImeConstants.PREF_FONT_SIZE, 18)
        val newSize = (currentSize + delta).coerceIn(10, 30)
        sharedPreferences.edit().putInt(ImeConstants.PREF_FONT_SIZE, newSize).apply()
        updateKeyboardSettings()
    }

    private fun applyOneHandedMode() { val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE; val mode = if (isLandscape) "off" else sharedPreferences.getString("pref_one_handed_mode", "off") ; val mainLayout = mainKeyboardLayout ?: return; val spacerLeft = keyboardInputContainer?.findViewById<View>(R.id.one_handed_spacer_left); val spacerRight = keyboardInputContainer?.findViewById<View>(R.id.one_handed_spacer_right); when (mode) { "left" -> { mainLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 85f); spacerLeft?.visibility = View.GONE; spacerRight?.apply { visibility = View.VISIBLE; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 15f) } }; "right" -> { mainLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 85f); spacerRight?.visibility = View.GONE; spacerLeft?.apply { visibility = View.VISIBLE; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 15f) } }; else -> { mainLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 100f); spacerLeft?.visibility = View.GONE; spacerRight?.visibility = View.GONE } }; mainLayout.requestLayout() }
    private fun openGurbaniSearchWebsite() { var query = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString(); if (query.isEmpty() && currentInputConnection != null) { val textBefore = currentInputConnection?.getTextBeforeCursor(100, 0); if (!textBefore.isNullOrEmpty()) query = textBefore.toString().trim() }; val baseUrl = "https://gurbaninow.com"; val finalUrl = if (query.isNotEmpty()) { try { baseUrl + java.net.URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { "https://gurbaninow.com/" } } else "https://gurbaninow.com/"; val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ; startActivity(intent) }
    private fun toggleGurbaniPlayer() { isGurbaniPlayerPlaying = !isGurbaniPlayerPlaying; val intent = Intent(this, GurbaniPlayerService::class.java).apply { action = if (isGurbaniPlayerPlaying) GurbaniPlayerService.ACTION_PLAY else GurbaniPlayerService.ACTION_PAUSE }; startService(intent); val theme = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light") ?: "light"; customizeTopRowButtons(ContextThemeWrapper(this, keyboardManager.getThemeResIdForValue(theme))) }
    
    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) { 
        if (key == ImeConstants.PREF_KEYBOARD_THEME || key == ImeConstants.PREF_KEY_HEIGHT || key == ImeConstants.PREF_KEY_GAP || key == "pref_one_handed_mode" || key == ImeConstants.PREF_TOOLBAR_PINNED_ITEMS || key == ImeConstants.PREF_USE_ROUNDED_KEYS || key == ImeConstants.PREF_KEY_ROUNDNESS || key == ImeConstants.PREF_USE_KEY_TRANSPARENCY || key == ImeConstants.PREF_KEY_OPACITY || key == ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE || key == ImeConstants.PREF_BACKGROUND_DIM_AMOUNT) {
            setInputView(onCreateInputView()) 
        } else if (key == ImeConstants.PREF_FONT_SIZE || key == "popup_on_keypress") {
            updateKeyboardSettings() 
        }
    }
    
    private fun updateKeyboardSettings() { 
        val fontSize = sharedPreferences.getInt(ImeConstants.PREF_FONT_SIZE, 18)
        kv?.setKeyTextSize(fontSize.toFloat() * resources.displayMetrics.scaledDensity) 
    }
    
    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) { 
        super.onStartInputView(editorInfo, restarting)
        updateKeyboardSettings()
        updateEnterKey(editorInfo)
        
        val inputType = editorInfo?.inputType ?: 0
        if ((inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER) {
            handleKeyboardSwitch(KeyboardType.NUMPAD)
        } else if ((inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            handleKeyboardSwitch(KeyboardType.ENGLISH)
        } else {
            val currentKB = keyboardManager.currentKeyboardType
            handleKeyboardSwitch(currentKB)
        }
        
        kv?.post { kv?.requestLayout(); kv?.invalidate() }
        gurmukhiInputHandler.reset()
        if (::keyboardActionListener.isInitialized) keyboardActionListener.resetBuffers()
        refreshClipboardHistory()
        applyOneHandedMode()
        applyCustomBackground()
    }

    private fun updateEnterKey(editorInfo: EditorInfo?) { val kb = kv?.keyboard as? MyKeyboard ?: return; val enterKey = kb.keys.find { it.codes.contains(Keyboard.KEYCODE_DONE) } as? MyKey ?: return; val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: 0; val isWhatsApp = editorInfo?.packageName == "com.whatsapp"; if (isWhatsApp) { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_return_arrow); enterKey.label = null } else { when (action) { EditorInfo.IME_ACTION_SEARCH -> { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_search); enterKey.label = null }; EditorInfo.IME_ACTION_GO -> { enterKey.icon = null; enterKey.label = "Go" }; else -> { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_return_arrow); enterKey.label = null } } }; kv?.invalidateAllKeys() }
    internal fun switchPanel(panel: Int, forceRedraw: Boolean = false) { 
        currentPanel = panel
        val isCalendar = panel == ImeConstants.PANEL_NANAKSHAHI_CALENDAR
        
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 300 }
        
        mainKeyboardLayout?.visibility = if (isCalendar) View.INVISIBLE else View.VISIBLE
        nanakshahiCalendarPanelContainer?.visibility = if (isCalendar) View.VISIBLE else View.GONE
        
        val panels = listOf(
            translationPanelContainer to (panel == ImeConstants.PANEL_TRANSLATION),
            aiAssistantPanelContainer to (panel == ImeConstants.PANEL_AI_ASSISTANT),
            emojiPanelContainer to (panel == ImeConstants.PANEL_EMOJI),
            clipboardPanelLayout to (panel == ImeConstants.PANEL_CLIPBOARD)
        )
        
        panels.forEach { (view, shouldShow) ->
            if (shouldShow) {
                if (view?.visibility != View.VISIBLE) {
                    view?.visibility = View.VISIBLE
                    view?.startAnimation(fadeIn)
                }
            } else {
                view?.visibility = View.GONE
            }
        }
        
        if (panel == ImeConstants.PANEL_TRANSLATION) {
            translationInput?.requestFocus()
            translationInput?.setSelection(translationInput?.text?.length ?: 0)
        } else if (panel == ImeConstants.PANEL_AI_ASSISTANT) {
            val ic = currentInputConnection
            val selectedText = ic?.getSelectedText(0)
            val textBefore = ic?.getTextBeforeCursor(100, 0)
            
            val autoPasteText = if (!selectedText.isNullOrBlank()) {
                selectedText.toString()
            } else {
                textBefore?.toString()?.trim() ?: ""
            }
            
            if (autoPasteText.isNotEmpty()) {
                aiPromptInput?.setText(autoPasteText)
            }

            aiPromptInput?.requestFocus()
            aiPromptInput?.setSelection(aiPromptInput?.text?.length ?: 0)
        }
        
        keyboardActionListener.resetBuffers()
        if (forceRedraw) kv?.invalidateAllKeys() 
    }
    internal fun handleKeyboardSwitch(newType: KeyboardType) { val kb = keyboardManager.switchKeyboard(newType); if (kb != null) { kv?.keyboard = kb; updateKeyboardSettings(); kv?.invalidateAllKeys() } }
    override fun onTextRecognized(text: String, isFinal: Boolean) { 
        if (currentPanel == ImeConstants.PANEL_TRANSLATION) { 
            if (isFinal) translationPanelContainer?.findViewById<EditText>(R.id.translation_input_text)?.setText(text) 
        } else if (currentPanel == ImeConstants.PANEL_AI_ASSISTANT) {
             if (isFinal) aiAssistantPanelContainer?.findViewById<EditText>(R.id.ai_input_text)?.setText(text)
        } else { 
            if (isFinal) { currentInputConnection?.finishComposingText(); currentInputConnection?.commitText(text + " ", 1) } 
            else { currentInputConnection?.setComposingText(text, 1) } 
        } 
    }
    override fun onListeningError(errorMessage: String) { if (errorMessage.contains("Missing RECORD_AUDIO permission")) launchAppSettings() else Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show(); voiceInputPopupContainer?.visibility = View.GONE }
    override fun onReadyForSpeech() { voiceInputPopupContainer?.visibility = View.VISIBLE }
    override fun onEndOfSpeech() { voiceInputPopupContainer?.visibility = View.GONE }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) Log.d("MyKeyboardIME", "TTS Initialized successfully") else Log.e("MyKeyboardIME", "TTS Initialization failed with status: $status") }
    private fun speak(text: String, language: String) = tts?.run { 
        val locale = if (language.contains("-")) {
            val parts = language.split("-")
            if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
        } else {
            Locale(language)
        }
        this.language = locale
        setSpeechRate(speechRate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            voices?.takeIf { it.isNotEmpty() }?.let { v ->
                (v.find { it.name.lowercase().let { n -> n.contains("female") || n.contains("woman") } && it.locale.language == locale.language } ?: v.find { it.locale.language == locale.language })?.let { voice = it }
            }
        }
        speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }
    private fun refreshClipboardHistory(query: String? = null) = serviceScope.launch { databaseHelper.getClipboardHistory(query, 50).let { items -> withContext(Dispatchers.Main) { clipboardAdapter?.updateItems(items) } } }

    fun handleGestureSequence(sequence: List<String>) {
        if (sequence.isEmpty()) return
        
        serviceScope.launch {
            val prefix = sequence.joinToString("")
            val suggestions = predictionEngine?.getSuggestions(prefix) ?: emptyList()
            
            withContext(Dispatchers.Main) {
                val ic = currentInputConnection ?: return@withContext
                val wordToCommit = if (suggestions.isNotEmpty()) suggestions[0] else prefix
                
                ic.beginBatchEdit()
                ic.commitText(wordToCommit + " ", 1)
                ic.endBatchEdit()
                candidateView?.setSuggestions(suggestions)
            }
        }
    }
}
