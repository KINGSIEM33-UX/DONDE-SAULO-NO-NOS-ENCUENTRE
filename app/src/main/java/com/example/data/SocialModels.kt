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

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

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
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "Explorador",
    val bio: String = "Listo para la próxima salida con el grupo ✨",
    val avatarUrl: String = "avatar_sunset",
    val curiosities: List<String> = listOf("🍕 Pizza lover", "🎮 Gamer de noche", "🏔️ Senderismo", "🎶 Música en vivo"),
    val groupId: String = "GRUPO-QUEDADAS",
    val groupInviteCode: String = "QUEDA-2026",
    val groupName: String = "Peña Los Inseparables",
    val gamesHighScore: Long = 120L,
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
            "groupId" to groupId.take(128).ifBlank { "GRUPO-QUEDADAS" },
            "groupInviteCode" to groupInviteCode.take(32).ifBlank { "QUEDA-2026" },
            "groupName" to groupName.take(80).ifBlank { "Mi Grupo" },
            "gamesHighScore" to gamesHighScore,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "username" to username.take(60).ifBlank { "Usuario" },
            "bio" to bio.take(300),
            "avatarUrl" to avatarUrl.take(500),
            "curiosities" to curiosities.take(20).map { it.take(500) },
            "groupId" to groupId.take(128).ifBlank { "GRUPO-QUEDADAS" },
            "groupInviteCode" to groupInviteCode.take(32).ifBlank { "QUEDA-2026" },
            "groupName" to groupName.take(80).ifBlank { "Mi Grupo" },
            "gamesHighScore" to gamesHighScore,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

@IgnoreExtraProperties
data class Outing(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val groupId: String = "QUEDA-2026",
    val title: String = "",
    val location: String = "",
    val meetingPoint: String = "",
    val dateIso: String = "", // YYYY-MM-DD
    val time: String = "19:00",
    val status: String = OutingStatus.CONFIRMED.name,
    val budgetPerPerson: String = "15 €",
    val kitItems: String = "", // "Kit de la Quedada"
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
    val albumPhotos: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val outingStatus: OutingStatus
        get() = OutingStatus.fromString(status)

    val rsvpChoice: RsvpChoice
        get() = RsvpChoice.fromString(myRsvp)

    fun toCreateMap(uid: String): Map<String, Any> {
        return mapOf(
            "ownerId" to uid,
            "groupId" to groupId.take(128).ifBlank { "QUEDA-2026" },
            "title" to title.take(120).ifBlank { "Salida en grupo" },
            "location" to location.take(200).ifBlank { "Centro" },
            "meetingPoint" to meetingPoint.take(200),
            "dateIso" to dateIso.take(20).ifBlank { "2026-11-10" },
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
            "albumPhotos" to albumPhotos.take(20).map { it.take(500) },
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "groupId" to groupId.take(128).ifBlank { "QUEDA-2026" },
            "title" to title.take(120).ifBlank { "Salida en grupo" },
            "location" to location.take(200).ifBlank { "Centro" },
            "meetingPoint" to meetingPoint.take(200),
            "dateIso" to dateIso.take(20).ifBlank { "2026-11-10" },
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
            "albumPhotos" to albumPhotos.take(20).map { it.take(500) },
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

@IgnoreExtraProperties
data class GroupChatMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val groupId: String = "QUEDA-2026",
    val text: String = "",
    val messageType: String = "TEXT", // TEXT, VOICE, OUTING_SHARE, STORY_REACTION
    val audioDurationSec: Long = 0L,
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
        return mapOf(
            "senderId" to uid,
            "senderName" to senderName.take(80).ifBlank { "Miembro" },
            "groupId" to groupId.take(128).ifBlank { "QUEDA-2026" },
            "text" to text.take(1000),
            "messageType" to validType,
            "audioDurationSec" to audioDurationSec.coerceAtLeast(0L),
            "transcription" to transcription.take(1000),
            "sharedOutingId" to sharedOutingId.take(128),
            "sharedOutingTitle" to sharedOutingTitle.take(120),
            "reactions" to reactions.take(10).map { it.take(500) },
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

@IgnoreExtraProperties
data class OutingStory(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val caption: String = "",
    val filterStyle: String = "NEON_VIBES",
    val mediaUri: String = "story_sunset",
    val outingTitle: String = "Quedada en grupo",
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        return mapOf(
            "authorId" to uid,
            "authorName" to authorName.take(80).ifBlank { "Miembro" },
            "caption" to caption.take(300),
            "filterStyle" to filterStyle.take(40).ifBlank { "NEON_VIBES" },
            "mediaUri" to mediaUri.take(500).ifBlank { "story_sunset" },
            "outingTitle" to outingTitle.take(120),
            "likesCount" to likesCount.coerceAtLeast(0L),
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}
