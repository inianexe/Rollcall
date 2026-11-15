# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

College students who use MyCamu or KCT to track attendance and academic schedules. This audience is inferred from the app’s current provider integrations and screens.

## Product Purpose

RollCall helps students review attendance, weekly trends, courses, timetables, class history, leave records, and academic results in one companion app. This summary is based on the repository’s README and current Android dashboard.

## Positioning

Attendance calculations and provider access remain on the phone; the browser dashboard can read the latest account-scoped snapshot but cannot change attendance records. Confirmed in the repository README.

## Capabilities and Constraints

- Native Android application written in Java with Android Views and custom Views.
- Integrates MyCamu attendance and KCT academic records.
- Preserve attendance calculations, authentication, provider sessions, data formats, and navigation while changing presentation.
- Keep the existing single Android app module and Torch Genesis codebase boundaries.

## Product Principles

- Show attendance and academic information clearly enough to support planning.
- Keep provider access and attendance calculations on the device.
- Preserve existing records and navigation when presentation changes.
