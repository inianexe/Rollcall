# Rollcall Compose design system

## Scope

`AppPalette.get(dark, legacyTheme)` remains authoritative for Java palette selection and persistence. Its `toComposeColors()` mapping supplies the Compose semantic tokens, so Compose does not keep a second copy of the active palette. The legacy theme argument is currently ignored by the existing palette implementation; this phase does not add or reinterpret theme variants. Bridges are initialized with the active mapping when `DashboardView` is rebuilt, matching the current Activity recreation behavior on appearance changes.

## Tokens

- `RollcallThemeColors`: semantic colors mapped from `AppPalette` for canvas, surfaces, text, brand accents, borders, focus, ticket art and attendance states.
- `RollcallTypography`: shared JetBrains Mono text styles for the Compose slice.
- `RollcallSpacing`: 4dp-based spacing values.
- `RollcallShapes`: shared card, control and pill shapes.
- `RollcallMotion`: shared Compose timing and easing defaults.

These tokens retain the existing Rollcall Signal light and dark appearance. They do not create a new theme picker or alter persisted theme preferences. Add colors to the Java palette and mapping together only when a real semantic role is missing; do not hardcode a second theme inside a composable.

## Components

Screen components accept immutable UI data and a `Modifier`. The Java bridge may expose a Java-friendly update method, but it must replace one immutable state value instead of maintaining duplicate display fields. Put reusable presentation under `ui/components` only when two or more real screens need it. Keep screen-specific composition with its screen.

Provide previews for standalone Compose screens/components where their data can be represented without production services. Preview examples are not production data.

The Courses screen is Compose. Its `LazyColumn`, search field, filter controls, empty state and reused `CourseCard` use shared semantic colors and typography. Java still computes attendance, groups and priority-sorts lecture/lab rows, applies the legacy search/filter behavior, and owns expansion state and callbacks. `CourseCardUiState` contains the existing calculated percentage, Can miss, Must attend, status, progress color and expanded state. Do not add attendance math or a parallel grouping/filter pipeline to composables.

The Week page uses the same `RollcallTheme` and semantic tokens. `WeekScreen` renders Java-prepared schedule/history states and Java-owned expansion/mode state. Its Canvas Week Pulse receives the existing daily activity counts and labels; the drawing is a visual representation only. Attendance statuses, approved OD meaning, date grouping and pagination remain determined by Java.

Courses metrics switch to a single-column layout for larger font scales, single-metric groups, or long counts so values remain visible. Search/filter text wraps. At font scales above 1.3, the fixed five-item dashboard navigation hides its visual text labels to avoid broken word wrapping; the full tab names remain exposed on the clickable tab semantics. The 320dp/2.0x layout was screenshot-reviewed on the Android 16 emulator. Course title and summary may ellipsize visually at very large text sizes; their complete content remains available in Compose semantics. This is not a claim of TalkBack testing.

## Theme switching

The current Activity still owns theme choice and persistence. Compose components receive the colors produced by that existing choice. JVM and emulator tests cover mapped light/dark tokens, live palette repaint, Java/Compose screen appearance, and the existing preference across Activity recreation. Full process-death/relaunch persistence was not tested. Verify both Compose and legacy screens before removing `AppPalette` or its preference keys.
