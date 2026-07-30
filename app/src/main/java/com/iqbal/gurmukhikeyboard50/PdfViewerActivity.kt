package com.iqbal.gurmukhikeyboard50

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var recyclerView: ZoomableRecyclerView
    private lateinit var pageIndicator: TextView
    private lateinit var btnSearch: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var renderer: PdfRenderer
    private lateinit var fileDescriptor: ParcelFileDescriptor
    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private var filePath: String = ""
    
    // Memory-based cache: using ~1/4 of available memory
    private val bitmapCache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: Int, old: Bitmap, new: Bitmap?) {
            if (evicted) old.recycle()
        }
    }
    
    // PdfRenderer is not thread-safe. Use a single background thread for all rendering.
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val renderDispatcher = newSingleThreadContext("PdfRenderThread")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        filePath = intent.getStringExtra("pdf_path") ?: ""
        val fileName = intent.getStringExtra("pdf_name") ?: "PDF Viewer"

        val toolbar = findViewById<Toolbar>(R.id.pdf_toolbar)
        setupToolbar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = fileName
        toolbar.setNavigationOnClickListener { finish() }

        val file = File(filePath)
        if (!file.exists()) {
            finish()
            return
        }

        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fileDescriptor)
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
            return
        }

        recyclerView = findViewById(R.id.pdf_recycler_view)
        pageIndicator = findViewById(R.id.pdf_page_indicator)
        btnSearch = findViewById(R.id.btn_search_page)
        seekBar = findViewById(R.id.pdf_seekbar)

        btnSearch.setOnClickListener { showGoToPageDialog() }
        pageIndicator.setOnClickListener { showGoToPageDialog() }
        
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        layoutManager.initialPrefetchItemCount = 2
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = PdfPageAdapter()
        recyclerView.setItemViewCacheSize(3)

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

        // Initialize indicator
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

        // Resume progress
        resumeLastReadPage()
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
                // First time opening this book, create entry
                val fileName = File(filePath).name
                val name = fileName.replace(".pdf", "").replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                db.bookDao().insertBook(BookEntity(name = name, path = filePath, fileName = fileName, lastPage = page))
            } else {
                db.bookDao().updateLastPage(filePath, page)
            }
        }
    }

    private fun showGoToPageDialog() {
        val totalPages = renderer.pageCount
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1 - $totalPages"
            setPadding(24.toPx(), 16.toPx(), 24.toPx(), 16.toPx())
        }

        AlertDialog.Builder(this)
            .setTitle("ਪੇਜ 'ਤੇ ਜਾਓ (Go to Page)")
            .setMessage("ਪੇਜ ਨੰਬਰ ਭਰੋ (ਕੁੱਲ ਪੇਜ: $totalPages):")
            .setView(input)
            .setPositiveButton("ਓਕੇ (OK)") { _, _ ->
                val pageStr = input.text.toString()
                if (pageStr.isNotEmpty()) {
                    val pageNum = pageStr.toInt()
                    if (pageNum in 1..totalPages) {
                        recyclerView.scrollToPosition(pageNum - 1)
                    } else {
                        Toast.makeText(this, "ਗਲਤ ਪੇਜ ਨੰਬਰ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("ਰੱਦ ਕਰੋ (Cancel)", null)
            .show()
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

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

    private fun renderPage(index: Int): Bitmap? {
        return try {
            val page = renderer.openPage(index)
            val screenWidth = resources.displayMetrics.widthPixels
            
            val width = (screenWidth * 1.2).toInt() // 1.2x for better memory efficiency
            val height = (width * page.height) / page.width
            
            // Use ARGB_8888 for compatibility
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("PdfViewer", "Error rendering page $index: ${e.message}")
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        renderDispatcher.close()
        try {
            renderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {}
        bitmapCache.evictAll() // This will trigger recycling of all bitmaps
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
            val cachedBitmap = bitmapCache.get(position)
            
            if (cachedBitmap != null) {
                holder.progressBar.isVisible = false
                holder.imageView.setImageBitmap(cachedBitmap)
            } else {
                holder.imageView.setImageBitmap(null)
                holder.progressBar.isVisible = true
                holder.job = scope.launch {
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
            val imageView: ImageView = view.findViewById(R.id.pdf_page_image)
            val progressBar: ProgressBar = view.findViewById(R.id.pdf_page_loader)
            var job: Job? = null
        }
    }
}
