package asia.axientstudio.quickauth.android.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import asia.axientstudio.quickauth.android.R
import asia.axientstudio.quickauth.android.totp.TotpGenerator
import kotlinx.coroutines.delay

// Seconds at which the timer bar transitions from accent → danger
private const val URGENT_THRESHOLD = 8L

@Composable
fun MainScreen(
    accounts: Map<String, String>,
    onOpenSettings: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onDeleteAccount: (String) -> Unit = {},
    onAddAccount: (String, String) -> Unit = { _, _ -> }
) {
    var fabExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    val fabRotation by animateFloatAsState(targetValue = if (fabExpanded) 45f else 0f, label = "fab")

    val cs = MaterialTheme.colorScheme

    if (showAddDialog) {
        AddAccountDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, secret -> onAddAccount(name, secret); showAddDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────
            // No Material TopAppBar: it carries implicit elevation and tinting
            // that add chrome without adding information. A simple row at the
            // same height keeps the full screen for content.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(
                        width = 1.dp,
                        color = cs.outline,
                        shape = RoundedCornerShape(0.dp)   // flat bottom border
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "QuickAuth",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onBackground,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Account list ──────────────────────────────────────────────
            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),   // leave room for FAB
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.no_accounts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tap + to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)  // bottom: clear FAB
                ) {
                    items(accounts.keys.toList(), key = { it }) { accountName ->
                        AccountCard(
                            name = accountName,
                            secret = accounts[accountName] ?: "",
                            onDelete = { onDeleteAccount(accountName) }
                        )
                    }
                }
            }
        }

        // ── FAB cluster ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sub-actions — visible only when FAB is expanded
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn() + scaleIn(initialScale = 0.7f) + expandVertically(expandFrom = Alignment.Bottom),
                exit  = fadeOut() + scaleOut(targetScale = 0.7f) + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Scan QR
                    SmallFabOption(
                        icon      = Icons.Filled.CameraAlt,
                        label     = stringResource(R.string.action_camera),
                        onClick   = { fabExpanded = false; onNavigateToImport() }
                    )
                    // Manual entry
                    SmallFabOption(
                        icon      = Icons.Filled.Keyboard,
                        label     = stringResource(R.string.action_keyboard),
                        onClick   = { fabExpanded = false; showAddDialog = true }
                    )
                }
            }

            // Primary toggle FAB
            FloatingActionButton(
                onClick            = { fabExpanded = !fabExpanded },
                containerColor     = cs.primary,
                contentColor       = cs.onPrimary,
                modifier           = Modifier.rotate(fabRotation)
            ) {
                Icon(
                    imageVector     = if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_add)
                )
            }
        }
    }
}

/** Pill-shaped label + icon action button used inside the expanded FAB. */
@Composable
private fun SmallFabOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(cs.surfaceVariant)
            .border(1.dp, cs.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurface
        )
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = cs.onSurface,
            modifier           = Modifier.size(18.dp)
        )
    }
}

// ── Account card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountCard(name: String, secret: String, onDelete: () -> Unit) {
    var code     by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(1f) }
    var timeLeft by remember { mutableLongStateOf(30L) }
    var showMenu by remember { mutableStateOf(false) }

    val context       = LocalContext.current
    val codeCopiedText = stringResource(R.string.code_copied)
    val editLabel     = stringResource(R.string.action_edit)
    val deleteLabel   = stringResource(R.string.action_delete)

    LaunchedEffect(secret) {
        while (true) {
            val epoch = System.currentTimeMillis() / 1000L
            val tl = 30L - (epoch % 30L)
            timeLeft = tl
            progress = tl / 30f
            code     = TotpGenerator.generateCode(secret)
            delay(100)
        }
    }

    val cs        = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(12.dp)
    // Timer bar color: accent until URGENT_THRESHOLD, then danger
    val timerColor = if (timeLeft <= URGENT_THRESHOLD) cs.error else cs.primary
    // Format code as "123 456" for readability
    val formattedCode = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(cs.surface)
                .border(1.dp, cs.outline, cardShape)
                .combinedClickable(
                    onClick     = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip      = android.content.ClipData.newPlainText(codeCopiedText, code)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, codeCopiedText, Toast.LENGTH_SHORT).show()
                    },
                    onLongClick = { showMenu = true }
                )
        ) {
            // ── Card content ──────────────────────────────────────────────
            Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {

                // Row 1: account name + overflow menu
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text     = name,
                        style    = MaterialTheme.typography.labelMedium,
                        color    = cs.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick  = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = null,
                                tint               = cs.onSurfaceVariant,
                                modifier           = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded          = showMenu,
                            onDismissRequest  = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text    = { Text(editLabel) },
                                onClick = { showMenu = false /* TODO */ }
                            )
                            DropdownMenuItem(
                                text    = { Text(deleteLabel, color = cs.error) },
                                onClick = { onDelete(); showMenu = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Row 2: TOTP code (hero) + countdown
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Bottom
                ) {
                    // The code is the hero — monospace, bold, large, distinct color
                    Text(
                        text       = formattedCode,
                        fontSize   = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color      = cs.secondary,   // indigo / design token: code
                        letterSpacing = 1.sp
                    )
                    // Countdown — small, muted, right-aligned
                    Text(
                        text  = "${timeLeft}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (timeLeft <= URGENT_THRESHOLD) cs.error
                                else cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // ── Signature element: full-width timer bar ───────────────────
            // No horizontal padding — bleeds to card edges, clipped by cardShape.
            // Drains from full-width → 0 over 30 seconds.
            // Color flips to error when ≤ URGENT_THRESHOLD seconds remain.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(cs.surfaceVariant)   // track (empty portion)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(timerColor)
                )
            }
        }
    }
}

// ── Add account dialog ────────────────────────────────────────────────────

@Composable
fun AddAccountDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name   by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest   = onDismiss,
        containerColor     = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(stringResource(R.string.add_account_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text(stringResource(R.string.account_name_label)) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = secret,
                    onValueChange = { secret = it.trim().uppercase() },
                    label         = { Text(stringResource(R.string.secret_label)) },
                    singleLine    = true,
                    textStyle     = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (name.isNotBlank() && secret.isNotBlank()) onAdd(name, secret) },
                enabled  = name.isNotBlank() && secret.isNotBlank()
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ── Backwards-compat alias ─────────────────────────────────────────────────
// SettingsScreen still calls AccountItem in its old name; keep it pointing
// to the new implementation without a refactor across all call sites.
@Composable
fun AccountItem(name: String, secret: String, onDelete: () -> Unit) =
    AccountCard(name = name, secret = secret, onDelete = onDelete)
