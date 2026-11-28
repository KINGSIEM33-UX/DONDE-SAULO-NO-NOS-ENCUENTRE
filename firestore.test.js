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

const HIGH_ENTROPY_GROUP_ID = "grp_K7M9P2Q4R6T8V3W5X7Y9Z2A4B6";
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
    rsvpByUser: { [ownerId]: "YES" },
    albumPhotos: ["photo_1"],
    createdAt: ts,
    updatedAt: ts,
  };
}

async function createV2GroupAtomically(db, groupId, ownerUid, ownerName, groupName = "Grupo Seguro") {
  const serverNow = firebase.firestore.FieldValue.serverTimestamp();
  const batch = db.batch();
  batch.set(db.collection("groups").doc(groupId), {
    name: groupName,
    createdBy: ownerUid,
    schemaVersion: 2,
    createdAt: serverNow,
    updatedAt: serverNow,
  });
  batch.set(db.collection("groups").doc(groupId).collection("members").doc(ownerUid), {
    userId: ownerUid,
    groupId,
    displayName: ownerName,
    role: "OWNER",
    inviteToken: "",
    joinedAt: serverNow,
    updatedAt: serverNow,
  });
  await batch.commit();
}

async function createInviteDoc(db, { token, groupId, creatorUid, maxUses = 1, expiresInMs = 3600_000 }) {
  const expiresAt = new Date(Date.now() + expiresInMs);
  const serverNow = firebase.firestore.FieldValue.serverTimestamp();
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
    createdAt: serverNow,
    updatedAt: serverNow,
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
  const otherGroupId = "grp_OTHER9P2Q4R6T8V3W5X7Y9Z2A4";
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
  await assertSucceeds(bobDb.collection("outings").where("groupId", "==", "QUEDA-2026").get());

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
  await assertSucceeds(carolDb.collection("outings").where("groupId", "==", "QUEDA-2026").get());
});

// --- Content Authorization, Immutability & Self-Only Verifiable Delta Helpers ---

function validChatPayload(senderId, groupId, ts, text = "Hola grupo") {
  return {
    senderId,
    senderName: "Miembro",
    groupId,
    text,
    messageType: "TEXT",
    audioDurationSec: 0,
    audioUrl: "",
    transcription: "",
    sharedOutingId: "",
    sharedOutingTitle: "",
    reactions: [],
    reactionsByUser: {},
    createdAt: ts,
    updatedAt: ts,
  };
}

function validStoryPayload(authorId, groupId, ts, caption = "Gran tarde") {
  const payload = {
    authorId,
    authorName: "Miembro",
    caption,
    filterStyle: "OBSIDIAN",
    mediaUri: "story_sunset",
    outingTitle: "Cena",
    likesCount: 0,
    likedByUser: {},
    createdAt: ts,
    updatedAt: ts,
  };
  if (groupId !== null) {
    payload.groupId = groupId;
  }
  return payload;
}

async function rsvpOutingTransactionally(db, outingId, uid, choice) {
  const docRef = db.collection("outings").doc(outingId);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(docRef);
    const data = snap.data() || {};
    const existingMap = data.rsvpByUser || {};
    const oldChoice = existingMap[uid] || "NONE";
    const yesDelta = (choice === "YES" ? 1 : 0) - (oldChoice === "YES" ? 1 : 0);
    const maybeDelta = (choice === "MAYBE" ? 1 : 0) - (oldChoice === "MAYBE" ? 1 : 0);
    const noDelta = (choice === "NO" ? 1 : 0) - (oldChoice === "NO" ? 1 : 0);
    if (yesDelta === 0 && maybeDelta === 0 && noDelta === 0) return { noOp: true };

    const updateObj = {
      [`rsvpByUser.${uid}`]:
        choice === "NONE" ? firebase.firestore.FieldValue.delete() : choice,
      rsvpYesCount: firebase.firestore.FieldValue.increment(yesDelta),
      rsvpMaybeCount: firebase.firestore.FieldValue.increment(maybeDelta),
      rsvpNoCount: firebase.firestore.FieldValue.increment(noDelta),
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    };
    tx.update(docRef, updateObj);
    return { noOp: false };
  });
}

async function toggleStoryLikeTransactionally(db, storyId, uid) {
  const docRef = db.collection("stories").doc(storyId);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(docRef);
    const data = snap.data() || {};
    const likedMap = data.likedByUser || {};
    const wasLiked = likedMap[uid] === true;
    const delta = wasLiked ? -1 : 1;
    tx.update(docRef, {
      [`likedByUser.${uid}`]: wasLiked ? firebase.firestore.FieldValue.delete() : true,
      likesCount: firebase.firestore.FieldValue.increment(delta),
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    });
    return { liked: !wasLiked };
  });
}

// --- Adversarial Content Authorization Tests ---

test("Non-members and ex-members CANNOT create outings, chat_messages, or stories for another group or personal scope", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = new Date();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_MULTI, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 5 })
  );
  await assertSucceeds(redeemInviteTransactionally(carolDb, TOKEN_MULTI, CAROL_UID, "Carol"));

  // 1. Non-member Eve CANNOT create outings, chat_messages, or stories in Alice's group or Alice's personal scope
  await assertFails(
    eveDb.collection("outings").doc("eve_injected_outing").set(validOutingPayload(EVE_UID, HIGH_ENTROPY_GROUP_ID, now))
  );
  await assertFails(
    eveDb.collection("chat_messages").doc("eve_injected_msg").set(validChatPayload(EVE_UID, HIGH_ENTROPY_GROUP_ID, now))
  );
  await assertFails(
    eveDb.collection("stories").doc("eve_injected_story").set(validStoryPayload(EVE_UID, HIGH_ENTROPY_GROUP_ID, now))
  );
  await assertFails(
    eveDb.collection("outings").doc("eve_personal_inject").set(validOutingPayload(EVE_UID, `personal_${ALICE_UID}`, now))
  );
  await assertFails(
    eveDb.collection("chat_messages").doc("eve_personal_msg").set(validChatPayload(EVE_UID, `personal_${ALICE_UID}`, now))
  );
  await assertFails(
    eveDb.collection("stories").doc("eve_personal_story").set(validStoryPayload(EVE_UID, `personal_${ALICE_UID}`, now))
  );

  // 2. Current member Carol CAN create outings, chat_messages, and stories in the group
  await assertSucceeds(
    carolDb.collection("outings").doc("carol_grp_outing").set(validOutingPayload(CAROL_UID, HIGH_ENTROPY_GROUP_ID, now))
  );
  await assertSucceeds(
    carolDb.collection("chat_messages").doc("carol_grp_msg").set(validChatPayload(CAROL_UID, HIGH_ENTROPY_GROUP_ID, now))
  );
  await assertSucceeds(
    carolDb.collection("stories").doc("carol_grp_story").set(validStoryPayload(CAROL_UID, HIGH_ENTROPY_GROUP_ID, now))
  );

  // 3. Carol leaves the group -> ex-member Carol IMMEDIATELY loses write/edit/delete access to group content
  await assertSucceeds(
    carolDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(CAROL_UID).delete()
  );
  await assertFails(
    carolDb.collection("outings").doc("carol_after_leave").set(validOutingPayload(CAROL_UID, HIGH_ENTROPY_GROUP_ID, new Date()))
  );
  await assertFails(
    carolDb.collection("chat_messages").doc("carol_msg_after_leave").set(validChatPayload(CAROL_UID, HIGH_ENTROPY_GROUP_ID, new Date()))
  );
  await assertFails(
    carolDb.collection("stories").doc("carol_story_after_leave").set(validStoryPayload(CAROL_UID, HIGH_ENTROPY_GROUP_ID, new Date()))
  );
  await assertFails(
    carolDb.collection("outings").doc("carol_grp_outing").update({ title: "Edited after leave", updatedAt: new Date() })
  );

  // 4. Personal scope (personal_<uid> and group-less personal story) works cleanly for Alice
  await assertSucceeds(
    aliceDb.collection("outings").doc("alice_personal_1").set(validOutingPayload(ALICE_UID, `personal_${ALICE_UID}`, now))
  );
  await assertSucceeds(
    aliceDb.collection("chat_messages").doc("alice_personal_msg").set(validChatPayload(ALICE_UID, `personal_${ALICE_UID}`, now))
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("alice_personal_story").set(validStoryPayload(ALICE_UID, `personal_${ALICE_UID}`, now))
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("alice_groupless_story").set(validStoryPayload(ALICE_UID, null, now))
  );
});

test("Outing ownerId & groupId are immutable; non-owners CANNOT edit, cancel, or delete someone else's outing; initial RSVP forgery rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  const secondGroupId = "grp_SECOND9P2Q4R6T8V3W5X7Y9Z2A";
  await assertSucceeds(createV2GroupAtomically(aliceDb, secondGroupId, ALICE_UID, "Alice", "Segundo Grupo"));
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_MULTI, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 5 })
  );
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));

  // 1. Forged initial RSVP counts or third-party RSVP map on create -> REJECTED
  await assertFails(
    aliceDb.collection("outings").doc("forged_counts").set({
      ...validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, now),
      rsvpYesCount: 10,
    })
  );
  await assertFails(
    aliceDb.collection("outings").doc("forged_third_party_rsvp").set({
      ...validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, now),
      rsvpByUser: { [BOB_UID]: "YES" },
    })
  );

  // 2. Valid create by Alice succeeds
  await assertSucceeds(
    aliceDb.collection("outings").doc("alice_outing").set(validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, now))
  );

  // 3. Even owner Alice CANNOT change groupId or ownerId (strictly immutable)
  await assertFails(
    aliceDb.collection("outings").doc("alice_outing").update({ groupId: secondGroupId, updatedAt: new Date() })
  );
  await assertFails(
    aliceDb.collection("outings").doc("alice_outing").update({ ownerId: BOB_UID, updatedAt: new Date() })
  );

  // 4. Owner Alice CAN edit editorial fields & cancel her own outing, but CANNOT forge counters during editorial update
  await assertSucceeds(
    aliceDb.collection("outings").doc("alice_outing").update({
      title: "Cena Actualizada",
      status: "CANCELLED",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("outings").doc("alice_outing").update({
      title: "Cena + Contadores Falsos",
      rsvpYesCount: 99,
      updatedAt: new Date(),
    })
  );

  // 5. Group member Bob CANNOT edit title, cancel status, move groupId, change ownerId, modify albumPhotos, or delete Alice's outing
  await assertFails(
    bobDb.collection("outings").doc("alice_outing").update({ title: "Secuestrada", updatedAt: new Date() })
  );
  await assertFails(
    bobDb.collection("outings").doc("alice_outing").update({ status: "CONFIRMED", updatedAt: new Date() })
  );
  await assertFails(
    bobDb.collection("outings").doc("alice_outing").update({ groupId: secondGroupId, updatedAt: new Date() })
  );
  await assertFails(
    bobDb.collection("outings").doc("alice_outing").update({ ownerId: BOB_UID, updatedAt: new Date() })
  );
  await assertFails(
    bobDb.collection("outings").doc("alice_outing").update({ albumPhotos: ["hacked_photo"], updatedAt: new Date() })
  );
  await assertFails(bobDb.collection("outings").doc("alice_outing").delete());
});

test("Self-only verifiable RSVP: members cannot forge third-party RSVPs or counters; concurrent RSVPs BOTH succeed with exact counts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const now = new Date();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_MULTI, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 5 })
  );
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));
  await assertSucceeds(redeemInviteTransactionally(carolDb, TOKEN_MULTI, CAROL_UID, "Carol"));
  await assertSucceeds(
    aliceDb.collection("outings").doc("rsvp_outing").set(validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, now))
  );

  // 1. Bob CANNOT alter Alice's RSVP or inject a fake UID
  await assertFails(
    bobDb.collection("outings").doc("rsvp_outing").update({
      [`rsvpByUser.${ALICE_UID}`]: "NO",
      rsvpYesCount: 0,
      rsvpNoCount: 1,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("outings").doc("rsvp_outing").update({
      "rsvpByUser.ghost_uid": "YES",
      rsvpYesCount: 2,
      updatedAt: new Date(),
    })
  );

  // 2. Bob CANNOT forge counters (+50) or overwrite shared myRsvp
  await assertFails(
    bobDb.collection("outings").doc("rsvp_outing").update({
      [`rsvpByUser.${BOB_UID}`]: "YES",
      rsvpYesCount: 51,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("outings").doc("rsvp_outing").update({
      [`rsvpByUser.${BOB_UID}`]: "YES",
      rsvpYesCount: 2,
      myRsvp: "NO",
      updatedAt: new Date(),
    })
  );

  // 3. Real concurrency: Bob and Carol concurrently RSVP YES via transaction + dot-notation + FieldValue.increment
  await assertSucceeds(
    Promise.all([
      rsvpOutingTransactionally(bobDb, "rsvp_outing", BOB_UID, "YES"),
      rsvpOutingTransactionally(carolDb, "rsvp_outing", CAROL_UID, "YES"),
    ])
  );
  const afterBoth = (await aliceDb.collection("outings").doc("rsvp_outing").get()).data();
  assert.equal(afterBoth.rsvpYesCount, 3);
  assert.equal(afterBoth.rsvpMaybeCount, 0);
  assert.equal(afterBoth.rsvpNoCount, 0);
  assert.deepEqual(afterBoth.rsvpByUser, {
    [ALICE_UID]: "YES",
    [BOB_UID]: "YES",
    [CAROL_UID]: "YES",
  });

  // 4. Bob CANNOT double-count by submitting YES again with increment(1)
  await assertFails(
    bobDb.collection("outings").doc("rsvp_outing").update({
      [`rsvpByUser.${BOB_UID}`]: "YES",
      rsvpYesCount: firebase.firestore.FieldValue.increment(1),
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );

  // 5. Bob switches YES -> MAYBE and Carol removes her RSVP (YES -> NONE)
  await assertSucceeds(rsvpOutingTransactionally(bobDb, "rsvp_outing", BOB_UID, "MAYBE"));
  await assertSucceeds(rsvpOutingTransactionally(carolDb, "rsvp_outing", CAROL_UID, "NONE"));
  const finalDoc = (await aliceDb.collection("outings").doc("rsvp_outing").get()).data();
  assert.equal(finalDoc.rsvpYesCount, 1);
  assert.equal(finalDoc.rsvpMaybeCount, 1);
  assert.equal(finalDoc.rsvpNoCount, 0);
  assert.deepEqual(finalDoc.rsvpByUser, {
    [ALICE_UID]: "YES",
    [BOB_UID]: "MAYBE",
  });
});

test("Chat messages & stories: immutable groupId/author, owner-only editorial edits, self-only concurrent reactions & likes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const now = new Date();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_MULTI, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 5 })
  );
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));
  await assertSucceeds(redeemInviteTransactionally(carolDb, TOKEN_MULTI, CAROL_UID, "Carol"));

  // --- Chat Message ---
  await assertSucceeds(
    aliceDb.collection("chat_messages").doc("msg_1").set(validChatPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, now))
  );
  // Bob cannot edit Alice's text, change groupId, overwrite legacy reactions array, or delete Alice's message
  await assertFails(bobDb.collection("chat_messages").doc("msg_1").update({ text: "Alterado", updatedAt: new Date() }));
  await assertFails(bobDb.collection("chat_messages").doc("msg_1").update({ groupId: `personal_${BOB_UID}`, updatedAt: new Date() }));
  await assertFails(bobDb.collection("chat_messages").doc("msg_1").update({ reactions: ["💀"], updatedAt: new Date() }));
  await assertFails(bobDb.collection("chat_messages").doc("msg_1").delete());

  // Alice can edit her own text, cannot spoof Bob's reaction
  await assertSucceeds(aliceDb.collection("chat_messages").doc("msg_1").update({ text: "Hola editado", updatedAt: new Date() }));
  await assertFails(
    aliceDb.collection("chat_messages").doc("msg_1").update({ [`reactionsByUser.${BOB_UID}`]: "❤️", updatedAt: new Date() })
  );

  // Concurrent self-only reactions from Bob & Carol both succeed without clobbering
  await assertSucceeds(
    Promise.all([
      bobDb.collection("chat_messages").doc("msg_1").update({
        [`reactionsByUser.${BOB_UID}`]: "🔥",
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      }),
      carolDb.collection("chat_messages").doc("msg_1").update({
        [`reactionsByUser.${CAROL_UID}`]: "🎉",
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      }),
    ])
  );
  const msgSnap = (await aliceDb.collection("chat_messages").doc("msg_1").get()).data();
  assert.deepEqual(msgSnap.reactionsByUser, { [BOB_UID]: "🔥", [CAROL_UID]: "🎉" });

  // Bob cannot delete or alter Carol's reaction
  await assertFails(
    bobDb.collection("chat_messages").doc("msg_1").update({
      [`reactionsByUser.${CAROL_UID}`]: firebase.firestore.FieldValue.delete(),
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );

  // --- Stories ---
  // Forged initial likesCount rejected
  await assertFails(
    aliceDb.collection("stories").doc("forged_story").set({
      ...validStoryPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, now),
      likesCount: 42,
    })
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set(validStoryPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, now))
  );

  // Bob cannot edit caption, move groupId, delete Alice's story, or forge likesCount
  await assertFails(bobDb.collection("stories").doc("story_1").update({ caption: "Fake", updatedAt: new Date() }));
  await assertFails(bobDb.collection("stories").doc("story_1").update({ groupId: `personal_${BOB_UID}`, updatedAt: new Date() }));
  await assertFails(bobDb.collection("stories").doc("story_1").update({ likesCount: 99, updatedAt: new Date() }));
  await assertFails(bobDb.collection("stories").doc("story_1").delete());

  // Alice can edit own caption, cannot forge likesCount
  await assertSucceeds(aliceDb.collection("stories").doc("story_1").update({ caption: "Atardecer editado", updatedAt: new Date() }));
  await assertFails(aliceDb.collection("stories").doc("story_1").update({ caption: "Hack", likesCount: 5, updatedAt: new Date() }));

  // Concurrent self-only likes from Bob & Carol both succeed with exact likesCount == 2
  await assertSucceeds(
    Promise.all([
      toggleStoryLikeTransactionally(bobDb, "story_1", BOB_UID),
      toggleStoryLikeTransactionally(carolDb, "story_1", CAROL_UID),
    ])
  );
  const likedStory = (await aliceDb.collection("stories").doc("story_1").get()).data();
  assert.equal(likedStory.likesCount, 2);
  assert.deepEqual(likedStory.likedByUser, { [BOB_UID]: true, [CAROL_UID]: true });

  // Bob cannot double-like or remove Carol's like
  await assertFails(
    bobDb.collection("stories").doc("story_1").update({
      [`likedByUser.${BOB_UID}`]: true,
      likesCount: firebase.firestore.FieldValue.increment(1),
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("story_1").update({
      [`likedByUser.${CAROL_UID}`]: firebase.firestore.FieldValue.delete(),
      likesCount: firebase.firestore.FieldValue.increment(-1),
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );

  // Bob can toggle off his own like (likesCount -> 1)
  await assertSucceeds(toggleStoryLikeTransactionally(bobDb, "story_1", BOB_UID));
  const afterUnlike = (await aliceDb.collection("stories").doc("story_1").get()).data();
  assert.equal(afterUnlike.likesCount, 1);
  assert.deepEqual(afterUnlike.likedByUser, { [CAROL_UID]: true });
});

test("Legacy group (QUEDA-2026) & legacy docs without rsvpByUser/likedByUser: preserves access and enforces authz without migration", async () => {
  const now = new Date();
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
    // Legacy outing WITHOUT rsvpByUser map
    const legacyOuting = validOutingPayload(ALICE_UID, "QUEDA-2026", now);
    delete legacyOuting.rsvpByUser;
    await db.collection("outings").doc("legacy_no_map_outing").set(legacyOuting);
    // Legacy story WITHOUT likedByUser map
    const legacyStory = validStoryPayload(ALICE_UID, "QUEDA-2026", now);
    delete legacyStory.likedByUser;
    legacyStory.likesCount = 2;
    await db.collection("stories").doc("legacy_no_map_story").set(legacyStory);
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  // Outsider Eve CANNOT create content in legacy QUEDA-2026 or RSVP/like legacy docs
  await assertFails(
    eveDb.collection("outings").doc("eve_legacy_outing").set(validOutingPayload(EVE_UID, "QUEDA-2026", new Date()))
  );
  await assertFails(
    eveDb.collection("chat_messages").doc("eve_legacy_msg").set(validChatPayload(EVE_UID, "QUEDA-2026", new Date()))
  );
  await assertFails(
    eveDb.collection("stories").doc("eve_legacy_story").set(validStoryPayload(EVE_UID, "QUEDA-2026", new Date()))
  );
  await assertFails(rsvpOutingTransactionally(eveDb, "legacy_no_map_outing", EVE_UID, "YES"));
  await assertFails(toggleStoryLikeTransactionally(eveDb, "legacy_no_map_story", EVE_UID));

  // Existing legacy member Bob CAN create new content, RSVP on legacy outing, and like legacy story
  await assertSucceeds(
    bobDb.collection("chat_messages").doc("bob_legacy_msg").set(validChatPayload(BOB_UID, "QUEDA-2026", new Date()))
  );
  await assertSucceeds(rsvpOutingTransactionally(bobDb, "legacy_no_map_outing", BOB_UID, "YES"));
  const updatedLegacyOuting = (await bobDb.collection("outings").doc("legacy_no_map_outing").get()).data();
  assert.equal(updatedLegacyOuting.rsvpYesCount, 2);
  assert.equal(updatedLegacyOuting.rsvpByUser[BOB_UID], "YES");

  await assertSucceeds(toggleStoryLikeTransactionally(bobDb, "legacy_no_map_story", BOB_UID));
  const updatedLegacyStory = (await bobDb.collection("stories").doc("legacy_no_map_story").get()).data();
  assert.equal(updatedLegacyStory.likesCount, 3);
  assert.equal(updatedLegacyStory.likedByUser[BOB_UID], true);

  // Legacy member Bob still CANNOT edit or cancel Alice's legacy outing
  await assertFails(
    bobDb.collection("outings").doc("legacy_no_map_outing").update({ status: "CANCELLED", updatedAt: new Date() })
  );
});
