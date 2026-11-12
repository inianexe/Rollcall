# Rollcall for Android

Rollcall opens MyCamu's attendance page in an in-app WebView, requests the same-origin attendance endpoint, and displays the returned course counts on a native dashboard. The same WebView stays alive behind the dashboard and refreshes once a minute while Rollcall is in the foreground. Attendance is saved on-device so the dashboard remains useful after the app restarts.

The request reads the selected program from the MyCamu page's `ngStorage-progressionData` and posts to `/api/Attendance/getDtaForStupage`. The response is validated before Rollcall saves course name, course code, attended periods, and total periods. No credentials or attendance rows are sent to a Rollcall server. The endpoint is undocumented and can change.

## Android project layout

- `app/src/main/java/.../MainActivity.java`: embedded MyCamu WebView, same-origin attendance fetch, and one-minute foreground refresh.
- `app/src/main/java/.../DashboardView.java`: native Java View dashboard styled after `UI IDEAS/rollcall-dashboard.html`, with overview, searchable courses, live attendance ring, target editor, and interactive attendance planner. Short ease transitions use alpha, scale, and translation animations.
- `app/src/main/java/.../LiveSnapshotStore.java`: validates the in-process snapshot handoff and persists attendance rows locally.
- `app/src/main/java/.../Attendance.java`: validates counts and calculates percentages and recovery guidance for the selected target.
- `app/src/main/AndroidManifest.xml`: launcher, WebView activity, and internet permission. No overlay or accessibility service is declared.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: pinned Gradle wrapper for repeatable builds.

## Build and install

Install Android Studio or the Android command line tools, including SDK Platform 35 and Build Tools. From the repository root:

```sh
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.iniexe.rollcall/.MainActivity
```

## Release signing

The V1 release APK is signed with a dedicated Rollcall release key. The private keystore and password are kept outside this repository at `~/.config/rollcall/`; never commit or send either file. Keep an encrypted offline backup of both. Losing this key prevents signing compatible updates.

Build the signed release APK with:

```sh
cd android
./gradlew :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Release builds fail if the local signing setup is missing, rather than silently producing an unsigned APK. The previous public V1 APK used Android's debug certificate, so installing this release over that preview is not supported; users must uninstall the preview first, which clears its local data.

On a fresh install, sign in to MyCamu in the embedded WebView. Rollcall opens the dashboard after it confirms the account session; later launches go straight to the dashboard. The dashboard shows the student name, institute, and roll/admission identifier when MyCamu provides them. The moon/sun control switches the native dashboard between dark and light themes. **About Rollcall** includes the project summary and links to the developer's GitHub profile and repository.

The dashboard follows the supplied mobile concept with a forest-green animated attendance ring, summary tiles, a focus banner, searchable course rows, and a bottom navigation capsule for Overview, Courses, and Lab. Light and dark palettes, target setting, filters, and course expansion are implemented natively. The attendance planner projects a selected course after a chosen number of missed or attended classes, using attended/total counts from MyCamu; it reports unavailable when counts are missing. The WebView refreshes attendance on entry and every minute while Rollcall stays in the foreground. Use the refresh control for an immediate sync. Set the attendance target from the dashboard (default 75%); it is saved locally.

## Data and refresh behavior

Course rows show attendance percentage and target status; expanded rows show attended/total counts and guidance toward the selected target. The dashboard displays the last successful update time and refreshes as soon as a new snapshot arrives. Student profile details and attendance are saved locally by Rollcall. Polling pauses when Rollcall leaves the foreground. The installed MyCamu app's private storage is not read or shared.
