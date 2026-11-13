# Rollcall Android development notes

- Work on the migration branch/worktree unless the user explicitly selects another branch. Never reset, clean, or overwrite unrelated user changes.
- Before structural Android changes, inspect the current branch, build files, manifest, screen/controller callers and relevant tests. Read the architecture docs under `docs/architecture/`.
- Rollcall is a production Java + Kotlin Android app. Migrate incrementally; keep Java business/integration code where it is stable.
- `Attendance` is authoritative for the current attendance formulas. Compose displays values; do not duplicate these calculations.
- Preserve MyCamu/KCT WebView authentication, cookies, endpoint requests, SharedPreferences names/JSON formats, Firebase, WorkManager, notifications, widgets, and app update checks unless the task explicitly requires a compatible change.
- New Compose screens belong under `ui/screens`; shared Compose components under `ui/components`; theme tokens under `ui/theme`; motion and Lottie adapters under `ui/animation`; Android View interop under `ui/bridge`.
- Put Lottie files directly in `app/src/main/res/raw/` with lowercase resource names. Do not ship unverified or unlicensed animation assets. Animation state must follow actual application state.
- Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` from `android/` after code changes. Run release/instrumentation builds only when signing/device configuration is available. State which checks were skipped.
- Never commit local signing files, passwords, private keys, `local.properties`, or device/session data. Do not publish a release without authorization.
- Create a rollback checkpoint before high-risk migration stages. Keep prior checkpoints immutable.

## Task-specific skills

Load only the skills needed for the current task:

- Java View to Compose migration: `rollcall-migration-runner`, `rollcall-regression-guardian`, and `migrate-xml-views-to-jetpack-compose` for relevant interoperability guidance. RollCall UI is often built programmatically in Java, so apply only the migration guidance that fits the actual source.
- Compose/runtime performance: `rollcall-performance-gate`, `android-profiler`, and `jetpack-compose-performance`.
- Android tests: `testing-setup` and `rollcall-regression-guardian`.
- Insets/system bars: `edge-to-edge`.
- Incoming intents, exported components, PendingIntents: `android-intent-security`.
- Explicit Java-to-Kotlin conversions only: `kotlin-tooling-java-to-kotlin` and `rollcall-regression-guardian`. Never infer a request to convert authoritative Java business logic.
- R8/ProGuard/release size: `r8-analyzer`; pair with the performance gate only for a measured runtime question.
- Lottie work: use the existing `text-to-lottie` skill when authoring or editing animation assets; keep playback tied to application state and use Rollcall's motion system for ordinary Compose transitions.

See `docs/agent/AGENT_SKILLS.md` for installed skills and the persistent benchmark evidence policy.
