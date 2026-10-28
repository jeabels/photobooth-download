package com.foodpos.app.security

import android.content.Context
import com.foodpos.app.data.local.Role
import com.foodpos.app.data.local.UserDao
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.data.local.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: UserEntity) : SessionState {
        val isOwner: Boolean get() = user.role == Role.OWNER
    }
}

/**
 * Remembers who is signed in on this device. The session follows the user's row, so if an owner
 * rejects an account (on any synced device) that person is signed out right away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManager(context: Context, userDao: UserDao, scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("pos_session", Context.MODE_PRIVATE)
    private val userId = MutableStateFlow(prefs.getString(KEY_USER_ID, null))

    val state: StateFlow<SessionState> = userId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(SessionState.SignedOut)
            } else {
                userDao.observeById(id).map { user ->
                    if (user != null && user.status == UserStatus.APPROVED) {
                        SessionState.SignedIn(user)
                    } else {
                        SessionState.SignedOut
                    }
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    val currentUser: UserEntity? get() = (state.value as? SessionState.SignedIn)?.user

    fun signIn(user: UserEntity) {
        prefs.edit().putString(KEY_USER_ID, user.id).apply()
        userId.value = user.id
    }

    fun signOut() {
        prefs.edit().remove(KEY_USER_ID).apply()
        userId.value = null
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
    }
}
