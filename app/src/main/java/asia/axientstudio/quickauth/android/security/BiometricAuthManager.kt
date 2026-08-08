package asia.axientstudio.quickauth.android.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Biometric-only unlock. No PIN/pattern/password device-credential fallback is
 * offered: [BiometricPrompt.PromptInfo] is built with only
 * [BiometricManager.Authenticators.BIOMETRIC_STRONG] and no negative/fallback
 * button, so the system prompt cannot drop down to the device's screen lock.
 */
class BiometricAuthManager(private val activity: FragmentActivity) {

    /** True if the device has at least one strong biometric (fingerprint/face) enrolled. */
    fun canAuthenticateWithBiometrics(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!canAuthenticateWithBiometrics()) {
            onError("No biometric (fingerprint/face) enrolled on this device. Please enroll one in system settings to use QuickAuth.")
            return
        }

        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
                override fun onAuthenticationFailed() {
                    onError("Authentication failed")
                }
            })

        // BIOMETRIC_STRONG only (no DEVICE_CREDENTIAL) means the system cannot
        // fall back to the phone's PIN/pattern/password. The negative button is
        // required by the API when DEVICE_CREDENTIAL isn't included, but it only
        // dismisses the prompt (Cancel) — it does not open a device-credential
        // unlock screen.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock QuickAuth")
            .setSubtitle("Biometric authentication required")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(false)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
