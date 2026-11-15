---
name: rollcall-regression-guardian
description: Protect RollCall attendance calculations, provider sessions, snapshots, account boundaries, and Android system integrations during changes.
---

# RollCall Regression Guardian

Apply when a change can affect attendance, marks, authentication, synchronization, persistence, reminders, notifications, widgets, or updates.

1. Identify the data owner and all callers before changing a model or integration. Preserve `Attendance` formulas, MyCamu/KCT session behavior, Firebase identity, snapshot authorization, deletion/revocation semantics, and update-signing checks.
2. Add or update a focused regression test for each changed trust boundary, parser, migration, or calculation. Use synthetic fixtures; never place real credentials, cookies, student records, or personal screenshots in tests or commits.
3. Validate inputs and schema versions at boundaries. Keep cached valid data on refresh failure, mark it stale, and never present mock data as live.
4. Check old-client compatibility and account isolation before changing cloud payloads or API storage. Do not introduce a backend or dependency without a demonstrated need.
5. Run targeted tests, the Android unit suite/build, and web tests when relevant. State external services and device behavior that were not exercised.
6. Device verification must not overwrite the installed production package. Do not use an emulator for RollCall device checks; use the user's physical device only when authorized and avoid changing account or portal data.
