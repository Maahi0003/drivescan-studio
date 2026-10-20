package com.drivescan.shared

/**
 * Platform crypto primitives.
 * JVM/Android actual: PBKDF2WithHmacSHA256 via javax.crypto + SecureRandom.
 * (Same primitives the web app mirrors with WebCrypto PBKDF2.)
 */
expect object PinCrypto {
    fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray
    fun randomBytes(count: Int): ByteArray
}

/**
 * PIN records for the vault app-lock. Format: "iterations:saltHex:hashHex".
 * Honest scope (same as web app): this is an app lock, not file encryption.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun newRecord(pin: String): String {
        require(pin.length >= 4) { "PIN must be at least 4 digits" }
        val salt = PinCrypto.randomBytes(SALT_BYTES)
        val hash = PinCrypto.pbkdf2(pin, salt, ITERATIONS)
        return "$ITERATIONS:${salt.toHex()}:${hash.toHex()}"
    }

    fun verify(pin: String, record: String): Boolean {
        val parts = record.split(":")
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = parts[1].fromHex() ?: return false
        val expected = parts[2].fromHex() ?: return false
        val actual = PinCrypto.pbkdf2(pin, salt, iterations)
        return constantTimeEquals(actual, expected)
    }

    /** PBKDF2 output length in bits, exposed for the platform actual's key spec. */
    fun keyBits(): Int = KEY_BITS

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

private val HEX = "0123456789abcdef"

fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (byte in this) {
        val v = byte.toInt() and 0xFF
        sb.append(HEX[v shr 4])
        sb.append(HEX[v and 0x0F])
    }
    return sb.toString()
}

fun String.fromHex(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    var i = 0
    while (i < length) {
        val hi = HEX.indexOf(this[i].lowercaseChar())
        val lo = HEX.indexOf(this[i + 1].lowercaseChar())
        if (hi < 0 || lo < 0) return null
        out[i / 2] = ((hi shl 4) or lo).toByte()
        i += 2
    }
    return out
}
