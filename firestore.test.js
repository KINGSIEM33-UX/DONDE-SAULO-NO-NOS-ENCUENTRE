const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const firebase = require("firebase/compat/app");
require("firebase/compat/firestore");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CAROL_UID = "carol_789";
const EVE_UID = "eve_attacker";

const HIGH_ENTROPY_GROUP_ID = "v2_grp_K7M9P2Q4R6T8V3W5X7Y9Z2A4B6";
const TOKEN_SINGLE = "INV-A2B3C4D5-E6F7G8H9-J2K3L4M5-N6P7Q8R9";
const TOKEN_MULTI = "INV-M2N3P4Q5-R6S7T8U9-V2W3X4Y5-Z6A7B8C9";
const TOKEN_REVOKED = "INV-R2E3V4O5-K6E7D8T9-O2K3E4N5-X6Y7Z8A9";
const TOKEN_EXPIRED = "INV-E2X3P4I5-R6E7D8T9-O2K3E4N5-B6C7D8E9";
const TOKEN_LEGACY_NEW = "INV-L2E3G4A5-C6Y7G8R9-O2U3P4T5-K6N7A8B9";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validOutingPayload(ownerId, groupId, ts) {
  return {
    ownerId,
    groupId,
    title: "Cena y Bolera",
    location: "Centro Comercial Norte",
    meetingPoint: "Entrada Principal",
    dateIso: "2026-11-10",
    time: "20:30",
    status: "CONFIRMED",
    budgetPerPerson: "15 EUR",
    kitItems: "Chaqueta, entradas",
    additionalInfo: "Llegar 10 minutos antes",
    placePhotoUrl: "preset_bowling",
    destLat: 40.4168,
    destLng: -3.7038,
    meetLat: 40.4175,
    meetLng: -3.7042,
    rsvpYesCount: 1,
    rsvpMaybeCount: 0,
    rsvpNoCount: 0,
    myRsvp: "YES",
    albumPhotos: ["photo_1"],
    createdAt: ts,
    updatedAt: ts,
  };
}

async function createV2GroupAtomically(db, groupId, ownerUid, ownerName, groupName = "Grupo Seguro") {
  const now = new Date();
  const batch = db.batch();
  batch.set(db.collection("groups_id_registry").doc(groupId), {
    groupId,
    createdBy: ownerUid,
    schemaVersion: 2,
    createdAt: now,
  });
  batch.set(db.collection("groups").doc(groupId), {
    name: groupName,
    createdBy: ownerUid,
    schemaVersion: 2,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(db.collection("groups").doc(groupId).collection("members").doc(ownerUid), {
    userId: ownerUid,
    groupId,
    displayName: ownerName,
    role: "OWNER",
    inviteToken: "",
    joinedAt: now,
    updatedAt: now,
  });
  await batch.commit();
}

async function createInviteDoc(db, { token, groupId, creatorUid, maxUses = 1, expiresInMs = 3600_000 }) {
  const now = new Date();
  const expiresAt = new Date(now.getTime() + expiresInMs);
  await db.collection("group_invites").doc(token).set({
    token,
    groupId,
    groupNamePreview: "Grupo Seguro",
    createdBy: creatorUid,
    revoked: false,
    expiresAt,
    maxUses,
    useCount: 0,
    lastRedeemedBy: "",
    createdAt: now,
    updatedAt: now,
  });
}

async function redeemInviteTransactionally(db, token, uid, displayName) {
  return db.runTransaction(async (tx) => {
    const inviteRef = db.collection("group_invites").doc(token);
    const inviteSnap = await tx.get(inviteRef);
    if (!inviteSnap.exists) {
      throw new Error("INVITE_NOT_FOUND");
    }
    const data = inviteSnap.data();
    const memberRef = db.collection("groups").doc(data.groupId).collection("members").doc(uid);
    const memberSnap = await tx.get(memberRef);
    if (memberSnap.exists) {
      return { alreadyMember: true, groupId: data.groupId };
    }
    const serverNow = firebase.firestore.FieldValue.serverTimestamp();
    tx.update(inviteRef, {
      useCount: firebase.firestore.FieldValue.increment(1),
      lastRedeemedBy: uid,
      updatedAt: serverNow,
    });
    tx.set(memberRef, {
      userId: uid,
      groupId: data.groupId,
      displayName,
      role: "MEMBER",
      inviteToken: token,
      joinedAt: serverNow,
      updatedAt: serverNow,
    });
    return { alreadyMember: false, groupId: data.groupId };
  });
}

// --- Baseline Outings Security Tests ---

test("Unauthenticated user: cannot read outings or groups", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("outings").get());
  await assertFails(unauthDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).get());
  await assertFails(unauthDb.collection("group_invites").doc(TOKEN_SINGLE).get());
});

test("Authenticated user: cannot read another user's private outing", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("outings")
      .doc("bob_outing_1")
      .set(validOutingPayload(BOB_UID, "personal_bob_456", new Date()));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("outings").doc("bob_outing_1").get());
});

test("Authenticated user: can create and query own outings, rejects shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("alice_outing_1")
      .set(validOutingPayload(ALICE_UID, "personal_alice_123", now))
  );
  await assertSucceeds(
    aliceDb.collection("outings").where("ownerId", "==", ALICE_UID).get()
  );
  await assertFails(
    aliceDb.collection("outings").doc("alice_outing_1").update({
      title: "Cena Editada",
      isAdminHack: true,
      updatedAt: new Date(),
    })
  );
});

// --- Adversarial Group & High-Entropy Invite Security Tests ---

test("Non-member who knows exact groupId CANNOT read group, members, outings, or self-join", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("grp_outing_1")
      .set(validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, new Date()))
  );

  // Eve knows exact groupId: ZERO access to group doc, other members, member list, or outings
  await assertFails(eveDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).get());
  await assertFails(eveDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").get());
  await assertFails(eveDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).get());
  await assertFails(eveDb.collection("outings").doc("grp_outing_1").get());
  await assertFails(eveDb.collection("group_invites").get());

  // Eve checking her own non-existent member slot gets exists=false identically to a non-existent groupId (zero leak)
  const eveOwnExistingGroup = await assertSucceeds(
    eveDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(EVE_UID).get()
  );
  const eveOwnMissingGroup = await assertSucceeds(
    eveDb.collection("groups").doc("grp_NONEXISTENT00000000000000").collection("members").doc(EVE_UID).get()
  );
  assert.equal(eveOwnExistingGroup.exists, false);
  assert.equal(eveOwnMissingGroup.exists, false);

  // Eve tries to self-join via direct member creation without valid invite
  const now = new Date();
  await assertFails(
    eveDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(EVE_UID).set({
      userId: EVE_UID,
      groupId: HIGH_ENTROPY_GROUP_ID,
      displayName: "Eve",
      role: "MEMBER",
      inviteToken: "",
      joinedAt: now,
      updatedAt: now,
    })
  );

  // Eve tries to self-join via old group document update
  await assertFails(
    eveDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).update({
      name: "Hacked",
      updatedAt: now,
    })
  );
});

test("Forged, wrong-group, short, revoked, expired, and reused single-use tokens are ALL rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  const otherGroupId = "v2_grp_OTHER9P2Q4R6T8V3W5X7Y9Z2A4";
  await assertSucceeds(createV2GroupAtomically(carolDb, otherGroupId, CAROL_UID, "Carol", "Otro Grupo"));

  // 1. Short/low-entropy token rejected even by owner
  await assertFails(
    createInviteDoc(aliceDb, { token: "QUEDA-2026", groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID })
  );

  // 2. Forged non-existent token rejected
  await assertFails(
    redeemInviteTransactionally(eveDb, "INV-FAKEFAKE-FAKEFAKE-FAKEFAKE-FAKEFAKE", EVE_UID, "Eve")
  );

  // 3. Single-use token: Bob succeeds, Carol reuse fails & cannot even read exhausted token
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_SINGLE, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 1 })
  );
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_SINGLE, BOB_UID, "Bob"));
  await assertFails(carolDb.collection("group_invites").doc(TOKEN_SINGLE).get());
  await assertFails(redeemInviteTransactionally(carolDb, TOKEN_SINGLE, CAROL_UID, "Carol"));

  // 4. Token belonging to otherGroupId CANNOT be used to join HIGH_ENTROPY_GROUP_ID
  const otherToken = "INV-O2T3H4E5-R6G7R8O9-U2P3T4O5-K6E7N8A9";
  await assertSucceeds(
    createInviteDoc(carolDb, { token: otherToken, groupId: otherGroupId, creatorUid: CAROL_UID, maxUses: 5 })
  );
  const now = new Date();
  await assertFails(
    eveDb.runTransaction(async (tx) => {
      const invRef = eveDb.collection("group_invites").doc(otherToken);
      const inv = await tx.get(invRef);
      tx.update(invRef, { useCount: inv.data().useCount + 1, lastRedeemedBy: EVE_UID, updatedAt: now });
      tx.set(eveDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(EVE_UID), {
        userId: EVE_UID,
        groupId: HIGH_ENTROPY_GROUP_ID,
        displayName: "Eve",
        role: "MEMBER",
        inviteToken: otherToken,
        joinedAt: now,
        updatedAt: now,
      });
    })
  );

  // 5. Revoked token: owner revokes -> Eve cannot read or redeem
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_REVOKED, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 5 })
  );
  await assertSucceeds(
    aliceDb.collection("group_invites").doc(TOKEN_REVOKED).update({ revoked: true, updatedAt: new Date() })
  );
  await assertFails(eveDb.collection("group_invites").doc(TOKEN_REVOKED).get());
  await assertFails(redeemInviteTransactionally(eveDb, TOKEN_REVOKED, EVE_UID, "Eve"));

  // 6. Expired token (seeded with past expiresAt) -> Eve cannot read or redeem
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const past = new Date(Date.now() - 60_000);
    await ctx.firestore().collection("group_invites").doc(TOKEN_EXPIRED).set({
      token: TOKEN_EXPIRED,
      groupId: HIGH_ENTROPY_GROUP_ID,
      groupNamePreview: "Grupo Seguro",
      createdBy: ALICE_UID,
      revoked: false,
      expiresAt: past,
      maxUses: 5,
      useCount: 0,
      lastRedeemedBy: "",
      createdAt: past,
      updatedAt: past,
    });
  });
  await assertFails(eveDb.collection("group_invites").doc(TOKEN_EXPIRED).get());
  await assertFails(redeemInviteTransactionally(eveDb, TOKEN_EXPIRED, EVE_UID, "Eve"));
});

test("Member CANNOT add/kick third parties, escalate own role, or burn invites; self-update and self-leave work", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_MULTI, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 5 })
  );
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));
  await assertSucceeds(redeemInviteTransactionally(carolDb, TOKEN_MULTI, CAROL_UID, "Carol"));

  // Bob (regular MEMBER) tries to escalate own role to ADMIN or OWNER -> DENIED
  await assertFails(
    bobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(BOB_UID).update({
      role: "ADMIN",
      updatedAt: new Date(),
    })
  );

  // Bob tries to kick Carol -> DENIED
  await assertFails(
    bobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(CAROL_UID).delete()
  );

  // Bob tries to add Eve directly -> DENIED
  const now = new Date();
  await assertFails(
    bobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(EVE_UID).set({
      userId: EVE_UID,
      groupId: HIGH_ENTROPY_GROUP_ID,
      displayName: "Eve",
      role: "MEMBER",
      inviteToken: TOKEN_MULTI,
      joinedAt: now,
      updatedAt: now,
    })
  );

  // Bob tries to create or revoke an invite -> DENIED (only OWNER/ADMIN)
  await assertFails(
    bobDb.collection("group_invites").doc(TOKEN_MULTI).update({ revoked: true, updatedAt: new Date() })
  );

  // Bob tries to burn an invite use without creating a new member doc -> DENIED
  await assertFails(
    bobDb.collection("group_invites").doc(TOKEN_MULTI).update({
      useCount: 3,
      lastRedeemedBy: BOB_UID,
      updatedAt: new Date(),
    })
  );

  // Bob CAN update ONLY his own displayName
  await assertSucceeds(
    bobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(BOB_UID).update({
      displayName: "Bob Actualizado",
      updatedAt: new Date(),
    })
  );

  // Alice (OWNER) promotes Bob to ADMIN -> ALLOWED; Admin Bob still CANNOT kick OWNER Alice -> DENIED
  await assertSucceeds(
    aliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(BOB_UID).update({
      role: "ADMIN",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).delete()
  );

  // Carol CAN leave the group herself -> ALLOWED
  await assertSucceeds(
    carolDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(CAROL_UID).delete()
  );
  // After leaving, Carol can no longer read the group
  await assertFails(carolDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).get());
});

test("Two concurrent joins on a multi-use invite BOTH succeed without losing either member", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_MULTI, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 5 })
  );

  const [bobRes, carolRes] = await Promise.all([
    redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"),
    redeemInviteTransactionally(carolDb, TOKEN_MULTI, CAROL_UID, "Carol"),
  ]);

  assert.equal(bobRes.alreadyMember, false);
  assert.equal(carolRes.alreadyMember, false);

  const inviteSnap = await aliceDb.collection("group_invites").doc(TOKEN_MULTI).get();
  assert.equal(inviteSnap.data().useCount, 2);

  const membersSnap = await aliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").get();
  const memberIds = membersSnap.docs.map((d) => d.id).sort();
  assert.deepEqual(memberIds, [ALICE_UID, BOB_UID, CAROL_UID].sort());

  // Idempotent re-join by Bob does NOT increment useCount
  const bobAgain = await redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob");
  assert.equal(bobAgain.alreadyMember, true);
  const inviteAfter = await aliceDb.collection("group_invites").doc(TOKEN_MULTI).get();
  assert.equal(inviteAfter.data().useCount, 2);
});

test("Two concurrent joins racing on a single-use invite: EXACTLY ONE succeeds, ONE fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_SINGLE, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 1 })
  );

  const results = await Promise.allSettled([
    redeemInviteTransactionally(bobDb, TOKEN_SINGLE, BOB_UID, "Bob"),
    redeemInviteTransactionally(carolDb, TOKEN_SINGLE, CAROL_UID, "Carol"),
  ]);

  const fulfilled = results.filter((r) => r.status === "fulfilled");
  const rejected = results.filter((r) => r.status === "rejected");
  assert.equal(fulfilled.length, 1);
  assert.equal(rejected.length, 1);

  const inviteSnap = await aliceDb.collection("group_invites").doc(TOKEN_SINGLE).get();
  assert.equal(inviteSnap.data().useCount, 1);
});

test("ALTO-4: group delete is denied and stale members/invites cannot cross a same-ID recreation", async () => {
  const oldAliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const newOwnerDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const freshBobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const staleToken = "INV-S7A8L9E0-G1R2E3S4-T5T6S7S8-M9E0N1T2";
  const oldCreatedAt = new Date(Date.now() - 120_000);

  // Model a complete, already-orphaned old subtree without deleting application data.
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      groupId: HIGH_ENTROPY_GROUP_ID,
      displayName: "Alice antigua",
      role: "OWNER",
      inviteToken: "",
      joinedAt: oldCreatedAt,
      updatedAt: oldCreatedAt,
    });
    await db.collection("group_invites").doc(staleToken).set({
      token: staleToken,
      groupId: HIGH_ENTROPY_GROUP_ID,
      groupNamePreview: "Grupo antiguo",
      createdBy: ALICE_UID,
      revoked: false,
      expiresAt: new Date(Date.now() + 3_600_000),
      maxUses: 3,
      useCount: 0,
      lastRedeemedBy: "",
      createdAt: oldCreatedAt,
      updatedAt: oldCreatedAt,
    });
    await db.collection("outings").doc("stale_group_outing").set(
      validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, oldCreatedAt)
    );
    await db.collection("outings").doc("stale_third_party_outing").set(
      validOutingPayload(BOB_UID, HIGH_ENTROPY_GROUP_ID, oldCreatedAt)
    );
  });

  // Recreating the same ID with a fresh creation timestamp is a normal route for the new owner.
  await assertSucceeds(createV2GroupAtomically(newOwnerDb, HIGH_ENTROPY_GROUP_ID, EVE_UID, "Eve", "Grupo nuevo"));

  // The old OWNER is not a member/owner of the new generation, despite the parent existing.
  await assertFails(oldAliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).get());
  // Owner-scoped access is intentionally independent of current group membership: Alice may
  // still read her own historical document, but not another user's historical document.
  const oldAliceOuting = await assertSucceeds(
    oldAliceDb.collection("outings").doc("stale_group_outing").get()
  );
  assert.equal(oldAliceOuting.data().ownerId, ALICE_UID);
  await assertFails(oldAliceDb.collection("outings").doc("stale_third_party_outing").get());
  await assertFails(
    oldAliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).get()
  );
  await assertFails(oldAliceDb.collection("group_invites").doc(staleToken).get());
  await assertFails(
    oldAliceDb.collection("group_invites").doc(staleToken).update({ revoked: true, updatedAt: new Date() })
  );
  await assertFails(
    oldAliceDb.collection("group_invites").doc("INV-N1E2W3-4T5R6E7S8-T9T0-OLD-STALE-TOKEN").set({
      token: "INV-N1E2W3-4T5R6E7S8-T9T0-OLD-STALE-TOKEN",
      groupId: HIGH_ENTROPY_GROUP_ID,
      groupNamePreview: "Intento antiguo",
      createdBy: ALICE_UID,
      revoked: false,
      expiresAt: new Date(Date.now() + 3_600_000),
      maxUses: 1,
      useCount: 0,
      lastRedeemedBy: "",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Client deletion is fail-closed, so no new orphan set can be created through Rules.
  await assertFails(newOwnerDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).delete());

  // New-generation members cannot read old group content, even when they know its document ID.
  await assertFails(freshBobDb.collection("outings").doc("stale_group_outing").get());
  await assertFails(freshBobDb.collection("outings").doc("stale_third_party_outing").get());

  // A client cannot create new group content with a forged pre-generation timestamp.
  await assertFails(
    oldAliceDb.collection("outings").doc("forged_old_generation_outing").set(
      validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, oldCreatedAt)
    )
  );
  await assertFails(
    oldAliceDb.collection("outings").doc("unauthorized_new_generation_outing").set(
      validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, new Date())
    )
  );

  // Positive normal path remains available for a fresh invite in the recreated group.
  const freshToken = "INV-F9R8E7S6-H5G4R3O2-U1P0N9E8-W7X6Y5Z4";
  await assertSucceeds(createInviteDoc(newOwnerDb, {
    token: freshToken,
    groupId: HIGH_ENTROPY_GROUP_ID,
    creatorUid: EVE_UID,
    maxUses: 1,
  }));
  await assertSucceeds(redeemInviteTransactionally(freshBobDb, freshToken, BOB_UID, "Bob nuevo"));
  await assertSucceeds(
    newOwnerDb.collection("outings").doc("fresh_group_outing").set(
      validOutingPayload(EVE_UID, HIGH_ENTROPY_GROUP_ID, new Date())
    )
  );
  await assertSucceeds(freshBobDb.collection("outings").doc("fresh_group_outing").get());
});

test("Legacy group (QUEDA-2026): preserves existing members & outings, closes self-join door, supports reversible backfill & v2 invites", async () => {
  const now = new Date();
  // Seed legacy v1 group & outing as they already exist in production
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("groups").doc("QUEDA-2026").set({
      name: "Grupo Legacy",
      inviteCode: "QUEDA-2026",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdAt: now,
      updatedAt: now,
    });
    await db
      .collection("outings")
      .doc("legacy_outing_1")
      .set(validOutingPayload(ALICE_UID, "QUEDA-2026", now));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  // 1. Existing legacy member Bob keeps reading group & legacy outing with ZERO disruption
  await assertSucceeds(bobDb.collection("groups").doc("QUEDA-2026").get());
  await assertSucceeds(bobDb.collection("outings").doc("legacy_outing_1").get());
  await assertFails(
    bobDb.collection("outings").where("groupId", "==", "QUEDA-2026").get()
  );

  // 2. Outsider Eve knowing QUEDA-2026 CANNOT read group, CANNOT read legacy outing, and CANNOT self-join!
  await assertFails(eveDb.collection("groups").doc("QUEDA-2026").get());
  await assertFails(eveDb.collection("outings").doc("legacy_outing_1").get());
  await assertFails(
    eveDb.collection("groups").doc("QUEDA-2026").update({
      memberIds: [ALICE_UID, BOB_UID, EVE_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob", [EVE_UID]: "Eve" },
      updatedAt: new Date(),
    })
  );

  // 3. Legacy member Bob CANNOT backfill himself as OWNER, but CAN backfill himself as MEMBER
  await assertFails(
    bobDb.collection("groups").doc("QUEDA-2026").collection("members").doc(BOB_UID).set({
      userId: BOB_UID,
      groupId: "QUEDA-2026",
      displayName: "Bob",
      role: "OWNER",
      inviteToken: "",
      joinedAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc("QUEDA-2026").collection("members").doc(BOB_UID).set({
      userId: BOB_UID,
      groupId: "QUEDA-2026",
      displayName: "Bob",
      role: "MEMBER",
      inviteToken: "",
      joinedAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // 4. Legacy owner Alice issues a new high-entropy v2 invite for QUEDA-2026 -> Carol joins & reads legacy outing!
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_LEGACY_NEW, groupId: "QUEDA-2026", creatorUid: ALICE_UID, maxUses: 3 })
  );
  await assertSucceeds(redeemInviteTransactionally(carolDb, TOKEN_LEGACY_NEW, CAROL_UID, "Carol"));
  await assertSucceeds(carolDb.collection("groups").doc("QUEDA-2026").get());
  await assertSucceeds(carolDb.collection("outings").doc("legacy_outing_1").get());
  await assertFails(
    carolDb.collection("outings").where("groupId", "==", "QUEDA-2026").get()
  );
});

// --- ALTO-4 v2 namespace + immutable registry adversarial tests ---
test("ALTO-4: v2 creation requires one atomic registry + group + OWNER batch; legacy IDs fail closed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const v2Id = "v2_grp_ATOMIC7P4R6T8V3W5X7Y9Z2A4B6";
  const legacyId = "QUEDA-9999";
  const now = new Date();
  const groupPayload = {
    name: "Atómico", createdBy: ALICE_UID, schemaVersion: 2,
    createdAt: now, updatedAt: now,
  };
  const ownerPayload = {
    userId: ALICE_UID, groupId: v2Id, displayName: "Alice", role: "OWNER",
    inviteToken: "", joinedAt: now, updatedAt: now,
  };

  await assertFails(aliceDb.collection("groups").doc(v2Id).set(groupPayload));
  await assertFails(aliceDb.collection("groups_id_registry").doc(v2Id).set({
    groupId: v2Id, createdBy: ALICE_UID, schemaVersion: 2, createdAt: now,
  }));
  await assertFails(aliceDb.collection("groups").doc(legacyId).set({
    name: "Legacy nuevo", createdBy: ALICE_UID, schemaVersion: 2,
    createdAt: now, updatedAt: now,
  }));

  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("groups_id_registry").doc(v2Id), {
    groupId: v2Id, createdBy: ALICE_UID, schemaVersion: 2, createdAt: now,
  });
  batch.set(aliceDb.collection("groups").doc(v2Id), groupPayload);
  batch.set(aliceDb.collection("groups").doc(v2Id).collection("members").doc(ALICE_UID), ownerPayload);
  await assertSucceeds(batch.commit());
});

test("ALTO-4: registry cannot be forged, read, listed, updated, deleted, or bypassed by a batch", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const v2Id = "v2_grp_REGISTRY7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, v2Id, ALICE_UID, "Alice"));
  const registry = aliceDb.collection("groups_id_registry").doc(v2Id);

  await assertFails(eveDb.collection("groups_id_registry").doc(v2Id).get());
  await assertFails(eveDb.collection("groups_id_registry").get());
  await assertFails(eveDb.collection("groups_id_registry").doc(v2Id).set({
    groupId: v2Id, createdBy: EVE_UID, schemaVersion: 2, createdAt: new Date(),
  }));
  await assertFails(aliceDb.collection("groups_id_registry").doc(v2Id).update({ createdBy: EVE_UID }));
  await assertFails(aliceDb.collection("groups_id_registry").doc(v2Id).delete());

  // A batch cannot create a registry entry for a missing group or spoof its owner.
  const orphanId = "v2_grp_ORPHAN7P4R6T8V3W5X7Y9Z2A4B6";
  const orphanBatch = eveDb.batch();
  orphanBatch.set(eveDb.collection("groups_id_registry").doc(orphanId), {
    groupId: orphanId, createdBy: EVE_UID, schemaVersion: 2, createdAt: new Date(),
  });
  await assertFails(orphanBatch.commit());
  assert.ok(registry.path.endsWith(v2Id));
});

test("ALTO-4: delete/recreate is permanently blocked by the tombstone, including simultaneous attempts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const v2Id = "v2_grp_TOMBSTONE7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, v2Id, ALICE_UID, "Alice"));
  await assertFails(aliceDb.collection("groups").doc(v2Id).delete());

  // Even if a privileged migration removes the parent, the client cannot reuse the ID.
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("groups").doc(v2Id).delete();
  });
  const results = await Promise.allSettled([
    createV2GroupAtomically(aliceDb, v2Id, ALICE_UID, "Alice otra"),
    createV2GroupAtomically(eveDb, v2Id, EVE_UID, "Eve"),
  ]);
  assert.equal(results.filter((r) => r.status === "fulfilled").length, 0);
  assert.equal(results.filter((r) => r.status === "rejected").length, 2);
  await assertFails(aliceDb.collection("groups_id_registry").doc(v2Id).delete());
});
