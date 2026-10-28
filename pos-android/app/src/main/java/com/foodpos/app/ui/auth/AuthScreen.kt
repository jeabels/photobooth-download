package com.foodpos.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodpos.app.ui.appViewModel

@Composable
fun AuthScreen() {
    val vm = appViewModel { AuthViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showSync by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        // The card is centred when it fits and scrolls when the keyboard leaves too little room.
        ElevatedCard(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PointOfSale, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Text(
                    when {
                        state.firstRun -> "Set up your store"
                        state.registering -> "Create a staff account"
                        else -> "Sign in"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    when {
                        state.firstRun -> "The first account is the Owner account, with full access."
                        state.registering -> "New staff accounts can sign in once an Owner approves them."
                        else -> "Works offline. Your data stays on this device and syncs when online."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))

                if (state.registering) {
                    OutlinedTextField(
                        value = state.fullName,
                        onValueChange = { v -> vm.edit { it.copy(fullName = v) } },
                        label = { Text("Full name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { v -> vm.edit { it.copy(username = v.trim()) } },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { v -> vm.edit { it.copy(password = v) } },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (state.registering) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.registering) {
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = { v -> vm.edit { it.copy(confirmPassword = v) } },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                state.info?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }

                Button(onClick = vm::submit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            when {
                                state.firstRun -> "Create owner account"
                                state.registering -> "Request access"
                                else -> "Sign in"
                            },
                        )
                    }
                }
                if (!state.firstRun) {
                    TextButton(onClick = vm::toggleMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (state.registering) "I already have an account" else "New staff? Create an account")
                    }
                }

                if (state.syncAvailable) {
                    HorizontalDivider()
                    TextButton(onClick = { showSync = !showSync }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (showSync) "Hide store sync" else "Join an existing store (sync key)")
                    }
                    if (showSync) {
                        Text(
                            "Enter the sync key shown in Settings on the owner's device to download the store's accounts and data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = state.syncKey,
                            onValueChange = { v -> vm.edit { it.copy(syncKey = v) } },
                            label = { Text("Store sync key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = vm::downloadStore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Download store data")
                        }
                    }
                }
            }
        }
    }
}
