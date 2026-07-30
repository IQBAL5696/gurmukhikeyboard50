package com.iqbal.gurmukhikeyboard50
import android.annotation.SuppressLint; import android.app.Activity; import android.content.BroadcastReceiver; import android.content.ClipboardManager; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.content.res.ColorStateList; import android.content.res.Configuration; import android.graphics.Bitmap; import android.graphics.BitmapFactory; import android.graphics.Canvas; import android.graphics.Matrix; import android.graphics.Paint; import android.graphics.PixelFormat; import android.graphics.Rect; import android.graphics.RectF; import android.graphics.drawable.BitmapDrawable; import android.inputmethodservice.InputMethodService; import android.inputmethodservice.Keyboard; import android.net.Uri; import android.os.Build; import android.os.SystemClock; import android.provider.Settings; import android.speech.tts.TextToSpeech; import android.speech.tts.Voice; import android.text.Editable; import android.text.InputType; import android.text.TextWatcher; import android.util.Log; import android.util.TypedValue; import android.view.*; import android.view.animation.AlphaAnimation; import android.view.animation.Animation; import android.view.inputmethod.EditorInfo; import android.widget.*; import androidx.annotation.RequiresApi; import androidx.appcompat.view.ContextThemeWrapper; import androidx.core.content.ContextCompat; import androidx.core.view.ViewCompat; import androidx.core.view.WindowInsetsCompat; import androidx.emoji2.text.EmojiCompat; import androidx.preference.PreferenceManager; import androidx.recyclerview.widget.GridLayoutManager; import androidx.recyclerview.widget.LinearLayoutManager; import androidx.recyclerview.widget.RecyclerView; import com.android.volley.Request; import com.android.volley.toolbox.JsonObjectRequest; import com.android.volley.toolbox.Volley; import com.google.android.material.tabs.TabLayout; import com.iqbal.CandidateView; import kotlinx.coroutines.CoroutineScope; import kotlinx.coroutines.Dispatchers; import kotlinx.coroutines.SupervisorJob; import kotlinx.coroutines.cancel; import kotlinx.coroutines.launch; import kotlinx.coroutines.withContext; import org.json.JSONArray; import org.json.JSONObject; import java.io.File; import java.util.*

@Suppress("deprecation") class MyKeyboardIME : InputMethodService(), SharedPreferences.OnSharedPreferenceChangeListener, VoiceRecognitionResultListener, TextToSpeech.OnInitListener { companion object { var currentPanel: Int = ImeConstants.PANEL_KEYBOARD }
    var kv: MyKeyboardView? = null; var lastAlphabeticKeyboard: KeyboardType = KeyboardType.GURMUKHI; private var isGurbaniPlayerPlaying = false; private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob()); private var keyboardInputContainer: FrameLayout? = null; private var nanakshahiCalendarPanel: NanakshahiCalendarPanel? = null; private var nanakshahiCalendarPanelContainer: View? = null; private var nitnemPanelContainer: View? = null; private var calculatorPanel: CalculatorPanel? = null; private var calculatorPanelContainer: View? = null; private var translationPanelContainer: View? = null; private var emojiPanelContainer: View? = null; private var clipboardPanelLayout: View? = null; private var voiceInputPopupContainer: View? = null; private var expandedTopRowContainer: View? = null; private var suggestionPanelRoot: View? = null; private var candidateView: @Suppress("REDECLARATION") CandidateView? = null; private var mainKeyboardLayout: View? = null; private var fixedTopRowButtons: LinearLayout? = null; private var navigationBarSpacer: View? = null; internal lateinit var voiceInputManager: VoiceInputManager; internal var translationInput: EditText? = null; internal lateinit var sharedPreferences: SharedPreferences; internal lateinit var keyboardManager: KeyboardManager; internal lateinit var gurmukhiInputHandler: GurmukhiInputHandler; internal lateinit var keyboardActionListener: MyKeyboardActionListener; internal lateinit var translationManager: TranslationManager; internal lateinit var databaseHelper: DatabaseHelper; private var predictionEngine: PredictionEngine? = null; private var tts: TextToSpeech? = null; private var clipboardAdapter: ClipboardAdapter? = null; private var clipboardManager: ClipboardManager? = null; private var speechRate: Float = 0.5f; internal var currentSuggestions: List<String> = emptyList(); internal var currentCorrectionIndex: Int = -1

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ImeConstants.ACTION_SETTINGS_CHANGED) {
                setInputView(onCreateInputView())
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
        PunjabiNextWordLM.init(this)
        serviceScope.launch { withContext(Dispatchers.IO) { DictionaryHelper.convertParagraphToDictionary(applicationContext); predictionEngine = PredictionEngine(applicationContext) } }
        gurmukhiInputHandler = GurmukhiInputHandler { word, _ -> updateSuggestions(word) }
        voiceInputManager = VoiceInputManager(this, this)
        translationManager = TranslationManager(this)
        tts = TextToSpeech(this, this)
        EmojiCompat.init(this)

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)

        // Using ContextCompat safely clears the red underlines in Android Studio and supports Android 14+
        val settingsFilter = IntentFilter(ImeConstants.ACTION_SETTINGS_CHANGED)
        ContextCompat.registerReceiver(this, settingsReceiver, settingsFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        val calcFilter = IntentFilter("com.iqbal.gurmukhikeyboard50.CALC_UPDATE")
        ContextCompat.registerReceiver(this, calcReceiver, calcFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    private val clipboardListener =
        ClipboardManager.OnPrimaryClipChangedListener {

        }
    private val calcReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.iqbal.gurmukhikeyboard50.CALC_UPDATE") {
                calculatorPanel?.handleExternalUpdate(intent)
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        try {
            unregisterReceiver(settingsReceiver)
            unregisterReceiver(calcReceiver)
        } catch (e: Exception) {
            Log.e("MyKeyboardIME", "Error during unregister", e)
        }
        voiceInputManager.stopVoiceRecognition()
        translationManager.close()
        tts?.stop()
        tts?.shutdown()
        serviceScope.cancel()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean { if (keyCode == KeyEvent.KEYCODE_BACK && currentPanel != ImeConstants.PANEL_KEYBOARD) { switchPanel(ImeConstants.PANEL_KEYBOARD, animate = true); return true }; return super.onKeyDown(keyCode, event) }

    override fun onCreateInputView(): View {
        return try {
            val themeValue = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light") ?: "light"
            val themeResId = keyboardManager.getThemeResIdForValue(themeValue)
            val themedContext: Context = ContextThemeWrapper(this, themeResId)
            val totalWidth = resources.displayMetrics.widthPixels
            keyboardManager.loadAllKeyboards(themedContext, totalWidth)

            val root = LayoutInflater.from(themedContext).inflate(R.layout.input_view, null) as FrameLayout
            keyboardInputContainer = root

            setupInputViewElements(root, themedContext)

            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                navigationBarSpacer?.let { spacer ->
                    val params = spacer.layoutParams
                    if (params.height != navInsets.bottom) {
                        params.height = navInsets.bottom
                        spacer.layoutParams = params
                    }
                }
                insets
            }

            root
        } catch (e: Exception) {
            Log.e("MyKeyboardIME", "Failed to inflate input view", e)
            val fallback = FrameLayout(this)
            fallback.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
            fallback
        }
    }

    private fun setupInputViewElements(root: FrameLayout, themedContext: Context) {
        mainKeyboardLayout = root.findViewById(R.id.mainKeyboardLayout)
        nanakshahiCalendarPanelContainer = root.findViewById(R.id.nanakshahi_calendar_panel_container)
        nitnemPanelContainer = root.findViewById(R.id.nitnem_panel_container)
        calculatorPanelContainer = root.findViewById(R.id.calculator_panel_container)
        translationPanelContainer = root.findViewById(R.id.translation_panel_container)
        emojiPanelContainer = root.findViewById(R.id.emoji_panel_container)
        clipboardPanelLayout = root.findViewById(R.id.clipboard_panel_layout)
        voiceInputPopupContainer = root.findViewById(R.id.voice_input_popup_container)
        expandedTopRowContainer = root.findViewById(R.id.expanded_top_row_container)
        fixedTopRowButtons = root.findViewById(R.id.fixed_top_row_buttons)
        suggestionPanelRoot = root.findViewById(R.id.suggestion_panel_root)
        candidateView = suggestionPanelRoot?.findViewById(R.id.candidate_view)
        kv = root.findViewById(R.id.keyboardView)
        navigationBarSpacer = root.findViewById(R.id.navigation_bar_spacer)

        currentPanel = ImeConstants.PANEL_KEYBOARD
        kv?.keyboard = keyboardManager.getCurrentKeyboard()
        updateKeyboardSettings()
        kv?.setService(this)
        candidateView?.setService(this)
        candidateView?.setOnSuggestionClickListener { handleSuggestionClick(it) }
        candidateView?.setOnSuggestionLongClickListener { handleSuggestionLongClick(it) }
        keyboardActionListener = MyKeyboardActionListener(this)
        kv?.setOnKeyboardActionListener(keyboardActionListener)
        customizeTopRowButtons(themedContext)

        nanakshahiCalendarPanel = NanakshahiCalendarPanel(themedContext, { switchPanel(ImeConstants.PANEL_KEYBOARD) }, { dateStr -> currentInputConnection?.commitText(dateStr, 1) })
        (nanakshahiCalendarPanelContainer as? FrameLayout)?.addView(nanakshahiCalendarPanel?.view)

        calculatorPanel = CalculatorPanel(themedContext) { switchPanel(ImeConstants.PANEL_KEYBOARD) }
        (calculatorPanelContainer as? FrameLayout)?.addView(calculatorPanel?.view)

        setupTranslationPanel(themedContext)
        setupEmojiPanel(themedContext)
        setupClipboardPanel(themedContext)
        setupExpandedTopRow(themedContext)
        setupNitnemPanel(themedContext)
        applyPanelVisibility(currentPanel, animate = false)
        applyOneHandedMode()
        applyCustomBackground()
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (outInsets != null) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
        }
    }

    private fun handleSuggestionLongClick(suggestion: String) { if (suggestion.isEmpty() || suggestion == "↶ Undo" || suggestion == "↷ Redo") return; serviceScope.launch { withContext(Dispatchers.IO) { databaseHelper.deleteWord(suggestion); DictionaryHelper.deleteWordFromDictionary(applicationContext, suggestion); predictionEngine = PredictionEngine(applicationContext) }; withContext(Dispatchers.Main) { Toast.makeText(this@MyKeyboardIME, "ਸ਼ਬਦ ਮਿਟਾ ਦਿੱਤਾ ਗਿਆ: $suggestion", Toast.LENGTH_SHORT).show(); val currentWord = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString(); updateSuggestions(currentWord) } } }
    private fun applyCustomBackground() { val theme = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light"); val imagePath = sharedPreferences.getString(ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE, null); val dimAmount = sharedPreferences.getInt(ImeConstants.PREF_BACKGROUND_DIM_AMOUNT, 50); if (theme == "custom" && !imagePath.isNullOrEmpty()) { try { val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(imagePath, options); val reqWidth = resources.displayMetrics.widthPixels; val reqHeight = resources.displayMetrics.heightPixels / 2; options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight); options.inJustDecodeBounds = false; val bitmap = BitmapFactory.decodeFile(imagePath, options); if (bitmap != null) { val drawable = BitmapDrawable(resources, bitmap); val dimColor = android.graphics.Color.argb((dimAmount * 2.55).toInt(), 0, 0, 0); drawable.setColorFilter(dimColor, android.graphics.PorterDuff.Mode.SRC_ATOP); mainKeyboardLayout?.background = drawable; suggestionPanelRoot?.setBackgroundColor(android.graphics.Color.TRANSPARENT); fixedTopRowButtons?.setBackgroundColor(android.graphics.Color.TRANSPARENT) } } catch (e: Exception) { Log.e("MyKeyboardIME", "Error applying background", e) } } else { mainKeyboardLayout?.background = null } }
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int { val (height: Int, width: Int) = options.outHeight to options.outWidth; var inSampleSize = 1; if (height > reqHeight || width > reqWidth) { val halfHeight: Int = height / 2; val halfWidth: Int = width / 2; while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) { inSampleSize *= 2 } }; return inSampleSize }
    private fun handleSuggestionClick(suggestion: String) {
        val ic = currentInputConnection ?: return
        val isTranslationMode = currentPanel == ImeConstants.PANEL_TRANSLATION

        when (suggestion) {
            "↶ Undo" -> {
                if (!isTranslationMode) {
                    val now = SystemClock.uptimeMillis()
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
                }
                return
            }
            "↷ Redo" -> {
                if (!isTranslationMode) {
                    val now = SystemClock.uptimeMillis()
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON))
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON))
                }
                return
            }
            "💬 WhatsApp" -> {
                val typedWord = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString()
                if (typedWord.isNotEmpty()) WhatsAppHelper.openWhatsAppChat(this, typedWord)
                return
            }
        }

        if (isTranslationMode) {
            val currentText = translationInput?.text?.toString() ?: ""
            val typedWord = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString()

            val newText = StringBuilder(currentText)
            if (typedWord.isNotEmpty() && currentText.endsWith(typedWord)) {
                newText.delete(currentText.length - typedWord.length, currentText.length)
            }
            newText.append(suggestion).append(" ")

            translationInput?.setText(newText.toString())
            translationInput?.setSelection(newText.length)
        } else {
            ic.beginBatchEdit()
            ic.commitText("$suggestion ", 1)
            ic.endBatchEdit()
        }

        if (currentInputEditorInfo?.packageName == "com.whatsapp") saveToClipboard(suggestion)

        if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.reset() else keyboardActionListener.englishWordBuffer.setLength(0)
        updateSuggestions("")

        if (suggestion.isNotEmpty() && suggestion != "💬 WhatsApp" && !suggestion.startsWith("📅")) {
            serviceScope.launch(Dispatchers.IO) { databaseHelper.addWord(suggestion.trim()) }
        }
    }

    internal fun updateSuggestions(word: String) {
        serviceScope.launch {
            val suggestions = withContext(Dispatchers.IO) {
                predictionEngine?.getSuggestions(word)?.toMutableList() ?: mutableListOf()
            }
            if (WhatsAppHelper.isPhoneNumber(word)) suggestions.add(0, "💬 WhatsApp")

            val contextText = if (currentPanel == ImeConstants.PANEL_TRANSLATION) translationInput?.text?.toString() ?: "" else currentInputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: ""
            val lastPart = if (word.isNotEmpty()) word else contextText.trim().split(" ").lastOrNull() ?: ""

            ShortcutsManager.getShortcut(this@MyKeyboardIME, lastPart)?.let { if (!suggestions.contains(it)) suggestions.add(0, it) }
            CalculatorHelper.evaluate(lastPart)?.let { if (!suggestions.contains(it)) suggestions.add(0, it) }

            val compact = CalculatorHelper.getInternationalCompact(lastPart)
            if (compact != null && !suggestions.contains(compact)) suggestions.add(0, compact)

            EmojiSearchHelper.searchEmoji(word)?.let { if (!suggestions.contains(it)) suggestions.add(0, it) }

            val words = PunjabiNumberConverter.convertToWords(word, false)
            if (words.isNotEmpty() && words != " ਰੁਪਏ ਮਾਤਰ") suggestions.add(0, words)

            if (word.length >= 7) {
                val intlWords = PunjabiNumberConverter.convertToWords(word, true)
                if (intlWords.isNotEmpty() && !suggestions.contains(intlWords)) suggestions.add(0, intlWords)
            }

            val loweredWord = word.lowercase()
            if (loweredWord == "date" || loweredWord == "today" || word == "ਤਾਰੀਖ" || word == "ਅੱਜ") {
                val now = Calendar.getInstance(NanakshahiCalendar.currentTimeZone)
                val dateStr = "📅 " + NanakshahiCalendar.getShortNanakshahiDate(this@MyKeyboardIME, now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR))
                suggestions.add(0, dateStr)
            }

            if (currentPanel == ImeConstants.PANEL_TRANSLATION) {
                val translationOutput = translationPanelContainer?.findViewById<TextView>(R.id.translation_output_text)?.text?.toString()
                if (!translationOutput.isNullOrEmpty() && translationOutput != "Translating..." && !translationOutput.startsWith("Error")) {
                    if (!suggestions.contains(translationOutput)) suggestions.add(0, translationOutput)
                }
            }

            // LM Prediction logic
            var correctionIndex = -1
            val finalSuggestions = if (word.isEmpty()) {
                contextText.trim().split(" ").lastOrNull()?.let { lastWord ->
                    PunjabiNextWordLM.predict(lastWord).takeIf { it.isNotEmpty() }?.let { (it + suggestions).distinct().toMutableList() }
                } ?: suggestions
            } else {
                val lmPreds = PunjabiNextWordLM.predict(word)
                // Prioritize dictionary completions over next-word predictions while typing
                val combined = (suggestions + lmPreds).distinct().toMutableList()

                // Ensure the typed word itself is always the first suggestion
                combined.remove(word)
                combined.add(0, word)

                // If the typed word is not in dictionary, highlight the next best suggestion as correction
                val isCorrect = withContext(Dispatchers.IO) { predictionEngine?.isWordInDictionary(word) == true }
                if (!isCorrect && combined.size > 1) {
                    correctionIndex = 1
                }

                combined
            }

            currentSuggestions = finalSuggestions
            currentCorrectionIndex = correctionIndex

            withContext(Dispatchers.Main) {
                candidateView?.setSuggestions(finalSuggestions, correctionIndex)
                // Dynamically show suggestions only when active typing occurs or next-word is ready
                if (word.isNotEmpty() || (finalSuggestions.isNotEmpty() && currentPanel != ImeConstants.PANEL_TRANSLATION)) {
                    suggestionPanelRoot?.visibility = View.VISIBLE
                    fixedTopRowButtons?.visibility = View.GONE
                } else {
                    suggestionPanelRoot?.visibility = View.GONE
                    fixedTopRowButtons?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun customizeTopRowButtons(context: Context) {
        fixedTopRowButtons?.removeAllViews()
        val pinnedSet = sharedPreferences.getStringSet(ImeConstants.PREF_TOOLBAR_PINNED_ITEMS, null)
        val buttonOrder = if (pinnedSet == null || pinnedSet.isEmpty()) {
            listOf(
                ImeConstants.KEYCODE_EMOJI,
                ImeConstants.KEYCODE_VOICE_INPUT,
                ImeConstants.KEYCODE_LANGUAGE_SWITCH,
                ImeConstants.KEYCODE_TRANSLATE,
                ImeConstants.KEYCODE_EXPANDED_MENU
            )
        } else {
            pinnedSet.map { mapPreferenceValueToKeyCode(it) }.filter { it != 0 && it != ImeConstants.KEYCODE_SETTINGS && it != ImeConstants.KEYCODE_CALCULATOR && it != ImeConstants.KEYCODE_NITNEM && it != ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL }.toMutableList()
        }
        val tv = TypedValue()
        context.theme.resolveAttribute(R.attr.iconColor, tv, true)
        val iconColor = if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) tv.data else android.graphics.Color.BLACK
        val inflater = LayoutInflater.from(context)
        val buttonHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics).toInt()
        
        for (keycode in buttonOrder) {
            val config = getButtonConfig(keycode)
            if (config.first == 0) continue
            val button = inflater.inflate(R.layout.top_row_button, fixedTopRowButtons, false) as ImageButton
            val params = button.layoutParams as LinearLayout.LayoutParams
            params.height = buttonHeight
            button.layoutParams = params
            button.setImageResource(config.first)
            button.imageTintList = ColorStateList.valueOf(iconColor)
            button.setOnClickListener { config.second() }
            fixedTopRowButtons?.addView(button)
        }
        
        // Add fixed Settings button on the far right
        val settingsConfig = getButtonConfig(ImeConstants.KEYCODE_SETTINGS)
        val settingsButton = inflater.inflate(R.layout.top_row_button, fixedTopRowButtons, false) as ImageButton
        val settingsParams = settingsButton.layoutParams as LinearLayout.LayoutParams
        settingsParams.height = buttonHeight
        settingsButton.layoutParams = settingsParams
        settingsButton.setImageResource(settingsConfig.first)
        settingsButton.imageTintList = ColorStateList.valueOf(iconColor)
        settingsButton.setOnClickListener { settingsConfig.second() }
        fixedTopRowButtons?.addView(settingsButton)
    }
    private fun mapPreferenceValueToKeyCode(value: String): Int = when (value) { "translate" -> ImeConstants.KEYCODE_TRANSLATE; "emoji" -> ImeConstants.KEYCODE_EMOJI; "mic" -> ImeConstants.KEYCODE_VOICE_INPUT; "gurmukhi_search" -> ImeConstants.KEYCODE_GURBANI_SEARCH; "gurbani" -> ImeConstants.KEYCODE_GURBANI_PLAYER; "settings" -> ImeConstants.KEYCODE_SETTINGS; "calendar" -> ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL; "nitnem" -> ImeConstants.KEYCODE_NITNEM; "calculator" -> ImeConstants.KEYCODE_CALCULATOR; "language_switch" -> ImeConstants.KEYCODE_LANGUAGE_SWITCH; "expanded_menu" -> ImeConstants.KEYCODE_EXPANDED_MENU; else -> 0 }
    private fun getButtonConfig(keycode: Int): Pair<Int, () -> Unit> { return when (keycode) { ImeConstants.KEYCODE_EMOJI -> R.drawable.ic_emoji to { keyboardActionListener.onKey(ImeConstants.KEYCODE_EMOJI, null) }; ImeConstants.KEYCODE_GURBANI_PLAYER -> { val icon = if (isGurbaniPlayerPlaying) R.drawable.ic_pause else R.drawable.ic_play; icon to { toggleGurbaniPlayer() } }; ImeConstants.KEYCODE_VOICE_INPUT -> R.drawable.ic_mic to { keyboardActionListener.onKey(ImeConstants.KEYCODE_VOICE_INPUT, null) }; ImeConstants.KEYCODE_LANGUAGE_SWITCH -> R.drawable.ic_language to { keyboardActionListener.onKey(ImeConstants.KEYCODE_LANGUAGE_SWITCH, null) }; ImeConstants.KEYCODE_TRANSLATE -> R.drawable.ic_translate to { keyboardActionListener.onKey(ImeConstants.KEYCODE_TRANSLATE, null) }; ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL -> R.drawable.ic_calender to { keyboardActionListener.onKey(ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL, null) }; ImeConstants.KEYCODE_NITNEM -> R.drawable.ik_onkar_svg to { switchPanel(ImeConstants.PANEL_NITNEM) }; ImeConstants.KEYCODE_CALCULATOR -> R.drawable.ic_calculator to { switchPanel(ImeConstants.PANEL_CALCULATOR) }; ImeConstants.KEYCODE_EXPANDED_MENU -> R.drawable.outline_arrows_output_24 to { fixedTopRowButtons?.visibility = View.GONE; expandedTopRowContainer?.visibility = View.VISIBLE }; ImeConstants.KEYCODE_SETTINGS -> R.drawable.ic_settings to { keyboardActionListener.onKey(ImeConstants.KEYCODE_SETTINGS, null) }; else -> 0 to { } } }
    private fun setupNitnemPanel(context: Context) {
        nitnemPanelContainer?.let { panel ->
            if (nanakshahiCalendarPanel == null) {
                val themeValue = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light");
                val themeResId = keyboardManager.getThemeResIdForValue(themeValue);
                val themedContext: Context = ContextThemeWrapper(this, themeResId);
                nanakshahiCalendarPanel = NanakshahiCalendarPanel(themedContext, { switchPanel(ImeConstants.PANEL_KEYBOARD) }, { dateStr -> currentInputConnection?.commitText(dateStr, 1) })
            };
            nanakshahiCalendarPanel?.setupNitnemPanel(panel)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (intent?.hasExtra("recognized_text") == true) { val text = intent.getStringExtra("recognized_text") ?: ""; currentInputConnection?.commitText(text, 1) }; return super.onStartCommand(intent, flags, startId) }
    internal fun launchSettings() { val intent = Intent(this, KeyboardSettingsActivity::class.java); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent) }
    private fun launchAppSettings() { val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); intent.data = Uri.fromParts("package", packageName, null); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent) }
    internal fun learnWord(word: String) { if (word.length >= 2) serviceScope.launch(Dispatchers.IO) { DictionaryHelper.addWordToDictionary(applicationContext, word); databaseHelper.addWord(word); predictionEngine?.updateDictionary(word) } }
    internal fun saveToClipboard(text: String) { if (text.isBlank()) return; serviceScope.launch(Dispatchers.IO) { databaseHelper.addClipboardItem(text) } }
    override fun onFinishInput() {
        if (currentInputEditorInfo?.packageName == "com.whatsapp") {
            val word = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString()
            if (word.isNotEmpty()) saveToClipboard(word)
        }
        super.onFinishInput()
    }
    private fun setupEmojiPanel(context: Context) { val emojiRecyclerView = emojiPanelContainer?.findViewById<RecyclerView>(R.id.emoji_recycler_view); val emojiTabLayout = emojiPanelContainer?.findViewById<TabLayout>(R.id.emoji_category_tabs); val allEmojisWithHeaders = EmojiHelper.getAllEmojisWithHeaders(context); val emojiAdapter = EmojiAdapter(allEmojisWithHeaders, { onEmojiClicked(it) }, { it is String && EmojiData.categories.containsKey(it) }); val layoutManager = GridLayoutManager(context, 8); layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int): Int = if (emojiAdapter.isHeader(position)) 8 else 1 }; emojiRecyclerView?.layoutManager = layoutManager; emojiRecyclerView?.adapter = emojiAdapter; val categories = EmojiHelper.getEmojiCategories(); for (category in categories) emojiTabLayout?.addTab(emojiTabLayout.newTab().setText(category)); emojiTabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener { override fun onTabSelected(tab: TabLayout.Tab?) { val word = EmojiHelper.getPositionForCategory(context, tab?.text.toString()); (emojiRecyclerView?.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(word, 0) }; override fun onTabUnselected(tab: TabLayout.Tab?) {}; override fun onTabReselected(tab: TabLayout.Tab?) { } }); emojiRecyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() { override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { super.onScrolled(recyclerView, dx, dy); val pos = (recyclerView.layoutManager as GridLayoutManager).findFirstVisibleItemPosition(); val category = EmojiHelper.getCategoryForPosition(context, pos); val tabIndex = categories.indexOf(category); if (tabIndex != -1 && emojiTabLayout?.selectedTabPosition != tabIndex) emojiTabLayout?.getTabAt(tabIndex)?.select() } }); emojiPanelContainer?.findViewById<ImageButton>(R.id.emoji_panel_return_to_gurmukhi)?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }; emojiPanelContainer?.findViewById<ImageButton>(R.id.emoji_panel_backspace)?.setOnClickListener { keyboardActionListener.onKey(Keyboard.KEYCODE_DELETE, null) } }
    private fun onEmojiClicked(emoji: String) { if (currentPanel == ImeConstants.PANEL_TRANSLATION) { val text = translationInput?.text?.toString() ?: ""; val newText = text + emoji; translationInput?.setText(newText); translationInput?.setSelection(newText.length) } else { currentInputConnection?.commitText(emoji, 1) }; RecentEmojiManager.addEmoji(this, emoji) }
    private fun setupTranslationPanel(context: Context) { translationInput = translationPanelContainer?.findViewById(R.id.translation_input_text); val translationOutput = translationPanelContainer?.findViewById<TextView>(R.id.translation_output_text); val sourceLanguageSpinner = translationPanelContainer?.findViewById<Spinner>(R.id.source_language_spinner); val targetLanguageSpinner = translationPanelContainer?.findViewById<Spinner>(R.id.target_language_spinner); val swapLanguagesButton = translationPanelContainer?.findViewById<ImageButton>(R.id.swap_languages_button); val speakTranslationButton = translationPanelContainer?.findViewById<ImageButton>(R.id.speak_translation_button); val voiceTranslateButton = translationPanelContainer?.findViewById<ImageButton>(R.id.voice_translate_button); val closeButton = translationPanelContainer?.findViewById<ImageButton>(R.id.close_translation_panel_button); val speedSeekBar = translationPanelContainer?.findViewById<SeekBar>(R.id.voice_speed_seekbar); val languageNames = translationManager.availableLanguages.map { it.name }; val adapter = ArrayAdapter(context, R.layout.spinner_item, languageNames); adapter.setDropDownViewResource(R.layout.spinner_dropdown_item); sourceLanguageSpinner?.adapter = adapter; targetLanguageSpinner?.adapter = adapter; translationInput?.showSoftInputOnFocus = false; translationInput?.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) switchPanel(ImeConstants.PANEL_TRANSLATION, forceRedraw = true) }; translationInput?.setOnClickListener { switchPanel(ImeConstants.PANEL_TRANSLATION, forceRedraw = true) }; val onLanguageSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { when (parent?.id) { R.id.source_language_spinner -> translationManager.sourceLanguage = translationManager.availableLanguages[position]; R.id.target_language_spinner -> translationManager.targetLanguage = translationManager.availableLanguages[position] }; val inputText = translationInput?.text?.toString(); if (!inputText.isNullOrEmpty()) translationManager.translate(inputText, translationOutput) }; override fun onNothingSelected(parent: AdapterView<*>?) {} }; sourceLanguageSpinner?.onItemSelectedListener = onLanguageSelectedListener; targetLanguageSpinner?.onItemSelectedListener = onLanguageSelectedListener; swapLanguagesButton?.setOnClickListener { translationManager.swapLanguages(); updateSpinnerSelections() }; speedSeekBar?.progress = (speechRate * 100).toInt(); speedSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { speechRate = (progress / 100.0f).coerceIn(0.1f, 2.0f) }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} }); speakTranslationButton?.setOnClickListener { val textToSpeak = translationOutput?.text?.toString(); if (!textToSpeak.isNullOrEmpty() && textToSpeak != "Translating...") speak(textToSpeak, translationManager.targetLanguage.speechCode) else Toast.makeText(this, "ਪਹਿਲਾਂ ਟੈਕਸਟ ਲਿਖੋ", Toast.LENGTH_SHORT).show() }; voiceTranslateButton?.setOnClickListener { voiceInputManager.startVoiceRecognition(translationManager.sourceLanguage.speechCode) }; closeButton?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }; translationInput?.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}; override fun afterTextChanged(s: Editable?) { translationManager.translate(s.toString(), translationOutput); updateSuggestions("") } }); updateSpinnerSelections() }
    private fun updateSpinnerSelections() { val srcS = translationPanelContainer?.findViewById<Spinner>(R.id.source_language_spinner); val tgtS = translationPanelContainer?.findViewById<Spinner>(R.id.target_language_spinner); val srcP = translationManager.availableLanguages.indexOf(translationManager.sourceLanguage); val tgtP = translationManager.availableLanguages.indexOf(translationManager.targetLanguage); if (srcP != -1) srcS?.setSelection(srcP); if (tgtP != -1) tgtS?.setSelection(tgtP) }
    private fun setupClipboardPanel(context: Context) { val recyclerView = clipboardPanelLayout?.findViewById<RecyclerView>(R.id.clipboard_recycler_view); val searchEditText = clipboardPanelLayout?.findViewById<EditText>(R.id.clipboard_search_edit_text); val backButton = clipboardPanelLayout?.findViewById<ImageButton>(R.id.back_to_keyboard_button); val backspaceButton = clipboardPanelLayout?.findViewById<ImageButton>(R.id.clipboard_backspace_button); recyclerView?.layoutManager = LinearLayoutManager(context); clipboardAdapter = ClipboardAdapter(emptyList(), { if (currentPanel == ImeConstants.PANEL_TRANSLATION) { val text = translationInput?.text?.toString() ?: ""; val newText = text + it; translationInput?.setText(newText); translationInput?.setSelection(newText.length) } else { currentInputConnection?.commitText(it, 1) } }, { _ -> }, { serviceScope.launch { databaseHelper.deleteClipboardItems(listOf(it)); refreshClipboardHistory(searchEditText?.text?.toString()) } }, { id, pinned -> serviceScope.launch { databaseHelper.updateClipboardPinned(id, pinned); refreshClipboardHistory(searchEditText?.text?.toString()) } }); recyclerView?.adapter = clipboardAdapter; searchEditText?.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}; override fun afterTextChanged(s: Editable?) { refreshClipboardHistory(s.toString()) } }); backButton?.setOnClickListener { switchPanel(ImeConstants.PANEL_KEYBOARD) }; backspaceButton?.setOnClickListener { keyboardActionListener.onKey(Keyboard.KEYCODE_DELETE, null) } }
    private fun setupExpandedTopRow(context: Context) { val closeBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.close_expanded_row); val selectAllBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_select_all); val copyBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_copy); val pasteBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_paste) ; val leftBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_cursor_left); val rightBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_cursor_right); val fontIncreaseBtn = expandedTopRowContainer?.findViewById<Button>(R.id.btn_font_increase); val fontDecreaseBtn = expandedTopRowContainer?.findViewById<Button>(R.id.btn_font_decrease); val gurbaniSearchBtn = expandedTopRowContainer?.findViewById<ImageButton>(R.id.btn_gurbani_search_shortcut); closeBtn?.setOnClickListener { expandedTopRowContainer?.visibility = View.GONE; fixedTopRowButtons?.visibility = View.VISIBLE }; selectAllBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }; copyBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.copy) }; pasteBtn?.setOnClickListener { currentInputConnection?.performContextMenuAction(android.R.id.paste) }; leftBtn?.setOnClickListener { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)) }; rightBtn?.setOnClickListener { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)) }; fontIncreaseBtn?.setOnClickListener { adjustFontSize(5) }; fontDecreaseBtn?.setOnClickListener { adjustFontSize(-5) }; gurbaniSearchBtn?.setOnClickListener { openGurbaniSearchWebsite() } }
    private fun adjustFontSize(delta: Int) { val currentSize = sharedPreferences.getInt(ImeConstants.PREF_FONT_SIZE, 18); val newSize = (currentSize + delta).coerceIn(10, 30); sharedPreferences.edit().putInt(ImeConstants.PREF_FONT_SIZE, newSize).apply(); updateKeyboardSettings() }
    private fun applyOneHandedMode() { val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE; val mode = if (isLandscape) "off" else sharedPreferences.getString("pref_one_handed_mode", "off") ; val mainKeyboardLayout = mainKeyboardLayout ?: return; val spacerLeft = keyboardInputContainer?.findViewById<View>(R.id.one_handed_spacer_left); val spacerRight = keyboardInputContainer?.findViewById<View>(R.id.one_handed_spacer_right); when (mode) { "left" -> { mainKeyboardLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 85f); spacerLeft?.visibility = View.GONE; spacerRight?.apply { visibility = View.VISIBLE; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 15f) } }; "right" -> { mainKeyboardLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 85f); spacerRight?.visibility = View.GONE; spacerLeft?.apply { visibility = View.VISIBLE; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 15f) } }; else -> { mainKeyboardLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 100f); spacerLeft?.visibility = View.GONE; spacerRight?.visibility = View.GONE } }; mainKeyboardLayout.requestLayout() }
    private fun openGurbaniSearchWebsite() { var query = if (keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) gurmukhiInputHandler.getCurrentWord() else keyboardActionListener.englishWordBuffer.toString(); if (query.isEmpty() && currentInputConnection != null) { val textBefore = currentInputConnection?.getTextBeforeCursor(100, 0); if (!textBefore.isNullOrEmpty()) query = textBefore.toString().trim() }; val baseUrl = "https://gurbaninow.com"; val finalUrl = if (query.isNotEmpty()) { try { baseUrl + java.net.URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { "https://gurbaninow.com/" } } else "https://gurbaninow.com/"; val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ; startActivity(intent) }
    private fun toggleGurbaniPlayer() { isGurbaniPlayerPlaying = !isGurbaniPlayerPlaying; val intent = Intent(this, GurbaniPlayerService::class.java).apply { action = if (isGurbaniPlayerPlaying) GurbaniPlayerService.ACTION_PLAY else GurbaniPlayerService.ACTION_PAUSE }; startService(intent); val theme = sharedPreferences.getString(ImeConstants.PREF_KEYBOARD_THEME, "light") ?: "light"; customizeTopRowButtons(ContextThemeWrapper(this, keyboardManager.getThemeResIdForValue(theme))) }
    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) { if (key == ImeConstants.PREF_KEYBOARD_THEME || key == ImeConstants.PREF_KEY_HEIGHT || key == ImeConstants.PREF_KEY_GAP || key == "pref_one_handed_mode" || key == ImeConstants.PREF_TOOLBAR_PINNED_ITEMS || key == ImeConstants.PREF_USE_ROUNDED_KEYS || key == ImeConstants.PREF_KEY_ROUNDNESS || key == ImeConstants.PREF_USE_KEY_TRANSPARENCY || key == ImeConstants.PREF_KEY_OPACITY || key == ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE || key == ImeConstants.PREF_BACKGROUND_DIM_AMOUNT || key == "pref_keyboard_font") { setInputView(onCreateInputView()) } else if (key == ImeConstants.PREF_FONT_SIZE || key == "popup_on_keypress") { updateKeyboardSettings() } }
    private fun updateKeyboardSettings() { val fontSize = sharedPreferences.getInt(ImeConstants.PREF_FONT_SIZE, 18); kv?.setKeyTextSize(fontSize.toFloat() * resources.displayMetrics.scaledDensity) }
    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting);
        updateKeyboardSettings();
        updateEnterKey(editorInfo);
        val inputType = editorInfo?.inputType ?: 0;
        val variation = inputType and InputType.TYPE_MASK_VARIATION;
        val isTextPassword = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        val isNumberPassword = variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        val isNumber = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER;
        val isPhone = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: 0;
        val isSearchAction = action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_SEND;
        val isSearchVariation = variation == InputType.TYPE_TEXT_VARIATION_FILTER || variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        val isSearch = isSearchAction || isSearchVariation;

        val currentType = keyboardManager.currentKeyboardType

        if (isSearch) {
            handleKeyboardSwitch(KeyboardType.ENGLISH)
        } else if (isNumber || isNumberPassword || isPhone) {
            handleKeyboardSwitch(KeyboardType.NUMPAD)
        } else if (isTextPassword) {
            handleKeyboardSwitch(KeyboardType.ENGLISH)
        } else if (currentType == KeyboardType.NUMPAD) {
            handleKeyboardSwitch(KeyboardType.NUMPAD)
        } else if (!restarting) {
            handleKeyboardSwitch(lastAlphabeticKeyboard)
        } else {
            if (currentType == KeyboardType.NUMPAD || currentType == KeyboardType.SYMBOLS || currentType == KeyboardType.SYMBOLS1) {
                handleKeyboardSwitch(currentType)
            } else {
                handleKeyboardSwitch(lastAlphabeticKeyboard)
            }
        };

        fixedTopRowButtons?.visibility = View.VISIBLE;
        suggestionPanelRoot?.visibility = View.GONE;

        gurmukhiInputHandler.reset();
        if (::keyboardActionListener.isInitialized) keyboardActionListener.resetBuffers();
        refreshClipboardHistory();
        applyOneHandedMode();
        applyCustomBackground();
        currentPanel = ImeConstants.PANEL_KEYBOARD;
        applyPanelVisibility(currentPanel, animate = false);

        keyboardInputContainer?.let { ViewCompat.requestApplyInsets(it) }

        keyboardManager.updateEnglishShiftState(currentInputConnection, editorInfo)
        kv?.invalidateAllKeys()
    }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        if (newSelStart != oldSelStart || newSelEnd != oldSelEnd) {
            val ic = currentInputConnection
            if (ic != null && (newSelStart != candidatesEnd)) {
                ic.finishComposingText()
                if (::keyboardActionListener.isInitialized) {
                    keyboardActionListener.resetBuffers()
                }
                gurmukhiInputHandler.reset()
                updateSuggestions("")
            }
        }
    }
    private fun updateEnterKey(editorInfo: EditorInfo?) { val kb = kv?.keyboard as? MyKeyboard ?: return; val enterKey = kb.keys.find { it.codes.contains(Keyboard.KEYCODE_DONE) } as? MyKey ?: return; val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: 0; val isWhatsApp = editorInfo?.packageName == "com.whatsapp"; if (isWhatsApp) { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_return_arrow); enterKey.label = null } else { when (action) { EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEND -> { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_search); enterKey.label = null }; else -> { enterKey.icon = ContextCompat.getDrawable(this, R.drawable.ic_return_arrow); enterKey.label = null } } }; kv?.invalidateAllKeys() }
    internal fun switchPanel(panel: Int, forceRedraw: Boolean = false, animate: Boolean = true) {
        if (animate && currentPanel == panel && panel != ImeConstants.PANEL_KEYBOARD && !forceRedraw) {
            switchPanel(ImeConstants.PANEL_KEYBOARD, animate = true); return
        };
        if (!forceRedraw && currentPanel == panel) { applyPanelVisibility(panel, animate); return };

        if (currentPanel == ImeConstants.PANEL_NITNEM && panel != ImeConstants.PANEL_NITNEM) {
            nanakshahiCalendarPanel?.stopMusic()
        }

        currentPanel = panel;
        applyPanelVisibility(panel, animate);
        if (panel == ImeConstants.PANEL_TRANSLATION) { translationInput?.requestFocus(); translationInput?.setSelection(translationInput?.text?.length ?: 0) }; keyboardActionListener.resetBuffers(); if (forceRedraw) kv?.invalidateAllKeys() }



    override fun onWindowHidden() {
        super.onWindowHidden()
        nanakshahiCalendarPanel?.stopMusic()
    }

    private fun applyPanelVisibility(panel: Int, animate: Boolean = true) {
        val isCalendar = panel == ImeConstants.PANEL_NANAKSHAHI_CALENDAR
        val isNitnem = panel == ImeConstants.PANEL_NITNEM
        val isCalculator = panel == ImeConstants.PANEL_CALCULATOR
        val isEmoji = panel == ImeConstants.PANEL_EMOJI
        val isClipboard = panel == ImeConstants.PANEL_CLIPBOARD
        val isTranslation = panel == ImeConstants.PANEL_TRANSLATION

        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 200 }

        val targetKeyboardVisibility = if (isCalendar || isNitnem || isCalculator || isEmoji || isClipboard) View.INVISIBLE else View.VISIBLE
        if (mainKeyboardLayout?.visibility != targetKeyboardVisibility) {
            mainKeyboardLayout?.visibility = targetKeyboardVisibility
        }

        val panels = listOf(
            nanakshahiCalendarPanelContainer to isCalendar,
            nitnemPanelContainer to isNitnem,
            calculatorPanelContainer to isCalculator,
            translationPanelContainer to isTranslation,
            emojiPanelContainer to isEmoji,
            clipboardPanelLayout to isClipboard
        )

        panels.forEach { (view, shouldShow) ->
            if (shouldShow) {
                if (view?.visibility != View.VISIBLE) {
                    view?.visibility = View.VISIBLE
                    if (animate) view?.startAnimation(fadeIn)
                }
            } else {
                if (view?.visibility != View.GONE) {
                    view?.clearAnimation()
                    view?.visibility = View.GONE
                }
            }
        }
    }
    internal fun handleKeyboardSwitch(newType: KeyboardType) { if (newType == KeyboardType.GURMUKHI || newType == KeyboardType.ENGLISH) { lastAlphabeticKeyboard = newType }; val kb = keyboardManager.switchKeyboard(newType); if (kb != null) { kv?.keyboard = kb; updateKeyboardSettings(); kv?.invalidateAllKeys() }; candidateView?.loadCustomFont() }
    override fun onTextRecognized(text: String, isFinal: Boolean) { 
        if (currentPanel == ImeConstants.PANEL_TRANSLATION) { 
            if (isFinal) translationPanelContainer?.findViewById<EditText>(R.id.translation_input_text)?.setText(text) 
        } else { 
            if (isFinal) { 
                currentInputConnection?.commitText(text + " ", 1) 
                if (currentInputEditorInfo?.packageName == "com.whatsapp") saveToClipboard(text)
            } else { 
                currentInputConnection?.setComposingText(text, 1) 
            } 
        } 
    }
    override fun onListeningError(errorMessage: String) { if (errorMessage.contains("Missing RECORD_AUDIO permission")) launchAppSettings(); voiceInputPopupContainer?.visibility = View.GONE }
    override fun onReadyForSpeech() { voiceInputPopupContainer?.visibility = View.VISIBLE }
    override fun onEndOfSpeech() { voiceInputPopupContainer?.visibility = View.GONE }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) Log.d("MyKeyboardIME", "TTS Initialized successfully") else Log.e("MyKeyboardIME", "TTS Initialization failed with status: $status") }
    private fun speak(text: String, language: String) = tts?.run { val locale = if (language.contains("-")) { val parts = language.split("-"); if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0]) } else { Locale(language) }; this.language = locale; setSpeechRate(speechRate); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { voices?.takeIf { it.isNotEmpty() }?.let { v -> (v.find { it.name.lowercase().let { n -> n.contains("female") || n.contains("woman") } && it.locale.language == locale.language } ?: v.find { it.locale.language == locale.language })?.let { voice = it } } }; speak(text, TextToSpeech.QUEUE_FLUSH, null, "") }
    private fun refreshClipboardHistory(query: String? = null) = serviceScope.launch { databaseHelper.getClipboardHistory(query, 50).let { items -> withContext(Dispatchers.Main) { clipboardAdapter?.updateItems(items) } } }
    fun handleGestureSequence(sequence: List<String>) { 
        if (sequence.isEmpty()) return; 
        val isGestureEnabled = sharedPreferences.getBoolean("pref_gesture_typing", true); 
        if (!isGestureEnabled) return; 

        val literalText = sequence.joinToString("")
        val ic = currentInputConnection ?: return
        
        ic.beginBatchEdit()
        ic.commitText(literalText, 1)
        ic.endBatchEdit()
        updateSuggestions("")

        serviceScope.launch { 
            val suggestions = predictionEngine?.getGestureSuggestions(sequence) ?: emptyList(); 
            if (suggestions.isNotEmpty()) {
                withContext(Dispatchers.Main) { 
                    candidateView?.setSuggestions(suggestions) 
                }
            }
        } 
    }
    override fun onRmsChanged(rmsdB: Float) {
        val voiceMicIcon = voiceInputPopupContainer?.findViewById<ImageView>(R.id.voice_popup_mic_icon)
        val scale = 1.0f + (rmsdB.coerceIn(0f, 10f) / 10f)
        voiceMicIcon?.scaleX = scale
        voiceMicIcon?.scaleY = scale
    }
}
