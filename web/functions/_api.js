const encoder = new TextEncoder();
const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers }
});
const b64url = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
const fromB64url = value => Uint8Array.from(atob(value.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((value.length + 3) % 4)), c => c.charCodeAt(0));
const random = size => crypto.getRandomValues(new Uint8Array(size));
const now = () => Math.floor(Date.now() / 1000);
const SESSION_TTL_SECONDS = 30 * 24 * 60 * 60;
const hex = bytes => Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, "0")).join("");
const constantEqual = (a, b) => {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
};
const digest = async value => hex(await crypto.subtle.digest("SHA-256", encoder.encode(value)));
const secretKey = async (value, usages) => crypto.subtle.importKey("raw", value, { name: "AES-GCM" }, false, usages);

async function readBody(request, limit = 1_900_000) {
  const length = Number(request.headers.get("content-length") || 0);
  if (length > limit) throw new Error("Request is too large.");
  if (!request.body) return {};
  const reader = request.body.getReader();
  const decoder = new TextDecoder();
  let size = 0, text = "";
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) throw new Error("Request is too large.");
      text += decoder.decode(value, { stream: true });
    }
    text += decoder.decode();
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  }
  return text ? JSON.parse(text) : {};
}

function sameOrigin(request) {
  const origin = request.headers.get("origin");
  return !origin || origin === new URL(request.url).origin;
}

async function verifyFirebaseToken(token, env) {
  if (!token || token.length > 8192) throw new Error("Sign in again to continue.");
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Sign in again to continue.");
  const header = JSON.parse(new TextDecoder().decode(fromB64url(parts[0])));
  const claims = JSON.parse(new TextDecoder().decode(fromB64url(parts[1])));
  if (header.alg !== "RS256" || !header.kid) throw new Error("Invalid sign-in token.");
  const keys = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com", { cf: { cacheTtl: 3600, cacheEverything: true } }).then(r => r.json());
  const jwk = keys.keys?.find(key => key.kid === header.kid);
  if (!jwk) throw new Error("Sign-in token key is unavailable.");
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, fromB64url(parts[2]), encoder.encode(`${parts[0]}.${parts[1]}`));
  const project = env.FIREBASE_PROJECT_ID;
  if (!valid || claims.aud !== project || claims.iss !== `https://securetoken.google.com/${project}`
      || !claims.sub || claims.sub.length > 128 || !Number.isFinite(claims.exp) || !Number.isFinite(claims.iat)
      || claims.exp <= now() || claims.iat > now() + 60) throw new Error("Sign in again to continue.");
  return { uid: claims.sub, email: String(claims.email || ""), emailVerified: claims.email_verified === true,
    authTime: claims.auth_time };
}

async function authFromHeader(request, env, requireVerified = true, requireRecent = false) {
  const match = request.headers.get("authorization")?.match(/^Bearer (.+)$/);
  if (!match) throw new Error("Sign in to your Rollcall account first.");
  const user = await verifyFirebaseToken(match[1], env);
  if (requireVerified && !user.emailVerified) throw new Error("Verify your email address before using web sync.");
  if (requireRecent && (!Number.isFinite(user.authTime) || user.authTime > now() + 60 || now() - user.authTime > 5 * 60))
    throw new Error("Sign in again before deleting your Rollcall account.");
  return user;
}

async function sessionUser(request, env) {
  if (!sameOrigin(request)) throw new Error("Invalid request origin.");
  const token = request.headers.get("cookie")?.split(";").map(x => x.trim()).find(x => x.startsWith("rc_session="))?.slice("rc_session=".length);
  if (!token) return null;
  const hash = await digest(`${env.SESSION_HASH_KEY}:${token}`);
  const row = await env.DB.prepare("SELECT uid FROM web_sessions WHERE session_hash=? AND expires_at>?").bind(hash, now()).first();
  return row?.uid || null;
}

async function hmac(value, secret) {
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return hex(await crypto.subtle.sign("HMAC", key, encoder.encode(value)));
}

function decodePrivateKey(pem) {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  return Uint8Array.from(atob(body), c => c.charCodeAt(0));
}

async function googleAccessToken(env) {
  const key = await crypto.subtle.importKey("pkcs8", decodePrivateKey(env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n")),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const iat = now();
  const header = b64url(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const payload = b64url(encoder.encode(JSON.stringify({ iss: env.FIREBASE_CLIENT_EMAIL, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: "https://oauth2.googleapis.com/token", iat, exp: iat + 3600 })));
  const unsigned = `${header}.${payload}`;
  const signature = b64url(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(unsigned)));
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${signature}` })
  });
  const body = await response.json();
  if (!response.ok || !body.access_token) throw new Error("Push service is not configured.");
  return body.access_token;
}

async function sendPushToDevices(env, tokens, data, ttl) {
  const accessToken = await googleAccessToken(env);
  const results = await Promise.all(tokens.map(async token => {
    try {
      const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
        method: "POST", headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
        body: JSON.stringify({ message: { token, data, android: { priority: "HIGH", ttl } } })
      });
      return response.ok;
    } catch (_) { return false; }
  }));
  if (!results.some(Boolean)) throw new Error("No signed-in Rollcall app could receive the code.");
  return results.filter(Boolean).length;
}

async function sendCodePush(env, tokens, uid, challengeId) {
  return sendPushToDevices(env, tokens, { type: "web_login", uid, challengeId }, "300s");
}

async function encryptCode(code, env) {
  const raw = Uint8Array.from(atob(env.OTP_ENCRYPTION_KEY), c => c.charCodeAt(0));
  if (raw.length !== 32) throw new Error("OTP encryption is not configured.");
  const iv = random(12), key = await secretKey(raw, ["encrypt"]);
  const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, encoder.encode(code));
  return { iv: b64url(iv), cipher: b64url(encrypted) };
}

async function decryptCode(row, env) {
  const raw = Uint8Array.from(atob(env.OTP_ENCRYPTION_KEY), c => c.charCodeAt(0));
  const key = await secretKey(raw, ["decrypt"]);
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: fromB64url(row.code_iv) }, key, fromB64url(row.code_cipher));
  return new TextDecoder().decode(plain);
}

function makeOneTimeCode() {
  const value = new DataView(random(4).buffer).getUint32(0) % 1_000_000;
  return String(value).padStart(6, "0");
}

async function createWebSession(env, uid) {
  const token = b64url(random(32));
  const createdAt = now();
  await env.DB.prepare("INSERT INTO web_sessions(session_hash,uid,created_at,expires_at) VALUES(?,?,?,?)")
    .bind(await digest(`${env.SESSION_HASH_KEY}:${token}`), uid, createdAt, createdAt + SESSION_TTL_SECONDS).run();
  return json({ ok: true }, 200, { "set-cookie": `rc_session=${token}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=${SESSION_TTL_SECONDS}` });
}

function normalizeSnapshot(input) {
  if (!input || typeof input !== "object" || input.schemaVersion !== 1) throw new Error("Unsupported snapshot format.");
  for (const key of ["courses", "timetable", "classMarks", "leaves", "academicRecords", "exams"]) {
    if (!Array.isArray(input[key]) || input[key].length > 5000) throw new Error(`Invalid ${key} data.`);
  }
  const target = Number(input.target);
  if (!Number.isFinite(target) || target < 1 || target > 100) throw new Error("Invalid attendance target.");
  return { ...input, target, updatedAt: now() };
}

async function route(request, env) {
  const url = new URL(request.url), path = url.pathname, method = request.method;
  if (method === "OPTIONS") return new Response(null, { status: 204, headers: { "allow": "GET, POST, PUT, OPTIONS" } });
  if ((method === "POST" || method === "PUT") && !sameOrigin(request)) return json({ error: "Invalid request origin." }, 403);

  if (path === "/api/device/trust" && method === "POST") {
    const user = await authFromHeader(request, env), body = await readBody(request, 12000);
    if (!/^[\w-]{12,100}$/.test(String(body.deviceId || "")) || !String(body.fcmToken || "") || String(body.fcmToken).length > 4096)
      return json({ error: "Invalid device details." }, 400);
    await env.DB.prepare("INSERT INTO app_devices(device_id,uid,fcm_token,updated_at) VALUES(?,?,?,?) ON CONFLICT(device_id) DO UPDATE SET uid=excluded.uid,fcm_token=excluded.fcm_token,updated_at=excluded.updated_at")
      .bind(body.deviceId, user.uid, body.fcmToken, now()).run();
    return json({ ok: true });
  }

  if (path === "/api/device/untrust" && method === "POST") {
    const user = await authFromHeader(request, env), body = await readBody(request, 3000);
    if (!/^[\w-]{12,100}$/.test(String(body.deviceId || ""))) return json({ error: "Invalid device details." }, 400);
    await env.DB.prepare("DELETE FROM app_devices WHERE uid=? AND device_id=?").bind(user.uid, body.deviceId).run();
    return json({ ok: true });
  }

  if (path === "/api/device/register" && method === "POST") {
    const user = await authFromHeader(request, env), body = await readBody(request, 12000);
    if (!/^[\w-]{12,100}$/.test(String(body.deviceId || "")) || String(body.fcmToken || "").length > 4096) return json({ error: "Invalid device details." }, 400);
    const existing = await env.DB.prepare("SELECT device_id FROM trusted_devices WHERE uid=?").bind(user.uid).first();
    if (existing && existing.device_id !== body.deviceId) {
      if (!body.replacementCode) return json({ error: "A code from another signed-in Rollcall app is required to link this phone.", code: "replacement_code_required" }, 403);
      if (!/^\d{6}$/.test(String(body.replacementCode))) return json({ error: "That app code was not accepted. Request a new code and try again.", code: "replacement_code_invalid" }, 403);
      const codeRow = await env.DB.prepare("SELECT code_hash FROM device_replacement_codes WHERE uid=? AND expires_at>? AND attempts<5 AND used_at IS NULL").bind(user.uid, now()).first();
      if (!codeRow) return json({ error: "That app code expired or reached its attempt limit. Request a fresh code.", code: "replacement_code_expired" }, 403);
      if (!constantEqual(await hmac(body.replacementCode, env.SESSION_HASH_KEY), codeRow.code_hash)) {
        const attempt = await env.DB.prepare("UPDATE device_replacement_codes SET attempts=attempts+1 WHERE uid=? AND used_at IS NULL AND expires_at>? AND attempts<5").bind(user.uid, now()).run();
        if (!attempt.meta.changes) return json({ error: "That app code expired or reached its attempt limit. Request a fresh code.", code: "replacement_code_expired" }, 403);
        return json({ error: "That app code was not accepted. Request a new code and try again.", code: "replacement_code_invalid" }, 403);
      }
      const claimed = await env.DB.prepare("UPDATE device_replacement_codes SET used_at=? WHERE uid=? AND used_at IS NULL AND expires_at>? AND attempts<5").bind(now(), user.uid, now()).run();
      if (claimed.meta.changes !== 1) return json({ error: "That app code has expired or was already used. Request a new code.", code: "replacement_code_invalid" }, 403);
      await env.DB.prepare("DELETE FROM web_sessions WHERE uid=?").bind(user.uid).run();
    }
    await env.DB.prepare("INSERT INTO trusted_devices(uid,device_id,fcm_token,updated_at) VALUES(?,?,?,?) ON CONFLICT(uid) DO UPDATE SET device_id=excluded.device_id,fcm_token=excluded.fcm_token,updated_at=excluded.updated_at")
      .bind(user.uid, body.deviceId, body.fcmToken, now()).run();
    return json({ ok: true });
  }

  if (path === "/api/device/replacement-code" && method === "POST") {
    const user = await authFromHeader(request, env), body = await readBody(request, 3000);
    if (!/^[\w-]{12,100}$/.test(String(body.deviceId || ""))) return json({ error: "Sign in to Rollcall on this phone first." }, 400);
    const device = await env.DB.prepare("SELECT device_id,fcm_token FROM trusted_devices WHERE uid=?").bind(user.uid).first();
    if (!device) return json({ error: "Connect your first phone before requesting a replacement code." }, 409);
    const appDevices = await env.DB.prepare("SELECT device_id,fcm_token FROM app_devices WHERE uid=? AND device_id<>?").bind(user.uid, body.deviceId).all();
    const recipients = [...new Set((appDevices.results || []).map(row => row.fcm_token).filter(Boolean))];
    if (!recipients.length && device.device_id !== body.deviceId && device.fcm_token) recipients.push(device.fcm_token);
    if (!recipients.length) return json({ error: "Open Rollcall on another signed-in phone to receive the link code." }, 409);
    await env.DB.prepare("DELETE FROM device_replacement_sends WHERE created_at<?").bind(now() - 86400).run();
    const recent = await env.DB.prepare("SELECT created_at FROM device_replacement_sends WHERE uid=? ORDER BY created_at DESC LIMIT 1").bind(user.uid).first();
    if (recent && recent.created_at > now() - 60) return json({ error: "A code was just sent. Please wait a minute before requesting another.", code: "replacement_code_rate_limited" }, 429);
    const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM device_replacement_sends WHERE uid=? AND created_at>?").bind(user.uid, now() - 900).first();
    if (count.n >= 5) return json({ error: "Too many code requests. Try again in 15 minutes.", code: "replacement_code_rate_limited" }, 429);
    await env.DB.prepare("INSERT INTO device_replacement_sends(uid,created_at) VALUES(?,?)").bind(user.uid, now()).run();
    const code = makeOneTimeCode(), issuedAt = now();
    await env.DB.prepare("INSERT INTO device_replacement_codes(uid,code_hash,created_at,expires_at,attempts,used_at) VALUES(?,?,?,?,0,NULL) ON CONFLICT(uid) DO UPDATE SET code_hash=excluded.code_hash,created_at=excluded.created_at,expires_at=excluded.expires_at,attempts=0,used_at=NULL")
      .bind(user.uid, await hmac(code, env.SESSION_HASH_KEY), issuedAt, issuedAt + 600).run();
    const delivered = await sendPushToDevices(env, recipients, { type: "device_replacement", uid: user.uid, replacementCode: code }, "600s").catch(() => 0);
    if (!delivered) {
      await env.DB.prepare("DELETE FROM device_replacement_codes WHERE uid=? AND created_at=?").bind(user.uid, issuedAt).run();
      return json({ error: "Could not reach any other signed-in Rollcall app. Open one and try again.", code: "replacement_push_failed" }, 503);
    }
    return json({ ok: true, expiresIn: 600, sentTo: delivered });
  }

  if (path === "/api/auth/challenge" && method === "POST") {
    const user = await authFromHeader(request, env);
    const devices = await env.DB.prepare("SELECT fcm_token FROM app_devices WHERE uid=?").bind(user.uid).all();
    const appTokens = [...new Set((devices.results || []).map(row => row.fcm_token).filter(Boolean))];
    if (!appTokens.length) {
      const device = await env.DB.prepare("SELECT fcm_token FROM trusted_devices WHERE uid=?").bind(user.uid).first();
      if (device?.fcm_token) appTokens.push(device.fcm_token);
    }
    if (!appTokens.length) return json({ error: "Sign in to Rollcall on your phone to receive web sign-in codes." }, 409);
    await env.DB.prepare("DELETE FROM web_challenges WHERE uid=? AND (expires_at<=? OR consumed_at IS NOT NULL)").bind(user.uid, now()).run();
    await env.DB.prepare("DELETE FROM web_challenge_sends WHERE created_at<?").bind(now() - 86400).run();
    const send = await env.DB.prepare("INSERT INTO web_challenge_sends(uid,created_at) SELECT ?,? WHERE (SELECT COUNT(*) FROM web_challenge_sends WHERE uid=? AND created_at>?) < 5")
      .bind(user.uid, now(), user.uid, now() - 900).run();
    if (!send.meta.changes) return json({ error: "Too many code requests. Try again in 15 minutes." }, 429);
    let source;
    do { source = crypto.getRandomValues(new Uint32Array(1))[0]; } while (source >= 4294000000);
    const challengeId = b64url(random(18)), code = String(source % 1_000_000).padStart(6, "0");
    const encrypted = await encryptCode(code, env), expires = now() + 300;
    await env.DB.prepare("INSERT INTO web_challenges(id,uid,code_hash,code_iv,code_cipher,expires_at,created_at) VALUES(?,?,?,?,?,?,?)")
      .bind(challengeId, user.uid, await hmac(code, env.SESSION_HASH_KEY), encrypted.iv, encrypted.cipher, expires, now()).run();
    try { await sendCodePush(env, appTokens, user.uid, challengeId); }
    catch (error) { await env.DB.prepare("DELETE FROM web_challenges WHERE id=?").bind(challengeId).run(); throw error; }
    return json({ ok: true, challengeId, expiresAt: expires });
  }

  if (path === "/api/auth/pending" && method === "POST") {
    const user = await authFromHeader(request, env), body = await readBody(request, 3000);
    const device = await env.DB.prepare("SELECT device_id FROM app_devices WHERE uid=? AND device_id=?").bind(user.uid, body.deviceId).first();
    if (!device) {
      const legacyDevice = await env.DB.prepare("SELECT device_id FROM trusted_devices WHERE uid=?").bind(user.uid).first();
      if (!legacyDevice || legacyDevice.device_id !== body.deviceId) return json({ error: "This app is not signed in to the Rollcall account." }, 403);
    }
    const row = await env.DB.prepare("SELECT * FROM web_challenges WHERE id=? AND uid=? AND consumed_at IS NULL AND expires_at>?").bind(body.challengeId, user.uid, now()).first();
    if (!row) return json({ error: "This sign-in request expired. Request a new code on the web." }, 404);
    return json({ code: await decryptCode(row, env), expiresAt: row.expires_at });
  }

  if (path === "/api/auth/verify" && method === "POST") {
    const body = await readBody(request, 3000);
    const row = await env.DB.prepare("SELECT * FROM web_challenges WHERE id=? AND consumed_at IS NULL AND expires_at>? AND attempts<5").bind(body.challengeId, now()).first();
    if (!row) return json({ error: "That code expired or can no longer be used. Request another code." }, 401);
    const code = String(body.code || "").trim();
    const valid = /^\d{6}$/.test(code) && constantEqual(await hmac(code, env.SESSION_HASH_KEY), row.code_hash);
    if (!valid) {
      const attempt = await env.DB.prepare("UPDATE web_challenges SET attempts=attempts+1 WHERE id=? AND consumed_at IS NULL AND expires_at>? AND attempts<5").bind(row.id, now()).run();
      if (!attempt.meta.changes) return json({ error: "That code expired or can no longer be used. Request another code." }, 401);
      return json({ error: "The code was not accepted. Check it and try again." }, 401);
    }
    const claimed = await env.DB.prepare("UPDATE web_challenges SET consumed_at=? WHERE id=? AND consumed_at IS NULL AND expires_at>? AND attempts<5").bind(now(), row.id, now()).run();
    if (!claimed.meta.changes) return json({ error: "That code has already been used." }, 401);
    return createWebSession(env, row.uid);
  }

  if (path === "/api/auth/session" && method === "GET") {
    const uid = await sessionUser(request, env);
    if (!uid) return json({ signedIn: false }, 401);
    const row = await env.DB.prepare("SELECT updated_at FROM snapshots WHERE uid=?").bind(uid).first();
    return json({ signedIn: true, updatedAt: row?.updated_at || 0 });
  }

  if (path === "/api/auth/logout" && method === "POST") {
    const uid = await sessionUser(request, env);
    const token = request.headers.get("cookie")?.split(";").map(x => x.trim()).find(x => x.startsWith("rc_session="))?.slice("rc_session=".length);
    if (uid && token) await env.DB.prepare("DELETE FROM web_sessions WHERE session_hash=?").bind(await digest(`${env.SESSION_HASH_KEY}:${token}`)).run();
    return json({ ok: true }, 200, { "set-cookie": "rc_session=; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=0" });
  }

  if (path === "/api/snapshot" && method === "PUT") {
    const user = await authFromHeader(request, env), body = await readBody(request);
    const device = await env.DB.prepare("SELECT device_id FROM trusted_devices WHERE uid=?").bind(user.uid).first();
    if (!device || device.device_id !== body.deviceId) return json({ error: "Only the trusted Rollcall app can update this snapshot." }, 403);
    const snapshot = normalizeSnapshot(body.snapshot), serialized = JSON.stringify(snapshot);
    if (serialized.length > 1_900_000) return json({ error: "Snapshot is too large to sync." }, 413);
    await env.DB.prepare("INSERT INTO snapshots(uid,schema_version,snapshot_json,updated_at) VALUES(?,1,?,?) ON CONFLICT(uid) DO UPDATE SET schema_version=1,snapshot_json=excluded.snapshot_json,updated_at=excluded.updated_at")
      .bind(user.uid, serialized, snapshot.updatedAt).run();
    return json({ ok: true, updatedAt: snapshot.updatedAt });
  }

  if (path === "/api/snapshot" && method === "GET") {
    const uid = await sessionUser(request, env);
    if (!uid) return json({ error: "Sign in to view your synced data." }, 401);
    const row = await env.DB.prepare("SELECT snapshot_json,updated_at FROM snapshots WHERE uid=?").bind(uid).first();
    return row ? json({ snapshot: JSON.parse(row.snapshot_json), updatedAt: row.updated_at }) : json({ error: "No phone snapshot has been synced yet." }, 404);
  }

  if (path === "/api/account/delete" && method === "POST") {
    const user = await authFromHeader(request, env, true, true);
    await env.DB.batch(["trusted_devices", "app_devices", "snapshots", "web_challenges", "web_challenge_sends", "web_sessions", "device_replacement_codes", "device_replacement_sends"].map(table => env.DB.prepare(`DELETE FROM ${table} WHERE uid=?`).bind(user.uid)));
    return json({ ok: true });
  }

  return json({ error: "Not found." }, 404);
}

export async function handleApi(request, env) {
  try { return await route(request, env); }
  catch (error) {
    const message = error?.message || "Request failed.";
    const status = /sign in|verify your email/i.test(message) ? 401 : /too large/i.test(message) ? 413 : /not configured|push service|email delivery|could receive/i.test(message) ? 503 : 400;
    return json({ error: message }, status);
  }
}
