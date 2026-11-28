package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class GroupPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val groupId: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val cleanGroupId = groupId.trim().take(128)
        require(cleanGroupId.isNotBlank()) { "El grupo activo es obligatorio" }
        val cleanText = text.trim().take(2000)
        require(cleanText.isNotBlank()) { "La publicación no puede estar vacía" }
        return mapOf(
            "authorId" to uid,
            "authorName" to authorName.trim().take(80).ifBlank { "Miembro" },
            "groupId" to cleanGroupId,
            "text" to cleanText,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

enum class GroupFeedStatus { SIGNED_OUT, LOADING, READY, EMPTY, ERROR }

internal fun isCurrentGroupFeedScope(
    currentUid: String?,
    expectedUid: String,
    observedGroupId: String?,
    expectedGroupId: String
): Boolean = currentUid == expectedUid && observedGroupId == expectedGroupId

data class GroupFeedState(
    val status: GroupFeedStatus = GroupFeedStatus.SIGNED_OUT,
    val posts: List<GroupPost> = emptyList(),
    val message: String? = null,
    val isPublishing: Boolean = false,
    val deletingPostIds: Set<String> = emptySet(),
    val actionMessage: String? = null,
    val publishSuccessToken: Long = 0L,
    val publishSuccessGroupId: String? = null
)
