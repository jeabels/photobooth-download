package com.foodpos.app.data.repository

import androidx.room.withTransaction
import com.foodpos.app.core.newId
import com.foodpos.app.data.local.PosDatabase
import com.foodpos.app.data.local.Role
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.data.local.UserStatus
import com.foodpos.app.data.sync.SyncScheduler
import com.foodpos.app.security.PasswordHasher
import com.foodpos.app.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AuthResult {
    data class SignedIn(val user: UserEntity) : AuthResult
    /** Registered or signed in, but an owner still has to approve the account. */
    data class AwaitingApproval(val user: UserEntity) : AuthResult
    data class Failed(val message: String) : AuthResult
}

/**
 * Registration and login. The first account on a store becomes the Owner; every later account is
 * Staff and stays Pending until an Owner approves it.
 */
class AuthRepository(
    private val db: PosDatabase,
    private val session: SessionManager,
    private val sync: SyncScheduler,
) {
    private val users = db.userDao()

    suspend fun hasAnyUser(): Boolean = users.count() > 0

    suspend fun register(fullName: String, username: String, password: String): AuthResult {
        val name = fullName.trim()
        val login = normalize(username)
        validate(name, login, password)?.let { return AuthResult.Failed(it) }

        val salt = PasswordHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(password, salt) }
        val user = db.withTransaction {
            if (users.findByUsername(login).isNotEmpty()) return@withTransaction null
            val isFirst = users.count() == 0
            val now = System.currentTimeMillis()
            UserEntity(
                id = newId(),
                username = login,
                fullName = name,
                passwordHash = hash,
                salt = salt,
                role = if (isFirst) Role.OWNER else Role.STAFF,
                status = if (isFirst) UserStatus.APPROVED else UserStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            ).also { users.upsert(it) }
        } ?: return AuthResult.Failed("That username is already taken")

        sync.requestSync()
        return if (user.status == UserStatus.APPROVED) {
            session.signIn(user)
            AuthResult.SignedIn(user)
        } else {
            AuthResult.AwaitingApproval(user)
        }
    }

    suspend fun login(username: String, password: String): AuthResult {
        val candidates = users.findByUsername(normalize(username))
        val user = withContext(Dispatchers.Default) {
            candidates.firstOrNull { PasswordHasher.verify(password, it.salt, it.passwordHash) }
        } ?: return AuthResult.Failed("Wrong username or password")
        return when (user.status) {
            UserStatus.APPROVED -> {
                session.signIn(user)
                AuthResult.SignedIn(user)
            }
            UserStatus.PENDING -> AuthResult.AwaitingApproval(user)
            UserStatus.REJECTED -> AuthResult.Failed("This account was not approved. Please ask the owner.")
        }
    }

    fun logout() = session.signOut()

    private fun normalize(username: String) = username.trim().lowercase()

    private fun validate(name: String, login: String, password: String): String? = when {
        name.isEmpty() -> "Enter your full name"
        !USERNAME.matches(login) -> "Username: 3 to 32 letters, numbers, dots or underscores"
        password.length < MIN_PASSWORD -> "Password must be at least $MIN_PASSWORD characters"
        else -> null
    }

    private companion object {
        const val MIN_PASSWORD = 6
        val USERNAME = Regex("^[a-z0-9._]{3,32}$")
    }
}
