package asia.axientstudio.quickauth.android

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import asia.axientstudio.quickauth.android.data.SecureStorage
import asia.axientstudio.quickauth.android.network.SyncManager
import asia.axientstudio.quickauth.android.security.BiometricAuthManager
import asia.axientstudio.quickauth.android.ui.ImportScreen
import asia.axientstudio.quickauth.android.ui.MainScreen
import asia.axientstudio.quickauth.android.ui.SettingsScreen
import asia.axientstudio.quickauth.android.ui.SyncFirstLaunchDialog
import asia.axientstudio.quickauth.android.ui.SyncSetupDialog
import asia.axientstudio.quickauth.android.ui.theme.QuickAuthTheme
import kotlinx.coroutines.launch

private const val PREFS_NAME = "app_prefs"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_HAS_PROMPTED_SYNC = "has_prompted_sync_setup"

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val secureStorage = SecureStorage(this)
        val syncManager = SyncManager(this, secureStorage)
        val appPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val biometricAuthManager = BiometricAuthManager(this)

        setContent {
            val scope = rememberCoroutineScope()
            var isAuthenticated by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                biometricAuthManager.authenticate(
                    onSuccess = { isAuthenticated = true },
                    onError = { finish() }
                )
            }

            if (!isAuthenticated) return@setContent

            var themeMode by remember { mutableStateOf(appPrefs.getString(KEY_THEME_MODE, "System") ?: "System") }
            var showSettings by remember { mutableStateOf(false) }
            val showImport = remember { mutableStateOf(false) }
            var accountsMap by remember { mutableStateOf(secureStorage.getAllAccounts().mapValues { it.value?.toString() ?: "" }) }

            fun refreshAccounts() {
                accountsMap = secureStorage.getAllAccounts().mapValues { it.value?.toString() ?: "" }
            }

            // First-run sync prompt, ported from Python's setup_sync():
            // - Never prompted before -> ask "Enable Code Sync? Yes/No" once.
            // - Already configured -> sync silently on startup, like Python's
            //   `perform_sync(store)` call when __sync_enabled__ is already set.
            var hasPromptedSync by remember { mutableStateOf(appPrefs.getBoolean(KEY_HAS_PROMPTED_SYNC, false)) }
            var showFirstLaunchPrompt by remember {
                mutableStateOf(!hasPromptedSync && !syncManager.isSyncEnabled)
            }
            var showSyncSetupFromPrompt by remember { mutableStateOf(false) }

            LaunchedEffect(isAuthenticated) {
                if (isAuthenticated && hasPromptedSync && syncManager.isSyncEnabled) {
                    syncManager.performSync()
                    refreshAccounts()
                }
            }

            // Handle Back button
            BackHandler(enabled = showSettings || showImport.value) {
                if (showSettings) showSettings = false
                if (showImport.value) showImport.value = false
            }

            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            QuickAuthTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showFirstLaunchPrompt) {
                        SyncFirstLaunchDialog(
                            onEnable = {
                                showFirstLaunchPrompt = false
                                appPrefs.edit().putBoolean(KEY_HAS_PROMPTED_SYNC, true).apply()
                                hasPromptedSync = true
                                showSyncSetupFromPrompt = true
                            },
                            onSkip = {
                                showFirstLaunchPrompt = false
                                appPrefs.edit().putBoolean(KEY_HAS_PROMPTED_SYNC, true).apply()
                                hasPromptedSync = true
                                syncManager.disableSync()
                            }
                        )
                    } else if (showSyncSetupFromPrompt) {
                        SyncSetupDialog(
                            syncManager = syncManager,
                            onDismiss = { showSyncSetupFromPrompt = false },
                            onSuccess = {
                                showSyncSetupFromPrompt = false
                                scope.launch {
                                    syncManager.performSync()
                                    refreshAccounts()
                                }
                            }
                        )
                    } else if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                appPrefs.edit().putString(KEY_THEME_MODE, mode).apply()
                            },
                            syncManager = syncManager,
                            onSyncStateChanged = {
                                // Accounts may have been updated/merged by a sync pass;
                                // refresh the in-memory map shown on MainScreen.
                                refreshAccounts()
                            }
                        )
                    } else if (showImport.value) {
                        ImportScreen(
                            onBack = { showImport.value = false },
                            onImportGallery = { uri -> Toast.makeText(this, "Importing: $uri", Toast.LENGTH_SHORT).show() },
                            onScanCamera = { Toast.makeText(this, "Camera scan not yet fully implemented", Toast.LENGTH_SHORT).show() }
                        )
                    } else {
                        MainScreen(
                            accounts = accountsMap,
                            onOpenSettings = { showSettings = true },
                            onNavigateToImport = { showImport.value = true },
                            onDeleteAccount = { name ->
                                secureStorage.deleteAccount(name)
                                refreshAccounts()
                            },
                            onAddAccount = { name, secret ->
                                secureStorage.saveAccount(name, secret)
                                refreshAccounts()
                            }
                        )
                    }
                }
            }
        }
    }
}
