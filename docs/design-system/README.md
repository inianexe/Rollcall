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

## Theme switching

The current Activity still owns theme choice and persistence. Compose components receive the colors produced by that existing choice. Compose tests cover live palette updates and mapped light/dark tokens. Actual preference survival through Activity recreation remains owned by the unchanged Java preference flow and was not exercised by these isolated component tests. Verify both Compose and legacy screens before removing `AppPalette` or its preference keys.
