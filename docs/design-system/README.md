# Rollcall Compose design system

## Scope

Compose design tokens are centralized in `ui/theme`. The legacy Java UI still receives its palette through `AppPalette`; the two systems are not yet a single shared source. `RollcallHeroIdentityView` receives the active hero values from `DashboardView`, preserving the existing theme behavior.

## Tokens

- `RollcallThemeColors`: app and semantic colors for canvas, surfaces, text, brand accents, borders and attendance states.
- `RollcallTypography`: shared JetBrains Mono text styles for the Compose slice.
- `RollcallSpacing`: 4dp-based spacing values.
- `RollcallShapes`: shared card, control and pill shapes.
- `RollcallMotion`: shared Compose timing and easing defaults.

These tokens begin with the existing Rollcall Signal palette. They do not create a new theme picker or alter persisted theme preferences. When another screen moves to Compose, map the exact active `AppPalette` values into `RollcallThemeColors`; do not hardcode a second theme inside the screen.

## Components

Screen components accept immutable UI data and a `Modifier`. The Java bridge may expose a Java-friendly update method, but it must replace one immutable state value instead of maintaining duplicate display fields. Put reusable presentation under `ui/components` only when two or more real screens need it. Keep screen-specific composition with its screen.

Provide previews for standalone Compose screens/components where their data can be represented without production services. Preview examples are not production data.

## Theme switching

The current Activity still owns theme choice and persistence. Compose components receive the colors produced by that existing choice. A future complete theme migration must verify both light and dark modes alongside legacy screens before removing `AppPalette` or its preference keys.
