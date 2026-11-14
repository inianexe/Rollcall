# Account security

RollCall separates Firebase identity, academic records stored on Android, phone approval keys, browser sessions, and the optional cloud snapshot.

## Trust boundaries

- Firebase Authentication establishes the account UID. The Worker verifies ID-token signature, issuer, project audience, expiry, and verified email where required. Provider linking keeps the active UID; email equality never triggers an account merge.
- An ordinary signed-in app may receive account notifications. Its FCM token and client-generated installation ID do not establish approval-device trust.
- A trusted approval device has an RSA-2048 private key generated inside Android Keystore. The private key is non-exportable. The Worker stores only the public key and accepts approve/deny proofs signed over a one-time challenge bound to UID, device ID, browser challenge, action, and number match.
- Web-login and phone-replacement codes are sent only to enrolled approval-device FCM tokens. `/auth/pending` has been removed; knowing a device ID cannot retrieve a code. FCM tokens are delivery addresses, not authorization proof.
- Cloud sync remains separately opt-in. Upload work checks the signed-in UID against the locally chosen sync owner, and the Worker enforces a D1 sync-enabled gate. Pausing uploads preserves the current cloud copy; deleting the cloud copy is a separate confirmed action requiring recent Firebase reauthentication and atomically disables future uploads.

## Browser sessions

- Browser sessions use random `Secure`, `HttpOnly`, `SameSite=Strict` cookies and 30-day D1 expiry. D1 stores a keyed hash of the cookie, a random management ID, user-agent text, and last-use time; management responses never return cookie hashes.
- Android can list active browser sessions, revoke one session, revoke all sessions, or use the browser's own sign-out. Revocation requires a verified Firebase token with `auth_time` no more than five minutes old.
- Firebase token signature validation does not, by itself, discover that a Firebase refresh token was revoked after a D1 browser session was issued. D1 session revocation and 30-day expiry remain the controls in this implementation. Automatic Firebase revocation/disabled-account propagation needs a supported Admin lookup or revocation signal and is not claimed here.

## Recovery and residual risks

- Adding a second approval phone requires a one-time code sent to an already enrolled phone. A legacy trusted phone migrated without a key receives a short-lived setup nonce by FCM and must sign it from that phone before its key is accepted.
- If all enrolled approval phones are lost, there is **no self-service recovery flow yet**. Do not bypass approval using only a new device ID, an FCM token, or a fresh Firebase login. Recovery needs an independently verified path with a delay and notification/cancellation path; implement it before describing account recovery as complete.
- Keystore private keys are not hardware-attested in this version. Android Keystore protects key export, but a compromised device could still ask the OS to sign.
- Firebase App Check is not configured for the Worker. It can reduce API impersonation but would not replace Firebase identity or signed approval proof.
- Full six-digit fallback codes are delivered in FCM data and shown only inside the authenticated RollCall app. The lock-screen notification displays only the match number; Android private app storage holds the pending code briefly until the user opens it.

## Destructive operations

- Deleting the cloud snapshot requires recent Firebase reauthentication and permanently removes the remote snapshot. Android pauses future upload on this phone and preserves local academic records.
- Pausing cloud sync stops future uploads but retains the remote snapshot.
- Account deletion requires recent reauthentication, deletes UID-owned RollCall D1 rows, then deletes the Firebase identity. This cross-service sequence is not transactional. If Firebase deletion fails after D1 deletion, the identity may remain without its RollCall cloud data.
- None of these operations clears local attendance or MyCamu/KCT credentials.

## Operational verification limits

API tests use synthetic Firebase signing keys, fake D1, and intercepted FCM. They verify ownership, proof, expiry, attempt, session revocation, and deletion rules; they do not verify Firebase Console settings, production D1 migration, real FCM delivery, Android hardware-backed key properties, or Firebase revocation propagation.
