package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class GroupFeedModelsTest {

    @Test
    fun belongsToGeneration_rejectsHistoricalGroupIncarnations() {
        val currentGeneration = "a".repeat(32)
        val currentPost = GroupPost(groupId = "group-1", groupGenerationId = currentGeneration)
        val oldPost = currentPost.copy(groupGenerationId = "b".repeat(32))

        assertThat(currentPost.belongsToGeneration("group-1", currentGeneration)).isTrue()
        assertThat(oldPost.belongsToGeneration("group-1", currentGeneration)).isFalse()
        assertThat(currentPost.belongsToGeneration("other-group", currentGeneration)).isFalse()
        assertThat(currentPost.belongsToGeneration("group-1", "")).isFalse()
    }

    @Test
    fun membershipMustMatchCurrentUserGroupAndGeneration() {
        val currentGeneration = "a".repeat(32)
        val group = SocialGroup(id = "group-1", generationId = currentGeneration)
        val currentMember = GroupMember(
            userId = "alice",
            groupId = "group-1",
            groupGenerationId = currentGeneration
        )

        assertThat(currentMember.belongsToActiveGroup("alice", group)).isTrue()
        assertThat(currentMember.copy(groupGenerationId = "b".repeat(32))
            .belongsToActiveGroup("alice", group)).isFalse()
        assertThat(currentMember.belongsToActiveGroup("bob", group)).isFalse()
        assertThat(currentMember.copy(groupId = "other-group")
            .belongsToActiveGroup("alice", group)).isFalse()
        assertThat(currentMember.belongsToActiveGroup("alice", group.copy(generationId = ""))).isFalse()
    }

    @Test
    fun toCreateMap_usesAuthenticatedUidAndSanitizesContent() {
        val post = GroupPost(
            authorId = "spoofed-author",
            authorName = "  Ana  ",
            groupId = "GROUP-1",
            groupGenerationId = "a".repeat(32),
            text = "  Mensaje para el grupo  "
        )

        val map = post.toCreateMap("real-auth-uid")

        assertThat(map["authorId"]).isEqualTo("real-auth-uid")
        assertThat(map["authorName"]).isEqualTo("Ana")
        assertThat(map["groupId"]).isEqualTo("GROUP-1")
        assertThat(map["groupGenerationId"]).isEqualTo("a".repeat(32))
        assertThat(map["text"]).isEqualTo("Mensaje para el grupo")
        assertThat(map).containsKey("createdAt")
        assertThat(map).containsKey("updatedAt")
    }

    @Test
    fun toCreateMap_rejectsMissingUidGroupAndText() {
        assertThrows(IllegalArgumentException::class.java) {
            GroupPost(groupId = "GROUP-1", groupGenerationId = "a".repeat(32), text = "Mensaje").toCreateMap("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupPost(groupId = "", groupGenerationId = "a".repeat(32), text = "Mensaje").toCreateMap("uid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupPost(groupId = "GROUP-1", groupGenerationId = "a".repeat(32), text = "   ").toCreateMap("uid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupPost(groupId = "GROUP-1", text = "Mensaje").toCreateMap("uid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupPost(groupId = "GROUP-1", groupGenerationId = "z".repeat(32), text = "Mensaje")
                .toCreateMap("uid")
        }
    }

    @Test
    fun toCreateMap_capsUserSuppliedLengths() {
        val map = GroupPost(
            authorName = "A".repeat(100),
            groupId = "GROUP-1",
            groupGenerationId = "a".repeat(32),
            text = "x".repeat(2200)
        ).toCreateMap("uid")

        assertThat(map["authorName"]).isEqualTo("A".repeat(80))
        assertThat(map["text"]).isEqualTo("x".repeat(2000))
    }

    @Test
    fun feedState_tracksPublishingFailureAndSuccess() {
        val pending = GroupFeedState(status = GroupFeedStatus.EMPTY, isPublishing = true)
        val failed = pending.copy(isPublishing = false, actionMessage = "No se pudo publicar")
        val confirmed = failed.copy(
            actionMessage = null,
            publishSuccessToken = 1L,
            publishSuccessGroupId = "group-1"
        )

        assertThat(pending.isPublishing).isTrue()
        assertThat(failed.actionMessage).isEqualTo("No se pudo publicar")
        assertThat(confirmed.publishSuccessGroupId).isEqualTo("group-1")
        assertThat(confirmed.publishSuccessToken).isEqualTo(1L)
    }
}
