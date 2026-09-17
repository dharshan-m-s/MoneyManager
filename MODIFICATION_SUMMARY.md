# Money Manager — UI/UX modernization: modification summary

This document describes what changed in the whole-app UI/UX and functional-quality pass.
Existing architecture, database schema, repositories, ViewModels and business logic were reused;
no second transaction system, no duplicate repositories and no fabricated demo data were added.

## 1. Design system (new)

`ui/theme/Theme.kt` is now the single source of visual truth:

- **`MmSpacing`** — one spacing scale (`xxs` 2, `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24,
  `xxl` 32) plus `screen` (20dp minimum horizontal margin), `card`, `touchTarget` (48dp) and the
  standard radii (`radiusCard` 20, `radiusRow`/`radiusField` 16, `radiusSheet` 28).
- **`MmType`** — semantic text styles: `screenTitle`, `sectionTitle`, `body`, `label`, `caption`
  and a financial amount hierarchy (`amountHero` > `amountLarge` > `amountRow`).
- **`MmColors`** — *theme-resolved colour roles* (`background`, `surface`, `surfaceMuted`,
  `divider`, `outline`, `textPrimary/Secondary/Tertiary`, `accent`, **`onAccent`**, `income`,
  `expense`, `warning`). The old light-only literals (`MMBackground`, `MMGrayDivider`,
  `MMGrayText`, `MMOutline`, `MMSurfaceElevated`, `MMSurfaceMuted`, tints, `MMCashForward`, …)
  were removed from screens app-wide; this is what removes the white boxes and grey-on-grey text
  in dark mode. `MMGreenDark`/`MMWhite` (brand header), the chart hues and `MMIconShadow` are the
  only fixed constants left, and that rule is documented in the file.

## 2. Reusable component library (new, `ui/components/`)

- **`MmCore.kt`** — `MmCard`, `MmPlainCard`, `MmSectionHeader`, `MmIconBadge`, `MmPill`,
  `MmChip`, `MmSegmentedControl`, `MmSwitchRow`, `MmProgressBar`, `MmInfoRow` (renders nothing
  when a value is meaningless, so screens never print “—”), `MmInfoGroup`, `MmEmptyState`,
  `MmLoadingState`, `MmErrorState`, `MmQuickAction`, `MmBottomSheet`, `MmStatTile`.
- **`MmFields.kt`** — `MmTextField`, `MmAmountField` (₹ prefix, numeric keyboard, 2-decimal
  clamp), `MmPickerField` + `MmPickerSheet` (searchable single-choice bottom sheet),
  `MmDateField` (platform date/time pickers instead of typed `dd MMM yyyy`), `MmStepper`,
  `MmSearchField`.
- **`MmTransactionRow.kt`** — the one canonical row used by every transaction list:
  `[icon] Merchant / Category • Date / signed amount`. Single-line ellipsised title and a fixed
  amount column, so long merchant names can never wrap oddly or push the amount off-screen. Also
  `MmPeriodHeader`, `MmLegendDot`, `MmStatTile` and `mmCategoryTint` (stable per-name tint, lifted
  for dark surfaces).
- **`MmAttachment.kt`** — receipt thumbnail, viewer, camera and gallery pickers.
- **`AppChrome.kt`** — reduced to the one real primitive, `MoneyManagerTopBar`.
  Dead helpers (`SectionCard`, `StatusPill`, `IconBadge`, `DashboardFab`, `FabMenuItem`) deleted.
- Docs: `docs/UI_DESIGN_SYSTEM.md` rewritten to match the tokens and components that exist.

## 3. Receipt / bill attachment — now real and persistent

- New `transaction_attachments` table with a cascading foreign key, plus **Room migration 8 → 9**
  (SQL verified against the generated `9.json`), `TransactionAttachmentDao` and
  `AttachmentRepository`.
- Picked or captured images are **copied into the app's private storage**, so an attachment
  survives navigation, recomposition, process death, app restart and the source being deleted.
- Camera capture uses `ActivityResultContracts.TakePicture` with a FileProvider target and the
  gallery uses the modern photo picker — **no storage permissions**, and the app deliberately does
  not declare `CAMERA` (the system camera app is delegated to).
- Transaction detail shows the receipt; edit can view/replace/remove; deleting a transaction
  removes its attachment rows (cascade) *and* its image files from disk.

## 4. Transactions

- **Detail screen rebuilt**: hero (category icon, title, large amount, income/expense pill,
  date-time) followed by logically grouped facts. The old raw database dump
  (`Account —`, `Payment Type —`, `Business/Personal —`, …) is gone and empty optional fields no
  longer render as “—”. No database column was removed.
- **Edit screen rebuilt**: real date/time pickers, account/category/payment picker sheets,
  inline field validation, and an explicit income ⇄ expense switch. It is a true **UPDATE** of the
  existing row (no duplicates) via `TransactionRepository.updateEditableTransaction`, followed by
  a canonical recalculation of the affected accounts.
- **Split With Friends / Mark as Business / Loan given-paid are not exposed anywhere** in the
  transaction UI (nor in Add Transaction). `businessPersonal` is preserved untouched in the DB.
- Delete is a real operation: it removes paired transfer legs, un-links any bill-instance payment
  so the bill returns to unpaid, recalculates the affected accounts, and deletes receipt files.
- Every transaction in every list is clickable and routes to the one shared
  `TransactionDetail` destination.

## 5. Credit cards

- **Form rebuilt**: card identity preview (`HDFC Rupay Millennia` / `•••• 2346`), grouped sections,
  a cycle-day **stepper** and due-date **picker** instead of raw text, switches for auto-pay /
  inactive / hidden, Personal|Business segmented control, inline validation including a
  duplicate-card check.
- **Account detail rebuilt**: stable blue card header (no layout shift or clipping), equal-width
  View / Monthly / Billing-cycle tabs, "Last reported" only when data exists, and a limit-usage
  progress bar.
- Billing cycles are driven by the **configured billing-cycle start day** (`04 SEP – 03 OCT`),
  never by arbitrary calendar months.
- Semantics verified in `AccountingEngine`: `CARD_PURCHASE` increases outstanding, `CARD_PAYMENT`
  decreases it, and `CARD_PAYMENT` is flagged `countsAsTransferOrCashMovement` — it is not
  ordinary spending. Spend totals filter `txnSubType = 'EXPENSE'`, so a bill payment (recorded as
  `TRANSFER_IN` + `cc-bill-payment`) can never be double-counted.

## 6. Screens rebuilt on the design system

Dashboard (greeting header, summary tiles, quick actions, budget progress, canonical rows and a
filter bottom sheet) · Accounts (grouped Cash / Bank / Credit cards, real totals, whole row
tappable) · Account detail · Cash (balance hero, monthly grouping, income **and** spend add
control, dark-mode-safe forward-cash row) · Transactions (inline search + filter bottom sheet with
applied-filter chips) · Transaction detail/edit · Spend areas (donut + per-category progress) ·
Category drill-down · Budget · Bills and bill form (picker sheets, stepper, currency field) ·
Bill type selection (previously an unreachable screen — now wired ahead of the bill form) ·
Categories and create-category · Income · Search (integrated field, results count, distinct
empty/no-results states) · Reimbursements · Import (idle → analysing → preview → committing →
done/error, each with real content) · Settings (see below) · Drawer · App updates.

## 7. Settings and the app lock — no more decorative switches

The Settings screen was full of toggles that did nothing. It now contains only working controls:

- **Home screen**: show income, show cash wallet, hide amounts — backed by a new
  `HomePreferences` state holder, so flipping a switch changes the dashboard immediately.
- **Security**: a real PIN lock (`AppLock` + `PinHasher`, PBKDF2-HMAC-SHA256 with a random
  per-install salt and a constant-time comparison). `MoneyManagerRoot` shows a full-screen unlock
  gate and relocks after 30 seconds in the background, so a trip to the photo picker does not
  force a re-entry.
- **Backup & restore**: shows the real configured folder and the real last-backup timestamp,
  creates a backup immediately after a folder is chosen, and exposes automatic backup.
- **Data**: CSV export to a file the user chooses, plus statement import.
- **Appearance**: dark mode (applies immediately — `MainActivity` observes the preference).
- **About / Help & feedback / Open source**: real GitHub links built from
  `BuildConfig.GITHUB_OWNER` / `GITHUB_REPOSITORY`, an honest local-first privacy statement, a
  real version/build row and a real dependency licence list.

## 8. Dark mode, insets and responsiveness

- Every screen reads colours from `MmColors`; `Color.White` on a brand surface was replaced with
  `MmColors.onAccent` wherever the surface is the theme accent (filled buttons, FABs, brand
  headers). Remaining fixed colours are intentional: the always-green top bar, the always-blue
  card header and chart/category hues.
- True **edge-to-edge**: `setDecorFitsSystemWindows(window, false)`, the brand headers apply
  `statusBarsPadding()` so the green runs under the status bar, and the navigation host applies
  `navigationBarsPadding()` once so no list, form or button can hide behind the navigation bar or
  gesture handle. Bottom sheets also apply nav-bar and IME padding; the activity is
  `adjustResize`, so the keyboard does not destroy forms.
- Two-column layouts collapse to one when the width is insufficient (credit-card
  outstanding/limit stacks under 340dp), and long names are ellipsised rather than allowed to
  break layout.
- Micro-interactions are used sparingly: Material ripples on cards/rows, FAB speed-dial for the
  add action, expandable sections, progress indicators and snackbars for confirmations.

## 9. Verification performed

| Check | Result |
| --- | --- |
| `./gradlew clean compileDebugKotlin` | PASS — 0 errors, 0 warnings |
| `./gradlew testDebugUnitTest` | PASS — 40 tests, 0 failures, 0 errors |
| `./gradlew assembleDebug` | PASS — `app-debug.apk` produced |
| `./gradlew assembleRelease` (R8 + resource shrinking + `lintVital`) | PASS — unsigned release APK produced |
| Navigation audit (scripted) | PASS — 33 / 33 destinations declared and registered, no orphan screens |
| FileProvider + `file_paths.xml` | PASS — authority `${applicationId}.fileprovider` matches repository/installer usage; receipts path declared |
| Room migration 8 → 9 | Verified against the exported `9.json` schema |
| Unreachable / dead UI | Removed; `BillTypeSelection` was unreachable and is now wired before the bill form |
| Light-only colour literals outside `Theme.kt` | 0 remaining (scripted check) |
| `Color.White` on an accent surface | 0 remaining (scripted check) |

## 10. Not verified here (no device or emulator)

Real-device confirmation of dark mode, small/large screen widths, keyboard behaviour, camera
capture and receipt persistence across a real app restart. The code paths are wired and compile,
but they have only been checked statically. Receipt image bytes are intentionally **not** part of
the database-snapshot backup, so restoring on another device shows a clear “file missing” state
rather than a broken image.

Nothing has been committed or pushed to GitHub as part of this work.
