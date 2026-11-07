package com.simpletools.area

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import com.simpletools.area.core.settings.AppLanguage
import com.simpletools.area.core.settings.AppSettings
import com.simpletools.area.ui.theme.AreaTheme
import com.simpletools.area.ui.theme.isDark

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AreaApp
        setContent {
            val settings by app.settings.settings.collectAsState(initial = AppSettings())
            LaunchedEffect(settings.language) { applyLanguage(settings.language) }
            AreaTheme(settings.theme) {
                val dark = isDark(settings.theme)
                val view = LocalView.current
                SideEffect {
                    val c = WindowCompat.getInsetsController(window, view)
                    c.isAppearanceLightStatusBars = !dark
                    c.isAppearanceLightNavigationBars = !dark
                }
                AppNav(this@MainActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as AreaApp).billing.refresh()
    }

    private fun applyLanguage(lang: AppLanguage) {
        val wanted = LocaleListCompat.forLanguageTags(lang.tag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }
}
