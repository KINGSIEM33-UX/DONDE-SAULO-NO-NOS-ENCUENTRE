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
