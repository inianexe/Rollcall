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

Phone requests and browser challenge creation send a Firebase ID token in `Authorization: Bearer <id-token>`. Protected account operations require a verified email. `POST /account/delete` additionally requires a Firebase `auth_time` no more than five minutes old, so clients must reauthenticate before calling it. Browser data requests use the Worker-issued `rc_session` cookie (`Secure; HttpOnly; SameSite=Strict; Path=/; Max-Age=2592000`) and same-origin checks. Session tokens are random, only their SHA-256 keyed hashes are stored in D1, and both cookie and server row expire after 30 days. Logout/device replacement/account deletion removes the applicable session rows.

Mutating `POST` and `PUT` requests require a same-origin `Origin` when the header is present. API errors use JSON `{ "error": "...", "code": "..." }`; `code` is present for selected client-handled cases.

## Endpoints

| Method and path | Caller / auth | Request fields | Success | Key failure statuses |
|---|---|---|---|---|
| `POST /device/trust` | Android / verified Firebase bearer | `deviceId`, `fcmToken` | `{ "ok": true }` | `400` invalid device details |
| `POST /device/untrust` | Android / verified Firebase bearer | `deviceId` | `{ "ok": true }` | `400` invalid device details |
| `POST /device/register` | Android / Firebase bearer | `deviceId`, `fcmToken`, optional `replacementCode` (six digits) | `{ "ok": true }` | `400` invalid device; `403` `replacement_code_required`, `replacement_code_invalid`, or `replacement_code_expired` |
| `POST /device/replacement-code` | Android / verified Firebase bearer | `deviceId` | `{ "ok": true, "expiresIn": 600, "sentTo": count }` | `400` invalid device; `409` no trusted device/other signed-in app; `429` `replacement_code_rate_limited`; `503` `replacement_push_failed` |
| `POST /auth/challenge` | Browser / verified Firebase bearer | empty object | `{ "ok": true, "challengeId": string, "expiresAt": epoch seconds }`; push sent to every registered app | `409` no signed-in app; `429` too many requests; `503` push service unavailable |
| `POST /auth/pending` | Android / verified Firebase bearer | `deviceId`, `challengeId` | `{ "code": six digits, "expiresAt": epoch seconds }` | `403` app is not signed in to the UID; `404` expired challenge |
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

- Browser sign-in: a challenge code expires after 300 seconds and allows five verification attempts. Up to five challenge sends per account are accepted per 15 minutes, including requests whose challenge was consumed; send timestamps are retained for one day. Delivery is attempted separately to every registered app installation; the code is encrypted in D1 and stored as an HMAC for verification.
- Phone linking: a code expires after 600 seconds, allows five attempts, permits one send per minute, and permits five sends per account per 15 minutes. Delivery is attempted to every other registered app installation; D1 stores only an HMAC of the code. Successful linking invalidates existing web sessions.
- `device/register` request bodies are capped at 12,000 bytes; `auth/pending` and `auth/verify` bodies at 3,000 bytes. Other JSON requests use the 1,900,000-byte cap.
- Firebase ID tokens are limited to 8192 characters and must match the configured project, issuer, signature, and expiry.

## D1 migrations and release safety

Apply migrations in order after reviewing them. Migration `0002_email_device_replacement.sql` adds short-lived code storage and retains legacy recovery-code rows. Migration `0003_expire_web_sessions.sql` sets existing sessions to expire 30 days after their original creation, so sessions older than 30 days require sign-in. Migration `0004_limit_web_challenge_sends.sql` adds durable challenge-send counters. Migration `0005_trusted_app_devices.sql` migrates the current trusted installation into the per-install registry and replaces the email-send rate ledger while copying its recent timestamps. Do not run remote migrations as part of code verification.

## Contract verification

- Web: `cd web && npm test` (Worker route behavior and request limits).
- Android: `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` (model and payload integration compile; instrumentation is not run by this command).
- Device/provider and production Cloudflare/Firebase behavior require separate controlled verification; local tests do not call production services.
