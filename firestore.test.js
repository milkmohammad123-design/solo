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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: denied across all Atelier AI collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.doc(`users/${ALICE_UID}`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/garments`).get());
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/garments/g1`).set({
      userId: ALICE_UID,
      name: "Wool Coat",
      category: "Outerwear",
      colorName: "Camel",
      colorHex: "#B8956A",
      season: "Fall/Winter",
      formality: 4,
      price: 890,
      wears: 0,
      status: "AVAILABLE",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("UserProfile: owner create, update, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      displayName: "Alice",
      seasonPalette: "Deep Autumn",
      undertone: "Warm",
      contrastLevel: "High",
      archetypes: ["Quiet Luxury", "Minimalist"],
      silhouette: "Tailored",
      monthlyBudget: 600,
      powerColors: ["#8B5A3C", "#2E4034"],
      neutralColors: ["#FAF8F5", "#141414"],
      avoidColors: ["#FF00FF"],
      darkMode: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update allowed
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).update({
      monthlyBudget: 750,
      darkMode: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Shadow field rejected
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}`).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read denied
  await assertFails(bobDb.doc(`users/${ALICE_UID}`).get());
});

test("Garments: create, wear increment, status change, invalid status/id rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/garments/garment_1`).set({
      userId: ALICE_UID,
      name: "Double-Breasted Wool Coat",
      brand: "The Row",
      category: "Outerwear",
      subCategory: "Overcoat",
      colorName: "Camel Tan",
      colorHex: "#B8956A",
      fabric: "Virgin Wool",
      season: "Fall/Winter",
      formality: 5,
      price: 890,
      wears: 9,
      status: "AVAILABLE",
      careInstructions: "Dry clean only",
      tags: ["quiet-luxury", "tailored"],
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection succeeds for owner, fails for Bob
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/garments`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/garments`).get());

  // Wear increment & status update succeed
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/garments/garment_1`).update({
      wears: 10,
      lastWornEpochMs: Date.now() - 1000,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/garments/garment_1`).update({
      status: "IN_LAUNDRY",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject invalid status, future timestamp, or @DocumentId 'id' field
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/garments/garment_1`).update({
      status: "LOST",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/garments/garment_2`).set({
      id: "garment_2",
      userId: ALICE_UID,
      name: "Injected ID",
      category: "Tops",
      colorName: "Black",
      colorHex: "#141414",
      season: "All-Season",
      formality: 3,
      price: 100,
      wears: 0,
      status: "AVAILABLE",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/garments/garment_3`).set({
      userId: ALICE_UID,
      name: "Future Timestamp",
      category: "Tops",
      colorName: "Black",
      colorHex: "#141414",
      season: "All-Season",
      formality: 3,
      price: 100,
      wears: 0,
      status: "AVAILABLE",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );
});

test("Looks, Calendar, Wishlist, Trips & OOTD Feedback: owner CRUD and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 4000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/looks/look_1`).set({
      userId: ALICE_UID,
      title: "Autumn Gallery Opening",
      occasion: "Evening",
      collectionName: "Autumn Work",
      harmonyScore: 96,
      itemIds: ["item-1", "item-4", "item-7"],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/calendar/2026-11-20`).set({
      userId: ALICE_UID,
      dateKey: "2026-11-20",
      lookId: "look_1",
      lookTitle: "Autumn Gallery Opening",
      itemIds: ["item-1", "item-4"],
      occasion: "Gallery",
      whoYouSaw: "Elena & Marc",
      worn: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/wishlist/wl_1`).set({
      userId: ALICE_UID,
      name: "Espresso Suede Loafers",
      category: "Footwear",
      colorName: "Espresso",
      colorHex: "#4A3728",
      price: 295,
      verdict: "BUY",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/trips/trip_1`).set({
      userId: ALICE_UID,
      destination: "Milan, Italy",
      days: 4,
      climate: "Cool & Crisp",
      bagSize: "Carry-on",
      packingItemIds: ["item-1", "item-4", "item-7"],
      checkedItemIds: ["item-1"],
      itineraryLines: ["Day 1|Gallery|item-1,item-4,item-7|Layered tailoring"],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/ootd_feedback/fb_1`).set({
      userId: ALICE_UID,
      title: "SoHo Minimal",
      action: "LOVE",
      itemIds: ["item-2", "item-5"],
      createdAt: created,
    })
  );

  // Cross-user access denied
  await assertFails(bobDb.collection(`users/${ALICE_UID}/looks`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/calendar`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/wishlist`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/trips`).get());
});
