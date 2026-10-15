# Rollcall v1.0.0 — Android Preview

This preview adds a native Android app with a movable overlay for attendance guidance and a Today timetable view.

## Install

Download `rollcall-android-v1.0.0-debug.apk`, install it on Android, and open Rollcall. Allow **Display over other apps**, enable the Rollcall accessibility service, then start the overlay. In MyCamu, scan Attendance → Subject-wise or select the daily timetable and tap **Read day** in the overlay.

The accessibility service reads visible text only when you tap Scan or Read day, and only while one of the supported MyCamu Android packages is the active app. It does not sign in, change attendance, or send data to a server. If attendance text is not exposed by the app, add course counts manually in Rollcall.

This is a debug-signed preview APK. MyCamu's native UI could not be live-verified against an institution account in the build environment; timetable text layouts and automatic attendance capture may need adaptation.
