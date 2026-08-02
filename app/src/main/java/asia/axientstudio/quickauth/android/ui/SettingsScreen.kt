package asia.axientstudio.quickauth.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import asia.axientstudio.quickauth.android.R
import java.util.Locale

import androidx.compose.foundation.clickable
// ... (imports)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onPerformSync: () -> Unit,
    onToggleSync: (Boolean) -> Unit,
    syncEnabled: Boolean
) {
    var language by remember { mutableStateOf(Locale.getDefault().language) }

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
                Text("Biometric Authentication: Coming soon.")
            }

            item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

            item {
                Text(stringResource(R.string.sync), style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onToggleSync(!syncEnabled) }
                ) {
                    Switch(checked = syncEnabled, onCheckedChange = onToggleSync)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable Sync")
                }
                Button(onClick = onPerformSync, modifier = Modifier.fillMaxWidth()) {
                    Text("Perform Sync Now")
                }
            }
        }
    }
}

