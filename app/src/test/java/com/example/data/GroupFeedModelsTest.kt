package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class GroupFeedModelsTest {

    @Test
    fun toCreateMap_usesAuthenticatedUidAndSanitizesContent() {
        val post = GroupPost(
            authorId = "spoofed-author",
            authorName = "  Ana  ",
            groupId = "GROUP-1",
            text = "  Mensaje para el grupo  "
        )

        val map = post.toCreateMap("real-auth-uid")

        assertThat(map["authorId"]).isEqualTo("real-auth-uid")
        assertThat(map["authorName"]).isEqualTo("Ana")
        assertThat(map["groupId"]).isEqualTo("GROUP-1")
        assertThat(map["text"]).isEqualTo("Mensaje para el grupo")
        assertThat(map).containsKey("createdAt")
        assertThat(map).containsKey("updatedAt")
    }

    @Test
    fun toCreateMap_rejectsBlankTextAndGroup() {
        assertThrows(IllegalArgumentException::class.java) {
            GroupPost(groupId = "GROUP-1", text = "   ").toCreateMap("uid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupPost(groupId = "", text = "Mensaje").toCreateMap("uid")
        }
    }

    @Test
    fun currentGroupFeedScope_rejectsStaleSessionOrGroup() {
        assertThat(isCurrentGroupFeedScope("uid-1", "uid-1", "group-1", "group-1")).isTrue()
        assertThat(isCurrentGroupFeedScope("uid-2", "uid-1", "group-1", "group-1")).isFalse()
        assertThat(isCurrentGroupFeedScope("uid-1", "uid-1", "group-2", "group-1")).isFalse()
        assertThat(isCurrentGroupFeedScope(null, "uid-1", "group-1", "group-1")).isFalse()
    }

    @Test
    fun feedState_keepsDraftRelevantOperationStateUntilExplicitSuccess() {
        val pending = GroupFeedState(
            status = GroupFeedStatus.EMPTY,
            isPublishing = true,
            actionMessage = null
        )
        val failed = pending.copy(
            isPublishing = false,
            actionMessage = "No se pudo publicar"
        )
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
