package asia.axientstudio.quickauth.android.totp

import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow
import java.nio.ByteBuffer

object TotpGenerator {
    private const val TIME_STEP = 30
    private const val DIGITS = 6

    fun generateCode(secretBase32: String, timeMillis: Long = System.currentTimeMillis()): String {
        val secretBytes = decodeBase32(secretBase32)
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
        return String.format("%0${DIGITS}d", code)
    }

    private fun decodeBase32(base32: String): ByteArray {
        return Base64.getDecoder().decode(base32)
    }
}
