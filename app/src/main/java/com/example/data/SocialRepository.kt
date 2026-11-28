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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
        return observeGroup(groupId).flatMapLatest { group ->
            if (group == null) return@flatMapLatest emptyFlow()
            val generationId = requireCurrentGroupGenerationId(groupId, group.generationId)
            db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(MEMBERS_SUBCOLLECTION)
                .whereEqualTo("groupGenerationId", generationId)
                .snapshots().map { snap ->
                snap.toObjects(GroupMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<GroupMember> { it.groupRole.ordinal }.thenBy { it.displayName.lowercase() })
            }
        }
    }

    fun observeGroupInvites(groupId: String): Flow<List<GroupInvite>> {
        return observeGroup(groupId).flatMapLatest { group ->
            if (group == null) return@flatMapLatest emptyFlow()
            val generationId = requireCurrentGroupGenerationId(groupId, group.generationId)
            db.collection(GROUP_INVITES_COLLECTION)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("groupGenerationId", generationId)
                .snapshots().map { snap ->
                snap.toObjects(GroupInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
        }
    }

    /**
     * Atomically creates a schema v2 group with a 130-bit random groupId (decoupled from invites)
     * and initializes an immutable registry tombstone plus the creator's OWNER
     * document in /groups/{groupId}/members/{creatorUid}, all in one batch.
     */
    suspend fun createGroupAtomic(
        groupName: String,
        creatorUid: String = requireUserId(),
        creatorName: String
    ): Result<SocialGroup> = runCatching {
        val cleanName = groupName.trim().take(80).ifBlank { "Grupo de Salidas" }
        val groupId = GroupInviteCrypto.generateHighEntropyGroupId()
        val groupGenerationId = UUID.randomUUID().toString().replace("-", "")
        val groupRef = db.collection(GROUPS_COLLECTION).document(groupId)
        val registryRef = db.collection(GROUP_ID_REGISTRY_COLLECTION).document(groupId)
        val ownerMemberRef = groupRef.collection(MEMBERS_SUBCOLLECTION).document(creatorUid)
        try {
            val group = SocialGroup(
                id = groupId,
                name = cleanName,
                createdBy = creatorUid,
                schemaVersion = 2L,
                generationId = groupGenerationId
            )
            val batch = db.batch()
            batch.set(registryRef, mapOf(
                "groupId" to groupId,
                "createdBy" to creatorUid,
                "schemaVersion" to 2L,
                "generationId" to groupGenerationId,
                "createdAt" to FieldValue.serverTimestamp()
            ))
            batch.set(groupRef, group.toV2CreateMap(creatorUid))
            batch.set(ownerMemberRef, GroupMember.toOwnerCreateMap(creatorUid, groupId, creatorName, groupGenerationId))
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
        val groupGenerationId = resolveGroupGenerationId(cleanGroupId)
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
                    maxUses = safeMaxUses,
                    groupGenerationId = groupGenerationId
                )
            ).await()
            GroupInvite(
                id = token,
                token = token,
                groupId = cleanGroupId,
                groupGenerationId = groupGenerationId,
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
            // If an orphaned child record survived a prior group incarnation, remove only
            // this user's stale membership first. The invite redemption below remains atomic;
            // this cleanup grants no access and is safe to retry if redemption later fails.
            val invitePreview = inviteRef.get().await().toObject(GroupInvite::class.java)
            if (invitePreview != null) {
                val memberRef = db.collection(GROUPS_COLLECTION)
                    .document(invitePreview.groupId)
                    .collection(MEMBERS_SUBCOLLECTION)
                    .document(userId)
                val memberPreview = memberRef.get().await()
                if (memberPreview.exists() &&
                    memberPreview.getString("groupGenerationId").orEmpty() != invitePreview.groupGenerationId
                ) {
                    memberRef.delete().await()
                }
            }

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
                val existingMember = existingMemberSnap.toObject(GroupMember::class.java)
                if (existingMemberSnap.exists() && existingMember?.groupGenerationId == invite.groupGenerationId) {
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
                                inviteToken = normalized,
                                groupGenerationId = invite.groupGenerationId
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
            memberRef.set(
                GroupMember.toLegacyBackfillCreateMap(
                    uid = userId,
                    groupId = group.id,
                    displayName = displayName,
                    role = role,
                    groupGenerationId = group.generationId
                )
            ).await()
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
        val memberRef = db.collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(MEMBERS_SUBCOLLECTION)
            .document(userId)
        try {
            memberRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, memberRef.path)
            throw e
        }
    }

    fun observeGroupPosts(groupId: String): Flow<List<GroupPost>> {
        require(groupId.isNotBlank()) { "El grupo activo es obligatorio" }
        return observeGroup(groupId).flatMapLatest { group ->
            val generationId = group?.generationId?.takeIf { it.isNotBlank() } ?: return@flatMapLatest emptyFlow()
            db.collection(GROUP_POSTS_COLLECTION)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("groupGenerationId", generationId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GroupPost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
            }
    }

    suspend fun createGroupPost(post: GroupPost, userId: String = requireUserId()): Result<String> = runCatching {
        val generationId = resolveGroupGenerationId(post.groupId)
        require(generationId.isNotBlank()) { "No se pudo verificar la generación actual del grupo" }
        val currentPost = post.copy(groupGenerationId = generationId)
        val postId = post.id.ifBlank { "post_${UUID.randomUUID().toString().replace("-", "").take(20)}" }
        val docRef = db.collection(GROUP_POSTS_COLLECTION).document(postId)
        try {
            docRef.set(currentPost.toCreateMap(userId)).await()
            postId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteGroupPost(
        postId: String,
        expectedGroupId: String,
        userId: String = requireUserId()
    ): Result<Unit> = runCatching {
        require(postId.isNotBlank()) { "La publicación no es válida" }
        val docRef = db.collection(GROUP_POSTS_COLLECTION).document(postId)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) return@runCatching Unit
            val post = snapshot.toObject(GroupPost::class.java)
                ?: throw IllegalStateException("La publicación no es válida")
            require(post.groupId == expectedGroupId && post.authorId == userId) {
                "Solo puedes borrar tus propias publicaciones del grupo activo"
            }
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
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
        return observeGroup(groupId).flatMapLatest { group ->
            if (group == null) return@flatMapLatest emptyFlow()
            val generationId = requireCurrentGroupGenerationId(groupId, group.generationId)
            db.collection(OUTINGS_COLLECTION)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("groupGenerationId", generationId)
                .snapshots()
                .map { querySnapshot ->
                    querySnapshot.toObjects(
                        Outing::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.dateIso + " " + it.time }
                }
            }
    }

    fun observeGroupOutingRsvps(
        groupId: String,
        groupGenerationId: String,
        memberIds: List<String>
    ): Flow<List<OutingRsvp>> {
        val generationId = requireCurrentGroupGenerationId(groupId, groupGenerationId)
        val currentMemberIds = memberIds.map(String::trim).filter(String::isNotBlank).distinct()
        return observeGroupOutings(groupId).flatMapLatest { outings ->
            val responseFlows = outings
                .filter { it.groupId == groupId && it.groupGenerationId == generationId && it.id.isNotBlank() }
                .flatMap { outing ->
                    currentMemberIds.map { memberId ->
                        db.collection(OUTINGS_COLLECTION)
                            .document(outing.id)
                            .collection(OUTING_RSVPS_SUBCOLLECTION)
                            .document(memberId)
                            .snapshots()
                            .map { snapshot ->
                                snapshot.toObject(
                                    OutingRsvp::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )?.takeIf { response ->
                                    response.id == memberId && response.userId == memberId &&
                                        response.outingId == outing.id &&
                                        response.belongsToCurrentGroup(groupId, generationId)
                                }
                            }
                    }
                }
            if (responseFlows.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(responseFlows) { responsesByOuting ->
                    responsesByOuting.filterNotNull()
                }
            }
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
        val groupGenerationId = resolveGroupGenerationId(outing.groupId)
        val currentOuting = outing.copy(groupGenerationId = groupGenerationId)
        val docId = outing.id.ifBlank { "outing_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection(OUTINGS_COLLECTION).document(docId)
        try {
            docRef.set(currentOuting.toCreateMap(userId)).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun upsertOuting(outing: Outing, userId: String = requireUserId()): Result<String> = runCatching {
        val groupGenerationId = resolveGroupGenerationId(outing.groupId)
        val currentOuting = outing.copy(groupGenerationId = groupGenerationId)
        val docId = outing.id.ifBlank { "outing_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection(OUTINGS_COLLECTION).document(docId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    tx.update(docRef, currentOuting.toUpdateMap())
                } else {
                    tx.set(docRef, currentOuting.copy(id = docId).toCreateMap(userId))
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
        val groupGenerationId = resolveGroupGenerationId(outing.groupId)
        val currentOuting = outing.copy(groupGenerationId = groupGenerationId)
        val docRef = db.collection(OUTINGS_COLLECTION).document(outing.id)
        try {
            docRef.update(currentOuting.toUpdateMap()).await()
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
        val authUid = requireUserId()
        check(userId == authUid) { "Solo se puede guardar la respuesta del usuario autenticado" }
        val docRef = db.collection(OUTINGS_COLLECTION).document(outingId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) return@runTransaction
                val current = snap.toObject(Outing::class.java) ?: return@runTransaction
                check(current.ownerId == authUid) { "Solo el propietario puede editar esta salida" }
                check(current.groupId.startsWith("personal_")) {
                    "Las respuestas de salidas de grupo deben guardarse en el documento RSVP propio"
                }
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

    suspend fun updateGroupOutingRsvpTransactional(
        outingId: String,
        groupId: String,
        groupGenerationId: String,
        choice: RsvpChoice,
        userId: String = requireUserId()
    ): Result<Unit> = runCatching {
        val authUid = requireUserId()
        check(userId == authUid) { "Solo se puede guardar la respuesta del usuario autenticado" }
        check(groupId.isNotBlank() && !groupId.startsWith("personal_")) {
            "La salida no pertenece a un grupo compartido"
        }
        val generationId = requireCurrentGroupGenerationId(groupId, groupGenerationId)
        val groupRef = db.collection(GROUPS_COLLECTION).document(groupId)
        val memberRef = groupRef.collection(MEMBERS_SUBCOLLECTION).document(authUid)
        val outingRef = db.collection(OUTINGS_COLLECTION).document(outingId)
        val responseRef = outingRef.collection(OUTING_RSVPS_SUBCOLLECTION).document(authUid)

        try {
            db.runTransaction { tx ->
                val groupSnap = tx.get(groupRef)
                val memberSnap = tx.get(memberRef)
                val outingSnap = tx.get(outingRef)
                val responseSnap = tx.get(responseRef)

                val groupCreatedAt = groupSnap.getTimestamp("createdAt")
                val memberJoinedAt = memberSnap.getTimestamp("joinedAt")
                check(groupSnap.exists() && groupSnap.getString("generationId") == generationId) {
                    "La generación del grupo ya no es la actual"
                }
                check(
                    memberSnap.exists() &&
                        memberSnap.getString("userId") == authUid &&
                        memberSnap.getString("groupId") == groupId &&
                        memberSnap.getString("groupGenerationId") == generationId &&
                        groupCreatedAt != null && memberJoinedAt != null && memberJoinedAt >= groupCreatedAt
                ) { "Se necesita una membresía v2 vigente para responder" }
                check(
                    outingSnap.exists() &&
                        outingSnap.getString("groupId") == groupId &&
                        outingSnap.getString("groupGenerationId") == generationId
                ) { "La salida no pertenece a esta generación del grupo" }
                if (responseSnap.exists()) {
                    val existingChoice = responseSnap.getString("choice")
                    check(
                        responseSnap.getString("outingId") == outingId &&
                            responseSnap.getString("userId") == authUid &&
                            responseSnap.getString("groupId") == groupId &&
                            responseSnap.getString("groupGenerationId") == generationId &&
                            (existingChoice == RsvpChoice.YES.name ||
                                existingChoice == RsvpChoice.MAYBE.name ||
                                existingChoice == RsvpChoice.NO.name)
                    ) { "La respuesta RSVP existente no es válida para esta salida" }
                }

                when {
                    choice == RsvpChoice.NONE && responseSnap.exists() -> tx.delete(responseRef)
                    choice == RsvpChoice.NONE -> Unit
                    responseSnap.exists() -> tx.update(
                        responseRef,
                        mapOf(
                            "choice" to choice.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    else -> tx.set(
                        responseRef,
                        OutingRsvp(
                            outingId = outingId,
                            userId = authUid,
                            groupId = groupId,
                            groupGenerationId = generationId,
                            choice = choice.name
                        ).toCreateMap()
                    )
                }
            }.await()
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, responseRef.path)
            throw e
        }
    }

    suspend fun deleteOuting(outingId: String): Result<Unit> = runCatching {
        val authUid = requireUserId()
        val docRef = db.collection(OUTINGS_COLLECTION).document(outingId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                check(snap.exists()) { "La salida no existe" }
                val current = snap.toObject(Outing::class.java)
                    ?: error("No se pudo leer la salida")
                check(current.ownerId == authUid) { "Solo el propietario puede borrar esta salida" }
                check(current.groupId.startsWith("personal_")) {
                    "Las salidas grupales se cancelan; no se borran físicamente"
                }
                tx.delete(docRef)
            }.await()
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
        return observeGroup(groupId).flatMapLatest { group ->
            if (group == null) return@flatMapLatest emptyFlow()
            val generationId = requireCurrentGroupGenerationId(groupId, group.generationId)
            db.collection(CHAT_COLLECTION)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("groupGenerationId", generationId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GroupChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.seconds ?: 0L }
                }
            }
    }

    suspend fun sendChatMessage(message: GroupChatMessage, userId: String = requireUserId()): Result<String> = runCatching {
        val groupGenerationId = resolveGroupGenerationId(message.groupId)
        val currentMessage = message.copy(groupGenerationId = groupGenerationId)
        val docId = message.id.ifBlank { "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection(CHAT_COLLECTION).document(docId)
        try {
            docRef.set(currentMessage.toCreateMap(userId)).await()
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
        return observeGroup(groupId).flatMapLatest { group ->
            if (group == null) return@flatMapLatest emptyFlow()
            val generationId = requireCurrentGroupGenerationId(groupId, group.generationId)
            db.collection(STORIES_COLLECTION)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("groupGenerationId", generationId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        OutingStory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
            }
    }

    suspend fun createStory(story: OutingStory, userId: String = requireUserId()): Result<String> = runCatching {
        val groupGenerationId = resolveGroupGenerationId(story.groupId)
        val currentStory = story.copy(groupGenerationId = groupGenerationId)
        val docId = story.id.ifBlank { "story_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection(STORIES_COLLECTION).document(docId)
        try {
            docRef.set(currentStory.toCreateMap(userId)).await()
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

    private suspend fun resolveGroupGenerationId(groupId: String): String {
        val cleanGroupId = groupId.trim()
        if (cleanGroupId.isBlank() || cleanGroupId.startsWith("personal_")) return ""
        val snapshot = try {
            db.collection(GROUPS_COLLECTION).document(cleanGroupId).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("No se pudo verificar la generación actual del grupo", e)
        }
        check(snapshot.exists()) { "El grupo ya no existe o no es accesible" }
        return requireCurrentGroupGenerationId(cleanGroupId, snapshot.getString("generationId").orEmpty())
    }

    internal fun requireCurrentGroupGenerationId(groupId: String, generationId: String): String {
        val cleanGenerationId = generationId.trim()
        check(groupId.isNotBlank() && cleanGenerationId.matches(Regex("^[a-f0-9]{32}$"))) {
            "El grupo no tiene una generación actual verificable; requiere migración antes de mostrar su contenido social"
        }
        return cleanGenerationId
    }

    companion object {
        const val GROUPS_COLLECTION = "groups"
        const val GROUP_ID_REGISTRY_COLLECTION = "groups_id_registry"
        const val MEMBERS_SUBCOLLECTION = "members"
        const val GROUP_INVITES_COLLECTION = "group_invites"
        const val USERS_COLLECTION = "users"
        const val OUTINGS_COLLECTION = "outings"
        const val OUTING_RSVPS_SUBCOLLECTION = "rsvps"
        const val CHAT_COLLECTION = "chat_messages"
        const val STORIES_COLLECTION = "stories"
        const val GROUP_POSTS_COLLECTION = "group_posts"
    }
}
