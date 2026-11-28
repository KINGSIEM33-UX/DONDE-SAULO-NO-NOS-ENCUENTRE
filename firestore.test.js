const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");
const path = require("node:path");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync(path.join(__dirname, "firestore.rules"), "utf8");
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

function pastTs() {
  return new Date(Date.now() - 1000);
}

function sha256HexLower(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

function personalGroupId(uid) {
  const isDirect =
    typeof uid === "string" &&
    uid.length >= 1 &&
    uid.length <= 119 &&
    /^[a-zA-Z0-9_-]+$/.test(uid) &&
    !uid.startsWith("sha256_");
  return isDirect ? `personal_${uid}` : `personal_sha256_${sha256HexLower(uid)}`;
}

async function seedGroup(groupId, memberIds, createdBy = ALICE_UID, ts = pastTs()) {
  const memberNames = {};
  for (const uid of memberIds) {
    memberNames[uid] = `User_${uid}`;
  }
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc(groupId).set({
      name: `Grupo ${groupId}`,
      inviteCode: groupId,
      createdBy,
      memberIds,
      memberNames,
      createdAt: ts,
      updatedAt: ts,
    });
  });
}

function validOutingPayload(ownerId, ts, groupId = "GRUPO-2026") {
  const payload = {
    ownerId,
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
  if (groupId !== null) {
    payload.groupId = groupId;
  }
  return payload;
}

function validChatMessagePayload(senderId, ts, groupId = "GRUPO-2026") {
  const payload = {
    senderId,
    senderName: "Alice",
    text: "¡Nos vemos a las 20:30!",
    messageType: "TEXT",
    audioDurationSec: 0,
    transcription: "",
    sharedOutingId: "",
    sharedOutingTitle: "",
    reactions: [],
    createdAt: ts,
    updatedAt: ts,
  };
  if (groupId !== null) {
    payload.groupId = groupId;
  }
  return payload;
}

function validStoryPayload(authorId, ts, groupId = "GRUPO-2026") {
  const payload = {
    authorId,
    authorName: "Alice",
    caption: "Momento de la salida",
    filterStyle: "NEON_VIBES",
    mediaUri: "story_sunset",
    outingTitle: "Cena y Bolera",
    likesCount: 1,
    createdAt: ts,
    updatedAt: ts,
  };
  if (groupId !== null) {
    payload.groupId = groupId;
  }
  return payload;
}

// --- EXISTING TESTS (PRESERVED) ---

test("Unauthenticated user: cannot read outings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("outings").get());
});

test("Authenticated user: cannot read another user's outing", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("outings")
      .doc("bob_outing_1")
      .set(validOutingPayload(BOB_UID, pastTs()));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("outings").doc("bob_outing_1").get());
});

test("Authenticated user: can create and query their own outings with ownerId filter", async () => {
  await seedGroup("GRUPO-2026", [ALICE_UID], ALICE_UID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTs();
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("alice_outing_1")
      .set(validOutingPayload(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb.collection("outings").where("ownerId", "==", ALICE_UID).get()
  );
});

test("Authenticated user: fails query without ownerId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("outings").get());
});

test("Shadow update test: rejects ghost field injection on outings", async () => {
  await seedGroup("GRUPO-2026", [ALICE_UID], ALICE_UID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTs();
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("alice_outing_2")
      .set(validOutingPayload(ALICE_UID, now))
  );

  await assertFails(
    aliceDb.collection("outings").doc("alice_outing_2").update({
      title: "Cena Editada",
      isAdminHack: true,
      updatedAt: pastTs(),
    })
  );
});

test("Multi-user group: member can read and update shared group outing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTs();

  await assertSucceeds(
    aliceDb.collection("groups").doc("GRUPO-2026").set({
      name: "Grupo Test",
      inviteCode: "GRUPO-2026",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob joins the group using the invite code
  await assertSucceeds(
    bobDb.collection("groups").doc("GRUPO-2026").update({
      memberIds: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("shared_outing_1")
      .set(validOutingPayload(ALICE_UID, now))
  );

  // Bob can now read the shared group outing
  await assertSucceeds(
    bobDb.collection("outings").doc("shared_outing_1").get()
  );
});

// --- NEW ADVERSARIAL & POSITIVE TESTS: SHARED GROUP, PERSONAL SCOPE, PRIVACY, MIGRATIONS ---

test("Unauthenticated user: cannot create outings, chat_messages, or stories (group, personal, or private)", async () => {
  await seedGroup("GRUPO-2026", [ALICE_UID], ALICE_UID);
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = pastTs();

  await assertFails(
    unauthDb
      .collection("outings")
      .doc("unauth_outing")
      .set(validOutingPayload(ALICE_UID, ts, "GRUPO-2026"))
  );
  await assertFails(
    unauthDb
      .collection("outings")
      .doc("unauth_personal_outing")
      .set(validOutingPayload(ALICE_UID, ts, personalGroupId(ALICE_UID)))
  );
  await assertFails(
    unauthDb
      .collection("chat_messages")
      .doc("unauth_msg")
      .set(validChatMessagePayload(ALICE_UID, ts, "GRUPO-2026"))
  );
  await assertFails(
    unauthDb
      .collection("chat_messages")
      .doc("unauth_personal_msg")
      .set(validChatMessagePayload(ALICE_UID, ts, personalGroupId(ALICE_UID)))
  );
  await assertFails(
    unauthDb
      .collection("stories")
      .doc("unauth_group_story")
      .set(validStoryPayload(ALICE_UID, ts, "GRUPO-2026"))
  );
  await assertFails(
    unauthDb
      .collection("stories")
      .doc("unauth_personal_story")
      .set(validStoryPayload(ALICE_UID, ts, personalGroupId(ALICE_UID)))
  );
  await assertFails(
    unauthDb
      .collection("stories")
      .doc("unauth_private_story")
      .set(validStoryPayload(ALICE_UID, ts, null))
  );
});

test("Outings create (shared group): member succeeds; non-member, missing/empty groupId, and non-existent group fail", async () => {
  await seedGroup("GRUPO-2026", [ALICE_UID, BOB_UID], ALICE_UID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastTs();

  await assertSucceeds(
    bobDb
      .collection("outings")
      .doc("bob_valid_outing")
      .set(validOutingPayload(BOB_UID, ts, "GRUPO-2026"))
  );

  await assertFails(
    charlieDb
      .collection("outings")
      .doc("charlie_inject_outing")
      .set(validOutingPayload(CHARLIE_UID, ts, "GRUPO-2026"))
  );

  await assertFails(
    bobDb
      .collection("outings")
      .doc("bob_ghost_group_outing")
      .set(validOutingPayload(BOB_UID, ts, "GRUPO-INEXISTENTE"))
  );

  await assertFails(
    bobDb
      .collection("outings")
      .doc("bob_missing_group_outing")
      .set(validOutingPayload(BOB_UID, ts, null))
  );
  await assertFails(
    bobDb
      .collection("outings")
      .doc("bob_empty_group_outing")
      .set(validOutingPayload(BOB_UID, ts, ""))
  );
});

test("Chat messages create (shared group): member succeeds; non-member, missing/empty groupId, and non-existent group fail", async () => {
  await seedGroup("GRUPO-2026", [ALICE_UID, BOB_UID], ALICE_UID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastTs();

  await assertSucceeds(
    bobDb
      .collection("chat_messages")
      .doc("bob_valid_msg")
      .set(validChatMessagePayload(BOB_UID, ts, "GRUPO-2026"))
  );

  await assertFails(
    charlieDb
      .collection("chat_messages")
      .doc("charlie_inject_msg")
      .set(validChatMessagePayload(CHARLIE_UID, ts, "GRUPO-2026"))
  );

  await assertFails(
    bobDb
      .collection("chat_messages")
      .doc("bob_ghost_group_msg")
      .set(validChatMessagePayload(BOB_UID, ts, "GRUPO-INEXISTENTE"))
  );

  await assertFails(
    bobDb
      .collection("chat_messages")
      .doc("bob_missing_group_msg")
      .set(validChatMessagePayload(BOB_UID, ts, null))
  );
  await assertFails(
    bobDb
      .collection("chat_messages")
      .doc("bob_empty_group_msg")
      .set(validChatMessagePayload(BOB_UID, ts, ""))
  );
});

test("Stories create (shared group): member succeeds; non-member, non-existent group, whitespace groupId, and author spoof fail", async () => {
  await seedGroup("GRUPO-2026", [ALICE_UID, BOB_UID], ALICE_UID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastTs();

  await assertSucceeds(
    bobDb
      .collection("stories")
      .doc("bob_valid_group_story")
      .set(validStoryPayload(BOB_UID, ts, "GRUPO-2026"))
  );

  await assertFails(
    charlieDb
      .collection("stories")
      .doc("charlie_inject_story")
      .set(validStoryPayload(CHARLIE_UID, ts, "GRUPO-2026"))
  );

  await assertFails(
    bobDb
      .collection("stories")
      .doc("bob_ghost_group_story")
      .set(validStoryPayload(BOB_UID, ts, "GRUPO-INEXISTENTE"))
  );

  await assertFails(
    bobDb
      .collection("stories")
      .doc("bob_whitespace_group_story")
      .set(validStoryPayload(BOB_UID, ts, "   "))
  );

  await assertFails(
    bobDb
      .collection("stories")
      .doc("bob_spoof_story")
      .set(validStoryPayload(ALICE_UID, ts, "GRUPO-2026"))
  );
});

test("Stories create (legacy private without group): author succeeds with omitted or empty groupId; non-author cannot read, query, or spoof", async () => {
  await seedGroup("GRUPO-2026", [ALICE_UID, BOB_UID], ALICE_UID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastTs();

  await assertSucceeds(
    charlieDb
      .collection("stories")
      .doc("charlie_private_omitted")
      .set(validStoryPayload(CHARLIE_UID, ts, null))
  );
  await assertSucceeds(
    charlieDb
      .collection("stories")
      .doc("charlie_private_empty")
      .set(validStoryPayload(CHARLIE_UID, ts, ""))
  );

  await assertSucceeds(
    charlieDb.collection("stories").doc("charlie_private_omitted").get()
  );
  await assertSucceeds(
    charlieDb.collection("stories").doc("charlie_private_empty").get()
  );
  await assertSucceeds(
    charlieDb.collection("stories").where("authorId", "==", CHARLIE_UID).get()
  );

  await assertFails(
    aliceDb.collection("stories").doc("charlie_private_omitted").get()
  );
  await assertFails(
    aliceDb.collection("stories").doc("charlie_private_empty").get()
  );
  await assertFails(
    aliceDb.collection("stories").where("authorId", "==", CHARLIE_UID).get()
  );

  await assertFails(
    bobDb
      .collection("stories")
      .doc("bob_spoof_private")
      .set(validStoryPayload(ALICE_UID, ts, null))
  );
});

test("Personal scope (personal_<uid>): owner without group can create, read, query by owner/sender/author, update, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceScope = personalGroupId(ALICE_UID);
  const ts = pastTs();

  // Create without any /groups/{groupId} document existing
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("alice_personal_outing")
      .set(validOutingPayload(ALICE_UID, ts, aliceScope))
  );
  await assertSucceeds(
    aliceDb
      .collection("chat_messages")
      .doc("alice_personal_msg")
      .set(validChatMessagePayload(ALICE_UID, ts, aliceScope))
  );
  await assertSucceeds(
    aliceDb
      .collection("stories")
      .doc("alice_personal_story")
      .set(validStoryPayload(ALICE_UID, ts, aliceScope))
  );

  // Direct get
  await assertSucceeds(aliceDb.collection("outings").doc("alice_personal_outing").get());
  await assertSucceeds(aliceDb.collection("chat_messages").doc("alice_personal_msg").get());
  await assertSucceeds(aliceDb.collection("stories").doc("alice_personal_story").get());

  // Owner-scoped queries (single filter and combined owner + personal groupId filter)
  await assertSucceeds(
    aliceDb.collection("outings").where("ownerId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .where("ownerId", "==", ALICE_UID)
      .where("groupId", "==", aliceScope)
      .get()
  );
  await assertSucceeds(
    aliceDb.collection("chat_messages").where("senderId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb
      .collection("chat_messages")
      .where("senderId", "==", ALICE_UID)
      .where("groupId", "==", aliceScope)
      .get()
  );
  await assertSucceeds(
    aliceDb.collection("stories").where("authorId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb
      .collection("stories")
      .where("authorId", "==", ALICE_UID)
      .where("groupId", "==", aliceScope)
      .get()
  );

  // Owner updates allowed fields
  await assertSucceeds(
    aliceDb.collection("outings").doc("alice_personal_outing").update({
      title: "Salida Personal Editada",
      updatedAt: pastTs(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chat_messages").doc("alice_personal_msg").update({
      text: "Nota personal actualizada",
      reactions: ["👍"],
      updatedAt: pastTs(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("alice_personal_story").update({
      caption: "Historia personal editada",
      likesCount: 2,
      updatedAt: pastTs(),
    })
  );

  // Owner deletes personal docs
  await assertSucceeds(aliceDb.collection("outings").doc("alice_personal_outing").delete());
  await assertSucceeds(aliceDb.collection("chat_messages").doc("alice_personal_msg").delete());
  await assertSucceeds(aliceDb.collection("stories").doc("alice_personal_story").delete());
});

test("Personal scope adversarial (Alice vs Bob): Bob cannot inject, spoof uid, read, query, or update Alice's personal_<uid> records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceScope = personalGroupId(ALICE_UID);
  const ts = pastTs();

  // Alice creates her personal records
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("alice_p_outing")
      .set(validOutingPayload(ALICE_UID, ts, aliceScope))
  );
  await assertSucceeds(
    aliceDb
      .collection("chat_messages")
      .doc("alice_p_msg")
      .set(validChatMessagePayload(ALICE_UID, ts, aliceScope))
  );
  await assertSucceeds(
    aliceDb
      .collection("stories")
      .doc("alice_p_story")
      .set(validStoryPayload(ALICE_UID, ts, aliceScope))
  );

  // Adversarial: Bob cannot create docs in Alice's personal scope (with own UID or spoofed ALICE_UID)
  await assertFails(
    bobDb
      .collection("outings")
      .doc("bob_in_alice_personal_outing")
      .set(validOutingPayload(BOB_UID, ts, aliceScope))
  );
  await assertFails(
    bobDb
      .collection("outings")
      .doc("bob_spoof_alice_personal_outing")
      .set(validOutingPayload(ALICE_UID, ts, aliceScope))
  );
  await assertFails(
    bobDb
      .collection("chat_messages")
      .doc("bob_in_alice_personal_msg")
      .set(validChatMessagePayload(BOB_UID, ts, aliceScope))
  );
  await assertFails(
    bobDb
      .collection("chat_messages")
      .doc("bob_spoof_alice_personal_msg")
      .set(validChatMessagePayload(ALICE_UID, ts, aliceScope))
  );
  await assertFails(
    bobDb
      .collection("stories")
      .doc("bob_in_alice_personal_story")
      .set(validStoryPayload(BOB_UID, ts, aliceScope))
  );
  await assertFails(
    bobDb
      .collection("stories")
      .doc("bob_spoof_alice_personal_story")
      .set(validStoryPayload(ALICE_UID, ts, aliceScope))
  );

  // Adversarial: malformed personal prefixes rejected even for owner
  await assertFails(
    aliceDb
      .collection("outings")
      .doc("alice_bad_prefix_1")
      .set(validOutingPayload(ALICE_UID, ts, "personal_"))
  );
  await assertFails(
    aliceDb
      .collection("chat_messages")
      .doc("alice_bad_prefix_2")
      .set(validChatMessagePayload(ALICE_UID, ts, `${aliceScope}_extra`))
  );
  await assertFails(
    aliceDb
      .collection("stories")
      .doc("alice_bad_prefix_3")
      .set(validStoryPayload(ALICE_UID, ts, "personal_"))
  );

  // Adversarial: Bob cannot get, query, update, or delete Alice's personal records
  await assertFails(bobDb.collection("outings").doc("alice_p_outing").get());
  await assertFails(bobDb.collection("chat_messages").doc("alice_p_msg").get());
  await assertFails(bobDb.collection("stories").doc("alice_p_story").get());

  await assertFails(
    bobDb.collection("outings").where("ownerId", "==", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("outings").where("groupId", "==", aliceScope).get()
  );
  await assertFails(
    bobDb.collection("chat_messages").where("senderId", "==", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("chat_messages").where("groupId", "==", aliceScope).get()
  );
  await assertFails(
    bobDb.collection("stories").where("authorId", "==", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("stories").where("groupId", "==", aliceScope).get()
  );

  await assertFails(
    bobDb.collection("outings").doc("alice_p_outing").update({
      title: "Hack",
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("chat_messages").doc("alice_p_msg").update({
      reactions: ["💀"],
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("alice_p_story").update({
      likesCount: 99,
      updatedAt: pastTs(),
    })
  );
  await assertFails(bobDb.collection("outings").doc("alice_p_outing").delete());
  await assertFails(bobDb.collection("chat_messages").doc("alice_p_msg").delete());
  await assertFails(bobDb.collection("stories").doc("alice_p_story").delete());
});

test("Personal scope squatting defense: creating /groups/personal_<aliceUid> does NOT grant Bob access to Alice's personal records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceScope = personalGroupId(ALICE_UID);
  const ts = pastTs();

  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("alice_squat_outing")
      .set(validOutingPayload(ALICE_UID, ts, aliceScope))
  );
  await assertSucceeds(
    aliceDb
      .collection("chat_messages")
      .doc("alice_squat_msg")
      .set(validChatMessagePayload(ALICE_UID, ts, aliceScope))
  );
  await assertSucceeds(
    aliceDb
      .collection("stories")
      .doc("alice_squat_story")
      .set(validStoryPayload(ALICE_UID, ts, aliceScope))
  );

  // Simulate attacker Bob squatting /groups/personal_alice_123
  await seedGroup(aliceScope, [BOB_UID], BOB_UID, ts);

  // Even with /groups/personal_alice_123 existing and containing BOB_UID, isGroupMember rejects personal_*
  await assertFails(bobDb.collection("outings").doc("alice_squat_outing").get());
  await assertFails(bobDb.collection("chat_messages").doc("alice_squat_msg").get());
  await assertFails(bobDb.collection("stories").doc("alice_squat_story").get());
  await assertFails(
    bobDb.collection("outings").where("groupId", "==", aliceScope).get()
  );
  await assertFails(
    bobDb.collection("chat_messages").where("groupId", "==", aliceScope).get()
  );
  await assertFails(
    bobDb.collection("stories").where("groupId", "==", aliceScope).get()
  );
  await assertFails(
    bobDb
      .collection("outings")
      .doc("bob_squat_inject_outing")
      .set(validOutingPayload(BOB_UID, ts, aliceScope))
  );
  await assertFails(
    bobDb
      .collection("chat_messages")
      .doc("bob_squat_inject_msg")
      .set(validChatMessagePayload(BOB_UID, ts, aliceScope))
  );
  await assertFails(
    bobDb
      .collection("stories")
      .doc("bob_squat_inject_story")
      .set(validStoryPayload(BOB_UID, ts, aliceScope))
  );
  await assertFails(
    bobDb.collection("outings").doc("alice_squat_outing").update({
      title: "Hijacked",
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("chat_messages").doc("alice_squat_msg").update({
      reactions: ["🔥"],
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("alice_squat_story").update({
      likesCount: 5,
      updatedAt: pastTs(),
    })
  );
});

test("Migrations & group re-tagging bypass prevention: owner can migrate own outing to own group/personal; foreign re-tagging and chat/story groupId changes fail", async () => {
  await seedGroup("GRUPO-2026", [ALICE_UID, BOB_UID], ALICE_UID);
  await seedGroup("BOB-ONLY-GRP", [BOB_UID], BOB_UID);
  await seedGroup("CHARLIE-GRP", [CHARLIE_UID], CHARLIE_UID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastTs();

  // Alice creates a personal outing, personal message, and personal story
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("alice_migrate_outing")
      .set(validOutingPayload(ALICE_UID, ts, personalGroupId(ALICE_UID)))
  );
  await assertSucceeds(
    aliceDb
      .collection("chat_messages")
      .doc("alice_personal_retag_msg")
      .set(validChatMessagePayload(ALICE_UID, ts, personalGroupId(ALICE_UID)))
  );
  await assertSucceeds(
    aliceDb
      .collection("stories")
      .doc("alice_personal_retag_story")
      .set(validStoryPayload(ALICE_UID, ts, personalGroupId(ALICE_UID)))
  );

  // Positive: Alice migrates her personal outing into GRUPO-2026 (where she is a member)
  await assertSucceeds(
    aliceDb.collection("outings").doc("alice_migrate_outing").update({
      groupId: "GRUPO-2026",
      updatedAt: pastTs(),
    })
  );

  // Adversarial: Bob (member of GRUPO-2026, not owner) CANNOT hijack Alice's outing into Bob's personal scope or BOB-ONLY-GRP
  await assertFails(
    bobDb.collection("outings").doc("alice_migrate_outing").update({
      groupId: personalGroupId(BOB_UID),
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("outings").doc("alice_migrate_outing").update({
      groupId: "BOB-ONLY-GRP",
      updatedAt: pastTs(),
    })
  );

  // Positive: Bob CAN still update non-groupId fields on Alice's shared outing in GRUPO-2026
  await assertSucceeds(
    bobDb.collection("outings").doc("alice_migrate_outing").update({
      rsvpYesCount: 2,
      updatedAt: pastTs(),
    })
  );

  // Positive: Alice (owner) can move her outing back to her own personal scope
  await assertSucceeds(
    aliceDb.collection("outings").doc("alice_migrate_outing").update({
      groupId: personalGroupId(ALICE_UID),
      updatedAt: pastTs(),
    })
  );

  // Adversarial: Alice cannot re-tag her outing into BOB-ONLY-GRP, Bob's personal scope, or a non-existent group
  await assertFails(
    aliceDb.collection("outings").doc("alice_migrate_outing").update({
      groupId: "BOB-ONLY-GRP",
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    aliceDb.collection("outings").doc("alice_migrate_outing").update({
      groupId: personalGroupId(BOB_UID),
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    aliceDb.collection("outings").doc("alice_migrate_outing").update({
      groupId: "GRUPO-INEXISTENTE",
      updatedAt: pastTs(),
    })
  );

  // Adversarial: chat_messages and stories groupId is strictly immutable on update
  await assertFails(
    aliceDb.collection("chat_messages").doc("alice_personal_retag_msg").update({
      groupId: "GRUPO-2026",
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    aliceDb.collection("stories").doc("alice_personal_retag_story").update({
      groupId: "GRUPO-2026",
      updatedAt: pastTs(),
    })
  );

  // Adversarial: Charlie cannot create in CHARLIE-GRP or private story and re-tag into GRUPO-2026 or Alice's personal scope
  await assertSucceeds(
    charlieDb
      .collection("outings")
      .doc("charlie_own_outing")
      .set(validOutingPayload(CHARLIE_UID, ts, "CHARLIE-GRP"))
  );
  await assertSucceeds(
    charlieDb
      .collection("stories")
      .doc("charlie_private_for_retag")
      .set(validStoryPayload(CHARLIE_UID, ts, ""))
  );
  await assertFails(
    charlieDb.collection("outings").doc("charlie_own_outing").update({
      groupId: "GRUPO-2026",
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    charlieDb.collection("outings").doc("charlie_own_outing").update({
      groupId: personalGroupId(ALICE_UID),
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    charlieDb.collection("stories").doc("charlie_private_for_retag").update({
      groupId: "GRUPO-2026",
      updatedAt: pastTs(),
    })
  );

  // Positive: owner of a legacy outing can migrate it into GRUPO-2026 or own personal scope
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("outings")
      .doc("alice_legacy_migrate")
      .set(validOutingPayload(ALICE_UID, ts, "LEGACY-OLD"));
  });
  await assertSucceeds(
    aliceDb.collection("outings").doc("alice_legacy_migrate").update({
      groupId: "GRUPO-2026",
      updatedAt: pastTs(),
    })
  );
});

test("Legacy records compatibility: owner of pre-existing records without group doc can still read, query, update, and delete", async () => {
  const ts = pastTs();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("outings")
      .doc("legacy_outing_1")
      .set(validOutingPayload(ALICE_UID, ts, "LEGACY-NO-DOC"));
    await db
      .collection("chat_messages")
      .doc("legacy_msg_1")
      .set(validChatMessagePayload(ALICE_UID, ts, "LEGACY-NO-DOC"));
    await db
      .collection("stories")
      .doc("legacy_story_1")
      .set(validStoryPayload(ALICE_UID, ts, "LEGACY-NO-DOC"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("outings").doc("legacy_outing_1").get());
  await assertSucceeds(aliceDb.collection("outings").where("ownerId", "==", ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("outings").doc("legacy_outing_1").update({
      title: "Salida Legacy Editada",
      updatedAt: pastTs(),
    })
  );
  await assertSucceeds(aliceDb.collection("chat_messages").doc("legacy_msg_1").get());
  await assertSucceeds(aliceDb.collection("chat_messages").where("senderId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("stories").doc("legacy_story_1").get());
  await assertSucceeds(aliceDb.collection("stories").where("authorId", "==", ALICE_UID).get());

  await assertFails(bobDb.collection("outings").doc("legacy_outing_1").get());
  await assertFails(bobDb.collection("chat_messages").doc("legacy_msg_1").get());
  await assertFails(bobDb.collection("stories").doc("legacy_story_1").get());

  await assertSucceeds(aliceDb.collection("outings").doc("legacy_outing_1").delete());
  await assertSucceeds(aliceDb.collection("chat_messages").doc("legacy_msg_1").delete());
  await assertSucceeds(aliceDb.collection("stories").doc("legacy_story_1").delete());
});

test("Personal scope (long & special/UTF-8 UIDs): 119-char boundary uses direct; 120/128-char, special-char, and UTF-8 UIDs use personal_sha256_<hex>", async () => {
  const uid119 = "a".repeat(119);
  const uid120 = "a".repeat(120);
  const uid128 = "u".repeat(128);
  const specialUid = "google-oauth2|alice.smith@example.com:42/test";
  const utf8Uid = "josé_🚀@example.com";
  const ts = pastTs();

  // 119-char valid ASCII UID: direct scope (128 chars total) succeeds; hashed form rejected
  const db119 = testEnv.authenticatedContext(uid119).firestore();
  await assertSucceeds(
    db119
      .collection("outings")
      .doc("o_119_direct")
      .set(validOutingPayload(uid119, ts, `personal_${uid119}`))
  );
  await assertFails(
    db119
      .collection("outings")
      .doc("o_119_hashed_wrong")
      .set(validOutingPayload(uid119, ts, `personal_sha256_${sha256HexLower(uid119)}`))
  );

  // 120-char and 128-char UIDs: hashed scope succeeds across all 3 collections + owner queries + update
  for (const [label, uid] of [
    ["120", uid120],
    ["128", uid128],
    ["special", specialUid],
    ["utf8", utf8Uid],
  ]) {
    const db = testEnv.authenticatedContext(uid).firestore();
    const expectedScope = `personal_sha256_${sha256HexLower(uid)}`;

    await assertSucceeds(
      db
        .collection("outings")
        .doc(`o_${label}`)
        .set(validOutingPayload(uid, ts, expectedScope))
    );
    await assertSucceeds(
      db
        .collection("chat_messages")
        .doc(`m_${label}`)
        .set(validChatMessagePayload(uid, ts, expectedScope))
    );
    await assertSucceeds(
      db
        .collection("stories")
        .doc(`s_${label}`)
        .set(validStoryPayload(uid, ts, expectedScope))
    );

    // Real owner-scoped queries (single and combined with hashed groupId)
    await assertSucceeds(db.collection("outings").where("ownerId", "==", uid).get());
    await assertSucceeds(
      db
        .collection("outings")
        .where("ownerId", "==", uid)
        .where("groupId", "==", expectedScope)
        .get()
    );
    await assertSucceeds(
      db
        .collection("chat_messages")
        .where("senderId", "==", uid)
        .where("groupId", "==", expectedScope)
        .get()
    );
    await assertSucceeds(
      db
        .collection("stories")
        .where("authorId", "==", uid)
        .where("groupId", "==", expectedScope)
        .get()
    );

    // Unprefixed old fallback (personal_<sha256>) is rejected on new creates
    await assertFails(
      db
        .collection("outings")
        .doc(`o_${label}_old_unprefixed`)
        .set(validOutingPayload(uid, ts, `personal_${sha256HexLower(uid)}`))
    );
  }

  // Special UID raw personal_<specialUid> is rejected on create
  const specialDb = testEnv.authenticatedContext(specialUid).firestore();
  await assertFails(
    specialDb
      .collection("outings")
      .doc("o_special_raw_rejected")
      .set(validOutingPayload(specialUid, ts, `personal_${specialUid}`))
  );
});

test("Personal scope collision & squatting defense (hashed UIDs): disjoint namespaces block hex-UID collisions, sha256_-prefixed UIDs, and /groups/personal_sha256_* squatting", async () => {
  const specialAlice = "alice.special@example.com";
  const aliceHash = sha256HexLower(specialAlice);
  const aliceHashedScope = `personal_sha256_${aliceHash}`;

  // Attacker 1: UID literally equal to Alice's 64-char SHA-256 hex digest
  const hexBob = aliceHash;
  // Attacker 2: UID literally equal to "sha256_" + Alice's hash (would collide if direct allowed sha256_*)
  const prefixCharlie = `sha256_${aliceHash}`;

  const aliceDb = testEnv.authenticatedContext(specialAlice).firestore();
  const hexBobDb = testEnv.authenticatedContext(hexBob).firestore();
  const prefixCharlieDb = testEnv.authenticatedContext(prefixCharlie).firestore();
  const ts = pastTs();

  // Alice creates in personal_sha256_<aliceHash>
  await assertSucceeds(
    aliceDb
      .collection("outings")
      .doc("alice_hashed_outing")
      .set(validOutingPayload(specialAlice, ts, aliceHashedScope))
  );
  await assertSucceeds(
    aliceDb
      .collection("chat_messages")
      .doc("alice_hashed_msg")
      .set(validChatMessagePayload(specialAlice, ts, aliceHashedScope))
  );
  await assertSucceeds(
    aliceDb
      .collection("stories")
      .doc("alice_hashed_story")
      .set(validStoryPayload(specialAlice, ts, aliceHashedScope))
  );

  // HexBob's own scope is personal_<aliceHash> (disjoint from personal_sha256_<aliceHash>)
  await assertSucceeds(
    hexBobDb
      .collection("outings")
      .doc("hexbob_own_outing")
      .set(validOutingPayload(hexBob, ts, `personal_${hexBob}`))
  );
  // HexBob CANNOT touch Alice's hashed scope
  await assertFails(
    hexBobDb
      .collection("outings")
      .doc("hexbob_collide_alice")
      .set(validOutingPayload(hexBob, ts, aliceHashedScope))
  );
  await assertFails(hexBobDb.collection("outings").doc("alice_hashed_outing").get());

  // PrefixCharlie (uid="sha256_<aliceHash>") is forced to hashed branch and CANNOT claim personal_sha256_<aliceHash>
  await assertFails(
    prefixCharlieDb
      .collection("outings")
      .doc("charlie_prefix_collide")
      .set(validOutingPayload(prefixCharlie, ts, aliceHashedScope))
  );
  await assertSucceeds(
    prefixCharlieDb
      .collection("outings")
      .doc("charlie_prefix_own_hashed")
      .set(validOutingPayload(prefixCharlie, ts, personalGroupId(prefixCharlie)))
  );
  await assertFails(prefixCharlieDb.collection("outings").doc("alice_hashed_outing").get());

  // Squatting /groups/personal_sha256_<aliceHash> with HexBob in memberIds still denies all access
  await seedGroup(aliceHashedScope, [hexBob], hexBob, ts);
  await assertFails(hexBobDb.collection("outings").doc("alice_hashed_outing").get());
  await assertFails(hexBobDb.collection("chat_messages").doc("alice_hashed_msg").get());
  await assertFails(hexBobDb.collection("stories").doc("alice_hashed_story").get());
  await assertFails(
    hexBobDb.collection("outings").where("groupId", "==", aliceHashedScope).get()
  );
  await assertFails(
    hexBobDb
      .collection("outings")
      .doc("hexbob_squat_hashed")
      .set(validOutingPayload(hexBob, ts, aliceHashedScope))
  );
});

test("Legacy fallback compatibility: pre-existing docs created with old personal_<sha256> remain accessible to owner and migratable on outings without exposing to third parties", async () => {
  const specialAlice = "alice.legacy@example.com";
  const oldFallbackGroupId = `personal_${sha256HexLower(specialAlice)}`;
  const newCanonicalScope = personalGroupId(specialAlice);
  const ts = pastTs();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("outings")
      .doc("legacy_sha_outing")
      .set(validOutingPayload(specialAlice, ts, oldFallbackGroupId));
    await db
      .collection("chat_messages")
      .doc("legacy_sha_msg")
      .set(validChatMessagePayload(specialAlice, ts, oldFallbackGroupId));
    await db
      .collection("stories")
      .doc("legacy_sha_story")
      .set(validStoryPayload(specialAlice, ts, oldFallbackGroupId));
  });

  const aliceDb = testEnv.authenticatedContext(specialAlice).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Owner retains get, owner-query, update, migration to canonical hashed scope, and delete
  await assertSucceeds(aliceDb.collection("outings").doc("legacy_sha_outing").get());
  await assertSucceeds(aliceDb.collection("outings").where("ownerId", "==", specialAlice).get());
  await assertSucceeds(aliceDb.collection("chat_messages").where("senderId", "==", specialAlice).get());
  await assertSucceeds(aliceDb.collection("stories").where("authorId", "==", specialAlice).get());
  await assertSucceeds(
    aliceDb.collection("outings").doc("legacy_sha_outing").update({
      groupId: newCanonicalScope,
      updatedAt: pastTs(),
    })
  );

  // Third party denied even for old fallback records
  await assertFails(bobDb.collection("outings").doc("legacy_sha_outing").get());
  await assertFails(bobDb.collection("chat_messages").doc("legacy_sha_msg").get());
  await assertFails(bobDb.collection("stories").doc("legacy_sha_story").get());
});
