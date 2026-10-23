# Modern — the redesigned Money Manager appearance

Money Manager ships **two complete visual experiences** that share the same application,
database, accounting engine, ViewModels and navigation routes:

| Appearance | What it is | Default |
| --- | --- | --- |
| **Default** | The original Money Manager experience, preserved byte-for-byte. | ✅ for fresh installs |
| **Modern** | A clean, One UI-inspired light/dark interface with a floating bottom dock, restrained surfaces and clear amounts. | opt-in |

The look is selected in **Settings → Appearance → Look**. It is independent of the light/dark
switch: either look works in either mode, and changing one never changes the other.

Modern draws edge-to-edge. The activity leaves the status and navigation bars transparent;
screens consume safe insets. The navigation host reserves dock height on dock destinations.
The dock uses system/navigation bar insets so controls sit above the nav area without a
rectangular backing bar.

## Where the preference lives

- `ui/appearance/AppearancePreferences.kt` — the single source of truth.
  - SharedPreferences file `moneymanager_settings`, key `appearance_look`.
  - Stable stored values: `classic` → Default and `vintage` → Modern. Existing selections
    are preserved. Missing, unknown, or corrupted values resolve to **Default**.
  - Exposed as a `StateFlow<MmAppearance>`; `MainActivity` collects it once and recomposes
    the whole tree for immediate switching (no restart).
- `ui/appearance/AppRoot.kt` — mounts `MoneyManagerTheme + MoneyManagerRoot` for Default
  and `ModernTheme + ModernRoot` for Modern.
- `ui/appearance/AppearanceSelector.kt` — two radio cards with miniature previews.

## Package layout

```
ui/
  appearance/          shared preference, root switch, selector
  theme/ components/   Default ONLY (untouched)
  navigation/          Default ONLY (untouched)
  dashboard/ …         Default ONLY (untouched)
  modern/
    theme/             Modern palette, MmColors, MmType, MmShape, ModernTheme
    components/        ModernCore, ModernFields, ModernChrome, ModernMotion,
                       ModernTransactionRow, ModernFab, ModernInsets, etc.
    screens/           one folder per feature, reusing existing ViewModels
    navigation/        ModernRoot (lock gate, More popover, Add action surface), ModernNavHost, ModernDock
```

`ModernLayerIsolationTest` fails the build if anything under `ui/modern` imports
`com.moneymanager.app.ui.components.*` or `com.moneymanager.app.ui.theme.*`. Only
non-visual shared imports are allowed.

## Design system

### Colour roles

**Light mode**
| Role | Value | Use |
| --- | --- | --- |
| background | `#F7F8FA` | page |
| surface / surfaceElevated | `#FFFFFF` | cards, sheets, fields |
| surfaceContainer / surfaceContainerHigh | `#F0F2F6` / `#E8EBF0` | inset/chrome |
| outline / hairline | `#D8DCE3` / `#D8DCE3` | borders/dividers |
| textPrimary/secondary/tertiary | `#1C1C1E` / `#3C3C43` / `#6C6C70` | hierarchy |
| accent | `#2563EB` | links, selected |
| accentFill | `#2563EB` | primary buttons/FAB (white text) |
| income/expense/warning | `#047857` / `#DC2626` / `#B45309` | money semantics |

**Dark mode**
| Role | Value | Use |
| --- | --- | --- |
| background | `#000000` | page |
| surface | `#151517` | cards, sheets, fields |
| surfaceContainer / surfaceContainerHigh | `#202022` / `#242426` | inset/chrome |
| outline / hairline | `#3A3A3C` | borders/dividers |
| textPrimary/secondary/tertiary | `#F2F2F7` / `#AEAEB2` / `#8E8E93` | hierarchy |
| accent | `#5B9BFF` | links, selected |
| accentFill | `#2F6BDB` | primary buttons/FAB (white text) |
| income/expense/warning | `#34D399` / `#F87171` / `#FBBF24` | money semantics |

Accent is split: a readable accent for text/selection, and a higher-contrast accentFill for
filled controls with white content.

### Type, shape, spacing

- `MmType.screenTitle` 28sp bold, `sectionTitle` 18sp semibold, `body` 16sp,
  `label` 14sp medium, `caption` 13sp.
- Amounts use `amountHero` 40sp, `amountLarge` 26sp, `amountRow` 16sp, `amountTile` 17sp —
  all with tabular numerals (`tnum`).
- Cards `16dp`, rows/fields `12dp`, sheets `28dp` (top), full pill for segments/floats.
- Spacing scale 2/4/8/12/16/20/24/32dp; screen margin `20dp`, touch target `48dp`.

## Navigation

- Floating pill dock (Home, Transactions, Accounts, More) on top-level routes; hidden on
  detail/editor screens.
- Exactly one central Add button on the dock, rotating when open. It opens a compact action
  surface for expense/income/cash/transfer.
- More opens a compact popover (Bills, Cash, Spend Summary, Categories, Reimbursements,
  Settings). No full-height drawer/sidebar in Modern.
- The dashboard's top-left menu was removed (it opened More, not a drawer).

## Screens

Modern provides redesigned versions of all major routes using shared ViewModels/navigation:
Dashboard, Transactions, Accounts, Bills/Cash/Spend Summary/Budget/Categories/Reimbursements,
Add/Edit Transaction, Transfer, Transaction/Account detail and edits, Credit card form,
Import, Settings/Backup/App-lock/PIN, Updates, Feedback, About, and others. The Default
navigation drawer remains in the Default appearance only.

## Verification

```bash
./gradlew :app:testDebugUnitTest   # unit tests pass
./gradlew :app:assembleDebug       # debug APK built
./gradlew :app:compileDebugKotlin
```

New/updated tests:
- `ModernThemeContrastTest` — WCAG ratios computed from the real palette
- `AppearancePreferencesTest` — persistence and safe defaults
- `ModernLayerIsolationTest` — Modern tree isolation
- `androidTest/ModernLookTest` — appearance switching and shared components

Device UI testing: not possible in the current environment (no connected device/emulator).
