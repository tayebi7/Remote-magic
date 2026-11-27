package com.remotemagic.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remotemagic.app.navigation.AppRoot
import com.remotemagic.app.settings.SettingsViewModel
import com.remotemagic.app.settings.ThemeMode
import com.remotemagic.app.ui.RemoteTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsModel: SettingsViewModel = viewModel()
            val settings by settingsModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            RemoteTheme(dark) { AppRoot(settingsModel) }
        }
    }
}
