package com.teralone.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teralone.auth.totp.TotpGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(accounts: Map<String, String>) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("QuickAuth") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts.keys.toList()) { accountName ->
                AccountItem(name = accountName, secret = accounts[accountName] ?: "")
            }
        }
    }
}

@Composable
fun AccountItem(name: String, secret: String) {
    var code by remember { mutableStateOf("") }
    
    // Simple update mechanism
    LaunchedEffect(Unit) {
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
