# Navigation test matrix

Every row must be checked with **both** the app-bar Back button and Android system Back.
One Back action must remove exactly one layer. Dashboard is only the fallback when there is
genuinely no previous destination.

| # | Flow | Back 1 | Back 2 | Back 3 |
|---|------|--------|--------|--------|
| 1 | Dashboard → Accounts → Account details → Edit account | Edit account → Account details | Account details → Accounts | Accounts → Dashboard |
| 2 | Dashboard → Bills → Edit bill | Edit bill → Bills | Bills → Dashboard | — |
| 3 | Bills → Mark as paid → payment dialog | Dialog closes | Bills | Dashboard |
| 4 | Accounts → Credit card → Edit credit card | Edit credit card → Credit card details | Credit card details → Accounts | Accounts → Dashboard |
| 5 | Credit card details → Transaction details → Edit transaction | Edit transaction → Transaction details | Transaction details → Credit card details | Credit card details → Accounts |
| 6 | Account details → Add transaction | Add transaction → Account details | Account details → Accounts | Accounts → Dashboard |
| 7 | Dashboard → Transaction details → Edit transaction | Edit transaction → Transaction details | Transaction details → Dashboard | — |
| 8 | Dashboard → Search → Transaction details | Transaction details → Search | Search → Dashboard | — |
| 9 | Dashboard → Spend summary → Category transactions → Transaction details | Transaction details → Category transactions | Category transactions → Spend summary | Spend summary → Dashboard |
| 10 | Dashboard → Balance (breakdown) → Transaction details | Transaction details → Balance breakdown | Balance breakdown → Dashboard | — |
| 11 | Dashboard → Net worth breakdown | Breakdown → Dashboard | — | — |
| 12 | Dashboard → Account selection → Add transaction → Save | Add transaction and the chooser are removed; returns to Dashboard | — | — |
| 13 | Dashboard → Accounts → Edit account → Save | Returns to Account details (the caller) | Account details → Accounts | Accounts → Dashboard |
| 14 | Account details → Credit card → Edit credit card → Delete | Delete pops the editor **and** the now-stale card detail; lands on Accounts | — | — |

## Save / delete behaviour

- Saving any editor pops exactly one route back to its caller.
- Deleting the entity behind the current screen returns to a valid caller (it must not leave a
  detail screen pointing at a deleted id). A deleted **account/credit card** returns to the
  Accounts list and pops any stale detail in one step
  (`MoneyManagerNavHost.returnToAccountsAfterDelete()`); a deleted **transaction** returns to the
  list it was opened from, whose `Flow` has already dropped the row.
- Rapid double-taps on Save must not push/pop twice (saving guards are in place in the account,
  bill, credit-card, **transfer** and add-transaction forms).
- Top-level drawer navigation may reset the stack to Dashboard + selected section; it must not
  restore a nested editor from saved state.

## Automated coverage

`app/src/test/java/com/moneymanager/app/ui/navigation/NavigationRouteTest.kt` drives a **real
`NavController`** over a graph built from the same `Destination` objects the app uses (Robolectric).
It asserts, without a device:

- every parameterised route (`account_detail/{id}`, `credit_card_form?accountId=-1`,
  `add_transaction/{entryPoint}?accountId=-1`, `bill_form?billId&billType`, …) is registered and
  resolves;
- single-layer Back pops exactly one entry (`edit → detail → accounts → dashboard`);
- a rapid triple navigation with `launchSingleTop` leaves the route on the stack once;
- deleting an account pops the stale detail and lands on Accounts;
- drawer navigation keeps Dashboard as the Back target.

Run it with:

```bash
./gradlew :app:testDebugUnitTest --tests "com.moneymanager.app.ui.navigation.NavigationRouteTest"
```

## Manual verification commands

```bash
# While the device is connected:
adb shell input keyevent KEYCODE_BACK   # system Back
# Then repeat the same flow with the app-bar Back button.
```

The rows above can additionally be walked by hand on an emulator/device; the automated test covers
the routing contract that each flow depends on but does not render the screens.
