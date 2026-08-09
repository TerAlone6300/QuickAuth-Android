package asia.axientstudio.quickauth.android.ui

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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
    currentLanguageTag: String = "",
    onLanguageChange: (String) -> Unit = {},
    syncManager: SyncManager,
    onSyncStateChanged: () -> Unit = {},
    biometricEnabled: Boolean = true,
    onBiometricEnabledChange: (Boolean) -> Unit = {},
    lockTimeout: LockTimeout = LockTimeout.IMMEDIATE,
    onLockTimeoutChange: (LockTimeout) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var syncEnabled by remember { mutableStateOf(syncManager.isSyncEnabled) }
    var syncUser by remember { mutableStateOf(syncManager.syncUser) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var syncStatusIsError by remember { mutableStateOf(false) }

    // Resolved once per composition (Composable scope) so they can be safely
    // referenced from non-Composable lambdas (runSync, callbacks) below.
    val syncConfiguredSuccessText = stringResource(R.string.sync_configured_success)
    val syncSessionRevokedTemplate = stringResource(R.string.sync_session_revoked)
    val syncCompletedTemplate = stringResource(R.string.sync_completed)

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
                    syncStatus = syncCompletedTemplate.format(result.accountCount)
                    onSyncStateChanged()
                }
                is SyncResult.SessionInvalidated -> {
                    syncStatusIsError = true
                    syncStatus = syncSessionRevokedTemplate.format(result.message)
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
                syncStatus = syncConfiguredSuccessText
                runSync()
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Match MainScreen header style: flat row with 1dp bottom border,
            // no elevation, no shadow.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(0.dp)
                    )
                    .padding(start = 4.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            item {
                SectionLabel(stringResource(R.string.theme))
                Spacer(Modifier.height(8.dp))
                val themeOptions = listOf(
                    "System" to stringResource(R.string.theme_system),
                    "Light" to stringResource(R.string.theme_light),
                    "Dark" to stringResource(R.string.theme_dark)
                )
                themeOptions.forEach { (mode, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(mode) }
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = { onThemeModeChange(mode) })
                        Text(label)
                    }
                }
            }

            item { SectionDivider() }

            item {
                SectionLabel(stringResource(R.string.language))
                val languageOptions = listOf(
                    "" to stringResource(R.string.language_system_default),
                    "en" to stringResource(R.string.language_english),
                    "vi" to stringResource(R.string.language_vietnamese)
                )
                languageOptions.forEach { (tag, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageChange(tag) }
                    ) {
                        RadioButton(
                            selected = currentLanguageTag == tag,
                            onClick = { onLanguageChange(tag) }
                        )
                        Text(label)
                    }
                }
            }

            item { SectionDivider() }

            item {
                SectionLabel(stringResource(R.string.security))
                Text(
                    stringResource(R.string.security_flag_secure_desc),
                    style = MaterialTheme.typography.bodySmall
                )

                val manufacturer = Build.MANUFACTURER.lowercase()
                val isLikelyColorOs = manufacturer.contains("oppo") ||
                    manufacturer.contains("oneplus") ||
                    manufacturer.contains("realme")
                if (isLikelyColorOs) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.security_coloros_hint),
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
                        Text(stringResource(R.string.security_require_biometric))
                        Text(
                            stringResource(R.string.security_no_fallback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (biometricEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.security_lock_timing), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.security_lock_timing_desc),
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
                            Text(stringResource(option.labelRes))
                        }
                    }
                }
            }

            item { SectionDivider() }

            item {
                SectionLabel(stringResource(R.string.sync))

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
                        Text(stringResource(R.string.sync_enable_toggle))
                        if (syncEnabled && syncUser != null) {
                            Text(
                                stringResource(R.string.sync_logged_in_as, syncUser ?: ""),
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
                    Text(stringResource(R.string.sync_perform_now))
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
 * Section label — uppercase tracking eyebrow, matches Linear's "chrome recedes"
 * principle: small, muted, purely informational.
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * Section divider with consistent vertical breathing room.
 */
@Composable
private fun SectionDivider() {
    Divider(
        modifier  = Modifier.padding(vertical = 20.dp),
        color     = MaterialTheme.colorScheme.outline,
        thickness = 1.dp
    )
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

    // Resolved once per composition so the non-Composable validation/submit
    // functions below can safely reference them.
    val urlRequiredText = stringResource(R.string.sync_url_required)
    val urlSchemeErrorText = stringResource(R.string.sync_url_scheme_error)
    val checkUsernameFirstText = stringResource(R.string.sync_check_username_first)
    val passwordRequiredText = stringResource(R.string.sync_password_required)
    val passwordsMismatchText = stringResource(R.string.sync_passwords_mismatch)

    fun proceedFromUrl() {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            urlError = urlRequiredText
            return
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            urlError = urlSchemeErrorText
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
            errorMessage = checkUsernameFirstText
            return
        }
        if (pass.isEmpty()) {
            errorMessage = passwordRequiredText
            return
        }
        if (!exists && pass != passConfirm) {
            errorMessage = passwordsMismatchText
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
        title = { Text(if (step == SyncSetupStep.URL) stringResource(R.string.sync_enable_title) else stringResource(R.string.sync_account_title)) },
        text = {
            Column {
                if (step == SyncSetupStep.URL) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; urlError = null; insecureWarning = false },
                        label = { Text(stringResource(R.string.sync_server_url)) },
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
                            stringResource(R.string.sync_insecure_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it; userExists = null; errorMessage = null },
                        label = { Text(stringResource(R.string.sync_username)) },
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
                        Text(stringResource(R.string.sync_check_username))
                    }

                    userExists?.let { exists ->
                        Text(
                            if (exists) stringResource(R.string.sync_user_found)
                            else stringResource(R.string.sync_user_not_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        val visualTransform = if (passVisible) VisualTransformation.None else PasswordVisualTransformation()
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it; errorMessage = null },
                            label = { Text(if (exists) stringResource(R.string.sync_password) else stringResource(R.string.sync_new_password)) },
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
                                label = { Text(stringResource(R.string.sync_confirm_password)) },
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
                        Text(if (insecureWarning) stringResource(R.string.sync_continue_anyway) else stringResource(R.string.sync_next))
                    }
                }
                SyncSetupStep.CREDENTIALS -> {
                    Button(onClick = { submit() }, enabled = userExists != null && !submitting) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (userExists == false) stringResource(R.string.sync_register) else stringResource(R.string.sync_login))
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
                Text(if (step == SyncSetupStep.CREDENTIALS) stringResource(R.string.sync_back) else stringResource(R.string.sync_cancel))
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
        title = { Text(stringResource(R.string.sync_first_launch_title)) },
        text = {
            Text(stringResource(R.string.sync_first_launch_body))
        },
        confirmButton = {
            Button(onClick = onEnable) { Text(stringResource(R.string.sync_first_launch_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.sync_first_launch_no)) }
        }
    )
}
