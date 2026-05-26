package com.justpdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: LinearLayout
    private lateinit var btnShare: ImageButton
    private lateinit var btnOpenFile: ImageButton
    private lateinit var tvPageCounter: TextView
    private lateinit var tvEmpty: TextView

    // ── PDF state ──────────────────────────────────────────────────────────────
    private var currentFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var adapter: PdfPageAdapter? = null

    // ── Toolbar auto-hide ──────────────────────────────────────────────────────
    private val toolbarHandler = Handler(Looper.getMainLooper())
    private val hideToolbarRunnable = Runnable { hideToolbar() }
    private val TOOLBAR_HIDE_DELAY_MS = 3_000L

    // ── File picker launcher ───────────────────────────────────────────────────
    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {}
                    loadPdfFromUri(uri)
                }
            }
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_main)

        recyclerView  = findViewById(R.id.recyclerView)
        toolbar       = findViewById(R.id.toolbar)
        btnShare      = findViewById(R.id.btnShare)
        btnOpenFile   = findViewById(R.id.btnOpenFile)
        tvPageCounter = findViewById(R.id.tvPageCounter)
        tvEmpty       = findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Show toolbar on tap anywhere on the RecyclerView
        recyclerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) showToolbar()
            false
        }

        btnShare.setOnClickListener { sharePdf() }
        btnOpenFile.setOnClickListener { openFilePicker() }

        // Track visible page for the counter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION) {
                    val total = adapter?.itemCount ?: 0
                    updatePageCounter(firstVisible, total)
                }
            }
        })

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        toolbarHandler.removeCallbacks(hideToolbarRunnable)
        closePdfRenderer()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Intent handling
    // ──────────────────────────────────────────────────────────────────────────

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { loadPdfFromUri(it) }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let { loadPdfFromUri(it) }
            }
            else -> {
                if (currentFile == null) openFilePicker()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PDF loading
    // ──────────────────────────────────────────────────────────────────────────

    private fun loadPdfFromUri(uri: Uri) {
        val file = FileUtils.uriToFile(this, uri)
        if (file == null) {
            Toast.makeText(this, R.string.error_cannot_open, Toast.LENGTH_SHORT).show()
            return
        }
        loadPdfFromFile(file)
    }

    private fun loadPdfFromFile(file: File) {
        closePdfRenderer()
        currentFile = file

        try {
            pdfDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(pdfDescriptor!!)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_cannot_open, Toast.LENGTH_SHORT).show()
            return
        }

        val pageCount = pdfRenderer!!.pageCount
        tvEmpty.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        adapter = PdfPageAdapter(pdfRenderer!!, pageCount)
        recyclerView.adapter = adapter
        updatePageCounter(0, pageCount)
        showToolbar()
    }

    private fun closePdfRenderer() {
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { pdfDescriptor?.close() } catch (_: Exception) {}
        pdfRenderer = null
        pdfDescriptor = null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Toolbar
    // ──────────────────────────────────────────────────────────────────────────

    private fun showToolbar() {
        toolbarHandler.removeCallbacks(hideToolbarRunnable)
        toolbar.animate().cancel()
        toolbar.alpha = 1f
        toolbar.visibility = View.VISIBLE
        scheduleHideToolbar()
    }

    private fun hideToolbar() {
        toolbar.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { toolbar.visibility = View.GONE }
            .start()
    }

    private fun scheduleHideToolbar() {
        toolbarHandler.removeCallbacks(hideToolbarRunnable)
        toolbarHandler.postDelayed(hideToolbarRunnable, TOOLBAR_HIDE_DELAY_MS)
    }

    private fun updatePageCounter(page: Int, pageCount: Int) {
        tvPageCounter.text = getString(R.string.page_counter, page + 1, pageCount)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Share
    // ──────────────────────────────────────────────────────────────────────────

    private fun sharePdf() {
        val file = currentFile ?: run {
            Toast.makeText(this, R.string.error_cannot_open, Toast.LENGTH_SHORT).show()
            return
        }
        val shareUri = FileUtils.getShareUri(this, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // File picker
    // ──────────────────────────────────────────────────────────────────────────

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        openFileLauncher.launch(intent)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // RecyclerView Adapter — renders each PDF page as a Bitmap via PdfRenderer
    // ──────────────────────────────────────────────────────────────────────────

    private inner class PdfPageAdapter(
        private val renderer: PdfRenderer,
        private val pageCount: Int
    ) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

        inner class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val imageView = layoutInflater.inflate(
                R.layout.item_pdf_page, parent, false
            ) as ImageView
            return PageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val page = renderer.openPage(position)
            val screenWidth = resources.displayMetrics.widthPixels
            val scale = screenWidth.toFloat() / page.width
            val bitmapWidth = screenWidth
            val bitmapHeight = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            // White background (PdfRenderer renders transparent by default)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            holder.imageView.setImageBitmap(bitmap)
        }

        override fun getItemCount(): Int = pageCount
    }
}
