package com.foodpos.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Salted SHA-256 password hashing.
 *
 * Every user gets a fresh 16-byte random salt. The salt and password are hashed with SHA-256 and the
 * digest is re-hashed [ITERATIONS] times (key stretching), so a stolen database is slow to brute-force.
 * The stored format is "sha256$<iterations>$<hex digest>", which lets the iteration count be raised
 * later without breaking existing accounts. Plain passwords are never stored or synced.
 */
object PasswordHasher {
    const val ITERATIONS = 10_000
    private const val PREFIX = "sha256"
    private val random = SecureRandom()

    fun newSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(password: String, salt: String, iterations: Int = ITERATIONS): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltBytes)
        var result = digest.digest(password.toByteArray(Charsets.UTF_8))
        repeat(iterations - 1) {
            digest.update(result)
            result = digest.digest(saltBytes)
        }
        return "$PREFIX$$iterations$${result.toHex()}"
    }

    /** Constant-time comparison, so timing does not reveal how much of a hash matched. */
    fun verify(password: String, salt: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 3 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val candidate = runCatching { hash(password, salt, iterations) }.getOrNull() ?: return false
        return MessageDigest.isEqual(candidate.toByteArray(), stored.toByteArray())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
