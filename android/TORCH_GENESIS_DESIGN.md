# TORCH GENESIS — Rollcall's single native visual language

**Source of truth:** user-provided `rollcall_torch_prototype(1).html` (SHA-256: `e0c18e3493709bb9fe33916a8a2036cef9fe9dda0681bd4c382c5fe912361373`). This source defines the UI. The Android implementation is not intended to retain legacy palette or card styling.

## Visual grammar

Dark appearance is the canonical HTML prototype:

| Role | Reference |
| --- | --- |
| Canvas | `#121212` |
| Text/paper | `#F5F5EE` |
| Main accent / hero | `#CCFF00` |
| Orange punctuation / urgency | `#FF4400` |
| Lilac callout | `#C8C8FF` |
| Panels | `#222222`, `#2C2C2C` |
| Strokes | `#525252` |
| Secondary text | `#A8AAA1` |
| Success | `#9BE4A1` |

Typography: JetBrains Mono, compact uppercase kickers, oversized headings with tight tracking, 10–12sp metadata and legible body text. Cards use ~16dp corners, 1dp outlines, ~20dp internal padding. Icons use line strokes, not decorative colour fills. The nav dock uses a charcoal outline and a single lime active pill.

Light appearance is a deliberately accessible translation (`#F5F5EE` canvas, dark ink and warm-white surfaces), **not** an inversion; light-mode small orange text uses a darker ink-safe orange. Semantic red/green stay semantically meaningful.

## Screen map

| Prototype screen | Native screen and components |
| --- | --- |
| Home: broadcast hero, two cards, radar, week, needs-attention | `DashboardView.buildOverview`, `updateOverviewExam`, `updateOverviewSchedule`, `CourseRow` |
| Courses: dark outlined card, attendance pill, two decision metrics | `buildCoursesPage`, `CourseGroupRow.metric`, `attendanceMetricTile` |
| Week: segmented attendance/schedule and class history | `buildTimetablePage`, `updateTimetable`, `updateClassHistory` |
| What if: lilac before/after and 1–20 slider | `plannerCard`, `updatePlanner` |
| Marks: lime CGPA, orange trends and ordered results | `buildAcademicsPage`, `updateAcademics`, `SemesterGpaChart` |
| Settings: dark outlined controls, lilac project card | `MainActivity.buildAboutPage` |
| Popups, access, onboarding, updater | `MainActivity`, `UpdateActivity`, `RollcallDialogLight/Dark` |

The homepage print ticket is an **actual date**: next future class, upcoming KCT exam, or today. It routes to Week or Marks. Nothing is sourced from the static sample data embedded in the HTML.

## Motion

Use the source's 260ms eased fade-and-rise navigation, tactile 70–150ms press feedback, 240ms sheet entry and existing single-purpose sync mark. Respect system animation disable settings. Avoid continuous ambient movement on reading-heavy screens.

## Non-negotiable boundaries

1. **Do not** display prototype sample courses, GPA, or sample exam dates in a connected app.
2. **Do not** modify, scrape differently, restyle, or inject CSS into MyCamu/KCT third-party WebViews.
3. Keep attendance, OD, CGPA and update-signing calculations independent from UI changes.
4. Destructive account actions must retain confirmation and red semantic emphasis.
5. The native OS installer, permissions and system dialogs remain system-owned.
6. Ensure at least 4.5:1 contrast for functional text; include words alongside all semantic colours.

## Visual QA before merge

Review at 320dp, 360dp, 412dp, and large-font settings in both appearances. Check onboarding steps 1–4, account consent, Home with/without timetable, an upcoming exam and empty exam, Courses below/above target, lecture/lab pairs, Week attendance and schedule, What if, Marks, Leave/OD, Settings, Firebase credentials/account actions, updater, and navigation after screen rotation. Confirm that all controls remain at least 44–48dp tall.
