const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-adora-storage-ci";
const FIRESTORE_PORT = Number((process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":")[1]);
const STORAGE_PORT = Number((process.env.STORAGE_EMULATOR_HOST || "127.0.0.1:9199").split(":")[1]);
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: FIRESTORE_PORT,
    },
    storage: {
      rules: fs.readFileSync("storage.rules", "utf8"),
      host: "127.0.0.1",
      port: STORAGE_PORT,
    },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.clearStorage();
});

function imageMetadata() {
  return { contentType: "image/jpeg" };
}

function audioMetadata() {
  return { contentType: "audio/mp4" };
}

async function seedDefaultGroup(groupId = "GROUP-1") {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc(groupId).set({
      memberIds: [ALICE_UID],
    });
  });
}

test("legacy path: owner can upload an allowed image, another UID cannot write", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).storage();
  const bob = testEnv.authenticatedContext(BOB_UID).storage();
  await assertSucceeds(
    alice.ref("users/alice_123/album/photo.jpg").putString("photo", "raw", imageMetadata())
  );
  await assertFails(
    bob.ref("users/alice_123/album/other.jpg").putString("photo", "raw", imageMetadata())
  );
});

test("legacy path: rejects wrong content type and disallows non-owner reads", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).storage();
  const bob = testEnv.authenticatedContext(BOB_UID).storage();
  await assertFails(
    alice.ref("users/alice_123/album/not-an-image.bin").putString("voice", "raw", audioMetadata())
  );
  await assertSucceeds(
    alice.ref("users/alice_123/voice_notes/note.m4a").putString("audio", "raw", audioMetadata())
  );
  await assertFails(bob.ref("users/alice_123/voice_notes/note.m4a").getMetadata());
});

test("group migration path: default-database member can read and write only their UID", async () => {
  await seedDefaultGroup();
  const alice = testEnv.authenticatedContext(ALICE_UID).storage();
  const bob = testEnv.authenticatedContext(BOB_UID).storage();
  await assertSucceeds(
    alice.ref("groups/GROUP-1/media/alice_123/album/photo.jpg").putString("photo", "raw", imageMetadata())
  );
  await assertSucceeds(
    alice.ref("groups/GROUP-1/media/alice_123/album/photo.jpg").getMetadata()
  );
  await assertFails(
    bob.ref("groups/GROUP-1/media/alice_123/album/photo.jpg").getMetadata()
  );
  await assertFails(
    alice.ref("groups/GROUP-1/media/bob_456/album/impersonation.jpg").putString("photo", "raw", imageMetadata())
  );
});

test("group migration path: upload larger than 10 MiB is rejected", async () => {
  await seedDefaultGroup();
  const alice = testEnv.authenticatedContext(ALICE_UID).storage();
  const oversized = "x".repeat(10 * 1024 * 1024);
  await assertFails(
    alice.ref("groups/GROUP-1/media/alice_123/album/large.jpg").putString(oversized, "raw", imageMetadata())
  );
});
