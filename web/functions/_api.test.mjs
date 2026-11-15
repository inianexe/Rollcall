import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { handleApi } from "./_api.js";

globalThis.crypto ??= webcrypto;

function encode(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

async function authToken(privateKey, overrides = {}) {
  const header = encode({ alg: "RS256", kid: "test-key" });
  const payload = encode({
    aud: "rollcall-test", iss: "https://securetoken.google.com/rollcall-test", sub: "student-1",
    email: "student@example.com", email_verified: true, ...overrides,
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
    sends: [], challengeSends: [], qrSends: [], qrChallenges: [], code: null, challenge: null, session: null,
    deviceId: "old-device-id-1234", deviceCount: 1, sessionsDeleted: 0, deletedTables: [], snapshot: null, adminAudit: [], campaigns: [], receipts: [], pendingSignups: [], signupSends: [], releases: [],
    accountRows: Object.fromEntries(["trusted_devices", "snapshots", "web_challenges", "web_challenge_sends", "web_sessions", "web_qr_challenges", "device_replacement_codes", "device_replacement_email_sends"].map(table => [table, 1]))
  };
  return {
    state,
    async batch(statements) { await Promise.all(statements.map(statement => statement.run())); },
    prepare(sql) {
      let args = [];
      return {
        bind(...values) { args = values; return this; },
        async first() {
          if (sql === "SELECT * FROM devos_releases WHERE id=?") return state.releases.find(row => row.id === args[0]) || null;
          if (sql.startsWith("SELECT * FROM devos_releases WHERE worker_attempts<3")) return state.releases.find(row =>
            row.worker_attempts < 3 && (["validation_queued", "publish_queued"].includes(row.status)
              || (["validating", "publishing"].includes(row.status) && row.updated_at < args[0]))) || null;
          if (sql.startsWith("SELECT MAX(version_code) AS code FROM devos_releases"))
            return { code: Math.max(0, ...state.releases.filter(row => row.status === "published").map(row => row.version_code || 0)) };
          if (sql.includes("COUNT(*) AS count FROM trusted_devices")) return { count: state.deviceCount };
          if (sql.includes("COUNT(*) AS count FROM command_center_campaigns")) {
            let rows = state.campaigns;
            if (sql.includes("status='published'")) rows = rows.filter(row => row.status === "published");
            if (sql.includes("push_status='failed'")) rows = rows.filter(row => row.push_status === "failed");
            if (sql.includes("expires_at>?")) rows = rows.filter(row => row.expires_at > args[0]);
            return { count: rows.length };
          }
          if (sql.includes("FROM command_center_campaigns WHERE id=?")) return state.campaigns.find(row => row.id === args[0]) || null;
          if (sql.includes("SELECT uid FROM web_sessions"))
            return state.session && state.session.session_hash === args[0] && state.session.expires_at > args[1] ? { uid: state.session.uid } : null;
          if (sql.includes("SELECT device_id FROM trusted_devices")) return { device_id: state.deviceId };
          if (sql.includes("FROM web_qr_challenges WHERE id=?")) {
            const row = state.qrChallenges.find(item => item.id === args[0]);
            return row && row.expires_at > args[1] && !row.consumed_at ? row : null;
          }
          if (sql.includes("SELECT fcm_token FROM trusted_devices")) return state.deviceId ? { fcm_token: "test-fcm-token" } : null;
          if (sql.includes("SELECT snapshot_json,updated_at FROM snapshots")) return state.snapshot ? { snapshot_json: state.snapshot, updated_at: 1 } : null;
          if (sql.includes("FROM web_challenges WHERE id=? AND consumed_at IS NULL"))
            return state.challenge && !state.challenge.consumed_at && state.challenge.attempts < 5
              && state.challenge.expires_at > args[1] ? state.challenge : null;
          if (sql.includes("SELECT code_hash FROM device_replacement_codes"))
            return state.code && !state.code.used_at && state.code.attempts < 5 && state.code.expires_at > args[1] ? state.code : null;
          if (sql.includes("SELECT created_at FROM device_replacement_email_sends")) return state.sends.at(-1) || null;
          if (sql.includes("SELECT COUNT(*) AS n FROM device_replacement_email_sends")) return { n: state.sends.length };
          if (sql.includes("FROM pending_signups WHERE email=?")) {
            const signup = state.pendingSignups.find(item => item.email === args[0]);
            return signup && !signup.used_at && signup.expires_at > args[1] && signup.attempts < 5 ? signup : null;
          }
          if (sql.includes("SELECT COUNT(*) AS n FROM signup_verification_sends WHERE ip_hash")) return { n: state.signupSends.filter(item => item.ip_hash === args[0] && item.created_at > args[1]).length };
          if (sql.includes("SELECT created_at FROM signup_verification_sends")) return state.signupSends.filter(item => item.email === args[0]).at(-1) || null;
          if (sql.includes("SELECT COUNT(*) AS n FROM signup_verification_sends")) return { n: state.signupSends.filter(item => item.email === args[0] && item.created_at > args[1]).length };
          return null;
        },
        async all() {
          if (sql.startsWith("SELECT * FROM devos_releases ORDER BY")) return { results: [...state.releases].reverse() };
          if (sql.includes("FROM command_center_campaigns ORDER BY"))
            return { results: [...state.campaigns].sort((a, b) => b.created_at - a.created_at).slice(0, 100) };
          if (sql.includes("FROM command_center_campaigns c LEFT JOIN command_center_campaign_receipts"))
            return { results: state.campaigns.filter(row => row.status === "published" && row.expires_at > args[1])
              .sort((a, b) => b.published_at - a.published_at).slice(0, 50)
              .map(row => ({ ...row, receipt_state: state.receipts.find(item => item.campaign_id === row.id && item.uid === args[0])?.state || null })) };
          if (sql.includes("FROM command_center_audit"))
            return { results: [...state.adminAudit].sort((a, b) => b.created_at - a.created_at).slice(0, 20)
              .map(({ action, created_at, result }) => ({ action, created_at, result })) };
          return { results: [] };
        },
        async run() {
          if (sql.startsWith("INSERT INTO devos_releases")) state.releases.push({ id: args[0], created_by: args[1], status: "draft",
            channel: "stable", policy: args[2], notes: args[3], file_name: "", size_bytes: 0, sha256: "",
            version_code: null, version_name: null, min_sdk: null, target_sdk: null, signer_sha256: null,
            failure_reason: "", release_url: "", push_status: "not_requested", approval_code_hash: null,
            approval_expires_at: null, approval_sent_at: null, approval_attempts: 0, worker_attempts: 0,
            created_at: args[4], updated_at: args[5], validated_at: null, published_at: null });
          if (sql.startsWith("UPDATE devos_releases SET status='uploaded'")) {
            const row = state.releases.find(item => item.id === args[4] && item.status === "draft");
            if (row) Object.assign(row, { status: "uploaded", file_name: args[0], size_bytes: args[1], sha256: args[2], updated_at: args[3] });
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE devos_releases SET status='validation_queued'")) {
            const row = state.releases.find(item => item.id === args[1] && ["uploaded", "failed"].includes(item.status));
            if (row) Object.assign(row, { status: "validation_queued", failure_reason: "", worker_attempts: 0, updated_at: args[0] });
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE devos_releases SET status=?,worker_attempts=")) {
            const row = state.releases.find(item => item.id === args[2] && item.status === args[3] && item.updated_at === args[4]);
            if (row) Object.assign(row, { status: args[0], worker_attempts: row.worker_attempts + 1, updated_at: args[1] });
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE devos_releases SET status=?,version_code=")) {
            const row = state.releases.find(item => item.id === args[9] && item.status === "validating");
            if (row) Object.assign(row, { status: args[0], version_code: args[1], version_name: args[2],
              min_sdk: args[3], target_sdk: args[4], signer_sha256: args[5], failure_reason: args[6],
              validated_at: args[7], updated_at: args[8] });
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE devos_releases SET approval_code_hash=?")) {
            const row = state.releases.find(item => item.id === args[3] && item.status === "validated");
            if (row) Object.assign(row, { approval_code_hash: args[0], approval_expires_at: args[1],
              approval_sent_at: args[2], approval_attempts: 0 });
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE devos_releases SET status='publish_queued'")) {
            const row = state.releases.find(item => item.id === args[1] && item.status === "validated" && item.approval_code_hash === args[2]);
            if (row) Object.assign(row, { status: "publish_queued", updated_at: args[0], approval_code_hash: null,
              approval_expires_at: null, worker_attempts: 0 });
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("INSERT INTO command_center_audit")) state.adminAudit.push({ id: args[0], actor_uid: args[1], action: args[2], created_at: args[3], result: "success" });
          if (sql.startsWith("INSERT INTO command_center_campaigns")) state.campaigns.push({
            id: args[0], title: args[1], body: args[2], category: args[3], priority: args[4], channels_json: args[5],
            action_label: args[6], action_path: args[7], status: "draft", created_by: args[8], created_at: args[9],
            updated_at: args[10], published_at: null, expires_at: args[11], push_status: "not_requested"
          });
          if (sql.startsWith("UPDATE command_center_campaigns SET status='published'")) {
            const row = state.campaigns.find(item => item.id === args[3] && item.status === "draft");
            if (row) Object.assign(row, { status: "published", published_at: args[0], updated_at: args[1], push_status: args[2] });
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE command_center_campaigns SET status='revoked'")) {
            const row = state.campaigns.find(item => item.id === args[1] && item.status === "published");
            if (row) Object.assign(row, { status: "revoked", updated_at: args[0] });
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE command_center_campaigns SET push_status=")) {
            const row = state.campaigns.find(item => item.id === args.at(-1));
            if (row) { row.push_status = args[0]; row.updated_at = args[1]; }
          }
          if (sql.startsWith("UPDATE command_center_campaigns SET push_status='pending'")) {
            const row = state.campaigns.find(item => item.id === args[1]);
            if (row) Object.assign(row, { push_status: "pending", updated_at: args[0] });
          }
          if (sql.startsWith("INSERT INTO command_center_campaign_receipts")) {
            const [uid, receiptState, updatedAt, id, expiresAt] = args;
            const row = state.campaigns.find(item => item.id === id && item.status === "published" && item.expires_at > expiresAt);
            if (row) {
              const receipt = state.receipts.find(item => item.campaign_id === id && item.uid === uid);
              if (receipt) Object.assign(receipt, { state: receiptState, updated_at: updatedAt });
              else state.receipts.push({ campaign_id: id, uid, state: receiptState, updated_at: updatedAt });
            }
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("DELETE FROM device_replacement_email_sends")) state.sends = state.sends.filter(row => row.created_at >= args[0]);
          if (sql.startsWith("INSERT INTO device_replacement_email_sends")) state.sends.push({ uid: args[0], created_at: args[1] });
          if (sql.startsWith("DELETE FROM signup_verification_sends WHERE created_at")) state.signupSends = state.signupSends.filter(item => item.created_at >= args[0]);
          if (sql.startsWith("INSERT INTO signup_verification_sends")) state.signupSends.push({ email: args[0], ip_hash: args[1], created_at: args[2] });
          if (sql.startsWith("INSERT INTO pending_signups")) {
            const existing = state.pendingSignups.find(item => item.email === args[0]);
            const row = { email: args[0], code_hash: args[1], created_at: args[2], expires_at: args[3], attempts: 0, used_at: null };
            if (existing) Object.assign(existing, row); else state.pendingSignups.push(row);
          }
          if (sql.startsWith("UPDATE pending_signups SET attempts")) {
            const row = state.pendingSignups.find(item => item.email === args[0] && !item.used_at && item.expires_at > args[1] && item.attempts < 5);
            if (row) row.attempts++;
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE pending_signups SET used_at")) {
            const row = state.pendingSignups.find(item => item.email === args[1] && !item.used_at && item.expires_at > args[2] && item.attempts < 5);
            if (row) row.used_at = args[0];
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("DELETE FROM pending_signups")) state.pendingSignups = state.pendingSignups.filter(item => item.email !== args[0] || (args[1] && item.created_at !== args[1]));
          if (sql.startsWith("DELETE FROM web_challenge_sends")) state.challengeSends = state.challengeSends.filter(row => row.created_at >= args[0]);
          if (sql.startsWith("DELETE FROM web_qr_challenge_sends")) state.qrSends = state.qrSends.filter(row => row.created_at >= args[0]);
          if (sql.startsWith("DELETE FROM web_qr_challenges WHERE expires_at")) state.qrChallenges = state.qrChallenges.filter(row => row.expires_at > args[0] && !row.consumed_at);
          if (sql.startsWith("INSERT INTO web_qr_challenge_sends")) {
            const allowed = state.qrSends.filter(row => row.ip_hash === args[2] && row.created_at > args[3]).length < 10;
            if (allowed) state.qrSends.push({ ip_hash: args[0], created_at: args[1] });
            return { meta: { changes: allowed ? 1 : 0 } };
          }
          if (sql.startsWith("INSERT INTO web_qr_challenges")) state.qrChallenges.push({
            id: args[0], secret_hash: args[1], state: "pending", uid: null, browser_label: args[2], created_at: args[3], expires_at: args[4], consumed_at: null
          });
          if (sql.startsWith("UPDATE web_qr_challenges SET state='approved'")) {
            const row = state.qrChallenges.find(item => item.id === args[1] && item.state === "pending" && item.expires_at > args[2] && !item.consumed_at);
            if (row) { row.state = "approved"; row.uid = args[0]; }
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE web_qr_challenges SET state='denied'")) {
            const row = state.qrChallenges.find(item => item.id === args[1] && item.state === "pending" && item.expires_at > args[2] && !item.consumed_at);
            if (row) { row.state = "denied"; row.uid = args[0]; }
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE web_qr_challenges SET state='consumed'")) {
            const row = state.qrChallenges.find(item => item.id === args[1] && item.state === "approved" && item.expires_at > args[2] && !item.consumed_at);
            if (row) { row.state = "consumed"; row.consumed_at = args[0]; }
            return { meta: { changes: row ? 1 : 0 } };
          }
          if (sql.startsWith("INSERT INTO web_challenge_sends")) {
            const allowed = state.challengeSends.filter(row => row.uid === args[2] && row.created_at > args[3]).length < 5;
            if (allowed) state.challengeSends.push({ uid: args[0], created_at: args[1] });
            return { meta: { changes: allowed ? 1 : 0 } };
          }
          if (sql.startsWith("UPDATE web_challenges SET consumed_at") && state.challenge) state.challenge.consumed_at = args[0];
          if (sql.startsWith("INSERT INTO web_sessions")) state.session = { session_hash: args[0], uid: args[1], created_at: args[2], expires_at: args[3] };
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
          if (sql.startsWith("DELETE FROM web_sessions")) { state.sessionsDeleted++; state.session = null; }
          if (sql.startsWith("DELETE FROM ") && sql.endsWith(" WHERE uid=?")) {
            const table = sql.slice("DELETE FROM ".length, sql.indexOf(" WHERE uid=?"));
            state.deletedTables.push(table);
            if (table in state.accountRows) state.accountRows[table] = 0;
            if (table === "web_challenge_sends") state.challengeSends = [];
          }
          if (sql.startsWith("INSERT INTO trusted_devices")) state.deviceId = args[1];
          if (sql.startsWith("INSERT INTO snapshots")) state.snapshot = args[1];
          return { meta: { changes: 1 } };
        }
      };
    }
  };
}

test("email signup creates no Firebase user until its emailed code is verified", async t => {
  const database = fakeDatabase(), originalFetch = globalThis.fetch;
  let sentCode = "", firebaseCalls = 0;
  globalThis.fetch = async (url, options = {}) => {
    if (url === "https://api.resend.com/emails") {
      const email = JSON.parse(options.body);
      assert.equal(email.to[0], "student@example.com");
      assert.doesNotMatch(email.text, /correct horse battery staple/);
      sentCode = email.text.match(/\b\d{6}\b/)[0];
      return Response.json({ id: "email-id" });
    }
    if (String(url).includes("identitytoolkit.googleapis.com/v1/accounts:signUp")) {
      firebaseCalls++;
      assert.deepEqual(JSON.parse(options.body), {
        email: "student@example.com", password: "correct horse battery staple", returnSecureToken: true
      });
      return Response.json({ idToken: "temporary-token" });
    }
    if (String(url).includes("identitytoolkit.googleapis.com/v1/accounts:update")) {
      firebaseCalls++;
      assert.deepEqual(JSON.parse(options.body), { idToken: "temporary-token", emailVerified: true, returnSecureToken: false });
      return Response.json({ emailVerified: true });
    }
    throw new Error(`Unexpected fetch: ${url}`);
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const env = { DB: database, SESSION_HASH_KEY: "test-session-secret", RESEND_API_KEY: "test", ROLLCALL_EMAIL_FROM: "Rollcall <auth@example.com>", FIREBASE_WEB_API_KEY: "public-test-key" };
  const call = (path, body) => handleApi(new Request(`https://rollcall.test/api/auth/signup/${path}`, {
    method: "POST", headers: { "content-type": "application/json", origin: "https://rollcall.test" }, body: JSON.stringify(body)
  }), env);

  assert.equal((await call("start", { email: " Student@Example.com " })).status, 200);
  assert.equal(firebaseCalls, 0);
  assert.equal(database.state.pendingSignups[0].email, "student@example.com");
  assert.equal((await call("complete", { email: "student@example.com", code: "000000", password: "correct horse battery staple" })).status, 401);
  assert.equal(firebaseCalls, 0);
  const complete = await call("complete", { email: "student@example.com", code: sentCode, password: "correct horse battery staple" });
  assert.equal(complete.status, 200);
  assert.deepEqual(await complete.json(), { ok: true, emailVerified: true });
  assert.equal(firebaseCalls, 2);
  assert.equal(database.state.pendingSignups.length, 0);
});

test("Command Center sessions require an allowlisted verified Firebase identity and write an audit event", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase();
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", COMMAND_CENTER_ADMIN_UIDS: "owner-1", DB: database };
  const session = async token => handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { authorization: `Bearer ${token}` }
  }), env);

  const allowed = await session(await authToken(pair.privateKey, { sub: "owner-1", email: "owner@example.com" }));
  assert.equal(allowed.status, 200);
  const body = await allowed.json();
  assert.deepEqual({ authorized: body.authorized, role: body.role, email: body.email }, {
    authorized: true, role: "owner", email: "owner@example.com"
  });
  assert.equal(body.recentAccess.length, 1);
  assert.equal(body.recentAccess[0].action, "session_verified");
  assert.equal("actor_uid" in body.recentAccess[0], false);

  assert.equal((await session(await authToken(pair.privateKey))).status, 403);
  const ownerEmailEnv = { FIREBASE_PROJECT_ID: "rollcall-test", COMMAND_CENTER_ADMIN_EMAILS: "owner@example.com", DB: fakeDatabase() };
  const emailOwner = await handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { authorization: `Bearer ${await authToken(pair.privateKey, { sub: "another-uid", email: "OWNER@example.com" })}` }
  }), ownerEmailEnv);
  assert.equal(emailOwner.status, 200);
  assert.equal((await handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { authorization: `Bearer ${await authToken(pair.privateKey, { sub: "another-uid", email: "someone@example.com" })}` }
  }), ownerEmailEnv)).status, 403);
  const phoneToken = "trusted-owner-phone-session";
  database.state.session = {
    uid: "owner-1", expires_at: Math.floor(Date.now() / 1000) + 60,
    session_hash: Buffer.from(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`phone-secret:${phoneToken}`))).toString("hex")
  };
  env.SESSION_HASH_KEY = "phone-secret";
  const phoneSession = await handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { cookie: `rc_session=${phoneToken}`, origin: "https://rollcall.test" }
  }), env);
  assert.equal(phoneSession.status, 200);
  assert.equal((await phoneSession.json()).authorized, true);
  database.state.session.uid = "student-1";
  assert.equal((await handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { cookie: `rc_session=${phoneToken}`, origin: "https://rollcall.test" }
  }), env)).status, 403);
  assert.equal((await handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { authorization: `Bearer ${await authToken(pair.privateKey, { sub: "owner-1", email_verified: false })}` }
  }), env)).status, 401);

  const unconfigured = await handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { authorization: `Bearer ${await authToken(pair.privateKey, { sub: "owner-1" })}` }
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(unconfigured.status, 503);
});

test("DevOS release operations are read-only, admin-gated, and expose only trusted GitHub links", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async url => {
    const value = String(url);
    calls.push(value);
    if (value.includes("securetoken@system")) return Response.json({ keys: [jwk] });
    if (value.includes("/releases?")) return Response.json([{
      tag_name: "v1.0.0", name: "Rollcall 1.0.0", draft: false, prerelease: false,
      published_at: "2026-10-15T18:21:39Z", html_url: "https://github.com/inianexe/Rollcall/releases/tag/v1.0.0",
      assets: [
        { name: "Rollcall.apk", size: 1234567, download_count: 9, browser_download_url: "https://github.com/inianexe/Rollcall/releases/download/v1/Rollcall.apk" },
        { name: "fake.apk", size: 10, download_count: 0, browser_download_url: "https://evil.example/fake.apk" }
      ]
    }]);
    if (value.includes("/actions/runs?")) return Response.json({ workflow_runs: [{
      id: 42, run_number: 8, name: "Android and Web checks", display_title: "Build main",
      status: "completed", conclusion: "success", head_branch: "main", created_at: "2026-11-14T12:00:00Z",
      html_url: "https://github.com/inianexe/Rollcall/actions/runs/42"
    }] });
    throw new Error("Unexpected external request.");
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", COMMAND_CENTER_ADMIN_UIDS: "owner-1", DB: fakeDatabase() };
  const path = "https://rollcall.test/api/admin/release-operations";
  const student = await authToken(pair.privateKey);
  assert.equal((await handleApi(new Request(path, { headers: { authorization: `Bearer ${student}` } }), env)).status, 403);
  assert.equal(calls.length, 1, "unauthorized requests must not call GitHub");

  const owner = await authToken(pair.privateKey, { sub: "owner-1", email: "owner@example.com" });
  const response = await handleApi(new Request(path, { headers: { authorization: `Bearer ${owner}` } }), env);
  assert.equal(response.status, 200);
  const result = await response.json();
  assert.equal(result.releases[0].tag, "v1.0.0");
  assert.equal(result.releases[0].apk.length, 1);
  assert.equal(result.workflows[0].runNumber, 8);
  assert.deepEqual(calls.filter(url => url.includes("api.github.com")).sort(), [
    "https://api.github.com/repos/inianexe/Rollcall/actions/runs?per_page=12",
    "https://api.github.com/repos/inianexe/Rollcall/releases?per_page=8"
  ].sort());
});

test("Release Factory requires an owner, protects staged APKs, validates identity, and requires email approval", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  let approvalCode = "";
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("service_accounts/v1/jwk")) return Response.json({ keys: [jwk] });
    if (url === "https://api.resend.com/emails") {
      const mail = JSON.parse(options.body);
      assert.deepEqual(mail.to, ["owner@example.com"]);
      approvalCode = mail.text.match(/approval code in DevOS: (\d{6})/)[1];
      return Response.json({ id: "release-approval-email" });
    }
    throw new Error(`Unexpected fetch: ${url}`);
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase(), objects = new Map();
  const env = { DB: database, FIREBASE_PROJECT_ID: "rollcall-test", COMMAND_CENTER_ADMIN_UIDS: "owner-1",
    RELEASE_FACTORY_PULL_KEY: "worker-secret", SESSION_HASH_KEY: "session-secret",
    RESEND_API_KEY: "resend-test", ROLLCALL_EMAIL_FROM: "Rollcall <auth@example.com>",
    RELEASE_STAGING: {
      async put(key, value) { objects.set(key, value); },
      async get(key) { return objects.get(key) || null; },
      async delete(key) { objects.delete(key); }
    } };
  const owner = await authToken(pair.privateKey, { sub: "owner-1", email: "owner@example.com" });
  const student = await authToken(pair.privateKey);
  const call = (path, method = "GET", body, token = owner, extraHeaders = {}) => handleApi(new Request(`https://rollcall.test${path}`, {
    method, headers: { ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...(method === "POST" ? { origin: "https://rollcall.test" } : {}),
      ...(body && !(body instanceof Uint8Array) ? { "content-type": "application/json" } : {}), ...extraHeaders },
    ...(body ? { body: body instanceof Uint8Array ? body : JSON.stringify(body) } : {})
  }), env);
  assert.equal((await call("/api/admin/releases", "POST", { notes: "Safer sync", channel: "stable", policy: "optional", confirmAllInstallations: true }, student)).status, 403);
  assert.equal((await call("/api/release-worker/next", "GET", null, null, { "x-rollcall-release-key": "wrong" })).status, 403);
  const draft = await call("/api/admin/releases", "POST", { notes: "Safer sync", channel: "stable", policy: "optional", confirmAllInstallations: true });
  assert.equal(draft.status, 201);
  const id = (await draft.json()).release.id;
  const apk = new Uint8Array(1_000_001).fill(65);
  const uploaded = await call(`/api/admin/releases/${id}/apk`, "POST", apk, owner,
    { "content-type": "application/vnd.android.package-archive", "x-rollcall-file-name": "Rollcall.apk" });
  assert.equal(uploaded.status, 200);
  const sha = (await uploaded.json()).release.sha256;
  assert.match(sha, /^[0-9a-f]{64}$/);
  assert.equal(objects.get(`apk:${id}`).byteLength, apk.byteLength);
  assert.equal((await call(`/api/admin/releases/${id}/validate`, "POST", {})).status, 200);
  const workerHeaders = { "x-rollcall-release-key": "worker-secret" };
  const task = await call("/api/release-worker/next", "GET", null, null, workerHeaders);
  assert.deepEqual((await task.json()).task.stage, "validate");
  const staged = await call(`/api/release-worker/artifact/${id}`, "GET", null, null, workerHeaders);
  assert.equal(staged.status, 200);
  assert.equal((await staged.arrayBuffer()).byteLength, apk.byteLength);
  const metadata = { ok: true, packageName: "com.iniexe.rollcall", sha256: sha, versionCode: 32,
    versionName: "1.0.31", minSdk: 23, targetSdk: 35,
    signerSha256: "19117b6b5320c6adb907834880f031d00040e0a3114a9a8d3633539349bf1bc6" };
  const bad = await call(`/api/release-worker/${id}/validated`, "POST", { ...metadata, signerSha256: "wrong" }, null, workerHeaders);
  assert.equal((await bad.json()).release.status, "failed");
  assert.equal((await call(`/api/admin/releases/${id}/validate`, "POST", {})).status, 200);
  assert.equal((await call("/api/release-worker/next", "GET", null, null, workerHeaders)).status, 200);
  const verified = await call(`/api/release-worker/${id}/validated`, "POST", metadata, null, workerHeaders);
  assert.equal((await verified.json()).release.status, "validated");
  assert.equal((await call(`/api/admin/releases/${id}/approval-code`, "POST", {})).status, 200);
  assert.match(approvalCode, /^\d{6}$/);
  assert.equal((await call(`/api/admin/releases/${id}/publish`, "POST", { code: "000000", confirmTag: "v1.0.31" })).status, 401);
  const approved = await call(`/api/admin/releases/${id}/publish`, "POST", { code: approvalCode, confirmTag: "v1.0.31" });
  assert.equal((await approved.json()).release.status, "publish_queued");
  const publishTask = await call("/api/release-worker/next", "GET", null, null, workerHeaders);
  assert.equal((await publishTask.json()).task.stage, "publish");
});

test("Command Center campaigns publish to the authenticated inbox, record receipts, and revoke", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase();
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", COMMAND_CENTER_ADMIN_UIDS: "owner-1", DB: database };
  const adminToken = await authToken(pair.privateKey, { sub: "owner-1", email: "owner@example.com" });
  const studentToken = await authToken(pair.privateKey);
  const call = (path, token, method = "GET", body) => handleApi(new Request(`https://rollcall.test${path}`, {
    method, headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  }), env);

  assert.equal((await call("/api/admin/campaigns", adminToken, "POST", {
    title: "Service update", body: "Sync is available again.", category: "resolved", priority: "important",
    channels: ["inbox", "popup"], actionLabel: "", actionPath: ""
  })).status, 201);
  const draft = database.state.campaigns[0];
  assert.deepEqual(JSON.parse(draft.channels_json), ["inbox", "popup"]);
  assert.equal((await call(`/api/admin/campaigns/${draft.id}/publish`, adminToken, "POST", {})).status, 400);
  const publish = await call(`/api/admin/campaigns/${draft.id}/publish`, adminToken, "POST", { confirmAllInstallations: true });
  assert.equal(publish.status, 200);
  assert.equal((await publish.json()).status, "published");
  let inbox = await call("/api/announcements", studentToken);
  assert.equal(inbox.status, 200);
  const delivered = (await inbox.json()).announcements[0];
  assert.equal(delivered.readState, "unread");
  assert.deepEqual(delivered.channels, ["inbox", "popup"]);
  assert.equal((await call(`/api/announcements/${draft.id}/receipt`, studentToken, "POST", { state: "read" })).status, 200);
  inbox = await call("/api/announcements", studentToken);
  assert.equal((await inbox.json()).announcements[0].readState, "read");
  const sessionToken = "browser-session-token";
  database.state.session = {
    uid: "student-1", expires_at: Math.floor(Date.now() / 1000) + 60,
    session_hash: Buffer.from(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`test-secret:${sessionToken}`))).toString("hex")
  };
  env.SESSION_HASH_KEY = "test-secret";
  const webCall = (path, method = "GET", body) => handleApi(new Request(`https://rollcall.test${path}`, {
    method, headers: { cookie: `rc_session=${sessionToken}`, origin: "https://rollcall.test", "content-type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  }), env);
  assert.equal((await webCall("/api/announcements")).status, 200);
  assert.equal((await webCall(`/api/announcements/${draft.id}/receipt`, "POST", { state: "dismissed" })).status, 200);
  assert.equal((await (await webCall("/api/announcements")).json()).announcements[0].readState, "dismissed");
  assert.equal((await handleApi(new Request("https://rollcall.test/api/announcements"), env)).status, 401);
  assert.equal((await call(`/api/admin/campaigns/${draft.id}/revoke`, adminToken, "POST", { confirmRevoke: true })).status, 200);
  assert.equal((await (await call("/api/announcements", studentToken)).json()).announcements.length, 0);
  assert.ok(database.state.adminAudit.some(event => event.action === "campaign_published"));
  assert.ok(database.state.adminAudit.some(event => event.action === "campaign_revoked"));
});

test("Command Center reports Firebase accounts separately from trusted installations", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const pkcs8 = Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64");
  const pem = `-----BEGIN PRIVATE KEY-----\n${pkcs8.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----`;
  const originalFetch = globalThis.fetch;
  let denyCount = false;
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("securetoken@system.gserviceaccount.com")) return Response.json({ keys: [jwk] });
    if (String(url) === "https://oauth2.googleapis.com/token") {
      const assertion = new URLSearchParams(options.body).get("assertion");
      const claims = JSON.parse(Buffer.from(assertion.split(".")[1], "base64url").toString());
      assert.equal(claims.scope, "https://www.googleapis.com/auth/identitytoolkit");
      return Response.json({ access_token: "test-service-token" });
    }
    if (String(url).includes("identitytoolkit.googleapis.com")) {
      assert.deepEqual(JSON.parse(options.body), { returnUserInfo: false });
      return denyCount ? Response.json({ error: { message: "PERMISSION_DENIED" } }, { status: 403 }) : Response.json({ recordsCount: "1" });
    }
    throw new Error(`Unexpected request: ${url}`);
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase();
  database.state.deviceCount = 3;
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", FIREBASE_CLIENT_EMAIL: "service@example.com", FIREBASE_PRIVATE_KEY: pem,
    COMMAND_CENTER_ADMIN_UIDS: "owner-1", DB: database };
  const token = await authToken(pair.privateKey, { sub: "owner-1" });
  const call = () => handleApi(new Request("https://rollcall.test/api/admin/overview", {
    headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test" }
  }), env);

  const result = await call();
  assert.equal(result.status, 200);
  assert.deepEqual(await result.json(), { firebaseUsers: 1, registeredInstallations: 3, campaigns: 0, activeCampaigns: 0, pushFailures: 0 });
  denyCount = true;
  assert.equal((await (await call()).json()).firebaseUsers, null);
});

test("Command Center lists recent Firebase accounts and keeps configured admins separate", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const pkcs8 = Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64");
  const pem = `-----BEGIN PRIVATE KEY-----\n${pkcs8.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----`;
  const originalFetch = globalThis.fetch;
  let accountQueryCalls = 0;
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("securetoken@system.gserviceaccount.com")) return Response.json({ keys: [jwk] });
    if (String(url) === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "test-service-token" });
    if (String(url).includes("identitytoolkit.googleapis.com")) {
      accountQueryCalls++;
      assert.deepEqual(JSON.parse(options.body), { returnUserInfo: true, limit: "500", sortBy: "CREATED_AT", order: "DESC" });
      return Response.json({ recordsCount: "2", userInfo: [
        { localId: "new-user", email: "new@example.com", displayName: "New student", emailVerified: true,
          createdAt: "1763200000000", lastLoginAt: "1763200100000", providerUserInfo: [{ providerId: "google.com" }],
          passwordHash: "must-not-leak", dateOfBirth: "2000-01-01" },
        { localId: "owner-1", email: "admin@example.com", emailVerified: true, createdAt: "1763100000000" }
      ] });
    }
    throw new Error(`Unexpected request: ${url}`);
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", FIREBASE_CLIENT_EMAIL: "service@example.com", FIREBASE_PRIVATE_KEY: pem,
    COMMAND_CENTER_ADMIN_UIDS: "owner-1", COMMAND_CENTER_ADMIN_EMAILS: "admin@example.com", DB: fakeDatabase() };
  const token = await authToken(pair.privateKey, { sub: "owner-1", email: "admin@example.com" });
  const call = userToken => handleApi(new Request("https://rollcall.test/api/admin/accounts", {
    headers: { authorization: `Bearer ${userToken}`, origin: "https://rollcall.test" }
  }), env);
  assert.equal((await call(await authToken(pair.privateKey))).status, 403);
  assert.equal(accountQueryCalls, 0);
  const response = await call(token);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.admins, [{ uid: "owner-1", email: "admin@example.com" }]);
  assert.equal(body.accounts.length, 1);
  assert.equal(body.accounts[0].email, "new@example.com");
  assert.equal(body.accounts[0].providers[0], "google.com");
  assert.equal("passwordHash" in body.accounts[0], false);
  assert.equal("dateOfBirth" in body.accounts[0], false);
});

test("Command Center campaigns reject external actions and unconfirmed publish requests", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  t.after(() => { globalThis.fetch = originalFetch; });
  const token = await authToken(pair.privateKey, { sub: "owner-1" });
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", COMMAND_CENTER_ADMIN_UIDS: "owner-1", DB: fakeDatabase() };
  const response = await handleApi(new Request("https://rollcall.test/api/admin/campaigns", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ title: "Unsafe link", body: "Example", channels: ["inbox"], actionLabel: "Open", actionPath: "https://bad.example" })
  }), env);
  assert.equal(response.status, 400);
});

test("API returns authorization and server failures with the right status without leaking internals", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch, originalError = console.error;
  globalThis.fetch = async () => Response.json({ keys: [jwk] });
  console.error = () => {};
  t.after(() => { globalThis.fetch = originalFetch; console.error = originalError; });
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", COMMAND_CENTER_ADMIN_UIDS: "owner-1", DB: {
    prepare() { return { bind() { return this; }, async run() { throw new Error("D1 internal binding secret"); } }; }
  } };
  const malformed = await handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { authorization: "Bearer not-a-jwt" }
  }), env);
  assert.equal(malformed.status, 401);
  const token = await authToken(pair.privateKey, { sub: "owner-1" });
  const databaseFailure = await handleApi(new Request("https://rollcall.test/api/admin/session", {
    headers: { authorization: `Bearer ${token}` }
  }), env);
  assert.equal(databaseFailure.status, 500);
  assert.deepEqual(await databaseFailure.json(), { error: "The service could not complete this request. Please try again." });
});

test("snapshot stores validated internal marks and accepts older clients without them", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async url => {
    assert.match(String(url), /securetoken@system\.gserviceaccount\.com/);
    return Response.json({ keys: [jwk] });
  };
  t.after(() => { globalThis.fetch = originalFetch; });
  const database = fakeDatabase(), token = await authToken(pair.privateKey);
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", DB: database };
  const snapshot = { schemaVersion: 1, target: 75, courses: [], timetable: [], classMarks: [], leaves: [], academicRecords: [], exams: [],
    internalMarks: [{ assessmentId: "cia-1", semester: "Semester 5", courseCode: "CS301", course: "Networks", assessment: "CIA 1", score: 18, maximum: 20, status: "published" },
      { course: "Networks", assessment: "CIA 2", status: "withheld", score: 5, maximum: 20 }] };
  const put = data => handleApi(new Request("https://rollcall.test/api/snapshot", {
    method: "PUT", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "old-device-id-1234", snapshot: data })
  }), env);
  let response = await put(snapshot);
  assert.equal(response.status, 200);
  let stored = JSON.parse(database.state.snapshot);
  assert.equal(stored.internalMarks[0].score, 18);
  assert.equal(stored.internalMarks[1].score, null);
  assert.equal(stored.internalMarks[1].status, "withheld");
  assert.equal((await put({ ...snapshot, internalMarks: [{ course: "Networks", assessment: "CIA 1", score: 21, maximum: 20, status: "published" }] })).status, 400);
  response = await put({ ...snapshot, internalMarks: undefined });
  assert.equal(response.status, 200);
  stored = JSON.parse(database.state.snapshot);
  assert.deepEqual(stored.internalMarks, []);
});

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
  const token = await authToken(pair.privateKey);
  const response = await handleApi(new Request("https://rollcall.test/api/account/delete", {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test" }
  }), { FIREBASE_PROJECT_ID: "rollcall-test", DB: database });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true });
  assert.equal(database.state.challengeSends.length, 0);
  assert.deepEqual(database.state.deletedTables.sort(), ["device_replacement_codes", "device_replacement_email_sends", "snapshots", "trusted_devices", "web_challenge_sends", "web_challenges", "web_qr_challenges", "web_sessions"].sort());
});

test("trusted phone approves a short-lived QR login and the browser exchanges it once", async t => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async url => {
    assert.match(String(url), /securetoken@system\.gserviceaccount\.com/);
    return Response.json({ keys: [jwk] });
  };
  t.after(() => { globalThis.fetch = originalFetch; });

  const database = fakeDatabase(), token = await authToken(pair.privateKey);
  const secret = "test-session-secret-with-enough-entropy";
  const env = { FIREBASE_PROJECT_ID: "rollcall-test", SESSION_HASH_KEY: secret, DB: database };
  const start = await handleApi(new Request("https://rollcall.test/api/auth/qr/start", {
    method: "POST", headers: { origin: "https://rollcall.test", "cf-connecting-ip": "192.0.2.1", "user-agent": "Mozilla/5.0 Chrome/124.0 (X11; Linux x86_64)" }
  }), env);
  assert.equal(start.status, 200);
  const challenge = await start.json();
  assert.match(challenge.scanPayload, /^rollcall:\/\/web-sign-in\?challenge=[A-Za-z0-9_-]{24}&token=[A-Za-z0-9_-]{43}$/);
  assert.equal(challenge.browserLabel, "Chrome on Linux");
  assert.equal(challenge.expiresAt - Math.floor(Date.now() / 1000), 120);

  const phoneRequest = path => handleApi(new Request(`https://rollcall.test/api/auth/qr/${path}`, {
    method: "POST", headers: { authorization: `Bearer ${token}`, origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "old-device-id-1234", challengeId: challenge.challengeId, secret: challenge.secret })
  }), env);
  const preview = await phoneRequest("preview");
  assert.deepEqual(await preview.json(), { browserLabel: "Chrome on Linux", expiresAt: challenge.expiresAt });
  assert.equal((await phoneRequest("approve")).status, 200);

  const poll = () => handleApi(new Request("https://rollcall.test/api/auth/qr/poll", {
    method: "POST", headers: { origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ challengeId: challenge.challengeId, secret: challenge.secret })
  }), env);
  const signedIn = await poll();
  assert.deepEqual(await signedIn.json(), { state: "signed_in" });
  assert.match(signedIn.headers.get("set-cookie"), /^rc_session=.*HttpOnly.*SameSite=Strict/);
  assert.equal(database.state.session.uid, "student-1");
  assert.equal((await poll()).status, 404);
});

test("QR sign-in rejects invalid secrets and rate-limits anonymous challenge creation", async () => {
  const database = fakeDatabase(), secret = "test-session-secret-with-enough-entropy";
  const env = { SESSION_HASH_KEY: secret, DB: database };
  let challenge;
  for (let i = 0; i < 11; i++) {
    const response = await handleApi(new Request("https://rollcall.test/api/auth/qr/start", {
      method: "POST", headers: { origin: "https://rollcall.test", "cf-connecting-ip": "192.0.2.2" }
    }), env);
    if (i < 10) { assert.equal(response.status, 200); challenge = await response.json(); }
    else assert.equal(response.status, 429);
  }
  const rejected = await handleApi(new Request("https://rollcall.test/api/auth/qr/poll", {
    method: "POST", headers: { origin: "https://rollcall.test", "content-type": "application/json" },
    body: JSON.stringify({ challengeId: challenge.challengeId, secret: "A".repeat(43) })
  }), env);
  assert.equal(rejected.status, 404);
});
