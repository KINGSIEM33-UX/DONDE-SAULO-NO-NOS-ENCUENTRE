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

    // --- 0. Social Groups (Multi-user Collaboration) ---
    fun observeGroup(groupId: String): Flow<SocialGroup?> {
        return db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(SocialGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
    }

    suspend fun createOrEnsureGroup(
        groupId: String,
        groupName: String,
        creatorUid: String = requireUserId(),
        creatorName: String
    ): Result<SocialGroup> = runCatching {
        val cleanId = groupId.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "").take(32)
            .ifBlank { "QUEDA-${(1000..9999).random()}" }
        val docRef = db.collection(GROUPS_COLLECTION).document(cleanId)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val current = existing.toObject(SocialGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (current != null && !current.memberIds.contains(creatorUid)) {
                    val newIds = (current.memberIds + creatorUid).distinct().take(50)
                    val newNames = current.memberNames + (creatorUid to creatorName.take(60).ifBlank { "Usuario" })
                    docRef.update(
                        mapOf(
                            "memberIds" to newIds,
                            "memberNames" to newNames,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    current.copy(memberIds = newIds, memberNames = newNames)
                } else {
                    current ?: SocialGroup(id = cleanId, name = groupName, inviteCode = cleanId, createdBy = creatorUid)
                }
            } else {
                val group = SocialGroup(
                    id = cleanId,
                    name = groupName.take(80).ifBlank { "Grupo $cleanId" },
                    inviteCode = cleanId,
                    createdBy = creatorUid,
                    memberIds = listOf(creatorUid),
                    memberNames = mapOf(creatorUid to creatorName.take(60).ifBlank { "Usuario" })
                )
                docRef.set(group.toCreateMap(creatorUid, creatorName)).await()
                group
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun joinGroupByCode(
        inviteCode: String,
        userId: String = requireUserId(),
        displayName: String,
        fallbackName: String = ""
    ): Result<SocialGroup> = runCatching {
        val cleanCode = inviteCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "").take(32)
        require(cleanCode.isNotBlank()) { "El código de invitación no puede estar vacío" }
        val docRef = db.collection(GROUPS_COLLECTION).document(cleanCode)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                // Create the group on-the-fly if user entered a brand-new shared code
                val name = fallbackName.ifBlank { "Grupo $cleanCode" }
                val group = SocialGroup(
                    id = cleanCode,
                    name = name,
                    inviteCode = cleanCode,
                    createdBy = userId,
                    memberIds = listOf(userId),
                    memberNames = mapOf(userId to displayName.take(60).ifBlank { "Usuario" })
                )
                docRef.set(group.toCreateMap(userId, displayName)).await()
                group
            } else {
                val existing = snap.toObject(SocialGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Grupo inválido")
                val updatedIds = (existing.memberIds + userId).distinct().take(50)
                val updatedNames = existing.memberNames + (userId to displayName.take(60).ifBlank { "Usuario" })
                docRef.update(
                    mapOf(
                        "memberIds" to updatedIds,
                        "memberNames" to updatedNames,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                existing.copy(memberIds = updatedIds, memberNames = updatedNames)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
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
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    tx.update(docRef, profile.toUpdateMap())
                } else {
                    tx.set(docRef, profile.toCreateMap(userId))
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserAvatar(userId: String = requireUserId(), avatarUrl: String): Result<Unit> = runCatching {
        val docRef = db.collection(USERS_COLLECTION).document(userId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    tx.update(docRef, mapOf("avatarUrl" to avatarUrl.take(500), "updatedAt" to FieldValue.serverTimestamp()))
                } else {
                    throw IllegalStateException("No existe el perfil de usuario; no se puede actualizar solo el avatar")
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserProfileInfo(userId: String, profile: UserProfile): Result<Unit> = runCatching {
        val docRef = db.collection(USERS_COLLECTION).document(userId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) {
                    throw IllegalStateException("No existe el perfil de usuario; no se puede actualizar su información")
                }
                tx.update(docRef, mapOf(
                    "username" to profile.username.take(60).ifBlank { "Usuario" },
                    "bio" to profile.bio.take(300),
                    "avatarUrl" to profile.avatarUrl.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
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

    fun observeGroupOutings(groupId: String): Flow<List<Outing>> {
        return db.collection(OUTINGS_COLLECTION)
            .whereEqualTo("groupId", groupId)
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

    suspend fun upsertOuting(outing: Outing, userId: String = requireUserId()): Result<String> = runCatching {
        val docId = outing.id.ifBlank { "outing_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection(OUTINGS_COLLECTION).document(docId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    // General outing edits must not overwrite RSVP data changed
                    // concurrently by another member; RSVP has its own transaction.
                    tx.update(docRef, outing.toUpdateMap(includeRsvp = false))
                } else {
                    tx.set(docRef, outing.copy(id = docId).toCreateMap(userId))
                }
            }.await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
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

    suspend fun addOutingAlbumPhotoTransactional(
        outingId: String,
        photoUrl: String,
        userId: String = requireUserId()
    ): Result<Unit> = runCatching {
        require(outingId.isNotBlank()) { "Outing ID must not be blank" }
        require(photoUrl.isNotBlank()) { "Photo URL must not be blank" }
        val docRef = db.collection(OUTINGS_COLLECTION).document(outingId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) {
                    throw IllegalStateException("No existe la salida $outingId")
                }
                val current = snap.toObject(Outing::class.java)
                    ?: throw IllegalStateException("La salida $outingId no tiene un formato válido")
                val photos = (listOf(photoUrl) + current.albumPhotos).distinct().take(20)
                tx.update(docRef, mapOf("albumPhotos" to photos.map { it.take(500) }, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateOutingRsvpTransactional(
        outingId: String,
        userId: String = requireUserId(),
        choice: RsvpChoice
    ): Result<Unit> = runCatching {
        val docRef = db.collection(OUTINGS_COLLECTION).document(outingId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) return@runTransaction
                val current = snap.toObject(Outing::class.java) ?: return@runTransaction
                val updatedMap = current.rsvpByUser.toMutableMap()
                if (choice == RsvpChoice.NONE) {
                    updatedMap.remove(userId)
                } else {
                    updatedMap[userId] = choice.name
                }
                val yesCount = updatedMap.values.count { it == RsvpChoice.YES.name }.toLong()
                val maybeCount = updatedMap.values.count { it == RsvpChoice.MAYBE.name }.toLong()
                val noCount = updatedMap.values.count { it == RsvpChoice.NO.name }.toLong()
                tx.update(
                    docRef,
                    mapOf(
                        "rsvpByUser" to updatedMap,
                        "rsvpYesCount" to yesCount,
                        "rsvpMaybeCount" to maybeCount,
                        "rsvpNoCount" to noCount,
                        "myRsvp" to choice.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
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

    fun observeGroupChatMessages(groupId: String): Flow<List<GroupChatMessage>> {
        return db.collection(CHAT_COLLECTION)
            .whereEqualTo("groupId", groupId)
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

    fun observeGroupStories(groupId: String): Flow<List<OutingStory>> {
        return db.collection(STORIES_COLLECTION)
            .whereEqualTo("groupId", groupId)
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

    // --- 5. Private group feed ---
    fun observeGroupPosts(groupId: String): Flow<List<GroupPost>> {
        require(groupId.isNotBlank()) { "El grupo activo es obligatorio" }
        return db.collection(GROUP_POSTS_COLLECTION)
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    GroupPost::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<GroupPost> { it.createdAt?.seconds ?: 0L }
                        .thenByDescending { it.createdAt?.nanoseconds ?: 0 }
                )
            }
    }

    suspend fun createGroupPost(post: GroupPost, userId: String = requireUserId()): Result<String> = runCatching {
        val docId = post.id.ifBlank { "post_${UUID.randomUUID().toString().replace("-", "").take(20)}" }
        val docRef = db.collection(GROUP_POSTS_COLLECTION).document(docId)
        try {
            docRef.set(post.toCreateMap(userId)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteGroupPost(postId: String, userId: String = requireUserId()): Result<Unit> = runCatching {
        require(postId.isNotBlank()) { "El ID de la publicación es obligatorio" }
        val docRef = db.collection(GROUP_POSTS_COLLECTION).document(postId)
        try {
            val snapshot = docRef.get().await()
            require(snapshot.exists()) { "La publicación ya no existe" }
            require(snapshot.getString("authorId") == userId) { "Solo el autor puede borrar la publicación" }
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    companion object {
        const val GROUPS_COLLECTION = "groups"
        const val USERS_COLLECTION = "users"
        const val OUTINGS_COLLECTION = "outings"
        const val CHAT_COLLECTION = "chat_messages"
        const val STORIES_COLLECTION = "stories"
        const val GROUP_POSTS_COLLECTION = "group_posts"
    }
}
