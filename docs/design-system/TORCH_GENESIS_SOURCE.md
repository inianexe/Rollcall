# Torch Genesis — Rollcall visual source of truth

This document governs **every Rollcall-owned surface**. The visual target is the user's `rollcall_torch_prototype(1).html` interactive concept. Do not blend in the old Rollcall layout or introduce an unrelated design system.

## Visual contract

- **Dark canvas** `#121212`; primary text `#F5F5EE`; secondary text `#A8AAA1`.
- **Cards** `#222222` / `#242424`; controls `#2C2C2C`; borders `#525252`.
- **Torch lime** `#CCFF00`: primary actions, selected tabs, hero card, progress and high-value callouts. Always use nearly-black labels on lime.
- **Signature orange** `#FF4400`: logo mark, punctuation, exam and urgency markers; **not** generic success/error.
- **Lilac** `#C8C8FF`: paper ticket backs, secondary editorial panel and projected What-if result. Use black text.
- **Mint** `#9BE4A1`: positive status in dark mode. Red and amber represent meaningful warning states only.
- **Typography** JetBrains Mono: short uppercase labels with tracking, large bold condensed-looking headings, compact legible metadata.
- **Geometry**: page insets around 20–23dp, cards ~15–18dp radii, controls ~9–12dp, navigation active capsule.
- **Density**: display a few clear actionable facts, reserve loud color for the action or metric that matters, keep neutral balance.

## Screens

| Surface | Required mapping |
| --- | --- |
| Home | Lime transmission hero, real event-date ticket (not arbitrary calendar number), live status microcard, lilac companion tile, radar exam, week rhythm, needs-attention list |
| Courses | Individual course cards; code, name, percent pill, **CAN MISS** / **MUST ATTEND**, count and bar, expandable explanation |
| Week | Attendance-first two-way selector, readable compact history entries, genuine schedule and no-classes artwork |
| What if? | Lilac before/after projection, attend/miss segment, native 1–50 class slider, OD policy controls if relevant |
| Marks | Lime GPA banner, orange semester trend, minimalist results and functional KCT refresh |
| Settings | Dark editorial controls, lime primary actions, lilac about panel, safe/destructive actions differentiated |
| Onboarding | Lime brand story, paper/ticket illustration, lilac informational card, clear optional permission and account choices |
| MyCamu and KCT | Restyle **Rollcall-owned connection header only**. Never inject CSS or modify third-party content. |
| Dialogs and updates | Rollcall-themed light/dark dialogs and account sheet; show cancellation, recovery and destructive intent clearly |
| Widgets | Reuse AppPalette light/dark semantic colors; real data only |

## Functional contract

Maintain attendance calculations, MyCamu/KCT WebViews and certificate checks, saved data, approved OD logic, all navigation, notifications, Firebase, app update/download/install, accessible screen-reader labels, and dark/light preference. No prototype sample attendance/CGPA may enter production.

The Compose-enabled branch remains the base: `MainActivity` is a `ComponentActivity`; Home identity and upcoming exam use Compose bridge components; remaining native views may be migrated separately without changing behavior.

## Motion / performance

Inspired by React Bits **interaction principles**, not a React runtime. Native Compose/Android animations should be small (roughly 120–300ms), springy when pressed, cancelled on detach, and disabled when system animators are disabled. Avoid looping animation on a static card, jank on sync, and resource-heavy 3D scenes.

## Release checks

1. Android CI must compile and pass unit tests.
2. Compare Home, Courses, Week, What if, Marks, onboarding, Settings, cloud actions and update flow to the visual reference in dark mode.
3. Review light mode with contrast-safe semantic variants; never invert the dark palette wholesale.
4. Verify across small and large Android displays and increased font sizes; icons and text must remain visible on accent surfaces.
5. Test live sync, empty data, expired sessions, no exams, disconnected account, approved OD, target changes and back navigation.
6. Do not merge solely on source/build checks; preview on an Android device or emulator.

## What not to do

Do not preserve old Rollcall visual design just because it existed. Do not use a generic Material dashboard theme or invent fake live course data. Do not replace Java data logic during UI work. Do not remove the Compose/Lottie architecture introduced before this redesign.
