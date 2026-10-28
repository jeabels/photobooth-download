package com.foodpos.app.ui.auth

import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.data.repository.AuthResult
import com.foodpos.app.ui.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val registering: Boolean = false,
    /** No account exists yet on this device: the next registration creates the Owner. */
    val firstRun: Boolean = false,
    val fullName: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val syncAvailable: Boolean = false,
    val syncKey: String = "",
)

class AuthViewModel(private val container: AppContainer) : BaseViewModel() {
    private val _state = MutableStateFlow(
        AuthUiState(syncAvailable = container.sync.isConfigured, syncKey = container.settings.current.syncKey),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        refreshFirstRun()
    }

    private fun refreshFirstRun() {
        viewModelScope.launch {
            val first = !container.auth.hasAnyUser()
            _state.update { it.copy(firstRun = first, registering = first || it.registering) }
        }
    }

    fun edit(transform: (AuthUiState) -> AuthUiState) = _state.update { transform(it).copy(error = null) }

    fun toggleMode() = _state.update { it.copy(registering = !it.registering, error = null, info = null) }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        if (s.registering && s.password != s.confirmPassword) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            val result = if (s.registering) {
                container.auth.register(s.fullName, s.username, s.password)
            } else {
                container.auth.login(s.username, s.password)
            }
            _state.update {
                when (result) {
                    is AuthResult.SignedIn -> it.copy(busy = false)
                    is AuthResult.AwaitingApproval -> it.copy(
                        busy = false,
                        registering = false,
                        password = "",
                        confirmPassword = "",
                        info = "Account for ${result.user.fullName} is waiting for an owner's approval.",
                    )
                    is AuthResult.Failed -> it.copy(busy = false, error = result.message)
                }
            }
        }
    }

    /** Links this device to an existing store and downloads its accounts before signing in. */
    fun downloadStore() {
        val key = _state.value.syncKey.trim().uppercase()
        if (key.isEmpty()) return
        _state.update { it.copy(busy = true, error = null, info = null) }
        container.settings.update { it.copy(syncKey = key) }
        viewModelScope.launch {
            val result = container.sync.syncNow()
            _state.update {
                if (result.isSuccess) {
                    it.copy(busy = false, syncKey = key, info = "Store data downloaded. You can sign in now.", registering = false)
                } else {
                    it.copy(busy = false, error = "Could not sync: ${result.exceptionOrNull()?.message}")
                }
            }
            refreshFirstRun()
        }
    }
}
