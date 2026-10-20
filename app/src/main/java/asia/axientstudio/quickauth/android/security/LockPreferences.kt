package asia.axientstudio.quickauth.android.security

import android.content.Context
import asia.axientstudio.quickauth.android.R

/**
 * Options for how long the app may sit in the background before it requires
 * biometric re-authentication again. "Immediately" re-locks the instant the
 * app leaves the foreground (matches leaving/backgrounding the app).
 */
enum class LockTimeout(val minutes: Long, val labelRes: Int) {
    IMMEDIATE(0L, R.string.lock_timeout_immediate),
    ONE_MINUTE(1L, R.string.lock_timeout_1min),
    FIVE_MINUTES(5L, R.string.lock_timeout_5min),
    FIFTEEN_MINUTES(15L, R.string.lock_timeout_15min);

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
class LockPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)

    /**
     * Until the user chooses, the lock is on only if this device can actually do biometrics. A device without
     * (or with nothing enrolled) used to get a lock it could never open, which made the app close on launch. The
     * default is decided once and stored, so enrolling a fingerprint later doesn't switch the lock on by itself.
     */
    var biometricEnabled: Boolean
        get() {
            if (!prefs.contains(KEY_BIOMETRIC_ENABLED)) {
                val default = BiometricAuthManager.isAvailable(context)
                prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, default).apply()
                return default
            }
            return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        }
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var lockTimeout: LockTimeout
        get() = LockTimeout.fromName(prefs.getString(KEY_LOCK_TIMEOUT, LockTimeout.IMMEDIATE.name))
        set(value) = prefs.edit().putString(KEY_LOCK_TIMEOUT, value.name).apply()

    companion object {
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LOCK_TIMEOUT = "lock_timeout"
    }
}
