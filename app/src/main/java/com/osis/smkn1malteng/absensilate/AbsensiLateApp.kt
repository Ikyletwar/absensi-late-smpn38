package com.osis.smkn1malteng.absensilate

import android.app.Application
import android.util.Log
import com.osis.smkn1malteng.absensilate.ui.theme.ThemeManager

class AbsensiLateApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 🔥 Global crash handler: log exception SEBELUM process mati
        //    Baca logcat dengan: adb logcat -s "CRASH:"
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CRASH", "=== UNCAUGHT EXCEPTION in ${thread.name} ===", throwable)
            Log.e("CRASH", "Message: ${throwable.message}")
            Log.e("CRASH", "Stack: ${throwable.stackTraceToString()}")
            // Re-throw agar default handler tetap kill process (biar crash terlihat di UI)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.d("AbsensiLateApp", "Application.onCreate: crash handler installed")

        // 🔥 Load tema gelap/terang tersimpan
        ThemeManager.init(this)
        Log.d("AbsensiLateApp", "Application.onCreate: theme loaded (${if (ThemeManager.isDarkTheme) "dark" else "light"})")
    }

    companion object {
        private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    }

    init {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    }
}
