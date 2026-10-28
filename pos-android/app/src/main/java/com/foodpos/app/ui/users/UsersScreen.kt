package com.foodpos.app.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.core.Dates
import com.foodpos.app.data.local.Role
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.data.local.UserStatus
import com.foodpos.app.ui.BaseViewModel
import com.foodpos.app.ui.ShowMessages
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.components.EmptyState
import com.foodpos.app.ui.components.StatusPill
import com.foodpos.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UsersViewModel(private val container: AppContainer) : BaseViewModel() {
    val users = container.users.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStatus(user: UserEntity, status: UserStatus) = viewModelScope.launch {
        val message = when (status) {
            UserStatus.APPROVED -> "${user.fullName} can now sign in"
            UserStatus.REJECTED -> "${user.fullName} no longer has access"
            UserStatus.PENDING -> null
        }
        container.users.setStatus(user.id, status).report(message)
    }

    fun setRole(user: UserEntity, role: Role) = viewModelScope.launch {
        container.users.setRole(user.id, role).report("${user.fullName} is now ${if (role == Role.OWNER) "an Owner" else "Staff"}")
    }
}

/** Owner-only: approve new staff, revoke access and promote to Owner. */
@Composable
fun UsersScreen(currentUserId: String) {
    val vm = appViewModel { UsersViewModel(it) }
    val users by vm.users.collectAsStateWithLifecycle()
    ShowMessages(vm)

    if (users.isEmpty()) {
        EmptyState(Icons.Filled.Group, "No accounts", "Staff create their accounts on the sign-in screen.")
        return
    }
    val groups = listOf(
        "Waiting for approval" to users.filter { it.status == UserStatus.PENDING },
        "Active" to users.filter { it.status == UserStatus.APPROVED },
        "No access" to users.filter { it.status == UserStatus.REJECTED },
    ).filter { it.second.isNotEmpty() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { (title, list) ->
            item(key = title) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(list, key = { it.id }) { user -> UserCard(user, isMe = user.id == currentUserId, vm = vm) }
        }
    }
}

@Composable
private fun UserCard(user: UserEntity, isMe: Boolean, vm: UsersViewModel) {
    val colors = LocalStatusColors.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(user.fullName + if (isMe) " (you)" else "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "@${user.username} · joined ${Dates.formatDate(Dates.toLocalDate(user.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (user.status) {
                    UserStatus.PENDING -> StatusPill("Pending", colors.warning, colors.warningContainer)
                    UserStatus.REJECTED -> StatusPill("No access", colors.danger, colors.dangerContainer)
                    UserStatus.APPROVED -> StatusPill(if (user.role == Role.OWNER) "Owner" else "Staff", colors.good, colors.goodContainer)
                }
            }
            if (!isMe) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (user.status) {
                        UserStatus.PENDING -> {
                            Button(onClick = { vm.setStatus(user, UserStatus.APPROVED) }) { Text("Approve") }
                            OutlinedButton(onClick = { vm.setStatus(user, UserStatus.REJECTED) }) { Text("Reject") }
                        }
                        UserStatus.APPROVED -> {
                            TextButton(onClick = { vm.setRole(user, if (user.role == Role.OWNER) Role.STAFF else Role.OWNER) }) {
                                Text(if (user.role == Role.OWNER) "Make staff" else "Make owner")
                            }
                            TextButton(onClick = { vm.setStatus(user, UserStatus.REJECTED) }) {
                                Text("Remove access", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        UserStatus.REJECTED -> TextButton(onClick = { vm.setStatus(user, UserStatus.APPROVED) }) { Text("Restore access") }
                    }
                }
            }
        }
    }
}
