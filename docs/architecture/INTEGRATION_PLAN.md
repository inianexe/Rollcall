# Integration with the current default branch

## Current state

- `refactor/rollcall-java-torch` is based on `design/torch-genesis-exact-prototype` at `f20a982`.
- PR #13 targets `main` and is draft. GitHub currently reports it as conflicting.
- PR #14 (`codex/rollcall-torch-ui`) merged to `main` on 2026-11-13 and brought the Compose migration into the default branch.
- The Java Torch app identity is unchanged (`com.iniexe.rollcall`). Its versionCode is 24; the later hybrid app used versionCode 25. Do not publish an upgrade from version 24 or lower a release code.

## Safe integration approach

Keep this development branch and PR #13 separate while Java cleanup and parity work continues. Do not merge the divergent histories, rebase this branch onto `main`, or force-update either branch as a cleanup step.

When the Java branch is ready for an integration decision, create a new review branch from the then-current `main`. Compare the Java Torch tree against that exact base and transfer only reviewed Java-compatible changes. Preserve Java attendance, snapshot, provider-session, Firebase, worker, notification, widget and updater behavior; leave Compose modules out of the intended runtime. Resolve release version and signing continuity before considering an installable upgrade. Build and test the integration branch, then review the complete PR diff before any merge.

PR #13 remains a draft review record for the native Java checkpoint. Its current merge conflict is expected while the default branch contains the Compose merge; it is not a signal to rewrite or discard either history.
