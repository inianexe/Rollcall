const encoder = new TextEncoder();
const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers }
});
const b64url = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
const fromB64url = value => Uint8Array.from(atob(value.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((value.length + 3) % 4)), c => c.charCodeAt(0));
const random = size => crypto.getRandomValues(new Uint8Array(size));
const now = () => Math.floor(Date.now() / 1000);
const SESSION_TTL_SECONDS = 30 * 24 * 60 * 60;
const QR_CHALLENGE_TTL_SECONDS = 120;
// shortcut: KV limits one private APK to 25 MiB; move staging to R2 when R2 is enabled.
const RELEASE_MAX_BYTES = 20 * 1024 * 1024;
const RELEASE_SIGNER = "19117b6b5320c6adb907834880f031d00040e0a3114a9a8d3633539349bf1bc6";
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

async function readBinary(request, limit) {
  const declared = Number(request.headers.get("content-length") || 0);
  if (declared > limit) throw new Error("Request is too large.");
  if (!request.body) return new Uint8Array();
  const reader = request.body.getReader(), chunks = [];
  let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) throw new Error("Request is too large.");
      chunks.push(value);
    }
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
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
      || !claims.sub || claims.sub.length > 128 || claims.exp <= now() || claims.iat > now() + 60) throw new Error("Sign in again to continue.");
  return { uid: claims.sub, email: String(claims.email || ""), emailVerified: claims.email_verified === true };
}

async function authFromHeader(request, env, requireVerified = true) {
  const match = request.headers.get("authorization")?.match(/^Bearer (.+)$/);
  if (!match) throw new Error("Sign in to your Rollcall account first.");
  const user = await verifyFirebaseToken(match[1], env);
  if (requireVerified && !user.emailVerified) throw new Error("Verify your email address before using web sync.");
  return user;
}

async function adminFromHeader(request, env) {
  const allowed = String(env.COMMAND_CENTER_ADMIN_UIDS || "").split(",").map(uid => uid.trim()).filter(Boolean);
  const ownerEmails = String(env.COMMAND_CENTER_ADMIN_EMAILS || "").split(",").map(email => email.trim().toLowerCase()).filter(Boolean);
  if (!allowed.length && !ownerEmails.length) throw new Error("Command Center access is not configured.");
  if (!request.headers.has("authorization")) {
    const uid = await sessionUser(request, env);
    if (!uid) throw new Error("Sign in to your Rollcall account first.");
    if (!allowed.includes(uid)) throw new Error("You are not authorized to use the Command Center.");
    return { uid, email: "" };
  }
  const user = await authFromHeader(request, env);
  if (!allowed.includes(user.uid) && !(user.emailVerified && ownerEmails.includes(user.email.toLowerCase())))
    throw new Error("You are not authorized to use the Command Center.");
  return user;
}

async function releaseOwner(request, env) {
  if (!request.headers.has("authorization")) throw new Error("Sign in with your verified Google account to manage releases.");
  return adminFromHeader(request, env);
}

function releaseWorkerAuthorized(request, env) {
  const supplied = request.headers.get("x-rollcall-release-key") || "";
  if (!env.RELEASE_FACTORY_PULL_KEY) throw new Error("Release Factory worker is not configured.");
  if (!constantEqual(supplied, env.RELEASE_FACTORY_PULL_KEY)) throw new Error("Release worker is not authorized.");
}

function releaseView(row) {
  return { id: row.id, status: row.status, channel: row.channel, policy: row.policy,
    notes: row.notes, fileName: row.file_name, sizeBytes: row.size_bytes,
    sha256: row.sha256, versionCode: row.version_code, versionName: row.version_name,
    minSdk: row.min_sdk, targetSdk: row.target_sdk, failureReason: row.failure_reason,
    releaseUrl: row.release_url, pushStatus: row.push_status,
    createdAt: row.created_at, updatedAt: row.updated_at,
    validatedAt: row.validated_at, publishedAt: row.published_at };
}

async function releaseRow(env, id) {
  return env.DB.prepare("SELECT * FROM devos_releases WHERE id=?").bind(id).first();
}

async function sendReleaseApprovalEmail(env, email, code, release) {
  if (!env.RESEND_API_KEY || !env.ROLLCALL_EMAIL_FROM) throw new Error("Release approval email is not configured.");
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST", headers: { authorization: `Bearer ${env.RESEND_API_KEY}`, "content-type": "application/json" },
    body: JSON.stringify({ from: env.ROLLCALL_EMAIL_FROM, to: [email],
      subject: "Approve a Rollcall Android release",
      text: `A Rollcall release is awaiting your approval.\n\nAPK: ${release.file_name}\nVersion: ${release.version_name} (${release.version_code})\nSHA-256: ${release.sha256}\n\nEnter this one-time approval code in DevOS: ${code}\n\nThe code expires in 10 minutes. If you did not request this, do not approve the release.\n\nRollcall DevOS` })
  });
  if (!response.ok) throw new Error("Release approval email could not be sent.");
}

async function sendReleasePush(env, row) {
  const accessToken = await googleAccessToken(env);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: "POST", headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({ message: { topic: "app_updates", data: {
      type: "update", versionCode: String(row.version_code), versionName: row.version_name,
      releaseTag: `v${row.version_name}`, releaseTitle: `Rollcall ${row.version_name}`
    }, android: { priority: "high" } } })
  });
  if (!response.ok) throw new Error("Push provider rejected the release announcement.");
}

async function announcementUser(request, env) {
  if (request.headers.has("authorization")) return (await authFromHeader(request, env)).uid;
  const uid = await sessionUser(request, env);
  if (!uid) throw new Error("Sign in to your Rollcall account first.");
  return uid;
}

function campaignFields(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new Error("Invalid announcement.");
  const title = String(body.title || "").trim(), message = String(body.body || "").trim();
  const category = String(body.category || "announcement"), priority = String(body.priority || "normal");
  if (!title || title.length > 100 || !message || message.length > 1200) throw new Error("Title is required (100 characters max) and message is required (1200 characters max).");
  if (!["announcement","feature","maintenance","outage","resolved","update","operational"].includes(category)) throw new Error("Choose a supported announcement type.");
  if (!["normal","important","critical"].includes(priority)) throw new Error("Choose a supported priority.");
  const channels = Array.isArray(body.channels) ? [...new Set(body.channels)] : [];
  if (!channels.includes("inbox") || channels.some(channel => !["inbox","push","popup"].includes(channel))) throw new Error("Announcements must be saved to the inbox; supported delivery options are inbox, pop-up, and push.");
  const actionLabel = String(body.actionLabel || "").trim(), actionPath = String(body.actionPath || "").trim();
  if (Boolean(actionLabel) !== Boolean(actionPath) || actionLabel.length > 40 || actionPath.length > 160
      || (actionPath && (!actionPath.startsWith("/") || actionPath.startsWith("//") || !/^\/[A-Za-z0-9/_?#=&%.-]*$/.test(actionPath))))
    throw new Error("Use an internal Rollcall link and provide both action label and destination.");
  const expiresAt = body.expiresAt ? Math.floor(Date.parse(body.expiresAt) / 1000) : now() + 30 * 86400;
  if (!Number.isFinite(expiresAt) || expiresAt <= now() || expiresAt > now() + 90 * 86400)
    throw new Error("Choose an expiration time within the next 90 days.");
  return { title, body: message, category, priority, channels, actionLabel, actionPath, expiresAt };
}

function campaignFromRow(row) {
  return { id: row.id, title: row.title, body: row.body, category: row.category, priority: row.priority,
    channels: JSON.parse(row.channels_json), actionLabel: row.action_label, actionPath: row.action_path,
    status: row.status, createdAt: row.created_at, publishedAt: row.published_at,
    expiresAt: row.expires_at, pushStatus: row.push_status };
}

async function auditAdminAction(env, user, action, targetId = null, result = "success") {
  await env.DB.prepare("INSERT INTO command_center_audit(id,actor_uid,action,target_type,target_id,created_at,result) VALUES(?,?,?,'campaign',?,?,?)")
    .bind(crypto.randomUUID(), user.uid, action, targetId, now(), result).run();
}

async function auditReleaseAction(env, actor, action, id, result = "success") {
  await env.DB.prepare("INSERT INTO command_center_audit(id,actor_uid,action,target_type,target_id,created_at,result) VALUES(?,?,?,'release',?,?,?)")
    .bind(crypto.randomUUID(), actor, action, id, now(), result).run();
}

async function sendCampaignPush(env, campaign) {
  const accessToken = await googleAccessToken(env);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({ message: { topic: "app_updates", data: {
      type: "campaign", campaignId: campaign.id, title: campaign.title, body: campaign.body,
      category: campaign.category, priority: campaign.priority,
      actionLabel: campaign.actionLabel, actionPath: campaign.actionPath
    }, android: { priority: campaign.priority === "critical" ? "HIGH" : "NORMAL", ttl: "86400s" } } })
  });
  if (!response.ok) throw new Error("Push provider rejected the announcement.");
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

async function googleAccessToken(env, scope = "https://www.googleapis.com/auth/firebase.messaging") {
  const key = await crypto.subtle.importKey("pkcs8", decodePrivateKey(env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n")),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const iat = now();
  const header = b64url(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const payload = b64url(encoder.encode(JSON.stringify({ iss: env.FIREBASE_CLIENT_EMAIL, scope, aud: "https://oauth2.googleapis.com/token", iat, exp: iat + 3600 })));
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

async function firebaseAccountCount(env) {
  try {
    const accessToken = await googleAccessToken(env, "https://www.googleapis.com/auth/identitytoolkit");
    const response = await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}:queryAccounts`, {
      method: "POST", headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
      body: JSON.stringify({ returnUserInfo: false })
    });
    if (!response.ok) return null;
    const count = Number((await response.json()).recordsCount);
    return Number.isSafeInteger(count) && count >= 0 ? count : null;
  } catch {
    return null;
  }
}

async function firebaseAccountDirectory(env) {
  const accessToken = await googleAccessToken(env, "https://www.googleapis.com/auth/identitytoolkit");
  const response = await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}:queryAccounts`, {
    method: "POST", headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({ returnUserInfo: true, limit: "500", sortBy: "CREATED_AT", order: "DESC" })
  });
  if (!response.ok) throw new Error("Firebase account directory is unavailable.");
  const result = await response.json();
  const adminUids = String(env.COMMAND_CENTER_ADMIN_UIDS || "").split(",").map(value => value.trim()).filter(Boolean);
  const adminEmails = String(env.COMMAND_CENTER_ADMIN_EMAILS || "").split(",").map(value => value.trim().toLowerCase()).filter(Boolean);
  const firebaseUsers = Array.isArray(result.userInfo) ? result.userInfo : [];
  const configuredAdmins = new Map();
  adminEmails.forEach(email => {
    const match = firebaseUsers.find(user => String(user.email || "").toLowerCase() === email);
    configuredAdmins.set(match?.localId || `email:${email}`, { uid: String(match?.localId || ""), email });
  });
  adminUids.forEach(uid => {
    const match = firebaseUsers.find(user => String(user.localId || "") === uid);
    const email = String(match?.email || "").toLowerCase();
    configuredAdmins.set(uid, { uid, email: adminEmails.includes(email) ? email : "" });
  });
  const accounts = firebaseUsers.map(user => ({
    uid: String(user.localId || "").slice(0, 128),
    email: String(user.email || "").slice(0, 254),
    displayName: String(user.displayName || "").slice(0, 100),
    emailVerified: user.emailVerified === true,
    createdAt: String(user.createdAt || ""),
    lastLoginAt: String(user.lastLoginAt || ""),
    providers: [...new Set((Array.isArray(user.providerUserInfo) ? user.providerUserInfo : [])
      .map(provider => String(provider.providerId || "").slice(0, 40)).filter(Boolean))]
  })).filter(user => user.uid && !adminUids.includes(user.uid) && !adminEmails.includes(user.email.toLowerCase()));
  return { accounts, admins: [...configuredAdmins.values()], returned: accounts.length,
    hasMore: Number(result.recordsCount || 0) >= 500 };
}

async function sendCodePush(env, token, challengeId) {
  const accessToken = await googleAccessToken(env);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: "POST", headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({ message: { token, data: { type: "web_login", challengeId }, android: { priority: "HIGH", ttl: "300s" } } })
  });
  if (!response.ok) throw new Error("The trusted Rollcall app could not be reached. Open it and try again.");
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

function makeEmailCode() {
  const value = new DataView(random(4).buffer).getUint32(0) % 1_000_000;
  return String(value).padStart(6, "0");
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" })[character]);
}

async function sendDeviceReplacementEmail(env, email, code) {
  if (!env.RESEND_API_KEY || !env.ROLLCALL_EMAIL_FROM) throw new Error("Email delivery is not configured.");
  const safeCode = escapeHtml(code);
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { authorization: `Bearer ${env.RESEND_API_KEY}`, "content-type": "application/json" },
    body: JSON.stringify({
      from: env.ROLLCALL_EMAIL_FROM,
      to: [email],
      subject: "Confirm your new Rollcall phone",
      text: `Hello,\n\nUse this one-time code to link a new phone to your Rollcall account:\n\n${code}\n\nIt expires in 10 minutes. Enter it only in the Rollcall app. Once the new phone is linked, web sessions on your previous phone will be signed out.\n\nIf you did not request this change, ignore this email. Your account and linked phone have not changed. Never share this code with anyone.\n\nRollcall`,
      html: `<div style="margin:0;background:#f5f5ee;padding:32px 16px;font-family:Arial,sans-serif;color:#171717"><div style="max-width:520px;margin:0 auto;background:#fff;border:1px solid #deded3;border-radius:16px;overflow:hidden"><div style="padding:22px 28px;background:#ccff00;font-size:20px;font-weight:700;letter-spacing:1px">rollcall<span style="color:#ff4400">.</span></div><div style="padding:28px"><p style="margin:0 0 8px;color:#606456;font-size:12px;font-weight:700;letter-spacing:1.5px">ACCOUNT SECURITY</p><h1 style="margin:0 0 14px;font-size:24px;line-height:1.25">Confirm your new phone</h1><p style="margin:0 0 22px;font-size:15px;line-height:1.6">Use this one-time code to link a new phone to your Rollcall account.</p><div style="padding:18px;background:#f5f5ee;border:1px solid #deded3;border-radius:12px;text-align:center;font-size:32px;font-weight:700;letter-spacing:8px">${safeCode}</div><p style="margin:18px 0 0;font-size:14px;line-height:1.6"><b>This code expires in 10 minutes.</b> Enter it only in the Rollcall app. Once the new phone is linked, web sessions on your previous phone will be signed out.</p><div style="height:1px;margin:24px 0;background:#e8e8e0"></div><p style="margin:0;color:#606456;font-size:13px;line-height:1.6">If you did not request this change, ignore this email. Your account and linked phone have not changed. Never share this code with anyone.</p></div></div><p style="max-width:520px;margin:16px auto 0;color:#77796e;font-size:12px;line-height:1.5">This automated security message was sent because a phone replacement was requested for your Rollcall account.</p></div>`
    })
  });
  if (!response.ok) throw new Error("Email delivery failed.");
}

async function sendSignupVerificationEmail(env, email, code) {
  if (!env.RESEND_API_KEY || !env.ROLLCALL_EMAIL_FROM) throw new Error("Email delivery is not configured.");
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST", headers: { authorization: `Bearer ${env.RESEND_API_KEY}`, "content-type": "application/json" },
    body: JSON.stringify({
      from: env.ROLLCALL_EMAIL_FROM, to: [email], subject: "Verify your email for Rollcall",
      text: `Hello,\n\nUse this one-time code to verify your email and create your Rollcall account:\n\n${code}\n\nThe code expires in 10 minutes. Your Rollcall account will not be created unless you verify this code. If you did not request this, you can ignore this email.\n\nRollcall`,
      html: `<div style="margin:0;background:#f5f5ee;padding:32px 16px;font-family:Arial,sans-serif;color:#171717"><div style="max-width:520px;margin:0 auto;background:#fff;border:1px solid #deded3;border-radius:16px;overflow:hidden"><div style="padding:22px 28px;background:#ccff00;font-size:20px;font-weight:700;letter-spacing:1px">rollcall<span style="color:#ff4400">.</span></div><div style="padding:28px"><p style="margin:0 0 8px;color:#606456;font-size:12px;font-weight:700;letter-spacing:1.5px">EMAIL VERIFICATION</p><h1 style="margin:0 0 14px;font-size:24px;line-height:1.25">Verify your email</h1><p style="margin:0 0 22px;font-size:15px;line-height:1.6">Enter this one-time code in the Rollcall app to verify your address. Your account is created only after verification succeeds.</p><div style="padding:18px;background:#f5f5ee;border:1px solid #deded3;border-radius:12px;text-align:center;font-size:32px;font-weight:700;letter-spacing:8px">${escapeHtml(code)}</div><p style="margin:18px 0 0;font-size:14px;line-height:1.6"><b>This code expires in 10 minutes.</b> Never share it with anyone.</p><div style="height:1px;margin:24px 0;background:#e8e8e0"></div><p style="margin:0;color:#606456;font-size:13px;line-height:1.6">If you did not request a Rollcall account, ignore this email. No account has been created.</p></div></div><p style="max-width:520px;margin:16px auto 0;color:#77796e;font-size:12px;line-height:1.5">This automated message was sent because someone requested a Rollcall account using this email address.</p></div>`
    })
  });
  if (!response.ok) throw new Error("Email delivery failed.");
}

async function createVerifiedFirebaseAccount(env, email, password) {
  if (!env.FIREBASE_WEB_API_KEY) throw new Error("Account creation is not configured.");
  const endpoint = `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${encodeURIComponent(env.FIREBASE_WEB_API_KEY)}`;
  const created = await fetch(endpoint, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true })
  });
  const account = await created.json();
  if (!created.ok || !account.idToken) {
    const reason = String(account.error?.message || "");
    if (reason.includes("EMAIL_EXISTS")) throw new Error("This email already has a Rollcall account. Sign in instead.");
    if (reason.includes("INVALID_EMAIL")) throw new Error("Enter a valid email address.");
    if (reason.includes("WEAK_PASSWORD")) throw new Error("Choose a password with at least 8 characters.");
    throw new Error("The account could not be created. Try again.");
  }
  const verified = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:update?key=${encodeURIComponent(env.FIREBASE_WEB_API_KEY)}`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ idToken: account.idToken, emailVerified: true, returnSecureToken: false })
  });
  if (!verified.ok) {
    await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${encodeURIComponent(env.FIREBASE_WEB_API_KEY)}`, {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ idToken: account.idToken })
    }).catch(() => {});
    throw new Error("Email verification completed, but the account setup failed. Try again.");
  }
}

async function createWebSession(env, uid) {
  const token = b64url(random(32));
  const createdAt = now();
  await env.DB.prepare("INSERT INTO web_sessions(session_hash,uid,created_at,expires_at) VALUES(?,?,?,?)")
    .bind(await digest(`${env.SESSION_HASH_KEY}:${token}`), uid, createdAt, createdAt + SESSION_TTL_SECONDS).run();
  return json({ ok: true }, 200, { "set-cookie": `rc_session=${token}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=${SESSION_TTL_SECONDS}` });
}

function qrBrowserLabel(userAgent) {
  const browser = /Edg\//.test(userAgent) ? "Edge" : /Firefox\//.test(userAgent) ? "Firefox"
    : /Chrome\//.test(userAgent) ? "Chrome" : /Safari\//.test(userAgent) ? "Safari" : "Browser";
  const platform = /Windows NT/.test(userAgent) ? "Windows" : /Macintosh|Mac OS X/.test(userAgent) ? "macOS"
    : /Android/.test(userAgent) ? "Android" : /iPhone|iPad/.test(userAgent) ? "iOS"
      : /Linux/.test(userAgent) ? "Linux" : "device";
  return `${browser} on ${platform}`;
}

function validQrCredentials(challengeId, secret) {
  return /^[A-Za-z0-9_-]{24}$/.test(String(challengeId || ""))
    && /^[A-Za-z0-9_-]{43}$/.test(String(secret || ""));
}

async function trustedQrChallenge(request, env) {
  const user = await authFromHeader(request, env), body = await readBody(request, 3000);
  if (!validQrCredentials(body.challengeId, body.secret)) return { error: json({ error: "This QR sign-in request is invalid or expired." }, 404) };
  const device = await env.DB.prepare("SELECT device_id FROM trusted_devices WHERE uid=?").bind(user.uid).first();
  if (!device || device.device_id !== body.deviceId) return { error: json({ error: "This app is not the trusted Rollcall device." }, 403) };
  const row = await env.DB.prepare("SELECT * FROM web_qr_challenges WHERE id=? AND state='pending' AND expires_at>? AND consumed_at IS NULL")
    .bind(body.challengeId, now()).first();
  if (!row || !constantEqual(await hmac(`${body.challengeId}.${body.secret}`, env.SESSION_HASH_KEY), row.secret_hash))
    return { error: json({ error: "This QR sign-in request is invalid or expired." }, 404) };
  return { user, body, row };
}

function normalizeSnapshot(input) {
  if (!input || typeof input !== "object" || input.schemaVersion !== 1) throw new Error("Unsupported snapshot format.");
  for (const key of ["courses", "timetable", "classMarks", "leaves", "academicRecords", "exams"]) {
    if (!Array.isArray(input[key]) || input[key].length > 5000) throw new Error(`Invalid ${key} data.`);
  }
  const internalMarks = input.internalMarks ?? [];
  if (!Array.isArray(internalMarks) || internalMarks.length > 2000) throw new Error("Invalid internalMarks data.");
  const normalizedInternalMarks = internalMarks.map(row => {
    if (!row || typeof row !== "object" || Array.isArray(row)) throw new Error("Invalid internal mark.");
    const course = String(row.course || row.name || "").trim(), assessment = String(row.assessment || row.exam || "").trim();
    if (!course || course.length > 100 || !assessment || assessment.length > 100) throw new Error("Invalid internal mark.");
    let status = String(row.status || (row.score != null || row.marks != null ? "published" : "unpublished")).toLowerCase();
    if (!["published", "unpublished", "withheld", "missing"].includes(status)) throw new Error("Invalid internal mark status.");
    let score = null, maximum = null;
    if (status === "published") {
      score = Number(row.score ?? row.marks); maximum = Number(row.maximum ?? row.maxMarks);
      if (!Number.isFinite(score) || !Number.isFinite(maximum) || maximum <= 0 || maximum > 10000 || score < 0 || score > maximum)
        throw new Error("Invalid published internal mark value.");
    }
    const bounded = (value, max) => String(value || "").slice(0, max);
    const lastSyncedAt = Number(row.lastSyncedAt);
    return {
      id: bounded(row.assessmentId || row.id, 120), semester: bounded(row.semester, 80),
      courseCode: bounded(row.courseCode || row.code, 40), course, assessment, score, maximum, status,
      date: bounded(row.date, 40), publishedAt: bounded(row.publishedAt, 40),
      source: bounded(row.source || "KCT", 80), lastSyncedAt: Number.isFinite(lastSyncedAt) ? Math.max(0, lastSyncedAt) : 0
    };
  });
  const target = Number(input.target);
  if (!Number.isFinite(target) || target < 1 || target > 100) throw new Error("Invalid attendance target.");
  return { ...input, internalMarks: normalizedInternalMarks, target, updatedAt: now() };
}

async function route(request, env) {
  const url = new URL(request.url), path = url.pathname, method = request.method;
  if (method === "OPTIONS") return new Response(null, { status: 204, headers: { "allow": "GET, POST, PUT, OPTIONS" } });
  if ((method === "POST" || method === "PUT") && !sameOrigin(request)) return json({ error: "Invalid request origin." }, 403);

  if (path === "/api/auth/signup/start" && method === "POST") {
    const body = await readBody(request, 3000), email = String(body.email || "").trim().toLowerCase();
    if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json({ error: "Enter a valid email address." }, 400);
    const ipHash = await hmac(request.headers.get("cf-connecting-ip") || "unknown", env.SESSION_HASH_KEY);
    await env.DB.prepare("DELETE FROM signup_verification_sends WHERE created_at<?").bind(now() - 86400).run();
    const ipCount = await env.DB.prepare("SELECT COUNT(*) AS n FROM signup_verification_sends WHERE ip_hash=? AND created_at>?").bind(ipHash, now() - 900).first();
    if ((ipCount?.n || 0) >= 10) return json({ error: "Too many verification emails from this connection. Try again in 15 minutes." }, 429);
    const recent = await env.DB.prepare("SELECT created_at FROM signup_verification_sends WHERE email=? ORDER BY created_at DESC LIMIT 1").bind(email).first();
    if (recent && recent.created_at > now() - 60) return json({ error: "A verification code was just sent. Wait a minute before requesting another." }, 429);
    const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM signup_verification_sends WHERE email=? AND created_at>?").bind(email, now() - 900).first();
    if ((count?.n || 0) >= 5) return json({ error: "Too many verification emails. Try again in 15 minutes." }, 429);
    const code = makeEmailCode(), issuedAt = now();
    await env.DB.prepare("INSERT INTO pending_signups(email,code_hash,created_at,expires_at,attempts,used_at) VALUES(?,?,?,?,0,NULL) ON CONFLICT(email) DO UPDATE SET code_hash=excluded.code_hash,created_at=excluded.created_at,expires_at=excluded.expires_at,attempts=0,used_at=NULL")
      .bind(email, await hmac(code, env.SESSION_HASH_KEY), issuedAt, issuedAt + 600).run();
    await env.DB.prepare("INSERT INTO signup_verification_sends(email,ip_hash,created_at) VALUES(?,?,?)").bind(email, ipHash, issuedAt).run();
    try { await sendSignupVerificationEmail(env, email, code); }
    catch {
      await env.DB.prepare("DELETE FROM pending_signups WHERE email=? AND created_at=?").bind(email, issuedAt).run();
      return json({ error: "We could not send the verification email. Check the address and try again.", code: "email_delivery_failed" }, 503);
    }
    return json({ ok: true, expiresIn: 600 });
  }

  if (path === "/api/auth/signup/complete" && method === "POST") {
    const body = await readBody(request, 12_000), email = String(body.email || "").trim().toLowerCase();
    const code = String(body.code || "").trim(), password = String(body.password || "");
    if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
        || !/^\d{6}$/.test(code) || password.length < 8 || password.length > 128)
      return json({ error: "Enter the six-digit code and a password with at least 8 characters." }, 400);
    const signup = await env.DB.prepare("SELECT * FROM pending_signups WHERE email=? AND used_at IS NULL AND expires_at>? AND attempts<5").bind(email, now()).first();
    if (!signup) return json({ error: "That code expired or can no longer be used. Request a new one." }, 401);
    if (!constantEqual(await hmac(code, env.SESSION_HASH_KEY), signup.code_hash)) {
      await env.DB.prepare("UPDATE pending_signups SET attempts=attempts+1 WHERE email=? AND used_at IS NULL AND expires_at>? AND attempts<5").bind(email, now()).run();
      return json({ error: "That code was not accepted. Check it and try again." }, 401);
    }
    const claimed = await env.DB.prepare("UPDATE pending_signups SET used_at=? WHERE email=? AND used_at IS NULL AND expires_at>? AND attempts<5").bind(now(), email, now()).run();
    if (!claimed.meta.changes) return json({ error: "That code has already been used." }, 409);
    try { await createVerifiedFirebaseAccount(env, email, password); }
    catch (error) {
      await env.DB.prepare("DELETE FROM pending_signups WHERE email=?").bind(email).run();
      throw error;
    }
    await env.DB.prepare("DELETE FROM pending_signups WHERE email=?").bind(email).run();
    return json({ ok: true, emailVerified: true });
  }

  if (path === "/api/admin/overview" && method === "GET") {
    await adminFromHeader(request, env);
    const [devices, campaigns, active, pushFailures, firebaseUsers] = await Promise.all([
      env.DB.prepare("SELECT COUNT(*) AS count FROM trusted_devices").first(),
      env.DB.prepare("SELECT COUNT(*) AS count FROM command_center_campaigns").first(),
      env.DB.prepare("SELECT COUNT(*) AS count FROM command_center_campaigns WHERE status='published' AND expires_at>?").bind(now()).first(),
      env.DB.prepare("SELECT COUNT(*) AS count FROM command_center_campaigns WHERE status='published' AND push_status='failed'").first(),
      firebaseAccountCount(env)
    ]);
    return json({ firebaseUsers, registeredInstallations: devices?.count || 0, campaigns: campaigns?.count || 0,
      activeCampaigns: active?.count || 0, pushFailures: pushFailures?.count || 0 });
  }

  if (path === "/api/admin/accounts" && method === "GET") {
    await adminFromHeader(request, env);
    try { return json(await firebaseAccountDirectory(env)); }
    catch { return json({ error: "Firebase account details are unavailable. Check the service account’s Firebase Authentication read permission." }, 503); }
  }

  if (path === "/api/admin/releases" && method === "GET") {
    await adminFromHeader(request, env);
    const rows = await env.DB.prepare("SELECT * FROM devos_releases ORDER BY created_at DESC LIMIT 50").all();
    return json({ releases: (rows.results || []).map(releaseView), maxUploadBytes: RELEASE_MAX_BYTES });
  }

  if (path === "/api/admin/releases" && method === "POST") {
    const user = await releaseOwner(request, env), body = await readBody(request, 7000);
    const notes = String(body.notes || "").trim();
    if (!notes || notes.length > 5000) return json({ error: "Release notes must be 1–5,000 characters." }, 400);
    if (body.channel !== "stable" || !["optional", "recommended", "required"].includes(body.policy))
      return json({ error: "Choose a stable release and a supported update policy." }, 400);
    if (body.confirmAllInstallations !== true) return json({ error: "Confirm that a public stable release can be discovered by all existing app installations." }, 400);
    const id = crypto.randomUUID(), createdAt = now();
    await env.DB.prepare("INSERT INTO devos_releases(id,created_by,status,channel,policy,notes,created_at,updated_at) VALUES(?,?,'draft','stable',?,?,?,?)")
      .bind(id, user.uid, body.policy, notes, createdAt, createdAt).run();
    await auditReleaseAction(env, user.uid, "release_draft_created", id);
    return json({ release: releaseView(await releaseRow(env, id)) }, 201);
  }

  const releaseAction = path.match(/^\/api\/admin\/releases\/([0-9a-f-]{36})\/(apk|validate|approval-code|publish|cancel)$/);
  if (releaseAction && method === "POST") {
    const user = await releaseOwner(request, env), [, id, action] = releaseAction;
    const row = await releaseRow(env, id);
    if (!row) return json({ error: "Release draft not found." }, 404);
    if (action === "apk") {
      if (row.status !== "draft") return json({ error: "Only a new draft can receive an APK. Create another draft to retry." }, 409);
      if (!env.RELEASE_STAGING) throw new Error("Private release staging is not configured.");
      const type = request.headers.get("content-type") || "";
      if (!/^(application\/vnd\.android\.package-archive|application\/octet-stream)(?:;|$)/i.test(type))
        return json({ error: "Upload an APK file." }, 400);
      const length = Number(request.headers.get("content-length") || 0);
      if (length > RELEASE_MAX_BYTES) return json({ error: "APK is too large for private staging." }, 413);
      const bytes = await readBinary(request, RELEASE_MAX_BYTES);
      if (bytes.byteLength < 1_000_000 || bytes.byteLength > RELEASE_MAX_BYTES)
        return json({ error: "APK must be between 1 MB and 20 MiB." }, 400);
      const filename = String(request.headers.get("x-rollcall-file-name") || "Rollcall.apk").trim();
      if (filename.length > 100 || !/^[A-Za-z0-9][A-Za-z0-9 ._-]*\.apk$/i.test(filename) || filename.includes(".."))
        return json({ error: "Use a simple APK file name." }, 400);
      const sha256 = hex(await crypto.subtle.digest("SHA-256", bytes));
      await env.RELEASE_STAGING.put(`apk:${id}`, bytes, { expirationTtl: 14 * 86400 });
      const saved = await env.DB.prepare("UPDATE devos_releases SET status='uploaded',file_name=?,size_bytes=?,sha256=?,updated_at=? WHERE id=? AND status='draft'")
        .bind(filename, bytes.byteLength, sha256, now(), id).run();
      if (!saved.meta.changes) return json({ error: "Release state changed. Create a new draft." }, 409);
      await auditReleaseAction(env, user.uid, "release_apk_uploaded", id);
      return json({ release: releaseView(await releaseRow(env, id)) });
    }
    const body = await readBody(request, 1200);
    if (action === "validate") {
      if (row.status !== "uploaded" && row.status !== "failed") return json({ error: "Upload an APK before requesting validation." }, 409);
      if (!row.sha256) return json({ error: "The APK checksum is missing." }, 409);
      await env.DB.prepare("UPDATE devos_releases SET status='validation_queued',failure_reason='',worker_attempts=0,updated_at=? WHERE id=? AND status IN ('uploaded','failed')")
        .bind(now(), id).run();
      await auditReleaseAction(env, user.uid, "release_validation_requested", id);
      return json({ release: releaseView(await releaseRow(env, id)) });
    }
    if (action === "approval-code") {
      if (row.status !== "validated") return json({ error: "Validate the APK before requesting an approval code." }, 409);
      if (!user.email) return json({ error: "A verified admin email is required to approve this release." }, 403);
      if (row.approval_sent_at && row.approval_sent_at > now() - 60)
        return json({ error: "An approval code was just sent. Wait one minute." }, 429);
      const code = makeEmailCode(), sentAt = now();
      const reserved = await env.DB.prepare("UPDATE devos_releases SET approval_code_hash=?,approval_expires_at=?,approval_sent_at=?,approval_attempts=0 WHERE id=? AND status='validated'")
        .bind(await hmac(`${id}:${code}`, env.SESSION_HASH_KEY), sentAt + 600, sentAt, id).run();
      if (!reserved.meta.changes) return json({ error: "Release state changed. Refresh and try again." }, 409);
      try { await sendReleaseApprovalEmail(env, user.email, code, row); }
      catch (error) {
        await env.DB.prepare("UPDATE devos_releases SET approval_code_hash=NULL,approval_expires_at=NULL WHERE id=? AND approval_sent_at=?").bind(id, sentAt).run();
        throw error;
      }
      await auditReleaseAction(env, user.uid, "release_approval_code_sent", id);
      return json({ ok: true, expiresIn: 600 });
    }
    if (action === "publish") {
      if (row.status !== "validated") return json({ error: "Only a validated APK can be approved for publication." }, 409);
      if (body.confirmTag !== `v${row.version_name}` || !/^\d{6}$/.test(String(body.code || "")))
        return json({ error: "Confirm the exact release tag and enter the six-digit approval code." }, 400);
      if (!row.approval_code_hash || row.approval_expires_at <= now() || row.approval_attempts >= 5)
        return json({ error: "The approval code expired or reached its attempt limit. Request another." }, 401);
      if (!constantEqual(await hmac(`${id}:${body.code}`, env.SESSION_HASH_KEY), row.approval_code_hash)) {
        await env.DB.prepare("UPDATE devos_releases SET approval_attempts=approval_attempts+1 WHERE id=? AND status='validated'").bind(id).run();
        return json({ error: "That approval code was not accepted." }, 401);
      }
      const claimed = await env.DB.prepare("UPDATE devos_releases SET status='publish_queued',updated_at=?,approval_code_hash=NULL,approval_expires_at=NULL,worker_attempts=0 WHERE id=? AND status='validated' AND approval_code_hash=?")
        .bind(now(), id, row.approval_code_hash).run();
      if (!claimed.meta.changes) return json({ error: "Release state changed. Refresh and try again." }, 409);
      await auditReleaseAction(env, user.uid, "release_publish_approved", id);
      return json({ release: releaseView(await releaseRow(env, id)) });
    }
    if (!["draft", "uploaded", "validated", "failed"].includes(row.status))
      return json({ error: "This release is already in a workflow and cannot be cancelled." }, 409);
    const cancelled = await env.DB.prepare("UPDATE devos_releases SET status='cancelled',updated_at=? WHERE id=? AND status=?").bind(now(), id, row.status).run();
    if (!cancelled.meta.changes) return json({ error: "Release state changed. Refresh and try again." }, 409);
    if (env.RELEASE_STAGING) await env.RELEASE_STAGING.delete(`apk:${id}`);
    await auditReleaseAction(env, user.uid, "release_cancelled", id);
    return json({ release: releaseView(await releaseRow(env, id)) });
  }

  if (path === "/api/release-worker/next" && method === "GET") {
    releaseWorkerAuthorized(request, env);
    const row = await env.DB.prepare("SELECT * FROM devos_releases WHERE worker_attempts<3 AND (status IN ('validation_queued','publish_queued') OR (status IN ('validating','publishing') AND updated_at<?)) ORDER BY created_at LIMIT 1")
      .bind(now() - 1800).first();
    if (!row) return json({ task: null });
    const stage = ["validation_queued", "validating"].includes(row.status) ? "validate" : "publish";
    const target = stage === "validate" ? "validating" : "publishing";
    const claimed = await env.DB.prepare("UPDATE devos_releases SET status=?,worker_attempts=worker_attempts+1,updated_at=? WHERE id=? AND status=? AND updated_at=? AND worker_attempts<3")
      .bind(target, now(), row.id, row.status, row.updated_at).run();
    if (!claimed.meta.changes) return json({ task: null });
    return json({ task: { id: row.id, stage, sha256: row.sha256, sizeBytes: row.size_bytes,
      versionCode: row.version_code, versionName: row.version_name, notes: row.notes, policy: row.policy } });
  }

  const workerArtifact = path.match(/^\/api\/release-worker\/artifact\/([0-9a-f-]{36})$/);
  if (workerArtifact && method === "GET") {
    releaseWorkerAuthorized(request, env);
    if (!env.RELEASE_STAGING) throw new Error("Private release staging is not configured.");
    const row = await releaseRow(env, workerArtifact[1]);
    if (!row || !["validating", "publishing"].includes(row.status)) return json({ error: "Artifact is not available for this workflow." }, 404);
    const bytes = await env.RELEASE_STAGING.get(`apk:${row.id}`, "arrayBuffer");
    if (!bytes || bytes.byteLength !== row.size_bytes || hex(await crypto.subtle.digest("SHA-256", bytes)) !== row.sha256)
      return json({ error: "Staged APK is unavailable or failed its checksum." }, 503);
    return new Response(bytes, { headers: { "content-type": "application/vnd.android.package-archive", "cache-control": "no-store", "content-length": String(bytes.byteLength) } });
  }

  const workerResult = path.match(/^\/api\/release-worker\/([0-9a-f-]{36})\/(validated|published)$/);
  if (workerResult && method === "POST") {
    releaseWorkerAuthorized(request, env);
    const [, id, action] = workerResult, row = await releaseRow(env, id), body = await readBody(request, 3000);
    if (!row) return json({ error: "Release draft not found." }, 404);
    if (action === "validated") {
      if (row.status !== "validating") return json({ error: "Release is not awaiting validation." }, 409);
      const versionCode = Number(body.versionCode), versionName = String(body.versionName || "");
      const max = await env.DB.prepare("SELECT MAX(version_code) AS code FROM devos_releases WHERE status='published'").first();
      const valid = body.ok === true && body.packageName === "com.iniexe.rollcall"
        && body.sha256 === row.sha256 && body.signerSha256 === RELEASE_SIGNER
        && Number.isSafeInteger(versionCode) && versionCode > Math.max(31, max?.code || 0)
        && /^[A-Za-z0-9._-]{1,80}$/.test(versionName) && Number.isInteger(body.minSdk) && body.minSdk <= 23
        && Number.isInteger(body.targetSdk) && body.targetSdk >= 23 && body.targetSdk <= 36;
      await env.DB.prepare("UPDATE devos_releases SET status=?,version_code=?,version_name=?,min_sdk=?,target_sdk=?,signer_sha256=?,failure_reason=?,validated_at=?,updated_at=? WHERE id=? AND status='validating'")
        .bind(valid ? "validated" : "failed", valid ? versionCode : null, valid ? versionName : null,
          valid ? body.minSdk : null, valid ? body.targetSdk : null, valid ? body.signerSha256 : null,
          valid ? "" : String(body.error || "APK validation failed.").slice(0, 300), valid ? now() : null, now(), id).run();
      await auditReleaseAction(env, "release-worker", valid ? "release_validated" : "release_validation_failed", id, valid ? "success" : "failed");
      return json({ release: releaseView(await releaseRow(env, id)) });
    }
    if (row.status !== "publishing") return json({ error: "Release is not awaiting publication." }, 409);
    if (body.ok !== true || body.sha256 !== row.sha256 || body.tag !== `v${row.version_name}`
        || body.url !== `https://github.com/inianexe/Rollcall/releases/tag/${body.tag}`)
      return json({ error: "GitHub publication could not be verified." }, 400);
    const github = await fetch(`https://api.github.com/repos/inianexe/Rollcall/releases/tags/${encodeURIComponent(body.tag)}`,
      { headers: { accept: "application/vnd.github+json", "user-agent": "Rollcall-DevOS" } });
    if (!github.ok) return json({ error: "GitHub release is not visible yet. Retry completion." }, 503);
    const published = await github.json();
    const asset = (published.assets || []).find(item => item.name === `Rollcall-${row.version_name}.apk`
      && item.size === row.size_bytes && item.digest === `sha256:${row.sha256}`);
    if (!asset || published.draft || published.prerelease) return json({ error: "Published APK metadata does not match the validated artifact." }, 409);
    const saved = await env.DB.prepare("UPDATE devos_releases SET status='published',release_url=?,published_at=?,updated_at=?,push_status='failed' WHERE id=? AND status='publishing'")
      .bind(body.url, now(), now(), id).run();
    if (!saved.meta.changes) return json({ error: "Release state changed. Refresh and try again." }, 409);
    await auditReleaseAction(env, "release-worker", "release_published", id);
    try {
      await sendReleasePush(env, row);
      await env.DB.prepare("UPDATE devos_releases SET push_status='accepted' WHERE id=?").bind(id).run();
    } catch { /* The published APK remains available if FCM does not accept a hint. */ }
    if (env.RELEASE_STAGING) await env.RELEASE_STAGING.delete(`apk:${id}`);
    return json({ release: releaseView(await releaseRow(env, id)) });
  }

  if (path === "/api/admin/release-operations" && method === "GET") {
    await adminFromHeader(request, env);
    const headers = { accept: "application/vnd.github+json", "user-agent": "Rollcall-DevOS" };
    const options = { headers, cf: { cacheTtl: 30, cacheEverything: true } };
    try {
      const [releaseResponse, runResponse] = await Promise.all([
        fetch("https://api.github.com/repos/inianexe/Rollcall/releases?per_page=8", options),
        fetch("https://api.github.com/repos/inianexe/Rollcall/actions/runs?per_page=12", options)
      ]);
      if (!releaseResponse.ok || !runResponse.ok) throw new Error("GitHub release status is unavailable.");
      const [releaseData, runData] = await Promise.all([releaseResponse.json(), runResponse.json()]);
      if (!Array.isArray(releaseData) || !Array.isArray(runData.workflow_runs))
        throw new Error("GitHub release status is unavailable.");
      return json({
        releases: releaseData.map(release => ({
          tag: String(release.tag_name || "").slice(0, 100),
          name: String(release.name || release.tag_name || "Release").slice(0, 160),
          draft: release.draft === true,
          prerelease: release.prerelease === true,
          publishedAt: String(release.published_at || ""),
          url: /^https:\/\/github\.com\/inianexe\/Rollcall\/releases\//.test(release.html_url || "") ? release.html_url : "",
          apk: (Array.isArray(release.assets) ? release.assets : []).filter(asset => /\.apk$/i.test(asset.name || "")
            && /^https:\/\/github\.com\/inianexe\/Rollcall\/releases\/download\//.test(asset.browser_download_url || "")).map(asset => ({
            name: String(asset.name || "APK").slice(0, 120),
            size: Number.isSafeInteger(asset.size) && asset.size >= 0 ? asset.size : 0,
            downloads: Number.isSafeInteger(asset.download_count) && asset.download_count >= 0 ? asset.download_count : 0,
            url: /^https:\/\/github\.com\/inianexe\/Rollcall\/releases\/download\//.test(asset.browser_download_url || "") ? asset.browser_download_url : ""
          }))
        })),
        workflows: runData.workflow_runs.map(run => ({
          id: Number.isSafeInteger(run.id) ? run.id : 0,
          runNumber: Number.isSafeInteger(run.run_number) ? run.run_number : 0,
          name: String(run.name || "GitHub Actions").slice(0, 120),
          title: String(run.display_title || run.name || "Workflow run").slice(0, 160),
          status: String(run.status || "unknown").slice(0, 30),
          conclusion: String(run.conclusion || "").slice(0, 30),
          branch: String(run.head_branch || "").slice(0, 100),
          createdAt: String(run.created_at || ""),
          url: /^https:\/\/github\.com\/inianexe\/Rollcall\/actions\/runs\//.test(run.html_url || "") ? run.html_url : ""
        }))
      });
    } catch {
      return json({ error: "GitHub release and build status are temporarily unavailable." }, 503);
    }
  }

  if (path === "/api/admin/session" && method === "GET") {
    const user = await adminFromHeader(request, env);
    await env.DB.prepare("INSERT INTO command_center_audit(id,actor_uid,action,target_type,target_id,created_at,result) VALUES(?,?,?,'admin_console',NULL,?,'success')")
      .bind(crypto.randomUUID(), user.uid, "session_verified", now()).run();
    const events = await env.DB.prepare("SELECT action,created_at,result FROM command_center_audit ORDER BY created_at DESC LIMIT 20").all();
    return json({ authorized: true, role: "owner", email: user.email, recentAccess: events.results || [] });
  }

  if (path === "/api/admin/campaigns" && method === "GET") {
    await adminFromHeader(request, env);
    const rows = await env.DB.prepare("SELECT * FROM command_center_campaigns ORDER BY created_at DESC LIMIT 100").all();
    return json({ campaigns: (rows.results || []).map(campaignFromRow) });
  }

  if (path === "/api/admin/campaigns" && method === "POST") {
    const user = await adminFromHeader(request, env), fields = campaignFields(await readBody(request, 6000));
    const createdAt = now(), id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO command_center_campaigns(id,title,body,category,priority,channels_json,action_label,action_path,status,created_by,created_at,updated_at,published_at,expires_at,push_status) VALUES(?,?,?,?,?,?,?,?,'draft',?,?,?,NULL,?,'not_requested')")
      .bind(id, fields.title, fields.body, fields.category, fields.priority, JSON.stringify(fields.channels),
        fields.actionLabel, fields.actionPath, user.uid, createdAt, createdAt, fields.expiresAt).run();
    await auditAdminAction(env, user, "campaign_draft_created", id);
    return json({ campaign: { id, ...fields, status: "draft", createdAt, publishedAt: null, pushStatus: "not_requested" } }, 201);
  }

  const campaignAction = path.match(/^\/api\/admin\/campaigns\/([0-9a-f-]{36})\/(publish|revoke|retry-push)$/);
  if (campaignAction && method === "POST") {
    const user = await adminFromHeader(request, env), [, id, action] = campaignAction;
    const body = await readBody(request, 1000);
    const row = await env.DB.prepare("SELECT * FROM command_center_campaigns WHERE id=?").bind(id).first();
    if (!row) return json({ error: "Announcement not found." }, 404);
    if (action === "publish") {
      if (row.status !== "draft") return json({ error: "Only a draft announcement can be published." }, 409);
      if (body.confirmAllInstallations !== true) return json({ error: "Confirm that this announcement targets all eligible installations." }, 400);
      const wantsPush = JSON.parse(row.channels_json).includes("push");
      const publishedAt = now();
      const result = await env.DB.prepare("UPDATE command_center_campaigns SET status='published',published_at=?,updated_at=?,push_status=? WHERE id=? AND status='draft'")
        .bind(publishedAt, publishedAt, wantsPush ? "pending" : "not_requested", id).run();
      if (result.meta.changes !== 1) return json({ error: "Announcement state changed. Reload and try again." }, 409);
      await auditAdminAction(env, user, "campaign_published", id);
      let pushStatus = wantsPush ? "accepted" : "not_requested";
      if (wantsPush) {
        try { await sendCampaignPush(env, campaignFromRow({ ...row, status: "published", published_at: publishedAt })); }
        catch (_) { pushStatus = "failed"; await auditAdminAction(env, user, "campaign_push_failed", id, "failed"); }
        await env.DB.prepare("UPDATE command_center_campaigns SET push_status=?,updated_at=? WHERE id=?").bind(pushStatus, now(), id).run();
      }
      return json({ ok: true, status: "published", pushStatus, pushMeaning: pushStatus === "accepted" ? "accepted_by_fcm_not_delivery_confirmation" : pushStatus });
    }
    if (action === "retry-push") {
      if (row.status !== "published" || !JSON.parse(row.channels_json).includes("push"))
        return json({ error: "Only published announcements with push enabled can be retried." }, 409);
      if (row.push_status !== "failed") return json({ error: "Push delivery is not in a failed state." }, 409);
      const claimed = await env.DB.prepare("UPDATE command_center_campaigns SET push_status='pending',updated_at=? WHERE id=? AND push_status='failed'").bind(now(), id).run();
      if (claimed.meta.changes !== 1) return json({ error: "Push retry already started. Reload the campaign list." }, 409);
      try {
        await sendCampaignPush(env, campaignFromRow(row));
        await env.DB.prepare("UPDATE command_center_campaigns SET push_status='accepted',updated_at=? WHERE id=?").bind(now(), id).run();
        await auditAdminAction(env, user, "campaign_push_retried", id);
        return json({ ok: true, pushStatus: "accepted", pushMeaning: "accepted_by_fcm_not_delivery_confirmation" });
      } catch (_) {
        await env.DB.prepare("UPDATE command_center_campaigns SET push_status='failed',updated_at=? WHERE id=?").bind(now(), id).run();
        await auditAdminAction(env, user, "campaign_push_retry_failed", id, "failed");
        return json({ error: "Push could not be accepted. The inbox announcement remains published." }, 503);
      }
    }
    if (row.status !== "published") return json({ error: "Only a published announcement can be revoked." }, 409);
    if (body.confirmRevoke !== true) return json({ error: "Confirm that the announcement should be removed from active inboxes." }, 400);
    const revoked = await env.DB.prepare("UPDATE command_center_campaigns SET status='revoked',updated_at=? WHERE id=? AND status='published'").bind(now(), id).run();
    if (revoked.meta.changes !== 1) return json({ error: "Announcement state changed. Reload and try again." }, 409);
    await auditAdminAction(env, user, "campaign_revoked", id);
    return json({ ok: true, status: "revoked" });
  }

  if (path === "/api/announcements" && method === "GET") {
    const uid = await announcementUser(request, env);
    const rows = await env.DB.prepare("SELECT c.*,r.state AS receipt_state FROM command_center_campaigns c LEFT JOIN command_center_campaign_receipts r ON r.campaign_id=c.id AND r.uid=? WHERE c.status='published' AND c.expires_at>? ORDER BY c.published_at DESC LIMIT 50")
      .bind(uid, now()).all();
    return json({ announcements: (rows.results || []).map(row => ({ ...campaignFromRow(row), readState: row.receipt_state || "unread" })) });
  }

  const receipt = path.match(/^\/api\/announcements\/([0-9a-f-]{36})\/receipt$/);
  if (receipt && method === "POST") {
    const uid = await announcementUser(request, env), body = await readBody(request, 1000);
    if (!["read", "dismissed"].includes(body.state)) return json({ error: "Choose read or dismissed." }, 400);
    const saved = await env.DB.prepare("INSERT INTO command_center_campaign_receipts(campaign_id,uid,state,updated_at) SELECT id,?,?,? FROM command_center_campaigns WHERE id=? AND status='published' AND expires_at>? ON CONFLICT(campaign_id,uid) DO UPDATE SET state=excluded.state,updated_at=excluded.updated_at")
      .bind(uid, body.state, now(), receipt[1], now()).run();
    if (!saved.meta.changes) return json({ error: "Announcement not found or expired." }, 404);
    return json({ ok: true, state: body.state });
  }

  if (path === "/api/device/register" && method === "POST") {
    const user = await authFromHeader(request, env), body = await readBody(request, 12000);
    if (!/^[\w-]{12,100}$/.test(String(body.deviceId || "")) || String(body.fcmToken || "").length > 4096) return json({ error: "Invalid device details." }, 400);
    const existing = await env.DB.prepare("SELECT device_id FROM trusted_devices WHERE uid=?").bind(user.uid).first();
    if (existing && existing.device_id !== body.deviceId) {
      if (!body.emailCode) return json({ error: "Email confirmation is required to link a replacement phone.", code: "email_confirmation_required" }, 403);
      if (!/^\d{6}$/.test(String(body.emailCode))) return json({ error: "That email code was not accepted. Request a new code and try again.", code: "email_code_invalid" }, 403);
      const codeRow = await env.DB.prepare("SELECT code_hash FROM device_replacement_codes WHERE uid=? AND expires_at>? AND attempts<5 AND used_at IS NULL").bind(user.uid, now()).first();
      if (!codeRow) return json({ error: "That confirmation code expired or reached its attempt limit. Request a fresh email code.", code: "email_code_expired" }, 403);
      if (!constantEqual(await hmac(body.emailCode, env.SESSION_HASH_KEY), codeRow.code_hash)) {
        const attempt = await env.DB.prepare("UPDATE device_replacement_codes SET attempts=attempts+1 WHERE uid=? AND used_at IS NULL AND expires_at>? AND attempts<5").bind(user.uid, now()).run();
        if (!attempt.meta.changes) return json({ error: "That confirmation code expired or reached its attempt limit. Request a fresh email code.", code: "email_code_expired" }, 403);
        return json({ error: "That email code was not accepted. Request a new code and try again.", code: "email_code_invalid" }, 403);
      }
      const claimed = await env.DB.prepare("UPDATE device_replacement_codes SET used_at=? WHERE uid=? AND used_at IS NULL AND expires_at>? AND attempts<5").bind(now(), user.uid, now()).run();
      if (claimed.meta.changes !== 1) return json({ error: "That email code has expired or was already used. Request a new code.", code: "email_code_invalid" }, 403);
      await env.DB.prepare("DELETE FROM web_sessions WHERE uid=?").bind(user.uid).run();
    }
    await env.DB.prepare("INSERT INTO trusted_devices(uid,device_id,fcm_token,updated_at) VALUES(?,?,?,?) ON CONFLICT(uid) DO UPDATE SET device_id=excluded.device_id,fcm_token=excluded.fcm_token,updated_at=excluded.updated_at")
      .bind(user.uid, body.deviceId, body.fcmToken, now()).run();
    return json({ ok: true });
  }

  if (path === "/api/device/replacement-code" && method === "POST") {
    const user = await authFromHeader(request, env);
    if (!user.email) return json({ error: "Add and verify an email address on your Rollcall account before replacing a phone." }, 400);
    const device = await env.DB.prepare("SELECT device_id FROM trusted_devices WHERE uid=?").bind(user.uid).first();
    if (!device) return json({ error: "Connect your first phone before requesting a replacement code." }, 409);
    await env.DB.prepare("DELETE FROM device_replacement_email_sends WHERE created_at<?").bind(now() - 86400).run();
    const recent = await env.DB.prepare("SELECT created_at FROM device_replacement_email_sends WHERE uid=? ORDER BY created_at DESC LIMIT 1").bind(user.uid).first();
    if (recent && recent.created_at > now() - 60) return json({ error: "A code was just sent. Please wait a minute before requesting another.", code: "email_rate_limited" }, 429);
    const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM device_replacement_email_sends WHERE uid=? AND created_at>?").bind(user.uid, now() - 900).first();
    if (count.n >= 5) return json({ error: "Too many email-code requests. Try again in 15 minutes.", code: "email_rate_limited" }, 429);
    await env.DB.prepare("INSERT INTO device_replacement_email_sends(uid,created_at) VALUES(?,?)").bind(user.uid, now()).run();
    const code = makeEmailCode(), issuedAt = now();
    await env.DB.prepare("INSERT INTO device_replacement_codes(uid,code_hash,created_at,expires_at,attempts,used_at) VALUES(?,?,?,?,0,NULL) ON CONFLICT(uid) DO UPDATE SET code_hash=excluded.code_hash,created_at=excluded.created_at,expires_at=excluded.expires_at,attempts=0,used_at=NULL")
      .bind(user.uid, await hmac(code, env.SESSION_HASH_KEY), issuedAt, issuedAt + 600).run();
    try { await sendDeviceReplacementEmail(env, user.email, code); }
    catch (_) {
      await env.DB.prepare("DELETE FROM device_replacement_codes WHERE uid=? AND created_at=?").bind(user.uid, issuedAt).run();
      return json({ error: "We could not send the confirmation email. Check your connection and try again.", code: "email_delivery_failed" }, 503);
    }
    return json({ ok: true, expiresIn: 600 });
  }

  if (path === "/api/auth/challenge" && method === "POST") {
    const user = await authFromHeader(request, env);
    const device = await env.DB.prepare("SELECT fcm_token FROM trusted_devices WHERE uid=?").bind(user.uid).first();
    if (!device) return json({ error: "No trusted Rollcall app is linked to this account." }, 409);
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
    try { await sendCodePush(env, device.fcm_token, challengeId); }
    catch (error) { await env.DB.prepare("DELETE FROM web_challenges WHERE id=?").bind(challengeId).run(); throw error; }
    return json({ ok: true, challengeId, expiresAt: expires });
  }

  if (path === "/api/auth/qr/start" && method === "POST") {
    await readBody(request, 1000);
    const ip = request.headers.get("cf-connecting-ip") || "unknown";
    const ipHash = await hmac(ip.slice(0, 100), env.SESSION_HASH_KEY);
    await env.DB.prepare("DELETE FROM web_qr_challenge_sends WHERE created_at<?").bind(now() - 86400).run();
    await env.DB.prepare("DELETE FROM web_qr_challenges WHERE expires_at<=? OR consumed_at IS NOT NULL").bind(now()).run();
    const sentAt = now();
    const allowed = await env.DB.prepare("INSERT INTO web_qr_challenge_sends(ip_hash,created_at) SELECT ?,? WHERE (SELECT COUNT(*) FROM web_qr_challenge_sends WHERE ip_hash=? AND created_at>?) < 10")
      .bind(ipHash, sentAt, ipHash, sentAt - 900).run();
    if (!allowed.meta.changes) return json({ error: "Too many QR sign-in requests. Try again in 15 minutes." }, 429);
    const challengeId = b64url(random(18)), secret = b64url(random(32)), expiresAt = sentAt + QR_CHALLENGE_TTL_SECONDS;
    const browserLabel = qrBrowserLabel(request.headers.get("user-agent") || "");
    await env.DB.prepare("INSERT INTO web_qr_challenges(id,secret_hash,state,uid,browser_label,created_at,expires_at,consumed_at) VALUES(?,?,'pending',NULL,?,?,?,NULL)")
      .bind(challengeId, await hmac(`${challengeId}.${secret}`, env.SESSION_HASH_KEY), browserLabel, sentAt, expiresAt).run();
    return json({ ok: true, challengeId, secret, scanPayload: `rollcall://web-sign-in?challenge=${challengeId}&token=${secret}`, browserLabel, expiresAt });
  }

  if (path === "/api/auth/qr/preview" && method === "POST") {
    const result = await trustedQrChallenge(request, env);
    if (result.error) return result.error;
    return json({ browserLabel: result.row.browser_label, expiresAt: result.row.expires_at });
  }

  if (path === "/api/auth/qr/approve" && method === "POST") {
    const result = await trustedQrChallenge(request, env);
    if (result.error) return result.error;
    const changed = await env.DB.prepare("UPDATE web_qr_challenges SET state='approved',uid=? WHERE id=? AND state='pending' AND expires_at>? AND consumed_at IS NULL")
      .bind(result.user.uid, result.body.challengeId, now()).run();
    if (!changed.meta.changes) return json({ error: "This QR sign-in request was already handled." }, 409);
    return json({ ok: true });
  }

  if (path === "/api/auth/qr/deny" && method === "POST") {
    const result = await trustedQrChallenge(request, env);
    if (result.error) return result.error;
    const changed = await env.DB.prepare("UPDATE web_qr_challenges SET state='denied',uid=? WHERE id=? AND state='pending' AND expires_at>? AND consumed_at IS NULL")
      .bind(result.user.uid, result.body.challengeId, now()).run();
    if (!changed.meta.changes) return json({ error: "This QR sign-in request was already handled." }, 409);
    return json({ ok: true });
  }

  if (path === "/api/auth/qr/poll" && method === "POST") {
    const body = await readBody(request, 3000);
    if (!validQrCredentials(body.challengeId, body.secret)) return json({ error: "This QR sign-in request expired." }, 404);
    const row = await env.DB.prepare("SELECT * FROM web_qr_challenges WHERE id=? AND expires_at>? AND consumed_at IS NULL")
      .bind(body.challengeId, now()).first();
    if (!row || !constantEqual(await hmac(`${body.challengeId}.${body.secret}`, env.SESSION_HASH_KEY), row.secret_hash))
      return json({ error: "This QR sign-in request expired." }, 404);
    if (row.state === "denied") return json({ state: "denied" });
    if (row.state !== "approved" || !row.uid) return json({ state: "pending" });
    const consumed = await env.DB.prepare("UPDATE web_qr_challenges SET state='consumed',consumed_at=? WHERE id=? AND state='approved' AND expires_at>? AND consumed_at IS NULL")
      .bind(now(), body.challengeId, now()).run();
    if (!consumed.meta.changes) return json({ error: "This QR sign-in request was already used." }, 409);
    const session = await createWebSession(env, row.uid);
    const headers = new Headers(session.headers);
    return json({ state: "signed_in" }, 200, { "set-cookie": headers.get("set-cookie") });
  }

  if (path === "/api/auth/pending" && method === "POST") {
    const user = await authFromHeader(request, env), body = await readBody(request, 3000);
    const device = await env.DB.prepare("SELECT device_id FROM trusted_devices WHERE uid=?").bind(user.uid).first();
    if (!device || device.device_id !== body.deviceId) return json({ error: "This app is not the trusted Rollcall device." }, 403);
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
    const user = await authFromHeader(request, env);
    await env.DB.batch(["trusted_devices", "snapshots", "web_challenges", "web_challenge_sends", "web_sessions", "web_qr_challenges", "device_replacement_codes", "device_replacement_email_sends"].map(table => env.DB.prepare(`DELETE FROM ${table} WHERE uid=?`).bind(user.uid)));
    return json({ ok: true });
  }

  return json({ error: "Not found." }, 404);
}

export async function handleApi(request, env) {
  try { return await route(request, env); }
  catch (error) {
    const message = error?.message || "Request failed.";
    if (/^this email already has a rollcall account/i.test(message)) return json({ error: message }, 409);
    if (/^sign in|^verify your email|^invalid sign-in token\./i.test(message)) return json({ error: message }, 401);
    if (/not authorized/i.test(message)) return json({ error: message }, 403);
    if (/^invalid request origin\./i.test(message)) return json({ error: message }, 403);
    if (/too large/i.test(message)) return json({ error: message }, 413);
    if (/not configured|push service|email delivery|approval email could not|could not be reached|provider rejected|token key is unavailable/i.test(message))
      return json({ error: message }, 503);
    if (error instanceof SyntaxError || /^(invalid |unsupported |title is required|choose a supported|choose an expiration|announcements must|use an internal)/i.test(message))
      return json({ error: message }, 400);
    console.error("Rollcall API request failed", request.method, new URL(request.url).pathname, error);
    return json({ error: "The service could not complete this request. Please try again." }, 500);
  }
}
