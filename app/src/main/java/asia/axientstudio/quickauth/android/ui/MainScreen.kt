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
                if (fabExpanded) {
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

@Composable
fun AccountItem(name: String, secret: String) {
    var code by remember { mutableStateOf("") }
    
    // Simple update mechanism
    LaunchedEffect(secret) {
        while(true) {
            code = TotpGenerator.generateCode(secret)
            kotlinx.coroutines.delay(1000)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(text = code, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
