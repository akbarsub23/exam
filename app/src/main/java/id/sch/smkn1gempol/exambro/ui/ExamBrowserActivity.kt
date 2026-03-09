package id.sch.smkn1gempol.exambro.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.sch.smkn1gempol.exambro.R
import id.sch.smkn1gempol.exambro.databinding.ActivityExamBrowserBinding
import id.sch.smkn1gempol.exambro.utils.KioskService
import id.sch.smkn1gempol.exambro.utils.NetworkUtils

class ExamBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExamBrowserBinding
    private var examUrl   = "http://192.168.1.100"
    private var examTitle = "Ujian CBT"
    private var isPinned  = false

    // Prefix URL yang diizinkan — semua sub-path Moodle otomatis lolos
    private val allowedPrefixes = listOf(
        "http://192.168.1.",   // seluruh subnet lokal (handle redirect Moodle)
        "https://192.168.1.",
        "http://10.",          // subnet alternatif
        "https://10.",
        "http://localhost",
        "http://127.0.0.1",
        "https://lms.semakinpol.my.id"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExamBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        examUrl   = intent.getStringExtra("url")   ?: examUrl
        examTitle = intent.getStringExtra("title") ?: examTitle

        setupFullscreen()
        setupWebView()
        setupToolbar()
        startKioskService()
        activateScreenLock()

        loadExamUrl()
    }

    private fun loadExamUrl() {
        if (NetworkUtils.isConnected(this)) {
            binding.layoutError.visibility = View.GONE
            binding.webView.visibility     = View.VISIBLE
            // Hapus cache dan history agar tidak ada redirect tersimpan
            binding.webView.clearCache(true)
            binding.webView.clearHistory()
            binding.webView.loadUrl(examUrl)
        } else {
            showError("Tidak ada koneksi jaringan.\nPeriksa WiFi dan coba lagi.")
        }
    }

    private fun activateScreenLock() {
        try {
            startLockTask()
            isPinned = true
            binding.bannerLocked.visibility = View.VISIBLE
            binding.bannerLocked.postDelayed({ binding.bannerLocked.visibility = View.GONE }, 3000)
        } catch (e: Exception) {
            Toast.makeText(this, "Mode ujian aktif.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    private fun startKioskService() {
        val svc = Intent(this, KioskService::class.java).apply {
            putExtra("exam_title", examTitle)
            putExtra("exam_url",   examUrl)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled     = true
            domStorageEnabled     = true
            databaseEnabled       = true
            loadWithOverviewMode  = true
            useWideViewPort       = true
            builtInZoomControls   = false
            setSupportZoom(false)
            // PENTING: izinkan semua konten termasuk HTTP
            mixedContentMode      = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Izinkan cleartext (HTTP) secara eksplisit di WebView
            @Suppress("DEPRECATION")
            allowFileAccess       = true
            cacheMode             = WebSettings.LOAD_NO_CACHE
            userAgentString       = "Mozilla/5.0 (Linux; Android 10) ExambroCBT/4.0"
            allowFileAccessFromFileURLs      = false
            allowUniversalAccessFromFileURLs = false
        }

        // Izinkan cookie
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.webView, true)
        }

        binding.webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // Jangan intercept — biarkan WebView handle semua request termasuk HTTP
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()

                // Izinkan jika URL diawali salah satu prefix yang diperbolehkan
                // Ini menangani semua redirect internal Moodle (/login, /course, dll)
                return if (allowedPrefixes.any { url.startsWith(it) }) {
                    false // lanjutkan di WebView
                } else {
                    Toast.makeText(
                        this@ExamBrowserActivity,
                        "🚫 Akses diblokir: $url",
                        Toast.LENGTH_SHORT
                    ).show()
                    true // blokir
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
                binding.webView.visibility     = View.VISIBLE
                binding.layoutError.visibility = View.GONE
                binding.tvUrl.text = url
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.progressBar.visibility = View.GONE
                binding.tvTitle.text = view.title?.takeIf { it.isNotBlank() } ?: examTitle
                injectSecurityJS(view)
            }

            override fun onReceivedError(
                view: WebView,
                req: WebResourceRequest,
                err: WebResourceError
            ) {
                if (req.isForMainFrame) {
                    binding.progressBar.visibility = View.GONE
                    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) err.errorCode else -1
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) err.description.toString() else "Gagal"
                    showError("Tidak dapat terhubung ke server.\n\nURL: ${req.url}\nError: $desc (kode $code)\n\nPastikan:\n• HP terhubung ke WiFi sekolah\n• Server ujian menyala")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                req: WebResourceRequest,
                resp: WebResourceResponse
            ) {
                if (req.isForMainFrame && resp.statusCode >= 400) {
                    binding.progressBar.visibility = View.GONE
                    showError("Server merespons error HTTP ${resp.statusCode}.\nURL: ${req.url}")
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                binding.loadingBar.progress = p
                binding.loadingBar.visibility = if (p < 100) View.VISIBLE else View.GONE
            }
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                MaterialAlertDialogBuilder(this@ExamBrowserActivity)
                    .setMessage(message).setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setCancelable(false).show(); return true
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                MaterialAlertDialogBuilder(this@ExamBrowserActivity).setMessage(message)
                    .setPositiveButton("Ya") { _, _ -> result.confirm() }
                    .setNegativeButton("Tidak") { _, _ -> result.cancel() }
                    .setCancelable(false).show(); return true
            }
        }
    }

    private fun injectSecurityJS(view: WebView) {
        view.evaluateJavascript("""
            (function(){
                document.addEventListener('contextmenu',e=>e.preventDefault(),true);
                document.addEventListener('selectstart',e=>e.preventDefault(),true);
                document.addEventListener('copy',e=>e.preventDefault(),true);
                document.addEventListener('cut',e=>e.preventDefault(),true);
                var s=document.createElement('style');
                s.innerHTML='*{-webkit-user-select:none!important;user-select:none!important;}input,textarea,[contenteditable]{-webkit-user-select:text!important;user-select:text!important;}';
                document.head&&document.head.appendChild(s);
            })();
        """.trimIndent(), null)
    }

    private fun setupToolbar() {
        binding.tvTitle.text = examTitle
        binding.tvUrl.text   = examUrl
        binding.btnRefresh.setOnClickListener { loadExamUrl() }
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }
        binding.btnKeluar.setOnClickListener { doExit() }
    }

    private fun doExit() {
        stopService(Intent(this, KioskService::class.java))
        if (isPinned) { try { stopLockTask() } catch (e: Exception) { } }
        startActivity(Intent(this, LandingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun showError(msg: String) {
        binding.layoutError.visibility = View.VISIBLE
        binding.webView.visibility     = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.tvErrorMsg.text        = msg
        binding.btnRetry.setOnClickListener { loadExamUrl() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (binding.webView.canGoBack()) binding.webView.goBack() else doExit(); true
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullscreen()
    }

    override fun onDestroy() { binding.webView.destroy(); super.onDestroy() }
}
