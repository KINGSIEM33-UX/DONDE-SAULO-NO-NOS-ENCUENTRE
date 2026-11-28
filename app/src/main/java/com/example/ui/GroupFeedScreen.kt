package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GroupFeedStatus
import com.example.data.GroupPost
import java.text.DateFormat

@Composable
fun GroupFeedScreen(viewModel: MainViewModel) {
    val state by viewModel.groupFeedState.collectAsStateWithLifecycle()
    val activeGroup by viewModel.activeSocialGroup.collectAsStateWithLifecycle()
    val currentUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val visiblePosts = viewModel.visibleGroupFeedPosts(state.posts)
    var draft by remember(activeGroup?.id) { mutableStateOf("") }

    LaunchedEffect(state.publishSuccessToken, state.publishSuccessGroupId, activeGroup?.id) {
        if (state.publishSuccessToken > 0L && state.publishSuccessGroupId == activeGroup?.id) {
            draft = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DynamicFeed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Feed del grupo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    activeGroup?.name ?: "Espacio privado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            currentUid == null -> FeedMessage("Inicia sesión para ver las publicaciones de tu grupo.")
            activeGroup == null -> FeedMessage("Crea un grupo o únete a uno para activar el feed privado.")
            else -> {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 2000) draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Comparte algo con el grupo") },
                    supportingText = { Text("Solo miembros del grupo pueden leer y publicar aquí.") },
                    enabled = !state.isPublishing && state.status != GroupFeedStatus.SIGNED_OUT
                )
                Button(
                    onClick = { viewModel.createGroupPost(draft) },
                    enabled = draft.isNotBlank() && !state.isPublishing &&
                        state.status != GroupFeedStatus.LOADING && state.status != GroupFeedStatus.ERROR,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (state.isPublishing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Publicar")
                    }
                }
                state.actionMessage?.let { FeedMessage(it) }
                when (state.status) {
                    GroupFeedStatus.LOADING -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    GroupFeedStatus.ERROR -> FeedRetryMessage(
                        message = state.message ?: "No se pudo cargar el feed.",
                        onRetry = viewModel::retryGroupFeed
                    )
                    GroupFeedStatus.EMPTY -> FeedMessage("Todavía no hay publicaciones en este grupo.")
                    GroupFeedStatus.READY -> {
                        if (visiblePosts.isEmpty()) {
                            FeedRetryMessage(
                                message = "El feed se está sincronizando con el grupo activo.",
                                onRetry = viewModel::retryGroupFeed
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(visiblePosts, key = { it.id }) { post ->
                                    GroupPostCard(post = post, currentUid = currentUid, viewModel = viewModel)
                                }
                            }
                        }
                    }
                    GroupFeedStatus.SIGNED_OUT -> FeedMessage("El feed está disponible para miembros autenticados del grupo.")
                }
            }
        }
    }
}

@Composable
private fun FeedMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeedRetryMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FeedMessage(message)
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun GroupPostCard(post: GroupPost, currentUid: String?, viewModel: MainViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(post.authorName, fontWeight = FontWeight.SemiBold)
                    val date = post.createdAt?.toDate()?.let {
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
                    }
                    Text(
                        date ?: "Ahora",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (post.authorId == currentUid) {
                    val deleting = post.id in viewModel.groupFeedState.value.deletingPostIds
                    IconButton(onClick = { viewModel.deleteGroupPost(post.id) }, enabled = !deleting) {
                        if (deleting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar publicación")
                        }
                    }
                }
            }
            Text(post.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
