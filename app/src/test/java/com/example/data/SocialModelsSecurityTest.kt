package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SocialModelsSecurityTest {
    @Test
    fun redactFirestorePath_hidesCompleteInviteToken() {
        val token = "INV-A2B3C4D5-E6F7G8H9-J2K3L4M5-N6P7Q8R9"

        assertThat(redactFirestorePath("group_invites/$token")).isEqualTo("group_invites/[REDACTED]")
        assertThat(redactFirestorePath("groups/grp_123/members/bob")).isEqualTo("groups/grp_123/members/bob")
    }

    @Test
    fun redactFirestoreText_hidesCompleteInviteToken() {
        val token = "INV-A2B3C4D5-E6F7G8H9-J2K3L4M5-N6P7Q8R9"

        assertThat(redactFirestoreText("permission denied for $token"))
            .isEqualTo("permission denied for INV-[REDACTED]")
    }
}
