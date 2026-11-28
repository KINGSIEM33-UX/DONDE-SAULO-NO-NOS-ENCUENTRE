package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertThrows
import org.junit.Test

class SocialRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun groupQueriesRequireAValidGenerationId() {
        val repository = SocialRepository(firestore)
        val validGeneration = "a".repeat(32)

        assertThat(repository.requireCurrentGroupGenerationId("group-1", validGeneration))
            .isEqualTo(validGeneration)
        assertThrows(IllegalStateException::class.java) {
            repository.requireCurrentGroupGenerationId("group-1", "")
        }
        assertThrows(IllegalStateException::class.java) {
            repository.requireCurrentGroupGenerationId("group-1", "z".repeat(32))
        }
        assertThrows(IllegalStateException::class.java) {
            repository.requireCurrentGroupGenerationId("", validGeneration)
        }
    }

    @Test
    fun createOuting_validPayload_createsDocumentAndReturnsId() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SocialRepository(firestore)

        val outing = Outing(
            title = "Martes de Tapas y Cine",
            location = "Plaza Mayor",
            meetingPoint = "Fuente Central",
            dateIso = "2026-11-10",
            time = "20:00",
            status = OutingStatus.CONFIRMED.name,
            budgetPerPerson = "18 €",
            kitItems = "Entrada y paraguas"
        )

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createOuting(outing, uid)
        }
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isNotEmpty()
    }

    @Test
    fun observeOutings_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SocialRepository(firestore)

        val outingId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createOuting(
                Outing(
                    title = "Escapada a la Sierra",
                    location = "Puerto de Navacerrada",
                    dateIso = "2026-11-15",
                    time = "09:30",
                    status = OutingStatus.PLANNED.name
                ),
                uid
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeOutings(uid).first { list -> list.any { it.id == outingId } }
        }
        assertThat(emitted.map { it.id }).contains(outingId)
    }

    @Test
    fun getOutingById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SocialRepository(firestore)
        val outingId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createOuting(
                Outing(
                    title = "Salida Privada de Alice",
                    location = "Parque del Retiro",
                    dateIso = "2026-11-20",
                    time = "18:00"
                ),
                aliceUid
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SocialRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getOutingById(outingId)
        }
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()?.unwrapFirestoreException()
        assertThat(exception?.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun groupRsvpObserver_readsEmptyCurrentMemberSlotsAndOtherMembersResponse() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = SocialRepository(firestore)
        val group = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createGroupAtomic("Grupo RSVP", aliceUid, "Alice").getOrThrow()
        }
        val invite = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createGroupInvite(group.id, group.name, aliceUid).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val joinOutcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.redeemInviteAndJoinGroupAtomic(invite.token, bobUid, "Bob")
        }
        assertThat(joinOutcome is InviteRedemptionOutcome.Joined).isTrue()

        signInTestUser(ALICE_EMAIL)
        val outingId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createOuting(
                Outing(
                    groupId = group.id,
                    title = "Prueba de respuesta individual",
                    location = "Madrid",
                    dateIso = "2026-12-05",
                    time = "19:00"
                ),
                aliceUid
            ).getOrThrow()
        }
        val responses = repository.observeGroupOutingRsvps(
            groupId = group.id,
            groupGenerationId = group.generationId,
            memberIds = listOf(aliceUid, bobUid)
        )

        val unanswered = withTimeout(DEFAULT_TIMEOUT_MS) { responses.first() }
        assertThat(unanswered).isEmpty()

        val bobWrite = withTimeout(DEFAULT_TIMEOUT_MS) {
            signInTestUser(BOB_EMAIL)
            repository.updateGroupOutingRsvpTransactional(
                outingId = outingId,
                groupId = group.id,
                groupGenerationId = group.generationId,
                choice = RsvpChoice.YES,
                userId = bobUid
            )
        }
        assertThat(bobWrite.isSuccess).isTrue()

        signInTestUser(ALICE_EMAIL)
        val observedBob = withTimeout(DEFAULT_TIMEOUT_MS) {
            responses.first { list -> list.any { it.userId == bobUid } }
        }
        assertThat(observedBob.single().rsvpChoice).isEqualTo(RsvpChoice.YES)
    }

    @Test
    fun updateOutingRsvpTransactional_rejectsAnotherUsersUid() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            SocialRepository(firestore).updateOutingRsvpTransactional(
                outingId = "unused_outing",
                userId = "${aliceUid}_forged",
                choice = RsvpChoice.YES
            )
        }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("usuario autenticado")
    }

    @Test
    fun observeOutings_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = SocialRepository(firestore)

        val thrown = assertThrows(Exception::class.java) {
            runBlocking {
                withTimeout(FLOW_TIMEOUT_MS) {
                    repository.observeOutings("unauth_user_123").first()
                }
            }
        }
        val firestoreException = checkNotNull(thrown.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $thrown"
        }
        assertThat(firestoreException.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@quedadas.test"
        const val BOB_EMAIL = "bob@quedadas.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
