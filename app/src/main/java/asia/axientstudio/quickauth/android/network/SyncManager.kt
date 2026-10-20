package asia.axientstudio.quickauth.android.network

import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import asia.axientstudio.quickauth.android.crypto.E2ee
import asia.axientstudio.quickauth.android.data.SecureStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
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
    /** [undecryptable] = entries on the server that failed authentication (other passphrase, or tampering). */
    data class Success(val accountCount: Int, val undecryptable: Int = 0) : SyncResult()
    data class SessionInvalidated(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
    /** Nothing was uploaded: the end-to-end encryption passphrase has not been entered on this device yet. */
    object NeedsPassphrase : SyncResult()
    object Disabled : SyncResult()
}

sealed class AuthResult {
    data class Success(val isNewAccount: Boolean) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/** Whether this account already has an encryption passphrase on the server. */
sealed class E2eeState {
    object Create : E2eeState()   // first device: choose a passphrase
    object Unlock : E2eeState()   // another device already chose one: enter it
    data class Error(val message: String) : E2eeState()
}

sealed class E2eeResult {
    object Success : E2eeResult()
    object WrongPassphrase : E2eeResult()
    object SameAsLoginPassword : E2eeResult()
    data class Error(val message: String) : E2eeResult()
}

class SyncManager(private val context: Context, private val secureStorage: SecureStorage) {
    // Non-sensitive settings.
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    // Access/refresh tokens and the end-to-end encryption key live in encrypted storage (Keystore-backed
    // master key, same scheme as the account store). Created lazily: it is slow, so keep it off the main thread.
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sync_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Serializes nulls so that {"entry": null} (delete) is actually sent. Also used to read server error bodies.
    private val gson: Gson = GsonBuilder().serializeNulls().create()

    private var cachedBaseUrl: String? = null
    private var cachedApi: QuickAuthApiService? = null

    // Login password of the sync setup that just completed. Only kept in memory, only so that the user can't
    // pick it as the encryption passphrase (the server sees the login password, so it must not unlock the data).
    @Volatile private var lastLoginPassword: String? = null

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
            .addConverterFactory(GsonConverterFactory.create(gson))
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

    /**
     * Retrofit throws HttpException for every non-2xx answer, hiding the server's message
     * ("Password too short", "Too many attempts, ...") behind "HTTP 400". Read it from the error body.
     */
    private fun describe(e: Exception): String {
        if (e is HttpException) {
            val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val message = body?.let { try { gson.fromJson(it, ApiError::class.java)?.message } catch (_: Exception) { null } }
            return message ?: "HTTP ${e.code()}"
        }
        return e.message ?: "Network error"
    }

    /** One-time move of tokens from the old plain-text preferences file into encrypted storage. */
    private fun migrateLegacyTokens() {
        val old = context.getSharedPreferences("token_prefs", Context.MODE_PRIVATE)
        if (!old.contains("at") && !old.contains("rt")) return
        securePrefs.edit()
            .putString("at", old.getString("at", null))
            .putString("rt", old.getString("rt", null))
            .putLong("exp", old.getLong("exp", 0L))
            .apply()
        old.edit().clear().apply()
        context.deleteSharedPreferences("token_prefs")
    }

    private fun saveTokens(at: String?, rt: String?, exp: Long?) {
        securePrefs.edit()
            .putString("at", at)
            .putString("rt", rt)
            .putLong("exp", exp ?: 0L)
            .apply()
    }

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xFF))
        return sb.toString()
    }

    private fun unhex(s: String): ByteArray? =
        if (s.length % 2 != 0) null else try {
            ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } catch (_: NumberFormatException) { null }

    private fun loadKey(): ByteArray? = securePrefs.getString("e2ee_key", null)?.let { unhex(it) }

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
                    migrateLegacyTokens()
                    saveTokens(res.at, res.rt, res.exp)

                    // A cached encryption key belongs to one account on one server.
                    if (syncUrl != baseUrl || syncUser != user) {
                        securePrefs.edit().remove("e2ee_key").remove("e2ee_migrate").apply()
                    }
                    prefs.edit()
                        .putBoolean("sync_enabled", true)
                        .putString("sync_url", baseUrl)
                        .putString("sync_user", user)
                        .apply()
                    lastLoginPassword = pass

                    AuthResult.Success(isRegister)
                } else {
                    AuthResult.Error(res.message ?: "Authentication failed")
                }
            } catch (e: Exception) {
                AuthResult.Error(describe(e))
            }
        }

    /**
     * Equivalent of Python's refresh_session(): refreshes the access token if it's
     * locally expired (or close to it), then exchanges it for a short-lived session
     * token. Falls back to one last refresh attempt if the session call fails.
     */
    private suspend fun refreshSession(): String? {
        val api = currentApi() ?: return null
        migrateLegacyTokens()
        var at = securePrefs.getString("at", null) ?: return null
        var rt = securePrefs.getString("rt", null)
        val exp = securePrefs.getLong("exp", 0L)

        // 1. Proactively refresh if AT is expired or close to it.
        val nowSeconds = System.currentTimeMillis() / 1000
        if (nowSeconds > exp - REFRESH_MARGIN_SECONDS) {
            try {
                val refreshRes = api.refresh(RefreshRequest(rt = rt, env = getEnvInfo()))
                if (refreshRes.success && refreshRes.at != null) {
                    at = refreshRes.at
                    rt = refreshRes.rt
                    saveTokens(refreshRes.at, refreshRes.rt, refreshRes.exp)
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
                saveTokens(refreshRes.at, refreshRes.rt, refreshRes.exp)
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

    /**
     * Asks the server whether this account already has an encryption passphrase: [E2eeState.Create] on the first
     * device, [E2eeState.Unlock] on every later one.
     */
    suspend fun e2eeState(): E2eeState = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext E2eeState.Error("Sync URL not configured")
        val st = refreshSession()
            ?: return@withContext E2eeState.Error("Session expired or invalid. Please re-setup sync.")
        try {
            val res = api.sync(SyncRequest(st = st, data = null))
            if (!res.success) return@withContext E2eeState.Error(res.message ?: "Unknown error")
            if (res.data?.get(E2ee.CHECK_KEY) == null) E2eeState.Create else E2eeState.Unlock
        } catch (e: Exception) {
            E2eeState.Error(describe(e))
        }
    }

    /**
     * Derives the key from [passphrase], checks it against the account's check value (or publishes one if this is
     * the first device), and stores the key in encrypted storage. The passphrase itself is never stored or sent.
     */
    suspend fun setupE2ee(passphrase: String): E2eeResult {
        val user = syncUser ?: return E2eeResult.Error("Sync not configured")
        if (lastLoginPassword != null && passphrase == lastLoginPassword) return E2eeResult.SameAsLoginPassword

        val st: String
        val check: String?
        val api: QuickAuthApiService
        try {
            api = withContext(Dispatchers.IO) { currentApi() } ?: return E2eeResult.Error("Sync URL not configured")
            st = withContext(Dispatchers.IO) { refreshSession() }
                ?: return E2eeResult.Error("Session expired or invalid. Please re-setup sync.")
            val res = withContext(Dispatchers.IO) { api.sync(SyncRequest(st = st, data = null)) }
            if (!res.success) return E2eeResult.Error(res.message ?: "Unknown error")
            check = res.data?.get(E2ee.CHECK_KEY)
        } catch (e: Exception) {
            return E2eeResult.Error(describe(e))
        }

        val key = withContext(Dispatchers.Default) { E2ee.derive(passphrase, user) }  // ~1-3 s of CPU
        if (check != null && !E2ee.verifyCheck(key, check)) return E2eeResult.WrongPassphrase

        return withContext(Dispatchers.IO) {
            try {
                if (check == null) {  // first device: publish a check value so other devices can verify their passphrase
                    val r = api.sync(SyncRequest(st = st, data = mapOf(E2ee.CHECK_KEY to E2ee.makeCheck(key))))
                    if (!r.success) return@withContext E2eeResult.Error(r.message ?: "Unknown error")
                }
                // "e2ee_migrate": the next sync may import accounts that exist only as unencrypted entries on the
                // server (uploaded by an older client). Persisted so that an app restart can't skip it.
                securePrefs.edit().putString("e2ee_key", hex(key)).putBoolean("e2ee_migrate", true).apply()
                lastLoginPassword = null
                E2eeResult.Success
            } catch (e: Exception) {
                E2eeResult.Error(describe(e))
            }
        }
    }

    /** Equivalent of Python's perform_sync(). */
    suspend fun performSync(): SyncResult = withContext(Dispatchers.IO) {
        if (!isSyncEnabled) return@withContext SyncResult.Disabled
        val api = currentApi() ?: return@withContext SyncResult.Error("Sync URL not configured")

        val st = refreshSession()
            ?: return@withContext SyncResult.Error("Session expired or invalid. Please re-setup sync.")

        // Fail closed: without the encryption key nothing is uploaded (never fall back to plain text).
        val key = loadKey() ?: return@withContext SyncResult.NeedsPassphrase
        val fresh = securePrefs.getBoolean("e2ee_migrate", false)

        val accounts = secureStorage.getAllAccounts().mapValues { it.value?.toString() ?: "" }

        try {
            val res = api.sync(SyncRequest(st = st, data = E2ee.pack(key, accounts)))
            if (res.success) {
                val un = E2ee.unpack(key, res.data ?: emptyMap())

                // Entries that are not encrypted come from an un-upgraded device (or tampering). On the first sync
                // with a new key keep the accounts that only existed that way; always delete the plain-text copies.
                val imported = LinkedHashMap<String, String>()
                if (un.legacy.isNotEmpty()) {
                    if (fresh) {
                        for ((name, secret) in un.legacy) {
                            if (secret != null && E2ee.isValidName(name) && secureStorage.getAccount(name) == null) {
                                imported[name] = secret
                            }
                        }
                    }
                    // Upload the imported accounts encrypted AND delete the plain-text copies in ONE request, so the
                    // server is never left without a copy of them.
                    val purge = LinkedHashMap<String, String?>(E2ee.pack(key, imported))
                    for (name in un.legacy.keys) purge[name] = null
                    try {
                        api.sync(SyncRequest(st = st, data = purge))
                    } catch (e: Exception) {
                        // Retried on the next sync; the accounts are already stored locally.
                    }
                }

                val remote = un.accounts + imported
                remote.forEach { (name, secret) -> secureStorage.saveAccount(name, secret) }
                securePrefs.edit().putBoolean("e2ee_migrate", false).apply()
                prefs.edit().putLong("last_sync", System.currentTimeMillis() / 1000).apply()
                return@withContext SyncResult.Success(remote.size, un.undecryptable)
            }

            val message = res.message ?: "Unknown error"
            if (message in SESSION_INVALID_MESSAGES) {
                // Session was revoked server-side (e.g. IP mismatch). Wipe local
                // tokens and disable sync so we don't loop on an invalid session.
                securePrefs.edit().remove("at").remove("rt").remove("exp").apply()
                prefs.edit().putBoolean("sync_enabled", false).apply()
                return@withContext SyncResult.SessionInvalidated(message)
            }
            SyncResult.Error(message)
        } catch (e: Exception) {
            SyncResult.Error(describe(e))
        }
    }

    /** Disables sync locally without contacting the server. */
    fun disableSync() {
        prefs.edit().putBoolean("sync_enabled", false).apply()
    }

    /** Fully forgets sync configuration, tokens and the encryption key (used after a revoked session, or manual reset). */
    fun resetSync() {
        prefs.edit().clear().apply()
        securePrefs.edit().clear().apply()
        lastLoginPassword = null
        cachedApi = null
        cachedBaseUrl = null
    }
}
