---
name: rollcall-migration-runner
description: Guide incremental Rollcall Java View to Jetpack Compose screen and component migrations while preserving existing behavior. Use when migrating Rollcall presentation or its Java-to-Compose bridge.
---

# Rollcall migration runner

Read the relevant architecture and migration documentation, inspect the current branch and working tree, then limit changes to the requested screen.

For a migration:

1. Inventory visible states, inputs, outputs, interactions, navigation, accessibility, and theme behavior.
2. Trace which Java code owns data, calculations, persistence, and events. Keep those owners authoritative.
3. Characterize current behavior with deterministic tests before replacing presentation.
4. Pass immutable, already-computed UI state into Compose; bridge events back to existing Java owners.
5. Migrate one screen or component at a time. Keep the legacy implementation until parity is verified.
6. Verify empty/loading/error/populated states, light/dark appearance, accessibility, scrolling, and recreation as applicable.
7. Run targeted and relevant regression checks; update the migration tracker with verified results and limits.

Do not alter attendance formulas, MyCamu/KCT authentication, stored formats, or production records as incidental migration work. Never claim a screen is migrated until its existing behavior has evidence of parity. Commit only when the user or task authorizes it.
