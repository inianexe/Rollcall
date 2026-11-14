# Android ↔ Web API contract

Status: implemented in Android `1.0.25` / snapshot schema version `1` and the Cloudflare Pages Worker.

## Ownership

- Android owns MyCamu/KCT portal sessions, provider parsing, attendance calculations, local snapshot persistence, and upload scheduling.
- `CloudAccountClient` owns Android HTTP requests to the Rollcall account API. `CloudSnapshotPayload` serializes the persisted phone snapshot.
- `web/functions/_api.js` owns Firebase token verification, per-install app registrations, one-time sign-in and phone-link codes, sessions, and D1 snapshots.
- `web/cloud-auth.js` owns browser Firebase sign-in and calls the same-origin Worker API. `web/app.js` renders account snapshots read-only; local browser mode remains independent.
- MyCamu/KCT provider endpoints and payloads are not part of this contract. Their details are embedded in Android portal code and can change independently.

## Authentication and transport

The API base URL is compiled into Android as `BuildConfig.ROLLCALL_API_BASE_URL`, defaulting to `https://rollcall-campus-companion.pages.dev/api`. All requests and responses use JSON except an empty `OPTIONS` response. Responses set `Cache-Control: no-store`.

Phone requests and browser challenge creation send a Firebase ID token in `Authorization: Bearer <id-token>`. Protected account operations require a verified email. Snapshot deletion, browser-session revocation, and account deletion additionally require Firebase `auth_time` no more than five minutes old. Browser data requests use the Worker-issued `rc_session` cookie (`Secure; HttpOnly; SameSite=Strict; Path=/; Max-Age=2592000`) and same-origin checks. Session tokens are random, only their keyed hashes are stored in D1, and both cookie and server row expire after 30 days. Session management returns random IDs and activity metadata, never cookie hashes.

Mutating `POST`, `PUT`, and `DELETE` requests require a same-origin `Origin` when the header is present. API errors use JSON `{ "error": "...", "code": "..." }`; `code` is present for selected client-handled cases.

## Endpoints

| Method and path | Caller / auth | Request fields | Success | Key failure statuses |
|---|---|---|---|---|
| `POST /device/trust` | Android / verified Firebase bearer | `deviceId`, `fcmToken` | `{ "ok": true }` | `400` invalid details; `409` installation belongs to another UID |
| `POST /device/untrust` | Android / verified Firebase bearer | `deviceId` | `{ "ok": true }` | `400` invalid device details |
| `POST /device/register` | Android / Firebase bearer | `deviceId`, `fcmToken`, optional `replacementCode` (six digits), `enableSync` (`false` retains a paused gate; omitted/`true` preserves legacy explicit-connect behavior) | `{ "ok": true }` | `400` invalid device; `403` `replacement_code_required`, `replacement_code_invalid`, or `replacement_code_expired` |
| `POST /device/sync/pause` | Android / verified Firebase bearer | empty object | `{ "ok": true, "syncEnabled": false }` | `401` invalid/unverified bearer |
| `POST /device/replacement-code` | Android / verified Firebase bearer | `deviceId` | `{ "ok": true, "expiresIn": 600, "sentTo": count }` | `400` invalid device; `409` no trusted device/other signed-in app; `429` `replacement_code_rate_limited`; `503` `replacement_push_failed` |
| `POST /device/approval/enroll` | Android / verified Firebase bearer | `deviceId`, `fcmToken`, RSA `publicKey`, optional `replacementCode` | `{ "ok": true, "enrolled": true }` | `400` invalid key; `403` missing/invalid phone-link code; `409` existing legacy phone must use nonce flow |
| `POST /device/approval/enroll-existing` | Android / verified Firebase bearer | `deviceId` | `{ "ok": true, "requestId", "expiresAt" }`; a one-time nonce is pushed to the existing phone and never returned | `409` no legacy enrollment needed; `429` setup throttled; `503` push unavailable |
| `POST /device/approval/enroll-complete` | Android / verified Firebase bearer | `deviceId`, `requestId`, `nonce`, `publicKey`, `signature` | `{ "ok": true, "enrolled": true }` | `401` expired/wrong nonce; `403` invalid signature; `409` request already used |
| `POST /auth/challenge` | Browser / verified Firebase bearer | empty object | `{ "ok": true, "challengeId", "expiresAt", "matchCode" }`; push sent only to approval devices | `409` no approval phone; `429` too many requests; `503` push service unavailable |
| `POST /auth/approve` or `/auth/deny` | Android / verified Firebase bearer and enrolled device signature | `deviceId`, `challengeId`, `signature` | `{ "ok": true }`; signed action binds UID/device/challenge/match number | `403` invalid proof; `404` unavailable challenge; `409` already handled |
| `POST /auth/status` | Browser / same-origin | `challengeId` | `{ "status": "pending" | "denied" }`, or `{ "ok": true }` and sets session cookie after approval | `404` expired challenge; `409` already completed |
| `POST /auth/verify` | Browser / same-origin | `challengeId`, `code` (six digits) | `{ "ok": true }` and sets `rc_session` | `403` origin mismatch; `401` expired, exhausted, invalid, or consumed code |
| `GET /auth/session` | Browser / `rc_session` | — | `{ "signedIn": true, "updatedAt": epoch seconds }` | `401` `{ "signedIn": false }` |
| `GET /auth/sessions` | Android / verified Firebase bearer | — | `{ "sessions": [{ "session_id", "created_at", "last_used_at", "expires_at", "user_agent" }] }` | `401` invalid/unverified bearer |
| `POST /auth/sessions/revoke` | Android / verified Firebase bearer, recent auth | `sessionId` or `{ "all": true }` | `{ "ok": true, "revoked": 1 | "all" }` | `401` stale auth; `404` session no longer active |
| `POST /auth/logout` | Browser / same-origin, cookie optional | empty object | `{ "ok": true }`, clears cookie | `200` even when already signed out |
| `PUT /snapshot` | Android / verified Firebase bearer, trusted `deviceId`, server sync gate enabled | `deviceId`, `snapshot` | `{ "ok": true, "updatedAt": epoch seconds }` | `403` device mismatch or sync paused; `413` oversized serialized snapshot; `400` invalid snapshot |
| `GET /snapshot` | Browser / `rc_session` | — | `{ "snapshot": object, "updatedAt": epoch seconds }` | `401` not signed in; `404` no uploaded snapshot |
| `DELETE /snapshot` | Android / verified Firebase bearer, recent auth | — | `{ "ok": true }`; deletes only the cloud snapshot | `401` stale auth |
| `POST /account/delete` | Android / verified Firebase bearer, reauthenticated within five minutes | empty object | `{ "ok": true }`; deletes Rollcall D1 rows for the UID | `401` missing/invalid/unverified/stale bearer token |

Unknown method/path combinations return `404`. `OPTIONS` returns `204` with `Allow: GET, POST, PUT, DELETE, OPTIONS`.

Firebase Authentication account deletion is performed by the Android client separately; `/account/delete` only purges Rollcall-owned D1 rows. This multi-service sequence is not transactional: if Firebase deletion fails after the API succeeds, the Firebase identity remains but its Rollcall D1 rows have already been removed.

## Snapshot schema v1

`CloudSnapshotPayload.build` sends:

```json
{
  "schemaVersion": 1,
  "name": "",
  "institution": "",
  "rollNumber": "",
  "rollNumberLabel": "Roll No.",
  "target": 75,
  "updatedAt": 0,
  "courses": [],
  "timetable": [],
  "classMarks": [],
  "leaves": [],
  "academicRecords": [],
  "academicUpdatedAt": 0,
  "exams": []
}
```

The phone emits the listed fields for `schemaVersion: 1`; the server validates `schemaVersion`, `target`, and all six arrays. `target` must be finite and within 1–100. Each array is limited to 5000 entries; the serialized request snapshot is limited to 1,900,000 bytes. Android class/timetable/leave timestamps (`startsAt`, `endsAt`) are Unix epoch milliseconds. `updatedAt` and `academicUpdatedAt` are supplied from Android local millisecond clocks, but the server replaces snapshot `updatedAt` with its own Unix epoch seconds before storage. The response and D1 `updated_at` also use epoch seconds. Consumers must not compare these mixed units without conversion.

Course entries contain `code`, `name`, `attended`, `total`, `percentage` (number or `null`), and `countsKnown`. The other collections are projected from the Android model fields in `CloudSnapshotPayload`; treat unknown fields as forward-compatible and ignore them. Schema changes must be coordinated across `CloudSnapshotPayload`, `normalizeSnapshot`, the web renderer, tests, and this document.

## One-time codes and limits

- Browser sign-in: challenge expires after 300 seconds; code fallback allows five attempts. Up to five challenge sends per account are accepted per 15 minutes. Approval and denial proofs are single-use. Delivery targets enrolled approval devices only; D1 stores the code HMAC and a short-lived encrypted fallback value.
- Phone linking: a code expires after 600 seconds, allows five attempts, permits one send per minute, and permits five sends per account per 15 minutes. Delivery targets other enrolled approval phones only; D1 stores only an HMAC of the code. Successful device replacement invalidates existing web sessions.
- `device/register`, approval enrollment, and signed approval request bodies are capped at 12,000 bytes; challenge and code bodies at 3,000 bytes. Other JSON requests use the 1,900,000-byte cap.
- Firebase ID tokens are limited to 8192 characters and must match the configured project, issuer, signature, and expiry.

## D1 migrations and release safety

Apply migrations in order after reviewing them. Migration `0006_approval_devices.sql` copies the current trusted phone into an approval-device row without a key so it can authorize its own key enrollment. Migration `0007_web_session_management.sql` backfills opaque management IDs and activity metadata for existing browser sessions. Migration `0008_cloud_sync_gate.sql` preserves sync for existing trusted devices, but lets pause/delete disable uploads server-side until the user explicitly opts in again. Migration `0009_clear_unused_recovery_codes.sql` clears legacy recovery-code hashes; no current code path uses those credentials. Apply these before deploying the Worker code that reads the new columns. Do not run remote migrations as part of code verification.

## Contract verification

- Web: `cd web && npm test` (Worker route behavior and request limits).
- Android: `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` (model and payload integration compile; instrumentation is not run by this command).
- Device/provider and production Cloudflare/Firebase behavior require separate controlled verification; local tests do not call production services.
