# Torch Genesis / Rollcall Android UI

This branch replaces the Java-built dashboard **presentation** with the UI from the supplied
`rollcall_torch_prototype(1).html`. It is not a visual approximation: the prototype's
HTML structure, JetBrains Mono typography, CSS colours, shapes, navigation and motion
are shipped as local Android assets.

## Architecture in one sentence

**Java owns the student's data and actions. Local HTML/CSS owns how Rollcall looks.**

- `app/src/main/assets/rollcall_torch_native.html` — the original reference design
  with device-safe overrides, a real Leave/OD view, responsive spacing, and light mode.
- `app/src/main/assets/rollcall_torch_runtime.js` — renders **actual data** and invokes
  only the app's own Java actions.
- `DashboardView.java` — a small local WebView wrapper instead of thousands of lines
  of programmatically built cards. It exposes a narrow bridge for navigation,
  attendance sync, appearance, preferences, accounts, privacy, and the target.
- `MainActivity.java` — continues to own MyCamu, KCT academics, account auth,
  attendance calculations, notification permissions, onboarding and data storage.
- `UpdateActivity.java` — native updater with the Torch lime broadcast hero.

## Visual source of truth

Dark: Ink `#121212`, Paper `#F5F5EE`, Lime `#CCFF00`,
Orange `#FF4400`, Lilac `#C8C8FF`, Panels `#222222`/`#2C2C2C`.

Maintain neutral balance; most cards remain dark neutral. Lime is the active/highlight
colour and orange is an editorial and risk accent. Red/green retain semantic meaning.
Light mode is a role-based counterpart, not a colour inversion.

The exact UI CSS is **not** translated into Java `LinearLayout` dimensions. Avoid
parallel implementations. When changing a card or animation, edit the local HTML/CSS,
not a second copy in Java.

## What is live

The Home hero, calendar ticket, next class, KCT upcoming exam, weekly preview, below-target
course, attendance percentages, course `CAN MISS` / `MUST ATTEND`, class history,
schedule, Leave/OD period mapping, What-if projections, weighted CGPA, semester grades,
theme, reminders and account actions all render from the existing Java app state.

The HTML prototype's example numbers are **not** rendered in connected app state.

## Boundaries and safety

The UI uses `androidx.webkit.WebViewAssetLoader` at
`https://appassets.androidplatform.net/assets/`. Its asset WebView blocks
non-asset HTTP requests and external navigation and disables file/content access.
The packaged script alone can call the small `RollcallBridge` interface.

**MyCamu and KCT sign-in WebViews are separate.** Their web pages are not restyled,
and the student password is not exposed to the Torch UI bridge.

Android's OS-controlled installer, app permissions and system chooser screens
cannot legitimately be replaced with custom application views. Rollcall-owned
dialogs use `TorchDialogDark` and `TorchDialogLight`.

## Review before merging

- Test a 320dp-wide phone, a normal 360dp phone and a tablet.
- Verify every tab, settings action, academic record, empty state and sync failure.
- Check light and dark: icons, semantic badges, forms, dialog controls and system bars.
- Test a course with a recoverable deficit, a safe absence budget, a 100% target,
  zero recorded classes and attendance percentages without counts.
- Toggle the target, try What-if (Attend/Miss) and compare projected values.
- Check approved, pending and unmapped Leave/OD records.
- Test Android back, rotation, re-entry via widget and account reauthentication.
- Verify provider sign-in screens have not changed and no new credentials are stored.
- Review on-device animations with Android **Remove animations** enabled.

### Run locally
```bash
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

This is a draft until a human visually verifies the APK. It is not automatically
published or merged into the installed release.
