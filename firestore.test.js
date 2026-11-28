const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function validOutingPayload(ownerId, ts) {
  return {
    ownerId,
    groupId: "GRUPO-2026",
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
      .set(validOutingPayload(BOB_UID, new Date()));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("outings").doc("bob_outing_1").get());
});

test("Authenticated user: can create and query their own outings with ownerId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
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
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
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
      updatedAt: new Date(),
    })
  );
});

test("Multi-user group: member can read and update shared group outing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

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

function validGroupPost(authorId, groupId, ts, text = "Mensaje privado") {
  return {
    authorId,
    authorName: authorId === ALICE_UID ? "Alice" : "Bob",
    groupId,
    text,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Group feed: member can query only the selected group", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("groups").doc("FEED-GROUP").set({
      name: "Feed Test",
      inviteCode: "FEED-GROUP",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_posts").doc("post_1").set(
      validGroupPost(ALICE_UID, "FEED-GROUP", now)
    )
  );
  await assertSucceeds(
    aliceDb.collection("group_posts").where("groupId", "==", "FEED-GROUP").get()
  );
  await assertFails(aliceDb.collection("group_posts").get());
});

test("Group feed: non-member cannot read, create, or delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("groups").doc("FEED-PRIVATE").set({
      name: "Private Feed",
      inviteCode: "FEED-PRIVATE",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_posts").doc("post_private").set(
      validGroupPost(ALICE_UID, "FEED-PRIVATE", now)
    )
  );
  await assertFails(
    bobDb.collection("group_posts").where("groupId", "==", "FEED-PRIVATE").get()
  );
  await assertFails(
    bobDb.collection("group_posts").doc("post_private").delete()
  );
  await assertFails(
    bobDb.collection("group_posts").doc("post_bob").set(
      validGroupPost(BOB_UID, "FEED-PRIVATE", now)
    )
  );
  await assertSucceeds(
    aliceDb.collection("group_posts").doc("post_private").delete()
  );
});

function validChatMessage(senderId, groupId, ts) {
  return {
    senderId,
    senderName: senderId === ALICE_UID ? "Alice" : "Bob",
    groupId,
    text: "Mensaje mínimo",
    messageType: "TEXT",
    audioDurationSec: 0,
    transcription: "",
    sharedOutingId: "",
    sharedOutingTitle: "",
    reactions: [],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validStory(authorId, groupId, ts) {
  return {
    authorId,
    authorName: authorId === ALICE_UID ? "Alice" : "Bob",
    groupId,
    caption: "Historia mínima",
    filterStyle: "NONE",
    mediaUri: "preset://story",
    outingTitle: "",
    likesCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validGroup(groupId, memberIds, memberNames, ts) {
  return {
    name: "Grupo privado de prueba",
    inviteCode: groupId,
    createdBy: ALICE_UID,
    memberIds,
    memberNames,
    createdAt: ts,
    updatedAt: ts,
  };
}

async function seedPrivateGroupData() {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("groups").doc("RED-PRIVATE").set(
      validGroup("RED-PRIVATE", [ALICE_UID], { [ALICE_UID]: "Alice" }, now)
    );
    await db.collection("outings").doc("red_private_outing").set(
      { ...validOutingPayload(ALICE_UID, now), groupId: "RED-PRIVATE" }
    );
    await db.collection("chat_messages").doc("red_private_chat").set(
      validChatMessage(ALICE_UID, "RED-PRIVATE", now)
    );
    await db.collection("stories").doc("red_private_story").set(
      validStory(ALICE_UID, "RED-PRIVATE", now)
    );
  });
}

test("Red: non-member cannot inject outing, chat, or story into a foreign group", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();
  await seedPrivateGroupData();

  await assertFails(
    bobDb.collection("outings").doc("red_bob_outing").set(
      { ...validOutingPayload(BOB_UID, now), groupId: "RED-PRIVATE" }
    )
  );
  await assertFails(
    bobDb.collection("chat_messages").doc("red_bob_chat").set(
      validChatMessage(BOB_UID, "RED-PRIVATE", now)
    )
  );
  await assertFails(
    bobDb.collection("stories").doc("red_bob_story").set(
      validStory(BOB_UID, "RED-PRIVATE", now)
    )
  );
});

test("Red: existing member cannot add or remove UIDs or rename another member", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc("RED-MEMBERS").set(
      validGroup("RED-MEMBERS", [ALICE_UID, BOB_UID], {
        [ALICE_UID]: "Alice",
        [BOB_UID]: "Bob",
      }, now)
    );
  });

  await assertFails(
    aliceDb.collection("groups").doc("RED-MEMBERS").update({
      memberIds: [ALICE_UID, BOB_UID, "mallory_789"],
      memberNames: {
        [ALICE_UID]: "Alice",
        [BOB_UID]: "Bob",
        mallory_789: "Mallory",
      },
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("groups").doc("RED-MEMBERS").update({
      memberIds: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("groups").doc("RED-MEMBERS").update({
      memberIds: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Robert" },
      updatedAt: now,
    })
  );
});

test("Red: non-member cannot access private data after learning document IDs", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await seedPrivateGroupData();

  await assertFails(bobDb.collection("groups").doc("RED-PRIVATE").get());
  await assertFails(bobDb.collection("outings").doc("red_private_outing").get());
  await assertFails(
    bobDb.collection("chat_messages").doc("red_private_chat").get()
  );
  await assertFails(bobDb.collection("stories").doc("red_private_story").get());
});

test("Red: member cannot change another user's RSVP in a shared outing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("groups").doc("RED-RSVP").set(
      validGroup("RED-RSVP", [ALICE_UID, BOB_UID], {
        [ALICE_UID]: "Alice",
        [BOB_UID]: "Bob",
      }, now)
    );
    await db.collection("outings").doc("red_rsvp_outing").set({
      ...validOutingPayload(ALICE_UID, now),
      groupId: "RED-RSVP",
      rsvpByUser: { [ALICE_UID]: "YES", [BOB_UID]: "MAYBE" },
    });
  });

  await assertFails(
    aliceDb.collection("outings").doc("red_rsvp_outing").update({
      rsvpByUser: { [ALICE_UID]: "YES", [BOB_UID]: "NO" },
      updatedAt: now,
    })
  );
});
