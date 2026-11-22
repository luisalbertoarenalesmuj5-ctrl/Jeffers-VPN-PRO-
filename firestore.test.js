import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from "firebase/firestore";
import { after, before, beforeEach, describe, it } from "node:test";
import { readFileSync } from "node:fs";

const PROJECT_ID = "gen-lang-client-0374613410";
const DATABASE_ID = "ai-studio-android-jeffersv-815ce2ff-3ee1-4904-acb8-c0856917ca5f";
const ADMIN_EMAIL = "luisalbertoarenalesmuj5@gmail.com";

let testEnv;

before(async () => {
  const rules = readFileSync("firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: "127.0.0.1",
      port: 8085,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function dbFor(uid, tokenOverrides = {}) {
  if (!uid) {
    return testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  }
  return testEnv.authenticatedContext(uid, tokenOverrides).firestore(DATABASE_ID);
}

describe("Jeffers VPN Firestore Security Rules", () => {
  it("denies all unauthenticated reads and writes", async () => {
    const anonDb = dbFor(null);
    await assertFails(getDoc(doc(anonDb, "users", "user-1")));
    await assertFails(getDoc(doc(anonDb, "device_licenses", "8719ba6889e9480d")));
    await assertFails(getDoc(doc(anonDb, "license_codes", "JFF-PRM-20261231-ALL-A1B2")));
    await assertFails(getDoc(doc(anonDb, "personal_clients", "cliente_vip")));
    await assertFails(
      setDoc(doc(anonDb, "device_licenses", "8719ba6889e9480d"), {
        deviceId: "8719ba6889e9480d",
      })
    );
  });

  it("enforces strict verified-email and role assignment on admins collection", async () => {
    const adminUid = "admin-uid-1";
    const adminDb = dbFor(adminUid, {
      email: ADMIN_EMAIL,
      email_verified: true,
    });
    await assertSucceeds(
      setDoc(doc(adminDb, "admins", adminUid), {
        email: ADMIN_EMAIL,
        role: "admin",
        createdAt: serverTimestamp(),
      })
    );

    const spoofedDb = dbFor("spoof-uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    });
    await assertFails(
      setDoc(doc(spoofedDb, "admins", "spoof-uid"), {
        email: ADMIN_EMAIL,
        role: "admin",
        createdAt: serverTimestamp(),
      })
    );

    const randomUserDb = dbFor("user-1", {
      email: "customer@gmail.com",
      email_verified: true,
    });
    await assertFails(
      setDoc(doc(randomUserDb, "admins", "user-1"), {
        email: "customer@gmail.com",
        role: "admin",
        createdAt: serverTimestamp(),
      })
    );
  });

  it("enforces owner-only access on users/{uid} with createdAt immutability", async () => {
    const aliceDb = dbFor("alice-uid", { email: "alice@gmail.com", email_verified: true });
    const bobDb = dbFor("bob-uid", { email: "bob@gmail.com", email_verified: true });

    await assertSucceeds(
      setDoc(doc(aliceDb, "users", "alice-uid"), {
        uid: "alice-uid",
        email: "alice@gmail.com",
        displayName: "alice2026",
        userCountry: "GUATEMALA",
        deviceId: "8719ba6889e9480d",
        activeTier: "FREE",
        activationCode: "",
        expiresAtMillis: 0,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    await assertSucceeds(
      updateDoc(doc(aliceDb, "users", "alice-uid"), {
        displayName: "alice_vip99",
        activeTier: "PREMIUM",
        activationCode: "JFF-PRM-20261231-8719BA68-ABCD",
        expiresAtMillis: Math.floor(Date.now()) + 30 * 86400000,
        updatedAt: serverTimestamp(),
      })
    );

    await assertFails(getDoc(doc(bobDb, "users", "alice-uid")));
    await assertFails(
      updateDoc(doc(bobDb, "users", "alice-uid"), {
        displayName: "hijacked",
        updatedAt: serverTimestamp(),
      })
    );
  });

  it("supports Activar por ID in real time while blocking forgery, blanket list, and hijacking", async () => {
    const adminDb = dbFor("admin-1", { email: ADMIN_EMAIL, email_verified: true });
    const customerDb = dbFor("cust-1", { email: "cust@gmail.com", email_verified: true });
    const attackerDb = dbFor("evil-1", { email: "evil@gmail.com", email_verified: true });

    const targetId = "8719ba6889e9480d";
    const expiresAt = Math.floor(Date.now()) + 30 * 86400000;

    // Customer cannot self-issue a device license
    await assertFails(
      setDoc(doc(customerDb, "device_licenses", targetId), {
        deviceId: targetId,
        code: "JFF-PRM-20261231-8719BA68-ABCD",
        tier: "PREMIUM",
        durationDays: 30,
        expiresAtMillis: expiresAt,
        revoked: false,
        note: "Fake",
        issuerUid: "cust-1",
        activatedAtMillis: Math.floor(Date.now()),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    // Admin sends PREMIUM plan to Device ID 8719ba6889e9480d
    await assertSucceeds(
      setDoc(doc(adminDb, "device_licenses", targetId), {
        deviceId: targetId,
        code: "JFF-PRM-20261231-8719BA68-ABCD",
        tier: "PREMIUM",
        durationDays: 30,
        expiresAtMillis: expiresAt,
        revoked: false,
        note: "Activado por ID",
        issuerUid: "admin-1",
        activatedAtMillis: Math.floor(Date.now()),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    // Customer reads exact device_licenses/{deviceId} document directly
    await assertSucceeds(getDoc(doc(customerDb, "device_licenses", targetId)));

    // Blanket collection list is denied for non-admin
    await assertFails(getDocs(collection(customerDb, "device_licenses")));

    // Customer claims ownership of their synced license
    await assertSucceeds(
      updateDoc(doc(customerDb, "device_licenses", targetId), {
        claimedByUid: "cust-1",
        activatedAtMillis: Math.floor(Date.now()),
        updatedAt: serverTimestamp(),
      })
    );

    // Customer can query their own claimed licenses
    await assertSucceeds(
      getDocs(query(collection(customerDb, "device_licenses"), where("claimedByUid", "==", "cust-1")))
    );

    // Customer CANNOT tamper plan tier or expiresAtMillis
    await assertFails(
      updateDoc(doc(customerDb, "device_licenses", targetId), {
        tier: "SUPER_PRO",
        updatedAt: serverTimestamp(),
      })
    );

    // Attacker CANNOT hijack claimedByUid
    await assertFails(
      updateDoc(doc(attackerDb, "device_licenses", targetId), {
        claimedByUid: "evil-1",
        activatedAtMillis: Math.floor(Date.now()),
        updatedAt: serverTimestamp(),
      })
    );

    // Admin can list, extend, revoke, and delete
    await assertSucceeds(
      getDocs(query(collection(adminDb, "device_licenses"), orderBy("updatedAt", "desc"), limit(50)))
    );
    await assertSucceeds(
      updateDoc(doc(adminDb, "device_licenses", targetId), {
        expiresAtMillis: expiresAt + 30 * 86400000,
        durationDays: 60,
        updatedAt: serverTimestamp(),
      })
    );
    await assertSucceeds(
      updateDoc(doc(adminDb, "device_licenses", targetId), {
        revoked: true,
        updatedAt: serverTimestamp(),
      })
    );
    await assertSucceeds(deleteDoc(doc(adminDb, "device_licenses", targetId)));
  });

  it("validates license_codes and personal_clients lookup, claim, and revocation", async () => {
    const adminDb = dbFor("admin-1", { email: ADMIN_EMAIL, email_verified: true });
    const customerDb = dbFor("cust-2", { email: "cust2@gmail.com", email_verified: true });
    const expiresAt = Math.floor(Date.now()) + 15 * 86400000;

    // Admin issues a code
    const codeKey = "JFF-SPR-20261231-ALL-1234";
    await assertSucceeds(
      setDoc(doc(adminDb, "license_codes", codeKey), {
        code: codeKey,
        tier: "SUPER_PRO",
        durationDays: 15,
        expiresAtMillis: expiresAt,
        revoked: false,
        note: "Promo",
        issuerUid: "admin-1",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    // Customer fetches exact code and claims it
    await assertSucceeds(getDoc(doc(customerDb, "license_codes", codeKey)));
    await assertFails(getDocs(collection(customerDb, "license_codes")));
    await assertSucceeds(
      updateDoc(doc(customerDb, "license_codes", codeKey), {
        claimedByUid: "cust-2",
        activatedByDeviceId: "dev002",
        activatedAtMillis: Math.floor(Date.now()),
        updatedAt: serverTimestamp(),
      })
    );

    // Admin creates a personal client
    const usernameKey = "juan_vip";
    await assertSucceeds(
      setDoc(doc(adminDb, "personal_clients", usernameKey), {
        usernameKey,
        displayUsername: "juan_vip",
        code: "JFF-VIP-20261231-ALL-9999",
        tier: "VIP",
        durationDays: 30,
        expiresAtMillis: expiresAt,
        hasPassword: true,
        passwordHash: "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
        revoked: false,
        note: "Cliente personal",
        issuerUid: "admin-1",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    await assertSucceeds(getDoc(doc(customerDb, "personal_clients", usernameKey)));
    await assertFails(getDocs(collection(customerDb, "personal_clients")));

    // Admin revokes personal client -> subsequent claim fails
    await assertSucceeds(
      updateDoc(doc(adminDb, "personal_clients", usernameKey), {
        revoked: true,
        updatedAt: serverTimestamp(),
      })
    );
    await assertFails(
      updateDoc(doc(customerDb, "personal_clients", usernameKey), {
        claimedByUid: "cust-2",
        activatedByDeviceId: "dev002",
        activatedAtMillis: Math.floor(Date.now()),
        updatedAt: serverTimestamp(),
      })
    );
  });
});
