package id.sch.smkn1gempol.exambro.ui

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.sch.smkn1gempol.exambro.R
import id.sch.smkn1gempol.exambro.databinding.ActivityLandingBinding

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding

    data class Network(val name: String, val url: String, val label: String)

    private val networks = listOf(
        Network("Jaringan Lokal",   "http://192.168.1.100",         "192.168.1.100"),
        Network("Jaringan Online",  "https://lms.semakinpol.my.id", "lms.semakinpol.my.id")
    )
    private var selectedIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardMain.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.slide_up_fade)
        )

        selectNetwork(0)
        binding.cardLocal.setOnClickListener  { selectNetwork(0) }
        binding.cardOnline.setOnClickListener { selectNetwork(1) }

        binding.btnMasuk.setOnClickListener {
            binding.btnMasuk.isEnabled = false
            binding.btnMasuk.text = "Membuka..."

            val net = networks[selectedIndex]
            startActivity(Intent(this, ExamBrowserActivity::class.java).apply {
                putExtra("url",   net.url)
                putExtra("title", net.name)
            })
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

            binding.root.postDelayed({
                binding.btnMasuk.isEnabled = true
                binding.btnMasuk.text = "Buka"
            }, 2000)
        }

        binding.btnKeluar.setOnClickListener { confirmExit() }
    }

    private fun selectNetwork(index: Int) {
        selectedIndex = index
        val pale   = getColor(R.color.blue_pale)
        val normal = getColor(R.color.gray_soft)
        binding.cardLocal.setCardBackgroundColor(if (index == 0) pale else normal)
        binding.cardOnline.setCardBackgroundColor(if (index == 1) pale else normal)
        binding.radioLocal.isChecked  = (index == 0)
        binding.radioOnline.isChecked = (index == 1)
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this, R.style.ExitDialogTheme)
            .setTitle("Keluar Aplikasi?")
            .setMessage("Hubungi pengawas jika ada masalah teknis.")
            .setNegativeButton("Batal") { d, _ -> d.dismiss() }
            .setPositiveButton("Keluar") { _, _ -> finishAffinity() }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { confirmExit() }
}
