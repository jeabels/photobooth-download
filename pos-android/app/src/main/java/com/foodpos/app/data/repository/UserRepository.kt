package com.foodpos.app.data.repository

import androidx.room.withTransaction
import com.foodpos.app.data.local.PosDatabase
import com.foodpos.app.data.local.Role
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.data.local.UserStatus
import com.foodpos.app.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow

/** Owner-only account management: approve or reject staff, change roles. */
class UserRepository(private val db: PosDatabase, private val sync: SyncScheduler) {
    private val users = db.userDao()

    fun observeAll(): Flow<List<UserEntity>> = users.observeAll()

    suspend fun setStatus(userId: String, status: UserStatus): Result<Unit> = change(userId) { user ->
        if (status != UserStatus.APPROVED && user.role == Role.OWNER && users.countActiveOwners() <= 1) {
            error("The store needs at least one active owner")
        }
        user.copy(status = status)
    }

    suspend fun setRole(userId: String, role: Role): Result<Unit> = change(userId) { user ->
        if (role == Role.STAFF && user.role == Role.OWNER && users.countActiveOwners() <= 1) {
            error("The store needs at least one active owner")
        }
        user.copy(role = role)
    }

    private suspend fun change(userId: String, edit: suspend (UserEntity) -> UserEntity): Result<Unit> =
        runCatching {
            db.withTransaction {
                val user = users.findById(userId) ?: error("Account not found")
                users.upsert(edit(user).copy(updatedAt = System.currentTimeMillis(), dirty = true))
            }
            sync.requestSync()
        }
}
