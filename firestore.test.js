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
const crypto = require("node:crypto");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const groupGenerationIds = new Map();
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CAROL_UID = "carol_789";
const EVE_UID = "eve_attacker";
const DAVE_UID = "dave_1011";

const HIGH_ENTROPY_GROUP_ID = "v2_grp_K7M9P2Q4R6T8V3W5X7Y9Z2A4B6";
const TOKEN_SINGLE = "INV-A2B3C4D5-E6F7G8H9-J2K3L4M5-N6P7Q8R9";
const TOKEN_MULTI = "INV-M2N3P4Q5-R6S7T8U9-V2W3X4Y5-Z6A7B8C9";
const TOKEN_REVOKED = "INV-R2E3V4O5-K6E7D8T9-O2K3E4N5-X6Y7Z8A9";
const TOKEN_EXPIRED = "INV-E2X3P4I5-R6E7D8T9-O2K3E4N5-B6C7D8E9";
const TOKEN_LEGACY_NEW = "INV-L2E3G4A5-C6Y7G8R9-O2U3P4T5-K6N7A8B9";

const emulatorAddress = process.env.FIRESTORE_EMULATOR_HOST;
if (!emulatorAddress) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST must be set explicitly; the test suite clears Firestore before each case."
  );
}
const [emulatorHost, emulatorPortStr] = emulatorAddress.split(":");
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
  groupGenerationIds.clear();
});

function validOutingPayload(ownerId, groupId, ts, generationId = groupGenerationIds.get(groupId)) {
  const isPersonal = groupId.startsWith("personal_");
  const payload = {
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
    rsvpYesCount: isPersonal ? 1 : 0,
    rsvpMaybeCount: 0,
    rsvpNoCount: 0,
    myRsvp: isPersonal ? "YES" : "NONE",
    albumPhotos: ["photo_1"],
    createdAt: ts,
    updatedAt: ts,
  };
  if (generationId) payload.groupGenerationId = generationId;
  return payload;
}

function validGroupOutingRsvpPayload({
  outingId,
  userId,
  groupId,
  generationId = groupGenerationIds.get(groupId),
  choice = "YES",
  ...overrides
}) {
  return {
    outingId,
    userId,
    groupId,
    groupGenerationId: generationId,
    choice,
    createdAt: firebase.firestore.FieldValue.serverTimestamp(),
    updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    ...overrides,
  };
}

async function writeGroupOutingRsvpTransactionally(db, {
  outingId,
  userId,
  groupId,
  generationId,
  choice,
}) {
  const responseRef = db.collection("outings").doc(outingId).collection("rsvps").doc(userId);
  return db.runTransaction(async (transaction) => {
    const response = await transaction.get(responseRef);
    if (response.exists) {
      transaction.update(responseRef, {
        choice,
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      });
    } else {
      transaction.set(responseRef, validGroupOutingRsvpPayload({
        outingId,
        userId,
        groupId,
        generationId,
        choice,
      }));
    }
  });
}

function validChatPayload(senderId, groupId, ts, generationId = groupGenerationIds.get(groupId)) {
  const payload = {
    senderId,
    senderName: senderId === ALICE_UID ? "Alice" : "Bob",
    groupId,
    text: "Hola grupo",
    messageType: "TEXT",
    audioDurationSec: 0,
    audioUrl: "",
    transcription: "",
    sharedOutingId: "",
    sharedOutingTitle: "",
    reactions: [],
    createdAt: ts,
    updatedAt: ts,
  };
  if (generationId) payload.groupGenerationId = generationId;
  return payload;
}

function validStoryPayload(authorId, ts, groupId = null, generationId = groupGenerationIds.get(groupId)) {
  const story = {
    authorId,
    authorName: authorId === ALICE_UID ? "Alice" : "Bob",
    caption: "Una salida genial",
    filterStyle: "NEON_VIBES",
    mediaUri: "story_sunset",
    outingTitle: "Cena y Bolera",
    likesCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
  if (groupId) {
    story.groupId = groupId;
    if (generationId) story.groupGenerationId = generationId;
  }
  return story;
}

async function createV2GroupAtomically(db, groupId, ownerUid, ownerName, groupName = "Grupo Seguro") {
  const now = new Date();
  const generationId = crypto.randomBytes(16).toString("hex");
  groupGenerationIds.set(groupId, generationId);
  const batch = db.batch();
  batch.set(db.collection("groups_id_registry").doc(groupId), {
    groupId,
    createdBy: ownerUid,
    schemaVersion: 2,
    generationId,
    createdAt: now,
  });
  batch.set(db.collection("groups").doc(groupId), {
    name: groupName,
    createdBy: ownerUid,
    schemaVersion: 2,
    generationId,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(db.collection("groups").doc(groupId).collection("members").doc(ownerUid), {
    userId: ownerUid,
    groupId,
    groupGenerationId: generationId,
    displayName: ownerName,
    role: "OWNER",
    inviteToken: "",
    joinedAt: now,
    updatedAt: now,
  });
  await batch.commit();
  return generationId;
}

async function createInviteDoc(db, { token, groupId, creatorUid, maxUses = 1, expiresInMs = 3600_000 }) {
  const now = new Date();
  const expiresAt = new Date(now.getTime() + expiresInMs);
  const invite = {
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
  };
  const generationId = groupGenerationIds.get(groupId);
  if (generationId) invite.groupGenerationId = generationId;
  await db.collection("group_invites").doc(token).set(invite);
}

async function redeemInviteTransactionally(db, token, uid, displayName) {
  const invitePreview = await db.collection("group_invites").doc(token).get();
  if (invitePreview.exists) {
    const preview = invitePreview.data();
    const previewMemberRef = db.collection("groups").doc(preview.groupId).collection("members").doc(uid);
    const previewMember = await previewMemberRef.get();
    if (previewMember.exists && (previewMember.data().groupGenerationId || "") !== (preview.groupGenerationId || "")) {
      await previewMemberRef.delete();
    }
  }

  return db.runTransaction(async (tx) => {
    const inviteRef = db.collection("group_invites").doc(token);
    const inviteSnap = await tx.get(inviteRef);
    if (!inviteSnap.exists) {
      throw new Error("INVITE_NOT_FOUND");
    }
    const data = inviteSnap.data();
    const memberRef = db.collection("groups").doc(data.groupId).collection("members").doc(uid);
    const memberSnap = await tx.get(memberRef);
    if (memberSnap.exists && memberSnap.data().groupGenerationId === data.groupGenerationId) {
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
      ...(data.groupGenerationId ? { groupGenerationId: data.groupGenerationId } : {}),
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
  await assertSucceeds(
    aliceDb.collection("outings").doc("alice_outing_1").update({
      rsvpByUser: { [ALICE_UID]: "MAYBE" },
      rsvpYesCount: 0,
      rsvpMaybeCount: 1,
      myRsvp: "MAYBE",
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );
  await assertSucceeds(aliceDb.collection("outings").doc("alice_outing_1").delete());
});

test("Group outing RSVP is private to the current group, per-user writable, and never stored on the parent", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupId = HIGH_ENTROPY_GROUP_ID;
  const generationId = await createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice");
  await assertSucceeds(createInviteDoc(aliceDb, {
    token: TOKEN_MULTI, groupId, creatorUid: ALICE_UID, maxUses: 3,
  }));
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));

  const outingId = "group_rsvp_outing";
  const outingRef = aliceDb.collection("outings").doc(outingId);
  await assertSucceeds(outingRef.set(validOutingPayload(ALICE_UID, groupId, new Date(), generationId)));
  await assertFails(outingRef.delete());
  assert.equal((await assertSucceeds(outingRef.get())).exists, true);
  const aliceRsvpRef = outingRef.collection("rsvps").doc(ALICE_UID);
  await assertSucceeds(aliceRsvpRef.set(validGroupOutingRsvpPayload({
    outingId, userId: ALICE_UID, groupId, generationId, choice: "YES",
  })));

  // A current v2 member can read another current member's response by its exact UID path.
  const bobReadsAlice = await assertSucceeds(
    bobDb.collection("outings").doc(outingId).collection("rsvps").doc(ALICE_UID).get()
  );
  assert.equal(bobReadsAlice.data().choice, "YES");
  const aliceReadsBobUnanswered = await assertSucceeds(
    aliceDb.collection("outings").doc(outingId).collection("rsvps").doc(BOB_UID).get()
  );
  assert.equal(aliceReadsBobUnanswered.exists, false);
  await assertFails(
    bobDb.collection("outings").doc(outingId).collection("rsvps").get()
  );

  // Bob can create, change, and remove only his own RSVP document.
  const bobRsvpRef = bobDb.collection("outings").doc(outingId).collection("rsvps").doc(BOB_UID);
  await assertSucceeds(bobRsvpRef.set(validGroupOutingRsvpPayload({
    outingId, userId: BOB_UID, groupId, generationId, choice: "MAYBE",
  })));
  await assertSucceeds(bobRsvpRef.update({
    choice: "NO",
    updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
  }));
  await assertFails(aliceDb.collection("outings").doc(outingId).collection("rsvps").doc(BOB_UID).delete());
  await assertSucceeds(bobRsvpRef.delete());

  await assertSucceeds(bobRsvpRef.set(validGroupOutingRsvpPayload({
    outingId, userId: BOB_UID, groupId, generationId, choice: "YES",
  })));
  await assertSucceeds(
    aliceDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).delete()
  );
  await assertFails(aliceDb.collection("outings").doc(outingId).collection("rsvps").doc(BOB_UID).get());
  await assertFails(bobRsvpRef.get());

  // The outing owner may edit outing details, but cannot forge even their own group RSVP counts.
  await assertSucceeds(outingRef.update({
    title: "Cena actualizada",
    updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
  }));
  await assertFails(outingRef.update({ rsvpYesCount: 99, updatedAt: firebase.firestore.FieldValue.serverTimestamp() }));
  await assertFails(outingRef.update({ myRsvp: "NO", updatedAt: firebase.firestore.FieldValue.serverTimestamp() }));
  await assertFails(outingRef.update({ rsvpByUser: { [ALICE_UID]: "YES" }, updatedAt: firebase.firestore.FieldValue.serverTimestamp() }));
});

test("Group outing RSVP rejects non-members, cross-user paths, stale generations, and malformed payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const groupId = HIGH_ENTROPY_GROUP_ID;
  const generationId = await createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice");
  const outingId = "group_rsvp_adversarial";
  const outingRef = aliceDb.collection("outings").doc(outingId);
  await assertSucceeds(outingRef.set(validOutingPayload(ALICE_UID, groupId, new Date(), generationId)));
  const aliceRsvpRef = outingRef.collection("rsvps").doc(ALICE_UID);
  await assertSucceeds(aliceRsvpRef.set(validGroupOutingRsvpPayload({
    outingId, userId: ALICE_UID, groupId, generationId,
  })));

  await assertFails(eveDb.collection("outings").doc(outingId).collection("rsvps").doc(ALICE_UID).get());
  await assertFails(eveDb.collection("outings").doc(outingId).collection("rsvps").doc(EVE_UID).get());
  await assertFails(
    eveDb.collection("outings").doc(outingId).collection("rsvps").get()
  );
  await assertFails(
    eveDb.collection("outings").doc(outingId).collection("rsvps").doc(EVE_UID).set(
      validGroupOutingRsvpPayload({ outingId, userId: EVE_UID, groupId, generationId })
    )
  );

  const wrongPathRef = outingRef.collection("rsvps").doc(BOB_UID);
  await assertFails(wrongPathRef.set(validGroupOutingRsvpPayload({
    outingId, userId: BOB_UID, groupId, generationId,
  })));

  const wrongGeneration = crypto.randomBytes(16).toString("hex");
  await assertFails(aliceRsvpRef.set(validGroupOutingRsvpPayload({
    outingId, userId: ALICE_UID, groupId, generationId: wrongGeneration,
  })));
  await assertFails(aliceRsvpRef.set(validGroupOutingRsvpPayload({
    outingId: "different_outing", userId: ALICE_UID, groupId, generationId,
  })));
  await assertFails(aliceRsvpRef.set(validGroupOutingRsvpPayload({
    outingId, userId: ALICE_UID, groupId: "other_v2_group", generationId,
  })));
  await assertFails(aliceRsvpRef.set(validGroupOutingRsvpPayload({
    outingId, userId: ALICE_UID, groupId, generationId, choice: "ATTENDING",
  })));
  await assertFails(aliceRsvpRef.set({
    ...validGroupOutingRsvpPayload({ outingId, userId: ALICE_UID, groupId, generationId }),
    admin: true,
  }));
  const missingUserId = validGroupOutingRsvpPayload({ outingId, userId: ALICE_UID, groupId, generationId });
  delete missingUserId.userId;
  await assertFails(aliceRsvpRef.set(missingUserId));
});

test("Direct RSVP reads reject orphaned and path-inconsistent documents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const groupId = HIGH_ENTROPY_GROUP_ID;
  const generationId = await createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice");
  const outingId = "group_rsvp_path_audit";
  const outingRef = aliceDb.collection("outings").doc(outingId);
  await assertSucceeds(outingRef.set(validOutingPayload(ALICE_UID, groupId, new Date(), generationId)));

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = new Date();
    await db.collection("outings").doc(outingId).collection("rsvps").doc(ALICE_UID).set(
      validGroupOutingRsvpPayload({ outingId, userId: ALICE_UID, groupId, generationId })
    );
    // Data claims a valid outing, but the document actually lives under another parent path.
    await db.collection("outings").doc("wrong_rsvp_parent").collection("rsvps").doc(BOB_UID).set({
      outingId,
      userId: BOB_UID,
      groupId,
      groupGenerationId: generationId,
      choice: "YES",
      createdAt: now,
      updatedAt: now,
    });
    // Correct path shape, but there is no parent outing document to authorize.
    await db.collection("outings").doc("missing_rsvp_parent").collection("rsvps").doc(CAROL_UID).set({
      outingId: "missing_rsvp_parent",
      userId: CAROL_UID,
      groupId,
      groupGenerationId: generationId,
      choice: "MAYBE",
      createdAt: now,
      updatedAt: now,
    });
    // Parent exists, but the UID in the document does not match the RSVP document ID.
    await db.collection("outings").doc(outingId).collection("rsvps").doc(BOB_UID).set({
      outingId,
      userId: ALICE_UID,
      groupId,
      groupGenerationId: generationId,
      choice: "NO",
      createdAt: now,
      updatedAt: now,
    });
  });

  const validAliceRsvpRef = outingRef.collection("rsvps").doc(ALICE_UID);
  await assertSucceeds(validAliceRsvpRef.get());
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("outings").doc(outingId).collection("rsvps").doc(ALICE_UID)
      .update({ privateNote: "must-not-be-readable" });
  });
  await assertFails(validAliceRsvpRef.get());
  await assertFails(outingRef.collection("rsvps").doc(BOB_UID).get());
  await assertFails(outingRef.collection("rsvps").get());
  await assertFails(
    aliceDb.collection("outings").doc("wrong_rsvp_parent").collection("rsvps").doc(BOB_UID).get()
  );
  await assertFails(
    aliceDb.collection("outings").doc("missing_rsvp_parent").collection("rsvps").doc(CAROL_UID).get()
  );
});

test("Concurrent RSVP transactions preserve each member's response and serialize same-user changes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupId = HIGH_ENTROPY_GROUP_ID;
  const generationId = await createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice");
  await assertSucceeds(createInviteDoc(aliceDb, {
    token: TOKEN_MULTI, groupId, creatorUid: ALICE_UID, maxUses: 2,
  }));
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));

  const outingId = "group_rsvp_concurrent";
  await assertSucceeds(aliceDb.collection("outings").doc(outingId).set(
    validOutingPayload(ALICE_UID, groupId, new Date(), generationId)
  ));

  await Promise.all([
    writeGroupOutingRsvpTransactionally(aliceDb, {
      outingId, userId: ALICE_UID, groupId, generationId, choice: "YES",
    }),
    writeGroupOutingRsvpTransactionally(bobDb, {
      outingId, userId: BOB_UID, groupId, generationId, choice: "MAYBE",
    }),
  ]);

  const aliceUpdates = await Promise.allSettled([
    writeGroupOutingRsvpTransactionally(aliceDb, {
      outingId, userId: ALICE_UID, groupId, generationId, choice: "NO",
    }),
    writeGroupOutingRsvpTransactionally(aliceDb, {
      outingId, userId: ALICE_UID, groupId, generationId, choice: "MAYBE",
    }),
  ]);
  assert.equal(aliceUpdates.filter((result) => result.status === "fulfilled").length, 2);

  const aliceFinal = await assertSucceeds(
    aliceDb.collection("outings").doc(outingId).collection("rsvps").doc(ALICE_UID).get()
  );
  const bobFinal = await assertSucceeds(
    aliceDb.collection("outings").doc(outingId).collection("rsvps").doc(BOB_UID).get()
  );
  assert.ok(["NO", "MAYBE"].includes(aliceFinal.data().choice));
  assert.equal(bobFinal.data().choice, "MAYBE");
  await assertFails(aliceDb.collection("outings").doc(outingId).collection("rsvps").get());
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
      groupGenerationId: groupGenerationIds.get(HIGH_ENTROPY_GROUP_ID),
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

test("Group owners can remove members/admins; admins can remove members only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const daveDb = testEnv.authenticatedContext(DAVE_UID).firestore();

  await assertSucceeds(createV2GroupAtomically(aliceDb, HIGH_ENTROPY_GROUP_ID, ALICE_UID, "Alice"));
  await assertSucceeds(createInviteDoc(aliceDb, {
    token: TOKEN_MULTI, groupId: HIGH_ENTROPY_GROUP_ID, creatorUid: ALICE_UID, maxUses: 4,
  }));
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));
  await assertSucceeds(redeemInviteTransactionally(carolDb, TOKEN_MULTI, CAROL_UID, "Carol"));
  await assertSucceeds(redeemInviteTransactionally(eveDb, TOKEN_MULTI, EVE_UID, "Eve"));
  await assertSucceeds(redeemInviteTransactionally(daveDb, TOKEN_MULTI, DAVE_UID, "Dave"));

  for (const uid of [BOB_UID, EVE_UID]) {
    await assertSucceeds(
      aliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(uid).update({
        role: "ADMIN", updatedAt: new Date(),
      })
    );
  }

  const memberRef = (db, uid) => db.collection("groups").doc(HIGH_ENTROPY_GROUP_ID)
    .collection("members").doc(uid);
  await assertFails(memberRef(bobDb, EVE_UID).delete()); // admin cannot remove another admin
  await assertFails(memberRef(bobDb, ALICE_UID).delete()); // admin cannot remove owner
  await assertSucceeds(memberRef(bobDb, CAROL_UID).delete()); // admin may remove member
  await assertSucceeds(memberRef(aliceDb, DAVE_UID).delete()); // owner may remove member
  await assertSucceeds(memberRef(aliceDb, EVE_UID).delete()); // owner may remove admin
  await assertSucceeds(memberRef(aliceDb, BOB_UID).delete()); // owner may remove admin
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

  const membersSnap = await aliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID)
    .collection("members")
    .where("groupGenerationId", "==", groupGenerationIds.get(HIGH_ENTROPY_GROUP_ID))
    .get();
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
  const oldGenerationId = crypto.randomBytes(16).toString("hex");
  const oldCreatedAt = new Date(Date.now() - 120_000);

  // Model a complete, already-orphaned old subtree without deleting application data.
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      groupId: HIGH_ENTROPY_GROUP_ID,
      groupGenerationId: oldGenerationId,
      displayName: "Alice antigua",
      role: "OWNER",
      inviteToken: "",
      joinedAt: oldCreatedAt,
      updatedAt: oldCreatedAt,
    });
    await db.collection("group_invites").doc(staleToken).set({
      token: staleToken,
      groupId: HIGH_ENTROPY_GROUP_ID,
      groupGenerationId: oldGenerationId,
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
      validOutingPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, oldCreatedAt, oldGenerationId)
    );
    await db.collection("outings").doc("stale_third_party_outing").set(
      validOutingPayload(BOB_UID, HIGH_ENTROPY_GROUP_ID, oldCreatedAt, oldGenerationId)
    );
    await db.collection("chat_messages").doc("stale_group_chat").set(
      validChatPayload(ALICE_UID, HIGH_ENTROPY_GROUP_ID, oldCreatedAt, oldGenerationId)
    );
    await db.collection("stories").doc("stale_group_story").set(
      validStoryPayload(ALICE_UID, oldCreatedAt, HIGH_ENTROPY_GROUP_ID, oldGenerationId)
    );
    await db.collection("group_posts").doc("stale_group_post").set(
      validGroupPost(ALICE_UID, "Alice antigua", HIGH_ENTROPY_GROUP_ID, oldCreatedAt, "Post antiguo", oldGenerationId)
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
  await assertFails(oldAliceDb.collection("outings").doc("stale_group_outing").delete());
  await assertFails(oldAliceDb.collection("outings").doc("stale_third_party_outing").get());
  const oldOwnMember = await assertSucceeds(
    oldAliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).get()
  );
  assert.equal(oldOwnMember.data().groupGenerationId, oldGenerationId);
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
  await assertFails(freshBobDb.collection("chat_messages").doc("stale_group_chat").get());
  await assertFails(freshBobDb.collection("stories").doc("stale_group_story").get());
  await assertFails(freshBobDb.collection("group_posts").doc("stale_group_post").get());
  await assertFails(
    freshBobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).get()
  );
  await assertFails(
    freshBobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).delete()
  );

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

  const currentGenerationId = groupGenerationIds.get(HIGH_ENTROPY_GROUP_ID);
  await assertSucceeds(
    freshBobDb.collection("chat_messages").doc("fresh_group_chat").set(
      validChatPayload(BOB_UID, HIGH_ENTROPY_GROUP_ID, new Date())
    )
  );
  await assertSucceeds(
    freshBobDb.collection("stories").doc("fresh_group_story").set(
      validStoryPayload(BOB_UID, new Date(), HIGH_ENTROPY_GROUP_ID)
    )
  );
  await assertSucceeds(
    freshBobDb.collection("group_posts").doc("fresh_group_post").set(
      validGroupPost(BOB_UID, "Bob nuevo", HIGH_ENTROPY_GROUP_ID, new Date())
    )
  );

  const currentMembers = await assertSucceeds(
    freshBobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members")
      .where("groupGenerationId", "==", currentGenerationId)
      .get()
  );
  assert.deepEqual(currentMembers.docs.map((doc) => doc.id).sort(), [BOB_UID, EVE_UID].sort());
  await assertFails(
    freshBobDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members")
      .where("groupId", "==", HIGH_ENTROPY_GROUP_ID)
      .get()
  );

  for (const collection of ["outings", "chat_messages", "stories", "group_posts"]) {
    const scoped = await assertSucceeds(
      freshBobDb.collection(collection)
        .where("groupId", "==", HIGH_ENTROPY_GROUP_ID)
        .where("groupGenerationId", "==", currentGenerationId)
        .get()
    );
    assert.equal(scoped.size, 1, `${collection} should return only the current generation`);
    await assertFails(
      freshBobDb.collection(collection).where("groupId", "==", HIGH_ENTROPY_GROUP_ID).get()
    );
  }

  const currentInvites = await assertSucceeds(
    newOwnerDb.collection("group_invites")
      .where("groupId", "==", HIGH_ENTROPY_GROUP_ID)
      .where("groupGenerationId", "==", currentGenerationId)
      .get()
  );
  assert.equal(currentInvites.size, 1);
  await assertFails(
    newOwnerDb.collection("group_invites").where("groupId", "==", HIGH_ENTROPY_GROUP_ID).get()
  );

  // Personal owner/sender/author queries remain usable and do not need a generation filter.
  const aliceOwnOutings = await assertSucceeds(
    oldAliceDb.collection("outings").where("ownerId", "==", ALICE_UID).get()
  );
  assert.equal(aliceOwnOutings.size, 1);
  const aliceOwnChats = await assertSucceeds(
    oldAliceDb.collection("chat_messages").where("senderId", "==", ALICE_UID).get()
  );
  assert.equal(aliceOwnChats.size, 1);
  const aliceOwnStories = await assertSucceeds(
    oldAliceDb.collection("stories").where("authorId", "==", ALICE_UID).get()
  );
  assert.equal(aliceOwnStories.size, 1);

  // An old member may rejoin, but only by consuming an invite for the new generation.
  const rejoinToken = "INV-R3J4O5I6-N7M8E9M0-B1E2R3S4-H5A6R7E8";
  await assertSucceeds(createInviteDoc(newOwnerDb, {
    token: rejoinToken,
    groupId: HIGH_ENTROPY_GROUP_ID,
    creatorUid: EVE_UID,
    maxUses: 3,
  }));
  await assertSucceeds(redeemInviteTransactionally(oldAliceDb, rejoinToken, ALICE_UID, "Alice nueva"));
  const rejoinedMember = await assertSucceeds(
    oldAliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).collection("members").doc(ALICE_UID).get()
  );
  assert.equal(rejoinedMember.data().groupGenerationId, currentGenerationId);
  assert.equal(rejoinedMember.data().role, "MEMBER");
  await assertSucceeds(oldAliceDb.collection("groups").doc(HIGH_ENTROPY_GROUP_ID).get());
});

test("Legacy group (QUEDA-2026): preserves existing members & outings, closes self-join door, supports reversible backfill & v2 invites", async () => {
  const now = new Date();
  const generationId = crypto.randomBytes(16).toString("hex");
  groupGenerationIds.set("QUEDA-2026", generationId);
  // Seed legacy v1 group & outing as they already exist in production
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("groups_id_registry").doc("QUEDA-2026").set({
      groupId: "QUEDA-2026",
      createdBy: ALICE_UID,
      schemaVersion: 2,
      generationId,
      createdAt: now,
    });
    await db.collection("groups").doc("QUEDA-2026").set({
      name: "Grupo Legacy",
      inviteCode: "QUEDA-2026",
      createdBy: ALICE_UID,
      generationId,
      memberIds: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdAt: now,
      updatedAt: now,
    });
    await db
      .collection("outings")
      .doc("legacy_outing_1")
      .set(validOutingPayload(ALICE_UID, "QUEDA-2026", now, generationId));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  // 1. Existing legacy member Bob keeps reading and querying group outings with ZERO disruption
  await assertSucceeds(bobDb.collection("groups").doc("QUEDA-2026").get());
  await assertSucceeds(bobDb.collection("outings").doc("legacy_outing_1").get());
  const legacyOutings = await assertSucceeds(
    bobDb.collection("outings")
      .where("groupId", "==", "QUEDA-2026")
      .where("groupGenerationId", "==", generationId)
      .get()
  );
  assert.equal(legacyOutings.size, 1);
  await assertFails(
    bobDb.collection("outings").doc("legacy_outing_1").collection("rsvps").doc(BOB_UID).set(
      validGroupOutingRsvpPayload({
        outingId: "legacy_outing_1", userId: BOB_UID, groupId: "QUEDA-2026", generationId,
      })
    )
  );
  await assertFails(
    aliceDb.collection("outings").doc("legacy_outing_1").collection("rsvps").doc(ALICE_UID).set(
      validGroupOutingRsvpPayload({
        outingId: "legacy_outing_1", userId: ALICE_UID, groupId: "QUEDA-2026", generationId,
      })
    )
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
      groupGenerationId: generationId,
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
      groupGenerationId: generationId,
      displayName: "Bob",
      role: "MEMBER",
      inviteToken: "",
      joinedAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("outings").doc("legacy_outing_1").collection("rsvps").doc(BOB_UID).set(
      validGroupOutingRsvpPayload({
        outingId: "legacy_outing_1", userId: BOB_UID, groupId: "QUEDA-2026", generationId,
      })
    )
  );

  // 4. Legacy owner Alice issues a new high-entropy v2 invite for QUEDA-2026 -> Carol joins & reads legacy outing!
  await assertSucceeds(
    createInviteDoc(aliceDb, { token: TOKEN_LEGACY_NEW, groupId: "QUEDA-2026", creatorUid: ALICE_UID, maxUses: 3 })
  );
  await assertSucceeds(redeemInviteTransactionally(carolDb, TOKEN_LEGACY_NEW, CAROL_UID, "Carol"));
  await assertSucceeds(carolDb.collection("groups").doc("QUEDA-2026").get());
  await assertSucceeds(carolDb.collection("outings").doc("legacy_outing_1").get());
  await assertSucceeds(
    carolDb.collection("outings").doc("legacy_outing_1").collection("rsvps").doc(CAROL_UID).set(
      validGroupOutingRsvpPayload({
        outingId: "legacy_outing_1", userId: CAROL_UID, groupId: "QUEDA-2026", generationId,
      })
    )
  );
  const carolOutings = await assertSucceeds(
    carolDb.collection("outings")
      .where("groupId", "==", "QUEDA-2026")
      .where("groupGenerationId", "==", generationId)
      .get()
  );
  assert.equal(carolOutings.size, 1);
});

test("Older v2 groups without generationId or registry fail closed until a trusted migration", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupId = "v2_grp_OLDER8P4R6T8V3W5X7Y9Z2A4B6";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("groups").doc(groupId).set({
      name: "Grupo v2 anterior",
      createdBy: ALICE_UID,
      schemaVersion: 2,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("groups").doc(groupId).collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      groupId,
      displayName: "Alice",
      role: "OWNER",
      inviteToken: "",
      joinedAt: now,
      updatedAt: now,
    });
  });

  await assertFails(aliceDb.collection("groups").doc(groupId).get());
  await assertFails(aliceDb.collection("groups").doc(groupId).collection("members").get());
  await assertFails(aliceDb.collection("groups").doc(groupId).update({ name: "Grupo actualizado", updatedAt: new Date() }));
  await assertFails(createInviteDoc(aliceDb, {
    token: TOKEN_LEGACY_NEW,
    groupId,
    creatorUid: ALICE_UID,
  }));
  await assertFails(redeemInviteTransactionally(bobDb, TOKEN_LEGACY_NEW, BOB_UID, "Bob"));
  await assertFails(bobDb.collection("groups").doc(groupId).get());
  await assertFails(
    bobDb.collection("groups").doc(groupId).collection("members")
      .where("groupId", "==", groupId)
      .get()
  );

  const outingRef = aliceDb.collection("outings").doc("older_v2_outing");
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("outings").doc("older_v2_outing").set(
      validOutingPayload(ALICE_UID, groupId, new Date())
    );
  });
  await assertFails(outingRef.get());
  await assertFails(bobDb.collection("outings").where("groupId", "==", groupId).get());
});

test("Generationless descendants stay inaccessible after the same groupId is recreated", async () => {
  const groupId = "v2_grp_RECREATED7P4R6T8V3W5X7Y9Z2A4B6";
  const staleInvite = "INV-O1L2D3G4-E5N6R7O8-U9P0N1O2-S3T4A5L6";
  const oldCreatedAt = new Date(Date.now() - 120_000);
  const newCreatedAt = new Date();
  const generationId = crypto.randomBytes(16).toString("hex");
  const oldOwnerDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const newOwnerDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const newMemberDb = testEnv.authenticatedContext(BOB_UID).firestore();
  groupGenerationIds.delete(groupId);

  // Preserve old children, remove the unsealed parent, and model a trusted migration
  // recreating the same ID with a new registry generation and OWNER membership.
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("groups").doc(groupId).set({
      name: "Grupo anterior", createdBy: ALICE_UID, schemaVersion: 2,
      createdAt: oldCreatedAt, updatedAt: oldCreatedAt,
    });
    for (const [uid, role] of [[ALICE_UID, "OWNER"], [BOB_UID, "MEMBER"]]) {
      await db.collection("groups").doc(groupId).collection("members").doc(uid).set({
        userId: uid, groupId, displayName: "Antiguo", role, inviteToken: "",
        joinedAt: oldCreatedAt, updatedAt: oldCreatedAt,
      });
    }
    await db.collection("group_invites").doc(staleInvite).set({
      token: staleInvite, groupId, groupNamePreview: "Grupo anterior", createdBy: ALICE_UID,
      revoked: false, expiresAt: new Date(Date.now() + 3_600_000), maxUses: 4,
      useCount: 0, lastRedeemedBy: "", createdAt: oldCreatedAt, updatedAt: oldCreatedAt,
    });
    await db.collection("outings").doc("generationless_old_outing").set(
      validOutingPayload(ALICE_UID, groupId, oldCreatedAt)
    );
    await db.collection("chat_messages").doc("generationless_old_chat").set(
      validChatPayload(ALICE_UID, groupId, oldCreatedAt)
    );
    await db.collection("stories").doc("generationless_old_story").set(
      validStoryPayload(ALICE_UID, oldCreatedAt, groupId)
    );
    await db.collection("group_posts").doc("generationless_old_post").set(
      validGroupPost(ALICE_UID, "Antiguo", groupId, oldCreatedAt, "Post viejo")
    );
    await db.collection("groups").doc(groupId).delete();
    await db.collection("groups_id_registry").doc(groupId).set({
      groupId, createdBy: EVE_UID, schemaVersion: 2, generationId, createdAt: newCreatedAt,
    });
    await db.collection("groups").doc(groupId).set({
      name: "Grupo nuevo", createdBy: EVE_UID, schemaVersion: 2,
      generationId, createdAt: newCreatedAt, updatedAt: newCreatedAt,
    });
    await db.collection("groups").doc(groupId).collection("members").doc(EVE_UID).set({
      userId: EVE_UID, groupId, groupGenerationId: generationId, displayName: "Eve",
      role: "OWNER", inviteToken: "", joinedAt: newCreatedAt, updatedAt: newCreatedAt,
    });
  });
  groupGenerationIds.set(groupId, generationId);

  await assertFails(oldOwnerDb.collection("groups").doc(groupId).get());
  const newGroup = await assertSucceeds(newOwnerDb.collection("groups").doc(groupId).get());
  assert.equal(newGroup.data().generationId, generationId);
  await assertFails(newMemberDb.collection("group_invites").doc(staleInvite).get());
  await assertFails(newMemberDb.collection("outings").doc("generationless_old_outing").get());
  await assertFails(newMemberDb.collection("chat_messages").doc("generationless_old_chat").get());
  await assertFails(newMemberDb.collection("stories").doc("generationless_old_story").get());
  await assertFails(newMemberDb.collection("group_posts").doc("generationless_old_post").get());

  const freshInvite = "INV-N3E4W5G6-E7N8E9R0-A1T2I3O4-N5B6O7B8";
  await assertSucceeds(createInviteDoc(newOwnerDb, {
    token: freshInvite, groupId, creatorUid: EVE_UID, maxUses: 2,
  }));
  await assertSucceeds(redeemInviteTransactionally(newMemberDb, freshInvite, BOB_UID, "Bob nuevo"));
  const currentMemberIds = await assertSucceeds(
    newMemberDb.collection("groups").doc(groupId).collection("members")
      .where("groupGenerationId", "==", generationId).get()
  );
  assert.deepEqual(currentMemberIds.docs.map((doc) => doc.id).sort(), [BOB_UID, EVE_UID].sort());
  await assertFails(
    newMemberDb.collection("groups").doc(groupId).collection("members")
      .where("groupId", "==", groupId).get()
  );

  await assertSucceeds(newMemberDb.collection("outings").doc("fresh_generation_outing").set(
    validOutingPayload(BOB_UID, groupId, new Date())
  ));
  await assertSucceeds(newMemberDb.collection("chat_messages").doc("fresh_generation_chat").set(
    validChatPayload(BOB_UID, groupId, new Date())
  ));
  await assertSucceeds(newMemberDb.collection("stories").doc("fresh_generation_story").set(
    validStoryPayload(BOB_UID, new Date(), groupId)
  ));
  await assertSucceeds(newMemberDb.collection("group_posts").doc("fresh_generation_post").set(
    validGroupPost(BOB_UID, "Bob nuevo", groupId, new Date())
  ));

  for (const collection of ["outings", "chat_messages", "stories", "group_posts"]) {
    const freshDocs = await assertSucceeds(
      newMemberDb.collection(collection)
        .where("groupId", "==", groupId)
        .where("groupGenerationId", "==", generationId)
        .get()
    );
    assert.equal(freshDocs.size, 1, `${collection} must return only current-generation data`);
    await assertFails(newMemberDb.collection(collection).where("groupId", "==", groupId).get());
  }

  const freshInvites = await assertSucceeds(
    newOwnerDb.collection("group_invites")
      .where("groupId", "==", groupId)
      .where("groupGenerationId", "==", generationId)
      .get()
  );
  assert.equal(freshInvites.size, 1);
  await assertFails(newOwnerDb.collection("group_invites").where("groupId", "==", groupId).get());
});

// --- ALTO-4 v2 namespace + immutable registry adversarial tests ---
test("ALTO-4: v2 creation requires one atomic registry + group + OWNER batch; legacy IDs fail closed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const v2Id = "v2_grp_ATOMIC7P4R6T8V3W5X7Y9Z2A4B6";
  const legacyId = "QUEDA-9999";
  const now = new Date();
  const generationId = crypto.randomBytes(16).toString("hex");
  const groupPayload = {
    name: "Atómico", createdBy: ALICE_UID, schemaVersion: 2,
    generationId,
    createdAt: now, updatedAt: now,
  };
  const ownerPayload = {
    userId: ALICE_UID, groupId: v2Id, groupGenerationId: generationId,
    displayName: "Alice", role: "OWNER",
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
    groupId: v2Id, createdBy: ALICE_UID, schemaVersion: 2, generationId, createdAt: now,
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

test("Group outings cannot move between groups and require current membership for edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const groupId = "v2_grp_FEED7P4R6T8V3W5X7Y9Z2A4B6";
  const otherGroupId = "v2_grp_PRIVATE7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice"));
  await assertSucceeds(createV2GroupAtomically(carolDb, otherGroupId, CAROL_UID, "Carol"));
  await assertSucceeds(createInviteDoc(aliceDb, {
    token: TOKEN_MULTI,
    groupId,
    creatorUid: ALICE_UID,
    maxUses: 5,
  }));
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));

  const outingRef = bobDb.collection("outings").doc("bob_group_outing");
  await assertSucceeds(outingRef.set(validOutingPayload(BOB_UID, groupId, new Date())));
  await assertSucceeds(outingRef.update({ title: "Actualizada", updatedAt: new Date() }));
  const generationId = groupGenerationIds.get(groupId);
  await assertFails(outingRef.update({
    groupGenerationId: crypto.randomBytes(16).toString("hex"),
    updatedAt: new Date(),
  }));
  await assertFails(outingRef.update({
    groupGenerationId: firebase.firestore.FieldValue.delete(),
    updatedAt: new Date(),
  }));
  const outingAfterRejectedGenerationChanges = await assertSucceeds(outingRef.get());
  assert.equal(outingAfterRejectedGenerationChanges.data().groupGenerationId, generationId);
  await assertFails(outingRef.update({ groupId: otherGroupId, updatedAt: new Date() }));

  await assertSucceeds(bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).delete());
  await assertFails(outingRef.update({ title: "Sin membresía", updatedAt: new Date() }));
  await assertFails(outingRef.get());
  await assertFails(outingRef.delete());
});

test("Group outing queries are available to current members, but never to non-members", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const groupId = "v2_grp_OUTING7P4R6T8V3W5X7Y9Z2A4B6";
  const otherGroupId = "v2_grp_PRIVATE7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice"));
  await assertSucceeds(createV2GroupAtomically(carolDb, otherGroupId, CAROL_UID, "Carol"));
  await assertSucceeds(createInviteDoc(aliceDb, {
    token: TOKEN_MULTI,
    groupId,
    creatorUid: ALICE_UID,
    maxUses: 5,
  }));
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));

  const outingRef = bobDb.collection("outings").doc("bob_group_outing");
  await assertSucceeds(outingRef.set(validOutingPayload(BOB_UID, groupId, new Date())));
  const generationId = groupGenerationIds.get(groupId);
  const memberOutings = await assertSucceeds(
    aliceDb.collection("outings")
      .where("groupId", "==", groupId)
      .where("groupGenerationId", "==", generationId)
      .get()
  );
  assert.equal(memberOutings.size, 1);
  assert.equal(memberOutings.docs[0].data().ownerId, BOB_UID);
  await assertFails(
    aliceDb.collection("outings").where("groupId", "==", groupId).get()
  );
  await assertFails(
    eveDb.collection("outings")
      .where("groupId", "==", groupId)
      .where("groupGenerationId", "==", generationId)
      .get()
  );
  const otherGenerationId = groupGenerationIds.get(otherGroupId);
  await assertFails(
    aliceDb.collection("outings")
      .where("groupId", "==", otherGroupId)
      .where("groupGenerationId", "==", otherGenerationId)
      .get()
  );
});

test("Chat messages keep their group, and group stories differ from personal stories", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupId = "v2_grp_NAMECHECK7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice"));
  await assertSucceeds(createInviteDoc(aliceDb, {
    token: TOKEN_MULTI,
    groupId,
    creatorUid: ALICE_UID,
    maxUses: 5,
  }));
  await assertSucceeds(redeemInviteTransactionally(bobDb, TOKEN_MULTI, BOB_UID, "Bob"));

  const now = new Date();
  const chatRef = bobDb.collection("chat_messages").doc("bob_group_chat");
  await assertSucceeds(chatRef.set(validChatPayload(BOB_UID, groupId, now)));
  await assertSucceeds(chatRef.update({ text: "Editado", updatedAt: new Date() }));
  await assertFails(chatRef.update({ groupId: "v2_grp_WRONGSEC7P4R6T8V3W5X7Y9Z2A4B6", updatedAt: new Date() }));
  await assertFails(chatRef.update({ senderId: ALICE_UID, updatedAt: new Date() }));
  await assertFails(chatRef.update({ createdAt: new Date(), updatedAt: new Date() }));

  const groupStoryRef = bobDb.collection("stories").doc("bob_group_story");
  await assertSucceeds(groupStoryRef.set(validStoryPayload(BOB_UID, new Date(), groupId)));
  await assertFails(groupStoryRef.update({ caption: "Edición no permitida", updatedAt: new Date() }));
  await assertSucceeds(aliceDb.collection("stories").doc("bob_group_story").update({ likesCount: 1, updatedAt: new Date() }));
  const generationId = groupGenerationIds.get(groupId);
  const groupMessages = await assertSucceeds(
    aliceDb.collection("chat_messages")
      .where("groupId", "==", groupId)
      .where("groupGenerationId", "==", generationId)
      .get()
  );
  assert.equal(groupMessages.size, 1);
  await assertFails(aliceDb.collection("chat_messages").get());
  const groupStories = await assertSucceeds(
    aliceDb.collection("stories")
      .where("groupId", "==", groupId)
      .where("groupGenerationId", "==", generationId)
      .get()
  );
  assert.equal(groupStories.size, 1);
  await assertFails(aliceDb.collection("stories").get());
  await assertFails(
    aliceDb.collection("chat_messages").where("groupId", "==", groupId).get()
  );
  await assertFails(
    aliceDb.collection("stories").where("groupId", "==", groupId).get()
  );
  await assertSucceeds(bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).delete());
  await assertFails(groupStoryRef.update({ likesCount: 2, updatedAt: new Date() }));

  const personalChatRef = bobDb.collection("chat_messages").doc("bob_personal_chat");
  const personalChatPayload = validChatPayload(BOB_UID, "personal_ignored", new Date());
  delete personalChatPayload.groupId;
  await assertSucceeds(personalChatRef.set(personalChatPayload));
  await assertSucceeds(personalChatRef.update({ text: "Mensaje personal", updatedAt: new Date() }));
  await assertSucceeds(bobDb.collection("chat_messages").where("senderId", "==", BOB_UID).get());
  await assertFails(aliceDb.collection("chat_messages").doc("bob_personal_chat").get());

  const personalStoryRef = bobDb.collection("stories").doc("bob_personal_story");
  await assertSucceeds(personalStoryRef.set(validStoryPayload(BOB_UID, new Date())));
  await assertSucceeds(personalStoryRef.update({ caption: "Historia personal", updatedAt: new Date() }));
  await assertFails(aliceDb.collection("stories").doc("bob_personal_story").get());
});

function validGroupPost(authorId, authorName, groupId, ts, text = "Mensaje privado", generationId = groupGenerationIds.get(groupId)) {
  const post = {
    authorId,
    authorName,
    groupId,
    text,
    createdAt: ts,
    updatedAt: ts,
  };
  if (generationId) post.groupGenerationId = generationId;
  return post;
}

test("Private group feed: v2 member can create, query, read and delete own post", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const groupId = "v2_grp_FEED7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice"));

  const postId = "post_alice_001";
  const postRef = aliceDb.collection("group_posts").doc(postId);
  await assertSucceeds(postRef.set(validGroupPost(ALICE_UID, "Alice", groupId, new Date())));
  await assertSucceeds(postRef.get());
  const generationId = groupGenerationIds.get(groupId);
  const groupPosts = await assertSucceeds(
    aliceDb.collection("group_posts")
      .where("groupId", "==", groupId)
      .where("groupGenerationId", "==", generationId)
      .get()
  );
  assert.equal(groupPosts.size, 1);
  await assertFails(aliceDb.collection("group_posts").where("groupId", "==", groupId).get());
  await assertFails(aliceDb.collection("group_posts").get());
  await assertFails(postRef.update({ text: "Editado", updatedAt: new Date() }));
  await assertSucceeds(postRef.delete());
});

test("Private group feed: non-member cannot read, query, create or delete posts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const groupId = "v2_grp_PRIVATE7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice"));
  const postRef = aliceDb.collection("group_posts").doc("post_private_001");
  await assertSucceeds(postRef.set(validGroupPost(ALICE_UID, "Alice", groupId, new Date())));

  await assertFails(eveDb.collection("group_posts").doc("post_private_001").get());
  await assertFails(eveDb.collection("group_posts").where("groupId", "==", groupId).get());
  await assertFails(
    eveDb.collection("group_posts").doc("post_eve_001").set(
      validGroupPost(EVE_UID, "Eve", groupId, new Date())
    )
  );
  await assertFails(eveDb.collection("group_posts").doc("post_private_001").delete());
});

test("Private group feed: legacy member arrays alone do not grant feed access or allow name spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const groupId = "LEGACY-FEED-001";
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("groups").doc(groupId).set({
      name: "Legacy",
      inviteCode: "LEGACY-FEED-001",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertFails(
    aliceDb.collection("group_posts").doc("legacy_post").set(
      validGroupPost(ALICE_UID, "Alice", groupId, new Date())
    )
  );

  const v2GroupId = "v2_grp_NAMECHECK7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, v2GroupId, ALICE_UID, "Alice"));
  await assertFails(
    aliceDb.collection("group_posts").doc("post_spoofed_name").set(
      validGroupPost(ALICE_UID, "Bob", v2GroupId, new Date())
    )
  );
});

test("Private group feed: queries stay in-group and fence stale generations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const groupId = "v2_grp_FEED7P4R6T8V3W5X7Y9Z2A4B6";
  const otherGroupId = "v2_grp_PRIVATE7P4R6T8V3W5X7Y9Z2A4B6";
  await assertSucceeds(createV2GroupAtomically(aliceDb, groupId, ALICE_UID, "Alice"));
  await assertSucceeds(createV2GroupAtomically(carolDb, otherGroupId, CAROL_UID, "Carol"));
  const groupStart = (await aliceDb.collection("groups").doc(groupId).get()).data().createdAt;
  const groupGenerationId = groupGenerationIds.get(groupId);
  const otherGroupGenerationId = groupGenerationIds.get(otherGroupId);

  await assertSucceeds(
    aliceDb.collection("group_posts").doc("post_alice_fresh")
      .set(validGroupPost(ALICE_UID, "Alice", groupId, new Date()))
  );
  await assertSucceeds(
    carolDb.collection("group_posts").doc("post_carol_other")
      .set(validGroupPost(CAROL_UID, "Carol", otherGroupId, new Date()))
  );

  const ownPosts = await assertSucceeds(
    aliceDb.collection("group_posts")
      .where("groupId", "==", groupId)
      .where("groupGenerationId", "==", groupGenerationId)
      .get()
  );
  assert.equal(ownPosts.size, 1);
  await assertFails(
    aliceDb.collection("group_posts")
      .where("groupId", "==", otherGroupId)
      .where("groupGenerationId", "==", otherGroupGenerationId)
      .get()
  );
  await assertFails(aliceDb.collection("group_posts").get());
  await assertFails(aliceDb.collection("group_posts").where("groupId", "==", groupId).get());

  const staleCreatedAt = new Date(groupStart.toMillis() - 60_000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("group_posts").doc("post_stale_generation").set(
      validGroupPost(ALICE_UID, "Alice", groupId, staleCreatedAt, "Mensaje antiguo", "0".repeat(32))
    );
  });
  await assertFails(aliceDb.collection("group_posts").doc("post_stale_generation").get());
  await assertFails(aliceDb.collection("group_posts").doc("post_stale_generation").delete());
  const currentPosts = await assertSucceeds(
    aliceDb.collection("group_posts")
      .where("groupId", "==", groupId)
      .where("groupGenerationId", "==", groupGenerationId)
      .get()
  );
  assert.equal(currentPosts.size, 1);

  await assertFails(
    aliceDb.collection("group_posts").doc("post_forged_stale_timestamp").set(
      validGroupPost(ALICE_UID, "Alice", groupId, staleCreatedAt, "Mensaje antiguo", groupGenerationId)
    )
  );
});
