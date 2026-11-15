# Android ↔ Web API contract

Status: implemented in Android `1.0.24` / schema version `1` and the Cloudflare Pages Worker. This document records the existing wire contract; it does not imply that the production Worker has the latest code or migrations.

## Ownership

- Android owns MyCamu/KCT portal sessions, provider parsing, attendance calculations, local snapshot persistence, and upload scheduling.
- `CloudAccountClient` owns Android HTTP requests to the Rollcall account API. `CloudSnapshotPayload` serializes the persisted phone snapshot.
- `web/functions/_api.js` owns Firebase token verification, trusted-device checks, one-time sign-in challenges, replacement-phone email codes, sessions, D1 snapshots, and server-validated Command Center access.
- `web/cloud-auth.js` owns browser Firebase sign-in and calls the same-origin Worker API. `web/app.js` renders account snapshots read-only; local browser mode remains independent.
- MyCamu/KCT provider endpoints and payloads are not part of this contract. Their details are embedded in Android portal code and can change independently.

## Authentication and transport

The API base URL is compiled into Android as `BuildConfig.ROLLCALL_API_BASE_URL`, defaulting to `https://rollcall-campus-companion.pages.dev/api`. All requests and responses use JSON except an empty `OPTIONS` response. Responses set `Cache-Control: no-store`.

Phone requests and browser challenge creation send a Firebase ID token in `Authorization: Bearer <id-token>`. Protected account operations require a verified email. Browser data requests use the Worker-issued `rc_session` cookie (`Secure; HttpOnly; SameSite=Strict; Path=/; Max-Age=2592000`) and same-origin checks. Session tokens are random, only their SHA-256 keyed hashes are stored in D1, and both cookie and server row expire after 30 days. Logout/device replacement/account deletion removes the applicable session rows.

Mutating `POST` and `PUT` requests require a same-origin `Origin` when the header is present. API errors use JSON `{ "error": "...", "code": "..." }`; `code` is present for selected client-handled cases.

## Endpoints

| Method and path | Caller / auth | Request fields | Success | Key failure statuses |
|---|---|---|---|---|
| `GET /admin/session` | Command Center / verified Firebase bearer whose UID is in `COMMAND_CENTER_ADMIN_UIDS` or whose verified email is in `COMMAND_CENTER_ADMIN_EMAILS`; a trusted-phone web session is accepted only when its UID is allowlisted | — | `{ "authorized": true, "role": "owner", "email": string, "recentAccess": [] }`; successful checks are recorded in `command_center_audit` | `401` missing/invalid/unverified identity; `403` identity not allowlisted; `503` owner allowlist not configured |
| `GET /admin/overview` | Command Center owner | — | Installation registry count and campaign/push state totals | `401`/`403`/`503` as above |
| `GET /admin/accounts` | Command Center owner | — | Latest Firebase account identities with creation/last-sign-in times; server filters configured admins into a separate list and returns only display name, email, verification, provider, and timestamps | `401`/`403`/`503` as above |
| `GET /admin/release-operations` | Command Center owner | — | Read-only, allowlisted published release metadata and recent workflow runs from the public GitHub repository API | `401`/`403`; `503` when GitHub is unavailable |
| `GET /admin/campaigns` | Command Center owner | — | Up to 100 most recent campaigns | `401`/`403`/`503` as above |
| `POST /admin/campaigns` | Command Center owner, same-origin | Campaign title/body, supported category/priority, mandatory inbox plus optional one-time pop-up and push channels, internal action path, expiry | Creates a draft; messages remain available in the app updates menu, optional pop-ups are shown once per campaign on a device, and links are restricted to internal Rollcall paths | `400` invalid schema; `401`/`403`/`503` authorization failure |
| `POST /admin/campaigns/{id}/publish` | Command Center owner, same-origin | `{ "confirmAllInstallations": true }` | Publishes persistent inbox content; optional FCM topic submission is tracked separately and an `accepted` state only means FCM accepted the request | `400` missing confirmation; `404` unknown campaign; `409` invalid state |
| `POST /admin/campaigns/{id}/revoke` | Command Center owner, same-origin | `{ "confirmRevoke": true }` | Removes the campaign from future inbox reads; cannot recall a system notification already displayed | `400` missing confirmation; `409` invalid state |
| `POST /admin/campaigns/{id}/retry-push` | Command Center owner, same-origin | `{}` | Retries a failed FCM submission for a published push campaign | `409` invalid state; `503` provider rejection |
| `GET /announcements` | Verified Firebase bearer or trusted-phone web session cookie | — | Up to 50 unexpired published announcements with per-account read state | `401` missing/invalid/unverified identity |
| `POST /announcements/{id}/receipt` | Verified Firebase bearer or trusted-phone web session cookie, same-origin | `{ "state": "read" | "dismissed" }` | Upserts this account's receipt for an active campaign | `400` invalid state; `404` unknown/expired campaign |
| `POST /device/register` | Android / Firebase bearer | `deviceId` (12–100 letters, digits, `_`, or `-`), `fcmToken` (up to 4096 chars), optional `emailCode` (six digits) | `{ "ok": true }` | `400` invalid device; `403` `email_confirmation_required`, `email_code_invalid`, or `email_code_expired` |
| `POST /device/replacement-code` | Android / verified Firebase bearer | empty object | `{ "ok": true, "expiresIn": 600 }` | `400` no email; `409` no trusted device; `429` `email_rate_limited`; `503` `email_delivery_failed` |
| `POST /auth/qr/start` | Browser / same-origin | empty object | `{ "ok": true, "challengeId": string, "secret": string, "scanPayload": string, "browserLabel": string, "expiresAt": epoch seconds }` | `429` too many QR requests |
| `POST /auth/qr/preview` | Android / verified Firebase bearer and trusted device | `deviceId`, `challengeId`, `secret` | `{ "browserLabel": string, "expiresAt": epoch seconds }` | `403` untrusted device; `404` expired/invalid QR |
| `POST /auth/qr/approve` or `/auth/qr/deny` | Android / verified Firebase bearer and trusted device | `deviceId`, `challengeId`, `secret` | `{ "ok": true }` | `403` untrusted device; `404` expired/invalid QR; `409` already handled |
| `POST /auth/qr/poll` | Browser / same-origin | `challengeId`, `secret` | `{ "state": "pending" }`, `{ "state": "denied" }`, or `{ "state": "signed_in" }` and sets `rc_session` once approved | `404` expired/invalid QR; `409` already consumed |
| `POST /auth/challenge` | Browser / verified Firebase bearer | empty object | `{ "ok": true, "challengeId": string, "expiresAt": epoch seconds }` | `409` no trusted app; `429` too many requests; `503` push service unavailable |
| `POST /auth/signup/start` | Android / same-origin or HTTPS client | `{ "email": string }` | Sends a 6-digit verification email; no Firebase user is created yet; `{ "ok": true, "expiresIn": 600 }` | `400` invalid email; `429` rate limited; `503` email delivery unavailable |
| `POST /auth/signup/complete` | Android / same-origin or HTTPS client | `{ "email": string, "code": string, "password": string }` | Verifies the emailed code, then creates a Firebase email/password user with `emailVerified: true`; password is used only for the Firebase Auth REST request and is never stored in D1 | `400` invalid input; `401` wrong/expired code; `409` code already used or account already exists; `503` Firebase setup unavailable |
| `POST /auth/pending` | Android / verified Firebase bearer | `deviceId`, `challengeId` | `{ "code": six digits, "expiresAt": epoch seconds }` | `403` untrusted device; `404` expired challenge |
| `POST /auth/verify` | Browser / same-origin | `challengeId`, `code` (six digits) | `{ "ok": true }` and sets `rc_session` | `403` origin mismatch; `401` expired, exhausted, invalid, or consumed code |
| `GET /auth/session` | Browser / `rc_session` | — | `{ "signedIn": true, "updatedAt": epoch seconds }` | `401` `{ "signedIn": false }` |
| `POST /auth/logout` | Browser / same-origin, cookie optional | empty object | `{ "ok": true }`, clears cookie | `200` even when already signed out |
| `PUT /snapshot` | Android / verified Firebase bearer and trusted `deviceId` | `deviceId`, `snapshot` | `{ "ok": true, "updatedAt": epoch seconds }` | `403` device mismatch; `413` oversized serialized snapshot; `400` invalid snapshot |
| `GET /snapshot` | Browser / `rc_session` | — | `{ "snapshot": object, "updatedAt": epoch seconds }` | `401` not signed in; `404` no uploaded snapshot |
| `POST /account/delete` | Android / verified Firebase bearer | empty object | `{ "ok": true }`; deletes Rollcall D1 rows for the UID | `401` missing/invalid/unverified bearer token |

Unknown method/path combinations return `404`. `OPTIONS` returns `204` with `Allow: GET, POST, PUT, OPTIONS`.

Firebase Authentication account deletion is performed by the Android client separately; `/account/delete` only purges Rollcall-owned D1 rows.

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

- Browser QR sign-in: the QR challenge expires after 120 seconds, can be approved only by the account's trusted Android device, and creates one browser session. QR-start requests are limited to ten per hashed IP per 15 minutes; hashes and stale challenge rows are pruned after one day. The QR secret is never stored directly in D1.
- Browser email/password sign-in fallback: a challenge code expires after 300 seconds and allows five verification attempts. Up to five challenge sends per account are accepted per 15 minutes, including requests whose challenge was consumed; send timestamps are retained for one day. Delivery is through the trusted app’s FCM token; the code is encrypted in D1 and stored as an HMAC for verification.
- New email/password signup: a six-digit Resend code expires after 600 seconds, allows five attempts, and can be requested at most once per minute and five times per email per 15 minutes, with a ten-send per IP limit per 15 minutes. D1 stores only an HMAC of the code and prunes send timestamps after one day. The Worker creates the Firebase Auth account and marks the address verified only after the code is accepted; it does not persist the password.
- Phone replacement: an email code expires after 600 seconds, up to five attempts, at most one send per minute, and five sends per account per 15 minutes. D1 stores only an HMAC of the code. Successful replacement invalidates existing web sessions.
- `device/register` request bodies are capped at 12,000 bytes; auth pending, verify, and QR requests at 3,000 bytes (QR start at 1,000 bytes). Other JSON requests use the 1,900,000-byte cap.
- Firebase ID tokens are limited to 8192 characters and must match the configured project, issuer, signature, and expiry.

## D1 migrations and release safety

Apply migrations in order only after reviewing them against a current D1 backup. Migration `0002_email_device_replacement.sql` drops the old `recovery_codes` table after introducing email replacement codes; applying it is destructive to any still-needed recovery-code rows. Migration `0003_expire_web_sessions.sql` sets existing sessions to expire 30 days after their original creation, so sessions older than 30 days will require sign-in after rollout. Migration `0004_limit_web_challenge_sends.sql` adds durable challenge-send counters. Migration `0005_web_qr_sign_in.sql` adds short-lived QR sign-in challenges and hashed-IP request limits. Do not run remote migrations as part of code verification. Production migration timing, backup/restore, email secrets, and deployed Worker version are operator-controlled.

Migration `0007_command_center.sql` adds Command Center audit, broadcast, and per-account receipt tables. Set `COMMAND_CENTER_ADMIN_UIDS` or `COMMAND_CENTER_ADMIN_EMAILS` as a Pages environment variable to a comma-separated allowlist of Firebase Auth UIDs or verified owner emails before enabling console access. Broadcast actions are owner-only, same-origin protected, validated, audited, and explicitly confirmed before publishing. No arbitrary code or external links are accepted. Push status reports provider acceptance, not phone delivery. Migration `0008_verified_email_signup.sql` stores only pending signup email-code hashes and rate-limit timestamps; Firebase Auth records are created only after code verification. Set `FIREBASE_WEB_API_KEY` in the Pages Worker environment for server-side signup completion.

Migration `0009_release_factory.sql` adds an isolated Release Factory state table. Admin releases require a verified Firebase bearer token on the existing admin allowlist. `GET/POST /api/admin/releases` list and create stable release drafts; `POST /api/admin/releases/:id/apk` accepts a 1–20 MiB binary APK; `POST /:id/validate`, `/:id/approval-code`, `/:id/publish`, and `/:id/cancel` move the draft through validation, emailed one-time approval, publication, or cancellation. The APK remains private in the `RELEASE_STAGING` KV binding until GitHub publication. The GitHub Actions worker uses `RELEASE_FACTORY_PULL_KEY` in its request header for `GET /api/release-worker/next`, `GET /api/release-worker/artifact/:id`, and `POST /api/release-worker/:id/validated|published`. The worker verifies package identity, signature, version, SDK range, archive integrity, and SHA-256 before publication. Only a GitHub release asset with the validated size and digest can complete publication. A published release carries `rollcall-version-code`, `rollcall-version-name`, and `rollcall-update-policy` metadata in its body for the native updater. Publishing a stable public GitHub release makes it discoverable by every existing Android installation; the policy controls reminders, not whether the update can be ignored. The FCM push is an update hint and may fail independently of GitHub publication.

## Contract verification

- Web: `cd web && npm test` (Worker route behavior and request limits).
- Android: `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` (model and payload integration compile; instrumentation is not run by this command).
- Device/provider and production Cloudflare/Firebase behavior require separate controlled verification; local tests do not call production services.
