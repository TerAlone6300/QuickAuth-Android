package asia.axientstudio.quickauth.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import asia.axientstudio.quickauth.android.R
import asia.axientstudio.quickauth.android.totp.TotpGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    accounts: Map<String, String>,
    onOpenSettings: () -> Unit = {},
    onNavigateToImport: () -> Unit = {}
) {
    var fabExpanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (fabExpanded) 45f else 0f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QuickAuth") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn() + scaleIn(initialScale = 0.5f),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut() + scaleOut(targetScale = 0.5f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ExtendedFloatingActionButton(
                            text = { Text("Keyboard") },
                            icon = { Icon(Icons.Filled.Keyboard, contentDescription = null) },
                            onClick = { fabExpanded = false /* TODO: Navigate to Add Account */ }
                        )
                        ExtendedFloatingActionButton(
                            text = { Text("Camera") },
                            icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                            onClick = { 
                                fabExpanded = false
                                onNavigateToImport()
                            }
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    modifier = Modifier.rotate(rotation)
                ) {
                    Icon(
                        if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = "Add"
                    )
                }
            }
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_accounts), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts.keys.toList(), key = { it }) { accountName ->
                AccountItem(name = accountName, secret = accounts[accountName] ?: "")
            }
        }
    }
}

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import asia.axientstudio.quickauth.android.R
import asia.axientstudio.quickauth.android.totp.TotpGenerator
import kotlinx.coroutines.delay

// ... (Rest of MainScreen code remains the same up to AccountItem)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountItem(name: String, secret: String) {
    var code by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(1f) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(secret) {
        while(true) {
            val epoch = System.currentTimeMillis() / 1000
            val timeLeft = 30 - (epoch % 30)
            progress = timeLeft / 30f
            code = TotpGenerator.generateCode(secret)
            delay(100)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* TODO: Copy code */ },
                onLongClick = { showMenu = true }
            )
    ) {
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { /* TODO */ showMenu = false })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { /* TODO */ showMenu = false })
        }

        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Fan-style progress
                Canvas(modifier = Modifier.size(24.dp).padding(4.dp)) {
                    drawArc(
                        color = Color.LightGray,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        size = Size(size.width, size.height)
                    )
                    drawArc(
                        color = Color.Blue,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = true,
                        size = Size(size.width, size.height)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = code, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
