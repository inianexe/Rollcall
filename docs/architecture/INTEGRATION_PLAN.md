# Integration with the current default branch

## Current state

- `refactor/rollcall-java-torch` is based on `design/torch-genesis-exact-prototype` at `f20a982`.
- PR #13 targets `main`, is open and non-draft, and GitHub currently reports it as conflicting. Its latest Android `verify` check passed on 2026-11-14.
- PR #14 (`codex/rollcall-torch-ui`) merged to `main` on 2026-11-13 and brought the Compose migration into the default branch.
- `main`'s `android-app-checks.yml` filters pull requests to the Compose branch. The Java branch has a separate verification workflow; this consolidation updates it to run Android and Web checks on the Java and consolidation branches.
- The Java Torch app identity is unchanged (`com.iniexe.rollcall`), with versionCode 26 and versionName 1.0.25. The checked-in V1 APK is older; verify release APK identity and signing before publishing.

## Safe integration approach

Keep the Java development branch and PR #13 separate while cleanup and parity work continues. The repository-consolidation branch is stacked on the Java branch and should be reviewed against that parent; do not merge the divergent histories, rebase onto `main`, or force-update either branch as a cleanup step. Retain the other open Torch design PRs and branches until the repository owner chooses how to close or supersede them.

When the Java branch is ready for an integration decision, create a new review branch from the then-current `main`. Compare the Java Torch tree against that exact base and transfer only reviewed Java-compatible changes. Preserve Java attendance, snapshot, provider-session, Firebase, worker, notification, widget and updater behavior; leave Compose modules out of the intended runtime. Resolve release version and signing continuity before considering an installable upgrade. Build and test the integration branch, then review the complete PR diff before any merge.

PR #13 remains the review record for the native Java checkpoint. Its current merge conflict is expected while the default branch contains the Compose merge; it is not a signal to rewrite or discard either history.
