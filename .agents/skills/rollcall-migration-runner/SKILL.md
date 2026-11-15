---
name: rollcall-migration-runner
description: Plan and deliver a RollCall Android or web screen migration while preserving tested product behavior and the repository's current architecture.
---

# RollCall Migration Runner

Use this for a named screen migration or substantial screen restructuring in RollCall.

1. Read the repository `AGENTS.md`, check branch/worktree status, then read only the architecture, phase, and performance notes for the target screen.
2. Confirm the implementation that is actually checked out. The Java Torch branch uses native Java Views and custom Views; preserve that architecture. Reuse Compose only when the target branch already contains the established Compose screen. Do not add Kotlin or Compose as a side effect of a UI request.
3. Trace the current screen's navigation, state inputs, callbacks, persistence, accessibility labels, and tests before editing. Keep attendance/GPA rules and provider sessions in their existing owners.
4. Write down the current behavior and a short acceptance checklist. Implement one vertical slice at a time, retaining loading, empty, error, offline, and stale-data states.
5. Reuse existing models, palette tokens, widgets, and test fixtures. Add only the smallest missing helper or dependency.
6. Run targeted tests/builds after each slice and the repository's required regression checks before reporting completion. Use a physical device only when authorized; never install over the user's existing app unless explicitly requested.
7. Compare screenshots at the target screen sizes and themes. Document device-only checks that could not be completed; do not claim runtime or performance verification from source inspection alone.
8. Commit a coherent, reviewed change on a feature branch. Do not merge to `main`, deploy, publish, or alter production settings without explicit authorization.
