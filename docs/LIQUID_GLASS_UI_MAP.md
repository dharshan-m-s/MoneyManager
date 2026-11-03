# Compose UI and backdrop map

The modern Compose navigation is the active application UI. `ModernNavHost` owns route-to-screen
mapping. `ModernRoot` owns the live page recording used by the floating dock and transient dock
overlays.

## Primary destinations

| Destination | Screen entry | Layout and scrolling | Deliberate glass surfaces |
| --- | --- | --- | --- |
| Home | `DashboardScreen` | One `LazyColumn`; safe top inset and dock clearance are applied to scroll content. | Solid indigo balance hero and gradient income/spend cards; transaction rows and charts remain native. |
| Accounts | `AccountsScreen` | Stationary collapsible header, balance overview and type tabs above a scrolling account list. Search fades away as the header compacts; the overview contracts and moves upward to free list space. Supports All, bank, debit/credit card, wallet, prepaid, cash, investment and loan types. | Entry-keyed sampled balance overview and header materials. Dense account rows stay native. |
| Activity | `TransactionsScreen` | Fixed, collapsible top bar and flow summary overlay the date-grouped ledger. Search and filter chips collapse on scroll; rows move behind both glass surfaces. | Sampled-glass top bar; translucent, locally tinted flow-summary glass avoids stale cross-page samples. Transaction rows stay native. |
| Cash | `CashScreen` | Stationary measured header and balance panel; monthly metrics and ledger scroll beneath them. | Theme-tinted balance panel and top chrome. Ledger rows stay native. |
| Bills | `BillsScreen` | Stationary shared header/search with a scrolling bill list; the screen disables Scaffold's duplicate system insets. | Search control and transient payment/action surfaces. Bill data remains readable. |
| Budget | `BudgetScreen` | Stationary collapsible header with a vertically scrolling monthly budget overview and six-month spend trend. | Native budget overview and sharp progress graphics. |
| More | `ModernMorePopover` in `ModernRoot` | Floating two-column menu above the dock; selecting an entry navigates to Bills, Cash, Income, Categories, Reimbursements or Settings. | Shared live-page glass overlay. |

## Other routes

`ModernNavHost` maps account details and forms, transaction details and forms, bill forms, transfer,
search, import, spending summary and category screens, reimbursements, settings, feedback, updates,
open-source information and About screens to `ui/modern/screens`. These detail flows retain the
same page background and system-bar policy; the dock is hidden where the route is not in
`DOCK_ROUTES`.

Account details use one sampled-glass header panel that extends from the top inset to below the
account artwork. Payment-card artwork uses issuer-colored dimensional gradients and restrained
edge reflections, while statement and balance details stay sharp and readable.

## Rendering ownership

- `ModernRoot` owns the opaque page viewport and a live `LayerBackdrop` for dock/overlay sampling.
- Page-owned glass surfaces sample a throttled, entry-keyed still of their own page. A glass
  surface subtree is omitted from its still so labels cannot be sampled back under themselves.
  A physical Android 16 test confirmed that page surfaces must not sample the live page
  RenderNode. The dock and popovers, as sibling layers, sample the completed live page.
- `DrawBackdropNode` suppresses sampling during source capture to keep the RenderNode graph acyclic.
- A renderer failure in one sampled surface should affect that surface only. A failure while
  recording the shared source may switch all its consumers to the stable material fallback.
- `MoneyManagerTopBar`, `modernTopInsets`, `overlayWithoutLayoutSpace` and `LocalDockContentPadding`
  provide shared chrome, safe top insets, measured overlay reservations and bottom scroll clearance.

## Visual QA focus

Prioritize cold launch and repeated Home → Accounts → Activity → More → Home navigation. Inspect
first-frame headers and overview panels, top safe insets, the live dock over both light and dark
pages, and final-row visibility above the dock. Runtime appearance must be checked on a phone.
