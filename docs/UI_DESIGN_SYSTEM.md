# Money Manager UI design system

One visual language for every screen. The rules live in code (`ui/theme/Theme.kt`,
`ui/components/`) so a new screen inherits the design instead of re-inventing it.

Design principles: One UI for reachability, hierarchy and generous viewing space; Apple HIG for
clarity, focused actions and progressive disclosure. Nothing is copied literally - this stays a
native Android app on Material 2 compose.

## Tokens

### Colour

Never hard-code a colour in a screen. Use the theme-resolved roles in `MmColors`, which read from
`MaterialTheme.colors` and therefore adapt to dark mode automatically:

| Role | Use for |
| --- | --- |
| `MmColors.background` | Screen background |
| `MmColors.surface` | Cards, sheets, fields |
| `MmColors.surfaceMuted` | Inset/tinted areas (segmented controls, stepper, tiles) |
| `MmColors.divider` | Hairline separators |
| `MmColors.outline` | Card and field borders |
| `MmColors.textPrimary` / `textSecondary` / `textTertiary` | Body / supporting / disabled text |
| `MmColors.accent` | Brand and primary actions |
| `MmColors.onAccent` | Text and icons **on** `accent` (filled buttons, brand headers) |
| `MmColors.income` / `MmColors.expense` / `MmColors.warning` | Semantic money states |

The fixed constants at the top of `Theme.kt` are limited to three legitimate uses: the brand top
bar (`MMGreenDark` + `MMWhite`), the chart/category hues (`MMGreen`, `MMBlue`, `MMBrown`,
`MMPurple`, `MMCyan`, `MMAmberDue`) and `MMIconShadow`.

> `Color.White` on `MmColors.accent` is a dark-mode bug - the dark theme's accent is a light
> green. Always use `MmColors.onAccent` on brand surfaces.

### Spacing

`MmSpacing` is the only spacing scale: `xxs` 2, `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24,
`xxl` 32, plus `screen` (20dp minimum horizontal margin), `card` (16dp internal padding) and
`touchTarget` (48dp minimum). Radii: `radiusCard` 20, `radiusRow` 16, `radiusField` 16,
`radiusSheet` 28.

### Type

`MmType`: `screenTitle`, `sectionTitle`, `body`, `label`, `caption`, and the amount hierarchy
`amountHero` > `amountLarge` > `amountRow`. Amounts are always the most prominent element in
their group.

## Components (`ui/components/`)

**`MmCore.kt`** - `MmCard`, `MmPlainCard`, `MmSectionHeader`, `MmIconBadge`, `MmPill`, `MmChip`,
`MmSegmentedControl`, `MmSwitchRow`, `MmProgressBar`, `MmInfoRow` (renders nothing when the value
is meaningless, so screens never print "—"), `MmInfoGroup`, `MmEmptyState`, `MmLoadingState`,
`MmErrorState`, `MmQuickAction`, `MmBottomSheet`, `MmStatTile`.

**`MmFields.kt`** - `MmTextField`, `MmAmountField` (₹ prefix, numeric keyboard, 2-decimal clamp),
`MmPickerField` + `MmPickerSheet` (searchable single-choice bottom sheet), `MmDateField`
(platform date/time pickers, never typed strings), `MmStepper`, `MmSearchField`.

**`MmTransactionRow.kt`** - THE canonical transaction row used by every transaction list:

```
[icon]  Merchant            ₹1,250
        Category • Date
```

The title is single-line ellipsised and the amount column is fixed width, so a long merchant name
can never wrap unpredictably or push the amount off-screen. Also exposes `MmPeriodHeader`,
`MmLegendDot`, `MmStatTile` and `mmCategoryTint` (stable per-name tint, lifted in dark mode).

**`MmAttachment.kt`** - receipt thumbnail, viewer and camera/gallery pickers.

**`AppChrome.kt`** - `MoneyManagerTopBar`: back + title + optional subtitle + a small trailing
action row. Deliberately shallow so toolbars are never overcrowded.

**`MoneyFormat.kt`** - the only place amounts are formatted.

## Rules for a new screen

1. `Column` with `MmColors.background`, then `MoneyManagerTopBar`.
2. Pull every padding/gap from `MmSpacing`; do not invent values.
3. Group content into `MmCard`s only where grouping helps - not every value needs a card.
4. Any transaction list uses `MmTransactionRow` and navigates to `Destination.TransactionDetail`.
5. Every async screen has a loading, empty and error state.
6. Every action gives feedback (snackbar, inline error, or navigation).
7. Hide optional fields with no value rather than showing a dash; never delete the underlying data.
8. Verify the screen in light mode, dark mode and at a small width before calling it done.
