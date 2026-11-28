package com.example.data

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Date

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
        val group = SocialGroup(id = "grp_ABC", name = "Equipo Adora", generationId = "a".repeat(32))
        val map = group.toV2CreateMap("alice_uid")
        assertThat(map["name"]).isEqualTo("Equipo Adora")
        assertThat(map["createdBy"]).isEqualTo("alice_uid")
        assertThat(map["schemaVersion"]).isEqualTo(2L)
        assertThat(map["generationId"]).isEqualTo("a".repeat(32))
        assertThat(map).doesNotContainKey("inviteCode")
        assertThat(map).doesNotContainKey("memberIds")
        assertThat(map).doesNotContainKey("memberNames")
    }

    @Test
    fun groupMemberCreateMaps_enforceExpectedRolesAndSelfFields() {
        val generationId = "a".repeat(32)
        val ownerMap = GroupMember.toOwnerCreateMap("alice", "grp_1", "Alice", generationId)
        assertThat(ownerMap["role"]).isEqualTo(GroupRole.OWNER.name)
        assertThat(ownerMap["inviteToken"]).isEqualTo("")
        assertThat(ownerMap["groupGenerationId"]).isEqualTo(generationId)

        val token = GroupInviteCrypto.generateHighEntropyInviteToken()
        val joinMap = GroupMember.toInviteJoinCreateMap("bob", "grp_1", "Bob", token, generationId)
        assertThat(joinMap["role"]).isEqualTo(GroupRole.MEMBER.name)
        assertThat(joinMap["inviteToken"]).isEqualTo(token)
        assertThat(joinMap["groupGenerationId"]).isEqualTo(generationId)

        val selfUpdate = GroupMember.toSelfUpdateMap("Bob Nuevo")
        assertThat(selfUpdate.keys).containsExactly("displayName", "updatedAt")
    }

    @Test
    fun v2ModelBuilders_rejectMissingOrMalformedGeneration() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialGroup(name = "Equipo Adora").toV2CreateMap("alice")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupMember.toOwnerCreateMap("alice", "grp_1", "Alice", "bad-generation")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupMember.toInviteJoinCreateMap("bob", "grp_1", "Bob", "invite-token", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupInvite.toCreateMap(
                token = GroupInviteCrypto.generateHighEntropyInviteToken(),
                groupId = "grp_1",
                groupNamePreview = "Equipo",
                creatorUid = "alice",
                expiresAt = Timestamp(Date(System.currentTimeMillis() + 60_000L)),
                maxUses = 1L,
                groupGenerationId = "not-hex"
            )
        }
    }

    @Test
    fun outingUpdateMap_preservesGroupGenerationAndOmitsItForPersonalScope() {
        val generationId = "b".repeat(32)
        val groupMap = Outing(
            ownerId = "alice",
            groupId = "v2_grp_12345678901234567890",
            groupGenerationId = generationId,
            title = "Salida"
        ).toUpdateMap()
        assertThat(groupMap["groupGenerationId"]).isEqualTo(generationId)

        val personalMap = Outing(ownerId = "alice", title = "Personal").toUpdateMap()
        assertThat(personalMap["groupId"]).isEqualTo("personal_alice")
        assertThat(personalMap).doesNotContainKey("groupGenerationId")
    }

    @Test
    fun outingCreateMap_startsGroupRsvpEmptyButKeepsPersonalRsvpState() {
        val groupMap = Outing(
            ownerId = "alice_123",
            groupId = "v2_grp_12345678901234567890",
            groupGenerationId = "a".repeat(32),
            rsvpYesCount = 6L,
            rsvpMaybeCount = 2L,
            rsvpNoCount = 1L,
            myRsvp = RsvpChoice.YES.name,
            rsvpByUser = mapOf("alice_123" to RsvpChoice.YES.name)
        ).toCreateMap("alice_123")

        assertThat(groupMap["rsvpYesCount"]).isEqualTo(0L)
        assertThat(groupMap["rsvpMaybeCount"]).isEqualTo(0L)
        assertThat(groupMap["rsvpNoCount"]).isEqualTo(0L)
        assertThat(groupMap["myRsvp"]).isEqualTo(RsvpChoice.NONE.name)
        assertThat(groupMap["rsvpByUser"]).isEqualTo(emptyMap<String, String>())

        val personalMap = Outing(
            ownerId = "alice_123",
            groupId = "personal_alice_123",
            rsvpYesCount = 1L,
            myRsvp = RsvpChoice.YES.name
        ).toCreateMap("alice_123")
        assertThat(personalMap["rsvpYesCount"]).isEqualTo(1L)
        assertThat(personalMap["myRsvp"]).isEqualTo(RsvpChoice.YES.name)
        assertThat(personalMap["rsvpByUser"]).isEqualTo(mapOf("alice_123" to RsvpChoice.YES.name))
    }

    @Test
    fun outingUpdateMap_doesNotWriteSharedGroupRsvpFields() {
        val groupMap = Outing(
            ownerId = "alice_123",
            groupId = "v2_grp_12345678901234567890",
            groupGenerationId = "b".repeat(32),
            rsvpYesCount = 4L,
            rsvpMaybeCount = 2L,
            rsvpNoCount = 1L,
            myRsvp = RsvpChoice.YES.name,
            rsvpByUser = mapOf("alice_123" to RsvpChoice.YES.name)
        ).toUpdateMap()

        assertThat(groupMap).doesNotContainKey("rsvpYesCount")
        assertThat(groupMap).doesNotContainKey("rsvpMaybeCount")
        assertThat(groupMap).doesNotContainKey("rsvpNoCount")
        assertThat(groupMap).doesNotContainKey("myRsvp")
        assertThat(groupMap).doesNotContainKey("rsvpByUser")

        val personalMap = Outing(
            ownerId = "alice_123",
            groupId = "personal_alice_123",
            rsvpYesCount = 1L,
            rsvpMaybeCount = 2L,
            rsvpNoCount = 0L,
            myRsvp = RsvpChoice.MAYBE.name,
            rsvpByUser = mapOf("alice_123" to RsvpChoice.MAYBE.name)
        ).toUpdateMap()
        assertThat(personalMap["rsvpMaybeCount"]).isEqualTo(2L)
        assertThat(personalMap["myRsvp"]).isEqualTo(RsvpChoice.MAYBE.name)
        assertThat(personalMap["rsvpByUser"]).isEqualTo(mapOf("alice_123" to RsvpChoice.MAYBE.name))
    }

    @Test
    fun outingRsvp_createMapIsCanonicalAndRejectsNoneOrInvalidScope() {
        val response = OutingRsvp(
            outingId = "outing_123",
            userId = "alice_123",
            groupId = "v2_grp_12345678901234567890",
            groupGenerationId = "c".repeat(32),
            choice = RsvpChoice.MAYBE.name
        )
        val payload = response.toCreateMap()

        assertThat(payload.keys).containsExactly(
            "outingId", "userId", "groupId", "groupGenerationId", "choice", "createdAt", "updatedAt"
        )
        assertThat(payload["outingId"]).isEqualTo("outing_123")
        assertThat(payload["userId"]).isEqualTo("alice_123")
        assertThat(payload["choice"]).isEqualTo(RsvpChoice.MAYBE.name)
        assertThat(response.copy(id = "alice_123").belongsToCurrentGroup(
            "v2_grp_12345678901234567890", "c".repeat(32)
        )).isTrue()
        assertThat(response.copy(id = "bob_456").belongsToCurrentGroup(
            "v2_grp_12345678901234567890", "c".repeat(32)
        )).isFalse()
        assertThat(response.copy(id = "alice_123", groupGenerationId = "d".repeat(32))
            .belongsToCurrentGroup("v2_grp_12345678901234567890", "c".repeat(32))).isFalse()

        assertThrows(IllegalArgumentException::class.java) {
            response.copy(choice = RsvpChoice.NONE.name).toCreateMap()
        }
        assertThrows(IllegalArgumentException::class.java) {
            response.copy(groupGenerationId = "invalid").toCreateMap()
        }
    }
}
