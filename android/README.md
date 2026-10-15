# Rollcall for Android

This is the first native Android port of the extension's attendance and Today views. It shows a movable window over other apps, reads text exposed by MyCamu's Android accessibility tree only when you tap **Scan** or **Read day**, and calculates the same 75% margin/recovery guidance. Today entries match attendance by exact course code; matches older than 15 minutes are marked stale. It does not sign in, send data to a server, or change MyCamu attendance. If MyCamu draws content without accessible text, enter attendance counts in the Rollcall app and scan again.

The timetable reader needs a real MyCamu screen to verify because native-app layouts expose different information than the website.

## Android project layout

- `settings.gradle`, `build.gradle`, `gradle.properties`: Gradle project and Android plugin setup.
- `app/build.gradle`: application ID, SDK levels, and app version.
- `app/src/main/AndroidManifest.xml`: app launcher, overlay permission, and Android services.
- `app/src/main/java/.../MainActivity.java`: permission links, launching MyCamu, and manual attendance entry.
- `app/src/main/java/.../OverlayService.java`: movable overlay and Scan/Hide controls.
- `app/src/main/java/.../MyCamuAccessibilityService.java`: reads visible accessibility text after a user scan.
- `app/src/main/java/.../Attendance.java`: validates rows and computes percent, safe absences, and recovery periods.
- `app/src/main/java/.../Timetable.java`: reads visible dates, course codes, and class times, then identifies NOW/NEXT.
- `app/src/main/res/xml/accessibility_service.xml`: declares that the service can read visible UI text.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: pinned Gradle wrapper for repeatable builds.

## Build and run

Install Android Studio or the Android command line tools, including SDK Platform 35 and Build Tools. The Gradle wrapper is included. From the repository root:

```sh
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.iniexe.rollcall/.MainActivity
```

In Rollcall, allow **Display over other apps**, enable **Rollcall** in Android Accessibility settings, then start the overlay. Open MyCamu and navigate to Attendance → Subject-wise. Tap **Scan** on the overlay. The accessibility permission is powerful: Android gives the service access to on-screen text. The service only reads text when Scan is tapped; it does not record keystrokes or press controls in MyCamu.

The Android CLI created and started the `medium_phone` API 36 profile for local verification. MyCamu itself is not installed or signed in on the emulator, so automatic reading of a live attendance screen still needs verification with your institution's account.
