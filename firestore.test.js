const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-telecomc-b0906986-2476-47cf-b62d-c23386040cf3";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validTelecomRecordPayload(ts = pastTimestamp()) {
  return {
    carrier: "Viettel",
    province: "Hà Nội",
    region: "Miền Bắc",
    category: "Thuê bao & Doanh thu",
    stationCode: "VTL-HN-01",
    subscribers: 4500000,
    revenueBillionVnd: 1250.5,
    coverage5gPercent: 92.4,
    popularPackage: "5G135",
    latitude: 21.0285,
    longitude: 105.8542,
    source: "Google Drive CSV",
    notes: "Cập nhật từ My Drive\\Viettel\\CSDL",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validSyncConfigPayload(ts = pastTimestamp()) {
  return {
    driveFolderPath: "My Drive\\Viettel\\CSDL",
    driveCsvUrl: "https://example.com/export.csv",
    localSafUri: "",
    autoSyncEnabled: true,
    syncHour: 7,
    wifiOnly: true,
    zaloWebhookUrl: "",
    zaloTargetLabel: "Nhóm Điều hành",
    lastSyncStatus: "Chưa đồng bộ",
    lastSyncTimeText: "--",
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated user cannot read or write telecom_records", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("telecom_records").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("telecom_records").doc("rec_1").set(validTelecomRecordPayload())
  );
});

test("Owner can create, read, list, update, and delete valid telecom_record", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("telecom_records").doc("rec_1");
  await assertSucceeds(docRef.set(validTelecomRecordPayload()));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("telecom_records").get()
  );
  await assertSucceeds(
    docRef.update({ subscribers: 4600000, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user access to telecom_records is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("telecom_records").doc("rec_1").set(validTelecomRecordPayload())
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("telecom_records").doc("rec_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("telecom_records").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("telecom_records").doc("rec_2").set(validTelecomRecordPayload())
  );
});

test("Shadow update with ghost field or invalid carrier is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const badPayload = { ...validTelecomRecordPayload(), isAdmin: true };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("telecom_records").doc("rec_bad").set(badPayload)
  );
  const badCarrier = { ...validTelecomRecordPayload(), carrier: "UnknownTel" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("telecom_records").doc("rec_bad2").set(badCarrier)
  );
});

test("Owner can create and update sync_configs, sync_logs, and zalo_reports", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const cfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("sync_configs").doc("default");
  await assertSucceeds(cfgRef.set(validSyncConfigPayload()));
  await assertSucceeds(cfgRef.update({ syncHour: 18, updatedAt: new Date(Date.now() - 1000) }));

  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("sync_logs").doc("log_1");
  await assertSucceeds(
    logRef.set({
      sourcePath: "My Drive\\Viettel\\CSDL",
      recordsProcessed: 12,
      status: "Thành công",
      message: "Đồng bộ 12 bản ghi",
      triggeredBy: "Thủ công",
      createdAt: pastTimestamp(),
    })
  );
  // sync_logs is append-only (update denied)
  await assertFails(logRef.update({ status: "Thất bại" }));

  const zaloRef = aliceDb.collection("users").doc(ALICE_UID).collection("zalo_reports").doc("rep_1");
  await assertSucceeds(
    zaloRef.set({
      targetName: "Nhóm Điều hành",
      channel: "Zalo App",
      summaryText: "Báo cáo ngày: Viettel 4.5M TB",
      status: "Đã gửi",
      createdAt: pastTimestamp(),
    })
  );
});
