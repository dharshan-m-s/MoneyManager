# Money Manager visual system

This document records the design constraints for the Compose UI refresh. The work changes presentation and navigation only; repository, ledger, persistence, import/export, and transaction workflows remain the source of truth.

## Design intent

Money Manager should feel like a calm, premium financial instrument: confident typography, generous rhythm, precise data presentation, and a distinct control layer. The visual language combines iOS-inspired hierarchy and material depth with compact Android ergonomics.

## Material rules

- Liquid Glass is reserved for the floating dock, navigation actions, and transient controls such as menus and popovers.
- Financial content remains on solid, adaptive surfaces. Balances, transactions, charts, and forms must remain legible regardless of the backdrop behind them.
- Use the sampled backdrop/lens implementation already in `ui/components/glass/backdrop`; do not imitate it with a white gradient and generic blur.
- On unsupported Android versions or when a backdrop is unavailable, controls fall back to a high-contrast opaque surface.

## Visual tokens

- Brand: indigo-violet, supported by restrained cool-blue highlights.
- Light canvas: porcelain / cool neutral; dark canvas: deep ink, not pure black.
- Content surfaces: white and layered graphite-blue surfaces with subtle borders.
- Semantic colors remain stable: income green, expense red, warning amber, and neutral transfer blue.
- Rounded geometry is consistent across cards, fields, and icon containers. Numeric values use tabular figures.

## Interaction and motion

- Navigation changes preserve section state and avoid duplicate route pushes.
- Main destinations retain the floating dock; detail, edit, and create flows receive the full screen.
- Tap feedback is short and subtle. Screen transitions are directional and spring-based without excessive bounce.
- Motion must not delay financial actions or conceal changing values.
- Destructive actions retain explicit confirmation and reversible navigation where possible.

## Quality bar

- Preserve every existing financial workflow and calculation.
- Keep labels, amounts, and touch targets readable in both themes.
- Test empty, populated, long-name, and large-amount states.
- Check compact portrait screens and keyboard-open form layouts.
- Run unit tests, Android lint, and a debug APK build before treating a redesign as complete.

## Reference guidance

- Apple Human Interface Guidelines — Materials: https://developer.apple.com/design/human-interface-guidelines/materials
- Apple Human Interface Guidelines: https://developer.apple.com/design/human-interface-guidelines/
- WWDC26 — Principles of great design: https://developer.apple.com/videos/play/wwdc2026/250/

These references are adapted to Android Compose rather than copied literally. In particular, Liquid Glass is treated as a functional control material, not a decorative replacement for content surfaces.
