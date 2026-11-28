package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupInviteCryptoTest {

    @Test
    fun generateHighEntropyGroupId_isRandomDecoupledAndValidId() {
        val ids = (1..50).map { GroupInviteCrypto.generateHighEntropyGroupId() }.toSet()
        assertThat(ids).hasSize(50)
        ids.forEach { id ->
            assertThat(id).startsWith("v2_grp_")
            assertThat(id.length).isEqualTo(33) // "v2_grp_" + 26 Base32 chars (130 bits)
            assertThat(id).matches("^v2_grp_[A-HJ-NP-Z2-9]{26}$")
            assertThat(GroupInviteCrypto.isHighEntropyInviteToken(id)).isFalse()
        }
    }

    @Test
    fun generateHighEntropyInviteToken_has160BitsEntropyAndCanonicalFormat() {
        val tokens = (1..50).map { GroupInviteCrypto.generateHighEntropyInviteToken() }.toSet()
        assertThat(tokens).hasSize(50)
        tokens.forEach { token ->
            assertThat(token).startsWith("INV-")
            assertThat(token.length).isEqualTo(39) // "INV-" + 32 chars + 3 dashes
            assertThat(GroupInviteCrypto.isHighEntropyInviteToken(token)).isTrue()
            assertThat(GroupInviteCrypto.isLegacyGroupCode(token)).isFalse()
        }
    }

    @Test
    fun normalizeInviteToken_extractsFromSharedMessageAndFormatsDashlessTokens() {
        val generated = GroupInviteCrypto.generateHighEntropyInviteToken()
        val lowerShared = "Únete a nuestro grupo en Adora usando: ${generated.lowercase()} (caduca en 7 días)"
        assertThat(GroupInviteCrypto.normalizeInviteToken(lowerShared)).isEqualTo(generated)

        val dashless = generated.removePrefix("INV-").replace("-", "").lowercase()
        assertThat(GroupInviteCrypto.normalizeInviteToken(dashless)).isEqualTo(generated)
    }

    @Test
    fun legacyCodes_areRejectedAsHighEntropyAndIdentifiedAsLegacy() {
        val legacySamples = listOf("QUEDA-2026", "QUEDA-1234", "GRUPO-9999", "abc123")
        legacySamples.forEach { code ->
            val normalized = GroupInviteCrypto.normalizeInviteToken(code)
            assertThat(GroupInviteCrypto.isHighEntropyInviteToken(normalized)).isFalse()
            assertThat(GroupInviteCrypto.isLegacyGroupCode(code)).isTrue()
        }
    }

    @Test
    fun validateInviteForRedemption_enforcesAllSecurityStates() {
        val token = GroupInviteCrypto.generateHighEntropyInviteToken()
        val now = 1_700_000_000_000L
        val future = now + 3_600_000L
        val past = now - 1_000L

        assertThat(
            GroupInviteCrypto.validateInviteForRedemption(
                token = token, revoked = false, expiresAtMillis = future,
                useCount = 0, maxUses = 1, nowMillis = now, isAlreadyMember = false
            )
        ).isEqualTo(GroupInviteCrypto.InviteValidationResult.Valid)

        assertThat(
            GroupInviteCrypto.validateInviteForRedemption(
                token = token, revoked = false, expiresAtMillis = future,
                useCount = 0, maxUses = 1, nowMillis = now, isAlreadyMember = true
            )
        ).isEqualTo(GroupInviteCrypto.InviteValidationResult.AlreadyMember)

        assertThat(
            GroupInviteCrypto.validateInviteForRedemption(
                token = token, revoked = true, expiresAtMillis = future,
                useCount = 0, maxUses = 5, nowMillis = now, isAlreadyMember = false
            )
        ).isEqualTo(GroupInviteCrypto.InviteValidationResult.Revoked)

        assertThat(
            GroupInviteCrypto.validateInviteForRedemption(
                token = token, revoked = false, expiresAtMillis = past,
                useCount = 0, maxUses = 5, nowMillis = now, isAlreadyMember = false
            )
        ).isEqualTo(GroupInviteCrypto.InviteValidationResult.Expired)

        assertThat(
            GroupInviteCrypto.validateInviteForRedemption(
                token = token, revoked = false, expiresAtMillis = future,
                useCount = 1, maxUses = 1, nowMillis = now, isAlreadyMember = false
            )
        ).isEqualTo(GroupInviteCrypto.InviteValidationResult.Exhausted)

        assertThat(
            GroupInviteCrypto.validateInviteForRedemption(
                token = "QUEDA-2026", revoked = false, expiresAtMillis = future,
                useCount = 0, maxUses = 5, nowMillis = now, isAlreadyMember = false
            )
        ).isEqualTo(GroupInviteCrypto.InviteValidationResult.InvalidFormat)
    }

    @Test
    fun socialGroupV2CreateMap_excludesInviteCodeAndMemberArrays() {
        val group = SocialGroup(id = "grp_ABC", name = "Equipo Adora")
        val map = group.toV2CreateMap("alice_uid")
        assertThat(map["name"]).isEqualTo("Equipo Adora")
        assertThat(map["createdBy"]).isEqualTo("alice_uid")
        assertThat(map["schemaVersion"]).isEqualTo(2L)
        assertThat(map).doesNotContainKey("inviteCode")
        assertThat(map).doesNotContainKey("memberIds")
        assertThat(map).doesNotContainKey("memberNames")
    }

    @Test
    fun groupMemberCreateMaps_enforceExpectedRolesAndSelfFields() {
        val ownerMap = GroupMember.toOwnerCreateMap("alice", "grp_1", "Alice")
        assertThat(ownerMap["role"]).isEqualTo(GroupRole.OWNER.name)
        assertThat(ownerMap["inviteToken"]).isEqualTo("")

        val token = GroupInviteCrypto.generateHighEntropyInviteToken()
        val joinMap = GroupMember.toInviteJoinCreateMap("bob", "grp_1", "Bob", token)
        assertThat(joinMap["role"]).isEqualTo(GroupRole.MEMBER.name)
        assertThat(joinMap["inviteToken"]).isEqualTo(token)

        val selfUpdate = GroupMember.toSelfUpdateMap("Bob Nuevo")
        assertThat(selfUpdate.keys).containsExactly("displayName", "updatedAt")
    }
}
