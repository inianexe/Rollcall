import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { handleApi } from "./_api.js";

globalThis.crypto ??= webcrypto;

function encode(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

async function authToken(privateKey) {
  const header = encode({ alg: "RS256", kid: "test-key" });
  const payload = encode({
    aud: "rollcall-test", iss: "https://securetoken.google.com/rollcall-test", sub: "student-1",
    email: "student@example.com", email_verified: true,
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600
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
    sends: [], challengeSends: [], code: null, challenge: null, session: null,
    deviceId: "old-device-id-1234", sessionsDeleted: 0
  };
  return {
    state,
    prepare(sql) {
      let args = [];
      return {
        bind(...values) { args = values; return this; },
        async first() {
          if (sql.includes("SELECT uid FROM web_sessions"))
            return state.session && state.session.expires_at > args[1] ? { uid: state.session.uid } : null;
          if (sql.includes("SELECT device_id FROM trusted_devices")) return { device_id: state.deviceId };
          if (sql.includes("SELECT fcm_token FROM trusted_devices")) return state.deviceId ? { fcm_token: "test-fcm-token" } : null;
          if (sql.includes("FROM web_challenges WHERE id=? AND consumed_at IS NULL"))
            return state.challenge && !state.challenge.consumed_at && state.challenge.attempts < 5
              && state.challenge.expires_at > args[1] ? state.challenge : null;
          if (sql.includes("SELECT code_hash FROM device_replacement_codes"))
            return state.code && !state.code.used_at && state.code.attempts < 5 && state.code.expires_at > args[1] ? state.code : null;
          if (sql.includes("SELECT created_at FROM device_replacement_email_sends")) return state.sends.at(-1) || null;
          if (sql.includes("SELECT COUNT(*) AS n FROM device_replacement_email_sends")) return { n: state.sends.length };
          return null;
        },
        async run() {
          if (sql.startsWith("DELETE FROM device_replacement_email_sends")) state.sends = state.sends.filter(row => row.created_at >= args[0]);
          if (sql.startsWith("INSERT INTO device_replacement_email_sends")) state.sends.push({ uid: args[0], created_at: args[1] });
          if (sql.startsWith("DELETE FROM web_challenge_sends")) state.challengeSends = state.challengeSends.filter(row => row.created_at >= args[0]);
          if (sql.startsWith("INSERT INTO web_challenge_sends")) {
            const allowed = state.challengeSends.filter(row => row.uid === args[2] && row.created_at > args[3]).length < 5;
            if (allowed) state.challengeSends.push({ uid: args[0], created_at: args[1] });
            return { meta: { changes: allowed ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE web_challenges SET consumed_at") && state.challenge) state.challenge.consumed_at = args[0];
          if (sql.startsWith("INSERT INTO web_sessions")) state.session = { session_hash: args[0], uid: args[1], created_at: args[2], expires_at: args[3] };
          if (sql.startsWith("INSERT INTO device_replacement_codes")) state.code = { uid: args[0], code_hash: args[1], created_at: args[2], expires_at: args[3], attempts: 0, used_at: null };
          if (sql.startsWith("UPDATE device_replacement_codes SET used_at")) state.code.used_at = args[0];
          if (sql.startsWith("UPDATE device_replacement_codes SET attempts")) state.code.attempts++;
          if (sql.startsWith("DELETE FROM web_sessions")) { state.sessionsDeleted++; state.session = null; }
          if (sql.startsWith("INSERT INTO trusted_devices")) state.deviceId = args[1];
          return { meta: { changes: 1 } };
        }
      };
    }
  };
}

test("replacement confirmation emails the verified account and stores only a short-lived hash", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  const database = fakeDatabase();
  let email;
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("securetoken@system.gserviceaccount.com")) return Response.json({ keys: [jwk] });
    assert.equal(String(url), "https://api.resend.com/emails");
    assert.equal(options.headers.authorization, "Bearer test-resend-key");
    email = JSON.parse(options.body);
    return Response.json({ id: "email-test" }, { status: 200 });
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const token = await authToken(pair.privateKey);
  const env = {
    FIREBASE_PROJECT_ID: "rollcall-test", SESSION_HASH_KEY: "test-session-secret-with-enough-entropy",
    RESEND_API_KEY: "test-resend-key", ROLLCALL_EMAIL_FROM: "Rollcall <security@example.com>", DB: database
  };
  const response = await handleApi(new Request("https://rollcall.test/api/device/replacement-code", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test" }
  }), env);
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.deepEqual(body, { ok: true, expiresIn: 600 });
  assert.equal(email.to[0], "student@example.com");
  const code = email.text.match(/\n(\d{6})\n/)[1];
  assert.equal(database.state.code.code_hash, await hmac(code, env.SESSION_HASH_KEY));
  assert.match(email.subject, /Confirm your new Rollcall phone/);
  assert.match(email.html, new RegExp(code));
  assert.equal(database.state.code.code_hash === code, false);
  assert.equal(database.state.code.expires_at - database.state.code.created_at, 600);
  assert.equal(JSON.stringify(body).includes(code), false);

  const wrongCode = await handleApi(new Request("https://rollcall.test/api/device/register", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "new-device-id-1234", fcmToken: "test-fcm", emailCode: code === "000000" ? "000001" : "000000" })
  }), env);
  assert.equal(wrongCode.status, 403);
  assert.equal((await wrongCode.json()).code, "email_code_invalid");
  assert.equal(database.state.code.attempts, 1);

  const linked = await handleApi(new Request("https://rollcall.test/api/device/register", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "new-device-id-1234", fcmToken: "test-fcm", emailCode: code })
  }), env);
  assert.equal(linked.status, 200, JSON.stringify(await linked.clone().json()));
  assert.equal(database.state.deviceId, "new-device-id-1234");
  assert.equal(database.state.sessionsDeleted, 1);

  const reused = await handleApi(new Request("https://rollcall.test/api/device/register", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "another-device-id-1234", fcmToken: "test-fcm", emailCode: code })
  }), env);
  assert.equal(reused.status, 403);
  assert.equal((await reused.json()).code, "email_code_expired");
  assert.equal(database.state.deviceId, "new-device-id-1234");

  const limited = await handleApi(new Request("https://rollcall.test/api/device/replacement-code", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test" }
  }), env);
  assert.equal(limited.status, 429);
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
