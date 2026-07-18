package dev.gatsyuk.soloranking

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.gatsyuk.soloranking.app.SoloRankingApp
import dev.gatsyuk.soloranking.core.ui.theme.SoloRankingTheme
import dev.gatsyuk.soloranking.feature.profile.SettingsViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Android 13+ gate for the "workout in progress" notification;
    // declining just means no notification, nothing else changes.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            SoloRankingTheme(themeMode = themeMode) {
                SoloRankingApp()
            }
        }
    }
}
