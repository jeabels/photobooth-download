package com.foodpos.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel with one-off user messages ("Saved", "Not enough stock") shown as snackbars. */
abstract class BaseViewModel : ViewModel() {
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    protected fun say(message: String) {
        _messages.trySend(message)
    }

    /** Shows [success] when the result succeeded, otherwise the error's message. */
    protected fun Result<*>.report(success: String? = null): Boolean {
        onSuccess { if (success != null) say(success) }
        onFailure { say(it.message ?: "Something went wrong") }
        return isSuccess
    }
}

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun ShowMessages(viewModel: BaseViewModel) {
    val snackbar = LocalSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
}
