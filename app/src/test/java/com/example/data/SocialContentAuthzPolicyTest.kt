package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SocialContentAuthzPolicyTest {

    @Test
    fun canEditOrCancelAndDeleteOuting_strictlyOwnerOnlyForAuthenticatedUsers() {
        val aliceOuting = Outing(id = "o1", ownerId = "alice", groupId = "grp_123")

        assertThat(SocialContentAuthzPolicy.canEditOrCancelOuting(aliceOuting, "alice", isGuestDemo = false)).isTrue()
        assertThat(SocialContentAuthzPolicy.canDeleteOuting(aliceOuting, "alice", isGuestDemo = false)).isTrue()

        assertThat(SocialContentAuthzPolicy.canEditOrCancelOuting(aliceOuting, "bob", isGuestDemo = false)).isFalse()
        assertThat(SocialContentAuthzPolicy.canDeleteOuting(aliceOuting, "bob", isGuestDemo = false)).isFalse()
        assertThat(SocialContentAuthzPolicy.canEditOrCancelOuting(aliceOuting, null, isGuestDemo = false)).isFalse()

        // Guest demo sandbox remains locally editable
        assertThat(SocialContentAuthzPolicy.canEditOrCancelOuting(aliceOuting, null, isGuestDemo = true)).isTrue()
    }

    @Test
    fun canWriteToGroupScope_allowsCurrentMemberAndOwnPersonalScope_blocksNonMembersAndOtherPersonalScopes() {
        assertThat(
            SocialContentAuthzPolicy.canWriteToGroupScope(
                targetGroupId = "personal_alice",
                currentUserId = "alice",
                activeGroupId = null,
                isCurrentGroupMember = false,
                isGuestDemo = false
            )
        ).isTrue()

        assertThat(
            SocialContentAuthzPolicy.canWriteToGroupScope(
                targetGroupId = "personal_bob",
                currentUserId = "alice",
                activeGroupId = "grp_1",
                isCurrentGroupMember = true,
                isGuestDemo = false
            )
        ).isFalse()

        assertThat(
            SocialContentAuthzPolicy.canWriteToGroupScope(
                targetGroupId = "grp_1",
                currentUserId = "alice",
                activeGroupId = "grp_1",
                isCurrentGroupMember = true,
                isGuestDemo = false
            )
        ).isTrue()

        // Ex-member or non-member blocked
        assertThat(
            SocialContentAuthzPolicy.canWriteToGroupScope(
                targetGroupId = "grp_1",
                currentUserId = "carol",
                activeGroupId = "grp_1",
                isCurrentGroupMember = false,
                isGuestDemo = false
            )
        ).isFalse()
    }

    @Test
    fun computeSelfRsvpTransition_modifiesOnlyCallerAndComputesExactDeltas() {
        val existing = mapOf("alice" to "YES", "carol" to "NO")

        // Bob joins as YES -> +1 yes, third parties untouched
        val bobYes = SocialContentAuthzPolicy.computeSelfRsvpTransition(
            existingByUser = existing,
            existingYes = 1,
            existingMaybe = 0,
            existingNo = 1,
            userId = "bob",
            newChoice = RsvpChoice.YES
        )
        assertThat(bobYes.isNoOp).isFalse()
        assertThat(bobYes.yesDelta).isEqualTo(1L)
        assertThat(bobYes.maybeDelta).isEqualTo(0L)
        assertThat(bobYes.noDelta).isEqualTo(0L)
        assertThat(bobYes.nextYesCount).isEqualTo(2L)
        assertThat(bobYes.nextRsvpByUser).containsExactly("alice", "YES", "carol", "NO", "bob", "YES")

        // Bob repeats YES -> no-op, zero delta
        val bobRepeat = SocialContentAuthzPolicy.computeSelfRsvpTransition(
            existingByUser = bobYes.nextRsvpByUser,
            existingYes = 2,
            existingMaybe = 0,
            existingNo = 1,
            userId = "bob",
            newChoice = RsvpChoice.YES
        )
        assertThat(bobRepeat.isNoOp).isTrue()
        assertThat(bobRepeat.yesDelta).isEqualTo(0L)

        // Bob switches YES -> MAYBE -> -1 yes, +1 maybe
        val bobMaybe = SocialContentAuthzPolicy.computeSelfRsvpTransition(
            existingByUser = bobYes.nextRsvpByUser,
            existingYes = 2,
            existingMaybe = 0,
            existingNo = 1,
            userId = "bob",
            newChoice = RsvpChoice.MAYBE
        )
        assertThat(bobMaybe.yesDelta).isEqualTo(-1L)
        assertThat(bobMaybe.maybeDelta).isEqualTo(1L)
        assertThat(bobMaybe.nextYesCount).isEqualTo(1L)
        assertThat(bobMaybe.nextMaybeCount).isEqualTo(1L)
        assertThat(bobMaybe.nextRsvpByUser["alice"]).isEqualTo("YES")
        assertThat(bobMaybe.nextRsvpByUser["carol"]).isEqualTo("NO")

        // Bob clears RSVP -> removes only bob, -1 maybe
        val bobNone = SocialContentAuthzPolicy.computeSelfRsvpTransition(
            existingByUser = bobMaybe.nextRsvpByUser,
            existingYes = 1,
            existingMaybe = 1,
            existingNo = 1,
            userId = "bob",
            newChoice = RsvpChoice.NONE
        )
        assertThat(bobNone.maybeDelta).isEqualTo(-1L)
        assertThat(bobNone.nextRsvpByUser).containsExactly("alice", "YES", "carol", "NO")
    }

    @Test
    fun computeSelfStoryLikeAndChatReaction_preserveThirdParties() {
        val like1 = SocialContentAuthzPolicy.computeSelfStoryLikeTransition(
            existingLikedByUser = mapOf("alice" to true),
            existingLikesCount = 1L,
            userId = "bob"
        )
        assertThat(like1.isLiked).isTrue()
        assertThat(like1.likesDelta).isEqualTo(1L)
        assertThat(like1.nextLikesCount).isEqualTo(2L)
        assertThat(like1.nextLikedByUser).containsExactly("alice", true, "bob", true)

        val unlike = SocialContentAuthzPolicy.computeSelfStoryLikeTransition(
            existingLikedByUser = like1.nextLikedByUser,
            existingLikesCount = 2L,
            userId = "bob"
        )
        assertThat(unlike.isLiked).isFalse()
        assertThat(unlike.likesDelta).isEqualTo(-1L)
        assertThat(unlike.nextLikesCount).isEqualTo(1L)
        assertThat(unlike.nextLikedByUser).containsExactly("alice", true)

        val react1 = SocialContentAuthzPolicy.computeSelfChatReactionTransition(
            existingReactionsByUser = mapOf("alice" to "❤️"),
            userId = "bob",
            emoji = "🔥"
        )
        assertThat(react1.removed).isFalse()
        assertThat(react1.nextReactionsByUser).containsExactly("alice", "❤️", "bob", "🔥")

        val reactToggleOff = SocialContentAuthzPolicy.computeSelfChatReactionTransition(
            existingReactionsByUser = react1.nextReactionsByUser,
            userId = "bob",
            emoji = "🔥"
        )
        assertThat(reactToggleOff.removed).isTrue()
        assertThat(reactToggleOff.nextReactionsByUser).containsExactly("alice", "❤️")
    }

    @Test
    fun outingSerialization_preventsForgedInitialCountsAndOmitsImmutableAndRsvpFieldsOnUpdate() {
        val tampered = Outing(
            id = "o_1",
            ownerId = "alice",
            groupId = "grp_1",
            title = "Cena",
            myRsvp = RsvpChoice.YES.name,
            rsvpYesCount = 99L,
            rsvpMaybeCount = 50L,
            rsvpByUser = mapOf("bob" to "YES")
        )
        val createMap = tampered.toCreateMap("alice")
        assertThat(createMap["rsvpYesCount"]).isEqualTo(1L)
        assertThat(createMap["rsvpMaybeCount"]).isEqualTo(0L)
        assertThat(createMap["rsvpNoCount"]).isEqualTo(0L)
        assertThat(createMap["rsvpByUser"]).isEqualTo(mapOf("alice" to "YES"))

        val updateMap = tampered.toUpdateMap()
        assertThat(updateMap).doesNotContainKey("ownerId")
        assertThat(updateMap).doesNotContainKey("groupId")
        assertThat(updateMap).doesNotContainKey("createdAt")
        assertThat(updateMap).doesNotContainKey("rsvpByUser")
        assertThat(updateMap).doesNotContainKey("rsvpYesCount")
        assertThat(updateMap).doesNotContainKey("rsvpMaybeCount")
        assertThat(updateMap).doesNotContainKey("rsvpNoCount")
        assertThat(updateMap).doesNotContainKey("myRsvp")
    }

    @Test
    fun effectiveOutingRsvpForUser_doesNotLeakCreatorsMyRsvpToOtherRealMembers() {
        val outing = Outing(
            id = "o1",
            ownerId = "alice",
            myRsvp = RsvpChoice.YES.name,
            rsvpByUser = mapOf("alice" to RsvpChoice.YES.name)
        )
        assertThat(SocialContentAuthzPolicy.effectiveOutingRsvpForUser(outing, "alice", isGuestDemo = false))
            .isEqualTo(RsvpChoice.YES)
        assertThat(SocialContentAuthzPolicy.effectiveOutingRsvpForUser(outing, "bob", isGuestDemo = false))
            .isEqualTo(RsvpChoice.NONE)
    }

    @Test
    fun chatMessageAndStoryCreateMaps_startWithHonestZeroCountsAndPreserveLegacyEffectiveReactions() {
        val legacyMsg = GroupChatMessage(reactions = listOf("👍", "🔥"), reactionsByUser = emptyMap())
        assertThat(legacyMsg.effectiveReactions()).containsExactly("👍", "🔥")

        val v2Msg = GroupChatMessage(
            reactions = listOf("👍"),
            reactionsByUser = mapOf("alice" to "❤️", "bob" to "🔥")
        )
        assertThat(v2Msg.effectiveReactions()).containsExactly("❤️", "🔥")

        val msgCreate = GroupChatMessage(text = "Hola", reactions = listOf("💀")).toCreateMap("alice")
        assertThat(msgCreate["reactions"]).isEqualTo(emptyList<String>())
        assertThat(msgCreate["reactionsByUser"]).isEqualTo(emptyMap<String, String>())

        val storyCreate = OutingStory(caption = "Foto", likesCount = 99L).toCreateMap("alice")
        assertThat(storyCreate["likesCount"]).isEqualTo(0L)
        assertThat(storyCreate["likedByUser"]).isEqualTo(emptyMap<String, Boolean>())
    }
}
