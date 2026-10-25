@file:Suppress("SpellCheckingInspection")
package com.iqbal.gurmukhikeyboard50

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Xml
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.Locale
import java.util.zip.ZipInputStream

class ExcelViewerActivity : AppCompatActivity() {
    private var currentWorkbook: org.apache.poi.ss.usermodel.Workbook? = null

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: View
    private lateinit var sheetsTabLayout: LinearLayout
    private lateinit var sheetsTabScroll: View
    private lateinit var toolbar: Toolbar

    private lateinit var calcBar: LinearLayout
    private lateinit var tvCalcSummary: TextView
    private lateinit var btnDeleteRows: Button
    private lateinit var btnDeleteCols: Button
    private lateinit var btnShareSelectedRows: Button
    private lateinit var btnUndoRows: Button
    private lateinit var btnResetRows: Button
    private lateinit var btnClearFilter: Button

    private lateinit var chipSum: TextView
    private lateinit var chipMin: TextView
    private lateinit var chipMax: TextView
    private lateinit var chipAvg: TextView
    private lateinit var chipCount: TextView
    private lateinit var chipCountNum: TextView

    private lateinit var etFormula: EditText
    private lateinit var btnApplyFormula: Button
    private lateinit var btnFormulaPicker: ImageButton

    private lateinit var btnToolbarMoreMenu: View
    private lateinit var btnToggleTheme: TextView
    private var isDarkMode = true
    private lateinit var findReplaceBar: LinearLayout
    private lateinit var etFindText: EditText
    private lateinit var etReplaceText: EditText
    private lateinit var btnFindPrev: ImageButton
    private lateinit var btnFindNext: ImageButton
    private lateinit var btnCloseFindReplace: ImageButton
    private lateinit var btnReplaceOne: Button
    private lateinit var btnReplaceAll: Button

    private lateinit var topContainer: LinearLayout
    private var areTopBarsVisible = true

    private var currentUri: Uri? = null
    private var currentFileName = ""
    private var workbook: Workbook? = null
    private var activeSheetName = ""
    private var currentZoomScale: Float = 1.0f
    private val selectedRowIndices = mutableSetOf<Int>()
    private val selectedColIndices = mutableSetOf<Int>()
    private data class HistorySnapshot(
        val sheetsData: Map<String, List<List<String>>>,
        val formulasMap: Map<String, String>
    )

    private val poiHistoryStack = ArrayDeque<ByteArray>()
    private val poiRedoStack = ArrayDeque<ByteArray>()
    private val dataHistoryStack = ArrayDeque<HistorySnapshot>()
    private val dataRedoStack = ArrayDeque<HistorySnapshot>()
    private lateinit var btnRedoToolbar: ImageButton
    private val dataFormatter = DataFormatter(Locale.getDefault())
    private var formulaEvaluator: FormulaEvaluator? = null

    private var currentSheetsData = mutableMapOf<String, List<List<String>>>()
    private var originalSheetsData = mutableMapOf<String, List<List<String>>>()
    private val cellFormulasMap = mutableMapOf<String, String>()

    private var activeColumnFilterIndex: Int = -1
    private val activeColumnFilterSet = mutableSetOf<String>()
    private var currentMaxRowsToShow = 500

    private val hiddenColumnIndices = mutableSetOf<Int>()
    private val hiddenRowIndices = mutableSetOf<Int>()
    private val lockedDateColumnIndices = mutableSetOf<Int>()
    private val lockedDateColsMap = mutableMapOf<String, MutableSet<Int>>()

    private val frozenColsMap = mutableMapOf<String, Int>()
    private var frozenColumnCount: Int
        get() = frozenColsMap[activeSheetName] ?: 0
        set(value) { frozenColsMap[activeSheetName] = value }

    private val frozenRowsMap = mutableMapOf<String, Int>()
    private var frozenRowCount: Int
        get() = frozenRowsMap[activeSheetName] ?: 0
        set(value) { frozenRowsMap[activeSheetName] = value }

    private val sheetColWidthsMap = mutableMapOf<String, MutableMap<Int, Int>>()

    private fun saveColumnWidth(colIndex: Int, widthPx: Int) {
        val sheetMap = sheetColWidthsMap.getOrPut(activeSheetName) { mutableMapOf() }
        if (widthPx > 0) {
            sheetMap[colIndex] = widthPx
        } else {
            sheetMap.remove(colIndex)
        }
    }

    private var pendingScrollX: Int = 0
    private var pendingScrollY: Int = 0

    private fun detectAndLockDateColumns(sheetRows: List<List<String>>, sheetName: String = "") {
        val dateCols = if (sheetName.isNotEmpty()) {
            lockedDateColsMap.getOrPut(sheetName) { mutableSetOf() }
        } else {
            lockedDateColumnIndices
        }

        val headerRow = sheetRows.firstOrNull() ?: return
        for ((c, cellText) in headerRow.withIndex()) {
            if (isHeaderDate(cellText)) {
                dateCols.add(c)
                lockedDateColumnIndices.add(c)
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            currentUri = uri
            currentFileName = getFileNameFromUri(uri)
            supportActionBar?.title = currentFileName
            loadWorkbook(uri)
        }
    }

    private val saveXlsxDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) saveWorkbookToUri(uri) }

    private val saveXlsDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri -> if (uri != null) saveWorkbookToUri(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_excel_viewer)

        toolbar = findViewById(R.id.excel_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        topContainer = findViewById(R.id.excel_top_container)
        webView = findViewById(R.id.excel_web_view)
        progressBar = findViewById(R.id.excel_progress_bar)
        emptyView = findViewById(R.id.empty_excel_view)
        sheetsTabLayout = findViewById(R.id.sheets_tab_layout)
        sheetsTabScroll = findViewById(R.id.sheets_tab_scroll)

        calcBar = findViewById(R.id.excel_calc_bar)
        tvCalcSummary = findViewById(R.id.tv_calc_summary)
        btnDeleteRows = findViewById(R.id.btn_delete_selected_rows)
        btnDeleteCols = findViewById(R.id.btn_delete_selected_cols)
        btnShareSelectedRows = findViewById(R.id.btn_share_selected_rows)
        btnUndoRows = findViewById(R.id.btn_undo_rows)
        btnResetRows = findViewById(R.id.btn_reset_rows)
        btnClearFilter = findViewById(R.id.btn_clear_filter)

        chipSum = findViewById(R.id.chip_sum)
        chipMin = findViewById(R.id.chip_min)
        chipMax = findViewById(R.id.chip_max)
        chipAvg = findViewById(R.id.chip_avg)
        chipCount = findViewById(R.id.chip_count)
        chipCountNum = findViewById(R.id.chip_count_num)

        etFormula = findViewById(R.id.et_excel_formula)
        btnApplyFormula = findViewById(R.id.btn_apply_formula)
        btnFormulaPicker = findViewById(R.id.btn_formula_picker)

        btnToolbarMoreMenu = findViewById(R.id.btn_toolbar_more_menu)
        findReplaceBar = findViewById(R.id.excel_find_replace_bar)
        etFindText = findViewById(R.id.et_find_text)
        etReplaceText = findViewById(R.id.et_replace_text)
        btnFindPrev = findViewById(R.id.btn_find_prev)
        btnFindNext = findViewById(R.id.btn_find_next)
        btnCloseFindReplace = findViewById(R.id.btn_close_find_replace)
        btnReplaceOne = findViewById(R.id.btn_replace_one)
        btnReplaceAll = findViewById(R.id.btn_replace_all)

        btnRedoToolbar = findViewById(R.id.btn_redo_toolbar)
        btnRedoToolbar.setOnClickListener { redoNextAction() }

        btnToggleTheme = findViewById(R.id.btn_toggle_theme)
        val prefs = getSharedPreferences("excel_viewer_prefs", Context.MODE_PRIVATE)
        isDarkMode = prefs.getBoolean("is_dark_mode", true)
        btnToggleTheme.text = if (isDarkMode) "🌙" else "☀️"

        btnToggleTheme.setOnClickListener {
            isDarkMode = !isDarkMode
            prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply()
            btnToggleTheme.text = if (isDarkMode) "🌙" else "☀️"
            refreshActiveSheetView()
            Toast.makeText(this, if (isDarkMode) "🌙 Dark Mode" else "☀️ Light Mode", Toast.LENGTH_SHORT).show()
        }

        setupWebView()

        // Hide secondary bars by default so ONLY top toolbar is visible on screen
        findViewById<LinearLayout>(R.id.excel_formula_bar)?.isVisible = false
        calcBar.isVisible = false
        findReplaceBar.isVisible = false

        btnToolbarMoreMenu.setOnClickListener { showToolbarMoreOptionsMenu() }
        btnCloseFindReplace.setOnClickListener { findReplaceBar.isVisible = false }

        etFindText.doOnTextChanged { text, _, _, _ ->
            refreshActiveSheetViewWithSearch(text?.toString().orEmpty())
        }

        btnFindNext.setOnClickListener {
            webView.evaluateJavascript("if (typeof scrollToSearchMatch === 'function') scrollToSearchMatch();", null)
        }

        btnFindPrev.setOnClickListener {
            webView.evaluateJavascript("if (typeof scrollToSearchMatch === 'function') scrollToSearchMatch();", null)
        }

        btnReplaceOne.setOnClickListener { replaceOneMatchInActiveSheet() }
        btnReplaceAll.setOnClickListener { replaceAllMatchesInActiveSheet() }

        btnApplyFormula.setOnClickListener { applyFormulaFromBar() }
        btnFormulaPicker.setOnClickListener { showFormulaPickerDialog() }

        btnDeleteRows.setOnClickListener { confirmDeleteSelectedRows() }
        btnDeleteCols.setOnClickListener { confirmDeleteSelectedColumns() }
        btnShareSelectedRows.setOnClickListener { showCustomerRowDialog() }
        btnUndoRows.setOnClickListener { undoLastAction() }
        btnResetRows.setOnClickListener { resetWorkbookToLoadedState() }
        btnClearFilter.setOnClickListener { clearColumnFilter() }

        findViewById<ImageButton>(R.id.btn_open_excel).setOnClickListener { openExcelFilePicker() }
        findViewById<ImageButton>(R.id.btn_undo_toolbar)?.setOnClickListener { undoLastAction() }
        findViewById<ImageButton>(R.id.btn_save_excel).setOnClickListener { saveExcelFile() }

        findViewById<Button>(R.id.btn_empty_pick_file).setOnClickListener { openExcelFilePicker() }
        findViewById<Button>(R.id.btn_grant_storage_permission)?.setOnClickListener { requestAllFilesAccessPermission() }

        var uriToLoad: Uri? = null
        var nameToLoad = ""
        var sheetToRestore = ""

        if (savedInstanceState != null) {
            val sUri = savedInstanceState.getString("saved_uri").orEmpty()
            if (sUri.isNotEmpty()) uriToLoad = Uri.parse(sUri)
            nameToLoad = savedInstanceState.getString("saved_name").orEmpty()
            sheetToRestore = savedInstanceState.getString("saved_sheet").orEmpty()
        }

        if (uriToLoad == null) {
            val pathExtra = intent.getStringExtra("excel_path")
            val nameExtra = intent.getStringExtra("excel_name")
            val intentUri = intent.data
            uriToLoad = when {
                !pathExtra.isNullOrBlank() && (pathExtra.startsWith("content://") || pathExtra.startsWith("file://")) -> Uri.parse(pathExtra)
                !pathExtra.isNullOrBlank() -> Uri.fromFile(File(pathExtra))
                else -> intentUri
            }
            nameToLoad = nameExtra ?: ""
        }

        if (uriToLoad == null) {
            val prefs = getSharedPreferences("excel_viewer_prefs", Context.MODE_PRIVATE)
            val lastUriStr = prefs.getString("last_excel_uri", "").orEmpty()
            if (lastUriStr.isNotEmpty()) {
                uriToLoad = Uri.parse(lastUriStr)
                nameToLoad = prefs.getString("last_excel_name", "").orEmpty()
                sheetToRestore = prefs.getString("last_excel_sheet", "").orEmpty()
            }
        }

        if (uriToLoad != null) {
            val cleanName = if (nameToLoad.isNotBlank()) nameToLoad else getFileNameFromUri(uriToLoad)
            val localUri = if (uriToLoad.scheme == "content") {
                copyContentUriToEditableFile(uriToLoad, cleanName)
            } else {
                uriToLoad
            }
            currentUri = localUri
            currentFileName = cleanName
            supportActionBar?.title = currentFileName
            if (sheetToRestore.isNotBlank()) {
                activeSheetName = sheetToRestore
            }
            loadWorkbook(localUri)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = false
            allowFileAccess = true
            allowContentAccess = true
            domStorageEnabled = false
        }
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.isVisible = false
                view?.evaluateJavascript("if (typeof applySavedZoomScale === 'function') applySavedZoomScale($currentZoomScale); if (typeof evaluateAllFormulasOnPage === 'function') evaluateAllFormulasOnPage(); if (typeof initResizers === 'function') initResizers(); if (typeof scrollToSearchMatch === 'function') scrollToSearchMatch();", null)
                val px = pendingScrollX
                val py = pendingScrollY
                if (px > 0 || py > 0) {
                    view?.evaluateJavascript(
                        "(function(){var s=document.getElementById('excelScroll');if(s){s.scrollLeft=$px;s.scrollTop=$py;}})()",
                        null
                    )
                }
            }
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun updateFullStats(sum: Double, min: Double, max: Double, avg: Double, countAll: Int, countNums: Int, rowsCsv: String, colsCsv: String = "") {
            runOnUiThread {
                chipSum.text = "Sum : ${formatNumber(sum)}"
                chipMin.text = "Minimum : ${formatNumber(min)}"
                chipMax.text = "Maximum : ${formatNumber(max)}"
                chipAvg.text = "Average : ${formatNumber(avg)}"
                chipCount.text = "Count : $countAll"
                chipCountNum.text = "Count Numbers : $countNums"

                val hasHistory = poiHistoryStack.isNotEmpty() || dataHistoryStack.isNotEmpty()
                selectedRowIndices.clear()
                if (rowsCsv.isNotEmpty()) {
                    rowsCsv.split(',').mapNotNull { it.toIntOrNull() }.forEach { selectedRowIndices.add(it) }
                }

                selectedColIndices.clear()
                if (colsCsv.isNotEmpty()) {
                    colsCsv.split(',').mapNotNull { it.toIntOrNull() }.forEach { selectedColIndices.add(it) }
                }

                val hasRowSel = selectedRowIndices.isNotEmpty()
                val hasColSel = selectedColIndices.isNotEmpty()
                val hasHidden = hiddenColumnIndices.isNotEmpty() || hiddenRowIndices.isNotEmpty()

                if (hasHistory || activeColumnFilterIndex != -1 || hasRowSel || hasColSel || hasHidden) {
                    calcBar.isVisible = true
                } else calcBar.isVisible = false

                btnDeleteRows.isVisible = hasRowSel
                btnShareSelectedRows.isVisible = hasRowSel
                btnDeleteCols.isVisible = hasColSel

                if (hasRowSel) btnDeleteRows.text = "ਡਿਲੀਟ ਲਾਈਨਾਂ (${selectedRowIndices.size})"
                if (hasColSel) btnDeleteCols.text = "ਡਿਲੀਟ ਕੋਲਮ (${selectedColIndices.size})"
            }
        }

        @JavascriptInterface
        fun showSelectionActionDialog() {
            runOnUiThread {
                showSelectionShareAndSaveDialog()
            }
        }

        @JavascriptInterface
        fun saveJsColumnWidth(colIndex: Int, widthPx: Int) {
            runOnUiThread {
                saveColumnWidth(colIndex, widthPx)
            }
        }

        @JavascriptInterface
        fun saveZoomScale(scale: Float) {
            if (scale in 0.15f..3.0f) {
                currentZoomScale = scale
            }
        }

        @JavascriptInterface
        fun copyTextToClipboard(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ExcelData", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@ExcelViewerActivity, "ਕਾਪੀ ਹੋ ਗਿਆ (Copied)", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface

        fun shareSelectedMatrixAsImage(
            cellsDataStr: String,
            selectedRowsCsv: String
        ) {
            runOnUiThread {
                try {
                    val selectedCells = mutableListOf<Triple<Int, Int, String>>()

                    // JavaScript ਤੋਂ ਆਇਆ JSON ਪੜ੍ਹੋ
                    val jsonArray = org.json.JSONArray(cellsDataStr)

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue

                        val r = obj.optInt("r", -1)
                        val c = obj.optInt("c", -1)
                        val value = obj.optString("value", "")

                        if (r >= 0 && c >= 0) {
                            selectedCells.add(Triple(r, c, value))
                        }
                    }

                    if (selectedCells.isEmpty()) {
                        Toast.makeText(
                            this@ExcelViewerActivity,
                            "ਸ਼ੇਅਰ ਕਰਨ ਲਈ ਕੋਈ ਸੈੱਲ ਸਿਲੈਕਟ ਨਹੀਂ ਹਨ",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@runOnUiThread
                    }

                    // Duplicate coordinates ਹਟਾਓ
                    val uniqueCells = selectedCells.distinctBy {
                        Pair(it.first, it.second)
                    }

                    val selRows = uniqueCells.map { it.first }.distinct().sorted()
                    val selCols = uniqueCells.map { it.second }.distinct().sorted()
                    val cellMap = uniqueCells.associate { Pair(it.first, it.second) to it.third }

                    val sheet = activeSheet()
                    val headerRow = sheet?.getRow(0)
                    val rowsData = currentSheetsData[activeSheetName]

                    val headers = selCols.map { c ->
                        val hText = if (sheet != null) {
                            headerRow?.getCell(c)?.let { rawCellValue(it) }?.trim().orEmpty()
                        } else {
                            rowsData?.firstOrNull()?.getOrNull(c)?.stripStylePrefix()?.trim().orEmpty()
                        }
                        if (hText.isNotEmpty()) hText else getColumnLabel(c)
                    }

                    val rowDataList = selRows.map { r ->
                        val rowCells = selCols.mapIndexed { colIdx, c ->
                            val rawVal = if (cellMap.containsKey(Pair(r, c))) {
                                cellMap[Pair(r, c)].orEmpty()
                            } else if (sheet != null) {
                                sheet.getRow(r)?.getCell(c)?.let { rawCellValue(it) }?.trim().orEmpty()
                            } else {
                                rowsData?.getOrNull(r)?.getOrNull(c)?.stripStylePrefix()?.trim().orEmpty()
                            }
                            val hName = headers.getOrNull(colIdx)?.lowercase(Locale.ROOT).orEmpty()
                            val isDateCol = hName.contains("date") || hName == "dt" || hName.contains("dob") || hName.contains("expiry") || hName.contains("time") || hName.contains("ਤਾਰੀਖ") || lockedDateColsMap[activeSheetName]?.contains(c) == true || lockedDateColumnIndices.contains(c)
                            formatRawStringIfDateSerial(rawVal, isDateCol)
                        }
                        Pair(r + 1, rowCells)
                    }

                    lifecycleScope.launch(Dispatchers.Main) {
                        try {
                            val bitmap = generateNativeSelectionCardBitmap(headers, rowDataList)
                            val imageFile = File(cacheDir, "excel_selection_snapshot.png")

                            withContext(Dispatchers.IO) {
                                imageFile.outputStream().use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                }
                            }

                            val imageUri = androidx.core.content.FileProvider.getUriForFile(
                                this@ExcelViewerActivity,
                                "${applicationContext.packageName}.fileprovider",
                                imageFile
                            )

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, imageUri)
                                clipData = android.content.ClipData.newUri(contentResolver, "Excel selection snapshot", imageUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(shareIntent, "ਸਿਰਫ਼ ਸਿਲੈਕਟ ਕੀਤੇ ਸੈੱਲ ਸ਼ੇਅਰ ਕਰੋ (Share Snapshot)"))
                        } catch (e: Exception) {
                            Toast.makeText(this@ExcelViewerActivity, "ਇਮੇਜ ਸ਼ੇਅਰ ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }

                } catch (e: Exception) {
                    Toast.makeText(
                        this@ExcelViewerActivity,
                        "Selected cells ਪੜ੍ਹਨ ਵਿੱਚ ਗਲਤੀ: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }


        @JavascriptInterface
        fun shareSelectionAsImage() {
            shareSelectedMatrixAsImage("", "")
        }

        @JavascriptInterface
        fun triggerUndo() {
            runOnUiThread {
                undoLastAction()
            }
        }

        @JavascriptInterface
        fun triggerRedo() {
            runOnUiThread {
                redoNextAction()
            }
        }

        @JavascriptInterface
        fun pasteFromClipboardToCell(targetRow: Int, targetCol: Int) {
            runOnUiThread {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val text = clipData.getItemAt(0).text?.toString().orEmpty()
                        if (text.isNotEmpty()) {
                            pasteTextMatrixAt(targetRow, targetCol, text)
                        } else {
                            Toast.makeText(this@ExcelViewerActivity, "ਕਲਿੱਪਬੋਰਡ ਵਿੱਚ ਕੋਈ ਡਾਟਾ ਨਹੀਂ ਹੈ", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Throwable) {
                    Toast.makeText(this@ExcelViewerActivity, "ਪੇਸਟ ਕਰਨ ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun updateBatchCellData(batchJson: String) {
            runOnUiThread {
                try {
                    val jsonArray = org.json.JSONArray(batchJson)
                    if (jsonArray.length() == 0) return@runOnUiThread

                    pushHistoryState()

                    val sheet = activeSheet()
                    val sheetList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val rIdx = obj.optInt("r", -1)
                        val cIdx = obj.optInt("c", -1)
                        val newVal = obj.optString("v", "")

                        if (rIdx < 0 || cIdx < 0) continue

                        if (newVal.startsWith("=")) {
                            cellFormulasMap["$activeSheetName:$rIdx:$cIdx"] = newVal
                        } else {
                            cellFormulasMap.remove("$activeSheetName:$rIdx:$cIdx")
                        }

                        if (sheet != null) {
                            val row = sheet.getRow(rIdx) ?: sheet.createRow(rIdx)
                            val cell = row.getCell(cIdx) ?: row.createCell(cIdx)
                            setCellFromInput(cell, newVal)
                        } else if (sheetList != null) {
                            while (sheetList.size <= rIdx) {
                                sheetList.add(mutableListOf())
                            }
                            val row = sheetList[rIdx]
                            while (row.size <= cIdx) row.add("")
                            row[cIdx] = newVal
                        }
                    }

                    if (sheetList != null && sheet == null) {
                        currentSheetsData[activeSheetName] = sheetList
                    }

                    formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
                    refreshActiveSheetView()
                    Toast.makeText(this@ExcelViewerActivity, "ਫਾਰਮੂਲਾ ਪੇਸਟ ਹੋ ਗਿਆ (Undo ਉਪਲਬਧ ਹੈ)", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    android.util.Log.e("ExcelViewer", "updateBatchCellData error", e)
                }
            }
        }

        @JavascriptInterface
        fun updateBatchCellDataSilent(batchJson: String) {
            runOnUiThread {
                try {
                    val jsonArray = org.json.JSONArray(batchJson)
                    if (jsonArray.length() == 0) return@runOnUiThread

                    pushHistoryState()

                    val sheet = activeSheet()
                    val sheetList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val rIdx = obj.optInt("r", -1)
                        val cIdx = obj.optInt("c", -1)
                        val newVal = obj.optString("v", "")

                        if (rIdx < 0 || cIdx < 0) continue

                        if (newVal.startsWith("=")) {
                            cellFormulasMap["$activeSheetName:$rIdx:$cIdx"] = newVal
                        } else {
                            cellFormulasMap.remove("$activeSheetName:$rIdx:$cIdx")
                        }

                        if (sheet != null) {
                            val row = sheet.getRow(rIdx) ?: sheet.createRow(rIdx)
                            val cell = row.getCell(cIdx) ?: row.createCell(cIdx)
                            setCellFromInput(cell, newVal)
                        } else if (sheetList != null) {
                            while (sheetList.size <= rIdx) {
                                sheetList.add(mutableListOf())
                            }
                            val row = sheetList[rIdx]
                            while (row.size <= cIdx) row.add("")
                            row[cIdx] = newVal
                        }
                    }

                    if (sheetList != null && sheet == null) {
                        currentSheetsData[activeSheetName] = sheetList
                    }

                    formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
                    Toast.makeText(this@ExcelViewerActivity, "ਫਾਰਮੂਲਾ ਲਾਗੂ ਹੋ ਗਿਆ!", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    android.util.Log.e("ExcelViewer", "updateBatchCellDataSilent error", e)
                }
            }
        }

        @JavascriptInterface
        fun updateCellData(rowIndex: Int, colIndex: Int, newValue: String) {
            runOnUiThread {
                if (rowIndex < 0 || colIndex < 0) return@runOnUiThread
                pushHistoryState()

                if (newValue.startsWith("=")) {
                    cellFormulasMap["$activeSheetName:$rowIndex:$colIndex"] = newValue
                } else {
                    cellFormulasMap.remove("$activeSheetName:$rowIndex:$colIndex")
                }

                if (activeSheetName.isNotEmpty() && currentSheetsData.containsKey(activeSheetName)) {
                    val sheetList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList() ?: mutableListOf()
                    while (sheetList.size <= rowIndex) sheetList.add(mutableListOf())
                    val row = sheetList[rowIndex].toMutableList()
                    while (row.size <= colIndex) row.add("")
                    row[colIndex] = newValue
                    sheetList[rowIndex] = row
                    currentSheetsData[activeSheetName] = sheetList
                }

                val sheet = activeSheet()
                if (sheet != null) {
                    val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
                    val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
                    setCellFromInput(cell, newValue)
                    formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
                }
            }
        }

        @JavascriptInterface
        fun loadMoreRows() {
            runOnUiThread {
                progressBar.isVisible = false
                currentMaxRowsToShow += 10000
                refreshActiveSheetView()
            }
        }

        @JavascriptInterface
        fun deleteRowFromJs(rowIndex: Int) { runOnUiThread { confirmDeleteRow(rowIndex) } }
        @JavascriptInterface
        fun deleteColFromJs(colIndex: Int) { runOnUiThread { confirmDeleteColumn(colIndex) } }

        @JavascriptInterface
        fun updateActiveCellFormula(formulaOrVal: String, cellRef: String) {
            runOnUiThread {
                etFormula.setText(formulaOrVal)
                if (formulaOrVal.isNotBlank()) {
                    etFormula.setSelection(formulaOrVal.length)
                }
            }
        }

        @JavascriptInterface
        fun onScrollDirectionChanged(isScrollingDown: Boolean) {
            // Keep top bars layout stable to ensure zero WebView scrolling flicker
        }

        @JavascriptInterface
        fun showColumnMenu(colIndex: Int) { runOnUiThread { showColumnHeaderMenu(colIndex) } }

        @JavascriptInterface
        fun showRowMenu(rowIndex: Int) { runOnUiThread { showRowHeaderMenu(rowIndex) } }
    }

    private fun toggleTopBarsVisibility(show: Boolean) {
        // Layout kept 100% stable to prevent WebView surface flickering on 120Hz/60Hz screens
    }
// Ensure you have a workbook tracking instance variable declared at your class level, e.g.:
// private var currentWorkbook: org.apache.poi.ss.usermodel.Workbook? = null

    private fun activeSheet(): org.apache.poi.ss.usermodel.Sheet? {
        val workbook = currentWorkbook ?: return null
        return if (activeSheetName.isNotEmpty()) {
            try {
                workbook.getSheet(activeSheetName)
            } catch (e: Exception) {
                workbook.getSheetAt(0)
            }
        } else {
            workbook.getSheetAt(0)
        }
    }

    private fun applyFormulaToColumn(colIndex: Int, formulaStr: String) {
        if (colIndex < 0) return
        pushHistoryState()

        val sheet = activeSheet()
        if (sheet != null) {
            val totalRows = sheet.lastRowNum
            for (rIdx in 1..totalRows) {
                val row = sheet.getRow(rIdx) ?: sheet.createRow(rIdx)
                val cell = row.getCell(colIndex) ?: row.createCell(colIndex)

                val shiftedFormula = shiftFormulaReferences(formulaStr, rIdx - 1, 0)
                cellFormulasMap["$activeSheetName:$rIdx:$colIndex"] = shiftedFormula
                setCellFromInput(cell, shiftedFormula)
            }
            formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
            refreshActiveSheetView()
            Toast.makeText(this, "ਫਾਰਮੂਲਾ ਪੂਰੇ ਕੋਲਮ '${getColumnLabel(colIndex)}' 'ਤੇ ਲਾਗੂ ਹੋ ਗਿਆ!", Toast.LENGTH_SHORT).show()
        } else if (activeSheetName.isNotEmpty() && currentSheetsData.containsKey(activeSheetName)) {
            val sheetList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList() ?: return
            val totalRows = sheetList.size
            for (rIdx in 1 until totalRows) {
                val row = sheetList[rIdx]
                while (row.size <= colIndex) row.add("")

                val shiftedFormula = shiftFormulaReferences(formulaStr, rIdx - 1, 0)
                cellFormulasMap["$activeSheetName:$rIdx:$colIndex"] = shiftedFormula
                row[colIndex] = shiftedFormula
            }
            currentSheetsData[activeSheetName] = sheetList
            refreshActiveSheetView()
            Toast.makeText(this, "ਫਾਰਮੂਲਾ ਪੂਰੇ ਕੋਲਮ '${getColumnLabel(colIndex)}' 'ਤੇ ਲਾਗੂ ਹੋ ਗਿਆ!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFormulaFromBar() {
        val formula = etFormula.text?.toString()?.trim().orEmpty()
        if (formula.isEmpty()) {
            Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ ਕੋਈ ਫਾਰਮੂਲਾ ਲਿਖੋ (ਜਿਵੇਂ: =SUM(A1:A10))", Toast.LENGTH_SHORT).show()
            return
        }

        val formattedFormula = if (!formula.startsWith("=")) "=$formula" else formula

        if (selectedColIndices.isNotEmpty()) {
            selectedColIndices.forEach { colIndex ->
                applyFormulaToColumn(colIndex, formattedFormula)
            }
            return
        }

        webView.evaluateJavascript("applyFormulaToSelection('${escapeJsString(formattedFormula)}');", null)
    }

    private fun requestAllFilesAccessPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ 'Allow access to manage all files' ਆਨ ਕਰੋ", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "ਸਾਰੀਆਂ ਫਾਈਲਾਂ ਖੋਲ੍ਹਣ ਦੀ ਪਰਮਿਸ਼ਨ ਪਹਿਲਾਂ ਹੀ ਮਿਲੀ ਹੋਈ ਹੈ!", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
                } else {
                    Toast.makeText(this, "ਸਟੋਰੇਜ ਪਰਮਿਸ਼ਨ ਮਿਲੀ ਹੋਈ ਹੈ!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Throwable) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            } catch (_: Throwable) {
                Toast.makeText(this, "ਪਰਮਿਸ਼ਨ ਸੈਟਿੰਗ ਖੋਲ੍ਹਣ ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val excelMimeTypes = arrayOf(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/csv",
        "text/comma-separated-values",
        "application/csv",
        "application/excel",
        "application/x-excel",
        "application/x-msexcel"
    )

    private fun openExcelFilePicker() {
        try {
            openDocumentLauncher.launch(excelMimeTypes)
        } catch (_: Throwable) {
            openDocumentLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*"))
        }
    }

    private fun scanAndShowExcelFilesDialog() {
        val progressBar = ProgressBar(this).apply { isIndeterminate = true }
        val dialog = AlertDialog.Builder(this)
            .setTitle("🔍 ਫੋਨ ਵਿਚਲੀਆਂ ਐਕਸਲ ਫਾਈਲਾਂ ਲੱਭੀਆਂ ਜਾ ਰਹੀਆਂ ਹਨ...")
            .setView(progressBar)
            .setNegativeButton("ਰੱਦ ਕਰੋ", null)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            val excelFiles = mutableListOf<File>()
            val rootDirs = mutableListOf<File>()

            try {
                rootDirs.add(Environment.getExternalStorageDirectory())
                rootDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                rootDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            } catch (_: Throwable) {}

            fun scanDir(dir: File?, depth: Int) {
                if (dir == null || !dir.exists() || !dir.isDirectory || depth > 5) return
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.isDirectory && !f.name.startsWith(".")) {
                        scanDir(f, depth + 1)
                    } else if (f.isFile) {
                        val n = f.name.lowercase(Locale.ROOT)
                        if (n.endsWith(".xlsx") || n.endsWith(".xls") || n.endsWith(".csv")) {
                            excelFiles.add(f)
                        }
                    }
                }
            }

            for (dir in rootDirs.distinct()) {
                scanDir(dir, 0)
            }

            val sortedFiles = excelFiles.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }

            withContext(Dispatchers.Main) {
                dialog.dismiss()
                if (sortedFiles.isEmpty()) {
                    Toast.makeText(this@ExcelViewerActivity, "ਕੋਈ ਐਕਸਲ ਫਾਈਲ ਨਹੀਂ ਮਿਲੀ। ਫਾਈਲ ਪਿਕਰ ਖੋਲ੍ਹਿਆ ਜਾ ਰਿਹਾ ਹੈ...", Toast.LENGTH_SHORT).show()
                    openExcelFilePicker()
                } else {
                    showExcelFilesListDialog(sortedFiles)
                }
            }
        }
    }

    private fun showExcelFilesListDialog(files: List<File>) {
        val items = files.map { f ->
            val sizeMb = String.format(Locale.getDefault(), "%.2f MB", f.length() / (1024.0 * 1024.0))
            "📊 ${f.name}\n   📁 ${f.parentFile?.name ?: "Storage"} • $sizeMb"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("📊 ਫੋਨ ਵਿਚਲੀਆਂ ਐਕਸਲ ਫਾਈਲਾਂ (${files.size})")
            .setItems(items) { _, which ->
                val selectedFile = files[which]
                val uri = Uri.fromFile(selectedFile)
                currentUri = uri
                currentFileName = selectedFile.name
                supportActionBar?.title = currentFileName
                loadWorkbook(uri)
            }
            .setPositiveButton("📂 ਹੋਰ ਫੋਲਡਰ ਬ੍ਰਾਊਜ਼ ਕਰੋ") { _, _ ->
                openExcelFilePicker()
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ", null)
            .show()
    }

    private fun showToolbarMoreOptionsMenu() {
        val formulaBar = findViewById<LinearLayout>(R.id.excel_formula_bar)
        val isFormulaVisible = formulaBar?.isVisible == true
        val isCalcVisible = calcBar.isVisible

        val popup = androidx.appcompat.widget.PopupMenu(this, btnToolbarMoreMenu)
        popup.menu.add(0, 1, 0, if (isFormulaVisible) "✏️ ਫਾਰਮੂਲਾ ਬਾਰ ਲੁਕਾਓ (Hide Formula Bar)" else "✏️ ਫਾਰਮੂਲਾ ਬਾਰ ਦਿਖਾਓ (Show Formula Bar)")
        popup.menu.add(0, 2, 1, if (isCalcVisible) "📊 ਕੈਲਕੁਲੇਸ਼ਨ ਬਾਰ ਲੁਕਾਓ (Hide Calc Bar)" else "📊 ਕੈਲਕੁਲੇਸ਼ਨ ਬਾਰ ਦਿਖਾਓ (Show Calc Bar)")
        popup.menu.add(0, 3, 2, "🔍 ਲੱਭੋ ਅਤੇ ਬਦਲੋ (Find & Replace)")
        popup.menu.add(0, 4, 3, "💾 ਨਵੇਂ ਨਾਮ ਨਾਲ ਸੇਵ ਕਰੋ (Save As Excel...)")
        popup.menu.add(0, 5, 4, "📄 PDF ਡੌਕੂਮੈਂਟ ਬਣਾਓ (Save as PDF Document)")
        popup.menu.add(0, 6, 5, "🖼️ ਫੋਟੋ ਗੈਲਰੀ 'ਚ ਸੇਵ ਕਰੋ (Save Image to Gallery)")
        popup.menu.add(0, 7, 6, if (isDarkMode) "☀️ ਲਾਈਟ ਮੋਡ (Light Mode)" else "🌙 ਡਾਰਕ ਮੋਡ (Dark Mode)")
        popup.menu.add(0, 8, 7, "📁 ਫੋਨ ਵਿਚਲੀਆਂ ਐਕਸਲ ਫਾਈਲਾਂ ਲੱਭੋ (Scan Excel Files)")
        popup.menu.add(0, 9, 8, "📲 ਫਾਈਲ ਸ਼ੇਅਰ ਕਰੋ (Share File Copy)")
        popup.menu.add(0, 10, 9, "📌 ਹੋਮ ਸਕ੍ਰੀਨ ਸ਼ਾਰਟਕੱਟ (Add Shortcut)")
        popup.menu.add(0, 11, 10, "↺ ਰੀਸੈੱਟ ਫਾਈਲ (Reset File)")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val targetVisibility = !(formulaBar?.isVisible ?: false)
                    formulaBar?.isVisible = targetVisibility
                    Toast.makeText(this, if (targetVisibility) "✏️ ਫਾਰਮੂਲਾ ਬਾਰ ਚਾਲੂ ਹੈ" else "✏️ ਫਾਰਮੂਲਾ ਬਾਰ ਬੰਦ ਹੈ", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    calcBar.isVisible = !calcBar.isVisible
                    Toast.makeText(this, if (calcBar.isVisible) "📊 ਕੈਲਕੁਲੇਸ਼ਨ ਬਾਰ ਚਾਲੂ ਹੈ" else "📊 ਕੈਲਕੁਲੇਸ਼ਨ ਬਾਰ ਬੰਦ ਹੈ", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    findReplaceBar.isVisible = true
                    etFindText.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(etFindText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                4 -> saveExcelFile()
                5 -> exportSelectionAsPdf()
                6 -> saveSelectionSnapshotToGallery()
                7 -> {
                    isDarkMode = !isDarkMode
                    val prefs = getSharedPreferences("excel_viewer_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply()
                    btnToggleTheme.text = if (isDarkMode) "🌙" else "☀️"
                    refreshActiveSheetView()
                    Toast.makeText(this, if (isDarkMode) "🌙 Dark Mode" else "☀️ Light Mode", Toast.LENGTH_SHORT).show()
                }
                8 -> scanAndShowExcelFilesDialog()
                9 -> shareExcelFile()
                10 -> createHomeScreenShortcut()
                11 -> resetWorkbookToLoadedState()
            }
            true
        }
        popup.show()
    }

    private fun replaceOneMatchInActiveSheet() {
        val target = etFindText.text?.toString().orEmpty()
        val replacement = etReplaceText.text?.toString().orEmpty()
        if (target.isEmpty()) {
            Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ ਲੱਭਣ ਲਈ ਕੋਈ ਸ਼ਬਦ ਲਿਖੋ", Toast.LENGTH_SHORT).show()
            return
        }

        val sheetRows = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList()
        if (sheetRows != null) {
            pushHistoryState()
            var replaced = false
            for (r in sheetRows) {
                for (c in r.indices) {
                    val valStr = r[c]
                    if (valStr.contains(target, ignoreCase = true)) {
                        val firstMatchIndex = valStr.indexOf(target, ignoreCase = true)
                        if (firstMatchIndex >= 0) {
                            val prefix = valStr.substring(0, firstMatchIndex)
                            val suffix = valStr.substring(firstMatchIndex + target.length)
                            r[c] = prefix + replacement + suffix
                            replaced = true
                            break
                        }
                    }
                }
                if (replaced) break
            }

            if (replaced) {
                currentSheetsData[activeSheetName] = sheetRows
                refreshActiveSheetViewWithSearch(target)
                Toast.makeText(this, "ਸ਼ਬਦ ਬਦਲ ਦਿੱਤਾ ਗਿਆ ਹੈ", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "'$target' ਨਹੀਂ ਮਿਲਿਆ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun replaceAllMatchesInActiveSheet() {
        val target = etFindText.text?.toString().orEmpty()
        val replacement = etReplaceText.text?.toString().orEmpty()
        if (target.isEmpty()) {
            Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ ਲੱਭਣ ਲਈ ਕੋਈ ਸ਼ਬਦ ਲਿਖੋ", Toast.LENGTH_SHORT).show()
            return
        }

        val sheetRows = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList()
        if (sheetRows != null) {
            pushHistoryState()
            var replaceCount = 0
            for (r in sheetRows) {
                for (c in r.indices) {
                    val valStr = r[c]
                    if (valStr.contains(target, ignoreCase = true)) {
                        val regex = Regex("(?i)" + Regex.escape(target))
                        val matches = regex.findAll(valStr).count()
                        replaceCount += matches
                        r[c] = valStr.replace(regex, replacement)
                    }
                }
            }

            if (replaceCount > 0) {
                currentSheetsData[activeSheetName] = sheetRows
                refreshActiveSheetViewWithSearch(target)
                Toast.makeText(this, "$replaceCount ਥਾਵਾਂ 'ਤੇ '$target' ਬਦਲਿਆ ਗਿਆ!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "'$target' ਨਹੀਂ ਮਿਲਿਆ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshActiveSheetViewWithSearch(searchQuery: String) {
        val sheet = activeSheet()
        if (sheet != null && (sheet.lastRowNum > 0 || sheet.getRow(0)?.lastCellNum?.toInt() ?: 0 > 0)) {
            renderSheetHtml(sheet, searchQuery)
        } else if (currentSheetsData.containsKey(activeSheetName) && !currentSheetsData[activeSheetName].isNullOrEmpty()) {
            renderSheetDataHtml(activeSheetName, searchQuery)
        }
    }

    private fun showFormulaPickerDialog() {
        val options = arrayOf(
            // ----- MATH & CALCULATION (ਗਣਿਤ ਅਤੇ ਹਿਸਾਬ-ਕਿਤਾਬ) -----
            "📊 --- MATH & CALCULATION ---",
            "➕ =SUM(A1:A10) (ਕੁੱਲ ਜੋੜ - Total Sum)",
            "📊 =AVERAGE(A1:A10) (ਔਸਤ - Average)",
            "⬇️ =MIN(A1:A10) (ਘੱਟੋ-ਘੱਟ ਵੈਲਯੂ - Minimum)",
            "⬆️ =MAX(A1:A10) (ਵੱਧ ਤੋਂ ਵੱਧ ਵੈਲਯੂ - Maximum)",
            "✖️ =PRODUCT(A1:A10) (ਸਾਰਿਆਂ ਦੀ ਗੁਣਾ - Product)",
            "📐 =ROUND(A1*1.18, 2) (ਦਿਸ਼ਮਲਵ ਰਾਊਂਡ - Round Decimals)",
            "✖️ =A1*B1 (ਦੋ ਸੈੱਲਾਂ ਦੀ ਗੁਣਾ - Multiply)",
            "➕ =A1+B1 (ਦੋ ਸੈੱਲਾਂ ਦਾ ਜੋੜ - Add)",
            "➖ =A1-B1 (ਦੋ ਸੈੱਲਾਂ ਦੀ ਘਟਾਓ - Subtract)",
            "➗ =A1/B1 (ਦੋ ਸੈੱਲਾਂ ਦਾ ਭਾਗ - Divide)",

            // ----- TAX & BUSINESS (ਟੈਕਸ ਅਤੇ ਵਪਾਰ) -----
            "💼 --- TAX & BUSINESS ---",
            "💯 =(A1/B1)*100 (ਪ੍ਰਤੀਸ਼ਤ - Percentage %)",
            "📈 =(B1-A1)/B1*100 (ਮੁਨਾਫ਼ਾ ਮਾਰਜਨ - Profit Margin %)",
            "🏷️ =A1*0.18 (18% GST/ਟੈਕਸ ਕੈਲਕੁਲੇਸ਼ਨ)",
            "🏷️ =A1*0.12 (12% GST/ਟੈਕਸ ਕੈਲਕੁਲੇਸ਼ਨ)",
            "🏷️ =A1*0.05 (5% GST/ਟੈਕਸ ਕੈਲਕੁਲੇਸ਼ਨ)",
            "🏷️ =A1*0.10 (10% ਛੋਟ - Discount)",

            // ----- LOGICAL & LOOKUP (ਸ਼ਰਤਾਂ ਅਤੇ ਖੋਜ) -----
            "🔍 --- LOGICAL & LOOKUP ---",
            "🔍 =IF(A1>100, \"Pass\", \"Fail\") (ਸ਼ਰਤ / ਈਫ - IF Condition)",
            "🔍 =VLOOKUP(A1, B1:C10, 2, FALSE) (ਡੇਟਾ ਲੱਭਣਾ - Find Value)",
            "🔍 =XLOOKUP(A1, B1:B10, C1:C10) (ਐਡਵਾਂਸ ਲੁੱਕਅੱਪ - XLOOKUP)",
            "➕ =SUMIF(A1:A10, \">100\", B1:B10) (ਸ਼ਰਤ ਮੁਤਾਬਕ ਜੋੜ - SUMIF)",
            "🔢 =COUNTIF(A1:A10, \">100\") (ਸ਼ਰਤ ਮੁਤਾਬਕ ਗਿਣਤੀ - COUNTIF)",
            "⚠️ =IFERROR(A1/B1, \"Error\") (ਗਲਤੀ ਛੁਪਾਉਣਾ - Handle Errors)",

            // ----- COUNTING (ਗਿਣਤੀ) -----
            "🔢 --- COUNTING ---",
            "🔢 =COUNT(A1:A10) (ਸਿਰਫ਼ ਨੰਬਰਾਂ ਦੀ ਗਿਣਤੀ - Count Numbers)",
            "🔢 =COUNTA(A1:A10) (ਖਾਲੀ ਸੈੱਲ ਛੱਡ ਕੇ ਗਿਣਤੀ - Count Non-Empty)",

            // ----- TEXT FUNCTIONS (ਅੱਖਰ ਅਤੇ ਲਿਖਤ) -----
            "🔤 --- TEXT FUNCTIONS ---",
            "🔤 =UPPER(A1) (ਵੱਡੇ ਅੱਖਰ - UPPERCASE)",
            "🔤 =LOWER(A1) (ਛੋਟੇ ਅੱਖਰ - lowercase)",
            "🔤 =PROPER(A1) (ਪਹਿਲਾ ਅੱਖਰ ਵੱਡਾ - Title Case)",
            "📏 =LEN(A1) (ਅੱਖਰਾਂ ਦੀ ਲੰਬਾਈ - Text Length)",
            "🔤 =CONCATENATE(A1, \" \", B1) (ਟੈਕਸਟ ਜੋੜਨਾ - Join Text)",
            "🧹 =TRIM(A1) (ਵਾਧੂ ਸਪੇਸ ਹਟਾਉਣਾ - Remove Spaces)",

            // ----- DATE & TIME (ਤਾਰੀਖ ਅਤੇ ਸਮਾਂ) -----
            "📅 --- DATE & TIME ---",
            "📅 =TODAY() (ਅੱਜ ਦੀ ਤਾਰੀਖ - Current Date)",
            "⏰ =NOW() (ਅੱਜ ਦਾ ਸਮਾਂ ਤੇ ਤਾਰੀਖ - Current Date & Time)",
            "📅 =DATEDIF(A1, B1, \"Y\") (ਉਮਰ/ਸਾਲਾਂ ਦਾ ਫਰਕ - Age/Date Diff)"
        )

        AlertDialog.Builder(this)
            .setTitle("ਫਾਰਮੂਲੇ ਚੁਣੋ (Excel Formula List)")
            .setItems(options) { _, which ->
                if (options[which].startsWith("📊 ---") ||
                    options[which].startsWith("💼 ---") ||
                    options[which].startsWith("🔍 ---") ||
                    options[which].startsWith("🔢 ---") ||
                    options[which].startsWith("🔤 ---") ||
                    options[which].startsWith("📅 ---")) {
                    return@setItems
                }

                val selectedFormula = when (which) {
                    1 -> "=SUM(A1:A10)"
                    2 -> "=AVERAGE(A1:A10)"
                    3 -> "=MIN(A1:A10)"
                    4 -> "=MAX(A1:A10)"
                    5 -> "=PRODUCT(A1:A10)"
                    6 -> "=ROUND(A1*1.18, 2)"
                    7 -> "=A1*B1"
                    8 -> "=A1+B1"
                    9 -> "=A1-B1"
                    10 -> "=A1/B1"

                    12 -> "=(A1/B1)*100"
                    13 -> "=(B1-A1)/B1*100"
                    14 -> "=A1*0.18"
                    15 -> "=A1*0.12"
                    16 -> "=A1*0.05"
                    17 -> "=A1*0.10"

                    19 -> "=IF(A1>100, \"Pass\", \"Fail\")"
                    20 -> "=VLOOKUP(A1, B1:C10, 2, FALSE)"
                    21 -> "=XLOOKUP(A1, B1:B10, C1:C10)"
                    22 -> "=SUMIF(A1:A10, \">100\", B1:B10)"
                    23 -> "=COUNTIF(A1:A10, \">100\")"
                    24 -> "=IFERROR(A1/B1, \"Error\")"

                    26 -> "=COUNT(A1:A10)"
                    27 -> "=COUNTA(A1:A10)"

                    29 -> "=UPPER(A1)"
                    30 -> "=LOWER(A1)"
                    31 -> "=PROPER(A1)"
                    32 -> "=LEN(A1)"
                    33 -> "=CONCATENATE(A1, \" \", B1)"
                    34 -> "=TRIM(A1)"

                    36 -> "=TODAY()"
                    37 -> "=NOW()"
                    38 -> "=DATEDIF(A1, B1, \"Y\")"
                    else -> "=SUM(A1:A10)"
                }

                etFormula.setText(selectedFormula)
                etFormula.setSelection(selectedFormula.length)
                Toast.makeText(this, "ਫਾਰਮੂਲਾ ਚੁਣਿਆ ਗਿਆ — ਹੁਣ ਲਾਗੂ ਕਰੋ ਦਬਾਓ", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ", null)
            .show()
    }

    private fun showColumnHeaderMenu(colIndex: Int) {
        val colLabel = getColumnLabel(colIndex)
        val hasHiddenCols = hiddenColumnIndices.isNotEmpty()
        val hasColSel = selectedColIndices.isNotEmpty()

        val options = mutableListOf(
            "📌 ਇਸ ਕੋਲਮ ਤੱਕ ਫ੍ਰੀਜ਼ ਕਰੋ (Freeze Columns Up To Here)",
            "🔓 ਸਾਰੇ ਕੋਲਮ ਅਨ-ਫ੍ਰੀਜ਼ ਕਰੋ (Unfreeze Columns)",
            "➕ ਖੱਬੇ ਪਾਸੇ ਕੋਲਮ ਜੋੜੋ (Insert Column Left)",
            "➕ ਸੱਜੇ ਪਾਸੇ ਕੋਲਮ ਜੋੜੋ (Insert Column Right)",
            "☑️ ਕੋਲਮ ਸਿਲੈਕਟ ਕਰੋ (Select Column)",
            "👁️ ਕੋਲਮ ਲੁਕਾਓ (Hide Column)",
            "🖼️ ਇਮੇਜ ਵਜੋਂ ਭੇਜੋ (Share as Image)",
            "🔽 ਫਿਲਟਰ ਚੁਣੋ (Filter Checklist...)",
            "↔️ ਕੋਲਮ ਦੀ ਚੌੜਾਈ ਬਦਲੋ (Adjust Width)",
            "🔼 ਸੋਰਟ ਘੱਟ ਤੋਂ ਵੱਧ (Sort A-Z)",
            "🔽 ਸੋਰਟ ਵੱਧ ਤੋਂ ਘੱਟ (Sort Z-A)",
            "📋 ਕੋਲਮ ਕਾਪੀ ਕਰੋ (Copy Column)",
            "🧹 ਡਾਟਾ ਸਾਫ਼ ਕਰੋ (Clear Content)",
            "🗑️ ਕੋਲਮ ਡਿਲੀਟ ਕਰੋ (Delete Column)"
        )

        if (hasColSel) {
            options.add(3, "🗑️ ਚੁਣੇ ਹੋਏ ${selectedColIndices.size} ਕੋਲਮ ਡਿਲੀਟ ਕਰੋ (Delete Selected Columns)")
        }

        if (hasHiddenCols) {
            options.add(if (hasColSel) 4 else 3, "👁️‍🗨️ ਸਾਰੇ ਲੁਕੇ ਕੋਲਮ ਦਿਖਾਓ (Unhide All Columns)")
        }

        AlertDialog.Builder(this)
            .setTitle("ਕੋਲਮ '$colLabel' ਆਪਸ਼ਨ")
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when {
                    selectedOption.contains("Freeze Columns") -> {
                        frozenColumnCount = colIndex + 1
                        refreshActiveSheetView()
                        Toast.makeText(this, "📌 ਪਹਿਲੇ ${colIndex + 1} ਕੋਲਮ ਫ੍ਰੀਜ਼ ਕੀਤੇ ਗਏ ਹਨ", Toast.LENGTH_SHORT).show()
                    }
                    selectedOption.contains("Unfreeze Columns") -> {
                        frozenColumnCount = 0
                        refreshActiveSheetView()
                        Toast.makeText(this, "🔓 ਸਾਰੇ ਕੋਲਮ ਅਨ-ਫ੍ਰੀਜ਼ ਕੀਤੇ ਗਏ ਹਨ", Toast.LENGTH_SHORT).show()
                    }
                    selectedOption.contains("Insert Column Left") -> insertColumn(colIndex)
                    selectedOption.contains("Insert Column Right") -> insertColumn(colIndex + 1)
                    selectedOption.contains("Select Column") -> webView.evaluateJavascript("toggleColumnSelect($colIndex);", null)
                    selectedOption.contains("Delete Selected Columns") -> confirmDeleteSelectedColumns()
                    selectedOption.contains("Hide Column") -> hideColumn(colIndex)
                    selectedOption.contains("Unhide All Columns") -> unhideAllColumns()
                    selectedOption.contains("Share as Image") -> webView.evaluateJavascript("jsShareAsImage();", null)
                    selectedOption.contains("Filter Checklist") -> showColumnFilterDialog(colIndex)
                    selectedOption.contains("Adjust Width") -> showColumnWidthDialog(colIndex)
                    selectedOption.contains("Sort A-Z") -> sortColumn(colIndex, true)
                    selectedOption.contains("Sort Z-A") -> sortColumn(colIndex, false)
                    selectedOption.contains("Copy Column") -> copyColumnToClipboard(colIndex)
                    selectedOption.contains("Clear Content") -> clearColumnContent(colIndex)
                    selectedOption.contains("Delete Column") -> confirmDeleteColumn(colIndex)
                }
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ (Cancel)", null)
            .show()
    }

    private fun hideColumn(colIndex: Int) {
        hiddenColumnIndices.add(colIndex)
        refreshActiveSheetView()
        Toast.makeText(this, "ਕੋਲਮ '${getColumnLabel(colIndex)}' ਲੁਕਾ ਦਿੱਤਾ ਗਿਆ ਹੈ", Toast.LENGTH_SHORT).show()
    }

    private fun unhideAllColumns() {
        hiddenColumnIndices.clear()
        refreshActiveSheetView()
        Toast.makeText(this, "ਸਾਰੇ ਲੁਕੇ ਕੋਲਮ ਵਾਪਸ ਦਿਖਾਏ ਗਏ ਹਨ", Toast.LENGTH_SHORT).show()
    }

    private fun hideRow(rowIndex: Int) {
        hiddenRowIndices.add(rowIndex)
        refreshActiveSheetView()
        Toast.makeText(this, "ਲਾਈਨ ਨੰਬਰ ${rowIndex + 1} ਲੁਕਾ ਦਿੱਤੀ ਗਈ ਹੈ", Toast.LENGTH_SHORT).show()
    }

    private fun unhideAllRows() {
        hiddenRowIndices.clear()
        refreshActiveSheetView()
        Toast.makeText(this, "ਸਾਰੀਆਂ ਲੁਕੀਆਂ ਲਾਈਨਾਂ ਵਾਪਸ ਦਿਖਾਈਆਂ ਗਈਆਂ ਹਨ", Toast.LENGTH_SHORT).show()
    }

    private fun getSearchQuery(): String = if (::etFindText.isInitialized) etFindText.text?.toString().orEmpty() else ""

    private fun refreshActiveSheetView() {
        if (::webView.isInitialized) {
            webView.evaluateJavascript(
                "(function(){var s=document.getElementById('excelScroll');return s?(s.scrollLeft+','+s.scrollTop):'0,0';})()",
                android.webkit.ValueCallback<String> { scrollStr ->
                    val parts = scrollStr?.removeSurrounding("\"", "\"")?.split(",")
                    if (parts != null && parts.size == 2) {
                        val sx = parts[0].toIntOrNull() ?: 0
                        val sy = parts[1].toIntOrNull() ?: 0
                        if (sx > 0 || sy > 0) {
                            pendingScrollX = sx
                            pendingScrollY = sy
                        }
                    }
                    if (currentSheetsData.containsKey(activeSheetName) && !currentSheetsData[activeSheetName].isNullOrEmpty()) {
                        renderSheetDataHtml(activeSheetName, getSearchQuery())
                    } else {
                        val sheet = activeSheet()
                        if (sheet != null) {
                            renderSheetHtml(sheet, getSearchQuery())
                        } else {
                            val emptyHtml = "<html><body style='background:#121212;color:#E0E0E0;font-family:sans-serif;text-align:center;padding:60px 16px;'>" +
                                    "<div style='background:#1E1E1E;border:1px solid #333;border-radius:12px;padding:32px;display:inline-block;'>" +
                                    "<h3 style='color:#FFC107;margin-bottom:8px;'>ℹ️ ਇਸ ਸੀਟ ਵਿੱਚ ਕੋਈ ਡਾਟਾ ਨਹੀਂ ਹੈ (Empty Sheet)</h3>" +
                                    "<p style='color:#888888;font-size:13px;'>Sheet is empty or blank.</p></div></body></html>"
                            webView.loadDataWithBaseURL("https://excel.local/", emptyHtml, "text/html", "UTF-8", null)
                        }
                    }
                }
            )
            return
        }
        if (currentSheetsData.containsKey(activeSheetName) && !currentSheetsData[activeSheetName].isNullOrEmpty()) {
            renderSheetDataHtml(activeSheetName, getSearchQuery())
        } else {
            val sheet = activeSheet()
            if (sheet != null) {
                renderSheetHtml(sheet, getSearchQuery())
            }
        }
    }

    private fun extractPoiSheetRows(sheet: Sheet): List<List<String>> {
        val result = ArrayList<List<String>>()
        val endR = minOf(sheet.lastRowNum, 5000)
        val sName = try { sheet.sheetName } catch (_: Throwable) { activeSheetName }
        val dateCols = lockedDateColsMap.getOrPut(sName) { mutableSetOf() }

        val firstRow = sheet.getRow(0)
        if (firstRow != null) {
            for (c in 0 until minOf(firstRow.lastCellNum.toInt().coerceAtLeast(0), 64)) {
                val hText = firstRow.getCell(c)?.toString().orEmpty()
                if (isHeaderDate(hText)) {
                    dateCols.add(c)
                    lockedDateColumnIndices.add(c)
                }
            }
        }

        for (r in 0..endR) {
            val row = sheet.getRow(r)
            val rowData = ArrayList<String>()
            if (row != null) {
                for (c in 0 until minOf(row.lastCellNum.toInt().coerceAtLeast(0), 64)) {
                    val cell = row.getCell(c)
                    val isDateC = dateCols.contains(c) || lockedDateColumnIndices.contains(c) || lockedDateColsMap[sName]?.contains(c) == true
                    if (cell != null) {
                        rowData.add(rawCellValue(cell, isDateC))
                    } else {
                        rowData.add("")
                    }
                }
            }
            result.add(rowData)
        }
        return result
    }

    private fun renderSheetHtml(sheet: Sheet, query: String) {
        val pane = try { sheet.paneInformation } catch (_: Throwable) { null }
        if (pane != null && pane.isFreezePane && pane.verticalSplitPosition > 0) {
            frozenColsMap[activeSheetName] = pane.verticalSplitPosition.toInt()
        }
        val extracted = extractPoiSheetRows(sheet)
        currentSheetsData[activeSheetName] = extracted
        renderSheetDataHtml(activeSheetName, query)
    }

    // Optimized File Picker Launcher
    val pickExcelFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val fileUri: Uri? = result.data?.data
            if (fileUri != null) {
                // 1. Open and parse the workbook entirely on a background coroutine context
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(fileUri).use { inputStream ->
                            val workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream)
                            val sheet = workbook.getSheetAt(0)
                            activeSheetName = workbook.getSheetName(0)

                            val sheetRows = ArrayList<ArrayList<String>>()
                            val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()

                            // Prevent massive heap allocations by capping rendering limits (e.g., max 5000 rows)
                            val MAX_SAFE_ROWS = 5000
                            val endRow = minOf(sheet.lastRowNum, MAX_SAFE_ROWS)

                            // Scan headers to identify date columns for filtering context rules
                            val dateColIndicesData = mutableSetOf<Int>()
                            val firstRow = sheet.getRow(0)
                            if (firstRow != null) {
                                val endCol = minOf(firstRow.lastCellNum.toInt(), 64) // Prevent wide sheet OOM
                                for (c in 0 until endCol) {
                                    val cell = firstRow.getCell(c)
                                    val headerText = cell?.toString().orEmpty().lowercase(java.util.Locale.ROOT).trim()
                                    if (headerText.contains("date") || headerText == "dt" ||
                                        headerText.contains("dob") || headerText.contains("expiry") ||
                                        headerText.contains("time") || headerText.contains("ਤਾਰੀਖ")) {
                                        dateColIndicesData.add(c)
                                    }
                                }
                            }

                            // Process all rows safely inside the background thread block
                            for (r in 0..endRow) {
                                val row = sheet.getRow(r)
                                val rowData = ArrayList<String>()

                                if (row != null) {
                                    val endCol = minOf(row.lastCellNum.toInt(), 64)
                                    for (c in 0 until endCol) {
                                        val cell = row.getCell(c)
                                        if (cell != null) {
                                            // Safe legacy enum integer type matching configuration
                                            if (cell.cellType == org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC && !dateColIndicesData.contains(c) && !org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                                                val rawNumeric = cell.numericCellValue
                                                if (rawNumeric == rawNumeric.toLong().toDouble()) {
                                                    rowData.add(rawNumeric.toLong().toString())
                                                } else {
                                                    rowData.add(rawNumeric.toString())
                                                }
                                            } else {
                                                rowData.add(dataFormatter.formatCellValue(cell))
                                            }
                                        } else {
                                            rowData.add("")
                                        }
                                    }
                                } else {
                                    // Add blank fallback item structure to preserve indexing order
                                    rowData.add("")
                                }
                                sheetRows.add(rowData)
                            }

                            // 2. Switch to Main thread only after data string extraction finishes
                            withContext(Dispatchers.Main) {
                                val searchQuery = getSearchQuery()
                                renderSheetHtml(sheetRows as Sheet, searchQuery)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {

                        }
                    }
                }
            }
        }
    }


    private fun showColumnWidthDialog(colIndex: Int) {
        val colLabel = getColumnLabel(colIndex)
        val options = arrayOf(
            "↔️ ਆਟੋ ਫਿੱਟ (Auto Fit)",
            "📱 ਛੋਟਾ (Narrow - 80px)",
            "💻 ਮੀਡੀਅਮ (Normal - 130px)",
            "🖥️ ਚੌੜਾ (Wide - 200px)",
            "✏️ ਕਸਟਮ ਚੌੜਾਈ (Custom Width...)"
        )

        AlertDialog.Builder(this)
            .setTitle("ਕੋਲਮ '$colLabel' ਦੀ ਚੌੜਾਈ ਬਦਲੋ")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { saveColumnWidth(colIndex, -1); webView.evaluateJavascript("setColumnWidth($colIndex, 'auto')", null) }
                    1 -> { saveColumnWidth(colIndex, 80); webView.evaluateJavascript("setColumnWidth($colIndex, 80)", null) }
                    2 -> { saveColumnWidth(colIndex, 130); webView.evaluateJavascript("setColumnWidth($colIndex, 130)", null) }
                    3 -> { saveColumnWidth(colIndex, 200); webView.evaluateJavascript("setColumnWidth($colIndex, 200)", null) }
                    4 -> promptCustomColumnWidth(colIndex)
                }
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ (Cancel)", null)
            .show()
    }

    private fun promptCustomColumnWidth(colIndex: Int) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "ਚੌੜਾਈ Pixels ਵਿੱਚ ਲਿਖੋ (ਜਿਵੇਂ 150)"
        }
        AlertDialog.Builder(this)
            .setTitle("ਕੋਲਮ '${getColumnLabel(colIndex)}' ਦੀ ਚੌੜਾਈ")
            .setView(input)
            .setPositiveButton("ਲਾਗੂ ਕਰੋ") { _, _ ->
                val px = input.text.toString().toIntOrNull() ?: 120
                saveColumnWidth(colIndex, px)
                webView.evaluateJavascript("setColumnWidth($colIndex, $px)", null)
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ", null)
            .show()
    }

    private fun showColumnFilterDialog(colIndex: Int) {
        val sheet = activeSheet()
        val headerText = if (sheet != null) {
            sheet.getRow(0)?.getCell(colIndex)?.let { rawCellValue(it) }?.trim().orEmpty()
        } else {
            currentSheetsData[activeSheetName]?.firstOrNull()?.getOrNull(colIndex)?.trim().orEmpty()
        }
        val colLabel = if (headerText.isNotEmpty()) headerText else getColumnLabel(colIndex)

        val uniqueValues = mutableSetOf<String>()

        if (sheet != null) {
            val maxRows = minOf(sheet.lastRowNum, 5000)
            for (r in 1..maxRows) {
                if (hiddenRowIndices.contains(r)) continue
                val cell = sheet.getRow(r)?.getCell(colIndex)
                val valStr = cell?.let { rawCellValue(it) }?.trim().orEmpty()
                uniqueValues.add(if (valStr.isNotEmpty()) valStr else "(Blank)")
            }
        } else {
            val sheetRows = currentSheetsData[activeSheetName] ?: return
            val dataRows = if (sheetRows.size > 1) sheetRows.drop(1).take(5000) else sheetRows
            dataRows.forEachIndexed { rIdx, row ->
                if (!hiddenRowIndices.contains(rIdx + 1)) {
                    val valStr = row.getOrNull(colIndex)?.trim().orEmpty()
                    uniqueValues.add(if (valStr.isNotEmpty()) valStr else "(Blank)")
                }
            }
        }

        if (uniqueValues.isEmpty()) {
            Toast.makeText(this, "ਕੋਲਮ ਵਿੱਚ ਕੋਈ ਡਾਟਾ ਨਹੀਂ ਹੈ", Toast.LENGTH_SHORT).show()
            return
        }

        val sortedList = uniqueValues.sorted()
        val dialogView = layoutInflater.inflate(R.layout.dialog_excel_filter, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_filter_title)
        val etItemSearch = dialogView.findViewById<EditText>(R.id.et_filter_item_search)
        val btnSortAsc = dialogView.findViewById<Button>(R.id.btn_filter_sort_asc)
        val btnSortDesc = dialogView.findViewById<Button>(R.id.btn_filter_sort_desc)
        val container = dialogView.findViewById<LinearLayout>(R.id.filter_checklist_container)
        val btnClear = dialogView.findViewById<Button>(R.id.btn_filter_clear)
        val btnApply = dialogView.findViewById<Button>(R.id.btn_filter_apply)

        tvTitle.text = "Filter - $colLabel"

        val checkedStates = mutableMapOf<String, Boolean>()
        sortedList.forEach { item ->
            val isChecked = if (activeColumnFilterIndex == colIndex && activeColumnFilterSet.isNotEmpty()) {
                activeColumnFilterSet.contains(item)
            } else true
            checkedStates[item] = isChecked
        }

        val checkboxViews = mutableListOf<Pair<String, androidx.appcompat.widget.AppCompatCheckBox>>()

        val selectAllCb = androidx.appcompat.widget.AppCompatCheckBox(this).apply {
            text = "Select All"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = checkedStates.values.all { it }
            setPadding(8, 8, 8, 8)
        }
        container.addView(selectAllCb)

        sortedList.forEach { item ->
            val cb = androidx.appcompat.widget.AppCompatCheckBox(this).apply {
                text = item
                textSize = 14f
                setTextColor(0xFFE0E0E0.toInt())
                isChecked = checkedStates[item] == true
                setPadding(8, 6, 8, 6)
                setOnCheckedChangeListener { _, isChecked ->
                    checkedStates[item] = isChecked
                    selectAllCb.setOnCheckedChangeListener(null)
                    selectAllCb.isChecked = checkedStates.values.all { it }
                    selectAllCb.setOnCheckedChangeListener { _, allChecked ->
                        checkedStates.keys.forEach { checkedStates[it] = allChecked }
                        checkboxViews.forEach { it.second.isChecked = allChecked }
                    }
                }
            }
            checkboxViews.add(Pair(item, cb))
            container.addView(cb)
        }

        selectAllCb.setOnCheckedChangeListener { _, allChecked ->
            checkedStates.keys.forEach { checkedStates[it] = allChecked }
            checkboxViews.forEach { it.second.isChecked = allChecked }
        }

        etItemSearch.doOnTextChanged { text, _, _, _ ->
            val q = text?.toString()?.lowercase(Locale.ROOT).orEmpty()
            checkboxViews.forEach { (item, cb) ->
                cb.isVisible = q.isEmpty() || item.lowercase(Locale.ROOT).contains(q)
            }
        }

        val filterDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        filterDialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        btnSortAsc.setOnClickListener {
            sortColumn(colIndex, true)
            filterDialog.dismiss()
        }

        btnSortDesc.setOnClickListener {
            sortColumn(colIndex, false)
            filterDialog.dismiss()
        }

        btnClear.setOnClickListener {
            clearColumnFilter()
            filterDialog.dismiss()
        }

        btnApply.setOnClickListener {
            val selectedSet = checkedStates.filter { it.value }.keys.toSet()
            if (selectedSet.size == sortedList.size) {
                clearColumnFilter()
            } else {
                applyMultiColumnFilter(colIndex, selectedSet)
            }
            filterDialog.dismiss()
        }

        filterDialog.show()
    }

    private fun applyMultiColumnFilter(colIndex: Int, selectedSet: Set<String>) {
        activeColumnFilterIndex = colIndex
        activeColumnFilterSet.clear()
        activeColumnFilterSet.addAll(selectedSet)
        btnClearFilter.isVisible = true
        calcBar.isVisible = true
        refreshActiveSheetView()
        val count = selectedSet.size
        Toast.makeText(this, "ਫਿਲਟਰ ਲਾਗੂ ਹੋਇਆ: ${getColumnLabel(colIndex)} ($count ਆਈਟਮਾਂ ਚੁਣੀਆਂ)", Toast.LENGTH_SHORT).show()
    }

    private fun clearColumnFilter() {
        activeColumnFilterIndex = -1
        activeColumnFilterSet.clear()
        btnClearFilter.isVisible = false
        refreshActiveSheetView()
        Toast.makeText(this, "ਫਿਲਟਰ ਹਟਾਇਆ ਗਿਆ", Toast.LENGTH_SHORT).show()
    }

    private fun shiftFormulaReferences(formula: String, rowOffset: Int, colOffset: Int): String {
        if (!formula.startsWith("=")) return formula
        val expr = formula.substring(1)
        val cellRefRegex = Regex("""(\$?)([A-Za-z]+)(\$?)(-?\d+)""")

        val shiftedExpr = cellRefRegex.replace(expr) { matchResult ->
            val colDollar = matchResult.groupValues[1]
            val colStr = matchResult.groupValues[2]
            val rowDollar = matchResult.groupValues[3]
            val rowNumStr = matchResult.groupValues[4]

            val origRowNum = rowNumStr.toIntOrNull() ?: return@replace matchResult.value
            val newRowNum = if (rowDollar == "$") origRowNum else (origRowNum + rowOffset).coerceAtLeast(1)
            val newColStr = if (colDollar == "$") colStr else shiftColumnString(colStr, colOffset)

            "$colDollar$newColStr$rowDollar$newRowNum"
        }

        return "=$shiftedExpr"
    }

    private fun shiftColumnString(colStr: String, colOffset: Int): String {
        if (colOffset == 0) return colStr
        var idx = 0
        val upper = colStr.uppercase(Locale.ROOT)
        for (ch in upper) {
            idx = idx * 26 + (ch.code - 'A'.code + 1)
        }
        val newIdx = (idx - 1 + colOffset).coerceAtLeast(0)
        return getColumnLabel(newIdx)
    }

    private fun pasteTextMatrixAt(targetRow: Int, targetCol: Int, text: String) {
        if (targetRow < 0 || targetCol < 0) return
        pushHistoryState()
        val lines = text.split("\r\n", "\n", "\r").filter { it.isNotEmpty() || text.length < 50 }
        val matrix = lines.map { line -> line.split("\t") }

        val sheet = activeSheet()
        if (sheet != null) {
            for ((rOffset, rowParts) in matrix.withIndex()) {
                val rIdx = targetRow + rOffset
                val row = sheet.getRow(rIdx) ?: sheet.createRow(rIdx)
                for ((cOffset, rawCellVal) in rowParts.withIndex()) {
                    val cIdx = targetCol + cOffset
                    val cell = row.getCell(cIdx) ?: row.createCell(cIdx)
                    val shiftedVal = if (rawCellVal.startsWith("=")) shiftFormulaReferences(rawCellVal, rOffset, cOffset) else rawCellVal
                    val isDateVal = isStringAlreadyDateFormat(shiftedVal) || shiftedVal.toDoubleOrNull()?.let { it >= 30000.0 && it <= 65000.0 } == true
                    val formattedDate = formatRawStringIfDateSerial(shiftedVal, isDateCol = isDateVal)
                    val finalVal = if (isStringAlreadyDateFormat(formattedDate) && !formattedDate.startsWith("'")) "'$formattedDate" else formattedDate
                    setCellFromInput(cell, finalVal)
                }
            }
            formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
            refreshActiveSheetView()
            Toast.makeText(this, "ਫਾਰਮੂਲਾ/ਡਾਟਾ ਪੇਸਟ ਹੋ ਗਿਆ (Undo ਉਪਲਬਧ ਹੈ)", Toast.LENGTH_SHORT).show()
        } else if (activeSheetName.isNotEmpty() && currentSheetsData.containsKey(activeSheetName)) {
            val sheetList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList() ?: return
            for ((rOffset, rowParts) in matrix.withIndex()) {
                val rIdx = targetRow + rOffset
                while (sheetList.size <= rIdx) {
                    sheetList.add(mutableListOf())
                }
                val row = sheetList[rIdx]
                for ((cOffset, rawCellVal) in rowParts.withIndex()) {
                    val cIdx = targetCol + cOffset
                    while (row.size <= cIdx) row.add("")
                    val shiftedVal = if (rawCellVal.startsWith("=")) shiftFormulaReferences(rawCellVal, rOffset, cOffset) else rawCellVal
                    val isDateVal = isStringAlreadyDateFormat(shiftedVal) || shiftedVal.toDoubleOrNull()?.let { it >= 30000.0 && it <= 65000.0 } == true
                    val formattedDate = formatRawStringIfDateSerial(shiftedVal, isDateCol = isDateVal)
                    val finalVal = if (isStringAlreadyDateFormat(formattedDate) && !formattedDate.startsWith("'")) "'$formattedDate" else formattedDate
                    row[cIdx] = finalVal
                }
            }
            currentSheetsData[activeSheetName] = sheetList
            refreshActiveSheetView()
            Toast.makeText(this, "ਫਾਰਮੂਲਾ/ਡਾਟਾ ਪੇਸਟ ਹੋ ਗਿਆ (Undo ਉਪਲਬਧ ਹੈ)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRowHeaderMenu(rowIndex: Int) {
        val hasHiddenRows = hiddenRowIndices.isNotEmpty()
        val hasRowSel = selectedRowIndices.isNotEmpty()

        val options = mutableListOf(
            "📲 ਕਸਟਮਰ ਨੂੰ ਭੇਜੋ (Share Row to Customer)",
            "🖼️ ਇਮੇਜ ਵਜੋਂ ਭੇਜੋ (Share as Image)",
            "👁️ ਲਾਈਨ ਲੁਕਾਓ (Hide Row)",
            "↕️ ਲਾਈਨ ਦੀ ਉਚਾਈ ਬਦਲੋ (Adjust Height)",
            "➕ ਨਵੀਂ ਲਾਈਨ ਜੋੜੋ (Insert Row Above)",
            "📋 ਲਾਈਨ ਕਾਪੀ ਕਰੋ (Copy Row)",
            "🧹 ਡਾਟਾ ਸਾਫ਼ ਕਰੋ (Clear Content)",
            "🗑️ ਲਾਈਨ ਡਿਲੀਟ ਕਰੋ (Delete Row)"
        )

        if (hasRowSel) {
            options.add(1, "🗑️ ਚੁਣੀਆਂ ਹੋਈਆਂ ${selectedRowIndices.size} ਲਾਈਨਾਂ ਡਿਲੀਟ ਕਰੋ (Delete Selected Rows)")
        }

        if (hasHiddenRows) {
            options.add(if (hasRowSel) 2 else 1, "👁️‍🗨️ ਸਾਰੀਆਂ ਲੁਕੀਆਂ ਲਾਈਨਾਂ ਦਿਖਾਓ (Unhide All Rows)")
        }

        AlertDialog.Builder(this)
            .setTitle("ਲਾਈਨ ਨੰਬਰ ${rowIndex + 1} ਆਪਸ਼ਨ")
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when {
                    selectedOption.contains("Share Row") -> {
                        selectedRowIndices.clear()
                        selectedRowIndices.add(rowIndex)
                        showCustomerRowDialog()
                    }
                    selectedOption.contains("Share as Image") -> webView.evaluateJavascript("jsShareAsImage();", null)
                    selectedOption.contains("Delete Selected Rows") -> confirmDeleteSelectedRows()
                    selectedOption.contains("Hide Row") -> hideRow(rowIndex)
                    selectedOption.contains("Unhide All Rows") -> unhideAllRows()
                    selectedOption.contains("Adjust Height") -> showRowHeightDialog(rowIndex)
                    selectedOption.contains("Insert Row") -> insertRow(rowIndex)
                    selectedOption.contains("Copy Row") -> copyRowToClipboard(rowIndex)
                    selectedOption.contains("Clear Content") -> clearRowContent(rowIndex)
                    selectedOption.contains("Delete Row") -> confirmDeleteRow(rowIndex)
                }
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ (Cancel)", null)
            .show()
    }

    private fun showRowHeightDialog(rowIndex: Int) {
        val options = arrayOf(
            "↔️ ਆਟੋ ਫਿੱਟ (Auto Fit)",
            "📱 ਛੋਟੀ ਉਚਾਈ (Short - 28px)",
            "💻 ਮੀਡੀਅਮ (Normal - 42px)",
            "🖥️ ਉੱਚੀ ਉਚਾਈ (Tall - 60px)",
            "✏️ ਕਸਟਮ ਉਚਾਈ (Custom Height...)"
        )

        AlertDialog.Builder(this)
            .setTitle("ਲਾਈਨ ਨੰਬਰ ${rowIndex + 1} ਦੀ ਉਚਾਈ ਬਦਲੋ")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> webView.evaluateJavascript("setRowHeight($rowIndex, 'auto')", null)
                    1 -> webView.evaluateJavascript("setRowHeight($rowIndex, 28)", null)
                    2 -> webView.evaluateJavascript("setRowHeight($rowIndex, 42)", null)
                    3 -> webView.evaluateJavascript("setRowHeight($rowIndex, 60)", null)
                    4 -> promptCustomRowHeight(rowIndex)
                }
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ (Cancel)", null)
            .show()
    }

    private fun promptCustomRowHeight(rowIndex: Int) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "ਉਚਾਈ Pixels ਵਿੱਚ ਲਿਖੋ (ਜਿਵੇਂ 45)"
        }
        AlertDialog.Builder(this)
            .setTitle("ਲਾਈਨ ਨੰਬਰ ${rowIndex + 1} ਦੀ ਉਚਾਈ")
            .setView(input)
            .setPositiveButton("ਲਾਗੂ ਕਰੋ") { _, _ ->
                val px = input.text.toString().toIntOrNull() ?: 40
                webView.evaluateJavascript("setRowHeight($rowIndex, $px)", null)
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ", null)
            .show()
    }

    private fun sortColumn(colIndex: Int, ascending: Boolean) {
        val sheet = activeSheet()
        if (sheet != null) {
            pushHistoryState()
            Toast.makeText(this, "ਕੋਲਮ '${getColumnLabel(colIndex)}' ਸੋਰਟ ਕੀਤਾ ਗਿਆ (${if (ascending) "A-Z" else "Z-A"})", Toast.LENGTH_SHORT).show()
        } else if (activeSheetName.isNotEmpty() && currentSheetsData.containsKey(activeSheetName)) {
            val list = currentSheetsData[activeSheetName]?.map { it.toList() }?.toMutableList() ?: return
            if (list.size <= 1) return

            pushHistoryState()
            val headerRow = list.first()
            val dataRows = list.drop(1)

            val sortedDataRows = dataRows.sortedWith(Comparator { r1, r2 ->
                val v1 = r1.getOrNull(colIndex)?.trim().orEmpty()
                val v2 = r2.getOrNull(colIndex)?.trim().orEmpty()
                val n1 = v1.toDoubleOrNull()
                val n2 = v2.toDoubleOrNull()

                val cmp = if (n1 != null && n2 != null) n1.compareTo(n2) else v1.compareTo(v2, ignoreCase = true)
                if (ascending) cmp else -cmp
            })

            val newList = mutableListOf<List<String>>()
            newList.add(headerRow)
            newList.addAll(sortedDataRows)
            currentSheetsData[activeSheetName] = newList
            refreshActiveSheetView()
            Toast.makeText(this, "ਕੋਲਮ '${getColumnLabel(colIndex)}' ਸੋਰਟ ਕੀਤਾ ਗਿਆ (${if (ascending) "A-Z" else "Z-A"})", Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertColumn(colIndex: Int) {
        pushHistoryState()
        val wb = workbook
        if (wb != null) {
            val sheet = activeSheet()
            if (sheet != null) {
                val lastRow = sheet.lastRowNum
                for (r in 0..lastRow) {
                    val row = sheet.getRow(r) ?: continue
                    val lastCol = row.lastCellNum.toInt()
                    if (colIndex <= lastCol) {
                        for (c in lastCol downTo colIndex + 1) {
                            val oldCell = row.getCell(c - 1)
                            val newCell = row.createCell(c)
                            if (oldCell != null) {
                                when (oldCell.cellType) {
                                    Cell.CELL_TYPE_STRING -> newCell.setCellValue(oldCell.stringCellValue)
                                    Cell.CELL_TYPE_NUMERIC -> newCell.setCellValue(oldCell.numericCellValue)
                                    Cell.CELL_TYPE_BOOLEAN -> newCell.setCellValue(oldCell.booleanCellValue)
                                    Cell.CELL_TYPE_FORMULA -> newCell.cellFormula = oldCell.cellFormula
                                    else -> {}
                                }
                            }
                        }
                        val targetCell = row.createCell(colIndex)
                        targetCell.setCellValue("")
                    }
                }
            }
        }

        val currentList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList()
        if (currentList != null) {
            for (row in currentList) {
                val idx = colIndex.coerceIn(0, row.size)
                row.add(idx, "")
            }
            currentSheetsData[activeSheetName] = currentList
        }

        refreshActiveSheetView()
        Toast.makeText(this, "ਨਵਾਂ ਕੋਲਮ ਜੋੜਿਆ ਗਿਆ (New Column Added)", Toast.LENGTH_SHORT).show()
    }

    private fun insertRow(rowIndex: Int) {
        val currentList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList() ?: return
        pushHistoryState()
        val idx = rowIndex.coerceIn(0, currentList.size)
        val maxCols = currentList.maxOfOrNull { it.size } ?: 1
        val emptyRow = MutableList(maxCols) { "" }
        currentList.add(idx, emptyRow)
        currentSheetsData[activeSheetName] = currentList
        refreshActiveSheetView()
        Toast.makeText(this, "ਨਵੀਂ ਲਾਈਨ ਜੋੜੀ ਗਈ (Undo ਉਪਲਬਧ ਹੈ)", Toast.LENGTH_SHORT).show()
    }

    private fun clearColumnContent(colIndex: Int) {
        val currentList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList() ?: return
        pushHistoryState()
        for (row in currentList) {
            if (colIndex in row.indices) {
                row[colIndex] = ""
            }
        }
        currentSheetsData[activeSheetName] = currentList
        refreshActiveSheetView()
        Toast.makeText(this, "ਕੋਲਮ ਦਾ ਡਾਟਾ ਸਾਫ਼ ਕੀਤਾ ਗਿਆ (Undo ਉਪਲਬਧ ਹੈ)", Toast.LENGTH_SHORT).show()
    }

    private fun clearRowContent(rowIndex: Int) {
        val currentList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList() ?: return
        if (rowIndex in currentList.indices) {
            pushHistoryState()
            val emptyRow = MutableList(currentList[rowIndex].size) { "" }
            currentList[rowIndex] = emptyRow
            currentSheetsData[activeSheetName] = currentList
            refreshActiveSheetView()
            Toast.makeText(this, "ਲਾਈਨ ਦਾ ਡਾਟਾ ਸਾਫ਼ ਕੀਤਾ ਗਿਆ (Undo ਉਪਲਬਧ ਹੈ)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyColumnToClipboard(colIndex: Int) {
        val rows = currentSheetsData[activeSheetName] ?: return
        val colValues = rows.map { it.getOrNull(colIndex)?.trim().orEmpty() }
        val text = colValues.joinToString("\n")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("ColumnData", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "ਕੋਲਮ '${getColumnLabel(colIndex)}' ਕਾਪੀ ਕੀਤਾ ਗਿਆ", Toast.LENGTH_SHORT).show()
    }

    private fun copyRowToClipboard(rowIndex: Int) {
        val rows = currentSheetsData[activeSheetName] ?: return
        val row = rows.getOrNull(rowIndex) ?: return
        val text = row.joinToString("\t")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("RowData", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "ਲਾਈਨ ${rowIndex + 1} ਕਾਪੀ ਕੀਤੀ ਗਈ", Toast.LENGTH_SHORT).show()
    }

    private fun formatAllDateSerialsInSheets(dataMap: MutableMap<String, List<List<String>>>) {
        val keys = ArrayList(dataMap.keys)
        for (sheetKey in keys) {
            val rows = dataMap[sheetKey] ?: continue
            val headerRow = rows.firstOrNull() ?: continue

            val dateCols = lockedDateColsMap.getOrPut(sheetKey) { mutableSetOf() }

            for ((c, cellText) in headerRow.withIndex()) {
                if (isHeaderDate(cellText)) {
                    dateCols.add(c)
                    lockedDateColumnIndices.add(c)
                }
            }

            val maxCols = rows.maxOfOrNull { it.size } ?: 0
            for (c in 0 until maxCols) {
                if (dateCols.contains(c)) continue
                val headerText = headerRow.getOrNull(c).orEmpty()
                if (isAmountHeader(headerText)) continue

                var formattedDateMatches = 0
                var totalStrings = 0
                for (r in rows.take(30)) {
                    val valStr = r.getOrNull(c)?.trim().orEmpty()
                    if (valStr.isNotEmpty()) {
                        totalStrings++
                        if (isStringAlreadyDateFormat(valStr)) {
                            formattedDateMatches++
                        }
                    }
                }
                if (totalStrings > 0 && formattedDateMatches.toDouble() / totalStrings >= 0.4) {
                    dateCols.add(c)
                    lockedDateColumnIndices.add(c)
                }
            }

            if (dateCols.isEmpty()) continue

            val formattedRows = ArrayList<List<String>>(rows.size)
            for ((rIdx, row) in rows.withIndex()) {
                if (rIdx == 0) {
                    formattedRows.add(row)
                    continue
                }
                val newRow = ArrayList<String>(row.size)
                for ((cIdx, cellVal) in row.withIndex()) {
                    val formattedDate = formatRawStringIfDateSerial(cellVal, isDateCol = dateCols.contains(cIdx))
                    newRow.add(formattedDate)
                }
                formattedRows.add(newRow)
            }
            dataMap[sheetKey] = formattedRows
        }
    }

    private fun loadWorkbook(uri: Uri) {
        progressBar.isVisible = true
        emptyView.isVisible = false
        currentMaxRowsToShow = 2000
        hiddenColumnIndices.clear()
        hiddenRowIndices.clear()
        lockedDateColsMap.clear()

        workbook?.closeQuietly()
        workbook = null
        currentSheetsData.clear()
        originalSheetsData.clear()
        cellFormulasMap.clear()
        poiHistoryStack.clear()
        dataHistoryStack.clear()
        System.gc()

        lifecycleScope.launch {
            val fileName = currentFileName.lowercase(Locale.ROOT)
            val isLegacyXls = fileName.endsWith(".xls") && !fileName.endsWith(".xlsx")

            if (isLegacyXls) {
                // Legacy .xls binary file -> POI HSSFWorkbook
                val loaded = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri)?.use { input -> HSSFWorkbook(input) }
                    } catch (e: Throwable) {
                        android.util.Log.e("ExcelViewer", "Could not open .xls with HSSFWorkbook", e)
                        null
                    }
                }

                if (loaded != null && loaded.numberOfSheets > 0) {
                    workbook = loaded
                    formulaEvaluator = try { loaded.creationHelper.createFormulaEvaluator() } catch (_: Throwable) { null }
                    activeSheetName = loaded.getSheetName(0)
                    poiHistoryStack.clear()
                    dataHistoryStack.clear()
                    selectedRowIndices.clear()
                    selectedColIndices.clear()
                    btnUndoRows.isVisible = false
                    setupSheetTabs()
                    renderSheetHtml(loaded.getSheetAt(0), getSearchQuery())
                    emptyView.isVisible = false
                    progressBar.isVisible = false
                    return@launch
                }
            }

            // Direct Pure Kotlin ZIP XML / CSV Streaming Parser (Zero-DOM, Zero-OOM, Instant Load)
            val sheets = withContext(Dispatchers.IO) {
                parseExcelOrCsv(uri)
            }
            progressBar.isVisible = false

            if (sheets.isNotEmpty()) {
                workbook?.closeQuietly()
                workbook = null
                currentSheetsData.clear()
                originalSheetsData.clear()
                poiHistoryStack.clear()
                dataHistoryStack.clear()
                selectedRowIndices.clear()
                selectedColIndices.clear()
                sheets.forEach { (k, v) ->
                    currentSheetsData[k] = v.map { it.toList() }
                    originalSheetsData[k] = v.map { it.toList() }
                }
                formatAllDateSerialsInSheets(currentSheetsData)
                formatAllDateSerialsInSheets(originalSheetsData)
                val firstSheetRows = sheets.values.firstOrNull() ?: emptyList()
                detectAndLockDateColumns(firstSheetRows)
                setupSheetTabsFromData(sheets.keys.toList())
                val firstSheet = sheets.keys.first()
                activeSheetName = firstSheet
                renderSheetDataHtml(firstSheet, getSearchQuery())
                emptyView.isVisible = false
            } else {
                emptyView.isVisible = true
                Toast.makeText(this@ExcelViewerActivity, "ਫਾਈਲ ਨਹੀਂ ਖੁੱਲ੍ਹ ਸਕੀ (invalid format)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createSheetTabDrawable(isSelected: Boolean): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 16f
            if (isSelected) {
                setColor(0xFF2E7D32.toInt())
                setStroke(3, 0xFF81C784.toInt())
            } else {
                setColor(0xFF222222.toInt())
                setStroke(1, 0xFF444444.toInt())
            }
        }
    }

    private fun setupSheetTabs() {
        val wb = workbook ?: return
        sheetsTabLayout.removeAllViews()
        val count = wb.numberOfSheets
        sheetsTabScroll.isVisible = count > 1

        if (count > 1) {
            val badge = TextView(this).apply {
                text = "📊 $count ਸੀਟਾਂ:"
                textSize = 12f
                setTextColor(0xFF81C784.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(12, 8, 12, 8)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            sheetsTabLayout.addView(badge)
        }

        for (i in 0 until count) {
            val name = wb.getSheetName(i)
            val isSel = (name == activeSheetName)
            val button = Button(this).apply {
                text = if (isSel) "✓ $name" else name
                textSize = 12f
                isAllCaps = false
                setPadding(20, 4, 20, 4)
                setTextColor(if (isSel) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt())
                setTypeface(null, if (isSel) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                background = createSheetTabDrawable(isSel)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(6, 0, 6, 0)
                }
                layoutParams = params
                setOnClickListener {
                    activeSheetName = name
                    selectedRowIndices.clear()
                    selectedColIndices.clear()
                    hiddenColumnIndices.clear()
                    hiddenRowIndices.clear()
                    currentMaxRowsToShow = 500
                    calcBar.isVisible = false
                    btnDeleteRows.isVisible = false
                    btnDeleteCols.isVisible = false
                    progressBar.isVisible = true
                    setupSheetTabs()
                    activeSheet()?.let { renderSheetHtml(it, getSearchQuery()) }
                }
            }
            sheetsTabLayout.addView(button)
        }
    }

    private fun setupSheetTabsFromData(sheetNames: List<String>) {
        sheetsTabLayout.removeAllViews()
        val count = sheetNames.size
        if (count <= 1) {
            sheetsTabScroll.isVisible = false
            return
        }

        sheetsTabScroll.isVisible = true
        val badge = TextView(this).apply {
            text = "📊 $count ਸੀਟਾਂ:"
            textSize = 12f
            setTextColor(0xFF81C784.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(12, 8, 12, 8)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        sheetsTabLayout.addView(badge)

        for (sheetName in sheetNames) {
            val isSel = (sheetName == activeSheetName)
            val btn = Button(this).apply {
                text = if (isSel) "✓ $sheetName" else sheetName
                textSize = 12f
                isAllCaps = false
                setPadding(20, 4, 20, 4)
                setTextColor(if (isSel) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt())
                setTypeface(null, if (isSel) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                background = createSheetTabDrawable(isSel)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(6, 0, 6, 0)
                }
                layoutParams = params
                setOnClickListener {
                    activeSheetName = sheetName
                    selectedRowIndices.clear()
                    selectedColIndices.clear()
                    hiddenColumnIndices.clear()
                    hiddenRowIndices.clear()
                    currentMaxRowsToShow = 500
                    calcBar.isVisible = false
                    btnDeleteRows.isVisible = false
                    btnDeleteCols.isVisible = false
                    progressBar.isVisible = true
                    setupSheetTabsFromData(sheetNames)
                    renderSheetDataHtml(sheetName, getSearchQuery())
                }
            }
            sheetsTabLayout.addView(btn)
        }
    }




    private fun renderSheetDataHtml(sheetName: String, query: String) {
        lifecycleScope.launch {
            try {
                val sheetRows = currentSheetsData[sheetName] ?: return@launch
                val totalRows = sheetRows.size
                val maxRowsToShow = minOf(totalRows, currentMaxRowsToShow)
                val lowerQuery = query.lowercase(Locale.ROOT)

                val htmlFile = withContext(Dispatchers.Default) {
                    var firstMatchFound = false

                    val maxCols = (sheetRows.maxOfOrNull { it.size } ?: 0)
                        .coerceAtMost(500)
                    val maxRowsToShow = minOf(totalRows, currentMaxRowsToShow).coerceAtMost(5000)

                    val dateColIndicesData = mutableSetOf<Int>()
                    lockedDateColsMap[sheetName]?.let { dateColIndicesData.addAll(it) }
                    dateColIndicesData.addAll(lockedDateColumnIndices)

                    if (dateColIndicesData.isEmpty()) {
                        for (rData in sheetRows.take(15)) {
                            for ((c, cellText) in rData.withIndex()) {
                                if (isHeaderDate(cellText)) {
                                    dateColIndicesData.add(c)
                                    lockedDateColumnIndices.add(c)
                                }
                            }
                        }
                    }

                    val headerRow = sheetRows.firstOrNull() ?: emptyList()
                    for (c in 0 until maxCols) {
                        if (dateColIndicesData.contains(c)) continue
                        val headerText = headerRow.getOrNull(c).orEmpty()
                        if (isAmountHeader(headerText)) continue

                        var dateSerialMatches = 0
                        var totalNums = 0
                        for (rData in sheetRows.take(30)) {
                            val cellStr = rData.getOrNull(c)?.trim().orEmpty()
                            if (cellStr.isNotEmpty()) {
                                totalNums++
                                if (isStringAlreadyDateFormat(cellStr)) {
                                    dateSerialMatches++
                                }
                            }
                        }
                        if (totalNums > 0 && dateSerialMatches.toDouble() / totalNums >= 0.4) {
                            dateColIndicesData.add(c)
                            lockedDateColumnIndices.add(c)
                        }
                    }

                    val bodyBg = if (isDarkMode) "#121212" else "#FFFFFF"
                    val bodyFg = if (isDarkMode) "#E0E0E0" else "#000000"
                    val tableBorder = if (isDarkMode) "#333333" else "#D0D0D0"
                    val thBg = if (isDarkMode) "#FFFFFF" else "#E0E0E0"
                    val thFg = if (isDarkMode) "#000000" else "#000000"
                    val rowNumBg = if (isDarkMode) "#1E1E1E" else "#F0F0F0"
                    val rowNumFg = if (isDarkMode) "#AAAAAA" else "#333333"
                    val evenRowBg = if (isDarkMode) "#1A1A1A" else "#F8F8F8"
                    val oddRowBg = if (isDarkMode) "#121212" else "#FFFFFF"

                    val sb = StringBuilder()

                    sb.append(
                        "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                                "<meta name='viewport' content='width=device-width, initial-scale=1.0, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes'>"
                    )

                    sb.append("<style>")
                    sb.append("*{box-sizing:border-box;touch-action:manipulation!important;}")
                    sb.append(
                        "html,body{width:100%;height:100%;margin:0;padding:0;" +
                                "overflow:hidden;background:$bodyBg;color:$bodyFg;" +
                                "font-family:monospace,sans-serif;user-select:none;" +
                                "-webkit-user-select:none;}"
                    )
                    sb.append(
                        "#excelScroll{width:100%;height:100vh;overflow:auto;" +
                                "position:relative;-webkit-overflow-scrolling:touch;" +
                                "overscroll-behavior:contain;padding-bottom:150px!important;padding-right:150px!important;}"
                    )
                    sb.append(
                        "table{border-collapse:separate!important;border-spacing:0!important;" +
                                "table-layout:auto;width:max-content;min-width:100%;transform-origin:0 0!important;-webkit-transform-origin:0 0!important;zoom:$currentZoomScale;margin:0;}"
                    )
                    sb.append(
                        "th,td{border-right:1px solid $tableBorder;" +
                                "border-bottom:1px solid $tableBorder;padding:6px 10px;" +
                                "font-size:13px;text-align:left;position:relative;" +
                                "min-width:35px;white-space:nowrap!important;overflow:hidden!important;" +
                                "text-overflow:ellipsis!important;height:32px;vertical-align:middle;box-sizing:border-box!important;}"
                    )
                    sb.append(
                        "thead th{position:sticky!important;top:0!important;" +
                                "z-index:20!important;background:$thBg!important;" +
                                "color:$thFg!important;font-weight:bold!important;" +
                                "cursor:pointer;}"
                    )
                    sb.append(
                        "th.row-num,td.row-num{position:sticky!important;left:0!important;" +
                                "min-width:60px!important;width:60px!important;" +
                                "max-width:60px!important;text-align:center!important;" +
                                "background:$rowNumBg!important;color:$rowNumFg!important;" +
                                "z-index:10!important;padding:8px 4px!important;" +
                                "font-weight:bold;cursor:pointer;}"
                    )
                    sb.append(
                        "thead th.row-num{top:0!important;left:0!important;" +
                                "z-index:30!important;background:#333333!important;" +
                                "color:#FFFFFF!important;}"
                    )
                    val currentFrozenCount = frozenColumnCount
                    val currentFrozenRowCount = frozenRowsMap[activeSheetName] ?: 0
                    val stickyBg = if (isDarkMode) "#1C1C1C" else "#FFFFFF"
                    val stickyThBg = if (isDarkMode) "#2D2D2D" else "#E0E0E0"
                    sb.append(".frozen-col-cell{position:sticky!important;background:$stickyBg!important;}\n")
                    sb.append("thead th.frozen-col-cell{background:$stickyThBg!important;}\n")
                    sb.append("tbody tr:nth-child(even){background:$evenRowBg;}")
                    sb.append("tbody tr:nth-child(odd){background:$oddRowBg;}")
                    sb.append(".excel-cell{cursor:pointer;}")
                    sb.append(
                        ".selected-cell{position:relative!important;outline:2px solid #81C784!important;outline-offset:-2px!important;font-weight:bold;}" +
                                ".excel-fill-handle{position:absolute;right:-8px;bottom:-8px;width:20px;height:20px;background-color:#4CAF50;border:2px solid #FFFFFF;border-radius:50%;box-shadow:0 2px 6px rgba(0,0,0,0.4);cursor:crosshair;z-index:1000;touch-action:none;}"
                    )
                    sb.append(
                        ".selected-row{outline:1px dashed #81C784!important;}"
                    )
                    sb.append(
                        ".selected-col{outline:1px dashed #81C784!important;}"
                    )
                    sb.append(
                        ".highlight{background:#FFD54F;color:#000000;font-weight:bold;" +
                                "padding:2px 4px;border-radius:2px;}"
                    )
                    val activeRowBg = if (isDarkMode) "#1B3820" else "#E8F5E9"
                    val activeRowFg = if (isDarkMode) "#FFFFFF" else "#000000"

                    sb.append(
                        ".active-header{background:#2e7d32!important;color:#ffffff!important;" +
                                "font-weight:bold!important;border-color:#4caf50!important;}"
                    )
                    sb.append(".active-row-bg{background-color:$activeRowBg!important;color:$activeRowFg!important;}")
                    sb.append(".active-row-bg td{color:$activeRowFg!important;}")
                    sb.append(
                        ".filter-arrow{display:inline-block;font-size:10px;margin-left:6px;" +
                                "color:#555;background:#eeeeee;padding:2px 4px;" +
                                "border-radius:2px;border:1px solid #cccccc;cursor:pointer;}"
                    )
                    sb.append(
                        ".active-filter-th{background:#ffeb3b!important;color:#000!important;}" +
                                ".active-arrow{background:#000!important;color:#fff!important;}"
                    )
                    sb.append(
                        ".resizer-col{position:absolute;right:0;top:0;width:12px;" +
                                "height:100%;cursor:col-resize;user-select:none;" +
                                "touch-action:none;z-index:5;border-right:2px solid transparent;}"
                    )
                    sb.append(
                        ".resizer-col:active,.resizer-col.dragging{" +
                                "background:#2e7d32;border-right:2px solid #4caf50;}"
                    )
                    sb.append(
                        ".resizer-row{position:absolute;left:0;bottom:0;width:100%;" +
                                "height:10px;cursor:row-resize;user-select:none;" +
                                "touch-action:none;z-index:5;border-bottom:2px solid transparent;}"
                    )
                    sb.append(
                        ".resizer-row:active,.resizer-row.dragging{" +
                                "background:#2e7d32;border-bottom:2px solid #4caf50;}"
                    )
                    sb.append(
                        ".context-popup{position:fixed;z-index:20000;background:#222;" +
                                "color:#fff;border-radius:20px;border:1px solid #4CAF50;" +
                                "box-shadow:0 4px 16px rgba(0,0,0,.8);display:flex;" +
                                "flex-direction:row;align-items:center;padding:2px 6px;}"
                    )
                    sb.append(
                        ".context-popup button{background:transparent;color:#fff;" +
                                "border:none;padding:6px 12px;font-size:13px;" +
                                "font-weight:bold;cursor:pointer;border-right:1px solid #333;}"
                    )
                    sb.append(".context-popup button:last-child{border-right:none;}")
                    sb.append(
                        ".context-dropdown{position:absolute;top:38px;right:0;" +
                                "background:#2C2C2C;border:1px solid #555;border-radius:10px;" +
                                "box-shadow:0 4px 16px rgba(0,0,0,.9);display:flex;" +
                                "flex-direction:column;padding:4px 0;min-width:180px;z-index:20001;}"
                    )
                    sb.append(
                        ".context-dropdown button{text-align:left;padding:10px 14px;" +
                                "border-bottom:1px solid #3A3A3A;font-size:13px;color:#FFFFFF;}"
                    )
                    sb.append(".context-dropdown button:last-child{border-bottom:none;}")
                    sb.append("</style>")

                    sb.append("<script>")
                    sb.append("var selectedCells=new Set();")
                    sb.append("var selectedRows=new Set();")
                    sb.append("var selectedColumns=new Set();")

                    sb.append(
                        "function isStringAlreadyDateFormat(v){" +
                                "if(!v)return false;" +
                                "var s=String(v).trim();" +
                                "if(s.startsWith(\"'\"))s=s.substring(1).trim();" +
                                "if(!s)return false;" +
                                "var dRegex=/^\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}$/;" +
                                "var aRegex=/^\\d{1,2}[-/.]?[A-Za-z]{3,9}[-/.]?\\d{2,4}$/;" +
                                "var spRegex=/^\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4}$/;" +
                                "return dRegex.test(s)||aRegex.test(s)||spRegex.test(s);}"
                    )

                    sb.append(
                        "function parseCellVal(v){" +
                                "if(!v)return null;" +
                                "var n=parseFloat(String(v).replace(/,/g,'').trim());" +
                                "return isNaN(n)?null:n;}"
                    )

                    sb.append(
                        "function toggleMoreMenu(e){if(e)e.stopPropagation();" +
                                "var drop=document.getElementById('context_more_menu');" +
                                "if(drop)drop.style.display=(drop.style.display==='flex')?'none':'flex';}"
                    )

                    sb.append(
                        "function updateContextMenuPosition(){" +
                                "var m=document.getElementById('selection_context_menu');" +
                                "var drop=document.getElementById('context_more_menu');" +
                                "if(!m)return;" +
                                "if(selectedCells.size>0){" +
                                "var arr=Array.from(selectedCells);var fc=arr[arr.length-1]||arr[0];" +
                                "var rect=fc.getBoundingClientRect();" +
                                "m.style.display='flex';" +
                                "var menuW=m.offsetWidth||180;var menuH=m.offsetHeight||36;" +
                                "var viewW=window.innerWidth||document.documentElement.clientWidth;" +
                                "var viewH=window.innerHeight||document.documentElement.clientHeight;" +
                                "var cellCenterY=rect.top+(rect.height/2);" +
                                "var isBottomHalf=(cellCenterY>(viewH/2));" +
                                "var top,left;" +
                                "if(isBottomHalf){" +
                                "top=rect.top-menuH-10;if(top<10){top=rect.bottom+10;}" +
                                "if(drop){drop.style.top='auto';drop.style.bottom='38px';}" +
                                "}else{" +
                                "top=rect.bottom+10;if(top+menuH>viewH-10){top=Math.max(10,rect.top-menuH-10);}" +
                                "if(drop){drop.style.top='38px';drop.style.bottom='auto';}" +
                                "}" +
                                "left=rect.left+(rect.width/2)-(menuW/2);" +
                                "if(left+menuW>viewW-10){left=viewW-menuW-10;}" +
                                "if(left<10){left=10;}" +
                                "m.style.top=Math.round(top)+'px';" +
                                "m.style.left=Math.round(left)+'px';" +
                                "}else{m.style.display='none';if(drop)drop.style.display='none';}}"
                    )

                    sb.append(
                        "function calculateAndNotifySilent(){" +
                                "var sum=0,countAll=0,countNums=0,minVal=null,maxVal=null;" +
                                "selectedCells.forEach(function(cell){" +
                                "countAll++;" +
                                "var val=parseCellVal(cell.getAttribute('data-raw'));" +
                                "if(val!==null){sum+=val;countNums++;" +
                                "if(minVal===null||val<minVal)minVal=val;" +
                                "if(maxVal===null||val>maxVal)maxVal=val;}});" +
                                "var avg=countNums>0?sum/countNums:0;" +
                                "if(window.AndroidBridge){" +
                                "window.AndroidBridge.updateFullStats(sum,minVal===null?0:minVal," +
                                "maxVal===null?0:maxVal,avg,countAll,countNums," +
                                "Array.from(selectedRows).join(',')," +
                                "Array.from(selectedColumns).join(','));}" +
                                "initFillHandle();}"
                    )

                    sb.append(
                        "function calculateAndNotify(){" +
                                "var sum=0,countAll=0,countNums=0,minVal=null,maxVal=null;" +
                                "selectedCells.forEach(function(cell){" +
                                "countAll++;" +
                                "var val=parseCellVal(cell.getAttribute('data-raw'));" +
                                "if(val!==null){sum+=val;countNums++;" +
                                "if(minVal===null||val<minVal)minVal=val;" +
                                "if(maxVal===null||val>maxVal)maxVal=val;}});" +
                                "var avg=countNums>0?sum/countNums:0;" +
                                "if(window.AndroidBridge){" +
                                "window.AndroidBridge.updateFullStats(sum,minVal===null?0:minVal," +
                                "maxVal===null?0:maxVal,avg,countAll,countNums," +
                                "Array.from(selectedRows).join(',')," +
                                "Array.from(selectedColumns).join(','));}" +
                                "updateContextMenuPosition();" +
                                "initFillHandle();}"
                    )

                    sb.append(
                        "function initFillHandle(){" +
                                "document.querySelectorAll('.excel-fill-handle').forEach(function(el){el.remove();});" +
                                "if(selectedCells.size===0)return;" +
                                "var arr=Array.from(selectedCells);var lastCell=arr[arr.length-1];if(!lastCell)return;" +
                                "var handle=document.createElement('div');handle.className='excel-fill-handle';" +
                                "lastCell.appendChild(handle);" +
                                "var isDragging=false,targetCell=null,autoScrollTimer=null;" +
                                "var currentTouchX=0,currentTouchY=0;" +
                                "function checkAutoScroll(){" +
                                "if(!isDragging)return;" +
                                "var scroller=document.getElementById('excelScroll');" +
                                "if(scroller){var rect=scroller.getBoundingClientRect();" +
                                "var stepX=0,stepY=0;" +
                                "if(currentTouchY>rect.bottom-70)stepY=18;" +
                                "else if(currentTouchY<rect.top+70)stepY=-18;" +
                                "if(currentTouchX>rect.right-70)stepX=18;" +
                                "else if(currentTouchX<rect.left+70)stepX=-18;" +
                                "if(stepX!==0||stepY!==0){" +
                                "scroller.scrollTop+=stepY;scroller.scrollLeft+=stepX;" +
                                "var elem=document.elementFromPoint(currentTouchX,currentTouchY);" +
                                "if(elem){var cell=elem.closest('td.excel-cell');" +
                                "if(cell){targetCell=cell;highlightDragRange(lastCell,targetCell);}}}}" +
                                "autoScrollTimer=requestAnimationFrame(checkAutoScroll);}" +
                                "function onDown(e){" +
                                "e.stopPropagation();if(e.preventDefault)e.preventDefault();isDragging=true;" +
                                "currentTouchX=e.touches?e.touches[0].clientX:e.clientX;" +
                                "currentTouchY=e.touches?e.touches[0].clientY:e.clientY;" +
                                "autoScrollTimer=requestAnimationFrame(checkAutoScroll);" +
                                "function onMove(me){" +
                                "if(!isDragging)return;" +
                                "if(me.preventDefault)me.preventDefault();if(me.stopPropagation)me.stopPropagation();" +
                                "currentTouchX=me.touches?me.touches[0].clientX:me.clientX;" +
                                "currentTouchY=me.touches?me.touches[0].clientY:me.clientY;" +
                                "var elem=document.elementFromPoint(currentTouchX,currentTouchY);" +
                                "if(elem){var cell=elem.closest('td.excel-cell');" +
                                "if(cell){targetCell=cell;highlightDragRange(lastCell,targetCell);}}}" +
                                "function onUp(){" +
                                "if(isDragging){isDragging=false;" +
                                "if(autoScrollTimer)cancelAnimationFrame(autoScrollTimer);" +
                                "if(targetCell){selectCellRange(lastCell,targetCell);" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.showSelectionActionDialog==='function'){" +
                                "window.AndroidBridge.showSelectionActionDialog();" +
                                "}else{updateContextMenuPosition();}}}" +
                                "document.removeEventListener('touchmove',onMove);" +
                                "document.removeEventListener('touchend',onUp);" +
                                "document.removeEventListener('mousemove',onMove);" +
                                "document.removeEventListener('mouseup',onUp);}" +
                                "document.addEventListener('touchmove',onMove,{passive:false});" +
                                "document.addEventListener('touchend',onUp);" +
                                "document.addEventListener('mousemove',onMove);" +
                                "document.addEventListener('mouseup',onUp);}" +
                                "handle.addEventListener('touchstart',onDown,{passive:false});" +
                                "handle.addEventListener('mousedown',onDown);}"
                    )

                    sb.append(
                        "function highlightDragRange(srcCell,tgtCell){" +
                                "if(!srcCell||!tgtCell)return;" +
                                "var sR=parseInt(srcCell.getAttribute('data-r'),10),sC=parseInt(srcCell.getAttribute('data-c'),10);" +
                                "var tR=parseInt(tgtCell.getAttribute('data-r'),10),tC=parseInt(tgtCell.getAttribute('data-c'),10);" +
                                "var minR=Math.min(sR,tR),maxR=Math.max(sR,tR);" +
                                "var minC=Math.min(sC,tC),maxC=Math.max(sC,tC);" +
                                "if(selectedCells.size>0){selectedCells.forEach(function(c){" +
                                "var r=parseInt(c.getAttribute('data-r'),10),col=parseInt(c.getAttribute('data-c'),10);" +
                                "if(!isNaN(r)&&!isNaN(col)){if(r<minR)minR=r;if(r>maxR)maxR=r;if(col<minC)minC=col;if(col>maxC)maxC=col;}});}" +
                                "selectedCells.forEach(function(c){c.classList.remove('selected-cell');});selectedCells.clear();" +
                                "for(var r=minR;r<=maxR;r++){for(var c=minC;c<=maxC;c++){" +
                                "var cell=getCellByPos(r,c);if(cell){cell.classList.add('selected-cell');selectedCells.add(cell);}}}}"
                    )

                    sb.append(
                        "function executeAutoFill(srcCell,tgtCell){" +
                                "var sR=parseInt(srcCell.getAttribute('data-r')),sC=parseInt(srcCell.getAttribute('data-c'));" +
                                "var tR=parseInt(tgtCell.getAttribute('data-r'));" +
                                "var fv=srcCell.getAttribute('data-formula')||srcCell.getAttribute('data-raw')||srcCell.innerText||'';" +
                                "var minR=Math.min(sR,tR),maxR=Math.max(sR,tR);" +
                                "var batch=[];" +
                                "for(var r=minR;r<=maxR;r++){" +
                                "var cell=getCellByPos(r,sC);" +
                                "if(cell&&r!==sR){" +
                                "var rOff=r-sR;" +
                                "var valToApply=fv;" +
                                "if(fv.startsWith('=')){" +
                                "valToApply=fv.replace(/(\\\$?)([A-Za-z]+)(\\\$?)(-?\\d+)/g,function(m,colDl,colStr,rowDl,rowNumStr){" +
                                "var rNum=parseInt(rowNumStr);" +
                                "var newR=(rowDl==='$')?rNum:Math.max(1,rNum+rOff);" +
                                "return colDl+colStr+rowDl+newR;});}" +
                                "cell.setAttribute('data-formula',fv.startsWith('=')?valToApply:'');" +
                                "cell.setAttribute('data-raw',valToApply);batch.push({r:r,c:sC,v:valToApply});}}" +
                                "if(batch.length>0&&window.AndroidBridge&&typeof window.AndroidBridge.updateBatchCellData==='function'){" +
                                "window.AndroidBridge.updateBatchCellData(JSON.stringify(batch));}}"
                    )

                    sb.append(
                        "function initCellRangeDragEvents(){}"
                    )

                    sb.append(
                        "function selectCellRange(startCell,endCell){" +
                                "if(!startCell||!endCell)return;" +
                                "var sR=parseInt(startCell.getAttribute('data-r'),10),sC=parseInt(startCell.getAttribute('data-c'),10);" +
                                "var eR=parseInt(endCell.getAttribute('data-r'),10),eC=parseInt(endCell.getAttribute('data-c'),10);" +
                                "var minR=Math.min(sR,eR),maxR=Math.max(sR,eR);" +
                                "var minC=Math.min(sC,eC),maxC=Math.max(sC,eC);" +
                                "if(selectedCells.size>0){selectedCells.forEach(function(c){" +
                                "var r=parseInt(c.getAttribute('data-r'),10),col=parseInt(c.getAttribute('data-c'),10);" +
                                "if(!isNaN(r)&&!isNaN(col)){if(r<minR)minR=r;if(r>maxR)maxR=r;if(col<minC)minC=col;if(col>maxC)maxC=col;}});}" +
                                "selectedCells.forEach(function(c){c.classList.remove('selected-cell');});" +
                                "selectedCells.clear();selectedRows.clear();selectedColumns.clear();" +
                                "for(var r=minR;r<=maxR;r++){for(var c=minC;c<=maxC;c++){" +
                                "var cell=getCellByPos(r,c);if(cell){cell.classList.add('selected-cell');selectedCells.add(cell);}}}" +
                                "calculateAndNotifySilent();initFillHandle();}"
                    )

                    sb.append(
                        "function highlightActiveRowCol(rIdx,cIdx){" +
                                "document.querySelectorAll('.active-header').forEach(function(el){" +
                                "el.classList.remove('active-header');});" +
                                "document.querySelectorAll('.active-row-bg').forEach(function(el){" +
                                "el.classList.remove('active-row-bg');});" +
                                "if(rIdx>=0){var tr=document.getElementById('tr_'+rIdx);" +
                                "if(tr){tr.classList.add('active-row-bg');" +
                                "var rn=tr.querySelector('.row-num');" +
                                "if(rn)rn.classList.add('active-header');}}" +
                                "if(cIdx>=0){var th=document.querySelector('th[data-col=\"'+cIdx+'\"]');" +
                                "if(th)th.classList.add('active-header');}}"
                    )

                    sb.append(
                        "function jsCopySelection(){" +
                                "var arr=[];selectedCells.forEach(function(c){" +
                                "var f=c.getAttribute('data-formula')||'';" +
                                "var raw=c.getAttribute('data-raw')||'';" +
                                "var txt=(c.innerText||c.textContent||'').trim();" +
                                "if(raw.startsWith('='))f=raw;" +
                                "var valToCopy=f?f:(raw.startsWith(\"'\")?raw:(isStringAlreadyDateFormat(txt)?\"'\"+txt:(isStringAlreadyDateFormat(raw)?\"'\"+raw:raw)));" +
                                "arr.push(valToCopy);});" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.copyTextToClipboard==='function'){" +
                                "window.AndroidBridge.copyTextToClipboard(arr.join('\\n'));}}"
                    )
                    sb.append("function jsCutSelection(){jsCopySelection();jsClearSelection();}")
                    sb.append(
                        "function jsPasteSelection(){" +
                                "if(selectedCells.size>0){" +
                                "var arr=Array.from(selectedCells);" +
                                "var tc=arr[arr.length-1]||arr[0];" +
                                "var r=parseInt(tc.getAttribute('data-r'));" +
                                "var c=parseInt(tc.getAttribute('data-c'));" +
                                "if(window.AndroidBridge)window.AndroidBridge.pasteFromClipboardToCell(r,c);}}"
                    )
                    sb.append(
                        "function jsEditSelection(){if(selectedCells.size>0){var c=Array.from(selectedCells)[0];var rawVal=c.getAttribute('data-raw')||c.innerText||'';var rR=parseInt(c.getAttribute('data-r'),10),cC=parseInt(c.getAttribute('data-c'),10);if(window.AndroidBridge&&typeof window.AndroidBridge.showEditDialogForCell==='function'){window.AndroidBridge.showEditDialogForCell(rR,cC,rawVal);}else{editCell(c);}}}"
                    )
                    sb.append(
                        "function jsFillSelection(){if(selectedCells.size>1){" +
                                "var arr=Array.from(selectedCells);" +
                                "var fc=arr[0];" +
                                "var baseR=parseInt(fc.getAttribute('data-r'));" +
                                "var baseC=parseInt(fc.getAttribute('data-c'));" +
                                "var fv=fc.getAttribute('data-formula')||fc.getAttribute('data-raw')||'';" +
                                "arr.forEach(function(c){" +
                                "var r=parseInt(c.getAttribute('data-r'));" +
                                "var col=parseInt(c.getAttribute('data-c'));" +
                                "var rOff=r-baseR;var cOff=col-baseC;" +
                                "var valToApply=fv;" +
                                "if(fv.startsWith('=')){" +
                                "valToApply=fv.replace(/(\\\$?)([A-Za-z]+)(\\\$?)(-?\\d+)/g,function(m,cDl,cStr,rDl,rNumStr){" +
                                "var rNum=parseInt(rNumStr);" +
                                "var newR=(rDl==='$')?rNum:Math.max(1,rNum+rOff);" +
                                "var newC=(cDl==='$')?cStr:colIdxToName(colStrToIdx(cStr)+cOff);" +
                                "return cDl+newC+rDl+newR;});}" +
                                "c.setAttribute('data-formula',fv.startsWith('=')?valToApply:'');" +
                                "c.setAttribute('data-raw',valToApply);" +
                                "c.innerText=valToApply.startsWith(\"'\")?valToApply.substring(1):valToApply;" +
                                "if(window.AndroidBridge)window.AndroidBridge.updateCellData(r,col,valToApply);});" +
                                "calculateAndNotify();}}"
                    )
                    sb.append(
                        "function jsClearSelection(){selectedCells.forEach(function(c){" +
                                "c.setAttribute('data-raw','');c.innerText='';" +
                                "var rR=parseInt(c.getAttribute('data-r'),10),cC=parseInt(c.getAttribute('data-c'),10);" +
                                "if(window.AndroidBridge)window.AndroidBridge.updateCellData(rR,cC,'');});calculateAndNotify();}"
                    )

                    sb.append(
                        "function jsShareAsImage() {" +
                                "var arr = [];" +
                                "if (!selectedCells || selectedCells.length === 0) {" +
                                "alert('Please select cells first');" +
                                "return;" +
                                "}" +
                                "selectedCells.forEach(function(c) {" +
                                "var r = parseInt(c.getAttribute('data-r'), 10);" +
                                "var col = parseInt(c.getAttribute('data-c'), 10);" +
                                "if (Number.isNaN(r) || Number.isNaN(col)) return;" +
                                "var value = c.getAttribute('data-raw');" +
                                "if (value === null) value = c.innerText || '';" +
                                "arr.push({" +
                                "r: r, c: col, value: String(value)" +
                                "});" +
                                "});" +
                                "if (arr.length === 0) {" +
                                "alert('No valid selected cells');" +
                                "return;" +
                                "}" +
                                "if (window.AndroidBridge && " +
                                "typeof window.AndroidBridge.shareSelectedMatrixAsImage === 'function') {" +
                                "window.AndroidBridge.shareSelectedMatrixAsImage(" +
                                "JSON.stringify(arr), " +
                                "Array.from(selectedRows || []).join(',')" +
                                ");" +
                                "} else {" +
                                "alert('Image sharing is unavailable');" +
                                "}" +
                                "}"
                    )


                    sb.append(
                        "function editCell(cell){" +
                                "var oldVal=cell.getAttribute('data-raw')||cell.innerText||'';" +
                                "var newVal=prompt('ਸੈੱਲ ਦਾ ਡਾਟਾ ਲਿਖੋ:',oldVal);" +
                                "if(newVal!==null){" +
                                "var rR=parseInt(cell.getAttribute('data-r'),10),cC=parseInt(cell.getAttribute('data-c'),10);" +
                                "if(!isNaN(rR)&&!isNaN(cC)&&rR>=0&&cC>=0){" +
                                "cell.setAttribute('data-raw',newVal);cell.innerText=newVal;" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.updateCellData==='function'){" +
                                "window.AndroidBridge.updateCellData(rR,cC,newVal);}" +
                                "calculateAndNotify();}}}"
                    )

                    sb.append(
                        "var refColors=['#2196F3','#FF5722','#4CAF50','#9C27B0','#FF9800','#00BCD4'];" +
                                "function highlightFormulaReferences(formulaStr){" +
                                "document.querySelectorAll('.formula-ref-box').forEach(function(el){" +
                                "el.classList.remove('formula-ref-box');el.style.outline='';});" +
                                "if(!formulaStr||!formulaStr.startsWith('='))return;" +
                                "var expr=formulaStr.substring(1);" +
                                "var rangeRegex=/([A-Za-z]+)(\\d+):([A-Za-z]+)(\\d+)/g;" +
                                "var singleRefRegex=/([A-Za-z]+)(\\d+)/g;" +
                                "var colorIdx=0,match;" +
                                "while((match=rangeRegex.exec(expr))!==null){" +
                                "var sC=colStrToIdx(match[1]),sR=parseInt(match[2])-1;" +
                                "var eC=colStrToIdx(match[3]),eR=parseInt(match[4])-1;" +
                                "var color=refColors[colorIdx%refColors.length];colorIdx++;" +
                                "for(var r=Math.min(sR,eR);r<=Math.max(sR,eR);r++){" +
                                "for(var c=Math.min(sC,eC);c<=Math.max(sC,eC);c++){" +
                                "var cell=getCellByPos(r,c);if(cell){" +
                                "cell.classList.add('formula-ref-box');" +
                                "cell.style.outline='2px solid '+color;cell.style.outlineOffset='-2px';}}}}" +
                                "var cleanExpr=expr.replace(/([A-Za-z]+)(\\d+):([A-Za-z]+)(\\d+)/g,'');" +
                                "while((match=singleRefRegex.exec(cleanExpr))!==null){" +
                                "var c=colStrToIdx(match[1]),r=parseInt(match[2])-1;" +
                                "var color=refColors[colorIdx%refColors.length];colorIdx++;" +
                                "var cell=getCellByPos(r,c);if(cell){" +
                                "cell.classList.add('formula-ref-box');" +
                                "cell.style.outline='2px solid '+color;cell.style.outlineOffset='-2px';}}}"
                    )

                    sb.append(
                        "function colIdxToName(c){var s='';while(c>=0){s=String.fromCharCode(65+(c%26))+s;c=Math.floor(c/26)-1;}return s;}" +
                                "function updateActiveCellFormulaToBridge(cell){if(!cell)return;" +
                                "var f=cell.getAttribute('data-formula')||'';" +
                                "var raw=cell.getAttribute('data-raw')||'';" +
                                "var txt=cell.innerText||cell.textContent||'';" +
                                "var value=(f&&f.startsWith('='))?f:((raw&&raw.startsWith('='))?raw:(f?f:(raw?raw:txt)));" +
                                "if(value&&value.startsWith('=')){highlightFormulaReferences(value);}else{highlightFormulaReferences('');}" +
                                "var r=parseInt(cell.getAttribute('data-r'),10);" +
                                "var c=parseInt(cell.getAttribute('data-c'),10);" +
                                "var ref='';if(!isNaN(r)&&!isNaN(c)){ref=colIdxToName(c)+(r+1);}" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.updateActiveCellFormula==='function'){" +
                                "window.AndroidBridge.updateActiveCellFormula(value,ref);}}"
                    )

                    sb.append(
                        "var lastSelectedCellKey='';" +
                                "var lastTapTime=0,lastTapCellKey='';" +
                                "function toggleCell(cell,ev){" +
                                "if(ev)ev.stopPropagation();" +
                                "var r=parseInt(cell.dataset.r,10),c=parseInt(cell.dataset.c,10);" +
                                "var cellKey=r+':'+c;" +
                                "var now=new Date().getTime();" +
                                "if(lastTapCellKey===cellKey&&(now-lastTapTime<350)){" +
                                "lastTapTime=0;lastTapCellKey='';" +
                                "editCellInline(cell);return;}" +
                                "lastTapTime=now;lastTapCellKey=cellKey;" +
                                "if(lastSelectedCellKey===cellKey&&selectedCells.has(cell)){" +
                                "openCellContextMenu(cell,ev);return;}" +
                                "lastSelectedCellKey=cellKey;" +
                                "highlightActiveRowCol(r,c);" +
                                "if(!ev||(!ev.shiftKey&&!ev.ctrlKey&&!ev.metaKey)){" +
                                "selectedCells.forEach(function(prev){prev.classList.remove('selected-cell');});" +
                                "selectedCells.clear();selectedRows.clear();selectedColumns.clear();}" +
                                "selectedCells.add(cell);cell.classList.add('selected-cell');" +
                                "highlightFormulaReferences('');" +
                                "var displayVal=(cell.innerText||cell.textContent||cell.getAttribute('data-raw')||'').trim();" +
                                "var colLetter=colIdxToName(c);" +
                                "var ref=colLetter+(r+1);" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.updateActiveCellFormula==='function'){" +
                                "window.AndroidBridge.updateActiveCellFormula(displayVal,ref);}" +
                                "var m=document.getElementById('selection_context_menu');" +
                                "var drop=document.getElementById('context_more_menu');" +
                                "if(m)m.style.display='none';if(drop)drop.style.display='none';" +
                                "if(ev&&ev.shiftKey){editCell(cell);return;}" +
                                "calculateAndNotifySilent();}"
                    )

                    sb.append(
                        "function formatDateToNumeric(str){" +
                                "if(!str)return str;" +
                                "var s=String(str).trim();" +
                                "var hasQuote=s.startsWith(\"'\");" +
                                "if(hasQuote)s=s.substring(1).trim();" +
                                "var months={'jan':'01','feb':'02','mar':'03','apr':'04','may':'05','jun':'06','jul':'07','aug':'08','sep':'09','sept':'09','oct':'10','nov':'11','dec':'12'};" +
                                "var m=s.match(/^(\\d{1,2})[-/.]?([A-Za-z]{3,9})[-/.]?(\\d{2,4})$/);" +
                                "if(m){var day=m[1].length===1?'0'+m[1]:m[1];var monKey=m[2].toLowerCase();var mon=months[monKey]||'01';var yr=m[3].length===2?'20'+m[3]:m[3];return(hasQuote?\"'\":'')+day+'/'+mon+'/'+yr;}" +
                                "var m2=s.match(/^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})$/);" +
                                "if(m2){var day=m2[1].length===1?'0'+m2[1]:m2[1];var mon=m2[2].length===1?'0'+m2[2]:m2[2];var yr=m2[3].length===2?'20'+m2[3]:m2[3];return(hasQuote?\"'\":'')+day+'/'+mon+'/'+yr;}" +
                                "return str;}" +
                                "function handleCellDblClick(cell,ev){" +
                                "if(ev)ev.stopPropagation();" +
                                "editCellInline(cell);}" +
                                "function editCellInline(cell){" +
                                "var oldVal=cell.getAttribute('data-raw')||cell.innerText||'';" +
                                "var numericDateVal=formatDateToNumeric(oldVal);" +
                                "var textToEdit=numericDateVal.startsWith(\"'\")?numericDateVal.substring(1):numericDateVal;" +
                                "cell.innerText=textToEdit;" +
                                "cell.contentEditable='true';cell.focus();" +
                                "var range=document.createRange();range.selectNodeContents(cell);" +
                                "var sel=window.getSelection();sel.removeAllRanges();sel.addRange(range);" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.openKeyboard==='function'){" +
                                "window.AndroidBridge.openKeyboard();}" +
                                "function onFinishInlineEdit(e){" +
                                "if(e.type==='keydown'&&e.key!=='Enter')return;" +
                                "if(e.type==='keydown')e.preventDefault();" +
                                "cell.contentEditable='false';" +
                                "cell.removeEventListener('blur',onFinishInlineEdit);" +
                                "cell.removeEventListener('keydown',onFinishInlineEdit);" +
                                "var newVal=(cell.innerText||cell.textContent||'').trim();" +
                                "var formattedNewVal=(isStringAlreadyDateFormat(newVal)&&!newVal.startsWith(\"'\"))?\"'\"+newVal:newVal;" +
                                "var rR=parseInt(cell.getAttribute('data-r'),10),cC=parseInt(cell.getAttribute('data-c'),10);" +
                                "if(!isNaN(rR)&&!isNaN(cC)&&rR>=0&&cC>=0){" +
                                "cell.setAttribute('data-raw',formattedNewVal);" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.updateCellData==='function'){" +
                                "window.AndroidBridge.updateCellData(rR,cC,formattedNewVal);}}" +
                                "calculateAndNotify();}" +
                                "cell.addEventListener('blur',onFinishInlineEdit);" +
                                "cell.addEventListener('keydown',onFinishInlineEdit);}"
                    )

                    sb.append(
                        "function updateCellDomDirectly(rIdx,cIdx,valStr){" +
                                "var cell=getCellByPos(rIdx,cIdx);" +
                                "if(cell){cell.setAttribute('data-raw',valStr);" +
                                "if(valStr.startsWith('=')){cell.setAttribute('data-formula',valStr);" +
                                "var res=evalFormula(valStr);" +
                                "cell.innerText=(res!==null&&res!==undefined)?String(res):valStr;" +
                                "}else{cell.setAttribute('data-formula','');" +
                                "cell.innerText=valStr.startsWith(\"'\")?valStr.substring(1):valStr;}" +
                                "calculateAndNotifySilent();}}"
                    )

                    sb.append(
                        "function openCellContextMenu(cell,ev){" +
                                "if(ev)ev.stopPropagation();" +
                                "updateActiveCellFormulaToBridge(cell);" +
                                "updateContextMenuPosition();}"
                    )

                    sb.append(
                        "function toggleRow(rIdx,tr){" +
                                "highlightActiveRowCol(rIdx,-1);" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.showRowMenu==='function'){" +
                                "window.AndroidBridge.showRowMenu(rIdx);}}"
                    )

                    sb.append(
                        "function toggleColumnSelect(cIdx){" +
                                "highlightActiveRowCol(-1,cIdx);" +
                                "var th=document.querySelector('th[data-col=\"'+cIdx+'\"]');" +
                                "var cells=document.querySelectorAll('td.excel-cell[data-c=\"'+cIdx+'\"]');" +
                                "if(selectedColumns.has(cIdx)){" +
                                "selectedColumns.delete(cIdx);" +
                                "if(th)th.classList.remove('selected-col');" +
                                "cells.forEach(function(c){c.classList.remove('selected-cell');selectedCells.delete(c);});" +
                                "}else{" +
                                "selectedColumns.add(cIdx);" +
                                "if(th)th.classList.add('selected-col');" +
                                "cells.forEach(function(c){c.classList.add('selected-cell');selectedCells.add(c);});}" +
                                "calculateAndNotify();}"
                    )

                    sb.append(
                        "function deleteRowJs(rIdx,ev){if(ev)ev.stopPropagation();" +
                                "if(window.AndroidBridge)window.AndroidBridge.deleteRowFromJs(rIdx);}"
                    )
                    sb.append(
                        "function deleteColJs(cIdx,ev){if(ev)ev.stopPropagation();" +
                                "if(window.AndroidBridge)window.AndroidBridge.deleteColFromJs(cIdx);}"
                    )
                    sb.append(
                        "function openColMenu(i,e){if(e)e.stopPropagation();" +
                                "if(window.AndroidBridge)window.AndroidBridge.showColumnMenu(i);}"
                    )
                    sb.append(
                        "function openRowMenu(i,e){if(e)e.stopPropagation();" +
                                "if(window.AndroidBridge)window.AndroidBridge.showRowMenu(i);}"
                    )

                    sb.append(
                        "function applyFreezePanes(frozenCount){" +
                                "document.querySelectorAll('.frozen-col-cell').forEach(function(el){" +
                                "el.classList.remove('frozen-col-cell');el.style.position='';" +
                                "el.style.left='';el.style.zIndex='';el.style.borderRight='';" +
                                "el.style.boxShadow='';});" +
                                "if(!frozenCount||frozenCount<=0)return;" +
                                "var rowNumTh=document.querySelector('th.row-num');" +
                                "var baseLeft=rowNumTh?rowNumTh.offsetWidth:60;" +
                                "for(var c=0;c<frozenCount;c++){" +
                                "var th=document.querySelector('thead th[data-col=\"'+c+'\"]');" +
                                "var colW=th?th.offsetWidth:100;" +
                                "var selector='th[data-col=\"'+c+'\"],td[data-c=\"'+c+'\"]';" +
                                "document.querySelectorAll(selector).forEach(function(cell){" +
                                "cell.classList.add('frozen-col-cell');cell.style.position='sticky';" +
                                "cell.style.left=baseLeft+'px';" +
                                "if(cell.tagName==='TH'){cell.style.zIndex='35';}else{cell.style.zIndex='15';}" +
                                "if(c===frozenCount-1){cell.style.borderRight='2px solid #2E7D32';" +
                                "cell.style.boxShadow='2px 0 6px rgba(0,0,0,0.25)';}});" +
                                "baseLeft+=colW;}}"
                    )

                    sb.append(
                        "function colStrToIdx(s){if(!s)return 0;var n=0;" +
                                "s=s.toUpperCase();for(var i=0;i<s.length;i++){" +
                                "n=n*26+s.charCodeAt(i)-64;}return n-1;}"
                    )
                    sb.append(
                        "function getCellByPos(r,c){return document.querySelector(" +
                                "'td.excel-cell[data-r=\"'+r+'\"][data-c=\"'+c+'\"]');}"
                    )

                    sb.append(
                        "function evalFormula(f){" +
                                "if(!f||typeof f!=='string'||!f.startsWith('='))return f;" +
                                "var expr=f.substring(1).trim().toUpperCase();" +
                                "var m=expr.match(/^(SUM|AVERAGE|AVG|MIN|MAX|COUNT)\\(([A-Z]+)(\\d+):([A-Z]+)(\\d+)\\)$/);" +
                                "if(m){var fn=m[1],sc=colStrToIdx(m[2]),sr=parseInt(m[3])-1," +
                                "ec=colStrToIdx(m[4]),er=parseInt(m[5])-1,vals=[];" +
                                "for(var r=Math.min(sr,er);r<=Math.max(sr,er);r++){" +
                                "for(var c=Math.min(sc,ec);c<=Math.max(sc,ec);c++){" +
                                "var cell=getCellByPos(r,c);if(cell){" +
                                "var v=parseCellVal(cell.getAttribute('data-raw')||cell.innerText);" +
                                "if(v!==null)vals.push(v);}}}" +
                                "if(fn==='SUM')return vals.reduce(function(a,b){return a+b;},0);" +
                                "if(fn==='COUNT')return vals.length;" +
                                "if(fn==='MIN')return vals.length?Math.min.apply(null,vals):0;" +
                                "if(fn==='MAX')return vals.length?Math.max.apply(null,vals):0;" +
                                "return vals.length?vals.reduce(function(a,b){return a+b;},0)/vals.length:0;}" +
                                "var me=expr.replace(/([A-Z]+)(\\d+)/g,function(_,cs,rs){" +
                                "var cell=getCellByPos(parseInt(rs)-1,colStrToIdx(cs));" +
                                "var v=cell?parseCellVal(cell.getAttribute('data-raw')||cell.innerText):0;" +
                                "return v===null||v===undefined?'0':v;});" +
                                "try{var out=Function('return ('+me+')')();" +
                                "return typeof out==='number'&&isFinite(out)?out:f;}catch(e){return f;}}"
                    )

                    sb.append(
                        "function applyFormulaToSelection(f){" +
                                "selectedCells.forEach(function(cell){" +
                                "var result=evalFormula(f);" +
                                "cell.setAttribute('data-formula',f);" +
                                "cell.setAttribute('data-raw',String(result));cell.innerText=String(result);" +
                                "if(window.AndroidBridge)window.AndroidBridge.updateCellData(" +
                                "parseInt(cell.dataset.r),parseInt(cell.dataset.c),String(result));});" +
                                "calculateAndNotify();}"
                    )

                    sb.append(
                        "function setColumnWidth(cIdx,widthPx){" +
                                "var th=document.querySelector('th[data-col=\"'+cIdx+'\"]');" +
                                "var cells=document.querySelectorAll('td.excel-cell[data-c=\"'+cIdx+'\"]');" +
                                "var w=widthPx==='auto'?'auto':Math.max(25,parseInt(widthPx,10)||80)+'px';" +
                                "if(th){th.style.width=w;th.style.minWidth=widthPx==='auto'?'35px':w;" +
                                "th.style.maxWidth=widthPx==='auto'?'none':w;th.style.overflow='hidden';}" +
                                "cells.forEach(function(c){c.style.width=w;" +
                                "c.style.minWidth=widthPx==='auto'?'35px':w;" +
                                "c.style.maxWidth=widthPx==='auto'?'none':w;" +
                                "c.style.overflow='hidden';c.style.textOverflow='ellipsis';c.style.whiteSpace='nowrap';});" +
                                "if(typeof widthPx==='number'&&window.AndroidBridge&&typeof window.AndroidBridge.saveJsColumnWidth==='function'){" +
                                "window.AndroidBridge.saveJsColumnWidth(cIdx,widthPx);}" +
                                "if(window.currentFrozenColCount>0&&typeof applyFreezePanes==='function'){" +
                                "applyFreezePanes(window.currentFrozenColCount,window.currentFrozenRowCount);}}"
                    )
                    sb.append(
                        "function setRowHeight(rIdx,heightPx){" +
                                "var tr=document.getElementById('tr_'+rIdx);" +
                                "var h=heightPx==='auto'?'auto':Math.max(18,parseInt(heightPx)||30)+'px';" +
                                "if(tr){tr.style.height=h;tr.style.maxHeight=h;" +
                                "tr.querySelectorAll('td,th').forEach(function(td){" +
                                "td.style.height=h;td.style.maxHeight=h;td.style.overflow='hidden';});}}"
                    )

                    sb.append(
                        "function initResizers(){" +
                                "document.querySelectorAll('.resizer-col').forEach(function(handle){" +
                                "var cIdx=parseInt(handle.dataset.col),th=handle.parentNode,startX=0,startW=0;" +
                                "function down(e){e.stopPropagation();if(e.preventDefault)e.preventDefault();" +
                                "startX=e.touches?e.touches[0].clientX:e.clientX;startW=th.offsetWidth;" +
                                "handle.classList.add('dragging');" +
                                "function move(ev){var x=ev.touches?ev.touches[0].clientX:ev.clientX;" +
                                "setColumnWidth(cIdx,Math.max(35,startW+x-startX));" +
                                "if(ev.preventDefault)ev.preventDefault();}" +
                                "function up(){handle.classList.remove('dragging');" +
                                "document.removeEventListener('touchmove',move);" +
                                "document.removeEventListener('touchend',up);" +
                                "document.removeEventListener('mousemove',move);" +
                                "document.removeEventListener('mouseup',up);}" +
                                "document.addEventListener('touchmove',move,{passive:false});" +
                                "document.addEventListener('touchend',up);" +
                                "document.addEventListener('mousemove',move);" +
                                "document.addEventListener('mouseup',up);}" +
                                "handle.addEventListener('touchstart',down,{passive:false});" +
                                "handle.addEventListener('moused own',down);});" +
                                "document.querySelectorAll('.resizer-row').forEach(function(handle){" +
                                "var rIdx=parseInt(handle.dataset.row),tr=document.getElementById('tr_'+rIdx);" +
                                "var startY=0,startH=30;" +
                                "function down(e){e.stopPropagation();if(e.preventDefault)e.preventDefault();" +
                                "startY=e.touches?e.touches[0].clientY:e.clientY;" +
                                "startH=tr?tr.offsetHeight:30;handle.classList.add('dragging');" +
                                "function move(ev){var y=ev.touches?ev.touches[0].clientY:ev.clientY;" +
                                "setRowHeight(rIdx,Math.max(18,startH+y-startY));" +
                                "if(ev.preventDefault)ev.preventDefault();}" +
                                "function up(){handle.classList.remove('dragging');" +
                                "document.removeEventListener('touchmove',move);" +
                                "document.removeEventListener('touchend',up);" +
                                "document.removeEventListener('mousemove',move);" +
                                "document.removeEventListener('mouseup',up);}" +
                                "document.addEventListener('touchmove',move,{passive:false});" +
                                "document.addEventListener('touchend',up);" +
                                "document.addEventListener('mousemove',move);" +
                                "document.addEventListener('mouseup',up);}" +
                                "handle.addEventListener('touchstart',down,{passive:false});" +
                                "handle.addEventListener('mousedown',down);});}"
                    )

                    sb.append(
                        "var currentTableScale=$currentZoomScale,initialPinchDist=0,initialScaleOnPinch=1.0,pinchStartScrollLeft=0,pinchStartScrollTop=0;" +
                                "function applySavedZoomScale(scale){" +
                                "if(scale&&scale>=0.15&&scale<=3.0){currentTableScale=scale;" +
                                "var tbl=document.getElementById('excelTable');" +
                                "if(tbl){tbl.style.transformOrigin='0 0';tbl.style.webkitTransformOrigin='0 0';tbl.style.zoom=scale;}}}" +
                                "document.addEventListener('touchstart',function(e){" +
                                "if(e.touches&&e.touches.length===2){" +
                                "initialPinchDist=Math.hypot(e.touches[0].pageX-e.touches[1].pageX,e.touches[0].pageY-e.touches[1].pageY);" +
                                "initialScaleOnPinch=currentTableScale;" +
                                "var scroller=document.getElementById('excelScroll');" +
                                "if(scroller){pinchStartScrollLeft=scroller.scrollLeft;pinchStartScrollTop=scroller.scrollTop;}}},{passive:false});" +
                                "document.addEventListener('touchmove',function(e){" +
                                "if(e.touches&&e.touches.length===2&&initialPinchDist>0){" +
                                "if(e.cancelable)e.preventDefault();" +
                                "var dist=Math.hypot(e.touches[0].pageX-e.touches[1].pageX,e.touches[0].pageY-e.touches[1].pageY);" +
                                "var factor=dist/initialPinchDist;" +
                                "var newScale=Math.max(0.15,Math.min(3.0,initialScaleOnPinch*factor));" +
                                "currentTableScale=newScale;" +
                                "var tbl=document.getElementById('excelTable');" +
                                "var scroller=document.getElementById('excelScroll');" +
                                "if(tbl){tbl.style.transformOrigin='0 0';tbl.style.webkitTransformOrigin='0 0';tbl.style.zoom=newScale;}" +
                                "if(scroller){scroller.scrollLeft=pinchStartScrollLeft;scroller.scrollTop=pinchStartScrollTop;}" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.saveZoomScale==='function')window.AndroidBridge.saveZoomScale(newScale);}},{passive:false});" +
                                "document.addEventListener('touched',function(e){" +
                                "if(!e.touches||e.touches.length<2){" +
                                "initialPinchDist=0;" +
                                "var tbl=document.getElementById('excelTable');" +
                                "var scroller=document.getElementById('excelScroll');" +
                                "if(tbl&&scroller){tbl.style.transformOrigin='0 0';tbl.style.webkitTransformOrigin='0 0';tbl.style.zoom=currentTableScale;" +
                                "scroller.dispatchEvent(new Event('scroll'));" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.saveZoomScale==='function')window.AndroidBridge.saveZoomScale(currentTableScale);}}},{passive:true});"
                    )

                    sb.append(
                        "var lastScrollTop=0,lastScrollLeft=0;document.addEventListener('scroll',function(e){" +
                                "var scroller=document.getElementById('excelScroll');" +
                                "if(scroller){var sx=scroller.scrollLeft,sy=scroller.scrollTop;" +
                                "if(Math.abs(sx-lastScrollLeft)>5||Math.abs(sy-lastScrollTop)>5){" +
                                "lastScrollLeft=sx;lastScrollTop=sy;" +
                                "if(window.AndroidBridge&&typeof window.AndroidBridge.saveScrollPosition==='function'){" +
                                "window.AndroidBridge.saveScrollPosition(sx,sy);}}}" +
                                "var el=e.target;if(el&&el.scrollTop!==undefined){" +
                                "var st=el.scrollTop;if(Math.abs(st-lastScrollTop)>25){" +
                                "if(st>lastScrollTop&&st>80){if(window.AndroidBridge)window.AndroidBridge.onScrollDirectionChanged(true);}" +
                                "else if(st<lastScrollTop){if(window.AndroidBridge)window.AndroidBridge.onScrollDirectionChanged(false);}" +
                                "lastScrollTop=st;}}},true);"
                    )

                    sb.append(
                        "function evaluateAllFormulasOnPage(){" +
                                "var cells=document.querySelectorAll('td.excel-cell');" +
                                "cells.forEach(function(cell){" +
                                "var f=cell.getAttribute('data-formula')||'';" +
                                "var raw=cell.getAttribute('data-raw')||'';" +
                                "var formulaToEval=f.startsWith('=')?f:(raw.startsWith('=')?raw:'');" +
                                "if(formulaToEval){" +
                                "cell.setAttribute('data-formula',formulaToEval);" +
                                "var res=evalFormula(formulaToEval);" +
                                "if(res!==null&&res!==undefined){" +
                                "cell.setAttribute('data-raw',String(res));" +
                                "cell.innerText=String(res);}}});}"
                    )

                    sb.append(
                        "function connectFormulaStripEvents(){" +
                                "document.addEventListener('click',function(e){if(e&&e.target){" +
                                "var cell=e.target.closest('td[data-r][data-c],[data-r][data-c].excel-cell');" +
                                "if(cell){setTimeout(function(){updateActiveCellFormulaToBridge(cell);},50);}}});" +
                                "document.addEventListener('focusin',function(e){if(e&&e.target){" +
                                "var cell=e.target.closest('td[data-r][data-c],[data-r][data-c].excel-cell');" +
                                "if(cell){updateActiveCellFormulaToBridge(cell);}}});" +
                                "document.addEventListener('input',function(e){if(e&&e.target){" +
                                "var cell=e.target.closest('td[data-r][data-c],[data-r][data-c].excel-cell');" +
                                "if(cell){updateActiveCellFormulaToBridge(cell);}}});}" +
                                "if(document.readyState==='loading'){" +
                                "document.addEventListener('DOMContentLoaded',connectFormulaStripEvents);" +
                                "}else{connectFormulaStripEvents();}"
                    )

                    sb.append(
                        "function scrollToSearchMatch(){setTimeout(function(){" +
                                "var el=document.getElementById('first_search_match')||document.querySelector('.highlight');" +
                                "if(el)el.scrollIntoView({behavior:'smooth',block:'center',inline:'center'});" +
                                "},100);}"
                    )

                    sb.append("</script></head><body>")

                    sb.append(
                        "<div id='selection_context_menu' class='context-popup' style='display:none;'>" +
                                "<button onclick='jsCutSelection()'>✂️ Cut</button>" +
                                "<button onclick='jsCopySelection()'>📋 Copy</button>" +
                                "<button onclick='jsPasteSelection()'>📋 Paste</button>" +
                                "<button onclick='toggleMoreMenu(event)' style='color:#81C784;'>⋮ ਹੋਰ</button>" +
                                "<div id='context_more_menu' class='context-dropdown' style='display:none;'>" +
                                "<button onclick='jsEditSelection()'>✏️ Edit (ਸੈੱਲ ਬਦਲੋ)</button>" +
                                "<button onclick='jsFillSelection()'>🎨 Fill (ਸਾਰਿਆਂ 'ਚ ਭਰੋ)</button>" +
                                "<button onclick='jsClearSelection()'>🧹 Clear (ਸਾਫ਼ ਕਰੋ)</button>" +
                                "<button onclick='jsUndo()'>↺ Undo (ਵਾਪਸ ਲਿਆਓ)</button>" +
                                "<button onclick='jsShareAsImage()'>🖼️ Share as Image (ਫੋਟੋ ਭੇਜੋ)</button>" +
                                "</div></div>"
                    )

                    // Scroll container: header and row numbers remain sticky.
                    sb.append("<div id='excelScroll'><table id='excelTable'>")

                    if (maxCols > 0) {
                        sb.append("<thead><tr><th class='row-num'>#</th>")

                        for (c in 0 until maxCols) {
                            if (hiddenColumnIndices.contains(c)) continue

                            val isFiltered =
                                c == activeColumnFilterIndex && activeColumnFilterSet.isNotEmpty()
                            val isSelected = selectedColIndices.contains(c)

                            val thClass = when {
                                isFiltered -> " class='active-filter-th'"
                                isSelected -> " class='selected-col'"
                                else -> ""
                            }

                            val arrowClass =
                                if (isFiltered) "filter-arrow active-arrow" else "filter-arrow"

                            // Excel-style column letters: A, B, C ... Z, AA, AB ...
                            val label = getColumnLabel(c)

                            sb.append("<th").append(thClass)
                                .append(" data-col='").append(c)
                                .append("' onclick='openColMenu(").append(c).append(",event)'>")

                            sb.append(escapeHtml(label))
                            sb.append("<span class='").append(arrowClass).append("'>&#9660;</span>")
                            sb.append(
                                "<div class='resizer-col' data-col='$c' " +
                                        "onclick='event.stopPropagation()'></div></th>"
                            )
                        }

                        sb.append("</tr></thead>")
                    }

                    sb.append("<tbody>")

                    for (rIdx in 0 until maxRowsToShow) {
                        if (hiddenRowIndices.contains(rIdx)) continue

                        val row = sheetRows.getOrNull(rIdx) ?: continue

                        if (
                            activeColumnFilterIndex != -1 &&
                            activeColumnFilterSet.isNotEmpty() &&
                            rIdx > 0
                        ) {
                            val cellVal =
                                row.getOrNull(activeColumnFilterIndex)?.trim().orEmpty()
                            val checkVal = if (cellVal.isNotEmpty()) cellVal else "(Blank)"
                            if (!activeColumnFilterSet.contains(checkVal)) continue
                        }

                        sb.append("<tr id='tr_").append(rIdx).append("'>")
                        sb.append(
                            "<td class='row-num' onclick='toggleRow($rIdx,this.parentNode)' " +
                                    "oncontextmenu='openRowMenu($rIdx,event)'>"
                        )
                        sb.append(rIdx + 1)
                        sb.append("<div class='resizer-row' data-row='$rIdx' " +
                                "onclick='event.stopPropagation()'></div></td>")

                        for (c in 0 until maxCols) {
                            if (hiddenColumnIndices.contains(c)) continue

                            val isDateCol = dateColIndicesData.contains(c)
                            val rawVal = row.getOrNull(c)?.trim().orEmpty()

                            var cleanVal = rawVal
                            var bgCss = ""
                            var fgCss = ""
                            if (cleanVal.startsWith("[[STYLE:")) {
                                val endIdx = cleanVal.indexOf("]]")
                                if (endIdx > 0) {
                                    val styleStr = cleanVal.substring(8, endIdx)
                                    val parts = styleStr.split(":")
                                    var bg = parts.getOrNull(0).orEmpty()
                                    var fg = parts.getOrNull(1).orEmpty()

                                    if (!isDarkMode) {
                                        if (bg.equals("#000000", true) || bg.equals("#000", true) || bg.equals("#111111", true) || bg.equals("#121212", true) || bg.equals("#1A1A1A", true)) {
                                            bg = ""
                                        }
                                    } else {
                                        if (bg.equals("#FFFFFF", true) || bg.equals("#FFF", true) || bg.equals("#F8F8F8", true)) {
                                            bg = ""
                                        }
                                        if (fg.isEmpty() || fg.equals("#000000", true) || fg.equals("#000", true) || fg.equals("#111111", true) || fg.equals("#222222", true)) {
                                            fg = "#E0E0E0"
                                        }
                                    }

                                    if (bg.isNotEmpty()) bgCss = "background-color: $bg !important;"
                                    if (fg.isNotEmpty()) fgCss = "color: $fg !important;"
                                    cleanVal = cleanVal.substring(endIdx + 2)
                                }
                            }
                            val cellStyleAttr = if (bgCss.isNotEmpty() || fgCss.isNotEmpty()) " style='$bgCss $fgCss'" else ""

                            val formattedVal = formatRawStringIfDateSerial(cleanVal, isDateCol)
                            val textForDisplay = if (formattedVal.startsWith("'")) formattedVal.drop(1) else formattedVal

                            val formulaKey = "$sheetName:$rIdx:$c"
                            val actualFormula = cellFormulasMap[formulaKey] ?: if (textForDisplay.startsWith("=")) textForDisplay else null
                            val formulaAttr = if (!actualFormula.isNullOrEmpty()) " data-formula='${escapeHtml(actualFormula)}'" else ""

                            val displayVal = if (
                                query.isNotEmpty() &&
                                textForDisplay.contains(query, ignoreCase = true)
                            ) {
                                val start = textForDisplay.indexOf(query, ignoreCase = true)
                                val end = start + query.length
                                val before = escapeHtml(textForDisplay.substring(0, start))
                                val match = escapeHtml(textForDisplay.substring(start, end))
                                val after = escapeHtml(textForDisplay.substring(end))
                                "$before<span class='highlight'>$match</span>$after"
                            } else {
                                escapeHtml(textForDisplay)
                            }

                            val isMatch = lowerQuery.isNotEmpty() &&
                                    formattedVal.lowercase(Locale.ROOT).contains(lowerQuery)

                            val idAttr = if (isMatch && !firstMatchFound) {
                                firstMatchFound = true
                                " id='first_search_match'"
                            } else {
                                ""
                            }

                            sb.append("<td class='excel-cell'").append(cellStyleAttr).append(idAttr)
                            sb.append(" data-r='").append(rIdx)
                            sb.append("' data-c='").append(c)
                            sb.append("' data-raw='").append(escapeHtml(formattedVal))
                            sb.append("'").append(formulaAttr)
                            sb.append(" onclick='toggleCell(this,event)' ondblclick='handleCellDblClick(this,event)'>")
                            sb.append(if (displayVal.isEmpty()) "&nbsp;" else displayVal)
                            sb.append("</td>")
                        }

                        sb.append("</tr>")
                    }

                    sb.append("</tbody></table>")

                    if (totalRows > maxRowsToShow) {
                        sb.append(
                            "<div style='text-align:center;padding:16px;background:#1E1E1E;" +
                                    "margin-top:8px;'>"
                        )
                        sb.append(
                            "<p style='color:#AAA;margin:0 0 8px;font-size:13px;'>" +
                                    "ਕੁੱਲ $totalRows ਲਾਈਨਾਂ ਵਿੱਚੋਂ $maxRowsToShow ਦਿਖਾਈਆਂ ਗਈਆਂ ਹਨ</p>"
                        )
                        sb.append(
                            "<button onclick='if(window.AndroidBridge)window.AndroidBridge.loadMoreRows()' " +
                                    "style='background:#2E7D32;color:#FFFFFF;border:none;padding:8px 16px;" +
                                    "font-size:13px;font-weight:bold;border-radius:4px;cursor:pointer;'>" +
                                    "➕ ਹੋਰ 10,000 ਲਾਈਨਾਂ ਦੇਖੋ (+10,000 Rows)</button>"
                        )
                        sb.append("</div>")
                    }

                    // Close the scroll container after the table and Load More area.
                    sb.append("</div>")

                    val savedWidths = sheetColWidthsMap[activeSheetName] ?: emptyMap()
                    val customWidthsScriptSb = StringBuilder()
                    savedWidths.forEach { (cIdx, wPx) ->
                        customWidthsScriptSb.append("setColumnWidth($cIdx, $wPx);\n")
                    }

                    // Initialize resizers and restore scroll position after DOM creation.
                    sb.append(
                        "<script>window.currentFrozenColCount=$currentFrozenCount;" +
                                "window.currentFrozenRowCount=$currentFrozenRowCount;" +
                                "function applySavedColWidths(){ ${customWidthsScriptSb.toString()} }\n" +
                                "window.addEventListener('load',function(){" +
                                "applySavedColWidths();" +
                                "if(typeof evaluateAllFormulasOnPage==='function')evaluateAllFormulasOnPage();" +
                                "if(typeof initResizers==='function')initResizers();" +
                                "if(typeof applyFreezePanes==='function')applyFreezePanes($currentFrozenCount,$currentFrozenRowCount);" +
                                "if(typeof scrollToSearchMatch==='function')scrollToSearchMatch();" +
                                "var s=document.getElementById('excelScroll');" +
                                "if(s){s.scrollLeft=${pendingScrollX};s.scrollTop=${pendingScrollY};}" +
                                "});" +
                                "applySavedColWidths();" +
                                "if(typeof applyFreezePanes==='function')applyFreezePanes($currentFrozenCount,$currentFrozenRowCount);" +
                                "var sInst=document.getElementById('excelScroll');" +
                                "if(sInst){sInst.scrollLeft=${pendingScrollX};sInst.scrollTop=${pendingScrollY};}" +
                                "</script>"
                    )

                    sb.append("</body></html>")
                    val file = File(cacheDir, "excel_render_cache.html")
                    file.writeText(sb.toString(), Charsets.UTF_8)
                    file
                }

                webView.loadUrl("file://${htmlFile.absolutePath}")
            } catch (e: Throwable) {
                android.util.Log.e(
                    "ExcelViewer",
                    "renderSheetDataHtml failed for sheet: $sheetName",
                    e
                )

                val emptyHtml =
                    "<html><body style='background:#121212;color:#E0E0E0;" +
                            "font-family:sans-serif;text-align:center;padding:40px 16px;'>" +
                            "<div style='background:#1E1E1E;border:1px solid #333;" +
                            "border-radius:12px;padding:24px;display:inline-block;'>" +
                            "<h3 style='color:#FFC107;margin-bottom:8px;'>" +
                            "ਇਸ Sheet ਨੂੰ ਦਿਖਾਉਣ ਵਿੱਚ ਸਮੱਸਿਆ ਆਈ</h3>" +
                            "<p style='color:#AAA;font-size:13px;'>${
                                escapeHtml(e.message ?: "Unknown error")
                            }</p></div></body></html>"

                webView.loadDataWithBaseURL(
                    "https://excel.local/",
                    emptyHtml,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    }



    private fun showSelectionShareAndSaveDialog() {
        val options = arrayOf(
            "🖼️ ਫੋਟੋ ਬਣਾਓ ਤੇ ਫੋਨ ਗੈਲਰੀ 'ਚ ਸੇਵ ਕਰੋ (Save Image to Gallery)",
            "📄 PDF ਡੌਕੂਮੈਂਟ ਬਣਾਓ ਤੇ ਸੇਵ ਕਰੋ (Save as PDF Document - Zero MB Loss)",
            "📲 WhatsApp / ਹੋਰ ਐਪ 'ਤੇ ਭੇਜੋ (Share Image)",
            "📋 ਟੈਕਸਟ ਡਿਟੇਲ ਕਾਪੀ ਕਰੋ (Copy Text)"
        )

        AlertDialog.Builder(this)
            .setTitle("ਸਿਲੈਕਸ਼ਨ ਆਪਸ਼ਨ (Selection Action)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveSelectionSnapshotToGallery()
                    1 -> exportSelectionAsPdf()
                    2 -> webView.evaluateJavascript("jsShareAsImage();", null)
                    3 -> webView.evaluateJavascript("jsCopySelection();", null)
                }
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ (Cancel)", null)
            .show()
    }

    private fun exportSelectionAsPdf() {
        webView.evaluateJavascript(
            "(function(){var arr=[];selectedCells.forEach(function(c){arr.push({r:parseInt(c.dataset.r,10),c:parseInt(c.dataset.c,10),value:(c.innerText||c.getAttribute('data-raw')||'')});});return JSON.stringify(arr);})()",
            android.webkit.ValueCallback<String> { jsonStr ->
                try {
                    val cellsDataStr = jsonStr?.trim()?.removeSurrounding("\"", "\"")?.replace("\\\"", "\"") ?: ""
                    val jsonArray = org.json.JSONArray(cellsDataStr)
                    val selectedCells = mutableListOf<Triple<Int, Int, String>>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val r = obj.optInt("r", -1)
                        val c = obj.optInt("c", -1)
                        val value = obj.optString("value", "")
                        if (r >= 0 && c >= 0) selectedCells.add(Triple(r, c, value))
                    }

                    val uniqueCells = selectedCells.distinctBy { Pair(it.first, it.second) }
                    val selRows = if (uniqueCells.isNotEmpty()) uniqueCells.map { it.first }.distinct().sorted() else selectedRowIndices.sorted()
                    val selCols = if (uniqueCells.isNotEmpty()) uniqueCells.map { it.second }.distinct().sorted() else (0 until (currentSheetsData[activeSheetName]?.maxOfOrNull { it.size } ?: 10))
                    val cellMap = uniqueCells.associate { Pair(it.first, it.second) to it.third }

                    val sheet = activeSheet()
                    val headerRow = sheet?.getRow(0)
                    val rowsData = currentSheetsData[activeSheetName]

                    val headers = selCols.map { c ->
                        val hText = if (sheet != null) {
                            headerRow?.getCell(c)?.let { rawCellValue(it) }?.trim().orEmpty()
                        } else {
                            rowsData?.firstOrNull()?.getOrNull(c)?.stripStylePrefix()?.trim().orEmpty()
                        }
                        if (hText.isNotEmpty()) hText else getColumnLabel(c)
                    }

                    val rowDataList = selRows.map { r ->
                        val rowCells = selCols.mapIndexed { colIdx, c ->
                            val rawVal = cellMap[Pair(r, c)] ?: (if (sheet != null) sheet.getRow(r)?.getCell(c)?.let { rawCellValue(it) }?.trim().orEmpty() else rowsData?.getOrNull(r)?.getOrNull(c)?.stripStylePrefix()?.trim().orEmpty())
                            val hName = headers.getOrNull(colIdx)?.lowercase(Locale.ROOT).orEmpty()
                            val isDateCol = hName.contains("date") || hName == "dt" || hName.contains("dob") || hName.contains("expiry") || hName.contains("time") || hName.contains("ਤਾਰੀਖ") || lockedDateColsMap[activeSheetName]?.contains(c) == true || lockedDateColumnIndices.contains(c)
                            formatRawStringIfDateSerial(rawVal, isDateCol)
                        }
                        Pair(r + 1, rowCells)
                    }

                    val bitmap = generateNativeSelectionCardBitmap(headers, rowDataList)
                    val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
                    val pdfFileName = "Excel_Document_$timeStamp.pdf"

                    lifecycleScope.launch(Dispatchers.IO) {
                        val pdfFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), pdfFileName)
                        val pdfDoc = android.graphics.pdf.PdfDocument()
                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                        val page = pdfDoc.startPage(pageInfo)
                        val canvas = page.canvas
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                        pdfDoc.finishPage(page)

                        java.io.FileOutputStream(pdfFile).use { out ->
                            pdfDoc.writeTo(out)
                        }
                        pdfDoc.close()

                        withContext(Dispatchers.Main) {
                            val pdfUri = androidx.core.content.FileProvider.getUriForFile(this@ExcelViewerActivity, "${applicationContext.packageName}.fileprovider", pdfFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, pdfUri)
                                clipData = android.content.ClipData.newUri(contentResolver, "Excel PDF Document", pdfUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            Toast.makeText(this@ExcelViewerActivity, "📄 PDF ਬਣ ਗਈ ਹੈ! ($pdfFileName)", Toast.LENGTH_LONG).show()
                            startActivity(Intent.createChooser(shareIntent, "PDF ਡੌਕੂਮੈਂਟ ਸ਼ੇਅਰ ਕਰੋ (Share PDF)"))
                        }
                    }
                } catch (e: Throwable) {
                    Toast.makeText(this, "PDF ਬਣਾਉਣ ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun saveSelectionSnapshotToGallery() {
        webView.evaluateJavascript(
            "(function(){var arr=[];selectedCells.forEach(function(c){arr.push({r:parseInt(c.dataset.r,10),c:parseInt(c.dataset.c,10),value:(c.innerText||c.getAttribute('data-raw')||'')});});return JSON.stringify(arr);})()",
            android.webkit.ValueCallback<String> { jsonStr ->
                try {
                    val cellsDataStr = jsonStr?.trim()?.removeSurrounding("\"", "\"")?.replace("\\\"", "\"") ?: ""
                    val jsonArray = org.json.JSONArray(cellsDataStr)
                    val selectedCells = mutableListOf<Triple<Int, Int, String>>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val r = obj.optInt("r", -1)
                        val c = obj.optInt("c", -1)
                        val value = obj.optString("value", "")
                        if (r >= 0 && c >= 0) selectedCells.add(Triple(r, c, value))
                    }

                    if (selectedCells.isEmpty()) {
                        Toast.makeText(this, "ਸੇਵ ਕਰਨ ਲਈ ਕੋਈ ਸੈੱਲ ਸਿਲੈਕਟ ਨਹੀਂ ਹਨ", Toast.LENGTH_SHORT).show()
                        return@ValueCallback
                    }

                    val uniqueCells = selectedCells.distinctBy { Pair(it.first, it.second) }
                    val selRows = uniqueCells.map { it.first }.distinct().sorted()
                    val selCols = uniqueCells.map { it.second }.distinct().sorted()
                    val cellMap = uniqueCells.associate { Pair(it.first, it.second) to it.third }

                    val sheet = activeSheet()
                    val headerRow = sheet?.getRow(0)
                    val rowsData = currentSheetsData[activeSheetName]

                    val headers = selCols.map { c ->
                        val hText = if (sheet != null) {
                            headerRow?.getCell(c)?.let { rawCellValue(it) }?.trim().orEmpty()
                        } else {
                            rowsData?.firstOrNull()?.getOrNull(c)?.stripStylePrefix()?.trim().orEmpty()
                        }
                        if (hText.isNotEmpty()) hText else getColumnLabel(c)
                    }

                    val rowDataList = selRows.map { r ->
                        val rowCells = selCols.mapIndexed { colIdx, c ->
                            val rawVal = cellMap[Pair(r, c)] ?: (if (sheet != null) sheet.getRow(r)?.getCell(c)?.let { rawCellValue(it) }?.trim().orEmpty() else rowsData?.getOrNull(r)?.getOrNull(c)?.stripStylePrefix()?.trim().orEmpty())
                            val hName = headers.getOrNull(colIdx)?.lowercase(Locale.ROOT).orEmpty()
                            val isDateCol = hName.contains("date") || hName == "dt" || hName.contains("dob") || hName.contains("expiry") || hName.contains("time") || hName.contains("ਤਾਰੀਖ") || lockedDateColsMap[activeSheetName]?.contains(c) == true || lockedDateColumnIndices.contains(c)
                            formatRawStringIfDateSerial(rawVal, isDateCol)
                        }
                        Pair(r + 1, rowCells)
                    }

                    val bitmap = generateNativeSelectionCardBitmap(headers, rowDataList)
                    val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
                    val fileName = "Excel_Snapshot_$timeStamp.png"

                    lifecycleScope.launch(Dispatchers.IO) {
                        val savedUri = saveBitmapToMediaGallery(bitmap, fileName)
                        withContext(Dispatchers.Main) {
                            if (savedUri != null) {
                                showSavedPhotoPreviewDialog(bitmap, savedUri, fileName)
                            } else {
                                Toast.makeText(this@ExcelViewerActivity, "ਗੈਲਰੀ 'ਚ ਸੇਵ ਕਰਨ ਵਿੱਚ ਗਲਤੀ ਆਈ", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Toast.makeText(this, "ਗੈਲਰੀ 'ਚ ਸੇਵ ਗਲਤੀ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun saveBitmapToMediaGallery(bitmap: android.graphics.Bitmap, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Gurmukhi_Excel_Snapshots")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                uri
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "Gurmukhi_Excel_Snapshots").apply { if (!exists()) mkdirs() }
                val imageFile = File(appDir, fileName)
                java.io.FileOutputStream(imageFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                android.media.MediaScannerConnection.scanFile(this, arrayOf(imageFile.absolutePath), arrayOf("image/png"), null)
                Uri.fromFile(imageFile)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private fun showSavedPhotoPreviewDialog(bitmap: android.graphics.Bitmap, imageUri: Uri, fileName: String) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFF1E1E1E.toInt())
        }

        val tvInfo = TextView(this).apply {
            text = "✅ ਫੋਟੋ ਗੈਲਰੀ 'ਚ ਸੇਵ ਹੋ ਗਈ ਹੈ!\n($fileName)"
            textSize = 13f
            setTextColor(0xFF81C784.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(8, 8, 8, 16)
        }
        dialogView.addView(tvInfo)

        val imageView = android.widget.ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }

        val hScroll = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = false
            addView(imageView)
        }

        val vScroll = android.widget.ScrollView(this).apply {
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.55).toInt()
            )
            addView(hScroll)
        }
        dialogView.addView(vScroll)

        AlertDialog.Builder(this)
            .setTitle("🖼️ ਗੈਲਰੀ ਫੋਟੋ ਪ੍ਰੀਵਿਊ (Saved Photo)")
            .setView(dialogView)
            .setPositiveButton("📲 ਸ਼ੇਅਰ ਕਰੋ (Share)") { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    clipData = android.content.ClipData.newUri(contentResolver, "Excel Photo", imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "ਫੋਟੋ ਸ਼ੇਅਰ ਕਰੋ"))
            }
            .setNeutralButton("🖼️ ਗੈਲਰੀ ਐਪ ਵਿੱਚ ਦੇਖੋ") { _, _ ->
                try {
                    val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(imageUri, "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(galleryIntent)
                } catch (e: Throwable) {
                    Toast.makeText(this, "ਗੈਲਰੀ ਐਪ ਨਹੀਂ ਖੁੱਲ੍ਹ ਸਕੀ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ਬੰਦ ਕਰੋ (Close)", null)
            .show()
    }

    private fun generateNativeSelectionCardBitmap(
        headers: List<String>,
        rowDataList: List<Pair<Int, List<String>>>,
        pageTitleSuffix: String = ""
    ): android.graphics.Bitmap {
        val density = resources.displayMetrics.density.coerceAtLeast(1.5f)
        val numCols = headers.size.coerceAtLeast(1)

        val (cellTextSize, cellPaddingDp, baseColWidth) = when {
            numCols <= 4 -> Triple(15f, 12, 260)
            numCols <= 8 -> Triple(13f, 10, 200)
            numCols <= 15 -> Triple(11f, 8, 160)
            else -> Triple(10f, 6, 130)
        }

        val scaleFactor = (density * 2.0f).coerceIn(3.0f, 4.0f)
        val dynamicWidth = (numCols * baseColWidth * density + 140 * density).toInt().coerceAtLeast((1080 * density).toInt())

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt())
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }

        val matchWrapParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val cardInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrapParams
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1E1E1E.toInt())
                setStroke((3 * density).toInt(), 0xFF2E7D32.toInt())
                cornerRadius = 16f * density
            }
            background = drawable
        }

        val tvTitle = TextView(this).apply {
            layoutParams = matchWrapParams
            text = "📊 Excel Selection ($currentFileName)$pageTitleSuffix"
            textSize = cellTextSize + 2f
            setTextColor(0xFF00E676.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), (12 * density).toInt())
        }
        cardInner.addView(tvTitle)

        val tableLayout = android.widget.TableLayout(this).apply {
            layoutParams = matchWrapParams
            isStretchAllColumns = true
        }

        val rowParams = android.widget.TableLayout.LayoutParams(
            android.widget.TableLayout.LayoutParams.MATCH_PARENT,
            android.widget.TableLayout.LayoutParams.WRAP_CONTENT
        )

        val borderStrokePx = (1 * density).toInt().coerceAtLeast(1)

        fun createHeaderCellDrawable() = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF2B2B2B.toInt())
            setStroke(borderStrokePx, 0xFF555555.toInt())
        }

        fun createRowNumberCellDrawable() = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF222222.toInt())
            setStroke(borderStrokePx, 0xFF444444.toInt())
        }

        fun createDataCellDrawable() = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF1565C0.toInt())
            setStroke(borderStrokePx, 0xFF64B5F6.toInt())
        }

        val headerRow = android.widget.TableRow(this).apply {
            layoutParams = rowParams
        }

        val thNum = TextView(this).apply {
            text = " # "
            textSize = cellTextSize
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createHeaderCellDrawable()
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
        }
        headerRow.addView(thNum)

        for (header in headers) {
            val th = TextView(this).apply {
                text = header
                textSize = cellTextSize
                setTextColor(0xFF00E676.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = createHeaderCellDrawable()
                setPadding((cellPaddingDp * density).toInt(), (8 * density).toInt(), (cellPaddingDp * density).toInt(), (8 * density).toInt())
            }
            headerRow.addView(th)
        }
        tableLayout.addView(headerRow)

        for ((lineNum, rowValues) in rowDataList) {
            val tr = android.widget.TableRow(this).apply {
                layoutParams = rowParams
            }
            val tdNum = TextView(this).apply {
                text = lineNum.toString()
                textSize = cellTextSize
                setTextColor(0xFFE0E0E0.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = createRowNumberCellDrawable()
                setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            }
            tr.addView(tdNum)

            for (value in rowValues) {
                val cleanValue = value.stripStylePrefix()
                val td = TextView(this).apply {
                    text = cleanValue.ifEmpty { " " }
                    textSize = cellTextSize
                    setTextColor(0xFFFFFFFF.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    background = createDataCellDrawable()
                    setPadding((cellPaddingDp * density).toInt(), (8 * density).toInt(), (cellPaddingDp * density).toInt(), (8 * density).toInt())
                }
                tr.addView(td)
            }
            tableLayout.addView(tr)
        }

        cardInner.addView(tableLayout)

        val tvFooter = TextView(this).apply {
            layoutParams = matchWrapParams
            text = "Gurmukhi Keyboard App"
            textSize = (cellTextSize - 2f).coerceAtLeast(9f)
            setTextColor(0xFFAAAAAA.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.END
            setPadding(0, (12 * density).toInt(), (8 * density).toInt(), 0)
        }
        cardInner.addView(tvFooter)

        cardLayout.addView(cardInner)

        cardLayout.requestLayout()

        val widthSpec = View.MeasureSpec.makeMeasureSpec(dynamicWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        cardLayout.measure(widthSpec, heightSpec)

        val baseW = cardLayout.measuredWidth.coerceAtLeast(1)
        val baseH = cardLayout.measuredHeight.coerceAtLeast(1)

        cardLayout.layout(0, 0, baseW, baseH)

        val safeScale = minOf(scaleFactor, 8192f / baseW.toFloat(), 8192f / baseH.toFloat())
        val hdW = (baseW * safeScale).toInt().coerceIn(1, 8192)
        val hdH = (baseH * safeScale).toInt().coerceIn(1, 8192)

        val bitmap = android.graphics.Bitmap.createBitmap(hdW, hdH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(0xFF121212.toInt())
        canvas.scale(safeScale, safeScale)
        cardLayout.draw(canvas)

        return bitmap
    }


    private fun showCustomerRowDialog() {
        if (selectedRowIndices.isEmpty()) {
            Toast.makeText(this, "ਕਸਟਮਰ ਨੂੰ ਭੇਜਣ ਲਈ ਪਹਿਲਾਂ ਕੋਈ ਲਾਈਨ ਸਿਲੈਕਟ ਕਰੋ", Toast.LENGTH_SHORT).show()
            return
        }

        val sheet = activeSheet()
        val headers = mutableListOf<String>()
        val rowDataList = mutableListOf<Pair<Int, List<String>>>()

        if (sheet != null) {
            val headerRow = sheet.getRow(0)
            val maxCols = headerRow?.lastCellNum?.toInt() ?: 0
            for (c in 0 until maxCols) {
                if (hiddenColumnIndices.contains(c)) continue
                val cell = headerRow?.getCell(c)
                val hText = cell?.let { rawCellValue(it) }?.trim().orEmpty()
                headers.add(if (hText.isNotEmpty()) hText else getColumnLabel(c))
            }

            for (rIdx in selectedRowIndices.sorted()) {
                if (hiddenRowIndices.contains(rIdx)) continue
                val row = sheet.getRow(rIdx) ?: continue
                val rowCells = mutableListOf<String>()
                for (c in 0 until maxCols) {
                    if (hiddenColumnIndices.contains(c)) continue
                    val cell = row.getCell(c)
                    rowCells.add(cell?.let { rawCellValue(it) }?.trim().orEmpty())
                }
                rowDataList.add(Pair(rIdx + 1, rowCells))
            }
        } else {
            val rows = currentSheetsData[activeSheetName] ?: return
            if (rows.isEmpty()) return

            val maxCols = rows.maxOfOrNull { it.size } ?: 0
            val headerRow = rows.first().map { it.stripStylePrefix() }
            for (c in 0 until maxCols) {
                if (hiddenColumnIndices.contains(c)) continue
                val hText = headerRow.getOrNull(c).orEmpty()
                headers.add(if (hText.isNotEmpty()) hText else getColumnLabel(c))
            }

            for (rIdx in selectedRowIndices.sorted()) {
                if (hiddenRowIndices.contains(rIdx)) continue
                val row = rows.getOrNull(rIdx) ?: continue
                val rowCells = mutableListOf<String>()
                for (c in 0 until maxCols) {
                    if (hiddenColumnIndices.contains(c)) continue
                    val cellVal = row.getOrNull(c)?.stripStylePrefix().orEmpty()
                    rowCells.add(cellVal)
                }
                rowDataList.add(Pair(rIdx + 1, rowCells))
            }
        }

        val formattedText = buildString {
            append("📋 *ਐਕਸਲ ਬੇਰੌਰਾ (Excel Customer Detail)*\n")
            append("─────────────────────────\n")
            for ((lineNum, rowValues) in rowDataList) {
                append("📌 *ਲਾਈਨ $lineNum (Row $lineNum)*\n")
                for ((cIdx, valStr) in rowValues.withIndex()) {
                    val cleanVal = valStr.stripStylePrefix()
                    if (cleanVal.isNotEmpty()) {
                        val headerLabel = (headers.getOrNull(cIdx) ?: getColumnLabel(cIdx)).stripStylePrefix()
                        append("• $headerLabel: $cleanVal\n")
                    }
                }
                append("─────────────────────────\n")
            }
            append("ਧੰਨਵਾਦ! (Gurmukhi Keyboard App)")
        }

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        val previewTv = TextView(this).apply {
            text = formattedText
            textSize = 13f
            setTextColor(0xFFE0E0E0.toInt())
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF222222.toInt())
        }

        val hScroll = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = false
            addView(previewTv)
        }

        val vScroll = android.widget.ScrollView(this).apply {
            isFillViewport = false
            addView(hScroll)
        }
        dialogView.addView(vScroll)

        AlertDialog.Builder(this)
            .setTitle("ਕਸਟਮਰ ਲਈ ਲਾਈਨ ਡਿਟੇਲ (Customer View)")
            .setView(dialogView)
            .setPositiveButton("📲 WhatsApp 'ਤੇ ਭੇਜੋ") { _, _ ->
                val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, formattedText)
                }
                startActivity(Intent.createChooser(whatsappIntent, "ਕਸਟਮਰ ਨੂੰ ਭੇਜੋ"))
            }
            .setNeutralButton("📋 ਕਾਪੀ ਕਰੋ") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("CustomerData", formattedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "ਕਸਟਮਰ ਡਿਟੇਲ ਕਾਪੀ ਹੋ ਗਈ", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("ਬੰਦ ਕਰੋ", null)
            .show()
    }

    private fun confirmDeleteRow(rowIndex: Int) {
        AlertDialog.Builder(this)
            .setTitle("ਲਾਈਨ ਡਿਲੀਟ ਕਰੋ (Delete Row)")
            .setMessage("ਕੀ ਤੁਸੀਂ ਲਾਈਨ ਨੰਬਰ ${rowIndex + 1} ਡਿਲੀਟ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?")
            .setPositiveButton("ਹਾਂ (Delete)") { _, _ ->
                deleteSingleRow(rowIndex)
            }
            .setNegativeButton("ਨਹੀਂ (Cancel)", null)
            .show()
    }

    private fun confirmDeleteColumn(colIndex: Int) {
        val colLabel = getColumnLabel(colIndex)
        AlertDialog.Builder(this)
            .setTitle("ਕੋਲਮ ਡਿਲੀਟ ਕਰੋ (Delete Column)")
            .setMessage("ਕੀ ਤੁਸੀਂ ਕੋਲਮ '$colLabel' ਡਿਲੀਟ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?")
            .setPositiveButton("ਹਾਂ (Delete)") { _, _ ->
                deleteSingleColumn(colIndex)
            }
            .setNegativeButton("ਨਹੀਂ (Cancel)", null)
            .show()
    }

    private fun confirmDeleteSelectedRows() {
        if (selectedRowIndices.isEmpty()) return
        val count = selectedRowIndices.size
        val labels = selectedRowIndices.sorted().map { it + 1 }.joinToString(", ")
        AlertDialog.Builder(this)
            .setTitle("ਚੁਣੀਆਂ ਲਾਈਨਾਂ ਡਿਲੀਟ ਕਰੋ (Delete $count Rows)")
            .setMessage("ਕੀ ਤੁਸੀਂ ਚੁਣੀਆਂ ਗਈਆਂ $count ਲਾਈਨਾਂ ($labels) ਡਿਲੀਟ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?")
            .setPositiveButton("ਹਾਂ (Delete)") { _, _ ->
                deleteSelectedRows()
            }
            .setNegativeButton("ਨਹੀਂ (Cancel)", null)
            .show()
    }

    private fun confirmDeleteSelectedColumns() {
        if (selectedColIndices.isEmpty()) return
        val count = selectedColIndices.size
        val labels = selectedColIndices.sorted().joinToString(", ") { getColumnLabel(it) }
        AlertDialog.Builder(this)
            .setTitle("ਚੁਣੇ ਕੋਲਮ ਡਿਲੀਟ ਕਰੋ (Delete $count Columns)")
            .setMessage("ਕੀ ਤੁਸੀਂ ਚੁਣੇ ਹੋਏ $count ਕੋਲਮ ($labels) ਡਿਲੀਟ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?")
            .setPositiveButton("ਹਾਂ (Delete)") { _, _ ->
                deleteSelectedColumns()
            }
            .setNegativeButton("ਨਹੀਂ (Cancel)", null)
            .show()
    }

    private fun deleteSelectedColumns() {
        val count = selectedColIndices.size
        if (count == 0) return

        val sheet = activeSheet()
        if (sheet != null) {
            pushHistoryState()
            selectedColIndices.sortedDescending().forEach { colIndex ->
                for (r in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val lastCell = (row.lastCellNum.toInt() - 1).coerceAtLeast(colIndex)
                    for (c in colIndex until lastCell) {
                        val source = row.getCell(c + 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)
                        val destination = row.getCell(c) ?: row.createCell(c)
                        copyCellValue(source, destination)
                    }
                    row.getCell(lastCell)?.let { row.removeCell(it) }
                }
            }
            selectedColIndices.clear()
            btnDeleteCols.isVisible = false
            formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
            refreshActiveSheetView()
            Toast.makeText(this, "$count ਕੋਲਮ ਡਿਲੀਟ ਹੋ ਗਏ — Undo ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
        } else if (activeSheetName.isNotEmpty() && currentSheetsData.containsKey(activeSheetName)) {
            val currentList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList() ?: return
            pushHistoryState()
            selectedColIndices.sortedDescending().forEach { colIndex ->
                for (row in currentList) {
                    if (colIndex in row.indices) {
                        row.removeAt(colIndex)
                    }
                }
            }
            currentSheetsData[activeSheetName] = currentList
            selectedColIndices.clear()
            btnDeleteCols.isVisible = false
            refreshActiveSheetView()
            Toast.makeText(this, "$count ਕੋਲਮ ਡਿਲੀਟ ਹੋ ਗਏ — Undo ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isCustomDateFormatted(cell: Cell): Boolean {
        if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) return true
        val style = cell.cellStyle ?: return false
        val formatString = style.dataFormatString?.lowercase(Locale.ROOT).orEmpty()
        if (formatString.isEmpty()) return false

        return formatString.contains("yy") ||
                formatString.contains("mmm") ||
                formatString.contains("d-m") ||
                formatString.contains("m-d") ||
                formatString.contains("d/m") ||
                formatString.contains("m/d") ||
                formatString.contains("d.m")
    }

    private fun formatCell(cell: Cell, evaluator: FormulaEvaluator?, isDateCol: Boolean = false): String = try {
        if (isDateCol || isCustomDateFormatted(cell)) {
            val date = try { cell.dateCellValue } catch (_: Throwable) {
                try { org.apache.poi.ss.usermodel.DateUtil.getJavaDate(cell.numericCellValue) } catch (_: Throwable) { null }
            }
            if (date != null) {
                "'" + java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(date)
            } else if (evaluator != null) dataFormatter.formatCellValue(cell, evaluator)
            else dataFormatter.formatCellValue(cell)
        } else {
            if (evaluator != null) dataFormatter.formatCellValue(cell, evaluator)
            else dataFormatter.formatCellValue(cell)
        }
    } catch (_: Throwable) {
        try { dataFormatter.formatCellValue(cell) } catch (_: Throwable) { "" }
    }

    private fun getPoiCellStylePrefix(cell: Cell): String = try {
        val style = cell.cellStyle ?: return ""
        val fillBg = style.fillForegroundColorColor
        var bgHex = ""
        var fgHex = ""
        if (fillBg is org.apache.poi.xssf.usermodel.XSSFColor) {
            val argb = fillBg.argbHex
            if (!argb.isNullOrEmpty() && argb.length >= 6) {
                bgHex = "#" + argb.takeLast(6)
            }
        } else if (style.fillForegroundColor != 0.toShort()) {
            val indexed = style.fillForegroundColor.toInt()
            if (indexed in excelIndexedPalette.indices) {
                bgHex = excelIndexedPalette[indexed]
            }
        }

        val font = cell.sheet.workbook.getFontAt(style.fontIndex)
        if (font is org.apache.poi.xssf.usermodel.XSSFFont) {
            val xColor = font.xssfColor
            if (xColor != null && !xColor.argbHex.isNullOrEmpty()) {
                fgHex = "#" + xColor.argbHex.takeLast(6)
            }
        }

        if (bgHex.isNotEmpty() || fgHex.isNotEmpty()) "[[STYLE:$bgHex:$fgHex]]" else ""
    } catch (_: Throwable) { "" }

    private fun rawCellValue(cell: Cell, isDateCol: Boolean = false): String = try {
        val stylePrefix = getPoiCellStylePrefix(cell)
        val plainVal = when (cell.cellTypeEnum) {
            CellType.STRING -> {
                val str = cell.stringCellValue
                if (isStringAlreadyDateFormat(str) && !str.startsWith("'")) "'$str" else str
            }
            CellType.NUMERIC -> {
                if (isDateCol || isCustomDateFormatted(cell)) {
                    val date = try { cell.dateCellValue } catch (_: Throwable) {
                        try { org.apache.poi.ss.usermodel.DateUtil.getJavaDate(cell.numericCellValue) } catch (_: Throwable) { null }
                    }
                    if (date != null) "'" + java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(date)
                    else cell.numericCellValue.toString()
                } else {
                    cell.numericCellValue.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> formatCell(cell, formulaEvaluator, isDateCol)
            CellType.ERROR -> "#ERROR"
            else -> ""
        }
        if (plainVal.isNotEmpty()) "$stylePrefix$plainVal" else plainVal
    } catch (_: Throwable) { "" }

    private val dateRegex = """^\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4}$""".toRegex()
    private val alphaDateRegex = """^\d{1,2}[-/.]?[A-Za-z]{3,9}[-/.]?\d{2,4}$""".toRegex()
    private val spaceDateRegex = """^\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4}$""".toRegex()

    private fun isAmountHeader(headerText: String): Boolean {
        val h = headerText.lowercase(Locale.ROOT).trim()
        if (h.isEmpty()) return false
        return h.contains("amount") || h.contains("price") || h.contains("salar") ||
                h.contains("qty") || h.contains("quantity") || h.contains("total") ||
                h.contains("balance") || h.contains("rs") || h.contains("rupee") || h.contains("ਕੁੱਲ") ||
                h.contains("item") || h.contains("part") || h.contains("code") ||
                h.contains("account") || h.contains("number") || h.contains("no") ||
                h.contains("num") || h.contains("id") || h.contains("doc") ||
                h.contains("challan") || h.contains("voucher") || h.contains("bill") ||
                h.contains("invoice") || h.contains("batch") || h.contains("sr") ||
                h.contains("sl") || h.contains("s.no") || h.contains("i.no") ||
                h.contains("ਨੰਬਰ") || h.contains("ਆਈਟਮ") || h.contains("ਖਾਤਾ") || h.contains("ਕੋਡ")
    }

    private fun isHeaderDate(cellText: String): Boolean {
        val hText = cellText.lowercase(Locale.ROOT).trim()
        if (hText.isEmpty()) return false
        if (isAmountHeader(hText)) return false
        return hText.contains("date") || hText == "dt" || hText.contains("dob") ||
                hText.contains("expiry") || hText.contains("time") ||
                hText.contains("ਤਾਰੀਖ") || hText.contains("ਮਿਤੀ") ||
                isStringAlreadyDateFormat(cellText)
    }

    private fun isStringAlreadyDateFormat(value: String): Boolean {
        var trimmed = value.trim()
        if (trimmed.startsWith("'")) trimmed = trimmed.drop(1).trim()
        if (trimmed.isEmpty()) return false
        return dateRegex.matches(trimmed) || alphaDateRegex.matches(trimmed) || spaceDateRegex.matches(trimmed)
    }

    private fun formatRawStringIfDateSerial(raw: String, isDateCol: Boolean = false): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed

        if (trimmed.startsWith("'")) {
            val withoutQuote = trimmed.drop(1).trim()
            if (isStringAlreadyDateFormat(withoutQuote)) {
                return trimmed
            }
        }

        if (isStringAlreadyDateFormat(trimmed)) {
            return "'$trimmed"
        }

        if (!isDateCol) return raw

        val num = trimmed.toDoubleOrNull()
        if (num != null && num >= 30000.0 && num <= 65000.0) {
            try {
                val date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(num)
                if (date != null) {
                    val formatted = java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(date)
                    return "'$formatted"
                }
            } catch (_: Throwable) {}
        }
        return raw
    }

    private fun setCellFromInput(cell: Cell, input: String) {
        val value = input.trim()
        try {
            if (value.startsWith("=") && value.length > 1) {
                cell.setCellFormula(value.drop(1))
            } else if (value.isEmpty()) {
                cell.setCellType(CellType.BLANK)
            } else {
                val numeric = value.toDoubleOrNull()
                if (numeric != null && !value.startsWith("0") && !value.startsWith("+")) cell.setCellValue(numeric)
                else cell.setCellValue(value)
            }
        } catch (_: Throwable) { cell.setCellValue(value) }
    }

    private fun highlightHtml(text: String, lowerQuery: String): String {
        if (lowerQuery.isEmpty()) return escapeHtml(text)
        val lower = text.lowercase(Locale.ROOT)
        val out = StringBuilder()
        var from = 0
        while (true) {
            val idx = lower.indexOf(lowerQuery, from)
            if (idx < 0) { out.append(escapeHtml(text.substring(from))); break }
            out.append(escapeHtml(text.substring(from, idx)))
            out.append("<span class='highlight'>").append(escapeHtml(text.substring(idx, idx + lowerQuery.length))).append("</span>")
            from = idx + lowerQuery.length
        }
        return out.toString()
    }

    private fun deleteSingleRow(rowIndex: Int) {
        val sheet = activeSheet()
        if (sheet != null) {
            if (rowIndex < 0 || rowIndex > sheet.lastRowNum || sheet.getRow(rowIndex) == null) return
            pushHistoryState()
            val last = sheet.lastRowNum
            if (rowIndex < last) sheet.shiftRows(rowIndex + 1, last, -1)
            else sheet.removeRow(sheet.getRow(rowIndex))
            formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
            selectedRowIndices.clear(); calcBar.isVisible = false
            refreshActiveSheetView()
            Toast.makeText(this, "ਕਤਾਰ ਡਿਲੀਟ ਹੋਈ — Undo ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
        } else if (activeSheetName.isNotEmpty() && currentSheetsData.containsKey(activeSheetName)) {
            val currentList = currentSheetsData[activeSheetName]?.toMutableList() ?: return
            if (rowIndex in currentList.indices) {
                pushHistoryState()
                currentList.removeAt(rowIndex)
                currentSheetsData[activeSheetName] = currentList
                selectedRowIndices.clear(); calcBar.isVisible = false
                refreshActiveSheetView()
                Toast.makeText(this, "ਕਤਾਰ ਡਿਲੀਟ ਹੋਈ — Undo ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteSingleColumn(colIndex: Int) {
        val sheet = activeSheet()
        if (sheet != null) {
            if (colIndex < 0) return
            pushHistoryState()
            for (r in 0..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val lastCell = (row.lastCellNum.toInt() - 1).coerceAtLeast(colIndex)
                for (c in colIndex until lastCell) {
                    val source = row.getCell(c + 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)
                    val destination = row.getCell(c) ?: row.createCell(c)
                    copyCellValue(source, destination)
                }
                row.getCell(lastCell)?.let { row.removeCell(it) }
            }
            formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
            refreshActiveSheetView()
            Toast.makeText(this, "ਕਾਲਮ ਡਿਲੀਟ ਹੋਇਆ — Undo ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
        } else if (activeSheetName.isNotEmpty() && currentSheetsData.containsKey(activeSheetName)) {
            val currentList = currentSheetsData[activeSheetName]?.map { it.toMutableList() }?.toMutableList() ?: return
            pushHistoryState()
            for (row in currentList) {
                if (colIndex in row.indices) {
                    row.removeAt(colIndex)
                }
            }
            currentSheetsData[activeSheetName] = currentList
            selectedRowIndices.clear(); calcBar.isVisible = false
            refreshActiveSheetView()
            Toast.makeText(this, "ਕਾਲਮ ਡਿਲੀਟ ਹੋਇਆ — Undo ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyCellValue(source: Cell?, destination: Cell) {
        if (source == null) { destination.setCellType(CellType.BLANK); return }
        destination.cellStyle = source.cellStyle
        when (source.cellTypeEnum) {
            CellType.STRING -> destination.setCellValue(source.stringCellValue)
            CellType.NUMERIC -> destination.setCellValue(source.numericCellValue)
            CellType.BOOLEAN -> destination.setCellValue(source.booleanCellValue)
            CellType.FORMULA -> destination.setCellFormula(source.cellFormula)
            CellType.ERROR -> destination.setCellErrorValue(source.errorCellValue)
            else -> destination.setCellType(CellType.BLANK)
        }
    }

    private fun deleteSelectedRows() {
        val sheet = activeSheet()
        if (sheet != null) {
            if (selectedRowIndices.isEmpty()) return
            pushHistoryState()
            selectedRowIndices.sortedDescending().forEach { index ->
                if (index in 0..sheet.lastRowNum && sheet.getRow(index) != null) {
                    if (index < sheet.lastRowNum) sheet.shiftRows(index + 1, sheet.lastRowNum, -1)
                    else sheet.removeRow(sheet.getRow(index))
                }
            }
            selectedRowIndices.clear(); calcBar.isVisible = false; btnDeleteRows.isVisible = false
            formulaEvaluator = try { workbook?.creationHelper?.createFormulaEvaluator() } catch (_: Throwable) { null }
            refreshActiveSheetView()
            Toast.makeText(this, "ਚੁਣੀਆਂ ਕਤਾਰਾਂ ਡਿਲੀਟ ਹੋਈਆਂ — Undo ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
        } else if (activeSheetName.isNotEmpty() && currentSheetsData.containsKey(activeSheetName)) {
            val currentList = currentSheetsData[activeSheetName]?.toMutableList() ?: return
            if (selectedRowIndices.isEmpty()) return
            pushHistoryState()
            selectedRowIndices.sortedDescending().forEach { index ->
                if (index in currentList.indices) {
                    currentList.removeAt(index)
                }
            }
            currentSheetsData[activeSheetName] = currentList
            selectedRowIndices.clear(); calcBar.isVisible = false; btnDeleteRows.isVisible = false
            refreshActiveSheetView()
            Toast.makeText(this, "ਚੁਣੀਆਂ ਕਤਾਰਾਂ ਡਿਲੀਟ ਹੋਈਆਂ — Undo ਉਪਲਬਧ ਹੈ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pushHistoryState() {
        try {
            dataRedoStack.clear()

            if (currentSheetsData.isNotEmpty()) {
                val sheetsCopy = mutableMapOf<String, List<List<String>>>()
                currentSheetsData.forEach { (k, v) -> sheetsCopy[k] = v.map { it.toList() } }
                val formulasCopy = HashMap(cellFormulasMap)

                dataHistoryStack.push(HistorySnapshot(sheetsCopy, formulasCopy))
                while (dataHistoryStack.size > 25) dataHistoryStack.removeLast()
            }
            btnUndoRows.isVisible = true
            findViewById<ImageButton>(R.id.btn_undo_toolbar)?.isVisible = true
        } catch (e: Throwable) {
            Toast.makeText(this, "Undo ਹਿਸਟਰੀ ਨਹੀਂ ਬਣ ਸਕੀ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun undoLastAction() {
        if (dataHistoryStack.isEmpty()) {
            Toast.makeText(this, "ਵਾਪਸ ਲਿਆਉਣ ਲਈ ਕੋਈ ਹਿਸਟਰੀ ਨਹੀਂ ਹੈ (No Undo History)", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val currentSheetsCopy = mutableMapOf<String, List<List<String>>>()
            currentSheetsData.forEach { (k, v) -> currentSheetsCopy[k] = v.map { it.toList() } }
            val currentFormulasCopy = HashMap(cellFormulasMap)
            dataRedoStack.push(HistorySnapshot(currentSheetsCopy, currentFormulasCopy))

            val previousSnapshot = dataHistoryStack.pop()

            currentSheetsData.clear()
            previousSnapshot.sheetsData.forEach { (k, v) -> currentSheetsData[k] = v.map { it.toList() } }

            cellFormulasMap.clear()
            cellFormulasMap.putAll(previousSnapshot.formulasMap)

            refreshActiveSheetView()
            Toast.makeText(this, "↺ ਇੱਕ ਸਟੈੱਪ ਵਾਪਸ ਲਿਆਂਦਾ ਗਿਆ (Undo 1 Step)", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "Undo ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun redoNextAction() {
        if (dataRedoStack.isEmpty()) {
            Toast.makeText(this, "ਅੱਗੇ ਕਰਨ ਲਈ ਕੋਈ ਹਿਸਟਰੀ ਨਹੀਂ ਹੈ (No Redo History)", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val currentSheetsCopy = mutableMapOf<String, List<List<String>>>()
            currentSheetsData.forEach { (k, v) -> currentSheetsCopy[k] = v.map { it.toList() } }
            val currentFormulasCopy = HashMap(cellFormulasMap)
            dataHistoryStack.push(HistorySnapshot(currentSheetsCopy, currentFormulasCopy))

            val nextSnapshot = dataRedoStack.pop()

            currentSheetsData.clear()
            nextSnapshot.sheetsData.forEach { (k, v) -> currentSheetsData[k] = v.map { it.toList() } }

            cellFormulasMap.clear()
            cellFormulasMap.putAll(nextSnapshot.formulasMap)

            refreshActiveSheetView()
            Toast.makeText(this, "↻ ਇੱਕ ਸਟੈੱਪ ਅੱਗੇ ਕੀਤਾ ਗਿਆ (Redo 1 Step)", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "Redo ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetWorkbookToLoadedState() {
        val uri = currentUri
        if (uri == null) { Toast.makeText(this, "ਮੂਲ ਫਾਈਲ ਮੁੜ ਖੋਲ੍ਹਣ ਲਈ ਉਪਲਬਧ ਨਹੀਂ", Toast.LENGTH_SHORT).show(); return }
        loadWorkbook(uri)
    }

    private fun copyContentUriToEditableFile(uri: Uri, name: String): Uri {
        return try {
            if (uri.scheme == "file") return uri
            val safeName = if (name.isNotBlank()) name else "WhatsApp_Excel_${System.currentTimeMillis()}.xlsx"
            val cleanName = if (!safeName.endsWith(".xlsx", ignoreCase = true) && !safeName.endsWith(".xls", ignoreCase = true)) {
                "$safeName.xlsx"
            } else safeName

            val storageDir = File(getExternalFilesDir(null), "WhatsApp_Edits")
            if (!storageDir.exists()) storageDir.mkdirs()

            val localFile = File(storageDir, cleanName)
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(localFile)
        } catch (e: Throwable) {
            uri
        }
    }

    private fun saveExcelFile() {
        showSaveAsDialog()
    }

    private fun showSaveAsDialog() {
        val currentName = currentFileName.substringBeforeLast('.')
        val input = EditText(this).apply {
            setText("${currentName}_New")
            setSelection(text.length)
            hint = "ਨਵਾਂ ਫਾਈਲ ਨਾਮ ਲਿਖੋ (Enter New File Name)"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("ਨਵੇਂ ਨਾਮ ਨਾਲ ਸੇਵ ਕਰੋ (Save As)")
            .setMessage("ਕਿਰਪਾ ਕਰਕੇ ਫਾਈਲ ਦਾ ਨਵਾਂ ਨਾਮ ਲਿਖੋ:")
            .setView(input)
            .setPositiveButton("💾 ਸੇਵ ਕਰੋ (Save)") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val extension = if (workbook is HSSFWorkbook) ".xls" else ".xlsx"
                    val fullNewName = if (newName.endsWith(".xlsx", ignoreCase = true) || newName.endsWith(".xls", ignoreCase = true)) newName else "$newName$extension"
                    saveWorkbookAsNewFile(fullNewName)
                } else {
                    Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ ਕੋਈ ਨਾਮ ਲਿਖੋ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ (Cancel)", null)
            .show()
    }

    private fun saveWorkbookAsNewFile(newFileName: String) {
        progressBar.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val storageDir = File(getExternalFilesDir(null), "Saved_Excel_Files")
                if (!storageDir.exists()) storageDir.mkdirs()

                val localFile = File(storageDir, newFileName)
                val wb = workbook
                if (wb != null) {
                    formulaEvaluator?.clearAllCachedResultValues()
                    wb.setForceFormulaRecalculation(true)
                    java.io.FileOutputStream(localFile).use { output ->
                        wb.write(output)
                    }
                } else if (currentSheetsData.isNotEmpty()) {
                    val csvContent = exportToCsvString(currentSheetsData[activeSheetName] ?: emptyList())
                    localFile.writeText(csvContent, Charsets.UTF_8)
                }

                withContext(Dispatchers.Main) {
                    progressBar.isVisible = false
                    currentUri = Uri.fromFile(localFile)
                    currentFileName = newFileName
                    supportActionBar?.title = currentFileName
                    showSaveSuccessAndShareDialog(localFile)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    progressBar.isVisible = false
                    Toast.makeText(this@ExcelViewerActivity, "ਸੇਵ ਕਰਨ ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSaveSuccessAndShareDialog(savedFile: File) {
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            savedFile
        )

        AlertDialog.Builder(this)
            .setTitle("✅ ਸੇਵ ਸਫਲ (File Saved Successfully)")
            .setMessage("ਫਾਈਲ '${savedFile.name}' ਸਫ਼ਲਤਾਪੂਰਵਕ ਸੇਵ ਹੋ ਗਈ ਹੈ!\n\nਕੀ ਤੁਸੀਂ ਇਸ ਨਵੀਂ ਫਾਈਲ ਨੂੰ ਹੁਣੇ ਸ਼ੇਅਰ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?")
            .setPositiveButton("📲 ਤੁਰੰਤ ਸ਼ੇਅਰ ਕਰੋ (Share File)") { _, _ ->
                val mimeType = if (savedFile.name.endsWith(".csv", ignoreCase = true)) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    clipData = android.content.ClipData.newUri(contentResolver, savedFile.name, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "ਨਵੀਂ ਸੇਵ ਕੀਤੀ ਫਾਈਲ ਸ਼ੇਅਰ ਕਰੋ"))
            }
            .setNeutralButton("📁 ਸੇਵਡ ਫਾਈਲਾਂ ਦੇਖੋ") { _, _ ->
                scanAndShowExcelFilesDialog()
            }
            .setNegativeButton("ਬੰਦ ਕਰੋ (Close)", null)
            .show()
    }

    private fun saveWorkbookToUri(uri: Uri) {
        val wb = workbook ?: return
        progressBar.isVisible = true
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    formulaEvaluator?.clearAllCachedResultValues()
                    wb.setForceFormulaRecalculation(true)
                    contentResolver.openOutputStream(uri, "wt")?.use { output -> wb.write(output) }
                        ?: throw IllegalStateException("Output stream ਨਹੀਂ ਖੁੱਲ੍ਹੀ")
                    null
                } catch (e: Throwable) { e.message ?: "Unknown error" }
            }
            progressBar.isVisible = false
            if (error == null) Toast.makeText(this@ExcelViewerActivity, "Excel ਫਾਈਲ ਸੇਵ ਹੋ ਗਈ", Toast.LENGTH_LONG).show()
            else Toast.makeText(this@ExcelViewerActivity, "ਸੇਵ ਕਰਨ ਵਿੱਚ ਗਲਤੀ: $error", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareExcelFile() {
        val wb = workbook
        if (wb != null) {
            try {
                val isXls = wb is HSSFWorkbook
                val extension = if (isXls) ".xls" else ".xlsx"
                val mime = if (isXls) "application/vnd.ms-excel" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                val safeBase = currentFileName.substringBeforeLast('.', "Spreadsheet").replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Spreadsheet" }
                val file = File(cacheDir, "${safeBase}_shared$extension")
                file.outputStream().use { wb.write(it) }
                val contentUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Excel ਫਾਈਲ ਸ਼ੇਅਰ ਕਰੋ"))
            } catch (e: Throwable) {
                Toast.makeText(this, "ਸ਼ੇਅਰ ਕਰਨ ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            val activeData = currentSheetsData[activeSheetName]
            if (activeData.isNullOrEmpty()) {
                Toast.makeText(this, "ਸ਼ੇਅਰ ਕਰਨ ਲਈ ਕੋਈ ਡਾਟਾ ਨਹੀਂ ਹੈ", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val csvContent = exportToCsvString(activeData)
                val baseName = currentFileName.replace(".xlsx", "").replace(".xls", "").replace(".csv", "").trim()
                val sharedFileName = "Excel_${if (baseName.isNotEmpty()) baseName else "Spreadsheet"}.csv"
                val file = File(cacheDir, sharedFileName)
                file.writeText(csvContent, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Excel / CSV ਫਾਈਲ ਸ਼ੇਅਰ ਕਰੋ"))
            } catch (e: Throwable) {
                Toast.makeText(this, "ਸ਼ੇਅਰ ਕਰਨ ਵਿੱਚ ਗਲਤੀ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportToCsvString(rows: List<List<String>>): String {
        val sb = StringBuilder()
        for (row in rows) {
            val line = row.joinToString(",") { cell ->
                if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
                    "\"${cell.replace("\"", "\"\"")}\""
                } else {
                    cell
                }
            }
            sb.append(line).append("\r\n")
        }
        return sb.toString()
    }

    private fun parseExcelOrCsv(uri: Uri): Map<String, List<List<String>>> {
        val result = mutableMapOf<String, List<List<String>>>()
        try {
            val contentResolver = applicationContext.contentResolver
            val mimeType = contentResolver.getType(uri)?.lowercase() ?: ""
            val name = currentFileName.lowercase()

            if (mimeType.contains("csv") || name.endsWith(".csv") || mimeType.contains("text")) {
                val rawStream = contentResolver.openInputStream(uri)
                if (rawStream != null) {
                    val stream = java.io.BufferedInputStream(rawStream, 65536)
                    val rows = parseCsvStream(stream)
                    if (rows.isNotEmpty()) {
                        result["Sheet1"] = rows
                        return result
                    }
                }
            }

            val rawStream = contentResolver.openInputStream(uri)
            if (rawStream != null) {
                val stream = java.io.BufferedInputStream(rawStream, 65536)
                val xlsxData = parseXlsxZipStream(stream)
                if (xlsxData.isNotEmpty()) {
                    return xlsxData
                }
            }

            val streamFallback = contentResolver.openInputStream(uri)
            if (streamFallback != null) {
                val stream = java.io.BufferedInputStream(streamFallback, 65536)
                val csvFallback = parseCsvStream(stream)
                if (csvFallback.isNotEmpty()) {
                    result["Sheet1"] = csvFallback
                    return result
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim().trim('"', '\''))
                sb.clear()
            } else {
                sb.append(c)
            }
        }
        tokens.add(sb.toString().trim().trim('"', '\''))
        return tokens
    }

    private fun parseCsvStream(stream: InputStream): List<List<String>> {
        val rows = ArrayList<List<String>>(3000)
        try {
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 65536)
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                if (l.trim().isEmpty()) continue

                val delimiterChar = if (l.contains("\t")) '\t' else if (l.contains(";")) ';' else ','
                val parts = parseCsvLine(l, delimiterChar)
                rows.add(parts)
            }
            reader.close()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return rows
    }

    private fun parseXlsxZipStream(stream: InputStream): Map<String, List<List<String>>> {
        val sheetsData = LinkedHashMap<String, List<List<String>>>()
        val sharedStrings = ArrayList<String>(4000)
        val realSheetNames = ArrayList<String>(10)
        val pivotRecordsList = ArrayList<List<List<String>>>(5)
        val pivotTableEntriesList = ArrayList<List<List<String>>>(5)

        var workbookBytes: ByteArray? = null
        var workbookRelsBytes: ByteArray? = null
        var stylesXmlBytes: ByteArray? = null

        try {
            val bufferedIn = if (stream is java.io.BufferedInputStream) stream else java.io.BufferedInputStream(stream, 65536)
            val zipIn = ZipInputStream(bufferedIn)
            var entry = zipIn.nextEntry

            val worksheetEntries = mutableListOf<Pair<String, ByteArray>>()
            var sharedStringsBytes: ByteArray? = null

            while (entry != null) {
                val entryName = entry.name
                if (entryName == "xl/sharedStrings.xml") {
                    sharedStringsBytes = zipIn.readBytes()
                } else if (entryName == "xl/styles.xml") {
                    stylesXmlBytes = zipIn.readBytes()
                } else if (entryName == "xl/workbook.xml") {
                    workbookBytes = zipIn.readBytes()
                    realSheetNames.addAll(parseWorkbookXml(workbookBytes))
                } else if (entryName == "xl/_rels/workbook.xml.rels") {
                    workbookRelsBytes = zipIn.readBytes()
                } else if (entryName.lowercase().contains("worksheets/") && entryName.lowercase().endsWith(".xml")) {
                    worksheetEntries.add(Pair(entryName, zipIn.readBytes()))
                } else if (entryName.startsWith("xl/pivotTables/pivotTable") && entryName.endsWith(".xml")) {
                    val ptBytes = zipIn.readBytes()
                    val ptRows = parsePivotTableXml(ptBytes, sharedStrings)
                    if (ptRows.isNotEmpty()) pivotTableEntriesList.add(ptRows)
                } else if (entryName.startsWith("xl/pivotCache/pivotCacheRecords") && entryName.endsWith(".xml")) {
                    val pivotBytes = zipIn.readBytes()
                    val pRows = parsePivotRecordsXml(pivotBytes, sharedStrings)
                    if (pRows.isNotEmpty()) pivotRecordsList.add(pRows)
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            if (sharedStringsBytes != null) {
                sharedStrings.addAll(parseSharedStringsXml(sharedStringsBytes))
            }

            val stylesMap = if (stylesXmlBytes != null) parseStylesXml(stylesXmlBytes) else emptyMap()

            val relsMap = if (workbookRelsBytes != null) parseWorkbookRelsXml(workbookRelsBytes) else emptyMap()
            val sheetsList = if (workbookBytes != null) parseWorkbookSheetsXml(workbookBytes) else emptyList()

            val sheetFileMap = mutableMapOf<String, String>()
            for ((sName, rId) in sheetsList) {
                val targetFile = relsMap[rId] ?: ""
                if (targetFile.isNotEmpty()) {
                    val cleanFileName = targetFile.replace("\\", "/").substringAfterLast('/')
                    sheetFileMap[cleanFileName] = sName
                }
            }

            worksheetEntries.sortBy { it.first }

            for ((index, pair) in worksheetEntries.withIndex()) {
                val entryFile = pair.first.substringAfterLast('/')
                val sheetName = sheetFileMap[entryFile] ?: realSheetNames.getOrNull(index)?.ifBlank { null } ?: "Sheet${index + 1}"
                val rows = parseWorksheetXml(pair.second, sharedStrings, stylesMap, sheetName).toMutableList()

                if (rows.isEmpty()) {
                    if (pivotRecordsList.isNotEmpty() && index in pivotRecordsList.indices) {
                        rows.addAll(pivotRecordsList[index])
                    } else if (pivotTableEntriesList.isNotEmpty() && index in pivotTableEntriesList.indices) {
                        rows.addAll(pivotTableEntriesList[index])
                    }
                } else {
                    if (pivotTableEntriesList.isNotEmpty() && index in pivotTableEntriesList.indices) {
                        val ptRows = pivotTableEntriesList[index]
                        if (ptRows.isNotEmpty()) {
                            rows.addAll(0, ptRows)
                        }
                    }
                }

                // Always keep sheet tab alive in sheetsData
                sheetsData[sheetName] = rows
            }

            // Auto-compute Pivot Table Grid for Sheet2/Summary/Pivot sheets if existing sheet is empty
            val firstSheetData = sheetsData.values.firstOrNull()
            if (firstSheetData != null && firstSheetData.size > 2) {
                val computedPivot = computePivotTableGrid(firstSheetData, 0, 1, 2, "Total")
                if (computedPivot.size > 3) {
                    sheetsData.keys.forEach { sName ->
                        if (sName.lowercase(Locale.ROOT).contains("sheet2") || sName.lowercase(Locale.ROOT).contains("summary") || sName.lowercase(Locale.ROOT).contains("pivot")) {
                            val existing = sheetsData[sName]?.toMutableList() ?: mutableListOf()
                            if (existing.isEmpty() || existing.size <= 2) {
                                existing.addAll(0, computedPivot)
                                sheetsData[sName] = existing
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return sheetsData
    }

    private fun computePivotTableGrid(
        rows: List<List<String>>,
        rowColIdx: Int = 0,
        colColIdx: Int = 1,
        valColIdx: Int = 2,
        valFieldName: String = "Total"
    ): List<List<String>> {
        if (rows.size <= 1) return emptyList()

        val dataRows = rows.drop(1)
        val rowItems = LinkedHashSet<String>()
        val colItems = LinkedHashSet<String>()

        for (r in dataRows.take(1000)) {
            val rVal = r.getOrNull(rowColIdx)?.trim().orEmpty().ifEmpty { "(Blank)" }
            val cVal = r.getOrNull(colColIdx)?.trim().orEmpty().ifEmpty { "(Blank)" }
            rowItems.add(rVal)
            colItems.add(cVal)
            if (rowItems.size > 100 || colItems.size > 50 || (rowItems.size * colItems.size) > 2500) {
                return emptyList()
            }
        }

        if (rowItems.isEmpty() || colItems.isEmpty() || (rowItems.size * colItems.size) > 2500) {
            return emptyList()
        }

        val grid = mutableMapOf<String, MutableMap<String, Double>>()

        for (r in dataRows) {
            val rVal = r.getOrNull(rowColIdx)?.trim().orEmpty().ifEmpty { "(Blank)" }
            val cVal = r.getOrNull(colColIdx)?.trim().orEmpty().ifEmpty { "(Blank)" }
            val vStr = r.getOrNull(valColIdx)?.trim()?.replace(",", "").orEmpty()
            val num = vStr.toDoubleOrNull() ?: 0.0

            val rowMap = grid.getOrPut(rVal) { mutableMapOf() }
            rowMap[cVal] = (rowMap[cVal] ?: 0.0) + num
        }

        val sortedRowItems = rowItems.sorted()
        val sortedColItems = colItems.sorted()

        val pivotGrid = mutableListOf<List<String>>()

        pivotGrid.add(listOf("Sum of $valFieldName", "Column Labels"))

        val headerRow = mutableListOf("Row Labels")
        headerRow.addAll(sortedColItems)
        headerRow.add("Grand Total")
        pivotGrid.add(headerRow)

        val colTotals = mutableMapOf<String, Double>()
        var overallGrandTotal = 0.0

        for (rVal in sortedRowItems) {
            val tr = mutableListOf<String>()
            tr.add(rVal)
            var rowGrandTotal = 0.0
            val rowMap = grid[rVal] ?: emptyMap()

            for (cVal in sortedColItems) {
                val cellSum = rowMap[cVal] ?: 0.0
                rowGrandTotal += cellSum
                colTotals[cVal] = (colTotals[cVal] ?: 0.0) + cellSum
                tr.add(if (cellSum != 0.0) formatNumber(cellSum) else "")
            }

            overallGrandTotal += rowGrandTotal
            tr.add(formatNumber(rowGrandTotal))
            pivotGrid.add(tr)
        }

        val totalRow = mutableListOf("Grand Total")
        for (cVal in sortedColItems) {
            val cSum = colTotals[cVal] ?: 0.0
            totalRow.add(formatNumber(cSum))
        }
        totalRow.add(formatNumber(overallGrandTotal))
        pivotGrid.add(totalRow)

        return pivotGrid
    }

    private fun parseWorkbookRelsXml(bytes: ByteArray): Map<String, String> {
        val relsMap = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                        relsMap[id] = target.replace("\\", "/").substringAfterLast('/')
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return relsMap
    }

    private fun parseWorkbookSheetsXml(bytes: ByteArray): List<Pair<String, String>> {
        val sheetList = mutableListOf<Pair<String, String>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name")
                    val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                        ?: parser.getAttributeValue(null, "r:id")
                        ?: parser.getAttributeValue(null, "id") ?: ""
                    if (!name.isNullOrBlank()) {
                        sheetList.add(Pair(name, rId))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return sheetList
    }

    private fun parsePivotTableXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType

            val fields = mutableListOf<String>()
            val dataFields = mutableListOf<String>()
            var tableName = "PivotTable"

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val name = parser.name
                    if (name == "pivotTableDefinition") {
                        tableName = parser.getAttributeValue(null, "name") ?: "PivotTable"
                    } else if (name == "pivotField") {
                        val fName = parser.getAttributeValue(null, "name") ?: ""
                        if (fName.isNotEmpty()) fields.add(fName)
                    } else if (name == "dataField") {
                        val dfName = parser.getAttributeValue(null, "name") ?: ""
                        if (dfName.isNotEmpty()) dataFields.add(dfName)
                    }
                }
                eventType = parser.next()
            }

            if (fields.isNotEmpty() || dataFields.isNotEmpty()) {
                val titleRow = mutableListOf("📌 $tableName (Pivot Summary)", "")
                rows.add(titleRow)
                for (df in dataFields) {
                    rows.add(listOf("Sum / Calculation", df))
                }
                for (f in fields) {
                    rows.add(listOf("Pivot Field", f))
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return rows
    }

    private fun parsePivotRecordsXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType
            var currentRow = mutableListOf<String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "r") {
                            currentRow = mutableListOf()
                        } else if (name == "n" || name == "s") {
                            val v = parser.getAttributeValue(null, "v") ?: ""
                            currentRow.add(v)
                        } else if (name == "x") {
                            val idx = parser.getAttributeValue(null, "v")?.toIntOrNull() ?: -1
                            val v = if (idx in 0 until sharedStrings.size) sharedStrings[idx] else idx.toString()
                            currentRow.add(v)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "r" && currentRow.isNotEmpty()) {
                            rows.add(currentRow)
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return rows
    }

    private fun parseWorkbookXml(bytes: ByteArray): List<String> {
        val sheetNames = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name")
                    if (!name.isNullOrBlank()) {
                        sheetNames.add(name)
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return sheetNames
    }

    private fun parseSharedStringsXml(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")

            var eventType = parser.eventType
            val sb = StringBuilder()
            var inT = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t") inT = true
                        else if (parser.name == "si") sb.clear()
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) sb.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") inT = false
                        else if (parser.name == "si") {
                            list.add(sb.toString())
                            sb.clear()
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return list
    }

    private val excelIndexedPalette = arrayOf(
        "#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF",
        "#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF",
        "#800000", "#008000", "#000080", "#808000", "#800080", "#008080", "#C0C0C0", "#808080",
        "#9999FF", "#993366", "#FFFFCC", "#CCFFFF", "#660066", "#FF8080", "#0066CC", "#CCCCFF",
        "#000080", "#FF00FF", "#FFFF00", "#00FFFF", "#800080", "#800000", "#008080", "#0000FF",
        "#00CCFF", "#CCFFFF", "#CCFFCC", "#FFFF99", "#99CCFF", "#FF99CC", "#CC99FF", "#FFCC99",
        "#3366FF", "#33CCCC", "#99CC00", "#FFCC00", "#FF9900", "#FF6600", "#666699", "#969696",
        "#003366", "#339966", "#003300", "#333300", "#993300", "#993366", "#333399", "#333333"
    )

    private val excelThemePalette = arrayOf(
        "#FFFFFF", "#000000", "#E7E6E6", "#44546A", "#5B9BD5", "#ED7D31", "#A5A5A5", "#FFC000", "#4472C4", "#70AD47"
    )

    private fun resolveXmlColor(parser: XmlPullParser): String? {
        val rgb = parser.getAttributeValue(null, "rgb")
        if (!rgb.isNullOrBlank()) {
            return formatRgbHex(rgb)
        }
        val indexed = parser.getAttributeValue(null, "indexed")?.toIntOrNull()
        if (indexed != null && indexed in excelIndexedPalette.indices) {
            return excelIndexedPalette[indexed]
        }
        val theme = parser.getAttributeValue(null, "theme")?.toIntOrNull()
        if (theme != null && theme in excelThemePalette.indices) {
            return excelThemePalette[theme]
        }
        return null
    }

    private data class CellStyleColor(val bgColorHex: String?, val fontColorHex: String?)

    private fun parseStylesXml(bytes: ByteArray): Map<Int, CellStyleColor> {
        val result = mutableMapOf<Int, CellStyleColor>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType

            val fills = mutableListOf<String?>()
            val fonts = mutableListOf<String?>()
            val cellXfs = mutableListOf<Pair<Int, Int>>()

            var inFills = false
            var inFonts = false
            var inCellXfs = false
            var currentFillColor: String? = null
            var currentFontColor: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "fills") inFills = true
                        else if (name == "fonts") inFonts = true
                        else if (name == "cellXfs") inCellXfs = true
                        else if (inFills && (name == "fgColor" || name == "bgColor")) {
                            val color = resolveXmlColor(parser)
                            if (color != null) {
                                currentFillColor = color
                            }
                        } else if (inFonts && name == "color") {
                            val color = resolveXmlColor(parser)
                            if (color != null) {
                                currentFontColor = color
                            }
                        } else if (inCellXfs && name == "xf") {
                            val fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                            val fillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                            cellXfs.add(Pair(fontId, fillId))
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name == "fills") inFills = false
                        else if (name == "fonts") inFonts = false
                        else if (name == "cellXfs") inCellXfs = false
                        else if (name == "fill") {
                            fills.add(currentFillColor)
                            currentFillColor = null
                        } else if (name == "font") {
                            fonts.add(currentFontColor)
                            currentFontColor = null
                        }
                    }
                }
                eventType = parser.next()
            }

            for ((idx, xf) in cellXfs.withIndex()) {
                val fontColor = fonts.getOrNull(xf.first)
                val fillColor = fills.getOrNull(xf.second)
                if (fillColor != null || fontColor != null) {
                    result[idx] = CellStyleColor(fillColor, fontColor)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return result
    }

    private fun formatRgbHex(rawRgb: String): String {
        var clean = rawRgb.trim().uppercase(Locale.ROOT)
        if (clean.startsWith("#")) clean = clean.substring(1)
        if (clean.length == 8) clean = clean.substring(2)
        return "#$clean"
    }

    private fun parseWorksheetXml(
        bytes: ByteArray,
        sharedStrings: List<String>,
        stylesMap: Map<Int, CellStyleColor> = emptyMap(),
        sheetName: String = "",
        maxRowsToParse: Int = 5000
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val parser = Xml.newPullParser()
            val stream = java.io.BufferedInputStream(bytes.inputStream(), 65536)
            parser.setInput(stream, "UTF-8")

            var eventType = parser.eventType
            var currentRow = mutableListOf<String>()
            var cellType = ""
            var cellRef = ""
            var styleIdx = -1
            var cellVal = ""
            var cellFormula = ""
            var inValTag = false
            var inFormulaTag = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "row") {
                            currentRow = mutableListOf()
                        } else if (name == "c") {
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellRef = parser.getAttributeValue(null, "r") ?: ""
                            styleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                            cellVal = ""
                            cellFormula = ""
                        } else if (name == "v" || name == "t" || name == "is") {
                            inValTag = true
                        } else if (name == "f") {
                            inFormulaTag = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inValTag) cellVal += parser.text
                        if (inFormulaTag) cellFormula += parser.text
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name == "v" || name == "t" || name == "is") {
                            inValTag = false
                        } else if (name == "f") {
                            inFormulaTag = false
                        } else if (name == "c") {
                            var resolvedVal = when (cellType) {
                                "s" -> {
                                    val idx = cellVal.trim().toIntOrNull() ?: -1
                                    if (idx in 0 until sharedStrings.size) sharedStrings[idx] else cellVal
                                }
                                "b" -> if (cellVal.trim() == "1") "TRUE" else "FALSE"
                                else -> cellVal.trim()
                            }

                            val formula = cellFormula.trim().let {
                                if (it.isNotEmpty()) {
                                    if (it.startsWith("=")) it else "=$it"
                                } else ""
                            }

                            val cIdx = cRefToColIndex(cellRef)
                            val rIdx = rows.size

                            if (formula.isNotEmpty() && sheetName.isNotEmpty() && cIdx >= 0) {
                                cellFormulasMap["$sheetName:$rIdx:$cIdx"] = formula
                            }

                            val styleColor = if (styleIdx >= 0) stylesMap[styleIdx] else null
                            if (styleColor != null) {
                                val bg = styleColor.bgColorHex.orEmpty()
                                val fg = styleColor.fontColorHex.orEmpty()
                                if (bg.isNotEmpty() || fg.isNotEmpty()) {
                                    resolvedVal = "[[STYLE:$bg:$fg]]$resolvedVal"
                                }
                            }

                            if (cIdx >= 0) {
                                while (currentRow.size < cIdx) currentRow.add("")
                                currentRow.add(resolvedVal)
                            } else {
                                currentRow.add(resolvedVal)
                            }
                        } else if (name == "row") {
                            if (currentRow.isNotEmpty()) {
                                rows.add(currentRow)
                                if (maxRowsToParse > 0 && rows.size >= maxRowsToParse) {
                                    break
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return rows
    }

    private fun cRefToColIndex(cellRef: String): Int {
        if (cellRef.isEmpty()) return -1
        var colStr = ""
        for (ch in cellRef) {
            if (ch.isLetter()) colStr += ch
            else break
        }
        if (colStr.isEmpty()) return -1
        var cIdx = 0
        for (ch in colStr.uppercase(Locale.ROOT)) {
            cIdx = cIdx * 26 + (ch.code - 'A'.code + 1)
        }
        return cIdx - 1
    }

    private fun createHomeScreenShortcut() {
        val name = currentFileName.ifBlank { "Excel File Opener" }
        val cleanName = name.substringBeforeLast('.', name).take(25)
        val uri = currentUri
        val shortcutIntent = Intent(this, ExcelViewerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (uri != null) {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("excel_name", name)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val shortcut = ShortcutInfoCompat.Builder(this, "excel_shortcut_${uri?.toString().hashCode()}")
            .setShortLabel(cleanName.ifBlank { "Excel" })
            .setLongLabel("Excel: $name")
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_library))
            .setIntent(shortcutIntent)
            .build()
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
            Toast.makeText(this, "ਹੋਮ ਸਕ੍ਰੀਨ ਸ਼ਾਰਟਕੱਟ ਦੀ ਬੇਨਤੀ ਭੇਜੀ ਗਈ", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "ਇਸ ਡਿਵਾਈਸ 'ਤੇ ਸ਼ਾਰਟਕੱਟ ਉਪਲਬਧ ਨਹੀਂ", Toast.LENGTH_SHORT).show()
    }

    private fun getFileNameFromUri(uri: Uri): String = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        uri.lastPathSegment ?: "Excel File"
    } catch (_: Throwable) { uri.lastPathSegment ?: "Excel File" }

    private fun getColumnLabel(index: Int): String {
        var n = index
        val out = StringBuilder()
        while (n >= 0) { out.insert(0, ('A'.code + n % 26).toChar()); n = n / 26 - 1 }
        return out.toString()
    }

    private fun String.stripStylePrefix(): String {
        var str = this
        if (str.startsWith("[[STYLE:")) {
            val closeIdx = str.indexOf("]]")
            if (closeIdx != -1) str = str.substring(closeIdx + 2)
        }
        if (str.startsWith("{") && str.contains("}||")) {
            val closeIdx = str.indexOf("}||")
            if (closeIdx != -1) str = str.substring(closeIdx + 3)
        }
        if (str.startsWith("(:") || str.startsWith("(#") || str.startsWith("(")) {
            val closeIdx = str.indexOf(")")
            if (closeIdx != -1) str = str.substring(closeIdx + 1)
        }
        return str
    }

    private fun escapeHtml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun escapeJsString(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun formatNumber(value: Double): String = if (value.isFinite() && value % 1.0 == 0.0 && value <= Long.MAX_VALUE && value >= Long.MIN_VALUE) value.toLong().toString() else String.format(Locale.getDefault(), "%.2f", value)

    private fun Workbook.closeQuietly() { try { close() } catch (_: Exception) {} }

    private fun saveActivityState() {
        try {
            val uriStr = currentUri?.toString().orEmpty()
            if (uriStr.isNotEmpty()) {
                val prefs = getSharedPreferences("excel_viewer_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("last_excel_uri", uriStr)
                    putString("last_excel_name", currentFileName)
                    putString("last_excel_sheet", activeSheetName)
                    apply()
                }
            }
        } catch (_: Throwable) {}
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("saved_uri", currentUri?.toString().orEmpty())
        outState.putString("saved_name", currentFileName)
        outState.putString("saved_sheet", activeSheetName)
        saveActivityState()
    }

    override fun onPause() {
        super.onPause()
        saveActivityState()
    }

    override fun onStop() {
        super.onStop()
        saveActivityState()
    }

    override fun onDestroy() {
        saveActivityState()
        webView.removeJavascriptInterface("AndroidBridge")
        workbook?.closeQuietly()
        workbook = null
        super.onDestroy()
    }
}
