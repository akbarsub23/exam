package id.sch.smkn1gempol.exambro.utils

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import id.sch.smkn1gempol.exambro.R
import id.sch.smkn1gempol.exambro.ui.ExamBrowserActivity

class KioskService : Service() {

    companion object {
        const val CHANNEL_ID = "exambro_session"
        const val NOTIF_ID   = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("exam_title") ?: "Ujian Berlangsung"
        startForeground(NOTIF_ID, buildNotif(title))
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Jika app di-swipe dari recents (seharusnya tidak bisa saat locked),
        // restart ke halaman ujian sebagai fallback
        startActivity(
            Intent(applicationContext, ExamBrowserActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotif(title: String): Notification {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, ExamBrowserActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 $title")
            .setContentText("Sesi ujian aktif — dilarang keluar dari aplikasi")
            .setSmallIcon(R.drawable.ic_lock_exam)
            .setOngoing(true)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Sesi Ujian CBT",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(false)
                description = "Notifikasi sesi ujian aktif"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
