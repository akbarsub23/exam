package id.sch.smkn1gempol.exambro.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Mengelola timer sesi ujian.
 * Siswa tidak bisa keluar selama LOCK_MINUTES menit pertama.
 */
object ExamSession {

    private const val PREF_NAME    = "exam_session"
    private const val KEY_START    = "session_start"
    const val LOCK_MINUTES         = 30L  // ← Ganti di sini jika ingin ubah durasi

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** Mulai sesi baru */
    fun startSession(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_START, System.currentTimeMillis()).apply()
    }

    /** Berapa detik tersisa sampai boleh keluar */
    fun secondsUntilCanExit(ctx: Context): Long {
        val start = prefs(ctx).getLong(KEY_START, 0L)
        if (start == 0L) return 0L
        val elapsed = (System.currentTimeMillis() - start) / 1000L
        return maxOf(0L, LOCK_MINUTES * 60L - elapsed)
    }

    /** Apakah masih dalam periode terkunci */
    fun isLocked(ctx: Context): Boolean = secondsUntilCanExit(ctx) > 0L

    /** Format sisa waktu: "28:45" */
    fun formattedTimeRemaining(ctx: Context): String {
        val secs = secondsUntilCanExit(ctx)
        return "%02d:%02d".format(secs / 60, secs % 60)
    }
}
