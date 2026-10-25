package com.iqbal.gurmukhikeyboard50

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Intent
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.*
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var recyclerView: ZoomableRecyclerView
    private lateinit var pageIndicator: TextView
    private lateinit var btnSearch: ImageButton
    private lateinit var btnNightMode: ImageButton
    private lateinit var btnBookmark: ImageButton
    private lateinit var btnAddShortcut: ImageButton
    private lateinit var btnPrevPage: ImageButton
    private lateinit var btnNextPage: ImageButton
    private lateinit var controlsLayout: LinearLayout
    private lateinit var toolbar: Toolbar
    private lateinit var seekBar: SeekBar

    // Search Bar UI Elements
    private lateinit var searchBarLayout: LinearLayout
    private lateinit var searchResultsBar: LinearLayout
    private lateinit var etSearchInput: EditText
    private lateinit var btnExecuteSearch: ImageButton
    private lateinit var btnCloseSearch: ImageButton
    private lateinit var tvSearchStatus: TextView
    private lateinit var btnPrevSearchMatch: ImageButton
    private lateinit var btnNextSearchMatch: ImageButton

    private lateinit var renderer: PdfRenderer
    private lateinit var fileDescriptor: ParcelFileDescriptor
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var filePath: String = ""
    private var isNightMode: Boolean = false

    // Search state & Word Highlight
    private val searchMatchingPages = mutableListOf<Int>()
    private var currentSearchMatchIndex = -1
    private var activeSearchQuery: String = ""
    private var liveSearchJob: Job? = null

    private val invertColorFilter by lazy {
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1.0f,  0.0f,  0.0f, 0.0f, 255.0f,
                     0.0f, -1.0f,  0.0f, 0.0f, 255.0f,
                     0.0f,  0.0f, -1.0f, 0.0f, 255.0f,
                     0.0f,  0.0f,  0.0f, 1.0f,   0.0f
                )
            )
        )
    }

    private val bitmapCache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: Int, old: Bitmap, new: Bitmap?) {
            if (evicted) old.recycle()
        }
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val renderDispatcher = newSingleThreadContext("PdfRenderThread")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        val intentData: Uri? = intent.data
        filePath = intent.getStringExtra("pdf_path") ?: intentData?.toString() ?: ""
        val fileName = intent.getStringExtra("pdf_name") ?: "PDF Viewer"

        toolbar = findViewById(R.id.pdf_toolbar)
        setupToolbar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = fileName
        toolbar.setNavigationOnClickListener { finish() }

        if (!initPdfRenderer()) {
            Toast.makeText(this, "PDF ਫਾਈਲ ਨਹੀਂ ਖੁੱਲ੍ਹ ਸਕੀ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        rootLayout = findViewById(R.id.pdf_root_layout)
        recyclerView = findViewById(R.id.pdf_recycler_view)
        pageIndicator = findViewById(R.id.pdf_page_indicator)
        btnSearch = findViewById(R.id.btn_search_page)
        btnNightMode = findViewById(R.id.btn_night_mode)
        btnBookmark = findViewById(R.id.btn_bookmark)
        btnPrevPage = findViewById(R.id.btn_prev_page)
        btnNextPage = findViewById(R.id.btn_next_page)
        controlsLayout = findViewById(R.id.pdf_controls_layout)
        seekBar = findViewById(R.id.pdf_seekbar)

        // Search Bar Bindings
        searchBarLayout = findViewById(R.id.pdf_search_bar_layout)
        searchResultsBar = findViewById(R.id.pdf_search_results_bar)
        etSearchInput = findViewById(R.id.et_search_input)
        btnExecuteSearch = findViewById(R.id.btn_execute_search)
        btnCloseSearch = findViewById(R.id.btn_close_search)
        tvSearchStatus = findViewById(R.id.tv_search_status)
        btnPrevSearchMatch = findViewById(R.id.btn_prev_search_match)
        btnNextSearchMatch = findViewById(R.id.btn_next_search_match)

        btnSearch.setOnClickListener { toggleSearchBar() }
        pageIndicator.setOnClickListener { toggleSearchBar() }

        btnExecuteSearch.setOnClickListener { executeSearch() }
        btnCloseSearch.setOnClickListener { closeSearchBar() }

        etSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executeSearch()
                true
            } else false
        }

        etSearchInput.doOnTextChanged { text, _, _, _ ->
            liveSearchJob?.cancel()
            val query = text?.toString()?.trim().orEmpty()
            if (query.isEmpty()) {
                activeSearchQuery = ""
                searchMatchingPages.clear()
                currentSearchMatchIndex = -1
                searchResultsBar.isVisible = false
                bitmapCache.evictAll()
                recyclerView.adapter?.notifyDataSetChanged()
            } else {
                liveSearchJob = scope.launch {
                    delay(200)
                    executeSearch(isLiveSearch = true)
                }
            }
        }

        btnPrevSearchMatch.setOnClickListener {
            if (searchMatchingPages.isNotEmpty()) {
                currentSearchMatchIndex = (currentSearchMatchIndex - 1 + searchMatchingPages.size) % searchMatchingPages.size
                focusOnCurrentSearchMatch()
            }
        }

        btnNextSearchMatch.setOnClickListener {
            if (searchMatchingPages.isNotEmpty()) {
                currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchMatchingPages.size
                focusOnCurrentSearchMatch()
            }
        }

        btnNightMode.setOnClickListener { toggleNightMode() }
        btnBookmark.setOnClickListener { bookmarkCurrentPage() }
        btnAddShortcut = findViewById(R.id.btn_add_shortcut)
        btnAddShortcut.setOnClickListener { createHomeScreenShortcut() }

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        layoutManager.initialPrefetchItemCount = 2
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = PdfPageAdapter()
        recyclerView.setItemViewCacheSize(3)

        recyclerView.onSingleTapListener = {
            toggleControlsVisibility()
        }

        btnPrevPage.setOnClickListener {
            val current = layoutManager.findFirstVisibleItemPosition()
            if (current > 0) {
                recyclerView.scrollToPosition(current - 1)
            }
        }

        btnNextPage.setOnClickListener {
            val current = layoutManager.findFirstVisibleItemPosition()
            if (current < renderer.pageCount - 1) {
                recyclerView.scrollToPosition(current + 1)
            }
        }

        seekBar.max = renderer.pageCount - 1
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    recyclerView.scrollToPosition(progress)
                    updatePageIndicator(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        updatePageIndicator(0)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val position = layoutManager.findFirstVisibleItemPosition()
                if (position != RecyclerView.NO_POSITION) {
                    updatePageIndicator(position)
                    seekBar.progress = position
                    preRenderAdjacentPages(position)
                    saveProgress(position)
                }
            }
        })

        resumeLastReadPage()
    }

    private var pdfBoxDocument: PDDocument? = null
    private val pdfBoxLock = Any()

    private fun normalizeSearchText(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .lowercase(java.util.Locale.ROOT)

    private fun loadPdfBoxDocument(): Boolean {
        return try {
            PDFBoxResourceLoader.init(applicationContext)
            synchronized(pdfBoxLock) {
                if (pdfBoxDocument != null) return@synchronized true
                pageTextCache.clear()

                val document = if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
                    contentResolver.openInputStream(Uri.parse(filePath))?.use { input ->
                        PDDocument.load(input)
                    }
                } else {
                    val file = File(filePath)
                    if (file.exists()) PDDocument.load(file) else null
                }
                pdfBoxDocument = document
                document != null
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfViewer", "PDFBox load failed", e)
            false
        }
    }

    private fun initPdfRenderer(): Boolean {
        return try {
            val opened = if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
                val uri = Uri.parse(filePath)
                val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return false
                fileDescriptor = pfd
                renderer = PdfRenderer(fileDescriptor)
                true
            } else {
                val file = File(filePath)
                if (!file.exists()) return false
                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(fileDescriptor)
                true
            }
            opened
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun toggleControlsVisibility() {
        val show = !controlsLayout.isVisible
        controlsLayout.isVisible = show
        toolbar.isVisible = show
    }

    private fun toggleSearchBar() {
        if (searchBarLayout.isVisible) {
            closeSearchBar()
        } else {
            searchBarLayout.isVisible = true
            etSearchInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etSearchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearchBar() {
        searchBarLayout.isVisible = false
        searchResultsBar.isVisible = false
        activeSearchQuery = ""
        searchMatchingPages.clear()
        currentSearchMatchIndex = -1
        bitmapCache.evictAll()
        recyclerView.adapter?.notifyDataSetChanged()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etSearchInput.windowToken, 0)
    }

    private fun convertGurmukhiDigitsToAscii(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            when (ch) {
                '੦' -> sb.append('0')
                '੧' -> sb.append('1')
                '੨' -> sb.append('2')
                '੩' -> sb.append('3')
                '੪' -> sb.append('4')
                '੫' -> sb.append('5')
                '੬' -> sb.append('6')
                '੭' -> sb.append('7')
                '੮' -> sb.append('8')
                '੯' -> sb.append('9')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun convertAsciiDigitsToGurmukhi(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            when (ch) {
                '0' -> sb.append('੦')
                '1' -> sb.append('੧')
                '2' -> sb.append('੨')
                '3' -> sb.append('੩')
                '4' -> sb.append('੪')
                '5' -> sb.append('੫')
                '6' -> sb.append('੬')
                '7' -> sb.append('੭')
                '8' -> sb.append('੮')
                '9' -> sb.append('੯')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun executeSearch(isLiveSearch: Boolean = false) {
        val query = etSearchInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            if (!isLiveSearch) {
                Toast.makeText(this, "ਸਰਚ ਲਈ ਅੱਖਰ ਜਾਂ ਨੰਬਰ ਲਿਖੋ", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (!isLiveSearch) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(etSearchInput.windowToken, 0)
        }

        activeSearchQuery = query
        searchMatchingPages.clear()
        currentSearchMatchIndex = -1
        bitmapCache.evictAll()

        searchResultsBar.isVisible = true
        tvSearchStatus.text = "ਲੱਭ ਰਹੇ ਹਾਂ: '$query'..."
        btnPrevSearchMatch.isVisible = false
        btnNextSearchMatch.isVisible = false

        lifecycleScope.launch {
            val searchOutcome = withContext(Dispatchers.IO) {
                val loaded = pdfBoxDocument != null || loadPdfBoxDocument()
                if (loaded) {
                    Pair(true, searchPdfTextStreams(filePath, query, renderer.pageCount))
                } else {
                    Pair(false, emptyList<Int>())
                }
            }
            val foundPages = searchOutcome.second
            if (!searchOutcome.first) {
                tvSearchStatus.text = "PDF ਦਾ ਟੈਕਸਟ ਪੜ੍ਹਿਆ ਨਹੀਂ ਜਾ ਸਕਿਆ"
                if (!isLiveSearch) {
                    Toast.makeText(
                        this@PdfViewerActivity,
                        "ਇਸ PDF ਵਿੱਚ ਟੈਕਸਟ ਸਰਚ ਨਹੀਂ ਹੋ ਸਕੀ",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            searchMatchingPages.clear()
            searchMatchingPages.addAll(foundPages)

            if (searchMatchingPages.isNotEmpty()) {
                currentSearchMatchIndex = 0
                btnPrevSearchMatch.isVisible = true
                btnNextSearchMatch.isVisible = true
                focusOnCurrentSearchMatch()
                recyclerView.adapter?.notifyDataSetChanged()
                if (!isLiveSearch) {
                    Toast.makeText(
                        this@PdfViewerActivity,
                        "'$query' ਲੱਭਿਆ: ${searchMatchingPages.size} ਪੰਨਿਆਂ ਵਿੱਚ",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                tvSearchStatus.text = "ਕੋਈ ਨਤੀਜਾ ਨਹੀਂ ਮਿਲਿਆ ('$query')"
                btnPrevSearchMatch.isVisible = false
                btnNextSearchMatch.isVisible = false
                recyclerView.adapter?.notifyDataSetChanged()
                if (!isLiveSearch) {
                    Toast.makeText(this@PdfViewerActivity, "'$query' ਨਹੀਂ ਲੱਭਿਆ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun focusOnCurrentSearchMatch() {
        if (searchMatchingPages.isEmpty() || currentSearchMatchIndex !in searchMatchingPages.indices) return
        val page = searchMatchingPages[currentSearchMatchIndex]

        recyclerView.scrollToPosition(page)
        updatePageIndicator(page)
        seekBar.progress = page
        updateSearchMatchStatus()

        recyclerView.post {
            try {
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(page)
                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                val pageWidth = screenWidth * 1.2f
                val pageHeight = (pageWidth * (renderer.openPage(page).use { it.height.toFloat() / it.width.toFloat() }))

                val wordRects = searchPdfPage(
                    page,
                    activeSearchQuery,
                    pageWidth,
                    pageHeight
                )

                if (wordRects.isNotEmpty()) {
                    val rect = wordRects[0]
                    val itemTop = viewHolder?.itemView?.top?.toFloat() ?: 0f
                    val relativeX = rect.centerX()
                    val relativeY = itemTop + rect.centerY()

                    val currentScale = maxOf(recyclerView.getScaleFactor(), 1.8f)
                    recyclerView.focusOnPoint(relativeX, relativeY, currentScale)
                }
            } catch (e: Exception) {
                android.util.Log.e("PdfViewer", "Error focusing on search match: ${e.message}")
            }
        }
    }

    private fun updateSearchMatchStatus() {
        if (searchMatchingPages.isEmpty() || currentSearchMatchIndex !in searchMatchingPages.indices) return
        val currentMatchNum = currentSearchMatchIndex + 1
        val totalMatches = searchMatchingPages.size
        val pageNum = searchMatchingPages[currentSearchMatchIndex] + 1
        tvSearchStatus.text = "ਨਤੀਜਾ: $currentMatchNum / $totalMatches (ਪੰਨਾ $pageNum)"
        recyclerView.adapter?.notifyDataSetChanged()
    }

    private val pageTextCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    private suspend fun searchPdfTextStreams(
        path: String,
        query: String,
        totalPages: Int
    ): List<Int> = withContext(Dispatchers.IO) {
        val document = pdfBoxDocument ?: return@withContext emptyList()
        val targets = buildSearchTargets(query)
        if (targets.isEmpty()) return@withContext emptyList()

        val matches = LinkedHashSet<Int>()

        // 1. Instant in-memory search if document text is already cached
        if (pageTextCache.size >= minOf(totalPages, document.numberOfPages)) {
            for ((pageIndex, text) in pageTextCache) {
                if (targets.any { target -> text.contains(target) }) {
                    matches.add(pageIndex)
                }
            }
            return@withContext matches.sorted()
        }

        // 2. Single-pass ultra-fast PDFTextStripper over the document
        synchronized(pdfBoxLock) {
            try {
                val stripper = object : PDFTextStripper() {
                    init {
                        sortByPosition = true
                    }

                    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                        val pageIdx = currentPageNo - 1
                        if (pageIdx >= 0) {
                            val normalized = normalizeSearchText(text)
                            val existing = pageTextCache[pageIdx] ?: ""
                            val updated = if (existing.isEmpty()) normalized else "$existing $normalized"
                            pageTextCache[pageIdx] = updated

                            if (targets.any { target -> updated.contains(target) }) {
                                matches.add(pageIdx)
                            }
                        }
                        super.writeString(text, textPositions)
                    }
                }
                stripper.getText(document)
            } catch (e: Exception) {
                android.util.Log.e("PdfViewer", "Fast PDFTextStripper search failed", e)
            }
        }

        return@withContext matches.sorted()
    }

    private fun buildSearchTargets(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return linkedSetOf(
            normalizeSearchText(trimmed),
            normalizeSearchText(convertGurmukhiDigitsToAscii(trimmed)),
            normalizeSearchText(convertAsciiDigitsToGurmukhi(trimmed))
        ).filter { it.isNotEmpty() }
    }

    private fun toggleNightMode() {
        isNightMode = !isNightMode
        val bgColor = if (isNightMode) Color.parseColor("#121212") else Color.parseColor("#EEEEEE")
        rootLayout.setBackgroundColor(bgColor)
        recyclerView.setBackgroundColor(bgColor)
        btnNightMode.setColorFilter(if (isNightMode) Color.YELLOW else Color.WHITE)
        recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun bookmarkCurrentPage() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val current = layoutManager.findFirstVisibleItemPosition()
        if (current >= 0) {
            saveProgress(current)
            Toast.makeText(this, "ਪੰਨਾ ${current + 1} ਬੁੱਕਮਾਰਕ ਕੀਤਾ ਗਿਆ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createHomeScreenShortcut() {
        if (filePath.isEmpty()) {
            Toast.makeText(this, "ਫਾਈਲ ਦੀ ਜਾਣਕਾਰੀ ਨਹੀਂ ਮਿਲੀ", Toast.LENGTH_SHORT).show()
            return
        }

        val fileObj = File(filePath)
        val rawName = if (fileObj.exists()) fileObj.name else "PDF"
        val cleanName = rawName.replace(".pdf", "").replace("_", " ").trim()
        val shortcutId = "pdf_shortcut_${filePath.hashCode()}"

        val shortcutIntent = Intent(this, PdfViewerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("pdf_path", filePath)
            putExtra("pdf_name", cleanName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pinShortcutInfo = ShortcutInfoCompat.Builder(this, shortcutId)
            .setShortLabel(cleanName)
            .setLongLabel(cleanName)
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut))
            .setIntent(shortcutIntent)
            .build()

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinnedSuccessfully = ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
            if (pinnedSuccessfully) {
                Toast.makeText(this, "'$cleanName' ਹੋਮ ਸਕ੍ਰੀਨ 'ਤੇ ਸ਼ਾਰਟਕੱਟ ਬਣਾਇਆ ਗਿਆ", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ਹੋਮ ਸਕ੍ਰੀਨ 'ਤੇ ਸ਼ਾਰਟਕੱਟ ਬਣਾਉਣ ਦੀ ਬੇਨਤੀ ਭੇਜੀ ਗਈ", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "ਤੁਹਾਡੇ ਫੋਨ ਦੀ ਹੋਮ ਸਕ੍ਰੀਨ 'ਤੇ ਸ਼ਾਰਟਕੱਟ ਸਪੋਰਟ ਉਪਲਬਧ ਨਹੀਂ ਹੈ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeLastReadPage() {
        lifecycleScope.launch {
            val book = AppDatabase.getDatabase(this@PdfViewerActivity).bookDao().getBookByPath(filePath)
            book?.let {
                if (it.lastPage > 0 && it.lastPage < renderer.pageCount) {
                    recyclerView.scrollToPosition(it.lastPage)
                    updatePageIndicator(it.lastPage)
                    seekBar.progress = it.lastPage
                }
            }
        }
    }

    private var lastSavedPage = -1
    private fun saveProgress(page: Int) {
        if (page == lastSavedPage) return
        lastSavedPage = page

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@PdfViewerActivity)
            val existing = db.bookDao().getBookByPath(filePath)
            if (existing == null) {
                val fileName = File(filePath).name
                val name = fileName.replace(".pdf", "").replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                db.bookDao().insertBook(BookEntity(name = name, path = filePath, fileName = fileName, lastPage = page))
            } else {
                db.bookDao().updateLastPage(filePath, page)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun preRenderAdjacentPages(currentPosition: Int) {
        val next = currentPosition + 1
        val prev = currentPosition - 1

        scope.launch {
            if (next < renderer.pageCount && bitmapCache.get(next) == null) {
                withContext(renderDispatcher) { renderPage(next) }?.let { bitmapCache.put(next, it) }
            }
            if (prev >= 0 && bitmapCache.get(prev) == null) {
                withContext(renderDispatcher) { renderPage(prev) }?.let { bitmapCache.put(prev, it) }
            }
        }
    }

    private fun updatePageIndicator(position: Int) {
        val totalPages = renderer.pageCount
        val currentPage = position + 1
        pageIndicator.text = "ਪੰਨਾ: $currentPage / $totalPages"
    }

    private fun searchPdfPage(
        pageIndex: Int,
        query: String,
        bitmapWidth: Float,
        bitmapHeight: Float
    ): List<RectF> {
        val document = pdfBoxDocument ?: return emptyList()
        if (
            query.isBlank() ||
            pageIndex !in 0 until document.numberOfPages ||
            bitmapWidth <= 0f ||
            bitmapHeight <= 0f
        ) return emptyList()

        val targets = buildSearchTargets(query)
        if (targets.isEmpty()) return emptyList()

        val page = document.getPage(pageIndex)
        val cropBox = page.cropBox
        val pageWidth = cropBox.width
        val pageHeight = cropBox.height
        if (pageWidth <= 0f || pageHeight <= 0f) return emptyList()

        val results = mutableListOf<RectF>()
        synchronized(pdfBoxLock) {
            try {
                val stripper = object : PDFTextStripper() {
                    init {
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                        sortByPosition = true
                    }

                    override fun writeString(
                        text: String,
                        textPositions: MutableList<TextPosition>
                    ) {
                        if (text.isNotEmpty() && textPositions.isNotEmpty()) {
                            val normalizedText = normalizeSearchText(text)
                            if (
                                normalizedText.length == text.length &&
                                textPositions.size == text.length
                            ) {
                                for (target in targets) {
                                    var start = normalizedText.indexOf(target)
                                    while (start >= 0) {
                                        val end = start + target.length
                                        if (end <= textPositions.size) {
                                            val positions = textPositions.subList(start, end)
                                            val leftPdf = positions.minOf { it.xDirAdj }
                                            val rightPdf = positions.maxOf {
                                                it.xDirAdj + it.widthDirAdj
                                            }
                                            val topPdf = positions.minOf {
                                                it.yDirAdj - it.heightDir
                                            }
                                            val bottomPdf = positions.maxOf { it.yDirAdj }

                                            val left = leftPdf / pageWidth * bitmapWidth
                                            val right = rightPdf / pageWidth * bitmapWidth
                                            val top = topPdf / pageHeight * bitmapHeight
                                            val bottom = bottomPdf / pageHeight * bitmapHeight

                                            if (
                                                left.isFinite() && right.isFinite() &&
                                                top.isFinite() && bottom.isFinite() &&
                                                right > left && bottom > top
                                            ) {
                                                results.add(RectF(left, top, right, bottom))
                                            }
                                        }
                                        start = normalizedText.indexOf(target, start + 1)
                                    }
                                }
                            }
                        }
                        super.writeString(text, textPositions)
                    }
                }
                stripper.getText(document)
            } catch (e: Exception) {
                android.util.Log.e("PdfViewer", "PDFBox page search failed on page $pageIndex", e)
            }
        }

        return results.distinctBy {
            listOf(
                (it.left * 10).toInt(),
                (it.top * 10).toInt(),
                (it.right * 10).toInt(),
                (it.bottom * 10).toInt()
            )
        }
    }

    @Synchronized
    private fun renderPage(index: Int): Bitmap? {
        return try {
            val page = renderer.openPage(index)
            val screenWidth = resources.displayMetrics.widthPixels

            val width = (screenWidth * 1.2).toInt()
            val height = (width * page.height) / page.width

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // Draw exact word text highlighter marker ONLY over the searched word/number
            if (activeSearchQuery.isNotEmpty() && searchMatchingPages.contains(index)) {
                val canvas = Canvas(bitmap)

                val highlightMarkerPaint = Paint().apply {
                    color = Color.parseColor("#99FFEA00") // Translucent Yellow Text Highlighter
                    style = Paint.Style.FILL
                }

                val wordRects = searchPdfPage(
                    index,
                    activeSearchQuery,
                    width.toFloat(),
                    height.toFloat()
                )

                if (wordRects.isNotEmpty()) {
                    for (rect in wordRects) {
                        canvas.drawRect(rect, highlightMarkerPaint)
                    }
                }
            }

            bitmap
        } catch (e: Exception) {
            android.util.Log.e("PdfViewer", "Error rendering page $index: ${e.message}")
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        renderDispatcher.close()
        pageTextCache.clear()
        try {
            synchronized(pdfBoxLock) {
                pdfBoxDocument?.close()
                pdfBoxDocument = null
            }
            renderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {}
        bitmapCache.evictAll()
    }

    private fun setupToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
    }

    inner class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.job?.cancel()

            val pageBgColor = if (isNightMode) Color.parseColor("#121212") else Color.WHITE

            holder.containerView.setBackgroundColor(pageBgColor)
            holder.imageView.setBackgroundColor(pageBgColor)
            holder.imageView.colorFilter = if (isNightMode) invertColorFilter else null

            val cachedBitmap = bitmapCache.get(position)
            if (cachedBitmap != null) {
                holder.progressBar.isVisible = false
                holder.imageView.setImageBitmap(cachedBitmap)
            } else {
                holder.imageView.setImageBitmap(null)
                holder.progressBar.isVisible = true
                holder.job = scope.launch {
                    @OptIn(ExperimentalCoroutinesApi::class)
                    val bitmap = withContext(renderDispatcher) {
                        renderPage(position)
                    }
                    if (bitmap != null) {
                        bitmapCache.put(position, bitmap)
                        holder.imageView.setImageBitmap(bitmap)
                    }
                    holder.progressBar.isVisible = false
                }
            }
        }

        override fun getItemCount(): Int = renderer.pageCount

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val containerView: View = view.findViewById(R.id.pdf_page_container)
            val imageView: ImageView = view.findViewById(R.id.pdf_page_image)
            val progressBar: ProgressBar = view.findViewById(R.id.pdf_page_loader)
            var job: Job? = null
        }
    }
}
