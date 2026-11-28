package com.example.data

/**
 * Pure-JVM authorization & self-only interactive delta policy for group/personal content
 * (outings, chat messages, and stories). Mirrors the invariants enforced server-side in
 * firestore.rules without reverting or replacing SocialSessionPolicy.
 */
object SocialContentAuthzPolicy {

    const val OWNER_ONLY_OUTING_ERROR =
        "Solo el creador de la salida puede editarla, cambiar su estado, mover su ubicación o eliminarla."

    const val NON_MEMBER_WRITE_ERROR =
        "Solo los miembros actuales del grupo pueden publicar o interactuar con su contenido."

    data class SelfRsvpTransition(
        val oldChoice: RsvpChoice,
        val newChoice: RsvpChoice,
        val yesDelta: Long,
        val maybeDelta: Long,
        val noDelta: Long,
        val nextYesCount: Long,
        val nextMaybeCount: Long,
        val nextNoCount: Long,
        val nextRsvpByUser: Map<String, String>,
        val isNoOp: Boolean
    )

    data class SelfStoryLikeTransition(
        val wasLiked: Boolean,
        val isLiked: Boolean,
        val likesDelta: Long,
        val nextLikesCount: Long,
        val nextLikedByUser: Map<String, Boolean>
    )

    data class SelfChatReactionTransition(
        val removed: Boolean,
        val emoji: String?,
        val nextReactionsByUser: Map<String, String>
    )

    fun isPersonalScope(groupId: String, userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false
        return groupId == SocialSessionPolicy.resolveGroupIdForWrite("", userId)
    }

    fun canEditOrCancelOuting(
        outing: Outing,
        currentUserId: String?,
        isGuestDemo: Boolean
    ): Boolean {
        if (isGuestDemo) return true
        return !currentUserId.isNullOrBlank() && outing.ownerId == currentUserId
    }

    fun canDeleteOuting(
        outing: Outing,
        currentUserId: String?,
        isGuestDemo: Boolean
    ): Boolean = canEditOrCancelOuting(outing, currentUserId, isGuestDemo)

    fun canWriteToGroupScope(
        targetGroupId: String,
        currentUserId: String?,
        activeGroupId: String?,
        isCurrentGroupMember: Boolean,
        isGuestDemo: Boolean
    ): Boolean {
        if (isGuestDemo) return true
        if (currentUserId.isNullOrBlank() || targetGroupId.isBlank()) return false
        if (isPersonalScope(targetGroupId, currentUserId)) return true
        return !activeGroupId.isNullOrBlank() && targetGroupId == activeGroupId && isCurrentGroupMember
    }

    fun effectiveOutingRsvpForUser(
        outing: Outing,
        currentUserId: String?,
        isGuestDemo: Boolean
    ): RsvpChoice {
        if (isGuestDemo) return outing.rsvpChoiceFor(currentUserId)
        if (currentUserId.isNullOrBlank()) return RsvpChoice.NONE
        if (outing.rsvpByUser.containsKey(currentUserId)) {
            return RsvpChoice.fromString(outing.rsvpByUser[currentUserId])
        }
        if (outing.rsvpByUser.isEmpty() && outing.ownerId == currentUserId) {
            return outing.rsvpChoice
        }
        return RsvpChoice.NONE
    }

    fun computeSelfRsvpTransition(
        existingByUser: Map<String, String>,
        existingYes: Long,
        existingMaybe: Long,
        existingNo: Long,
        userId: String,
        newChoice: RsvpChoice
    ): SelfRsvpTransition {
        val oldChoice = RsvpChoice.fromString(existingByUser[userId])
        val yesDelta = (if (newChoice == RsvpChoice.YES) 1L else 0L) - (if (oldChoice == RsvpChoice.YES) 1L else 0L)
        val maybeDelta = (if (newChoice == RsvpChoice.MAYBE) 1L else 0L) - (if (oldChoice == RsvpChoice.MAYBE) 1L else 0L)
        val noDelta = (if (newChoice == RsvpChoice.NO) 1L else 0L) - (if (oldChoice == RsvpChoice.NO) 1L else 0L)

        val nextMap = existingByUser.toMutableMap()
        if (newChoice == RsvpChoice.NONE) {
            nextMap.remove(userId)
        } else {
            nextMap[userId] = newChoice.name
        }

        val isNoOp = oldChoice == newChoice && (newChoice != RsvpChoice.NONE || !existingByUser.containsKey(userId))
        return SelfRsvpTransition(
            oldChoice = oldChoice,
            newChoice = newChoice,
            yesDelta = yesDelta,
            maybeDelta = maybeDelta,
            noDelta = noDelta,
            nextYesCount = (existingYes + yesDelta).coerceAtLeast(0L),
            nextMaybeCount = (existingMaybe + maybeDelta).coerceAtLeast(0L),
            nextNoCount = (existingNo + noDelta).coerceAtLeast(0L),
            nextRsvpByUser = nextMap,
            isNoOp = isNoOp
        )
    }

    fun computeSelfStoryLikeTransition(
        existingLikedByUser: Map<String, Boolean>,
        existingLikesCount: Long,
        userId: String
    ): SelfStoryLikeTransition {
        val wasLiked = existingLikedByUser[userId] == true
        val isLiked = !wasLiked
        val delta = if (isLiked) 1L else -1L
        val nextMap = existingLikedByUser.toMutableMap()
        if (isLiked) {
            nextMap[userId] = true
        } else {
            nextMap.remove(userId)
        }
        return SelfStoryLikeTransition(
            wasLiked = wasLiked,
            isLiked = isLiked,
            likesDelta = delta,
            nextLikesCount = (existingLikesCount + delta).coerceAtLeast(0L),
            nextLikedByUser = nextMap
        )
    }

    fun computeSelfChatReactionTransition(
        existingReactionsByUser: Map<String, String>,
        userId: String,
        emoji: String
    ): SelfChatReactionTransition {
        val cleanEmoji = emoji.trim().take(16)
        val currentOwn = existingReactionsByUser[userId]
        val nextMap = existingReactionsByUser.toMutableMap()
        val removed = cleanEmoji.isEmpty() || currentOwn == cleanEmoji
        if (removed) {
            nextMap.remove(userId)
        } else {
            nextMap[userId] = cleanEmoji
        }
        return SelfChatReactionTransition(
            removed = removed,
            emoji = if (removed) null else cleanEmoji,
            nextReactionsByUser = nextMap
        )
    }
}
