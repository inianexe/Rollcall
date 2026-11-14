import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { handleApi } from "./_api.js";

globalThis.crypto ??= webcrypto;

function encode(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

async function authToken(privateKey, claims = {}) {
  const header = encode({ alg: "RS256", kid: "test-key" });
  const payload = encode({
    aud: "rollcall-test", iss: "https://securetoken.google.com/rollcall-test", sub: "student-1",
    email: "student@example.com", email_verified: true,
    iat: Math.floor(Date.now() / 1000), auth_time: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600, ...claims
  });
  const unsigned = `${header}.${payload}`;
  const signature = Buffer.from(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, new TextEncoder().encode(unsigned))).toString("base64url");
  return `${unsigned}.${signature}`;
}

async function hmac(value, secret) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return Buffer.from(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value))).toString("hex");
}

function fakeDatabase() {
  const state = {
    sends: [], challengeSends: [], code: null, challenge: null, session: null, browserSessions: [],
    deviceId: "old-device-id-1234", syncEnabled: 1, snapshot: null, appDevices: [
      { uid: "student-1", device_id: "old-device-id-1234", fcm_token: "old-fcm-token" }
    ], approvalDevices: [
      { uid: "student-1", device_id: "old-device-id-1234", fcm_token: "trusted-fcm-token", public_key: null, created_at: 1, updated_at: 1 }
    ], enrollmentRequests: [], sessionsDeleted: 0, deletedTables: [],
    accountRows: Object.fromEntries(["trusted_devices", "app_devices", "approval_devices", "approval_enrollment_requests", "snapshots", "web_challenges", "web_challenge_sends", "web_sessions", "device_replacement_codes", "device_replacement_sends", "recovery_codes"].map(table => [table, 1]))
  };
  return {
    state,
    async batch(statements) { await Promise.all(statements.map(statement => statement.run())); },
    prepare(sql) {
      let args = [];
      return {
        bind(...values) { args = values; return this; },
        async first() {
          if (sql.includes("SELECT uid,session_id FROM web_sessions"))
            return state.session && state.session.expires_at > args[1] ? { uid: state.session.uid, session_id: state.session.session_id } : null;
          if (sql.includes("SELECT device_id FROM app_devices WHERE uid=? AND device_id=?"))
            return state.appDevices.find(row => row.uid === args[0] && row.device_id === args[1]) || null;
          if (sql.includes("FROM approval_devices WHERE uid=? AND device_id=?"))
            return state.approvalDevices.find(row => row.uid === args[0] && row.device_id === args[1]) || null;
          if (sql.includes("SELECT fcm_token FROM approval_devices WHERE uid=?"))
            return state.approvalDevices.find(row => row.uid === args[0]) || null;
          if (sql.includes("SELECT device_id") && sql.includes("FROM trusted_devices"))
            return state.deviceId ? { device_id: state.deviceId, fcm_token: "test-fcm-token", sync_enabled: state.syncEnabled } : null;
          if (sql.includes("SELECT fcm_token FROM trusted_devices")) return state.deviceId ? { fcm_token: "test-fcm-token" } : null;
          if (sql.includes("FROM web_challenges WHERE id=?"))
            return state.challenge && state.challenge.id === args[0]
              && (!sql.includes("uid=?") || state.challenge.uid === args[1])
              && !state.challenge.consumed_at && state.challenge.attempts < 5
              && state.challenge.expires_at > args[sql.includes("uid=?") ? 2 : 1] ? state.challenge : null;
          if (sql.includes("SELECT code_hash FROM device_replacement_codes"))
            return state.code && !state.code.used_at && state.code.attempts < 5 && state.code.expires_at > args[1] ? state.code : null;
          if (sql.includes("SELECT created_at FROM device_replacement_sends")) return state.sends.at(-1) || null;
          if (sql.includes("SELECT created_at FROM approval_enrollment_requests"))
            return state.enrollmentRequests.filter(row => row.uid === args[0]).sort((a, b) => b.created_at - a.created_at)[0] || null;
          if (sql.includes("FROM approval_enrollment_requests WHERE id=?"))
            return state.enrollmentRequests.find(row => row.id === args[0] && row.uid === args[1]
              && !row.completed_at && row.expires_at > args[2]) || null;
          if (sql.includes("SELECT COUNT(*) AS n FROM device_replacement_sends")) return { n: state.sends.length };
          return null;
        },
        async all() {
          if (sql.includes("SELECT session_id,created_at,last_used_at,expires_at,user_agent FROM web_sessions"))
            return { results: state.browserSessions.filter(row => row.uid === args[0] && row.expires_at > args[1]) };
          if (sql.includes("FROM approval_devices WHERE uid=? AND device_id<>?"))
            return { results: state.approvalDevices.filter(row => row.uid === args[0] && row.device_id !== args[1]) };
          if (sql.includes("FROM approval_devices WHERE uid=?"))
            return { results: state.approvalDevices.filter(row => row.uid === args[0]) };
          if (sql.includes("FROM app_devices WHERE uid=? AND device_id<>?"))
            return { results: state.appDevices.filter(row => row.uid === args[0] && row.device_id !== args[1]) };
          if (sql.includes("FROM app_devices WHERE uid=?"))
            return { results: state.appDevices.filter(row => row.uid === args[0]) };
          return { results: [] };
        },
        async run() {
          if (sql.startsWith("DELETE FROM device_replacement_sends")) state.sends = state.sends.filter(row => row.created_at >= args[0]);
          if (sql.startsWith("DELETE FROM approval_enrollment_requests WHERE expires_at"))
            state.enrollmentRequests = state.enrollmentRequests.filter(row => row.expires_at > args[0]);
          if (sql.startsWith("INSERT INTO approval_enrollment_requests"))
            state.enrollmentRequests.push({ id: args[0], uid: args[1], device_id: args[2], nonce_hash: args[3], created_at: args[4], expires_at: args[5], completed_at: null });
          if (sql.startsWith("UPDATE approval_enrollment_requests SET completed_at")) {
            const row = state.enrollmentRequests.find(row => row.id === args[1] && row.uid === args[2] && !row.completed_at && row.expires_at > args[3]);
            if (row) row.completed_at = args[0];
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE approval_devices SET public_key")) {
            const row = state.approvalDevices.find(row => row.uid === args[3] && row.device_id === args[4] && !row.public_key);
            if (row) { row.public_key = args[0]; row.key_enrolled_at = args[1]; row.updated_at = args[2]; }
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("INSERT INTO device_replacement_sends")) state.sends.push({ uid: args[0], created_at: args[1] });
          if (sql.startsWith("DELETE FROM web_challenge_sends")) state.challengeSends = state.challengeSends.filter(row => row.created_at >= args[0]);
          if (sql.startsWith("INSERT INTO web_challenge_sends")) {
            const allowed = state.challengeSends.filter(row => row.uid === args[2] && row.created_at > args[3]).length < 5;
            if (allowed) state.challengeSends.push({ uid: args[0], created_at: args[1] });
            return { meta: { changes: allowed ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE web_challenges SET consumed_at") && state.challenge) state.challenge.consumed_at = args[0];
          if (sql.startsWith("UPDATE web_challenges SET approved_at") && state.challenge && !state.challenge.approved_at && !state.challenge.denied_at) {
            state.challenge.approved_at = args[0]; state.challenge.approved_by_device_id = args[1]; return { meta: { changes: 1 } };
          }
          if (sql.startsWith("UPDATE web_challenges SET denied_at") && state.challenge && !state.challenge.approved_at && !state.challenge.denied_at) {
            state.challenge.denied_at = args[0]; state.challenge.approved_by_device_id = args[1]; return { meta: { changes: 1 } };
          }
          if (sql.startsWith("INSERT INTO web_sessions")) {
            state.session = { session_hash: args[0], uid: args[1], created_at: args[2], expires_at: args[3], session_id: args[4] };
            state.browserSessions.push({ session_hash: args[0], uid: args[1], created_at: args[2], expires_at: args[3], session_id: args[4], user_agent: args[5], last_used_at: args[6] });
          }
          if (sql.startsWith("INSERT INTO device_replacement_codes")) state.code = { uid: args[0], code_hash: args[1], created_at: args[2], expires_at: args[3], attempts: 0, used_at: null };
          if (sql.startsWith("UPDATE device_replacement_codes SET used_at")) state.code.used_at = args[0];
          if (sql.startsWith("UPDATE device_replacement_codes SET attempts")) {
            const allowed = state.code && !state.code.used_at && state.code.expires_at > args[1] && state.code.attempts < 5;
            if (allowed) state.code.attempts++;
            return { meta: { changes: allowed ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE web_challenges SET attempts")) {
            const allowed = state.challenge && !state.challenge.consumed_at && state.challenge.expires_at > args[1] && state.challenge.attempts < 5;
            if (allowed) state.challenge.attempts++;
            return { meta: { changes: allowed ? 1 : 0 } };
          }
          if (sql.startsWith("DELETE FROM web_sessions WHERE uid=? AND session_id=?")) {
    const before = state.browserSessions.length;
            state.browserSessions = state.browserSessions.filter(row => row.uid !== args[0] || row.session_id !== args[1]);
            if (state.session?.uid === args[0] && state.session?.session_id === args[1]) state.session = null;
            return { meta: { changes: before - state.browserSessions.length } };
          }
          if (sql.startsWith("DELETE FROM web_sessions")) { state.sessionsDeleted++; state.session = null; state.browserSessions = []; }
          if (sql.startsWith("DELETE FROM app_devices WHERE uid=? AND device_id=?"))
            state.appDevices = state.appDevices.filter(row => row.uid !== args[0] || row.device_id !== args[1]);
          if (sql.startsWith("DELETE FROM ") && sql.endsWith(" WHERE uid=?")) {
            const table = sql.slice("DELETE FROM ".length, sql.indexOf(" WHERE uid=?"));
            state.deletedTables.push(table);
            if (table in state.accountRows) state.accountRows[table] = 0;
            if (table === "web_challenge_sends") state.challengeSends = [];
          }
          if (sql.startsWith("INSERT INTO trusted_devices")) state.deviceId = args[1];
          if (sql.startsWith("INSERT INTO trusted_devices")) state.syncEnabled = args[4] === 1 ? 1 : state.syncEnabled;
          if (sql.startsWith("UPDATE trusted_devices SET sync_enabled=0")) state.syncEnabled = 0;
          if (sql.startsWith("INSERT INTO snapshots")) state.snapshot = { uid: args[0], snapshot_json: args[2], updated_at: args[3] };
          if (sql.startsWith("DELETE FROM snapshots WHERE uid=?")) state.snapshot = null;
          if (sql.startsWith("INSERT INTO app_devices")) {
            const current = state.appDevices.find(row => row.device_id === args[0]);
            if (current && current.uid !== args[1]) return { meta: { changes: 0 } };
            state.appDevices = state.appDevices.filter(row => row.device_id !== args[0]);
            state.appDevices.push({ device_id: args[0], uid: args[1], fcm_token: args[2], updated_at: args[3] });
          }
          if (sql.startsWith("INSERT INTO approval_devices")) {
            state.approvalDevices = state.approvalDevices.filter(row => row.device_id !== args[0]);
            state.approvalDevices.push({ device_id: args[0], uid: args[1], fcm_token: args[2], public_key: args[3], created_at: args[4], updated_at: args[5] });
          }
          return { meta: { changes: 1 } };
        }
      };
    }
  };
}

test("phone-link codes reach enrolled approvers, never merely signed-in apps", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const servicePair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const serviceKey = Buffer.from(await crypto.subtle.exportKey("pkcs8", servicePair.privateKey)).toString("base64");
  const originalFetch = globalThis.fetch;
  const database = fakeDatabase();
  database.state.appDevices.push(
    { uid: "student-1", device_id: "tablet-device-1234", fcm_token: "tablet-fcm-token" },
    { uid: "student-1", device_id: "new-device-id-1234", fcm_token: "new-phone-fcm-token" }
  );
  const messages = [];
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("securetoken@system.gserviceaccount.com")) return Response.json({ keys: [jwk] });
    if (String(url) === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "service-token" });
    assert.match(String(url), /^https:\/\/fcm\.googleapis\.com\/v1\/projects\/rollcall-test\/messages:send$/);
    assert.equal(options.headers.authorization, "Bearer service-token");
    messages.push(JSON.parse(options.body).message);
    return Response.json({ name: `projects/rollcall-test/messages/${messages.length}` });
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const token = await authToken(pair.privateKey);
  const servicePrivateKey = `-----BEGIN PRIVATE KEY-----\n${serviceKey}\n-----END PRIVATE KEY-----`;
  const env = {
    FIREBASE_PROJECT_ID: "rollcall-test", SESSION_HASH_KEY: "test-session-secret-with-enough-entropy",
    FIREBASE_CLIENT_EMAIL: "service@rollcall-test.iam.gserviceaccount.com", FIREBASE_PRIVATE_KEY: servicePrivateKey,
    DB: database
  };
  const response = await handleApi(new Request("https://rollcall.test/api/device/replacement-code", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "new-device-id-1234" })
  }), env);
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.deepEqual(body, { ok: true, expiresIn: 600, sentTo: 1 });
  assert.deepEqual(new Set(messages.map(message => message.token)), new Set(["trusted-fcm-token"]));
  assert(messages.every(message => message.data.type === "device_replacement" && message.data.uid === "student-1" && message.android.ttl === "600s"));
  const code = messages[0].data.replacementCode;
  assert.equal(database.state.code.code_hash, await hmac(code, env.SESSION_HASH_KEY));
  assert.match(code, /^\d{6}$/);
  assert.equal(database.state.code.code_hash === code, false);
  assert.equal(database.state.code.expires_at - database.state.code.created_at, 600);
  assert.equal(JSON.stringify(body).includes(code), false);

  const wrongCode = await handleApi(new Request("https://rollcall.test/api/device/register", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "new-device-id-1234", fcmToken: "test-fcm", replacementCode: code === "000000" ? "000001" : "000000" })
  }), env);
  assert.equal(wrongCode.status, 403);
  assert.equal((await wrongCode.json()).code, "replacement_code_invalid");
  assert.equal(database.state.code.attempts, 1);

  const linked = await handleApi(new Request("https://rollcall.test/api/device/register", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "new-device-id-1234", fcmToken: "test-fcm", replacementCode: code })
  }), env);
  assert.equal(linked.status, 200, JSON.stringify(await linked.clone().json()));
  assert.equal(database.state.deviceId, "new-device-id-1234");
  assert.equal(database.state.sessionsDeleted, 1);

  const reused = await handleApi(new Request("https://rollcall.test/api/device/register", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "another-device-id-1234", fcmToken: "test-fcm", replacementCode: code })
  }), env);
  assert.equal(reused.status, 403);
  assert.equal((await reused.json()).code, "replacement_code_expired");
  assert.equal(database.state.deviceId, "new-device-id-1234");

  const limited = await handleApi(new Request("https://rollcall.test/api/device/replacement-code", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "another-device-id-1234" })
  }), env);
  assert.equal(limited.status, 429);
});

test("web sign-in approval is pushed only to enrolled approvers", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const servicePair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const serviceKey = Buffer.from(await crypto.subtle.exportKey("pkcs8", servicePair.privateKey)).toString("base64");
  const database = fakeDatabase();
  database.state.appDevices.push({ uid: "student-1", device_id: "tablet-device-1234", fcm_token: "tablet-fcm-token" });
  const originalFetch = globalThis.fetch, sent = [];
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("securetoken@system.gserviceaccount.com")) return Response.json({ keys: [jwk] });
    if (String(url) === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "service-token" });
    sent.push(JSON.parse(options.body).message);
    return Response.json({ name: `projects/rollcall-test/messages/${sent.length}` });
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const token = await authToken(pair.privateKey);
  const response = await handleApi(new Request("https://rollcall.test/api/auth/challenge", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test" }
  }), {
    FIREBASE_PROJECT_ID: "rollcall-test", FIREBASE_CLIENT_EMAIL: "service@rollcall-test.iam.gserviceaccount.com",
    FIREBASE_PRIVATE_KEY: `-----BEGIN PRIVATE KEY-----\n${serviceKey}\n-----END PRIVATE KEY-----`,
    SESSION_HASH_KEY: "test-session-secret-with-enough-entropy", OTP_ENCRYPTION_KEY: Buffer.alloc(32, 7).toString("base64"),
    DB: database
  });
  assert.equal(response.status, 200);
  assert.deepEqual(new Set(sent.map(message => message.token)), new Set(["trusted-fcm-token"]));
  assert(sent.every(message => message.data.type === "web_login" && message.data.uid === "student-1" && message.android.ttl === "300s"));
});

test("legacy approval setup requires the nonce delivered to the existing enrolled phone", async t => {
  const authPair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const authJwk = await crypto.subtle.exportKey("jwk", authPair.publicKey);
  Object.assign(authJwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const servicePair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const serviceKey = Buffer.from(await crypto.subtle.exportKey("pkcs8", servicePair.privateKey)).toString("base64");
  const devicePair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const publicKey = Buffer.from(await crypto.subtle.exportKey("spki", devicePair.publicKey)).toString("base64url");
  const originalFetch = globalThis.fetch, messages = [];
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("securetoken@system.gserviceaccount.com")) return Response.json({ keys: [authJwk] });
    if (String(url) === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "service-token" });
    messages.push(JSON.parse(options.body).message);
    return Response.json({ name: `projects/rollcall-test/messages/${messages.length}` });
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const token = await authToken(authPair.privateKey);
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", FIREBASE_CLIENT_EMAIL: "service@rollcall-test.iam.gserviceaccount.com",
    FIREBASE_PRIVATE_KEY: `-----BEGIN PRIVATE KEY-----\n${serviceKey}\n-----END PRIVATE KEY-----`,
    SESSION_HASH_KEY: "test-session-secret-with-enough-entropy", DB: fakeDatabase() };
  const headers = { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" };
  const start = await handleApi(new Request("https://rollcall.test/api/device/approval/enroll-existing", {
    method: "POST", headers, body: JSON.stringify({ deviceId: "old-device-id-1234" })
  }), env);
  assert.equal(start.status, 200);
  const startBody = await start.json();
  assert.equal("nonce" in startBody, false);
  assert.equal(messages.length, 1);
  assert.equal(messages[0].token, "trusted-fcm-token");
  assert.equal(messages[0].data.type, "approval_key_enroll");

  const nonce = messages[0].data.nonce, requestId = startBody.requestId;
  const signed = `rollcall:enroll-approval:v1\nstudent-1\nold-device-id-1234\n${requestId}\n${nonce}`;
  const signature = Buffer.from(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", devicePair.privateKey, new TextEncoder().encode(signed))).toString("base64url");
  const complete = await handleApi(new Request("https://rollcall.test/api/device/approval/enroll-complete", {
    method: "POST", headers, body: JSON.stringify({ deviceId: "old-device-id-1234", requestId, nonce, publicKey, signature })
  }), env);
  assert.equal(complete.status, 200, JSON.stringify(await complete.clone().json()));
  assert.equal(env.DB.state.approvalDevices[0].public_key, publicKey);

  const replay = await handleApi(new Request("https://rollcall.test/api/device/approval/enroll-complete", {
    method: "POST", headers, body: JSON.stringify({ deviceId: "old-device-id-1234", requestId, nonce, publicKey, signature })
  }), env);
  assert.equal(replay.status, 401);
});

test("browser approval requires the enrolled phone signature and creates one session", async t => {
  const authPair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const authJwk = await crypto.subtle.exportKey("jwk", authPair.publicKey);
  Object.assign(authJwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const devicePair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const publicKey = Buffer.from(await crypto.subtle.exportKey("spki", devicePair.publicKey)).toString("base64url");
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [authJwk] });
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase(), token = await authToken(authPair.privateKey), now = Math.floor(Date.now() / 1000);
  database.state.approvalDevices[0].public_key = publicKey;
  database.state.challenge = { id: "browser-approval-123456", uid: "student-1", match_code: "42", expires_at: now + 300,
    attempts: 0, consumed_at: null, approved_at: null, denied_at: null };
  const headers = { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" };
  const message = "rollcall:web-approve:v1\nstudent-1\nold-device-id-1234\nbrowser-approval-123456\n42";
  const signature = Buffer.from(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", devicePair.privateKey, new TextEncoder().encode(message))).toString("base64url");
  const wrongSignatureBytes = Buffer.from(signature, "base64url"); wrongSignatureBytes[0] ^= 1;
  const rejected = await handleApi(new Request("https://rollcall.test/api/auth/approve", { method: "POST", headers,
    body: JSON.stringify({ deviceId: "old-device-id-1234", challengeId: "browser-approval-123456", signature: wrongSignatureBytes.toString("base64url") })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(rejected.status, 403);
  assert.equal(database.state.challenge.approved_at, null);

  const approved = await handleApi(new Request("https://rollcall.test/api/auth/approve", { method: "POST", headers,
    body: JSON.stringify({ deviceId: "old-device-id-1234", challengeId: "browser-approval-123456", signature })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(approved.status, 200);
  assert.equal(database.state.challenge.approved_by_device_id, "old-device-id-1234");

  const completed = await handleApi(new Request("https://rollcall.test/api/auth/status", { method: "POST", headers: { origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ challengeId: "browser-approval-123456" })
  }), { SESSION_HASH_KEY: "test-session-secret-with-enough-entropy", DB: database });
  assert.equal(completed.status, 200);
  assert.match(completed.headers.get("set-cookie"), /HttpOnly/);
  assert.equal(database.state.session.uid, "student-1");
  assert.ok(database.state.challenge.consumed_at);
});

test("web sign-in codes cannot be retrieved through a device-ID endpoint", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  t.after(() => { globalThis.fetch = originalFetch; });

  const database = fakeDatabase(), token = await authToken(pair.privateKey);
  database.state.challenge = {
    id: "challenge-for-untrusted-app", uid: "student-1", code_hash: await hmac("123456", "test-session-secret"),
    code_iv: "AA", code_cipher: "AA", expires_at: Math.floor(Date.now() / 1000) + 300, attempts: 0, consumed_at: null
  };
  database.state.appDevices.push({ uid: "student-1", device_id: "tablet-device-1234", fcm_token: "tablet-fcm-token" });
  const response = await handleApi(new Request("https://rollcall.test/api/auth/pending", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "tablet-device-1234", challengeId: "challenge-for-untrusted-app" })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", SESSION_HASH_KEY: "test-session-secret", OTP_ENCRYPTION_KEY: Buffer.alloc(32, 7).toString("base64"), DB: database });
  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), { error: "Not found." });
});

test("app trust registration is independent of sync and can be revoked on sign-out", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase(), token = await authToken(pair.privateKey);
  const headers = { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" };
  const trust = await handleApi(new Request("https://rollcall.test/api/device/trust", {
    method: "POST", headers, body: JSON.stringify({ deviceId: "second-device-1234", fcmToken: "second-fcm-token" })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(trust.status, 200);
  assert.equal(database.state.appDevices.find(row => row.device_id === "second-device-1234").fcm_token, "second-fcm-token");
  database.state.appDevices.push({ device_id: "owned-by-other-1234", uid: "different-user", fcm_token: "other-fcm-token" });
  const conflict = await handleApi(new Request("https://rollcall.test/api/device/trust", {
    method: "POST", headers, body: JSON.stringify({ deviceId: "owned-by-other-1234", fcmToken: "attacker-fcm-token" })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(conflict.status, 409);
  assert.equal(database.state.appDevices.find(row => row.device_id === "owned-by-other-1234").uid, "different-user");
  assert.equal(database.state.appDevices.find(row => row.device_id === "owned-by-other-1234").fcm_token, "other-fcm-token");
  const revoke = await handleApi(new Request("https://rollcall.test/api/device/untrust", {
    method: "POST", headers, body: JSON.stringify({ deviceId: "second-device-1234" })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(revoke.status, 200);
  assert.equal(database.state.appDevices.some(row => row.device_id === "second-device-1234"), false);
});

test("rejects oversized streamed requests without a content-length header", async () => {
  let cancelled = false;
  const body = new ReadableStream({
    start(controller) { controller.enqueue(new Uint8Array(1_900_001)); },
    cancel() { cancelled = true; }
  });
  const response = await handleApi(new Request("https://rollcall.test/api/auth/verify", {
    method: "POST", body, duplex: "half"
  }), {});
  assert.equal(response.status, 413);
  assert.deepEqual(await response.json(), { error: "Request is too large." });
  assert.equal(cancelled, true);
});

test("browser sessions expire after 30 days and expired cookies are rejected", async () => {
  const database = fakeDatabase();
  const secret = "test-session-secret-with-enough-entropy";
  const now = Math.floor(Date.now() / 1000);
  database.state.challenge = {
    id: "challenge-1", uid: "student-1", code_hash: await hmac("123456", secret),
    expires_at: now + 300, attempts: 0, consumed_at: null
  };
  const env = { SESSION_HASH_KEY: secret, DB: database };
  const verified = await handleApi(new Request("https://rollcall.test/api/auth/verify", {
    method: "POST", headers: { origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ challengeId: "challenge-1", code: "123456" })
  }), env);
  assert.equal(verified.status, 200);
  assert.match(verified.headers.get("set-cookie"), /Max-Age=2592000/);
  assert.equal(database.state.session.expires_at - database.state.session.created_at, 30 * 24 * 60 * 60);

  const cookie = verified.headers.get("set-cookie").split(";")[0];
  const active = await handleApi(new Request("https://rollcall.test/api/auth/session", { headers: { cookie } }), env);
  assert.deepEqual(await active.json(), { signedIn: true, updatedAt: 0 });

  database.state.session.expires_at = Math.floor(Date.now() / 1000) - 1;
  const expired = await handleApi(new Request("https://rollcall.test/api/auth/session", { headers: { cookie } }), env);
  assert.deepEqual(await expired.json(), { signedIn: false });
});

test("cloud snapshot deletion requires a verified, recently authenticated owner", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase(), token = await authToken(pair.privateKey);
  const response = await handleApi(new Request("https://rollcall.test/api/snapshot", {
    method: "DELETE", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test" }
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true });
  assert.equal(database.state.deletedTables.includes("snapshots"), true);
  const staleToken = await authToken(pair.privateKey, { auth_time: Math.floor(Date.now() / 1000) - 600 });
  const rejected = await handleApi(new Request("https://rollcall.test/api/snapshot", {
    method: "DELETE", headers: { authorization: `Bearer ${staleToken}`, origin: "https://rollcall.test" }
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(rejected.status, 401);
});

test("server sync gate holds across pause and cloud-copy deletion until explicit re-enable", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase(), token = await authToken(pair.privateKey);
  const headers = { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" };
  const payload = { schemaVersion: 1, target: 75, courses: [], timetable: [], classMarks: [], leaves: [], academicRecords: [], exams: [] };
  const pause = await handleApi(new Request("https://rollcall.test/api/device/sync/pause", { method: "POST", headers, body: "{}" }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(pause.status, 200);
  assert.equal(database.state.syncEnabled, 0);
  const tokenRefresh = await handleApi(new Request("https://rollcall.test/api/device/register", {
    method: "POST", headers, body: JSON.stringify({ deviceId: database.state.deviceId, fcmToken: "phone-token", enableSync: false })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(tokenRefresh.status, 200);
  assert.equal(database.state.syncEnabled, 0);
  const blocked = await handleApi(new Request("https://rollcall.test/api/snapshot", {
    method: "PUT", headers, body: JSON.stringify({ deviceId: database.state.deviceId, snapshot: payload })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(blocked.status, 403);
  const reconnect = await handleApi(new Request("https://rollcall.test/api/device/register", {
    method: "POST", headers, body: JSON.stringify({ deviceId: database.state.deviceId, fcmToken: "phone-token", enableSync: true })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(reconnect.status, 200);
  const allowed = await handleApi(new Request("https://rollcall.test/api/snapshot", {
    method: "PUT", headers, body: JSON.stringify({ deviceId: database.state.deviceId, snapshot: payload })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(allowed.status, 200);
  assert.ok(database.state.snapshot);
  const deleteCopy = await handleApi(new Request("https://rollcall.test/api/snapshot", { method: "DELETE", headers }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(deleteCopy.status, 200);
  assert.equal(database.state.snapshot, null);
  assert.equal(database.state.syncEnabled, 0);
  const staleUpload = await handleApi(new Request("https://rollcall.test/api/snapshot", {
    method: "PUT", headers, body: JSON.stringify({ deviceId: database.state.deviceId, snapshot: payload })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(staleUpload.status, 403);
});

test("browser sessions are listed without cookie secrets and require recent auth to revoke", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase(), token = await authToken(pair.privateKey);
  database.state.browserSessions.push({ session_id: "active-session-identifier-1", uid: "student-1", created_at: 1, last_used_at: 2, expires_at: 4_000_000_000, user_agent: "Chrome on Linux" });
  const headers = { authorization: `Bearer ${token}`, origin: "https://rollcall.test" };
  const listed = await handleApi(new Request("https://rollcall.test/api/auth/sessions", { headers }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  const listBody = await listed.json();
  assert.equal(listed.status, 200);
  assert.equal(listBody.sessions.length, 1);
  assert.equal("session_hash" in listBody.sessions[0], false);
  const revoked = await handleApi(new Request("https://rollcall.test/api/auth/sessions/revoke", {
    method: "POST", headers: { ...headers, "content-type": "application/json" }, body: JSON.stringify({ sessionId: "active-session-identifier-1" })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.deepEqual(await revoked.json(), { ok: true, revoked: 1 });
  assert.equal(database.state.browserSessions.length, 0);
  database.state.browserSessions.push({ session_id: "active-session-identifier-2", uid: "student-1", expires_at: 4_000_000_000 });
  const staleToken = await authToken(pair.privateKey, { auth_time: Math.floor(Date.now() / 1000) - 600 });
  const rejected = await handleApi(new Request("https://rollcall.test/api/auth/sessions/revoke", {
    method: "POST", headers: { ...headers, authorization: `Bearer ${staleToken}`, "content-type": "application/json" }, body: JSON.stringify({ all: true })
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(rejected.status, 401);
  assert.equal(database.state.browserSessions.length, 1);
});

test("rate limits sign-in challenge sends across consumed challenges", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async url => {
    assert.match(String(url), /securetoken@system\.gserviceaccount\.com/);
    return Response.json({ keys: [jwk] });
  };
  t.after(() => { globalThis.fetch = originalFetch; });

  const database = fakeDatabase();
  const now = Math.floor(Date.now() / 1000);
  database.state.challengeSends = Array.from({ length: 5 }, (_, i) => ({ uid: "student-1", created_at: now - i }));
  const token = await authToken(pair.privateKey);
  const response = await handleApi(new Request("https://rollcall.test/api/auth/challenge", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test" }
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(response.status, 429);
  assert.match((await response.json()).error, /Too many code requests/);
  assert.equal(database.state.challengeSends.length, 5);
});

test("wrong web sign-in codes stop incrementing at the five-attempt cap", async () => {
  const database = fakeDatabase();
  database.state.challenge = {
    id: "challenge-cap", uid: "student-1", code_hash: await hmac("123456", "cap-test-secret"),
    expires_at: Math.floor(Date.now() / 1000) + 300, attempts: 0, consumed_at: null
  };
  const env = { SESSION_HASH_KEY: "cap-test-secret", DB: database };
  for (let attempt = 0; attempt < 5; attempt++) {
    const response = await handleApi(new Request("https://rollcall.test/api/auth/verify", {
      method: "POST", headers: { origin: "https://rollcall.test", "content-type": "application/json" },
      body: JSON.stringify({ challengeId: "challenge-cap", code: "000000" })
    }), env);
    assert.equal(response.status, 401);
    assert.equal(database.state.challenge.attempts, attempt + 1);
  }
  const capped = await handleApi(new Request("https://rollcall.test/api/auth/verify", {
    method: "POST", headers: { origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ challengeId: "challenge-cap", code: "000000" })
  }), env);
  assert.equal(capped.status, 401);
  assert.equal(database.state.challenge.attempts, 5);
});

test("account deletion removes every per-user row including the challenge send ledger", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async url => {
    assert.match(String(url), /securetoken@system\.gserviceaccount\.com/);
    return Response.json({ keys: [jwk] });
  };
  t.after(() => { globalThis.fetch = originalFetch; });

  const database = fakeDatabase();
  database.state.challengeSends.push({ uid: "student-1", created_at: Math.floor(Date.now() / 1000) });
  const staleToken = await authToken(pair.privateKey, { auth_time: Math.floor(Date.now() / 1000) - 301 });
  const staleResponse = await handleApi(new Request("https://rollcall.test/api/account/delete", {
    method: "POST", headers: { authorization: `Bearer ${staleToken}`, origin: "https://rollcall.test" }
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(staleResponse.status, 401);
  assert.equal(database.state.deletedTables.length, 0);
  const futureToken = await authToken(pair.privateKey, { auth_time: Math.floor(Date.now() / 1000) + 61 });
  const futureResponse = await handleApi(new Request("https://rollcall.test/api/account/delete", {
    method: "POST", headers: { authorization: `Bearer ${futureToken}`, origin: "https://rollcall.test" }
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(futureResponse.status, 401);
  assert.equal(database.state.deletedTables.length, 0);
  const token = await authToken(pair.privateKey);
  const response = await handleApi(new Request("https://rollcall.test/api/account/delete", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test" }
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true });
  assert.equal(database.state.challengeSends.length, 0);
  assert.deepEqual(database.state.deletedTables.sort(), ["app_devices", "approval_devices", "approval_enrollment_requests", "device_replacement_codes", "device_replacement_sends", "recovery_codes", "snapshots", "trusted_devices", "web_challenge_sends", "web_challenges", "web_sessions"].sort());
});
