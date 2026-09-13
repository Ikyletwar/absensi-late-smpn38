package com.osis.smkn1malteng.absensilate.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeManager {
    private const val PREFS_NAME = "absensi_late_prefs"
    private const val KEY_DARK_THEME = "dark_theme"

    var isDarkTheme by mutableStateOf(false)
        private set

    private var appContext: Context? = null

    // 🔥 Load tema tersimpan saat app start (proses baru).
    //    Tanpa persist, tema akan selalu balik ke light saat app dibuka ulang.
    fun init(context: Context) {
        appContext = context.applicationContext
        isDarkTheme = appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_DARK_THEME, false) ?: false
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
        persist()
    }

    private fun persist() {
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_DARK_THEME, isDarkTheme)
            ?.apply()
    }
}