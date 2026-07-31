package asia.axientstudio.quickauth.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import asia.axientstudio.quickauth.android.data.SecureStorage
import asia.axientstudio.quickauth.android.network.SyncManager
import asia.axientstudio.quickauth.android.ui.ImportScreen
import asia.axientstudio.quickauth.android.ui.MainScreen
import asia.axientstudio.quickauth.android.ui.SettingsScreen
import asia.axientstudio.quickauth.android.ui.theme.QuickAuthTheme
import kotlinx.coroutines.launch

private const val PREFS_NAME = "app_prefs"
private const val KEY_THEME_MODE = "theme_mode"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val secureStorage = SecureStorage(this)
        val syncManager = SyncManager(this, secureStorage)

        // Theme preference is not sensitive, so a plain (unencrypted) prefs file is fine here.
        val appPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            val scope = rememberCoroutineScope()
            var themeMode by remember {
                mutableStateOf(appPrefs.getString(KEY_THEME_MODE, "System") ?: "System")
            }
            var showSettings by remember { mutableStateOf(false) }

            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            QuickAuthTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(targetState = showSettings, label = "SettingsTransition") { targetShowSettings ->
                        if (targetShowSettings) {
                            SettingsScreen(
                                onBack = { showSettings = false },
                                themeMode = themeMode,
                                onThemeModeChange = { mode ->
                                    themeMode = mode
                                    appPrefs.edit().putString(KEY_THEME_MODE, mode).apply()
                                },
                                onPerformSync = { scope.launch { syncManager.performSync() } }
                            )
                        } else {
                            // ...

                        // TODO: Implement proper navigation
                        val showImport = remember { mutableStateOf(false) }
                        
                        if (showImport.value) {
                            ImportScreen(
                                onBack = { showImport.value = false },
                                onImportGallery = { /* TODO */ },
                                onScanCamera = { /* TODO */ }
                            )
                        } else {
                            // Convert map of <String, *> to Map<String, String>
                            val accountsMap = secureStorage.getAllAccounts().mapValues {
                                it.value?.toString() ?: ""
                            }
                            MainScreen(
                                accounts = accountsMap,
                                onOpenSettings = { showSettings = true },
                                onNavigateToImport = { showImport.value = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
