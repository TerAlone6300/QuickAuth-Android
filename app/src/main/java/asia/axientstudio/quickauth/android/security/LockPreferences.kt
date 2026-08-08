package asia.axientstudio.quickauth.android.security

import android.content.Context

/**
 * Options for how long the app may sit in the background before it requires
 * biometric re-authentication again. "Immediately" re-locks the instant the
 * app leaves the foreground (matches leaving/backgrounding the app).
 */
enum class LockTimeout(val minutes: Long, val label: String) {
    IMMEDIATE(0L, "Immediately on exit"),
    ONE_MINUTE(1L, "1 minute after exit"),
    FIVE_MINUTES(5L, "5 minutes after exit"),
    FIFTEEN_MINUTES(15L, "15 minutes after exit");

    val millis: Long get() = minutes * 60_000L

    companion object {
        fun fromName(name: String?): LockTimeout =
            entries.firstOrNull { it.name == name } ?: IMMEDIATE
    }
}

/**
 * Persists the user's biometric-lock preferences: whether the lock is enabled
 * at all, and how long the app can be backgrounded before it re-locks.
 */
class LockPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var lockTimeout: LockTimeout
        get() = LockTimeout.fromName(prefs.getString(KEY_LOCK_TIMEOUT, LockTimeout.IMMEDIATE.name))
        set(value) = prefs.edit().putString(KEY_LOCK_TIMEOUT, value.name).apply()

    companion object {
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LOCK_TIMEOUT = "lock_timeout"
    }
}
