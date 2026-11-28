package com.example.data

import java.security.MessageDigest

/**
 * Distinguishes whether the Social world is currently showing clearly-labeled
 * local Guest Demo data or real cloud data for an authenticated account.
 */
enum class DataOrigin {
    GUEST_DEMO,
    CLOUD_REAL
}

/**
 * Explicit lifecycle state for each cloud-backed social resource so load errors
 * and in-flight fetches are never confused with an honest empty dataset.
 */
sealed class CloudLoadState {
    data object GuestDemo : CloudLoadState()
    data object Loading : CloudLoadState()
    data object Loaded : CloudLoadState()
    data class Error(
        val message: String,
        val hasCachedRealData: Boolean = false
    ) : CloudLoadState()
}

data class SocialSyncSnapshot(
    val origin: DataOrigin = DataOrigin.GUEST_DEMO,
    val profileState: CloudLoadState = CloudLoadState.GuestDemo,
    val groupState: CloudLoadState = CloudLoadState.GuestDemo,
    val outingsState: CloudLoadState = CloudLoadState.GuestDemo,
    val chatState: CloudLoadState = CloudLoadState.GuestDemo,
    val storiesState: CloudLoadState = CloudLoadState.GuestDemo
) {
    val isGuestDemo: Boolean
        get() = origin == DataOrigin.GUEST_DEMO

    val isAnyLoading: Boolean
        get() = listOf(profileState, groupState, outingsState, chatState, storiesState)
            .any { it is CloudLoadState.Loading }

    val firstErrorMessage: String?
        get() = listOf(profileState, groupState, outingsState, chatState, storiesState)
            .filterIsInstance<CloudLoadState.Error>()
            .firstOrNull()
            ?.message

    companion object {
        fun forGuestDemo(): SocialSyncSnapshot = SocialSyncSnapshot(
            origin = DataOrigin.GUEST_DEMO,
            profileState = CloudLoadState.GuestDemo,
            groupState = CloudLoadState.GuestDemo,
            outingsState = CloudLoadState.GuestDemo,
            chatState = CloudLoadState.GuestDemo,
            storiesState = CloudLoadState.GuestDemo
        )

        fun forRealUserLoading(): SocialSyncSnapshot = SocialSyncSnapshot(
            origin = DataOrigin.CLOUD_REAL,
            profileState = CloudLoadState.Loading,
            groupState = CloudLoadState.Loading,
            outingsState = CloudLoadState.Loading,
            chatState = CloudLoadState.Loading,
            storiesState = CloudLoadState.Loading
        )
    }
}

/**
 * Pure-JVM session & data integrity policies for Adora:
 * - Real accounts NEVER auto-seed fictional outings, chat, stories, profiles, or groups into Firestore.
 * - Real accounts start with an honest empty/loading state until real Firestore data arrives.
 * - Load errors are explicitly surfaced and NEVER masquerade as empty lists.
 * - Guest mode retains demo data only under DataOrigin.GUEST_DEMO with explicit UI labeling.
 */
object SocialSessionPolicy {

    const val GUEST_DEMO_USER_ID = "local_user"
    const val GUEST_DEMO_GROUP_ID = "QUEDA-2026"
    const val PERSONAL_GROUP_PREFIX = "personal_"
    private const val MAX_FIRESTORE_ID_LENGTH = 128

    val GUEST_DEMO_OUTING_IDS: Set<String> = setOf(
        "outing_upcoming_confirmed",
        "outing_upcoming_planned",
        "outing_upcoming_cancelled",
        "outing_upcoming_escape",
        "outing_history_past"
    )

    val GUEST_DEMO_CHAT_IDS: Set<String> = setOf("msg_1", "msg_2", "msg_3")
    val GUEST_DEMO_STORY_IDS: Set<String> = setOf("story_1", "story_2", "story_3")

    /**
     * Real accounts must NEVER auto-seed demo outings into Firestore.
     */
    fun shouldAutoSeedCloudOutings(): Boolean = false

    fun isGuestDemoOuting(outing: Outing): Boolean {
        return outing.id in GUEST_DEMO_OUTING_IDS || outing.ownerId == GUEST_DEMO_USER_ID
    }

    fun buildCleanRealUserProfile(
        uid: String,
        displayName: String? = null,
        email: String? = null,
        photoUrl: String? = null
    ): UserProfile {
        val cleanName = displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }
            ?: "Usuario"
        return UserProfile(
            id = uid,
            userId = uid,
            username = cleanName.take(60),
            bio = "",
            avatarUrl = photoUrl?.trim().orEmpty().take(500),
            curiosities = emptyList(),
            groupId = "",
            groupInviteCode = "",
            groupName = "",
            gamesHighScore = 0L
        )
    }

    /**
     * Preserves an existing remote profile untouched, or returns a clean empty real profile
     * (never inheriting demo bio/curiosities/group/score from Guest mode).
     */
    fun resolveProfileOnCloudEmit(
        remoteProfile: UserProfile?,
        uid: String,
        displayName: String? = null,
        email: String? = null,
        photoUrl: String? = null
    ): Pair<UserProfile, Boolean> {
        if (remoteProfile != null) {
            return remoteProfile to true
        }
        return buildCleanRealUserProfile(uid, displayName, email, photoUrl) to false
    }

    /**
     * Resolves the real shared group ID from a user's profile.
     * Returns "" when the user has not created or joined a group yet (never fabricates QUEDA-2026).
     */
    fun resolveTargetGroupId(profile: UserProfile): String {
        val candidate = profile.groupId.trim().ifBlank { profile.groupInviteCode.trim() }
        return candidate.take(128)
    }

    /**
     * Provides a valid Firestore groupId when a signed-in user creates a personal outing/message/story
     * before creating or joining a shared group, satisfying firestore.rules (groupId size 1..128)
     * without ever writing to the guest demo group QUEDA-2026.
     */
    fun resolveGroupIdForWrite(profileGroupId: String, uid: String): String {
        val cleanGroup = profileGroupId.trim().take(128)
        if (cleanGroup.isNotBlank()) return cleanGroup
        // Keep the contract expected by the personal-scope rules whenever the
        // Firebase UID itself is a valid Firestore ID component and fits.
        if (uid.matches(Regex("[a-zA-Z0-9_\\-]+")) &&
            PERSONAL_GROUP_PREFIX.length + uid.length <= MAX_FIRESTORE_ID_LENGTH
        ) {
            return PERSONAL_GROUP_PREFIX + uid
        }

        // UID formats outside that contract (or an unusually long UID) still
        // need a stable, collision-resistant ID rather than lossy truncation.
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return PERSONAL_GROUP_PREFIX + digest
    }

    /**
     * Defense-in-depth guard: only allows syncing outings that belong to a confirmed CLOUD_REAL
     * loaded state and never permits guest demo outings to be uploaded to Firestore.
     */
    fun filterOutingsAllowedForCloudWrite(
        origin: DataOrigin,
        outingsState: CloudLoadState,
        outings: List<Outing>
    ): List<Outing> {
        if (origin != DataOrigin.CLOUD_REAL) return emptyList()
        if (outingsState !is CloudLoadState.Loaded) return emptyList()
        return outings.filterNot { isGuestDemoOuting(it) }
    }

    /**
     * Guard used at the actual write boundary. Real outings may be saved while
     * their collection is loading, but guest/demo records must never cross into
     * Firestore from an authenticated session.
     */
    fun isOutingAllowedForCloudWrite(origin: DataOrigin, outing: Outing): Boolean {
        return origin == DataOrigin.CLOUD_REAL && !isGuestDemoOuting(outing)
    }

    /**
     * Returns true ONLY when the resource is genuinely empty (not loading and not in error).
     */
    fun shouldShowEmptyState(loadState: CloudLoadState, itemCount: Int): Boolean {
        if (itemCount > 0) return false
        return loadState is CloudLoadState.Loaded || loadState is CloudLoadState.GuestDemo
    }

    fun resolveErrorState(
        scopeLabel: String,
        throwable: Throwable?,
        currentOrigin: DataOrigin,
        currentItemCount: Int
    ): CloudLoadState.Error {
        val hasCachedRealData = currentOrigin == DataOrigin.CLOUD_REAL && currentItemCount > 0
        return CloudLoadState.Error(
            message = formatLoadErrorMessage(scopeLabel, throwable),
            hasCachedRealData = hasCachedRealData
        )
    }

    fun formatLoadErrorMessage(scopeLabel: String, throwable: Throwable?): String {
        val raw = throwable?.message.orEmpty()
        return when {
            raw.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Sin permisos para leer $scopeLabel en la nube. Revisa tu sesión o membresía del grupo."
            raw.contains("UNAVAILABLE", ignoreCase = true) ||
                raw.contains("network", ignoreCase = true) ||
                raw.contains("offline", ignoreCase = true) ||
                raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Sin conexión al cargar $scopeLabel. Comprueba tu red e inténtalo de nuevo."
            raw.contains("FAILED_PRECONDITION", ignoreCase = true) ->
                "Falta un índice o precondición en Firestore para cargar $scopeLabel."
            raw.isNotBlank() ->
                "No se pudieron cargar $scopeLabel: $raw"
            else ->
                "No se pudieron cargar $scopeLabel desde la nube."
        }
    }
}
