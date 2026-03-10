package id.sch.smkn1gempol.exambro.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.text.InputType
import android.widget.EditText
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.sch.smkn1gempol.exambro.R
import id.sch.smkn1gempol.exambro.databinding.ActivityExamBrowserBinding
import id.sch.smkn1gempol.exambro.utils.ExamSession
import id.sch.smkn1gempol.exambro.utils.KioskService
import id.sch.smkn1gempol.exambro.utils.NetworkUtils

class ExamBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExamBrowserBinding
    private var examUrl   = "http://192.168.1.100"
    private var examTitle = "Ujian CBT"
    private var isPinned  = false
    private var lockTimer: CountDownTimer? = null

    // PIN darurat pengawas untuk paksa keluar saat timer masih berjalan
    private val EMERGENCY_PIN = "88876"  // ← Ganti PIN di sini jika perlu

    private val allowedPrefixes = listOf(
        "http://192.168.1.",
        "https://192.168.1.",
        "http://10.",
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

        // Mulai sesi & bersihkan log lama
        ExamSession.startSession(this)

        setupFullscreen()
        setupWebView()
        setupToolbar()
        startKioskService()
        activateScreenLock()
        startLockCountdown()

        loadExamUrl()
    }

    // ─── COUNTDOWN LOCK 30 MENIT ─────────────────────────────────────────────
    private fun startLockCountdown() {
        val totalMs = ExamSession.secondsUntilCanExit(this) * 1000L
        if (totalMs <= 0L) {
            updateLockBadge(false)
            return
        }

        updateLockBadge(true)

        lockTimer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(ms: Long) {
                val secs = ms / 1000L
                val m = secs / 60
                val s = secs % 60
                binding.tvLockTimer.text = "🔒 Terkunci %02d:%02d".format(m, s)
            }
            override fun onFinish() {
                updateLockBadge(false)
                Toast.makeText(
                    this@ExamBrowserActivity,
                    "✅ Waktu minimum ujian selesai. Tombol keluar aktif.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    private fun updateLockBadge(locked: Boolean) {
        if (locked) {
            binding.tvLockTimer.visibility = View.VISIBLE
            binding.btnKeluar.alpha = 0.4f
            binding.btnKeluar.isEnabled = false
        } else {
            binding.tvLockTimer.visibility = View.GONE
            binding.btnKeluar.alpha = 1f
            binding.btnKeluar.isEnabled = true
        }
    }

    // ─── SCREEN LOCK ─────────────────────────────────────────────────────────
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

    // ─── FULLSCREEN ───────────────────────────────────────────────────────────
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

    // ─── KIOSK SERVICE ────────────────────────────────────────────────────────
    private fun startKioskService() {
        val svc = Intent(this, KioskService::class.java).apply {
            putExtra("exam_title", examTitle)
            putExtra("exam_url",   examUrl)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
    }

    // ─── WEBVIEW ──────────────────────────────────────────────────────────────
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
            mixedContentMode      = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode             = WebSettings.LOAD_NO_CACHE
            userAgentString       = "Mozilla/5.0 (Linux; Android 10) ExambroCBT/5.0"
            allowFileAccessFromFileURLs      = false
            allowUniversalAccessFromFileURLs = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.webView, true)
        }

        binding.webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = null

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                return if (allowedPrefixes.any { url.startsWith(it) }) false
                else {
                    Toast.makeText(this@ExamBrowserActivity, "🚫 Diblokir: $url", Toast.LENGTH_SHORT).show()
                    true
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

            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) {
                    binding.progressBar.visibility = View.GONE
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) err.description.toString() else "Gagal"
                    showError("Tidak dapat terhubung ke server.\n\nURL: ${req.url}\nError: $desc\n\nPastikan:\n• HP terhubung ke WiFi sekolah\n• Server ujian menyala")
                }
            }

            override fun onReceivedHttpError(view: WebView, req: WebResourceRequest, resp: WebResourceResponse) {
                if (req.isForMainFrame && resp.statusCode >= 400) {
                    binding.progressBar.visibility = View.GONE
                    showError("Server error HTTP ${resp.statusCode}.\nURL: ${req.url}")
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val url = error.url ?: ""
                val isLocal = url.contains("192.168.") || url.contains("10.0.") ||
                              url.contains("10.10.")   || url.contains("172.16.") ||
                              url.contains("localhost") || url.contains("127.0.0.1")
                if (isLocal) handler.proceed()
                else { handler.cancel(); showError("Koneksi tidak aman ke server online.") }
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

    // ─── TOOLBAR ──────────────────────────────────────────────────────────────
    private fun setupToolbar() {
        binding.tvTitle.text = examTitle
        binding.tvUrl.text   = examUrl
        binding.btnRefresh.setOnClickListener { loadExamUrl() }
        binding.btnBack.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.btnKeluar.setOnClickListener { attemptExit() }
    }

    // ─── EXIT LOGIC ───────────────────────────────────────────────────────────
    private fun attemptExit() {
        if (ExamSession.isLocked(this)) {
            showLockedDialog()
        } else {
            confirmExit()
        }
    }

    /** Dialog saat masih terkunci — ada opsi masukkan PIN darurat */
    private fun showLockedDialog() {
        MaterialAlertDialogBuilder(this, R.style.ExitDialogTheme)
            .setTitle("⏳ Belum Bisa Keluar")
            .setMessage(
                "Minimal ${ExamSession.lockMinutes} menit harus mengerjakan ujian.\n\n" +
                "Sisa waktu: ${ExamSession.formattedTimeRemaining(this)}\n\n" +
                "Jika ada kondisi darurat, pengawas dapat keluar menggunakan PIN."
            )
            .setNeutralButton("PIN Darurat") { _, _ -> showEmergencyPinDialog() }
            .setPositiveButton("Lanjutkan Ujian") { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
    }

    /** Dialog PIN darurat — hanya pengawas yang tahu */
    private fun showEmergencyPinDialog() {
        val input = EditText(this).apply {
            hint = "PIN Darurat"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(60, 36, 60, 36)
            textSize = 20f
        }
        MaterialAlertDialogBuilder(this, R.style.ExitDialogTheme)
            .setTitle("🔐 PIN Darurat Pengawas")
            .setMessage("Masukkan PIN untuk paksa keluar dari sesi ujian.")
            .setView(input)
            .setNegativeButton("Batal") { d, _ ->
                d.dismiss()
                showLockedDialog() // kembali ke dialog sebelumnya
            }
            .setPositiveButton("Konfirmasi") { _, _ ->
                if (input.text.toString() == EMERGENCY_PIN) {
                    doExit()
                } else {
                    android.widget.Toast.makeText(this, "❌ PIN salah!", android.widget.Toast.LENGTH_SHORT).show()
                    showLockedDialog()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this, R.style.ExitDialogTheme)
            .setTitle("Akhiri Ujian?")
            .setMessage("Yakin ingin keluar dari sesi ujian?")
            .setNegativeButton("Batal") { d, _ -> d.dismiss() }
            .setPositiveButton("Keluar") { _, _ ->
                doExit()
            }
            .show()
    }

    private fun doExit() {
        lockTimer?.cancel()
        stopService(Intent(this, KioskService::class.java))
        if (isPinned) { try { stopLockTask() } catch (e: Exception) { } }
        startActivity(Intent(this, LandingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun loadExamUrl() {
        if (NetworkUtils.isConnected(this)) {
            binding.layoutError.visibility = View.GONE
            binding.webView.visibility     = View.VISIBLE
            binding.webView.clearCache(true)
            binding.webView.clearHistory()
            binding.webView.loadUrl(examUrl)
        } else {
            showError("Tidak ada koneksi jaringan.\nPeriksa WiFi dan coba lagi.")
        }
    }

    private fun showError(msg: String) {
        binding.layoutError.visibility = View.VISIBLE
        binding.webView.visibility     = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.tvErrorMsg.text        = msg
        binding.btnRetry.setOnClickListener { loadExamUrl() }
    }

    // ─── BACK BUTTON ─────────────────────────────────────────────────────────
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (binding.webView.canGoBack()) binding.webView.goBack()
                else {
                    attemptExit()
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullscreen()
    }

    override fun onDestroy() {
        lockTimer?.cancel()
        binding.webView.destroy()
        super.onDestroy()
    }
}
