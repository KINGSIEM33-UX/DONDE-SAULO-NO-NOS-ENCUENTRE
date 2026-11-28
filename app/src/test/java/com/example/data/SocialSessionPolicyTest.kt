package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SocialSessionPolicyTest {

    @Test
    fun shouldAutoSeedCloudOutings_isAlwaysFalse() {
        assertThat(SocialSessionPolicy.shouldAutoSeedCloudOutings()).isFalse()
    }

    @Test
    fun buildCleanRealUserProfile_containsNoGuestDemoDataOrHardcodedGroup() {
        val profile = SocialSessionPolicy.buildCleanRealUserProfile(
            uid = "real_uid_123",
            displayName = "María López",
            email = "maria@example.com",
            photoUrl = "https://example.com/maria.jpg"
        )

        assertThat(profile.userId).isEqualTo("real_uid_123")
        assertThat(profile.username).isEqualTo("María López")
        assertThat(profile.bio).isEmpty()
        assertThat(profile.avatarUrl).isEqualTo("https://example.com/maria.jpg")
        assertThat(profile.curiosities).isEmpty()
        assertThat(profile.groupId).isEmpty()
        assertThat(profile.groupInviteCode).isEmpty()
        assertThat(profile.groupName).isEmpty()
        assertThat(profile.gamesHighScore).isEqualTo(0L)
        assertThat(SocialSessionPolicy.resolveTargetGroupId(profile)).isEmpty()
    }

    @Test
    fun buildCleanRealUserProfile_fallsBackToEmailPrefixWhenDisplayNameBlank() {
        val profile = SocialSessionPolicy.buildCleanRealUserProfile(
            uid = "real_uid_456",
            displayName = "   ",
            email = "carlos.dev@example.com",
            photoUrl = null
        )
        assertThat(profile.username).isEqualTo("carlos.dev")
        assertThat(profile.avatarUrl).isEmpty()
    }

    @Test
    fun resolveProfileOnCloudEmit_preservesExistingRealRemoteProfileIntact() {
        val remote = UserProfile(
            id = "uid_real",
            userId = "uid_real",
            username = "SauloReal",
            bio = "Mi bio real",
            curiosities = listOf("Lectura"),
            groupId = "GRP-9001",
            groupInviteCode = "GRP-9001",
            groupName = "Grupo Real",
            gamesHighScore = 500L
        )
        val (resolved, existsInCloud) = SocialSessionPolicy.resolveProfileOnCloudEmit(
            remoteProfile = remote,
            uid = "uid_real",
            displayName = "Ignorado",
            email = "otro@example.com"
        )
        assertThat(existsInCloud).isTrue()
        assertThat(resolved).isEqualTo(remote)
        assertThat(SocialSessionPolicy.resolveTargetGroupId(resolved)).isEqualTo("GRP-9001")
    }

    @Test
    fun resolveProfileOnCloudEmit_whenNull_returnsHonestEmptyProfileWithoutDemoData() {
        val (resolved, existsInCloud) = SocialSessionPolicy.resolveProfileOnCloudEmit(
            remoteProfile = null,
            uid = "new_user_uid",
            displayName = "Nuevo Usuario",
            email = "nuevo@example.com"
        )
        assertThat(existsInCloud).isFalse()
        assertThat(resolved.userId).isEqualTo("new_user_uid")
        assertThat(resolved.groupId).isEmpty()
        assertThat(resolved.curiosities).isEmpty()
        assertThat(resolved.gamesHighScore).isEqualTo(0L)
    }

    @Test
    fun userProfileDefaultsAndSerialization_doNotInjectQueda2026OrDemoData() {
        val blankProfile = UserProfile(userId = "u1", username = "Ana")
        assertThat(blankProfile.bio).isEmpty()
        assertThat(blankProfile.curiosities).isEmpty()
        assertThat(blankProfile.groupId).isEmpty()
        assertThat(blankProfile.groupName).isEmpty()
        assertThat(blankProfile.gamesHighScore).isEqualTo(0L)

        val createMap = blankProfile.toCreateMap("u1")
        assertThat(createMap["groupId"]).isEqualTo("")
        assertThat(createMap["groupInviteCode"]).isEqualTo("")
        assertThat(createMap["groupName"]).isEqualTo("")
        assertThat(createMap["gamesHighScore"]).isEqualTo(0L)

        val updateMap = blankProfile.toUpdateMap()
        assertThat(updateMap["groupId"]).isEqualTo("")
        assertThat(updateMap["groupInviteCode"]).isEqualTo("")
        assertThat(updateMap["groupName"]).isEqualTo("")
    }

    @Test
    fun resolveGroupIdForWrite_usesRealGroupOrPersonalNonDemoScope() {
        assertThat(SocialSessionPolicy.resolveGroupIdForWrite("GRP-1234", "uid_abc"))
            .isEqualTo("GRP-1234")

        val personalScope = SocialSessionPolicy.resolveGroupIdForWrite("", "uid_abc123")
        assertThat(personalScope).isEqualTo("personal_uid_abc123")
        assertThat(personalScope).isNotEqualTo(SocialSessionPolicy.GUEST_DEMO_GROUP_ID)
    }

    @Test
    fun personalChatAndStoryPayloadsOmitGroupIdButSharedPayloadsKeepIt() {
        val personalChat = GroupChatMessage(groupId = "").toCreateMap("uid_abc123")
        val groupChat = GroupChatMessage(groupId = "v2_grp_SOCIAL7P4R6T8V3W5X7Y9Z2A4B6")
            .toCreateMap("uid_abc123")
        val personalStory = OutingStory(groupId = "").toCreateMap("uid_abc123")
        val groupStory = OutingStory(groupId = "v2_grp_SOCIAL7P4R6T8V3W5X7Y9Z2A4B6")
            .toCreateMap("uid_abc123")

        assertThat(personalChat).doesNotContainKey("groupId")
        assertThat(personalStory).doesNotContainKey("groupId")
        assertThat(groupChat["groupId"]).isEqualTo("v2_grp_SOCIAL7P4R6T8V3W5X7Y9Z2A4B6")
        assertThat(groupStory["groupId"]).isEqualTo("v2_grp_SOCIAL7P4R6T8V3W5X7Y9Z2A4B6")
    }

    @Test
    fun filterOutingsAllowedForCloudWrite_blocksGuestDemoLoadingErrorAndDemoIds() {
        val demoOuting = Outing(
            id = "outing_upcoming_confirmed",
            ownerId = "local_user",
            title = "Cena & Noche de Bolera"
        )
        val realOuting = Outing(
            id = "outing_real_99",
            ownerId = "uid_real",
            groupId = "GRP-55",
            title = "Reunión del sábado"
        )

        // Never allowed in Guest Demo mode
        assertThat(
            SocialSessionPolicy.filterOutingsAllowedForCloudWrite(
                origin = DataOrigin.GUEST_DEMO,
                outingsState = CloudLoadState.GuestDemo,
                outings = listOf(demoOuting, realOuting)
            )
        ).isEmpty()

        // Never allowed while still Loading
        assertThat(
            SocialSessionPolicy.filterOutingsAllowedForCloudWrite(
                origin = DataOrigin.CLOUD_REAL,
                outingsState = CloudLoadState.Loading,
                outings = listOf(realOuting)
            )
        ).isEmpty()

        // Never allowed in Error state
        assertThat(
            SocialSessionPolicy.filterOutingsAllowedForCloudWrite(
                origin = DataOrigin.CLOUD_REAL,
                outingsState = CloudLoadState.Error("Sin conexión"),
                outings = listOf(realOuting)
            )
        ).isEmpty()

        // Even when CLOUD_REAL + Loaded, guest demo IDs are stripped as defense-in-depth
        val allowed = SocialSessionPolicy.filterOutingsAllowedForCloudWrite(
            origin = DataOrigin.CLOUD_REAL,
            outingsState = CloudLoadState.Loaded,
            outings = listOf(demoOuting, realOuting)
        )
        assertThat(allowed).containsExactly(realOuting)
    }

    @Test
    fun shouldShowEmptyState_neverConfusesLoadingOrErrorWithEmpty() {
        assertThat(SocialSessionPolicy.shouldShowEmptyState(CloudLoadState.Loading, 0)).isFalse()
        assertThat(SocialSessionPolicy.shouldShowEmptyState(CloudLoadState.Error("Error de red"), 0)).isFalse()
        assertThat(SocialSessionPolicy.shouldShowEmptyState(CloudLoadState.Loaded, 0)).isTrue()
        assertThat(SocialSessionPolicy.shouldShowEmptyState(CloudLoadState.Loaded, 2)).isFalse()
        assertThat(SocialSessionPolicy.shouldShowEmptyState(CloudLoadState.GuestDemo, 0)).isTrue()
    }

    @Test
    fun resolveErrorState_classifiesPermissionAndNetworkErrorsAndTracksCachedRealData() {
        val permErr = SocialSessionPolicy.resolveErrorState(
            scopeLabel = "las salidas del grupo",
            throwable = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions"),
            currentOrigin = DataOrigin.CLOUD_REAL,
            currentItemCount = 0
        )
        assertThat(permErr.message).contains("Sin permisos")
        assertThat(permErr.hasCachedRealData).isFalse()

        val netErrWithCache = SocialSessionPolicy.resolveErrorState(
            scopeLabel = "tus salidas",
            throwable = RuntimeException("UNAVAILABLE: Unable to resolve host"),
            currentOrigin = DataOrigin.CLOUD_REAL,
            currentItemCount = 3
        )
        assertThat(netErrWithCache.message).contains("Sin conexión")
        assertThat(netErrWithCache.hasCachedRealData).isTrue()
    }

    @Test
    fun groupRsvpSnapshot_isRetainedOnlyForTransientFailureWithinAuthorizedScope() {
        assertThat(
            SocialSessionPolicy.shouldRetainCachedGroupRsvpSnapshot(
                scopeIsStillAuthorized = true,
                failureIsTransient = true
            )
        ).isTrue()
        assertThat(
            SocialSessionPolicy.shouldRetainCachedGroupRsvpSnapshot(
                scopeIsStillAuthorized = false,
                failureIsTransient = true
            )
        ).isFalse()
        assertThat(
            SocialSessionPolicy.shouldRetainCachedGroupRsvpSnapshot(
                scopeIsStillAuthorized = true,
                failureIsTransient = false
            )
        ).isFalse()
    }

    @Test
    fun syncSnapshots_clearlyDistinguishGuestDemoAndRealCloudLoading() {
        val guest = SocialSyncSnapshot.forGuestDemo()
        assertThat(guest.isGuestDemo).isTrue()
        assertThat(guest.isAnyLoading).isFalse()
        assertThat(guest.firstErrorMessage).isNull()

        val realLoading = SocialSyncSnapshot.forRealUserLoading()
        assertThat(realLoading.isGuestDemo).isFalse()
        assertThat(realLoading.isAnyLoading).isTrue()
        assertThat(realLoading.origin).isEqualTo(DataOrigin.CLOUD_REAL)
    }
}
