# RollCall authentication

Status: Java Android and web implementation on `feature/firebase-auth-v2`. Authentication changes and D1 migrations in this branch are not deployed or applied remotely.

## Identity and data modes

- Firebase Authentication is the RollCall identity provider. Android uses native Java Views, Firebase Auth, and Credential Manager for Google; email-link and existing password accounts remain supported. The browser uses Firebase JavaScript Auth.
- The phone remains useful without a RollCall account. MyCamu/KCT identity and portal credentials are separate. Cloud sync requires explicit opt-in and remains tied to the signed-in Firebase UID.
- Turning sync off disables uploads on the Worker and retains the cloud snapshot. “Delete cloud copy” disables uploads and removes the snapshot in the same D1 batch after recent reauthentication. A stale in-flight upload cannot recreate a deleted snapshot; only explicit re-enrollment enables sync again. Local academic records are preserved.

## Web sign-in flow

**Before:** Firebase sign-in, receive a six-digit code on a signed-in app, find it, then enter it in the browser.

**After:** Firebase sign-in, view a two-digit match on the browser, approve or deny the matching request in RollCall on an enrolled phone, then the browser polls the same one-time challenge and receives its session automatically. The six-digit entry remains an explicit fallback. Requests expire after five minutes; sends and code attempts are rate-limited; challenge decisions and session creation are single-use.

The phone signs a server-bound action string using its Android Keystore RSA key. Merely signing into Firebase, possessing an FCM token, or claiming an installation ID does not grant approval. The old `/auth/pending` code-retrieval endpoint is removed.

## Enrollment and migration

1. Apply D1 migrations `0001` through `0009` in order in a reviewed staging environment. `0006` copies the current trusted phone into `approval_devices` with no key; this preserves delivery while requiring that phone to approve its own first key enrollment. `0007` adds opaque session-management IDs and browser activity fields, backfilling existing sessions. `0008` preserves current sync for existing trusted devices while adding the server-side pause/delete gate. `0009` clears obsolete recovery-code hashes that have no current consumer.
2. Ship the Android version containing `ApprovalKeyStore` and the nonce/signature handling. Users open Account → Set up phone sign-in approvals; legacy phones confirm the FCM request on that same phone.
3. Verify one-phone enrollment, a second-device transfer, browser approve/deny, manual-code fallback, expiry, replay, and session revoke in staging with synthetic accounts.
4. Only then deploy the Worker and migration together. Do not apply remote migrations as part of local verification.

Rollback should revert the Worker before changing table shape. Keep the additive tables/columns until the rollback window closes; do not drop old code or rows during rollout.

## Verification evidence

- `cd web && npm test`: 14 Worker tests pass, including untrusted-code retrieval denial, signed phone approval, one-use enrollment nonce, foreign installation-ID rejection, recent-auth snapshot deletion, session listing/revocation, server-side sync pause, and challenge expiry/attempt limits.
- All nine D1 migrations execute in order against a temporary SQLite database; the new session-management and sync-gate columns are present.
- `cd android && ANDROID_HOME=/home/inian/Android/Sdk ./gradlew testDebugUnitTest assembleRelease`: passed. The physical phone currently runs the production API build; this feature branch has not been installed or exercised on-device because its Worker and migrations are intentionally undeployed.
- Firebase live sign-in, real FCM, browser approval on a real account, and physical Keystore behavior still require staging configuration and controlled verification.

## Recovery boundary

There is no safe self-service path when a user loses every enrolled approval phone. This branch deliberately does not let a new Firebase-authenticated app approve itself. A separate recovery mechanism needs an independent verification channel, a delay, and a way for an existing device to cancel the request; until that is designed and verified, recovery is incomplete.

See [account security](../security/ACCOUNT_SECURITY.md), [Firebase setup](../development/FIREBASE_SETUP.md), and the [API contract](API_CONTRACT.md).
