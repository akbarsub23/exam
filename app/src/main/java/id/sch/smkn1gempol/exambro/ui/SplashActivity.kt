package id.sch.smkn1gempol.exambro.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebView
import id.sch.smkn1gempol.exambro.R
import id.sch.smkn1gempol.exambro.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pre-inisialisasi WebView engine di background — kurangi cold-start pertama kali
        WebView(applicationContext).destroy()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animasi logo
        binding.imgLogo.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.pulse_scale)
        )
        binding.layoutText.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.fade_slide_up)
        )
        // Cincin berputar
        binding.viewRing.animate()
            .rotation(360f).setDuration(2000).setStartDelay(100)
            .withEndAction { binding.viewRing.rotation = 0f }
            .start()

        // Langsung ke Landing — tanpa setup apapun
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LandingActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 2600)
    }
}
