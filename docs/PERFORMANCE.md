# Performance notes (Section 14)

This records what was profiled, what was changed, and the reproducible measurements. Numbers come
from `app/src/test/java/com/moneymanager/app/ui/accountdetail/LedgerPerformanceTest.kt`, which runs
on the JVM through Robolectric (no device required) and prints its own timings so they can be
re-checked:

```bash
./gradlew :app:testDebugUnitTest --tests "com.moneymanager.app.ui.accountdetail.LedgerPerformanceTest"
```

## Baseline and change

**Account detail grouping.** `AccountDetailViewModel.stateFor` computes per-month / per-cycle
income and expense from the account's **full** history (so a subtotal never changes when fewer rows
are paged in) while the *displayed rows* come from a bounded page. The original shape re-scanned the
page once per group — O(groups x page), which becomes O(n²) once the user scrolls until the whole
history is paged in.

The rewrite indexes each side once — O(full + page) — using two hash maps and then sorts. Output is
byte-for-byte identical (the test asserts equal labels, sums and row id order).

Measured on 6,000 rows across 33 months, with the full history as the page (the worst realistic
state after "Load more"):

| shape | groups | page | time |
| --- | --- | --- | --- |
| before (nested scan) | 33 | 6,000 | ~19 ms |
| after (single pass) | 33 | 6,000 | ~4 ms |

Whole-account read of the same 6,000 rows: ~40 ms (supported by the `occurredAtEpochMillis` and
`accountId` indices).

These are JVM/Robolectric figures, not device frame timings; they establish the relative improvement
and guard against a quadratic regression, and are not presented as on-device FPS numbers.

## Other Section 14 measures

- **Room stays the source of truth.** No screen keeps a second unbounded copy of the ledger.
  Dashboard and breakdown read only the visible date range (`observeInRange`); the transactions list
  is bounded (`observeRecent`); search runs a SQL query (`searchTransactions`) rather than scanning
  an in-memory list; export reads `findAllForExport` so limiting a *view* never hides records from
  search or export.
- **Stable lazy keys.** Every list uses `key = { it.id }` (transactions, categories, months, bills,
  search matches), so recomposition and scrolling do not thrash item identity.
- **Image downsampling.** Receipt and category images are decoded with `inJustDecodeBounds` +
  `inSampleSize` so only a display-sized bitmap is allocated.
- **Cache hygiene.** The update cache policy (`.part` staging, pruning stale APKs and abandoned
  partials, never touching backups/documents) is documented in `UPDATER_ARCHITECTURE.md` and tested
  by `UpdateCachePolicyTest`.
- **No main-thread work.** No `runBlocking`, `allowMainThreadQueries`, `GlobalScope` or `Thread.sleep`
  appears in production code; database, file and hashing work run on `Dispatchers.IO`/`Default` or
  `viewModelScope`.

## Deliberately not done

- Replacing the full-history aggregate scan with dedicated per-month/per-cycle SQL `SUM` queries
  would shave more, but risks drifting from the exact `includeInStatistics` + signed
  credit/debit semantics the app must preserve. The single-pass Kotlin rewrite is safe and verified;
  the SQL-aggregate route is left as a future, separately-tested optimization.
