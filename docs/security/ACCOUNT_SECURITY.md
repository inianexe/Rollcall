# Account security

RollCall keeps Firebase identity, local academic data, and the optional cloud snapshot in separate trust boundaries.

## Identity and provider linking

- Firebase Authentication is the only RollCall identity provider. Google uses Android Credential Manager and Firebase's Google credential; email uses Firebase email-link; legacy password accounts retain email/password sign-in and reset.
- Provider linking must be performed while authenticated to the account whose UID and cloud data should remain. The app stops on `credential-already-in-use` and related conflicts. Never merge accounts based on email equality.
- MyCamu/KCT passwords and portal cookies are not RollCall credentials and must not be uploaded as part of authentication or cloud sync.

## Cloud ownership and device trust

- Sync starts only after explicit user consent and records the active Firebase UID as the local cloud owner. Enqueue and upload both verify that the active Firebase UID still matches that owner.
- The Worker validates the Firebase ID token and scopes snapshots, app registrations, challenges, and sessions by UID.
- Verified app sign-in registers an installation for approval pushes, without enabling snapshot upload. Web Firebase sign-in alone is insufficient to read snapshots; a code delivered to the user's signed-in Rollcall apps must be confirmed before the Worker issues a browser session.
- Browser sessions use `Secure`, `HttpOnly`, `SameSite=Strict` cookies and a 30-day server-side expiry. Logout and trusted-device replacement revoke session rows.

## Sensitive operations and deletion

- Cloud account deletion requires a recent Firebase `auth_time` (at most five minutes old) at the Worker. Android first reauthenticates with the linked Google provider or an email action link, then refreshes its ID token and requests D1 deletion before deleting the Firebase user.
- Account deletion is a multi-service operation, not a transaction. If Firebase deletion fails after Worker data deletion, the Firebase identity can remain while its RollCall cloud rows are already gone. The app reports failure and allows the owner to contact support; retrying removes any remaining identity.
- Deletion does not clear the Android local snapshot or institutional portal account. Users must use the separate local-data control if they also want on-device records removed.

## Configuration and known limits

- Google sign-in needs the Firebase Android OAuth client configuration and the correct SHA fingerprints. The checked-in `google-services.json` currently has an empty `oauth_client` list, so Google sign-in cannot succeed in this build until the owner downloads an updated config.
- Email-link sign-in and reauthentication need Firebase Email/Password plus Email Link enabled, the Firebase Hosting action-link domain, and Android App Links configuration. These settings have not been changed or verified in production.
- App approval pushes target all registered installations for the Firebase UID. An app unregisters on sign-out; older app versions do not understand phone-link push codes and should be upgraded before linking another phone.
- Local unit/API tests do not verify Firebase Console settings, Google Play Services behavior, production D1, or physical-device push delivery.
