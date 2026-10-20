package asia.axientstudio.quickauth.android.crypto

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of synced data. Byte-compatible with the Python client
 * (teralone_auth.py >= 1.9.1), so both clients can read each other's data.
 *
 * Secrets are encrypted on the device with a key derived from a *sync passphrase* that is never sent
 * to the server (the login password IS sent, so it must not be reused). The server only ever stores
 * opaque "e1:" blobs under blinded ids.
 *
 *  - key:    PBKDF2-HMAC-SHA256(passphrase, "QuickAuth-E2EE-v1|" + user, 600000 iterations, 32 bytes)
 *  - cipher: HMAC-SHA256 counter-mode keystream, then an HMAC-SHA256 tag (encrypt-then-MAC)
 *  - blob:   "e1:" + base64url(nonce[16] || ciphertext || tag[32])
 *
 * Only javax.crypto / java.security are used. PBKDF2 and base64 are implemented here on purpose:
 * minSdk is 24, where "PBKDF2WithHmacSHA256" and java.util.Base64 do not exist, and Android's built-in
 * PBKDF2 providers disagree on how non-ASCII passphrases are encoded.
 */
object E2ee {
    const val ITERATIONS = 600_000
    const val CHECK_KEY = "__e2ee__"
    val CHECK_PLAINTEXT: ByteArray = "quickauth-e2ee-check".toByteArray(Charsets.UTF_8)

    private const val PREFIX = "e1:"
    private const val NONCE_LEN = 16
    private const val TAG_LEN = 32
    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val random = SecureRandom()

    // ---- key derivation -------------------------------------------------------------------------

    fun derive(passphrase: String, user: String, iterations: Int = ITERATIONS): ByteArray {
        require(passphrase.isNotEmpty()) { "empty passphrase" }
        val salt = "QuickAuth-E2EE-v1|$user".toByteArray(Charsets.UTF_8)
        return pbkdf2HmacSha256(passphrase.toByteArray(Charsets.UTF_8), salt, iterations)
    }

    /** PBKDF2 (RFC 8018) with HMAC-SHA256 for a single 32-byte output block. */
    internal fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val t = u.copyOf()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        return t
    }

    // ---- seal / open ----------------------------------------------------------------------------

    private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        for (p in parts) mac.update(p)
        return mac.doFinal()
    }

    private fun sub(master: ByteArray, label: String): ByteArray = hmac(master, label.toByteArray(Charsets.US_ASCII))

    private fun xorStream(master: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        val key = sub(master, "enc")
        val out = ByteArray(data.size)
        val counter = ByteArray(8)
        var pos = 0
        var block = 0L
        while (pos < data.size) {
            for (k in 0 until 8) counter[7 - k] = ((block ushr (8 * k)) and 0xFF).toByte()  // big-endian
            val ks = hmac(key, nonce, counter)
            val n = minOf(ks.size, data.size - pos)
            for (k in 0 until n) out[pos + k] = (data[pos + k].toInt() xor ks[k].toInt()).toByte()
            pos += n
            block++
        }
        return out
    }

    private fun tag(master: ByteArray, nonce: ByteArray, ad: ByteArray, ct: ByteArray): ByteArray {
        val adLen = byteArrayOf((ad.size ushr 24).toByte(), (ad.size ushr 16).toByte(), (ad.size ushr 8).toByte(), ad.size.toByte())
        return hmac(sub(master, "mac"), "e1".toByteArray(Charsets.US_ASCII), nonce, adLen, ad, ct)
    }

    fun seal(master: ByteArray, plaintext: ByteArray, ad: ByteArray = ByteArray(0)): String {
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val ct = xorStream(master, nonce, plaintext)
        return PREFIX + b64Encode(nonce + ct + tag(master, nonce, ad, ct))
    }

    /** Returns the plaintext, or null if the blob is malformed, forged, or made with another key/slot. */
    fun open(master: ByteArray, blob: String?, ad: ByteArray = ByteArray(0)): ByteArray? {
        if (blob == null || !blob.startsWith(PREFIX)) return null
        val raw = b64Decode(blob.substring(PREFIX.length)) ?: return null
        if (raw.size < NONCE_LEN + TAG_LEN) return null
        val nonce = raw.copyOfRange(0, NONCE_LEN)
        val ct = raw.copyOfRange(NONCE_LEN, raw.size - TAG_LEN)
        val tg = raw.copyOfRange(raw.size - TAG_LEN, raw.size)
        if (!MessageDigest.isEqual(tg, tag(master, nonce, ad, ct))) return null
        return xorStream(master, nonce, ct)
    }

    // ---- accounts -------------------------------------------------------------------------------

    /** Blinded server-side id for an account name (the server never sees the name). */
    fun id(master: ByteArray, name: String): String {
        val h = hmac(sub(master, "id"), name.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder()
        for (b in h) hex.append(String.format("%02x", b.toInt() and 0xFF))
        return "e1." + hex.substring(0, 32)
    }

    fun makeCheck(master: ByteArray): String = seal(master, CHECK_PLAINTEXT, CHECK_KEY.toByteArray(Charsets.UTF_8))

    fun verifyCheck(master: ByteArray, blob: String?): Boolean =
        open(master, blob, CHECK_KEY.toByteArray(Charsets.UTF_8))?.contentEquals(CHECK_PLAINTEXT) == true

    /** name -> secret  ==>  blindedId -> blob. Names other clients would reject are not uploaded. */
    fun pack(master: ByteArray, accounts: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((name, secret) in accounts) {
            if (!isValidName(name)) continue
            val kid = id(master, name)
            val json = "{\"n\":" + jsonString(name) + ",\"s\":" + jsonString(secret) + "}"
            out[kid] = seal(master, json.toByteArray(Charsets.UTF_8), kid.toByteArray(Charsets.UTF_8))
        }
        return out
    }

    class Unpacked(
        val accounts: Map<String, String>,        // authenticated + decrypted
        val legacy: Map<String, String?>,         // entries that are NOT encrypted (old clients / tampering)
        val undecryptable: Int                    // "e1:" entries that failed authentication
    )

    /** Config keys ("__*") sent by the server are never accepted as accounts. */
    fun unpack(master: ByteArray, data: Map<String, String?>): Unpacked {
        val accounts = LinkedHashMap<String, String>()
        val legacy = LinkedHashMap<String, String?>()
        var bad = 0
        for ((k, v) in data) {
            if (k.startsWith("__")) continue
            if (v != null && v.startsWith(PREFIX)) {
                val pt = open(master, v, k.toByteArray(Charsets.UTF_8))
                val obj = pt?.let { parseFlatStringObject(String(it, Charsets.UTF_8)) }
                val n = obj?.get("n")
                val s = obj?.get("s")
                if (n != null && s != null && isValidName(n)) accounts[n] = s else bad++
            } else {
                legacy[k] = v
            }
        }
        return Unpacked(accounts, legacy, bad)
    }

    /** Same rule as the Python client: 1..128 printable characters, not starting with "__". */
    fun isValidName(n: String?): Boolean {
        if (n == null || n.startsWith("__")) return false
        val len = n.codePointCount(0, n.length)
        if (len == 0 || len > 128) return false
        var i = 0
        while (i < n.length) {
            val cp = n.codePointAt(i)
            i += Character.charCount(cp)
            if (!isPrintable(cp)) return false
        }
        return true
    }

    private fun isPrintable(cp: Int): Boolean {
        if (cp == ' '.code) return true
        return when (Character.getType(cp)) {
            Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.SURROGATE.toInt(),
            Character.PRIVATE_USE.toInt(), Character.UNASSIGNED.toInt(), Character.LINE_SEPARATOR.toInt(),
            Character.PARAGRAPH_SEPARATOR.toInt(), Character.SPACE_SEPARATOR.toInt() -> false
            else -> true
        }
    }

    // ---- base64url (with padding, as Python's urlsafe_b64encode) -----------------------------------

    private fun b64Encode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            sb.append(B64[b0 shr 2])
            sb.append(B64[((b0 and 3) shl 4) or (b1 shr 4)])
            sb.append(if (i + 1 < data.size) B64[((b1 and 15) shl 2) or (b2 shr 6)] else '=')
            sb.append(if (i + 2 < data.size) B64[b2 and 63] else '=')
            i += 3
        }
        return sb.toString()
    }

    private fun b64Decode(s: String): ByteArray? {
        val t = s.trimEnd('=')
        if (t.length % 4 == 1) return null
        val out = ByteArrayOutputStream()
        var buf = 0
        var bits = 0
        for (c in t) {
            val v = B64.indexOf(c)
            if (v < 0) return null
            buf = (buf shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buf shr bits) and 0xFF)
                buf = buf and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    // ---- tiny JSON for {"n": "...", "s": "..."} ----------------------------------------------------

    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch < ' ' -> sb.append(String.format("\\u%04x", ch.code))
                else -> sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }

    /** Parses a flat JSON object whose values are all strings; returns null for anything else. */
    internal fun parseFlatStringObject(s: String): Map<String, String>? {
        var i = 0
        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }
        fun str(): String? {
            if (i >= s.length || s[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) return null
                        when (s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) return null
                                val cp = s.substring(i, i + 4).toIntOrNull(16) ?: return null
                                sb.append(cp.toChar()); i += 4
                            }
                            else -> return null
                        }
                    }
                    c < ' ' -> return null
                    else -> sb.append(c)
                }
            }
            return null
        }
        val out = LinkedHashMap<String, String>()
        ws()
        if (i >= s.length || s[i] != '{') return null
        i++
        ws()
        if (i < s.length && s[i] == '}') { i++; ws(); return if (i == s.length) out else null }
        while (true) {
            ws(); val k = str() ?: return null
            ws(); if (i >= s.length || s[i] != ':') return null
            i++
            ws(); val v = str() ?: return null
            out[k] = v
            ws()
            if (i >= s.length) return null
            if (s[i] == ',') { i++; continue }
            if (s[i] == '}') { i++; break }
            return null
        }
        ws()
        return if (i == s.length) out else null
    }
}
