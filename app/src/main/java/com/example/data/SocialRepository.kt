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

    // --- 0. Social Groups, Per-Member Subcollection & High-Entropy Invites ---
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

    fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> {
        return db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MEMBERS_SUBCOLLECTION)
            .snapshots()
            .map { snap ->
                snap.toObjects(GroupMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<GroupMember> { it.groupRole.ordinal }.thenBy { it.displayName.lowercase() })
            }
    }

    fun observeGroupInvites(groupId: String): Flow<List<GroupInvite>> {
        return db.collection(GROUP_INVITES_COLLECTION)
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snap ->
                snap.toObjects(GroupInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    /**
     * Atomically creates a schema v2 group with a 130-bit random groupId (decoupled from invites)
     * and initializes the creator's OWNER document in /groups/{groupId}/members/{creatorUid}.
     */
    suspend fun createGroupAtomic(
        groupName: String,
        creatorUid: String = requireUserId(),
        creatorName: String
    ): Result<SocialGroup> = runCatching {
        val cleanName = groupName.trim().take(80).ifBlank { "Grupo de Salidas" }
        val groupId = GroupInviteCrypto.generateHighEntropyGroupId()
        val groupRef = db.collection(GROUPS_COLLECTION).document(groupId)
        val ownerMemberRef = groupRef.collection(MEMBERS_SUBCOLLECTION).document(creatorUid)
        try {
            val group = SocialGroup(
                id = groupId,
                name = cleanName,
                createdBy = creatorUid,
                schemaVersion = 2L
            )
            val batch = db.batch()
            batch.set(groupRef, group.toV2CreateMap(creatorUid))
            batch.set(ownerMemberRef, GroupMember.toOwnerCreateMap(creatorUid, groupId, creatorName))
            batch.commit().await()
            group
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, groupRef.path)
            throw e
        }
    }

    /**
     * Creates a 160-bit high-entropy, revocable, expirable invite token in /group_invites/{token}.
     * Only OWNER or ADMIN of the target group is permitted by firestore.rules.
     */
    suspend fun createGroupInvite(
        groupId: String,
        groupNamePreview: String,
        creatorUid: String = requireUserId(),
        maxUses: Long = 1L,
        expiresInHours: Long = 24L
    ): Result<GroupInvite> = runCatching {
        val cleanGroupId = groupId.trim()
        require(cleanGroupId.isNotBlank()) { "El grupo no es válido" }
        val token = GroupInviteCrypto.generateHighEntropyInviteToken()
        val safeMaxUses = maxUses.coerceIn(1L, 100L)
        val safeHours = expiresInHours.coerceIn(1L, 24L * 30L)
        val expiresAt = com.google.firebase.Timestamp(
            java.util.Date(System.currentTimeMillis() + safeHours * 3_600_000L)
        )
        val inviteRef = db.collection(GROUP_INVITES_COLLECTION).document(token)
        try {
            inviteRef.set(
                GroupInvite.toCreateMap(
                    token = token,
                    groupId = cleanGroupId,
                    groupNamePreview = groupNamePreview,
                    creatorUid = creatorUid,
                    expiresAt = expiresAt,
                    maxUses = safeMaxUses
                )
            ).await()
            GroupInvite(
                id = token,
                token = token,
                groupId = cleanGroupId,
                groupNamePreview = groupNamePreview.take(80).ifBlank { "Grupo" },
                createdBy = creatorUid,
                revoked = false,
                expiresAt = expiresAt,
                maxUses = safeMaxUses,
                useCount = 0L
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, inviteRef.path)
            throw e
        }
    }

    suspend fun revokeGroupInvite(token: String): Result<Unit> = runCatching {
        val cleanToken = GroupInviteCrypto.normalizeInviteToken(token)
        val inviteRef = db.collection(GROUP_INVITES_COLLECTION).document(cleanToken)
        try {
            inviteRef.update(
                mapOf(
                    "revoked" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, inviteRef.path)
            throw e
        }
    }

    /**
     * Atomically and idempotently redeems a high-entropy invite token inside a single Firestore
     * transaction: increments /group_invites/{token}.useCount by +1 and creates the user's own
     * /groups/{groupId}/members/{userId} doc with role MEMBER. Prevents race conditions on
     * concurrent joins and never consumes a use if the user is already a member.
     */
    suspend fun redeemInviteAndJoinGroupAtomic(
        rawTokenInput: String,
        userId: String = requireUserId(),
        displayName: String
    ): InviteRedemptionOutcome {
        val normalized = GroupInviteCrypto.normalizeInviteToken(rawTokenInput)
        if (normalized.isBlank()) {
            return InviteRedemptionOutcome.InvalidOrUnavailable("Introduce un token o enlace de invitación válido.")
        }
        if (GroupInviteCrypto.isLegacyGroupCode(rawTokenInput)) {
            return InviteRedemptionOutcome.LegacyCodeUnsupported
        }
        if (!GroupInviteCrypto.isHighEntropyInviteToken(normalized)) {
            return InviteRedemptionOutcome.InvalidOrUnavailable(
                "Formato de invitación no válido. Pega el token completo (INV-…)."
            )
        }

        val inviteRef = db.collection(GROUP_INVITES_COLLECTION).document(normalized)
        return try {
            val (joinedGroupId, alreadyMember) = db.runTransaction { tx ->
                val inviteSnap = tx.get(inviteRef)
                if (!inviteSnap.exists()) {
                    throw IllegalStateException("INVITE_UNAVAILABLE")
                }
                val invite = inviteSnap.toObject(GroupInvite::class.java)
                    ?: throw IllegalStateException("INVITE_UNAVAILABLE")

                val memberRef = db.collection(GROUPS_COLLECTION)
                    .document(invite.groupId)
                    .collection(MEMBERS_SUBCOLLECTION)
                    .document(userId)
                val existingMemberSnap = tx.get(memberRef)
                if (existingMemberSnap.exists()) {
                    return@runTransaction invite.groupId to true
                }

                val validation = GroupInviteCrypto.validateInviteForRedemption(
                    token = normalized,
                    revoked = invite.revoked,
                    expiresAtMillis = invite.expiresAt?.toDate()?.time,
                    useCount = invite.useCount,
                    maxUses = invite.maxUses,
                    nowMillis = System.currentTimeMillis(),
                    isAlreadyMember = false
                )
                when (validation) {
                    GroupInviteCrypto.InviteValidationResult.Valid -> {
                        tx.update(
                            inviteRef,
                            mapOf(
                                "useCount" to FieldValue.increment(1L),
                                "lastRedeemedBy" to userId,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        tx.set(
                            memberRef,
                            GroupMember.toInviteJoinCreateMap(
                                uid = userId,
                                groupId = invite.groupId,
                                displayName = displayName,
                                inviteToken = normalized
                            )
                        )
                        invite.groupId to false
                    }
                    GroupInviteCrypto.InviteValidationResult.AlreadyMember -> invite.groupId to true
                    GroupInviteCrypto.InviteValidationResult.Revoked -> throw IllegalStateException("INVITE_REVOKED")
                    GroupInviteCrypto.InviteValidationResult.Expired -> throw IllegalStateException("INVITE_EXPIRED")
                    GroupInviteCrypto.InviteValidationResult.Exhausted -> throw IllegalStateException("INVITE_EXHAUSTED")
                    GroupInviteCrypto.InviteValidationResult.InvalidFormat -> throw IllegalStateException("INVITE_UNAVAILABLE")
                }
            }.await()

            // Now that the user is an authenticated member, read the group metadata safely
            val groupSnap = db.collection(GROUPS_COLLECTION).document(joinedGroupId).get().await()
            val group = groupSnap.toObject(SocialGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: SocialGroup(id = joinedGroupId, name = "Grupo")
            InviteRedemptionOutcome.Joined(group = group, alreadyMember = alreadyMember)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            when (e.message) {
                "INVITE_REVOKED" -> InviteRedemptionOutcome.InvalidOrUnavailable("Esta invitación fue revocada por el administrador.")
                "INVITE_EXPIRED" -> InviteRedemptionOutcome.InvalidOrUnavailable("Esta invitación ha caducado. Pide una nueva.")
                "INVITE_EXHAUSTED" -> InviteRedemptionOutcome.InvalidOrUnavailable("Esta invitación ya alcanzó su límite de usos.")
                else -> InviteRedemptionOutcome.InvalidOrUnavailable("Invitación inválida, caducada, revocada o ya utilizada.")
            }
        } catch (e: Exception) {
            val raw = e.message.orEmpty()
            handleFirestoreError(e, OperationType.WRITE, inviteRef.path)
            if (raw.contains("PERMISSION_DENIED", ignoreCase = true)) {
                InviteRedemptionOutcome.InvalidOrUnavailable(
                    "Invitación inválida, caducada, revocada o ya utilizada."
                )
            } else {
                InviteRedemptionOutcome.Error(
                    "No se pudo completar la unión: ${e.localizedMessage ?: "error de conexión"}"
                )
            }
        }
    }

    /**
     * Reversible lazy self-backfill for users who are ALREADY members of a legacy v1 group
     * (listed in legacy memberIds). Never grants access to non-members.
     */
    suspend fun backfillLegacyMembershipIfNeeded(
        group: SocialGroup,
        userId: String = requireUserId(),
        displayName: String
    ): Result<Unit> = runCatching {
        if (group.id.isBlank() || !group.memberIds.contains(userId)) return@runCatching
        val memberRef = db.collection(GROUPS_COLLECTION)
            .document(group.id)
            .collection(MEMBERS_SUBCOLLECTION)
            .document(userId)
        val snap = memberRef.get().await()
        if (!snap.exists()) {
            val role = if (group.createdBy == userId) GroupRole.OWNER else GroupRole.MEMBER
            memberRef.set(GroupMember.toLegacyBackfillCreateMap(userId, group.id, displayName, role)).await()
        }
    }

    suspend fun updateOwnMemberDisplayName(
        groupId: String,
        userId: String = requireUserId(),
        displayName: String
    ): Result<Unit> = runCatching {
        val memberRef = db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MEMBERS_SUBCOLLECTION)
            .document(userId)
        memberRef.update(GroupMember.toSelfUpdateMap(displayName)).await()
    }

    suspend fun leaveGroup(groupId: String, userId: String = requireUserId()): Result<Unit> = runCatching {
        require(groupId.isNotBlank()) { "El grupo no es válido" }
        val authenticatedUserId = requireUserId()
        require(userId == authenticatedUserId) { "Solo puedes salir con tu propio usuario" }
        val memberRef = db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MEMBERS_SUBCOLLECTION)
            .document(userId)
        val groupRef = db.collection(GROUPS_COLLECTION).document(groupId)
        try {
            db.runTransaction { tx ->
                val groupSnapshot = tx.get(groupRef)
                check(groupSnapshot.exists()) { "GROUP_NOT_FOUND" }
                val group = groupSnapshot.toObject(SocialGroup::class.java)
                    ?: error("GROUP_NOT_FOUND")
                val memberSnapshot = tx.get(memberRef)
                val member = memberSnapshot.toObject(GroupMember::class.java)
                val isLegacyMember = group.memberIds.contains(userId)
                val isOwner = group.createdBy == userId || member?.groupRole == GroupRole.OWNER
                check(!isOwner) { "OWNER_CANNOT_LEAVE" }
                check(isLegacyMember || memberSnapshot.exists()) { "NOT_A_GROUP_MEMBER" }

                if (memberSnapshot.exists()) {
                    tx.delete(memberRef)
                }
                if (isLegacyMember) {
                    tx.update(
                        groupRef,
                        mapOf(
                            "memberIds" to group.memberIds.filterNot { it == userId },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                null
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, memberRef.path)
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
                    tx.update(docRef, outing.toUpdateMap())
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

    companion object {
        const val GROUPS_COLLECTION = "groups"
        const val MEMBERS_SUBCOLLECTION = "members"
        const val GROUP_INVITES_COLLECTION = "group_invites"
        const val USERS_COLLECTION = "users"
        const val OUTINGS_COLLECTION = "outings"
        const val CHAT_COLLECTION = "chat_messages"
        const val STORIES_COLLECTION = "stories"
    }
}
