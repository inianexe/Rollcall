# RollCall cross-platform visual direction

## Contract

RollCall keeps its Signal identity on a dark, near-black foundation. Signal’s soft chartreuse is the primary accent; the other palettes change only the accent and a slight surface tint. Text, icons, controls, and statuses use semantic foreground/background pairs with contrast checks.

- **Typography:** Android keeps its existing measurement and display faces. The web dashboard uses the bundled Inter face; reserve monospace for measurements and code. Body copy stays sentence case and comfortably sized.
- **Layout:** align content to one left edge; use one clear primary summary followed by compact, task-specific sections. Lists scan vertically. A control stays beside the content it changes.
- **Motion:** keep state changes brief and action-led. Remove continuous decorative animation from status content; respect reduced motion and view lifecycle.
- **Data:** distinguish recorded, projected, stale, empty, and unavailable states in words. Never present missing duration or marks as zero.
- **Cross-platform:** retain Java Views on Android and the existing HTML/CSS/JavaScript web app. Use matching theme IDs and semantic roles, with native interaction patterns on each platform.

## Android visual reference (2026-11-14)

The current mobile direction follows the supplied RollCall screen reference: near-black graphite canvas, quiet charcoal surfaces, compact sans-serif typography, and high-chroma yellow, green, pink, and blue reserved for attendance and academic data. Keep status and control states semantic; the accent's paired dark foreground belongs on bright surfaces, while neutral body copy stays on dark surfaces.

The five root destinations are Home, Courses, Week, Marks, and More. Nested detail views provide an explicit back control. The bottom bar floats above the gesture area on a subtly outlined charcoal capsule; inactive destinations show icons only, while the active destination gets a white icon-and-label pill. Week starts on Schedule and includes a date-selectable month grid. Its markers and daily lists come only from actual synced schedule and attendance records. Empty data stays explicit rather than being filled with sample notifications, marks, or grades.

## Palette directions

These are the implemented dark surfaces and palette accents. Signal remains the default. Light and system appearance choices are intentionally removed; older saved preferences normalize to dark mode.

| Theme | Character | Core colors |
|---|---|---|
| Signal | Bright academic | Canvas `#090A0C`; surface `#131416`; action `#F4EC28` |
| Cobalt Studio | Cool, precise | Canvas `#090A0C`; surface `#131416`; action `#5795FF` |
| Copper Night | Warm industrial | Canvas `#090A0C`; surface `#131416`; action `#FFA070` |
| Forest Circuit | Botanical, technical | Canvas `#090A0C`; surface `#131416`; action `#35E97B` |
| Paper Circuit | Warm editorial | Canvas `#090A0C`; surface `#131416`; action `#F0E5C8` |

All palettes share readable primary and secondary text plus consistent green, amber, and red status pairs. Android and web use the same five palette IDs; the accent preference is local to the device/browser. Internal assessment rows are a separate optional snapshot field and stay empty until a verified student-authorized source exists. Semester results and GPA calculations remain separate and unchanged.
