package com.justpdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
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

        setContentView(R.layout.activity_main)

        recyclerView  = findViewById(R.id.recyclerView)
        toolbar       = findViewById(R.id.toolbar)
        btnShare      = findViewById(R.id.btnShare)
        btnOpenFile   = findViewById(R.id.btnOpenFile)
        tvPageCounter = findViewById(R.id.tvPageCounter)
        tvEmpty       = findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Show toolbar (and temporarily restore nav bar) on tap
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-apply immersive mode whenever the window regains focus
        // (e.g. after a dialog or system overlay is dismissed).
        if (hasFocus && currentFile != null) {
            enterImmersiveMode()
        }
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
    // Immersive / navigation-bar hiding
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Hides the system navigation bar (and status bar) using the appropriate
     * API for the running Android version.
     *
     * - API 30+: [WindowInsetsController] with BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
     * - API 21–29: legacy [View.SYSTEM_UI_FLAG_HIDE_NAVIGATION] + IMMERSIVE_STICKY
     */
    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.systemBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    /** Temporarily restore system bars so the user can interact with the toolbar. */
    private fun exitImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
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
        enterImmersiveMode()
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
        // Temporarily show nav bar so the user can interact with the toolbar
        if (currentFile != null) exitImmersiveMode()
        scheduleHideToolbar()
    }

    private fun hideToolbar() {
        toolbar.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                toolbar.visibility = View.GONE
                // Re-enter immersive mode once toolbar is gone
                if (currentFile != null) enterImmersiveMode()
            }
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

        inner class PageViewHolder(
            val zoomLayout: ZoomLayout,
            val imageView: ImageView
        ) : RecyclerView.ViewHolder(zoomLayout)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val zoomLayout = layoutInflater.inflate(
                R.layout.item_pdf_page, parent, false
            ) as ZoomLayout
            val imageView = zoomLayout.findViewById<ImageView>(R.id.pageImage)
            return PageViewHolder(zoomLayout, imageView)
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
