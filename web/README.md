# Rollcall Web

Responsive Rollcall dashboard. Local browser mode remains available; Rollcall account mode mirrors the phone snapshot as a read-only view.

## Run locally

From the repository root:

```sh
python3 -m http.server 4173 --directory web
```

Open `http://localhost:4173`. The shell can be installed as a PWA when served from HTTPS or localhost.

## Rollcall account and phone sync

The shared request, response, authentication, data-limit, and timestamp contract is in [`../docs/architecture/API_CONTRACT.md`](../docs/architecture/API_CONTRACT.md).

Create or verify the optional Rollcall account from **Settings → Rollcall Web Account** after connecting MyCamu. MyCamu passwords are never used for Rollcall account sign-in. The phone uploads a snapshot over HTTPS; the website authenticates with Firebase and then requires a one-time code delivered to every signed-in Rollcall app. When linking another phone for sync, a separate short-lived code is sent to every other signed-in app. App registration is separate from sync consent; snapshots still upload only after the user enables sync. Linking the new sync phone invalidates existing web sessions.

The web dashboard is read-only for phone-synced data. It mirrors attendance, timetable, class marks, leave/OD, KCT completed-course grades, GPA inputs, and upcoming exams. The browser refreshes the snapshot once a minute while open. MyCamu can sync in the background through Android WorkManager when its session is still valid. KCT marks and exams refresh after the user opens the KCT portal in the app; they are not independently scraped by this website.

Without a configured Rollcall account, local mode keeps its existing `localStorage` data. Export a backup before changing browsers. Account mode does not overwrite that local browser copy; signing out returns to local mode.

## Configure deployment (free tiers)

The repository is prepared for Firebase Authentication/Cloud Messaging and Cloudflare Pages Workers + D1, but it is not live until an owner configures these external services. No paid backend or custom domain is required; free-tier quotas apply.

1. In Firebase project `rollcall-ab5d1`, register a Web app, enable Email/Password authentication, and set the deployed `pages.dev` hostname as an authorized domain. Copy the web app's public config into `firebase-config.js`. Keep service-account credentials out of this file.
2. The Pages project and D1 database are named `rollcall-campus-companion`; their deployed URL is `https://rollcall-campus-companion.pages.dev/`.
3. Review pending migrations before applying them. Migration `0002_email_device_replacement.sql` adds one-time replacement-code storage and retains legacy recovery-code records. Migration `0003_expire_web_sessions.sql` expires sessions based on their original creation time; affected users may need to sign in again. Migration `0004_limit_web_challenge_sends.sql` adds the durable web challenge-send ledger. Migration `0005_trusted_app_devices.sql` adds per-install push registration and migrates the existing trusted device. Never use remote migration commands as a local test.
4. Set Pages secrets `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY`, `OTP_ENCRYPTION_KEY`, and `SESSION_HASH_KEY`. The Firebase service account needs permission to send Firebase Cloud Messaging messages. Generate independent random 32-byte values for the OTP and session secrets. Never commit `.dev.vars` or service-account keys.
5. After an owner approves a release, deploy with `npx wrangler pages deploy . --project-name rollcall-campus-companion`. Build Android with `-ProllcallApiBaseUrl=https://rollcall-campus-companion.pages.dev/api`. This consolidation performs no production deploy.
6. Build and install the Android app with the same Firebase project and verify account creation, email verification, app registration, phone-link codes delivered to the other signed-in apps, browser sign-in codes delivered to all signed-in apps, snapshot display, and deletion flows before sharing the site.

Use Firebase's Spark plan and Cloudflare's free tier only; do not enable paid billing for this deployment. Their quotas and product terms can change. Check current limits before public launch. Local preview with `python3 -m http.server` only tests the static UI; `/api/*` requires the deployed Worker or a separately configured Wrangler local environment.

## Files

- `index.html`: application shell, navigation, and accessible entry forms.
- `styles.css`: responsive Rollcall Blue system, dark appearance, and mobile navigation.
- `app.js`: local persistence, course guidance, OD duration, schedule, attendance status, What If, and local data workflows.
- `cloud-auth.js`, `functions/_api.js`, `_worker.js`, `migrations/`: trusted-phone OTP, web session, D1 snapshot, and deployment API.
- `CloudSnapshotPayload.java`, `CloudSnapshotSyncWorker.java`, `CloudAccountClient.java`: Android snapshot and account transport.
- `manifest.webmanifest`, `sw.js`: installable offline shell.
- `assets/`: the current Rollcall brush mark and Inter font used by Android.
