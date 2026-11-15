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

Create or verify the optional Rollcall account from **Settings → Rollcall Web Account** after connecting MyCamu. MyCamu passwords are never used for Rollcall account sign-in. The phone uploads a snapshot over HTTPS; the website authenticates with Firebase email/password and then requires a one-time code delivered to the trusted Rollcall app. The code is visible only inside the app, not in the push notification. When linking a replacement phone, Rollcall emails a short-lived confirmation code to the account's verified email address. Linking the new phone invalidates existing web sessions.

The web dashboard is read-only for phone-synced data. It mirrors attendance, timetable, class marks, leave/OD, KCT completed-course grades, GPA inputs, and upcoming exams. The browser refreshes the snapshot once a minute while open. MyCamu can sync in the background through Android WorkManager when its session is still valid. KCT marks and exams refresh after the user opens the KCT portal in the app; they are not independently scraped by this website.

Without a configured Rollcall account, local mode keeps its existing `localStorage` data. Export a backup before changing browsers. Account mode does not overwrite that local browser copy; signing out returns to local mode.

## Command Center

The private developer console is available at `/admin/`. It accepts Google sign-in verified by Firebase, or the website's existing trusted-phone QR session when its UID is on the server-side owner allowlist. A normal student Rollcall account does not grant access. The console fails closed until at least one owner identity is configured. Authorized owners can view installation/campaign counts, save drafts, publish an inbox announcement to all eligible accounts, optionally request FCM topic delivery, retry failed push submission, revoke future inbox availability, and review an audit trail. Publish and revoke require explicit confirmation. Push “accepted” means FCM accepted the request, not that a phone displayed it.

For local or production setup, set the Pages environment variable `COMMAND_CENTER_ADMIN_UIDS` to Firebase Auth UIDs or `COMMAND_CENTER_ADMIN_EMAILS` to verified owner email addresses. Keep either allowlist server-side. Apply migrations `0007_command_center.sql` through `0009_release_factory.sql` before using their respective console sections. Broadcast operations remain available. The Release Factory stages one signed stable APK at a time in private KV (20 MiB maximum), validates it in GitHub Actions, sends an emailed approval code, and publishes a checksum-verified GitHub release. Configure a `RELEASE_STAGING` KV binding and the same random `RELEASE_FACTORY_PULL_KEY` secret in Pages and GitHub Actions. The workflow `.github/workflows/devos-release-factory.yml` must be on the default branch; it polls every five minutes or can be dispatched manually. This workflow does not build or sign APKs, and the public release can reach every existing installation. Remote native features, hosted web modules, staged audience rollouts, experiments, monitoring, and rollback are separate capabilities and are not provided by this release path.

## Configure deployment (free tiers)

The repository is prepared for Firebase Authentication/Cloud Messaging and Cloudflare Pages Workers + D1, but it is not live until an owner configures these external services. No paid backend or custom domain is required; free-tier quotas apply.

1. In Firebase project `rollcall-ab5d1`, register a Web app, enable Email/Password authentication, and set the deployed `pages.dev` hostname as an authorized domain. Copy the web app's public config into `firebase-config.js`. Keep service-account credentials out of this file.
2. The Pages project and D1 database are named `rollcall-campus-companion`; their deployed URL is `https://rollcall-campus-companion.pages.dev/`.
3. Review every pending migration against a current D1 backup before applying it. Migration `0002_email_device_replacement.sql` drops the legacy `recovery_codes` table; confirm those rows are no longer needed and that backup restoration is available before applying. Migration `0003_expire_web_sessions.sql` expires sessions based on their original creation time; affected users may need to sign in again. Migration `0004_limit_web_challenge_sends.sql` adds the durable sign-in send ledger. Never use remote migration commands as a local test.
4. Set Pages secrets `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY`, `FIREBASE_WEB_API_KEY`, `OTP_ENCRYPTION_KEY`, `SESSION_HASH_KEY`, `RESEND_API_KEY`, and `ROLLCALL_EMAIL_FROM`. `FIREBASE_WEB_API_KEY` is the project's public Firebase web API key, used by the Worker only after a new signup email code is verified. The Firebase service account needs permission to send Firebase Cloud Messaging messages. Verify the sender domain in Resend before using it as `ROLLCALL_EMAIL_FROM`. Generate independent random 32-byte values for the OTP and session secrets. Never commit `.dev.vars`, email API keys, or service-account keys.
5. After an owner approves a release, deploy with `npx wrangler pages deploy . --project-name rollcall-campus-companion`. Build Android with `-ProllcallApiBaseUrl=https://rollcall-campus-companion.pages.dev/api`. This consolidation performs no production deploy.
6. Build and install the Android app with the same Firebase project and verify account creation, email verification, first-device registration, emailed replacement-phone confirmation, app-delivered browser sign-in codes, snapshot display, and deletion flows before sharing the site.

Use Firebase's Spark plan and Cloudflare's free tier only; do not enable paid billing for this deployment. Their quotas and product terms can change. Check current limits before public launch. Local preview with `python3 -m http.server` only tests the static UI; `/api/*` requires the deployed Worker or a separately configured Wrangler local environment.

## Files

- `index.html`: application shell, navigation, and accessible entry forms.
- `styles.css`: responsive Rollcall Blue system, dark appearance, and mobile navigation.
- `app.js`: local persistence, course guidance, OD duration, schedule, attendance status, What If, and local data workflows.
- `cloud-auth.js`, `functions/_api.js`, `_worker.js`, `migrations/`: trusted-phone OTP, web session, D1 snapshot, and deployment API.
- `admin/`: private developer console shell; all authorization is enforced by `/api/admin/*`.
- `CloudSnapshotPayload.java`, `CloudSnapshotSyncWorker.java`, `CloudAccountClient.java`: Android snapshot and account transport.
- `manifest.webmanifest`, `sw.js`: installable offline shell.
- `assets/`: the current Rollcall brush mark and Inter font used by Android.
