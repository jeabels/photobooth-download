package com.foodpos.app

import com.foodpos.app.security.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun `correct password verifies, wrong one does not`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("s3cret-pass", salt)
        assertTrue(PasswordHasher.verify("s3cret-pass", salt, hash))
        assertFalse(PasswordHasher.verify("s3cret-pasS", salt, hash))
        assertFalse(PasswordHasher.verify("s3cret-pass", PasswordHasher.newSalt(), hash))
    }

    @Test
    fun `same password with different salts gives different hashes`() {
        val a = PasswordHasher.hash("password", PasswordHasher.newSalt())
        val b = PasswordHasher.hash("password", PasswordHasher.newSalt())
        assertNotEquals(a, b)
    }

    @Test
    fun `hash is sha256 hex with its iteration count`() {
        val hash = PasswordHasher.hash("password", PasswordHasher.newSalt())
        val parts = hash.split('$')
        assertEquals("sha256", parts[0])
        assertEquals(PasswordHasher.ITERATIONS.toString(), parts[1])
        assertEquals(64, parts[2].length)
        assertTrue(parts[2].all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `single iteration equals plain sha256 of salt plus password`() {
        val salt = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(byteArrayOf(1, 2, 3) + "abc".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("sha256$1$$digest", PasswordHasher.hash("abc", salt, iterations = 1))
    }

    @Test
    fun `malformed stored hashes never verify`() {
        val salt = PasswordHasher.newSalt()
        assertFalse(PasswordHasher.verify("x", salt, ""))
        assertFalse(PasswordHasher.verify("x", salt, "md5$1$abc"))
        assertFalse(PasswordHasher.verify("x", salt, "sha256$0$abc"))
    }
}
