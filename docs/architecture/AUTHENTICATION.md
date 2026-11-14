# RollCall authentication

Status: implementation on `feature/firebase-auth-v2`; Firebase Authentication and Google OAuth branding were configured on 2026-11-14. Cloudflare production was not deployed or migrated.

## Identity and account modes

- Firebase Authentication is the RollCall identity provider. Android uses Java, native Views, Firebase Auth, and Android Credential Manager for Google ID tokens. The browser uses the Firebase JavaScript SDK.
- Sign-in methods are Google, Firebase email-link, and the existing email/password provider. Email/password remains available for existing accounts and password reset. Provider linking uses Firebase's link APIs and retains the current Firebase UID; credential conflicts stop without automatic account merges.
- Local attendance remains usable without a RollCall account. Cloud sync is opt-in and bound to the current Firebase UID. MyCamu/KCT credentials and portal sessions remain device-local and independent.
- Web dashboard access still requires verified Firebase identity plus approval from the registered Android device. Google or email-link authentication does not bypass that second step.

## Lifecycle

1. A user may stay local or authenticate using a configured provider. The app stores the last authenticated UID for display only; it does not make that UID the sync owner.
2. The user explicitly enables cloud sync. Android registers the trusted device and records `cloud_sync_uid`. Snapshot jobs require the signed-in UID and stored sync owner to match, and the worker checks again immediately before upload.
3. Browser sign-in obtains a Firebase ID token, requests a short-lived challenge, and waits for the code retrieved inside the trusted Android app. The Worker then creates a random, HttpOnly, Secure, SameSite=Strict session; D1 stores only a keyed hash.
4. Sign-out removes browser session state or Android pending challenge state. It does not clear local academic data or delete the cloud snapshot.
5. Account deletion requires recent Firebase reauthentication. Android then asks the Worker to delete UID-owned D1 data and deletes the Firebase identity. Local academic records remain on the phone.

## Link handling

Google and email-link credentials are linked to the signed-in Firebase user. The client verifies that the UID remains the same. If a credential is already attached to a different Firebase UID, the operation fails with a recovery message; RollCall never guesses that matching email addresses authorize a merge.

Android email links use Firebase Hosting's email action link and the `/__/auth/links` App Link. The email address stays in app-private preferences, not in the continuation URL. If a link opens on another device, the user must enter the same email. Email-link reauthentication is also used before deleting email-linked accounts.

## Boundaries and recovery

- Firebase validates identities; the Cloudflare Worker validates Firebase ID-token signature, project audience, issuer, expiry and verified email at the API boundary.
- D1 rows are scoped by Firebase UID. Web sessions are separately stored and revoked by logout, device replacement, and account deletion.
- Email/password reset remains available. Google, email-link, and authorized web-domain configuration is enabled in Firebase; authentication submissions and link delivery still require controlled live verification.
- The email-link mode currently shares the existing Firebase Auth user and D1 schema; it introduces no new identity table or database migration.

See [Firebase setup](../development/FIREBASE_SETUP.md), [account security](../security/ACCOUNT_SECURITY.md), and the [API contract](API_CONTRACT.md) for operational details.
