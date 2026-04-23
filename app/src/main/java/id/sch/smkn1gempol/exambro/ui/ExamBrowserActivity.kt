package id.sch.smkn1gempol.exambro.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.sch.smkn1gempol.exambro.R
import id.sch.smkn1gempol.exambro.databinding.ActivityExamBrowserBinding
import id.sch.smkn1gempol.exambro.utils.ExamSession
import id.sch.smkn1gempol.exambro.utils.KioskService

class ExamBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExamBrowserBinding
    private var examUrl   = "http://192.168.1.100"
    private var examTitle = "Ujian CBT"
    private var isPinned  = false
    private var lockTimer: CountDownTimer? = null

    private val EMERGENCY_PIN = "88876"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExamBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        examUrl   = intent.getStringExtra("url")   ?: examUrl
        examTitle = intent.getStringExtra("title") ?: examTitle

        ExamSession.startSession(this)

        setupFullscreen()
        setupUI()
        startKioskService()
        activateScreenLock()
        startLockCountdown()

        // Buka Chrome langsung ke URL Moodle
        openInChrome()
    }

    // ─── BUKA CHROME ─────────────────────────────────────────────────────────
    private fun openInChrome() {
        try {
            // Coba Custom Tabs dulu (Chrome terintegrasi)
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            customTabsIntent.launchUrl(this, Uri.parse(examUrl))
        } catch (e: Exception) {
            // Fallback: buka browser default
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(examUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Tidak ada browser terinstall!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─── UI ───────────────────────────────────────────────────────────────────
    private fun setupUI() {
        binding.tvTitle.text = examTitle
        binding.tvUrl.text   = examUrl

        // Tombol buka ulang Chrome jika siswa tidak sengaja tutup
        binding.btnRefresh.setOnClickListener { openInChrome() }
        binding.btnBack.visibility    = View.GONE  // tidak relevan
        binding.btnKeluar.setOnClickListener { attemptExit() }

        // Tampilkan instruksi di layar utama
        binding.webView.visibility      = View.GONE
        binding.layoutError.visibility  = View.GONE
        binding.progressBar.visibility  = View.GONE
        showStandbyScreen()
    }

    private fun showStandbyScreen() {
        // Tampilkan layar standby saat Chrome terbuka di atas
        binding.layoutStandby.visibility = View.VISIBLE
        binding.tvStandbyTitle.text  = "🌐 Ujian Berjalan di Browser"
        binding.tvStandbyDesc.text   =
            "Browser Chrome telah dibuka dengan halaman ujian.\n\n" +
            "Jika browser tertutup, tap tombol di bawah untuk membuka ulang."
        binding.btnReopenBrowser.setOnClickListener { openInChrome() }
    }

    // ─── COUNTDOWN LOCK ──────────────────────────────────────────────────────
    private fun startLockCountdown() {
        val totalMs = ExamSession.secondsUntilCanExit(this) * 1000L
        if (totalMs <= 0L) { updateLockBadge(false); return }
        updateLockBadge(true)
        lockTimer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(ms: Long) {
                val secs = ms / 1000L
                binding.tvLockTimer.text = "🔒 %02d:%02d".format(secs / 60, secs % 60)
            }
            override fun onFinish() {
                updateLockBadge(false)
                Toast.makeText(this@ExamBrowserActivity,
                    "✅ Waktu minimum selesai. Tombol keluar aktif.",
                    Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun updateLockBadge(locked: Boolean) {
        binding.tvLockTimer.visibility = if (locked) View.VISIBLE else View.GONE
        binding.btnKeluar.alpha        = if (locked) 0.4f else 1f
        binding.btnKeluar.isEnabled    = !locked
    }

    // ─── SCREEN LOCK ─────────────────────────────────────────────────────────
    private fun activateScreenLock() {
        try {
            startLockTask(); isPinned = true
            binding.bannerLocked.visibility = View.VISIBLE
            binding.bannerLocked.postDelayed(
                { binding.bannerLocked.visibility = View.GONE }, 3000)
        } catch (e: Exception) { }
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
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

    // ─── EXIT ─────────────────────────────────────────────────────────────────
    private fun attemptExit() {
        if (ExamSession.isLocked(this)) showLockedDialog() else confirmExit()
    }

    private fun showLockedDialog() {
        MaterialAlertDialogBuilder(this, R.style.ExitDialogTheme)
            .setTitle("⏳ Belum Bisa Keluar")
            .setMessage(
                "Minimal ${ExamSession.LOCK_MINUTES} menit harus mengerjakan ujian.\n\n" +
                "Sisa waktu: ${ExamSession.formattedTimeRemaining(this)}\n\n" +
                "Jika ada kondisi darurat, pengawas dapat keluar menggunakan PIN."
            )
            .setNeutralButton("PIN Darurat") { _, _ -> showEmergencyPinDialog() }
            .setPositiveButton("Lanjutkan Ujian") { d, _ -> d.dismiss() }
            .setCancelable(false).show()
    }

    private fun showEmergencyPinDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "PIN Darurat"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(60, 36, 60, 36); textSize = 20f
        }
        MaterialAlertDialogBuilder(this, R.style.ExitDialogTheme)
            .setTitle("🔐 PIN Darurat Pengawas")
            .setMessage("Masukkan PIN untuk paksa keluar dari sesi ujian.")
            .setView(input)
            .setNegativeButton("Batal") { d, _ -> d.dismiss(); showLockedDialog() }
            .setPositiveButton("Konfirmasi") { _, _ ->
                if (input.text.toString() == EMERGENCY_PIN) doExit()
                else { Toast.makeText(this, "❌ PIN salah!", Toast.LENGTH_SHORT).show()
                       showLockedDialog() }
            }
            .setCancelable(false).show()
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this, R.style.ExitDialogTheme)
            .setTitle("Akhiri Ujian?")
            .setMessage("Yakin ingin keluar dari sesi ujian?")
            .setNegativeButton("Batal") { d, _ -> d.dismiss() }
            .setPositiveButton("Keluar") { _, _ -> doExit() }
            .show()
    }

    private fun doExit() {
        lockTimer?.cancel()
        stopService(Intent(this, KioskService::class.java))
        if (isPinned) try { stopLockTask() } catch (e: Exception) { }
        startActivity(Intent(this, LandingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { attemptExit(); true }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullscreen()
    }

    override fun onDestroy() {
        lockTimer?.cancel()
        super.onDestroy()
    }
}
