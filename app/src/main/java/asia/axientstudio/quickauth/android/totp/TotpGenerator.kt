package asia.axientstudio.quickauth.android.totp

import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow
import java.nio.ByteBuffer

object TotpGenerator {
    private const val TIME_STEP = 30
    private const val DIGITS = 6

    /**
     * Generates a TOTP code for the given Base32 secret.
     * Returns a placeholder of dashes instead of crashing if the secret is
     * malformed (e.g. empty, invalid Base32 characters, or too short for HMAC).
     */
    fun generateCode(secretBase32: String, timeMillis: Long = System.currentTimeMillis()): String {
        return try {
            val secretBytes = decodeBase32(secretBase32)
            if (secretBytes.isEmpty()) return "-".repeat(DIGITS)

            val counter = timeMillis / 1000 / TIME_STEP
            val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()

            val hmac = Mac.getInstance("HmacSHA1")
            hmac.init(SecretKeySpec(secretBytes, "HmacSHA1"))
            val hash = hmac.doFinal(counterBytes)

            val offset = hash[hash.size - 1].toInt() and 0x0F
            val truncatedHash = (hash[offset].toInt() and 0x7F shl 24) or
                                (hash[offset + 1].toInt() and 0xFF shl 16) or
                                (hash[offset + 2].toInt() and 0xFF shl 8) or
                                (hash[offset + 3].toInt() and 0xFF)

            val code = truncatedHash % 10.0.pow(DIGITS).toInt()
            String.format(Locale.US, "%0${DIGITS}d", code)
        } catch (e: Exception) {
            "-".repeat(DIGITS)
        }
    }

    /**
     * Returns the number of seconds remaining until the current TOTP code expires.
     * Useful for driving a countdown/progress indicator in the UI.
     */
    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis()): Int {
        val secondsIntoStep = (timeMillis / 1000) % TIME_STEP
        return (TIME_STEP - secondsIntoStep).toInt()
    }

    /**
     * Proper RFC 4648 Base32 decoder (the previous implementation incorrectly
     * used Base64, which throws on any real Base32 secret and crashed the app
     * on first launch).
     */
    private fun decodeBase32(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = base32.trim().uppercase(Locale.US).replace("=", "").replace(" ", "")
        if (clean.isEmpty()) return ByteArray(0)

        val output = ByteArray(clean.length * 5 / 8)
        var buffer = 0L
        var bitsLeft = 0
        var index = 0

        for (c in clean) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue // skip characters that aren't valid Base32
            buffer = (buffer shl 5) or value.toLong()
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output[index++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        return output.copyOf(index)
    }
}
