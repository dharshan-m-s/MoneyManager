# UI/UX Reference Audit — BitChord to MoneyManager

Date: 2026-11-01
Reference repository: https://github.com/kushagrasinghx/BitChord
Reference commit: b690dc146009d4815825fa4a20815ef2e4ee8898
Target repository: https://github.com/dharshan-m-s/MoneyManager
Target base commit: 88dae50d3439ffdded91b06cb74137881cd075d1
Branch: beta

## Executive assessment

BitChord's visual richness comes from a coordinated rendering and interaction system, not translucent backgrounds everywhere. Its implementation combines a sampled app backdrop, GPU blur and lens refraction, directional edge highlights, inner/outer shadows, a shared floating-tab layout, a travelling selection lens, scroll-aware collapse/expand behaviour, careful insets, and a consistent type/shape system.

MoneyManager already has groundwork: ModernGlass.kt, ModernMotion.kt, reusable component primitives, a floating dock and modern theme tokens. However, public main has gaps between intent and active implementation. In particular, ModernGlass.kt documents an AGSL refraction shader, but the exposed mmLiquidGlass() and mmGlassHighlight() modifiers currently only clip content to rounded rectangles. The shader holder/effect/uniform path is not connected to these public modifiers. ModernDock is an opaque themed surface with a simple active background, not a sampled glass bar with a moving lens. ModernRoot is the right integration boundary for a shared backdrop provider and scroll-aware dock; business/navigation logic should remain outside visual components.

This is an audit and implementation blueprint. No BitChord source code is copied into MoneyManager.

## 1. BitChord implementation examined

### Glass material and backdrop

- LiquidGlass.kt separates full liquidGlass(), low-cost lightweightLiquidGlass(), and rim-only liquidGlassRim().
- CompositionLocals provide glass enablement and the app backdrop.
- Full RenderEffect blur is gated for Android 12 / API 31+. Lens refraction and chromatic dispersion are enabled on Android 13 / API 33+.
- Reduced dynamic blur and unsupported platforms use deterministic solid/fallback treatments.
- Theme-aware tint, edge hairline, directional highlight and shadow are composed as separate visual elements.
- Backdrop rendering is downsampled to 0.33 scale to reduce GPU cost.
- Small controls and stable flat backgrounds use rim/highlight treatments instead of full backdrop capture.
- The backdrop package is vendored from Kyant0/backdrop v2.0.0; source headers identify Apache-2.0 and upstream attribution.
- The LiquidGlass integration file documents adaptation from Echo Music GPL-3.0 integration glue. Do not transplant that integration code into MoneyManager. Use behaviour as inspiration and write original code, or obtain separate license review.

### Navigation and tab motion

- GlassNavBar combines tab navigation and a mini-player accessory into one floating composition.
- It supports expanded and compact/inline states; scroll collapses the bar and scrolling back up expands it.
- FloatingBottomBar defines shared geometry: pill inset, nav gutter, bottom lift, touch target geometry and icon sizing.
- A shared spring drives selection and glyph motion; the indicator elongates during travel and regathers at rest.
- Dragging includes boundary resistance and tab-boundary feedback.
- FloatingTabBar and GlassSelectionPill separate interaction physics from rendering: lift, travel, acceleration squash/stretch and a glass-to-flat hand-off.
- The travelling lens samples exported backdrop layers and the tab row rather than taking a costly screen capture.
- The floating tab bar source is attributed to an Apache-2.0 library; the motion concept is attributed in source to an MIT package. Preserve license notices if independently adopting any code and do not copy without checking terms.

### Top chrome, sheets and overlays

- FrostedTopBar uses transparent floating controls over a page-owned top gradient/backdrop rather than an opaque full-width toolbar.
- A large title is used at scroll top; a compact centered title appears after scrolling.
- PlayerSheetMotion controls position and velocity so a sheet can track the finger and preserve movement across open/close.
- PlayerFrost captures a still when a menu opens, blurs that still while the panel is visible and releases it after exit. This avoids continuously re-rendering a moving surface behind blurred menus.
- Insets are measured from real window geometry instead of assuming one fixed top/bottom offset.

### Appearance and design system

- Theme.kt uses a deliberate typography system, bundled typefaces and measured letter spacing, with coherent light/dark palettes.
- Shape, edge, text, icon and spacing values are reused across pages, navigation, sheets, top controls and transient menus.

Key reference files:
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/components/LiquidGlass.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/components/GlassNavBar.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/components/FloatingBottomBar.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/components/floatingtabbar/FloatingTabBar.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/components/floatingtabbar/GlassSelectionPill.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/components/FrostedTopBar.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/player/PlayerSheetMotion.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/PlayerFrost.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/components/backdrop/Backdrop.kt
- https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/ui/components/backdrop/effects/Lens.kt

## 2. MoneyManager current architecture and gaps

Inspected public main at 88dae50d3439ffdded91b06cb74137881cd075d1:

- ModernGlass.kt includes an AGSL refraction shader constant and support helpers, but its public mmLiquidGlass() and mmGlassHighlight() modifiers currently return only rounded clipping. The shader holder/effect/uniform methods are not connected to these modifiers.
- ModernMotion.kt provides press-scale, small staggered reveal, animated amount, skeleton shimmer and button primitives. This is a useful motion foundation, but not a consistent screen-transition/gesture model.
- ModernDock.kt uses a Material Surface with theme surface colour, outline and elevation. Selection is a small tinted background and colour transition. It is functional, but not an optical glass navigation system.
- ModernRoot.kt owns lock lifecycle, navigation controller, overlay state, dock routing and screen shell. It is the right integration boundary for a shared backdrop provider and scroll-aware dock.
- Theme.kt already supplies light/dark colours, semantic income/expense/transfer/warning colours, typography, shapes and spacing. Refine centrally rather than replacing these with per-screen colours.
- app/build.gradle.kts uses Compose BOM 2026.06.00, Material Compose, Navigation Compose 2.9.7 and minSdk 30. Validate any backdrop dependency against this Compose toolchain. BitChord says it vendored its backdrop renderer because an older-Compose binary artifact caused runtime incompatibility in its own app.

MoneyManager is a financial app. Transaction amounts, chart labels, bill states and account balances must remain crisp and high contrast. Avoid refraction or blur directly over dense numerical text or chart axes.

## 3. What MoneyManager should implement

### Phase A — Correct glass rendering foundation

1. Replace placeholder-only mmLiquidGlass and mmGlassHighlight behaviour with a tested, original implementation.
2. Introduce a root-owned backdrop/layer that samples actual app content behind glass. Avoid a generated gradient and avoid unbounded full-screen bitmap screenshots every frame.
3. Split material APIs by cost and intent: full glass for the primary bottom dock and a small number of floating overlays; lightweight glass for pills and selection chips; rim/highlight-only for controls on stable backgrounds; solid fallback for reduced blur, unsupported APIs, shader failure and low-performance devices.
4. Make tint, contrast, rim, highlight direction and shadow adapt to light/dark appearance.
5. Runtime-gate effects: API 30 fallback, API 31–32 blur/frost, API 33+ RuntimeShader lens/refraction where supported.
6. Profile downsampled recording or another rendering technique. Cache shaders, shapes and layers; never recreate them on every recomposition. Do not continuously animate shader time on static surfaces.
7. Provide accessibility/performance control such as Reduce transparency/motion, and stop backdrop recording when disabled.

### Phase B — Navigation as one coherent component

1. Rework ModernDock into one floating bottom navigation composition with stable geometry, insets and safe-area handling.
2. Use a travelling selection lens/pill interpolating between tabs, with restrained stretch/squash and spring motion.
3. Integrate Add without covering labels, content or system gesture areas.
4. Keep tab labels readable in light and dark themes over unpredictable backdrop content.
5. Optionally compact the dock on downward scroll and expand it on upward scroll; never jump while a sheet, keyboard, dialog or nested scroll is active.
6. Preserve navigation semantics, back stack, state restoration, accessibility labels and minimum touch targets. Keep account data and screen state outside the dock.
7. Handle short devices, gesture and three-button navigation, edge-to-edge and large font scale.

### Phase C — Top chrome and screen transitions

1. Introduce floating transparent top controls using a page-owned background gradient/backdrop; avoid opaque full-width bars where content can safely scroll behind.
2. Use a large page title at scroll top and a compact/centered title after scrolling; animate title without moving action targets.
3. Standardize route transitions: subtle fade plus small vertical offset, or shared-element transitions for related objects. Avoid competing effects on every component.
4. Use shared-element relationships only when the same account/transaction/category object persists between routes.
5. Standardize bottom sheets with drag-aware position/velocity, dismissal thresholds, cancellation and navigation-bar/IME inset handling. Prefer Material sheet state unless custom physics is justified by measured UX.
6. Keep transitions interruptible and never delay financial actions.

### Phase D — Screen visual language

- Dashboard: clear balance hero, compact income/expense context, clean spending chart and recent transactions. One focal card. Glass only on floating actions, not chart/data surfaces.
- Transactions: crisp grouped ledger rows, clear amount alignment, restrained row motion, category icon surfaces and filter/search chips. No background refraction under amounts.
- Accounts: distinct bank/card/wallet/prepaid group headers, clean cards, balance typography and card identity; subtle depth/gradient rather than glass on every card.
- Bills, budgets and spend summary: clear due/paid/overdue states, legible numerics and semantic colours. Glass around controls and overlays only.
- Dialogs and menus: consistent shape, backdrop dim and optional frozen blur snapshot; focus management and predictable dismiss.
- Empty/loading/error states: deliberate iconography, calm skeletons and no excessive repeating animations.

### Phase E — Polish and quality

- One typography scale with tabular numerals for money, one spacing scale, one shape language and semantic tokens.
- Consistent 48dp touch targets, content descriptions, selected states and TalkBack order.
- Dynamic type/font scaling; screen widths and landscape/foldable layouts.
- Respect light/dark setting; status/navigation bar contrast follows actual content.
- Profile GPU/rendering and recomposition; test API 30, 31/32, 33+ and low-end hardware. Provide deterministic solid fallbacks.
- Add screenshot/UI tests for dock selection, scroll collapse/expand, sheets, themes and reduced transparency.
- Verify visual work does not alter financial logic, repositories, Room schema or migrations.

## 4. Recommended MoneyManager visual direction

Retain MoneyManager's One UI-inspired ergonomics and semantic financial colours. Add BitChord-level material craft: porcelain/graphite content planes with restrained indigo accent; genuinely translucent floating dock; shallow optical rim and one travelling selection lens; calm dashboard typography and subtle hero depth; transparent/frosted top actions that preserve title contrast; gesture-aware sheets and consistent transitions. Limit glass to navigation, floating actions and transient controls. Keep primary financial data surfaces solid and legible.

## 5. Suggested implementation order

1. Add visual test harness and measure current frame/recomposition/GPU cost.
2. Build/test reusable backdrop layer, glass primitives and fallbacks.
3. Apply glass to floating dock and implement selection lens motion.
4. Apply shared top chrome, scroll title behaviour and route transitions.
5. Standardize sheets, dialogs and transient overlays.
6. Polish dashboard, transactions and accounts with shared typography/surface rules.
7. Extend to remaining screens after the foundation is stable.
8. Run unit tests, UI/screenshot tests and APK build; compare API 30, 31/32 and 33+.

## 6. Non-goals and safeguards

- Do not copy BitChord files or GPL-derived Echo integration code.
- Do not add expensive full-screen blur to every list/card.
- Do not blur/refraction-sample financial numerals, transaction rows or chart labels.
- Do not introduce unrelated data, accounting, navigation or persistence changes.
- Do not rewrite every screen in one enormous change. Build rendering foundation and navigation first, then migrate screens in controlled batches.
- Keep work on beta. Create one consolidated commit for each intentionally finished milestone. If a committed milestone is wrong and must be replaced, reset/revert the branch commit and replace it with one corrected consolidated commit instead of stacking noise.

## 7. Files reviewed

BitChord: LiquidGlass.kt, GlassNavBar.kt, FloatingBottomBar.kt, backdrop/Backdrop.kt, DrawBackdropModifier.kt, effects/Blur.kt, effects/Lens.kt, highlight/Highlight.kt, floatingtabbar/FloatingTabBar.kt, floatingtabbar/GlassSelectionPill.kt, FrostedTopBar.kt, PlayerSheetMotion.kt, PlayerFrost.kt, Theme.kt and app/build.gradle.kts.

MoneyManager: ModernGlass.kt, ModernMotion.kt, ModernCore.kt, ModernDock.kt, ModernRoot.kt, ModernNavHost.kt, DashboardScreen.kt, TransactionsScreen.kt, AccountsScreen.kt, Theme.kt, MoneyManagerTopBar.kt and app/build.gradle.kts.

This document is architecture analysis and an actionable roadmap. It does not claim visual implementation or APK verification.