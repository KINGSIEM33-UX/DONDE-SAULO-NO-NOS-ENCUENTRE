package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SocialRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return checkNotNull(FirebaseAuth.getInstance().currentUser?.uid) {
            "User must be authenticated before performing Firestore operations"
        }
    }

    // --- 1. User Profile ---
    fun observeUserProfile(userId: String = requireUserId()): Flow<UserProfile?> {
        return db.collection(USERS_COLLECTION)
            .document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
    }

    suspend fun getUserProfile(userId: String = requireUserId()): Result<UserProfile?> = runCatching {
        val docRef = db.collection(USERS_COLLECTION).document(userId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun saveUserProfile(profile: UserProfile, userId: String = requireUserId()): Result<Unit> = runCatching {
        val docRef = db.collection(USERS_COLLECTION).document(userId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(profile.toUpdateMap()).await()
            } else {
                docRef.set(profile.toCreateMap(userId)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- 2. Outings (Salidas y Quedadas) ---
    fun observeOutings(userId: String = requireUserId()): Flow<List<Outing>> {
        return db.collection(OUTINGS_COLLECTION)
            .whereEqualTo("ownerId", userId)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    Outing::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.dateIso + " " + it.time }
            }
    }

    suspend fun getUserOutings(userId: String = requireUserId()): Result<List<Outing>> = runCatching {
        try {
            val snapshot = db.collection(OUTINGS_COLLECTION)
                .whereEqualTo("ownerId", userId)
                .get()
                .await()
            snapshot.toObjects(
                Outing::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, OUTINGS_COLLECTION)
            throw e
        }
    }

    suspend fun getOutingById(outingId: String): Result<Outing?> = runCatching {
        val docRef = db.collection(OUTINGS_COLLECTION).document(outingId)
        try {
            val snapshot = docRef.get().await()
            snapshot.toObject(Outing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createOuting(outing: Outing, userId: String = requireUserId()): Result<String> = runCatching {
        val docId = outing.id.ifBlank { "outing_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection(OUTINGS_COLLECTION).document(docId)
        try {
            docRef.set(outing.toCreateMap(userId)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateOuting(outing: Outing): Result<Unit> = runCatching {
        require(outing.id.isNotBlank()) { "Outing ID must not be blank for update" }
        val docRef = db.collection(OUTINGS_COLLECTION).document(outing.id)
        try {
            docRef.update(outing.toUpdateMap()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteOuting(outingId: String): Result<Unit> = runCatching {
        val docRef = db.collection(OUTINGS_COLLECTION).document(outingId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 3. Group Chat Messages ---
    fun observeChatMessages(userId: String = requireUserId()): Flow<List<GroupChatMessage>> {
        return db.collection(CHAT_COLLECTION)
            .whereEqualTo("senderId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    GroupChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun sendChatMessage(message: GroupChatMessage, userId: String = requireUserId()): Result<String> = runCatching {
        val docId = message.id.ifBlank { "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection(CHAT_COLLECTION).document(docId)
        try {
            docRef.set(message.toCreateMap(userId)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateMessageReactions(messageId: String, reactions: List<String>): Result<Unit> = runCatching {
        val docRef = db.collection(CHAT_COLLECTION).document(messageId)
        try {
            docRef.update(
                mapOf(
                    "reactions" to reactions.take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 4. Instantaneas (Stories) ---
    fun observeStories(userId: String = requireUserId()): Flow<List<OutingStory>> {
        return db.collection(STORIES_COLLECTION)
            .whereEqualTo("authorId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    OutingStory::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun createStory(story: OutingStory, userId: String = requireUserId()): Result<String> = runCatching {
        val docId = story.id.ifBlank { "story_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection(STORIES_COLLECTION).document(docId)
        try {
            docRef.set(story.toCreateMap(userId)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun likeStory(storyId: String, newLikesCount: Long): Result<Unit> = runCatching {
        val docRef = db.collection(STORIES_COLLECTION).document(storyId)
        try {
            docRef.update(
                mapOf(
                    "likesCount" to newLikesCount.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val OUTINGS_COLLECTION = "outings"
        const val CHAT_COLLECTION = "chat_messages"
        const val STORIES_COLLECTION = "stories"
    }
}
