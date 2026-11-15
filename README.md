# RollCall

RollCall is a student attendance companion: a native Android app with an optional browser dashboard.

| Home | Week | Courses | Academics |
|---|---|---|---|
| ![RollCall home screen](release/android-v1/screenshots/01-home.png) | ![Weekly attendance](release/android-v1/screenshots/02-week.png) | ![Course attendance](release/android-v1/screenshots/03-courses.png) | ![Academic records](release/android-v1/screenshots/04-lab.png) |

## What it does

- **Android:** review attendance, weekly trends, courses, timetable, class history, leave records and academic results; connect MyCamu or KCT; set reminders and widgets.
- **Web:** view a local dashboard or sign in to read the latest phone snapshot. Web sync is read-only; attendance calculations and provider access remain on the phone.
- **Phone and web:** Firebase Authentication identifies the account using Google, email-link, or existing email/password credentials; Cloudflare Pages Functions and D1 manage trusted-device verification, browser sessions and account-scoped snapshots. Firebase Cloud Messaging sends web sign-in requests to the trusted phone. Cloud sync is optional and remains separate from MyCamu/KCT sign-in.

The Android app uses Java and native Views with the Torch Genesis design system. The website is lightweight JavaScript served by Cloudflare Pages/Workers; it is not a second Android UI. See [the API contract](docs/architecture/API_CONTRACT.md) and [Torch Genesis design notes](android/TORCH_GENESIS_DESIGN.md).

## Install Android

Download the APK from [GitHub Releases](https://github.com/inianexe/Rollcall/releases). Android may ask you to allow installation from your browser or file manager because the app is distributed outside Google Play. Only install RollCall releases signed with the established RollCall release key.

Current Java source baseline: **version 1.0.25 (versionCode 26)**. The public `v1.0.0` release and checked-in root APK are older and are not an upgrade build for this source. Do not publish the checked-in APK as a current release.

## Development

Requirements: Java 17, Android SDK Platform 35 and Android Build Tools. Install the Android SDK, then run:

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

The debug APK is at `android/app/build/outputs/apk/debug/app-debug.apk`. To run the browser Worker tests and syntax checks:

```sh
cd web
npm test
node --check app.js
node --check cloud-auth.js
node --check _worker.js
node --check functions/_api.js
```

Read [AGENTS.md](AGENTS.md), [CONTRIBUTING.md](CONTRIBUTING.md), and [development setup](docs/development/README.md) before changing code. Never commit Firebase service credentials, keystores, signing passwords, `local.properties`, APKs built for local use, or private student data.

## Repository map

```text
android/                  Java Android app, tests, Gradle wrapper
web/                      Browser UI, Pages Functions, D1 migrations and API tests
docs/architecture/        Feature inventory and Android–Web API contract
docs/development/          Local setup and release workflow
release/android-v1/       Historical screenshots and release notes
.github/workflows/         Android/Web CI and guarded release workflow
```

## Privacy and security

Attendance records are personal student data. The Android app stores its working data locally; optional cloud snapshots are account-scoped and read-only in the browser. Provider sessions are handled on the phone. Review [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md), and do not attach real student records to issues or pull requests.

## Releases and project status

The consolidated Java + Web application is maintained from `main`. Authentication redesign work is developed on the isolated `feature/firebase-auth-v2` branch and is not merged or deployed automatically. See [authentication architecture](docs/architecture/AUTHENTICATION.md) and [Firebase setup](docs/development/FIREBASE_SETUP.md).

No license has been selected for this repository yet. Until the owner chooses one, no open-source reuse license is granted. See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution expectations.
