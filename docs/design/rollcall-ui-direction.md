# RollCall cross-platform visual direction

## Contract

RollCall keeps its Signal identity: near-black and warm-paper foundations, sharp lime action color, and orange used sparingly for emphasis. The additional themes change the atmosphere while preserving the same hierarchy, status meanings, and readable text contrast.

- **Typography:** keep the existing JetBrains Mono for compact measurements and use the existing rounded sans display face for headings. Body copy stays sentence case and comfortably sized.
- **Layout:** align content to one left edge; use one clear primary summary followed by compact, task-specific sections. Lists scan vertically. A control stays beside the content it changes.
- **Motion:** keep state changes brief and action-led. Remove continuous decorative animation from status content; respect reduced motion and view lifecycle.
- **Data:** distinguish recorded, projected, stale, empty, and unavailable states in words. Never present missing duration or marks as zero.
- **Cross-platform:** retain Java Views on Android and the existing HTML/CSS/JavaScript web app. Use matching theme IDs and semantic roles, with native interaction patterns on each platform.

## Palette directions

These are the implemented surface, action, and emphasis tokens. Each theme has paired light and dark variants. Signal remains the default.

| Theme | Character | Core colors |
|---|---|---|
| Signal | Existing RollCall identity | Canvas `#151719` / `#F5F6F3`; action `#CCFF00`; orange emphasis `#FF663D` dark / `#B42F08` light |
| Cobalt Studio | Cool, precise | Canvas `#151719` / `#F5F6F3`; action `#90A9FF` dark / `#3157D5` light; warm emphasis `#FFC27A` / `#8A4B00` |
| Copper Night | Warm industrial | Canvas `#151719` / `#F5F6F3`; action `#E6A07A` dark / `#B7653D` light; emphasis `#E6A07A` / `#873E21` |
| Forest Circuit | Botanical, technical | Canvas `#151719` / `#F5F6F3`; action `#78D4A4` dark / `#287A57` light; emphasis `#78D4A4` / `#176146` |
| Paper Circuit | Bright editorial | Canvas `#151719` / `#F5F6F3`; action `#2563EB`; emphasis `#FFA77F` dark / `#B94A1A` light |

Each direction has intentionally selected light and dark surfaces. Status colors retain their meaning. Android and web use the same five palette IDs and Light/Dark/System preference; the preference is local to the device/browser. Internal assessment rows are a separate optional snapshot field and stay empty until a verified student-authorized source exists. Semester results and GPA calculations remain separate and unchanged.
