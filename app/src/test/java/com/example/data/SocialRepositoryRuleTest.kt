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
