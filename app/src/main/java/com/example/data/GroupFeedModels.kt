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
    val groupGenerationId: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun belongsToGeneration(expectedGroupId: String, expectedGenerationId: String): Boolean =
        expectedGroupId.isNotBlank() &&
            expectedGenerationId.matches(Regex("^[a-f0-9]{32}$")) &&
            groupId == expectedGroupId &&
            groupGenerationId == expectedGenerationId

    fun toCreateMap(uid: String): Map<String, Any> {
        require(uid.isNotBlank()) { "El usuario autenticado es obligatorio" }
        val cleanGroupId = groupId.trim().take(128)
        require(cleanGroupId.isNotBlank()) { "El grupo activo es obligatorio" }
        val cleanGenerationId = groupGenerationId.trim()
        require(cleanGenerationId.matches(Regex("^[a-f0-9]{32}$"))) {
            "La generación actual del grupo debe tener 32 caracteres hexadecimales"
        }
        val cleanText = text.trim().take(2000)
        require(cleanText.isNotBlank()) { "La publicación no puede estar vacía" }
        return mapOf(
            "authorId" to uid,
            "authorName" to authorName.trim().take(80).ifBlank { "Miembro" },
            "groupId" to cleanGroupId,
            "groupGenerationId" to cleanGenerationId,
            "text" to cleanText,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

internal fun GroupMember.belongsToActiveGroup(userId: String, group: SocialGroup): Boolean =
    userId.isNotBlank() &&
        this.userId == userId &&
        group.id.isNotBlank() &&
        group.generationId.matches(Regex("^[a-f0-9]{32}$")) &&
        groupId == group.id &&
        groupGenerationId == group.generationId

enum class GroupFeedStatus { SIGNED_OUT, LOADING, READY, EMPTY, ERROR }

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
