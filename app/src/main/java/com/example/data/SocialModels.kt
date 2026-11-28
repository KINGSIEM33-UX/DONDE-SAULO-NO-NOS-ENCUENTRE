package com.example.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
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

@IgnoreExtraProperties
data class SocialGroup(
    @DocumentId val id: String = "",
    val name: String = "Grupo de Salidas",
    val inviteCode: String = "",
    val createdBy: String = "",
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
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
        val initialChoice = if (rsvpByUser.containsKey(uid)) {
            RsvpChoice.fromString(rsvpByUser[uid])
        } else {
            RsvpChoice.fromString(myRsvp)
        }
        val initialMap = if (initialChoice == RsvpChoice.NONE) {
            emptyMap()
        } else {
            mapOf(uid to initialChoice.name)
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
            "rsvpYesCount" to if (initialChoice == RsvpChoice.YES) 1L else 0L,
            "rsvpMaybeCount" to if (initialChoice == RsvpChoice.MAYBE) 1L else 0L,
            "rsvpNoCount" to if (initialChoice == RsvpChoice.NO) 1L else 0L,
            "myRsvp" to initialChoice.name,
            "rsvpByUser" to initialMap,
            "albumPhotos" to albumPhotos.take(20).map { it.take(500) },
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    /**
     * Owner-only editorial update map. The parameter remains for source compatibility;
     * RSVP/counter and immutable fields are always omitted.
     */
    fun toUpdateMap(includeRsvp: Boolean = true): Map<String, Any> {
        return mapOf(
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
    val reactionsByUser: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun effectiveReactions(): List<String> {
        val fromMap = reactionsByUser.values.filter { it.isNotBlank() }.distinct()
        return if (fromMap.isNotEmpty()) fromMap else reactions
    }

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
            "reactions" to emptyList<String>(),
            "reactionsByUser" to emptyMap<String, String>(),
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
    val likedByUser: Map<String, Boolean> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isLikedBy(uid: String?): Boolean = !uid.isNullOrBlank() && likedByUser[uid] == true

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
            "likesCount" to 0L,
            "likedByUser" to emptyMap<String, Boolean>(),
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
