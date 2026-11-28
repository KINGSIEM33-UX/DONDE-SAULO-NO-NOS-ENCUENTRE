package com.example.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val INVITE_TOKEN_IN_PATH = Regex("(group_invites/)(INV-[A-Z0-9_-]{20,60})")

internal fun redactFirestorePath(path: String?): String? =
    path?.replace(INVITE_TOKEN_IN_PATH, "$1[REDACTED]")

internal fun redactFirestoreText(text: String): String =
    text.replace(Regex("INV-[A-Z0-9_-]{20,60}"), "INV-[REDACTED]")

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun todayIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("emailVerified", currentUser?.isEmailVerified)
    }

    val errorInfoJson = JSONObject().apply {
        put("error", redactFirestoreText(exception.message ?: exception.toString()))
        put("operationType", operationType.value)
        put("path", redactFirestorePath(path))
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

enum class OutingStatus(val label: String, val shortBadge: String) {
    CONFIRMED("Confirmada", "Sí"),
    PLANNED("Pensada / Por confirmar", "Tal vez"),
    CANCELLED("Cancelada", "No");

    companion object {
        fun fromString(value: String?): OutingStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PLANNED
        }
    }
}

enum class RsvpChoice(val label: String) {
    YES("Asistiré"),
    MAYBE("Aún no lo tengo claro"),
    NO("No asistiré"),
    NONE("Sin responder");

    companion object {
        fun fromString(value: String?): RsvpChoice {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}

enum class GroupRole(val label: String) {
    OWNER("Propietario"),
    ADMIN("Admin"),
    MEMBER("Miembro");

    val canManageInvites: Boolean
        get() = this == OWNER || this == ADMIN

    val canManageMembers: Boolean
        get() = this == OWNER || this == ADMIN

    companion object {
        fun fromString(value: String?): GroupRole {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEMBER
        }
    }
}

@IgnoreExtraProperties
data class SocialGroup(
    @DocumentId val id: String = "",
    val name: String = "Grupo de Salidas",
    val inviteCode: String = "", // Legacy v1 read-only metadata; v2 groups store invites in /group_invites/{token}
    val createdBy: String = "",
    val schemaVersion: Long = 1L,
    val memberIds: List<String> = emptyList(), // Legacy v1 fallback; v2 membership lives in /groups/{groupId}/members/{uid}
    val memberNames: Map<String, String> = emptyMap(), // Legacy v1 fallback
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(creatorUid: String, creatorName: String): Map<String, Any> {
        val code = inviteCode.trim().uppercase().take(32).ifBlank { id.take(32) }
        val cleanMembers = (memberIds + creatorUid).distinct().take(50)
        val names = memberNames.toMutableMap().apply {
            put(creatorUid, creatorName.take(60).ifBlank { "Usuario" })
        }
        return mapOf(
            "name" to name.take(80).ifBlank { "Mi Grupo" },
            "inviteCode" to code,
            "createdBy" to creatorUid,
            "memberIds" to cleanMembers,
            "memberNames" to names,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    /**
     * Schema v2 group creation payload: decoupled from invite tokens and from mutable
     * shared member arrays (membership is stored per-user in /groups/{groupId}/members/{uid}).
     */
    fun toV2CreateMap(creatorUid: String): Map<String, Any> {
        return mapOf(
            "name" to name.take(80).ifBlank { "Mi Grupo" },
            "createdBy" to creatorUid,
            "schemaVersion" to 2L,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class GroupMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val groupId: String = "",
    val displayName: String = "",
    val role: String = GroupRole.MEMBER.name,
    val inviteToken: String = "",
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val groupRole: GroupRole
        get() = GroupRole.fromString(role)

    companion object {
        fun toOwnerCreateMap(uid: String, groupId: String, displayName: String): Map<String, Any> = mapOf(
            "userId" to uid,
            "groupId" to groupId.take(128),
            "displayName" to displayName.take(60).ifBlank { "Usuario" },
            "role" to GroupRole.OWNER.name,
            "inviteToken" to "",
            "joinedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        fun toInviteJoinCreateMap(
            uid: String,
            groupId: String,
            displayName: String,
            inviteToken: String
        ): Map<String, Any> = mapOf(
            "userId" to uid,
            "groupId" to groupId.take(128),
            "displayName" to displayName.take(60).ifBlank { "Usuario" },
            "role" to GroupRole.MEMBER.name,
            "inviteToken" to inviteToken.take(64),
            "joinedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        fun toLegacyBackfillCreateMap(
            uid: String,
            groupId: String,
            displayName: String,
            role: GroupRole
        ): Map<String, Any> = mapOf(
            "userId" to uid,
            "groupId" to groupId.take(128),
            "displayName" to displayName.take(60).ifBlank { "Usuario" },
            "role" to role.name,
            "inviteToken" to "",
            "joinedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        fun toSelfUpdateMap(displayName: String): Map<String, Any> = mapOf(
            "displayName" to displayName.take(60).ifBlank { "Usuario" },
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class GroupInvite(
    @DocumentId val id: String = "",
    val token: String = "",
    val groupId: String = "",
    val groupNamePreview: String = "",
    val createdBy: String = "",
    val revoked: Boolean = false,
    val expiresAt: Timestamp? = null,
    val maxUses: Long = 1L,
    val useCount: Long = 0L,
    val lastRedeemedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isActiveAt(nowMillis: Long): Boolean {
        if (revoked) return false
        val expMs = expiresAt?.toDate()?.time ?: return false
        if (expMs <= nowMillis) return false
        return maxUses > 0L && useCount < maxUses
    }

    companion object {
        fun toCreateMap(
            token: String,
            groupId: String,
            groupNamePreview: String,
            creatorUid: String,
            expiresAt: Timestamp,
            maxUses: Long
        ): Map<String, Any> = mapOf(
            "token" to token.take(64),
            "groupId" to groupId.take(128),
            "groupNamePreview" to groupNamePreview.take(80).ifBlank { "Grupo" },
            "createdBy" to creatorUid,
            "revoked" to false,
            "expiresAt" to expiresAt,
            "maxUses" to maxUses.coerceIn(1L, 100L),
            "useCount" to 0L,
            "lastRedeemedBy" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

sealed class InviteRedemptionOutcome {
    data class Joined(val group: SocialGroup, val alreadyMember: Boolean) : InviteRedemptionOutcome()
    data object LegacyCodeUnsupported : InviteRedemptionOutcome()
    data class InvalidOrUnavailable(val message: String) : InviteRedemptionOutcome()
    data class Error(val message: String) : InviteRedemptionOutcome()
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val curiosities: List<String> = emptyList(),
    val groupId: String = "",
    val groupInviteCode: String = "",
    val groupName: String = "",
    val gamesHighScore: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        return mapOf(
            "userId" to uid,
            "username" to username.take(60).ifBlank { "Usuario" },
            "bio" to bio.take(300),
            "avatarUrl" to avatarUrl.take(500),
            "curiosities" to curiosities.take(20).map { it.take(500) },
            "groupId" to groupId.trim().take(128),
            "groupInviteCode" to groupInviteCode.trim().take(32),
            "groupName" to groupName.trim().take(80),
            "gamesHighScore" to gamesHighScore.coerceAtLeast(0L),
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "username" to username.take(60).ifBlank { "Usuario" },
            "bio" to bio.take(300),
            "avatarUrl" to avatarUrl.take(500),
            "curiosities" to curiosities.take(20).map { it.take(500) },
            "groupId" to groupId.trim().take(128),
            "groupInviteCode" to groupInviteCode.trim().take(32),
            "groupName" to groupName.trim().take(80),
            "gamesHighScore" to gamesHighScore.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class Outing(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val groupId: String = "",
    val title: String = "",
    val location: String = "",
    val meetingPoint: String = "",
    val dateIso: String = "", // YYYY-MM-DD
    val time: String = "19:00",
    val status: String = OutingStatus.CONFIRMED.name,
    val budgetPerPerson: String = "",
    val kitItems: String = "",
    val additionalInfo: String = "",
    val placePhotoUrl: String = "preset_plaza",
    val destLat: Double = 40.4168,
    val destLng: Double = -3.7038,
    val meetLat: Double = 40.4180,
    val meetLng: Double = -3.7020,
    val rsvpYesCount: Long = 1L,
    val rsvpMaybeCount: Long = 0L,
    val rsvpNoCount: Long = 0L,
    val myRsvp: String = RsvpChoice.YES.name,
    val rsvpByUser: Map<String, String> = emptyMap(),
    val albumPhotos: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val outingStatus: OutingStatus
        get() = OutingStatus.fromString(status)

    val rsvpChoice: RsvpChoice
        get() = RsvpChoice.fromString(myRsvp)

    fun rsvpChoiceFor(uid: String?): RsvpChoice {
        if (!uid.isNullOrBlank() && rsvpByUser.containsKey(uid)) {
            return RsvpChoice.fromString(rsvpByUser[uid])
        }
        return rsvpChoice
    }

    fun toCreateMap(uid: String): Map<String, Any> {
        val normalizedMap = if (rsvpByUser.isEmpty() && RsvpChoice.fromString(myRsvp) != RsvpChoice.NONE) {
            mapOf(uid to RsvpChoice.fromString(myRsvp).name)
        } else {
            rsvpByUser
        }
        val effectiveGroupId = SocialSessionPolicy.resolveGroupIdForWrite(groupId, uid)
        return mapOf(
            "ownerId" to uid,
            "groupId" to effectiveGroupId,
            "title" to title.take(120).ifBlank { "Salida en grupo" },
            "location" to location.take(200).ifBlank { "Centro" },
            "meetingPoint" to meetingPoint.take(200),
            "dateIso" to dateIso.take(20).ifBlank { todayIsoDate() },
            "time" to time.take(20).ifBlank { "19:00" },
            "status" to OutingStatus.fromString(status).name,
            "budgetPerPerson" to budgetPerPerson.take(60),
            "kitItems" to kitItems.take(500),
            "additionalInfo" to additionalInfo.take(1000),
            "placePhotoUrl" to placePhotoUrl.take(500).ifBlank { "preset_plaza" },
            "destLat" to destLat,
            "destLng" to destLng,
            "meetLat" to meetLat,
            "meetLng" to meetLng,
            "rsvpYesCount" to rsvpYesCount.coerceAtLeast(0L),
            "rsvpMaybeCount" to rsvpMaybeCount.coerceAtLeast(0L),
            "rsvpNoCount" to rsvpNoCount.coerceAtLeast(0L),
            "myRsvp" to RsvpChoice.fromString(myRsvp).name,
            "rsvpByUser" to normalizedMap,
            "albumPhotos" to albumPhotos.take(20).map { it.take(500) },
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toUpdateMap(): Map<String, Any> {
        val effectiveGroupId = SocialSessionPolicy.resolveGroupIdForWrite(groupId, ownerId)
        return mapOf(
            "groupId" to effectiveGroupId,
            "title" to title.take(120).ifBlank { "Salida en grupo" },
            "location" to location.take(200).ifBlank { "Centro" },
            "meetingPoint" to meetingPoint.take(200),
            "dateIso" to dateIso.take(20).ifBlank { todayIsoDate() },
            "time" to time.take(20).ifBlank { "19:00" },
            "status" to OutingStatus.fromString(status).name,
            "budgetPerPerson" to budgetPerPerson.take(60),
            "kitItems" to kitItems.take(500),
            "additionalInfo" to additionalInfo.take(1000),
            "placePhotoUrl" to placePhotoUrl.take(500).ifBlank { "preset_plaza" },
            "destLat" to destLat,
            "destLng" to destLng,
            "meetLat" to meetLat,
            "meetLng" to meetLng,
            "rsvpYesCount" to rsvpYesCount.coerceAtLeast(0L),
            "rsvpMaybeCount" to rsvpMaybeCount.coerceAtLeast(0L),
            "rsvpNoCount" to rsvpNoCount.coerceAtLeast(0L),
            "myRsvp" to RsvpChoice.fromString(myRsvp).name,
            "rsvpByUser" to rsvpByUser,
            "albumPhotos" to albumPhotos.take(20).map { it.take(500) },
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class GroupChatMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val groupId: String = "",
    val text: String = "",
    val messageType: String = "TEXT", // TEXT, VOICE, OUTING_SHARE, STORY_REACTION
    val audioDurationSec: Long = 0L,
    val audioUrl: String = "",
    val transcription: String = "",
    val sharedOutingId: String = "",
    val sharedOutingTitle: String = "",
    val reactions: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val validType = if (messageType in listOf("TEXT", "VOICE", "OUTING_SHARE", "STORY_REACTION")) {
            messageType
        } else {
            "TEXT"
        }
        val effectiveGroupId = SocialSessionPolicy.resolveGroupIdForWrite(groupId, uid)
        return mapOf(
            "senderId" to uid,
            "senderName" to senderName.take(80).ifBlank { "Miembro" },
            "groupId" to effectiveGroupId,
            "text" to text.take(1000),
            "messageType" to validType,
            "audioDurationSec" to audioDurationSec.coerceAtLeast(0L),
            "audioUrl" to audioUrl.take(500),
            "transcription" to transcription.take(1000),
            "sharedOutingId" to sharedOutingId.take(128),
            "sharedOutingTitle" to sharedOutingTitle.take(120),
            "reactions" to reactions.take(10).map { it.take(500) },
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class OutingStory(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val groupId: String = "",
    val caption: String = "",
    val filterStyle: String = "OBSIDIAN",
    val mediaUri: String = "story_sunset",
    val outingTitle: String = "Salida en grupo",
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val effectiveGroupId = SocialSessionPolicy.resolveGroupIdForWrite(groupId, uid)
        return mapOf(
            "authorId" to uid,
            "authorName" to authorName.take(80).ifBlank { "Miembro" },
            "groupId" to effectiveGroupId,
            "caption" to caption.take(300),
            "filterStyle" to filterStyle.take(40).ifBlank { "OBSIDIAN" },
            "mediaUri" to mediaUri.take(500).ifBlank { "story_sunset" },
            "outingTitle" to outingTitle.take(120),
            "likesCount" to likesCount.coerceAtLeast(0L),
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
