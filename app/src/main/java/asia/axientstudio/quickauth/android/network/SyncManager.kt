package asia.axientstudio.quickauth.android.network

import android.content.Context
import asia.axientstudio.quickauth.android.data.SecureStorage
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncManager(context: Context, private val secureStorage: SecureStorage) {
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val tokenPrefs = context.getSharedPreferences("token_prefs", Context.MODE_PRIVATE)
    private val api: QuickAuthApiService

    init {
        val baseUrl = prefs.getString("sync_url", "") ?: ""
        api = Retrofit.Builder()
            .baseUrl(baseUrl.ifEmpty { "https://localhost/" })
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QuickAuthApiService::class.java)
    }

    suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean("sync_enabled", false)) return@withContext false

        val st = refreshSession() ?: return@withContext false
        
        val accounts = secureStorage.getAllAccounts().mapValues { it.value?.toString() ?: "" }
        
        try {
            val res = api.sync(SyncRequest(st = st, data = accounts))
            if (res.success && res.data != null) {
                // Merge data
                res.data.forEach { (name, secret) -> secureStorage.saveAccount(name, secret) }
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    private suspend fun refreshSession(): String? {
        val at = tokenPrefs.getString("at", null) ?: return null
        
        // 1. Get Session Token
        try {
            val sessionRes = api.getSession(SessionRequest(at = at))
            if (sessionRes.success) return sessionRes.st
            
            // 2. If failed, try refresh
            val rt = tokenPrefs.getString("rt", null) ?: return null
            val refreshRes = api.refresh(RefreshRequest(rt = rt, env = "android"))
            
            if (refreshRes.success) {
                tokenPrefs.edit()
                    .putString("at", refreshRes.at)
                    .putString("rt", refreshRes.rt)
                    .putLong("exp", refreshRes.exp ?: 0L)
                    .apply()
                
                val retrySessionRes = api.getSession(SessionRequest(at = refreshRes.at))
                return retrySessionRes.st
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
