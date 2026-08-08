package asia.axientstudio.quickauth.android.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import asia.axientstudio.quickauth.android.R
import asia.axientstudio.quickauth.android.network.AuthResult
import asia.axientstudio.quickauth.android.network.SyncManager
import asia.axientstudio.quickauth.android.network.SyncResult
import asia.axientstudio.quickauth.android.security.LockTimeout
import kotlinx.coroutines.launch
import java.util.Locale

import androidx.compose.foundation.clickable
// ... (imports)

private enum class SyncSetupStep { URL, CREDENTIALS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    syncManager: SyncManager,
    onSyncStateChanged: () -> Unit = {},
    biometricEnabled: Boolean = true,
    onBiometricEnabledChange: (Boolean) -> Unit = {},
    lockTimeout: LockTimeout = LockTimeout.IMMEDIATE,
    onLockTimeoutChange: (LockTimeout) -> Unit = {}
) {
    var language by remember { mutableStateOf(Locale.getDefault().language) }
    val scope = rememberCoroutineScope()

    var syncEnabled by remember { mutableStateOf(syncManager.isSyncEnabled) }
    var syncUser by remember { mutableStateOf(syncManager.syncUser) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var syncStatusIsError by remember { mutableStateOf(false) }

    fun refreshSyncState() {
        syncEnabled = syncManager.isSyncEnabled
        syncUser = syncManager.syncUser
        onSyncStateChanged()
    }

    fun runSync() {
        scope.launch {
            syncBusy = true
            syncStatus = null
            when (val result = syncManager.performSync()) {
                is SyncResult.Success -> {
                    syncStatusIsError = false
                    syncStatus = "Sync completed (${result.accountCount} accounts total)"
                    onSyncStateChanged()
                }
                is SyncResult.SessionInvalidated -> {
                    syncStatusIsError = true
                    syncStatus = "Session revoked (${result.message}). Please set up sync again."
                    refreshSyncState()
                }
                is SyncResult.Error -> {
                    syncStatusIsError = true
                    syncStatus = result.message
                }
                SyncResult.Disabled -> { /* no-op */ }
            }
            syncBusy = false
        }
    }

    if (showSetupDialog) {
        SyncSetupDialog(
            syncManager = syncManager,
            onDismiss = { showSetupDialog = false },
            onSuccess = {
                showSetupDialog = false
                refreshSyncState()
                syncStatusIsError = false
                syncStatus = "Sync configured successfully!"
                runSync()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                listOf("System", "Light", "Dark").forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(mode) }
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = { onThemeModeChange(mode) })
                        Text(mode)
                    }
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

            item {
                Text("Language", style = MaterialTheme.typography.titleMedium)
                listOf("en", "vi").forEach { lang ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* TODO: Locale change logic */ }
                    ) {
                        RadioButton(selected = language == lang, onClick = { language = lang })
                        Text(if (lang == "en") "English" else "Tiếng Việt")
                    }
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

            item {
                Text(stringResource(R.string.security), style = MaterialTheme.typography.titleMedium)
                Text(
                    "• Screenshots and screen recording are blocked system-wide while the app is open (FLAG_SECURE).\n" +
                    "• The Recents (app switcher) preview shows a blank screen instead of your accounts.",
                    style = MaterialTheme.typography.bodySmall
                )

                val manufacturer = Build.MANUFACTURER.lowercase()
                val isLikelyColorOs = manufacturer.contains("oppo") ||
                    manufacturer.contains("oneplus") ||
                    manufacturer.contains("realme")
                if (isLikelyColorOs) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your device likely runs ColorOS. For extra protection in the Recents screen, " +
                        "you can also enable ColorOS's own \"Hide content\" (App Lock → Hide content) " +
                        "for QuickAuth in system Settings — this is a system feature and must be turned " +
                        "on manually, the app cannot enable it for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        onBiometricEnabledChange(!biometricEnabled)
                    }
                ) {
                    Switch(checked = biometricEnabled, onCheckedChange = onBiometricEnabledChange)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Require fingerprint/face to open")
                        Text(
                            "No device PIN/pattern/password fallback — biometric only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (biometricEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text("Lock timing", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Choose how long the app can stay in the background before it locks again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LockTimeout.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLockTimeoutChange(option) }
                        ) {
                            RadioButton(
                                selected = lockTimeout == option,
                                onClick = { onLockTimeoutChange(option) }
                            )
                            Text(option.label)
                        }
                    }
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

            item {
                Text(stringResource(R.string.sync), style = MaterialTheme.typography.titleMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (syncEnabled) {
                            syncManager.disableSync()
                            refreshSyncState()
                        } else {
                            showSetupDialog = true
                        }
                    }
                ) {
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showSetupDialog = true
                            } else {
                                syncManager.disableSync()
                                refreshSyncState()
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Enable Sync")
                        if (syncEnabled && syncUser != null) {
                            Text(
                                "Logged in as $syncUser",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { runSync() },
                    enabled = syncEnabled && !syncBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (syncBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Perform Sync Now")
                }

                syncStatus?.let { status ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status,
                        color = if (syncStatusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Sync setup flow, ported from Python's setup_sync():
 * 1. Ask for server URL (warn on plain http).
 * 2. Ask for username, check if it already exists on the server.
 * 3. If it exists -> ask password, login. If not -> ask for a new password (with
 *    confirmation), register.
 */
@Composable
fun SyncSetupDialog(
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(SyncSetupStep.URL) }

    var url by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var insecureWarning by remember { mutableStateOf(false) }

    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passConfirm by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    var checkingUser by remember { mutableStateOf(false) }
    var userExists by remember { mutableStateOf<Boolean?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun proceedFromUrl() {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            urlError = "URL is required"
            return
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            urlError = "URL must start with http:// or https://"
            return
        }
        if (trimmed.startsWith("http://") && !insecureWarning) {
            // Surface the warning once; user must tap "Continue anyway".
            insecureWarning = true
            return
        }
        urlError = null
        step = SyncSetupStep.CREDENTIALS
    }

    fun checkUser() {
        val trimmedUser = user.trim()
        if (trimmedUser.isEmpty()) return
        scope.launch {
            checkingUser = true
            errorMessage = null
            userExists = syncManager.checkUserExists(url.trim(), trimmedUser)
            checkingUser = false
        }
    }

    fun submit() {
        val exists = userExists
        if (exists == null) {
            errorMessage = "Please check the username first"
            return
        }
        if (pass.isEmpty()) {
            errorMessage = "Password is required"
            return
        }
        if (!exists && pass != passConfirm) {
            errorMessage = "Passwords do not match!"
            return
        }
        scope.launch {
            submitting = true
            errorMessage = null
            when (val res = syncManager.authenticate(url.trim(), user.trim(), pass, isRegister = !exists)) {
                is AuthResult.Success -> onSuccess()
                is AuthResult.Error -> errorMessage = res.message
            }
            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (step == SyncSetupStep.URL) "Enable Code Sync" else "Sync Account") },
        text = {
            Column {
                if (step == SyncSetupStep.URL) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; urlError = null; insecureWarning = false },
                        label = { Text("Server URL (http/https)") },
                        singleLine = true,
                        isError = urlError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    urlError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (insecureWarning) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⚠️ WARNING: YOU ARE USING INSECURE HTTP!\nPasswords and TOTP secrets can be intercepted.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it; userExists = null; errorMessage = null },
                        label = { Text("Username") },
                        singleLine = true,
                        trailingIcon = {
                            if (checkingUser) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { checkUser() }, enabled = user.isNotBlank() && !checkingUser) {
                        Text("Check username")
                    }

                    userExists?.let { exists ->
                        Text(
                            if (exists) "Account found — enter your password to login."
                            else "Username not found — create a new password to register.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        val visualTransform = if (passVisible) VisualTransformation.None else PasswordVisualTransformation()
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it; errorMessage = null },
                            label = { Text(if (exists) "Password" else "Create a new password") },
                            singleLine = true,
                            visualTransformation = visualTransform,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passVisible = !passVisible }) {
                                    Icon(
                                        if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!exists) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = passConfirm,
                                onValueChange = { passConfirm = it; errorMessage = null },
                                label = { Text("Confirm password") },
                                singleLine = true,
                                visualTransformation = visualTransform,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                SyncSetupStep.URL -> {
                    Button(onClick = { proceedFromUrl() }) {
                        Text(if (insecureWarning) "Continue anyway" else "Next")
                    }
                }
                SyncSetupStep.CREDENTIALS -> {
                    Button(onClick = { submit() }, enabled = userExists != null && !submitting) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (userExists == false) "Register" else "Login")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (step == SyncSetupStep.CREDENTIALS) {
                    step = SyncSetupStep.URL
                } else {
                    onDismiss()
                }
            }, enabled = !submitting) {
                Text(if (step == SyncSetupStep.CREDENTIALS) "Back" else "Cancel")
            }
        }
    )
}

/**
 * First-launch prompt, ported from Python's setup_sync() initial menu:
 * `TUI.menu(["Yes (Enable Sync)", "No (Local Only)"], "Enable Code Sync?")`.
 * Shown once, before any account data screen, when sync has never been configured.
 */
@Composable
fun SyncFirstLaunchDialog(
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Enable Code Sync?") },
        text = {
            Text("You can sync your accounts across devices using your own self-hosted QuickAuth server. This is optional — you can always set it up later from Settings.")
        },
        confirmButton = {
            Button(onClick = onEnable) { Text("Yes (Enable Sync)") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("No (Local Only)") }
        }
    )
}
