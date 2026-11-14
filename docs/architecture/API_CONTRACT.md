# Android ↔ Web API contract

Status: implemented in Android `1.0.24` / schema version `1` and the Cloudflare Pages Worker. This document records the existing wire contract; it does not imply that the production Worker has the latest code or migrations.

## Ownership

- Android owns MyCamu/KCT portal sessions, provider parsing, attendance calculations, local snapshot persistence, and upload scheduling.
- `CloudAccountClient` owns Android HTTP requests to the Rollcall account API. `CloudSnapshotPayload` serializes the persisted phone snapshot.
- `web/functions/_api.js` owns Firebase token verification, trusted-device checks, one-time sign-in challenges, replacement-phone email codes, sessions, and D1 snapshots.
- `web/cloud-auth.js` owns browser Firebase sign-in and calls the same-origin Worker API. `web/app.js` renders account snapshots read-only; local browser mode remains independent.
- MyCamu/KCT provider endpoints and payloads are not part of this contract. Their details are embedded in Android portal code and can change independently.

## Authentication and transport

The API base URL is compiled into Android as `BuildConfig.ROLLCALL_API_BASE_URL`, defaulting to `https://rollcall-campus-companion.pages.dev/api`. All requests and responses use JSON except an empty `OPTIONS` response. Responses set `Cache-Control: no-store`.

Phone requests and browser challenge creation send a Firebase ID token in `Authorization: Bearer <id-token>`. Protected account operations require a verified email. `POST /account/delete` additionally requires a Firebase `auth_time` no more than five minutes old, so clients must reauthenticate before calling it. Browser data requests use the Worker-issued `rc_session` cookie (`Secure; HttpOnly; SameSite=Strict; Path=/; Max-Age=2592000`) and same-origin checks. Session tokens are random, only their SHA-256 keyed hashes are stored in D1, and both cookie and server row expire after 30 days. Logout/device replacement/account deletion removes the applicable session rows.

Mutating `POST` and `PUT` requests require a same-origin `Origin` when the header is present. API errors use JSON `{ "error": "...", "code": "..." }`; `code` is present for selected client-handled cases.

## Endpoints

| Method and path | Caller / auth | Request fields | Success | Key failure statuses |
|---|---|---|---|---|
| `POST /device/register` | Android / Firebase bearer | `deviceId` (12–100 letters, digits, `_`, or `-`), `fcmToken` (up to 4096 chars), optional `emailCode` (six digits) | `{ "ok": true }` | `400` invalid device; `403` `email_confirmation_required`, `email_code_invalid`, or `email_code_expired` |
| `POST /device/replacement-code` | Android / verified Firebase bearer | empty object | `{ "ok": true, "expiresIn": 600 }` | `400` no email; `409` no trusted device; `429` `email_rate_limited`; `503` `email_delivery_failed` |
| `POST /auth/challenge` | Browser / verified Firebase bearer | empty object | `{ "ok": true, "challengeId": string, "expiresAt": epoch seconds }` | `409` no trusted app; `429` too many requests; `503` push service unavailable |
| `POST /auth/pending` | Android / verified Firebase bearer | `deviceId`, `challengeId` | `{ "code": six digits, "expiresAt": epoch seconds }` | `403` untrusted device; `404` expired challenge |
| `POST /auth/verify` | Browser / same-origin | `challengeId`, `code` (six digits) | `{ "ok": true }` and sets `rc_session` | `403` origin mismatch; `401` expired, exhausted, invalid, or consumed code |
| `GET /auth/session` | Browser / `rc_session` | — | `{ "signedIn": true, "updatedAt": epoch seconds }` | `401` `{ "signedIn": false }` |
| `POST /auth/logout` | Browser / same-origin, cookie optional | empty object | `{ "ok": true }`, clears cookie | `200` even when already signed out |
| `PUT /snapshot` | Android / verified Firebase bearer and trusted `deviceId` | `deviceId`, `snapshot` | `{ "ok": true, "updatedAt": epoch seconds }` | `403` device mismatch; `413` oversized serialized snapshot; `400` invalid snapshot |
| `GET /snapshot` | Browser / `rc_session` | — | `{ "snapshot": object, "updatedAt": epoch seconds }` | `401` not signed in; `404` no uploaded snapshot |
| `POST /account/delete` | Android / verified Firebase bearer, reauthenticated within five minutes | empty object | `{ "ok": true }`; deletes Rollcall D1 rows for the UID | `401` missing/invalid/unverified/stale bearer token |

Unknown method/path combinations return `404`. `OPTIONS` returns `204` with `Allow: GET, POST, PUT, OPTIONS`.

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

- Browser sign-in: a challenge code expires after 300 seconds and allows five verification attempts. Up to five challenge sends per account are accepted per 15 minutes, including requests whose challenge was consumed; send timestamps are retained for one day. Delivery is through the trusted app’s FCM token; the code is encrypted in D1 and stored as an HMAC for verification.
- Phone replacement: an email code expires after 600 seconds, up to five attempts, at most one send per minute, and five sends per account per 15 minutes. D1 stores only an HMAC of the code. Successful replacement invalidates existing web sessions.
- `device/register` request bodies are capped at 12,000 bytes; `auth/pending` and `auth/verify` bodies at 3,000 bytes. Other JSON requests use the 1,900,000-byte cap.
- Firebase ID tokens are limited to 8192 characters and must match the configured project, issuer, signature, and expiry.

## D1 migrations and release safety

Apply migrations in order only after reviewing them against a current D1 backup. Migration `0002_email_device_replacement.sql` drops the old `recovery_codes` table after introducing email replacement codes; applying it is destructive to any still-needed recovery-code rows. Migration `0003_expire_web_sessions.sql` sets existing sessions to expire 30 days after their original creation, so sessions older than 30 days will require sign-in after rollout. Migration `0004_limit_web_challenge_sends.sql` adds durable challenge-send counters. Do not run remote migrations as part of code verification. Production migration timing, backup/restore, email secrets, and deployed Worker version are operator-controlled.

## Contract verification

- Web: `cd web && npm test` (Worker route behavior and request limits).
- Android: `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` (model and payload integration compile; instrumentation is not run by this command).
- Device/provider and production Cloudflare/Firebase behavior require separate controlled verification; local tests do not call production services.
