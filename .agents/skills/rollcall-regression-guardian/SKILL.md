---
name: rollcall-regression-guardian
description: Check Rollcall changes for regressions to attendance, portal access, persistence, notifications, widgets, updates, or release safety. Use when changes can affect application behavior or data.
---

# Rollcall regression guardian

Before editing, identify affected flows, current tests, baseline outputs, and a safe rollback point. After editing, run relevant unit, integration, build, and instrumentation checks available in the environment, then compare outputs to baseline fixtures.

Protect these compatibility boundaries: attendance targets and Can Miss/Must Attend results; MyCamu and KCT sessions and navigation; Firebase/FCM; preferences and snapshot formats; reminders, notifications, workers, and widgets; GitHub updater and APK installation; application ID and release signing.

Do not bypass failing checks, clear production app data, uninstall or overwrite the user's phone installation, or report external integrations as verified unless exercised. Distinguish `VERIFIED`, `BUILD-VERIFIED`, `PARTIALLY VERIFIED`, `NOT TESTED`, and `FAILED`. Record the evidence and untested integrations plainly.
