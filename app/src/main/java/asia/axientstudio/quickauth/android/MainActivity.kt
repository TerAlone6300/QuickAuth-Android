package asia.axientstudio.quickauth.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import asia.axientstudio.quickauth.android.data.SecureStorage
import asia.axientstudio.quickauth.android.ui.MainScreen
import asia.axientstudio.quickauth.android.ui.theme.QuickAuthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val secureStorage = SecureStorage(this)
        
        // Populate with a demo account if completely empty to show something on first launch
        val accounts = secureStorage.getAllAccounts()
        if (accounts.isEmpty()) {
            // "JBSWY3DPEHPK3PXP" is "Hello!" in base32 (standard test secret)
            secureStorage.saveAccount("Demo Account", "JBSWY3DPEHPK3PXP")
        }
        
        setContent {
            QuickAuthTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Convert map of <String, *> to Map<String, String>
                    val accountsMap = secureStorage.getAllAccounts().mapValues { 
                        it.value?.toString() ?: "" 
                    }
                    MainScreen(accounts = accountsMap)
                }
            }
        }
    }
}
