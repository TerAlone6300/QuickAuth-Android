package asia.axientstudio.quickauth.android.network

import android.content.Context
import android.os.Build
import asia.axientstudio.quickauth.android.data.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Messages the sync server returns when the current session was invalidated
 * (e.g. IP changed, session revoked, token expired). Ported from the
 * `sync_request` error handling in the original Python client.
 */
private val SESSION_INVALID_MESSAGES = setOf(
    "Invalid/Expired/IP Mismatch",
    "IP mismatch, session revoked",
    "Invalid session",
    "Session expired"
)

/** How far ahead of expiry (seconds) we proactively refresh the access token. */
private const val REFRESH_MARGIN_SECONDS = 300L

sealed class SyncResult {
    data class Success(val accountCount: Int) : SyncResult()
    data class SessionInvalidated(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
    object Disabled : SyncResult()
}

sealed class AuthResult {
    data class Success(val isNewAccount: Boolean) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class SyncManager(private val context: Context, private val secureStorage: SecureStorage) {
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val tokenPrefs = context.getSharedPreferences("token_prefs", Context.MODE_PRIVATE)

    private var cachedBaseUrl: String? = null
    private var cachedApi: QuickAuthApiService? = null

    val isSyncEnabled: Boolean
        get() = prefs.getBoolean("sync_enabled", false)

    val syncUrl: String?
        get() = prefs.getString("sync_url", null)

    val syncUser: String?
        get() = prefs.getString("sync_user", null)

    val lastSync: Long
        get() = prefs.getLong("last_sync", 0L)

    /** Builds (and caches) a Retrofit API client for the given base URL. */
    private fun apiFor(baseUrl: String): QuickAuthApiService {
        if (cachedApi != null && cachedBaseUrl == baseUrl) return cachedApi!!
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val api = Retrofit.Builder()
            .baseUrl(normalized)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QuickAuthApiService::class.java)
        cachedBaseUrl = baseUrl
        cachedApi = api
        return api
    }

    private fun currentApi(): QuickAuthApiService? {
        val url = syncUrl ?: return null
        return apiFor(url)
    }

    private fun getEnvInfo(): String = "Android ${Build.VERSION.RELEASE} / ${Build.MANUFACTURER} ${Build.MODEL}"

    /** Equivalent of Python's check_user_exists(). */
    suspend fun checkUserExists(baseUrl: String, user: String): Boolean = withContext(Dispatchers.IO) {
        try {
            apiFor(baseUrl).checkUser(CheckUserRequest(user)).exists
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Equivalent of the login/register branch inside Python's setup_sync().
     * On success, persists sync config + tokens and enables sync.
     */
    suspend fun authenticate(baseUrl: String, user: String, pass: String, isRegister: Boolean): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val action = if (isRegister) "register" else "login"
                val res = apiFor(baseUrl).auth(AuthRequest(user, pass, action, getEnvInfo()))
                if (res.success && res.at != null && res.rt != null) {
                    tokenPrefs.edit()
                        .putString("at", res.at)
                        .putString("rt", res.rt)
                        .putLong("exp", res.exp ?: 0L)
                        .apply()

                    prefs.edit()
                        .putBoolean("sync_enabled", true)
                        .putString("sync_url", baseUrl)
                        .putString("sync_user", user)
                        .apply()

                    AuthResult.Success(isRegister)
                } else {
                    AuthResult.Error(res.message ?: "Authentication failed")
                }
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Network error")
            }
        }

    /**
     * Equivalent of Python's refresh_session(): refreshes the access token if it's
     * locally expired (or close to it), then exchanges it for a short-lived session
     * token. Falls back to one last refresh attempt if the session call fails.
     */
    private suspend fun refreshSession(): String? {
        val api = currentApi() ?: return null
        var at = tokenPrefs.getString("at", null) ?: return null
        var rt = tokenPrefs.getString("rt", null)
        val exp = tokenPrefs.getLong("exp", 0L)

        // 1. Proactively refresh if AT is expired or close to it.
        val nowSeconds = System.currentTimeMillis() / 1000
        if (nowSeconds > exp - REFRESH_MARGIN_SECONDS) {
            try {
                val refreshRes = api.refresh(RefreshRequest(rt = rt, env = getEnvInfo()))
                if (refreshRes.success && refreshRes.at != null) {
                    at = refreshRes.at
                    rt = refreshRes.rt
                    tokenPrefs.edit()
                        .putString("at", refreshRes.at)
                        .putString("rt", refreshRes.rt)
                        .putLong("exp", refreshRes.exp ?: 0L)
                        .apply()
                } else {
                    return null
                }
            } catch (e: Exception) {
                return null
            }
        }

        // 2. Exchange AT for a session token.
        try {
            val sessionRes = api.getSession(SessionRequest(at = at))
            if (sessionRes.success && sessionRes.st != null) {
                return sessionRes.st
            }
        } catch (e: Exception) {
            // fall through to last-resort refresh below
        }

        // 3. Session failed (e.g. server DB wiped) — try one last refresh + session.
        try {
            val refreshRes = api.refresh(RefreshRequest(rt = rt, env = getEnvInfo()))
            if (refreshRes.success && refreshRes.at != null) {
                tokenPrefs.edit()
                    .putString("at", refreshRes.at)
                    .putString("rt", refreshRes.rt)
                    .putLong("exp", refreshRes.exp ?: 0L)
                    .apply()
                val retrySessionRes = api.getSession(SessionRequest(at = refreshRes.at))
                if (retrySessionRes.success && retrySessionRes.st != null) {
                    return retrySessionRes.st
                }
            }
        } catch (e: Exception) {
            // give up
        }

        return null
    }

    /** Equivalent of Python's perform_sync(). */
    suspend fun performSync(): SyncResult = withContext(Dispatchers.IO) {
        if (!isSyncEnabled) return@withContext SyncResult.Disabled
        val api = currentApi() ?: return@withContext SyncResult.Error("Sync URL not configured")

        val st = refreshSession()
            ?: return@withContext SyncResult.Error("Session expired or invalid. Please re-setup sync.")

        val accounts = secureStorage.getAllAccounts().mapValues { it.value?.toString() ?: "" }

        try {
            val res = api.sync(SyncRequest(st = st, data = accounts))
            if (res.success) {
                res.data?.forEach { (name, secret) -> secureStorage.saveAccount(name, secret) }
                prefs.edit().putLong("last_sync", System.currentTimeMillis() / 1000).apply()
                return@withContext SyncResult.Success(res.data?.size ?: accounts.size)
            }

            val message = res.message ?: "Unknown error"
            if (message in SESSION_INVALID_MESSAGES) {
                // Session was revoked server-side (e.g. IP mismatch). Wipe local
                // tokens and disable sync so we don't loop on an invalid session.
                tokenPrefs.edit().clear().apply()
                prefs.edit().putBoolean("sync_enabled", false).apply()
                return@withContext SyncResult.SessionInvalidated(message)
            }
            SyncResult.Error(message)
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Network error")
        }
    }

    /** Disables sync locally without contacting the server. */
    fun disableSync() {
        prefs.edit().putBoolean("sync_enabled", false).apply()
    }

    /** Fully forgets sync configuration and tokens (used after a revoked session, or manual reset). */
    fun resetSync() {
        prefs.edit().clear().apply()
        tokenPrefs.edit().clear().apply()
        cachedApi = null
        cachedBaseUrl = null
    }
}
