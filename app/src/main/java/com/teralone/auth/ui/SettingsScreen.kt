package com.teralone.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            listOf("System", "Light", "Dark").forEach { mode ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = themeMode == mode, onClick = { onThemeModeChange(mode) })
                    Text(mode)
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text("Security", style = MaterialTheme.typography.titleMedium)
            // Biometric/Lock switches...
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text("Sync", style = MaterialTheme.typography.titleMedium)
            // Sync settings...
        }
    }
}
