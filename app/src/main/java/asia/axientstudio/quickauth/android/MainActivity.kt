package asia.axientstudio.quickauth.android

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import asia.axientstudio.quickauth.android.data.SecureStorage
import asia.axientstudio.quickauth.android.network.SyncManager
import asia.axientstudio.quickauth.android.security.BiometricAuthManager
import asia.axientstudio.quickauth.android.security.LockPreferences
import asia.axientstudio.quickauth.android.security.LockTimeout
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
private const val KEY_LANGUAGE_TAG = "language_tag"

/**
 * Applies (and persists) the app's per-app language. Uses
 * AppCompatDelegate.setApplicationLocales(), the AndroidX-recommended API for
 * runtime language switching — it works down to API 24 via the AppCompat
 * backport and defers to the platform LocaleManager on Android 13+.
 * MainActivity must extend AppCompatActivity (not just FragmentActivity) for
 * this to actually take effect on the Compose UI; a plain FragmentActivity
 * silently ignores the call.
 */
private fun applyLanguage(context: Context, languageTag: String) {
    val localeList = if (languageTag.isBlank()) {
        LocaleListCompat.getEmptyLocaleList() // follow system
    } else {
        LocaleListCompat.forLanguageTags(languageTag)
    }
    AppCompatDelegate.setApplicationLocales(localeList)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_LANGUAGE_TAG, languageTag).apply()
}

class MainActivity : AppCompatActivity() {

    // Timestamp (elapsedRealtime-independent wall clock is fine here since we
    // only care about "how long was the app backgrounded") of the last time
    // the whole app (not just this activity) left the foreground. Read/written
    // from a process-wide lifecycle observer so switching between in-app
    // screens (Settings, Import) never counts as "leaving the app".
    private var backgroundedAtMillis: Long? = null

    // Bumped every time this Activity resumes. Used as a LaunchedEffect key so
    // the biometric prompt is re-shown on every resume while unauthenticated —
    // this does NOT depend on isAuthenticated changing value.
    //
    // Why this is needed: BiometricPrompt auto-dismisses itself when the
    // Activity leaves the foreground (this is documented, intentional
    // platform behavior for security). On some devices/OS versions, that
    // auto-dismiss does not reliably invoke onAuthenticationError, so if the
    // user backgrounds the app before completing/cancelling the prompt (e.g.
    // taps Home a split second after the prompt appears), neither onSuccess
    // nor onError fires. isAuthenticated is then stuck at false with no event
    // to react to, and because LaunchedEffect(isAuthenticated) only reruns
    // when the key's *value* changes, re-opening the app just resumes the
    // same Activity into a permanently blank screen (isAuthenticated was
    // already false, so the key doesn't change) — recoverable only by force-
    // stopping or swiping the app away. resumeTrigger guarantees a fresh
    // attempt on every single resume regardless of whether isAuthenticated
    // actually changed.
    private var resumeTrigger by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTrigger++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Re-apply the saved language before the Activity/Compose content is
        // created, so the very first frame is already in the right language.
        val savedLanguageTag = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null)
        if (!savedLanguageTag.isNullOrBlank()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLanguageTag))
        }

        super.onCreate(savedInstanceState)

        // Screen-capture protection: FLAG_SECURE blocks screenshots and screen
        // recording for this window (including third-party recording/casting
        // apps), renders the window's content as blank/black in the system
        // Recents (app switcher) thumbnail, and blocks screen mirroring/casting.
        // This must be set before setContent() and applies for the entire
        // lifetime of the activity/window.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val secureStorage = SecureStorage(this)
        val syncManager = SyncManager(this, secureStorage)
        val appPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val biometricAuthManager = BiometricAuthManager(this)
        val lockPreferences = LockPreferences(this)

        setContent {
            val scope = rememberCoroutineScope()

            // If the user disabled the biometric lock entirely, skip the
            // authentication gate altogether and start "unlocked".
            var biometricEnabled by remember { mutableStateOf(lockPreferences.biometricEnabled) }
            var lockTimeout by remember { mutableStateOf(lockPreferences.lockTimeout) }
            var isAuthenticated by remember { mutableStateOf(!biometricEnabled) }

            fun runBiometricAuth() {
                biometricAuthManager.authenticate(
                    onSuccess = { isAuthenticated = true },
                    onError = { finish() }
                )
            }

            // Single source of truth for triggering the prompt: fires on first
            // composition, whenever isAuthenticated flips back to false (e.g.
            // re-lock after the configured background timeout), AND on every
            // Activity resume (resumeTrigger) — the last one is what recovers
            // from the stuck-blank-screen case described above, since it does
            // not depend on isAuthenticated's value actually changing.
            LaunchedEffect(isAuthenticated, resumeTrigger) {
                if (!isAuthenticated && biometricEnabled) runBiometricAuth()
            }

            // Re-lock policy, observed at the process level: when the whole
            // app leaves the foreground we record the time; when it returns,
            // if biometric lock is enabled and the configured timeout has
            // elapsed (or is set to "Immediately"), require re-authentication
            // before showing any account data again.
            DisposableEffect(biometricEnabled, lockTimeout) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            if (biometricEnabled) {
                                backgroundedAtMillis = System.currentTimeMillis()
                            }
                        }
                        Lifecycle.Event.ON_START -> {
                            val leftAt = backgroundedAtMillis
                            if (biometricEnabled && leftAt != null) {
                                val elapsed = System.currentTimeMillis() - leftAt
                                if (elapsed >= lockTimeout.millis) {
                                    isAuthenticated = false
                                }
                            }
                            backgroundedAtMillis = null
                        }
                        else -> Unit
                    }
                }
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                onDispose {
                    ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                }
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
                            currentLanguageTag = appPrefs.getString(KEY_LANGUAGE_TAG, "") ?: "",
                            onLanguageChange = { tag ->
                                // Persists the choice and recreates the Activity with
                                // the new locale applied (AppCompatDelegate handles the
                                // recreate on API < 33 automatically).
                                applyLanguage(this@MainActivity, tag)
                            },
                            syncManager = syncManager,
                            onSyncStateChanged = {
                                // Accounts may have been updated/merged by a sync pass;
                                // refresh the in-memory map shown on MainScreen.
                                refreshAccounts()
                            },
                            biometricEnabled = biometricEnabled,
                            onBiometricEnabledChange = { enabled ->
                                biometricEnabled = enabled
                                lockPreferences.biometricEnabled = enabled
                                // Turning the lock off unlocks immediately; turning it
                                // on takes effect from the next backgrounding, current
                                // session stays unlocked so the user isn't immediately
                                // re-prompted for the change they just made.
                            },
                            lockTimeout = lockTimeout,
                            onLockTimeoutChange = { timeout ->
                                lockTimeout = timeout
                                lockPreferences.lockTimeout = timeout
                            }
                        )
                    } else if (showImport.value) {
                        ImportScreen(
                            onBack = { showImport.value = false },
                            onImportGallery = { uri -> Toast.makeText(this, getString(R.string.importing_toast, uri), Toast.LENGTH_SHORT).show() },
                            onScanCamera = { Toast.makeText(this, getString(R.string.camera_scan_not_implemented), Toast.LENGTH_SHORT).show() }
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
