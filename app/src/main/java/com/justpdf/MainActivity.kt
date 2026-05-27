package com.justpdf

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var webView: WebView
    private lateinit var toolbar: LinearLayout
    private lateinit var btnShare: ImageButton
    private lateinit var btnOpenFile: ImageButton
    private lateinit var tvPageCounter: TextView
    private lateinit var tvEmpty: TextView

    // ── PDF state ──────────────────────────────────────────────────────────────
    private var currentFile: File? = null

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        webView       = findViewById(R.id.webView)
        toolbar       = findViewById(R.id.toolbar)
        btnShare      = findViewById(R.id.btnShare)
        btnOpenFile   = findViewById(R.id.btnOpenFile)
        tvPageCounter = findViewById(R.id.tvPageCounter)
        tvEmpty       = findViewById(R.id.tvEmpty)

        setupWebView()

        btnShare.setOnClickListener { sharePdf() }
        btnOpenFile.setOnClickListener { openFilePicker() }

        handleIncomingIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && currentFile != null) enterImmersiveMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WebView setup
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            // Allow the viewer.html (file://) to load pdf.mjs (also file://)
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
            builtInZoomControls = true
            displayZoomControls = false   // hide the +/- overlay buttons
            setSupportZoom(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        // Expose Android bridge to JavaScript
        webView.addJavascriptInterface(PdfJsBridge(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // viewer.html is loaded — now tell it which PDF to open
                currentFile?.let { file ->
                    val fileUrl = "file://${file.absolutePath}"
                    view.evaluateJavascript("window.loadPdf('$fileUrl')", null)
                }
            }

            // Intercept file:// requests for the PDF so we can serve it from
            // the app's private files directory (content:// URIs can't be
            // loaded directly by WebView).
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val path = request.url.path ?: return null
                val file = File(path)
                if (file.exists() && path.endsWith(".pdf", ignoreCase = true)) {
                    return try {
                        WebResourceResponse(
                            "application/pdf",
                            null,
                            FileInputStream(file)
                        )
                    } catch (_: Exception) { null }
                }
                return null
            }
        }

        // Load the viewer shell — PDF will be injected in onPageFinished
        webView.loadUrl("file:///android_asset/viewer.html")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // JavaScript → Android bridge
    // ──────────────────────────────────────────────────────────────────────────

    private inner class PdfJsBridge {

        /** Called by viewer.html once the pdf.js module is ready. */
        @JavascriptInterface
        fun onViewerReady() {
            runOnUiThread {
                currentFile?.let { file ->
                    val fileUrl = "file://${file.absolutePath}"
                    webView.evaluateJavascript("window.loadPdf('$fileUrl')", null)
                }
            }
        }

        /** Called by viewer.html after all pages have been rendered. */
        @JavascriptInterface
        fun onPdfLoaded(pageCount: Int) {
            runOnUiThread {
                tvEmpty.visibility = View.GONE
                webView.visibility = View.VISIBLE
                updatePageCounter(1, pageCount)
                // Defer immersive mode by 600ms so the system doesn't intercept
                // the first long-press to transiently show the navigation bar.
                Handler(Looper.getMainLooper()).postDelayed({ enterImmersiveMode() }, 600)
            }
        }

        /** Called by viewer.html if pdf.js fails to open the document. */
        @JavascriptInterface
        fun onPdfError(@Suppress("UNUSED_PARAMETER") message: String) {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_cannot_open),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Immersive / navigation-bar hiding
    // ──────────────────────────────────────────────────────────────────────────

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
        currentFile = file
        // Reset zoom to 100% and scroll to top before loading new document
        webView.setInitialScale(0)
        webView.scrollTo(0, 0)
        val fileUrl = "file://${file.absolutePath}"
        // If the viewer is already loaded, call loadPdf() directly;
        // otherwise it will be called from onPageFinished / onViewerReady.
        webView.evaluateJavascript("if(window.loadPdf) window.loadPdf('$fileUrl')", null)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Toolbar
    // ──────────────────────────────────────────────────────────────────────────

    private fun updatePageCounter(page: Int, pageCount: Int) {
        tvPageCounter.text = getString(R.string.page_counter, page, pageCount)
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
}
