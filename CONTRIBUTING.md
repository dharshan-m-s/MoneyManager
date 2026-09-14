# Contributing

## Licensing

- All **original** code, artwork, and documentation are licensed under the
  [MIT License](LICENSE). By contributing, you agree that your contributions
  become part of this project and are licensed accordingly.
- **Third-party** components keep their own licenses (see
  `THIRD_PARTY_NOTICES.md`). Do not import code that is incompatible with a
  permissive MIT + proprietary-features project without checking.
- Do not relicense or reproduce material from the Moneyview product (or any
  other commercial money app). This project only *reads* the CSV export format
  of data the application owner already possesses.

## Privacy

- Never commit personal financial data. Test fixtures must use fabricated
  merchants, amounts, and account numbers.
- The `FullStatementHarnessTest` reads a private CSV only through the
  `MONEYVIEW_REFERENCE_CSV` environment variable; do not check that file in.

## Workflow

1. Open an issue first for non-trivial changes.
2. Branch from `main`: `git checkout -b topic/my-change`.
3. Keep changes focused; preserve existing migrations (never rewrite history of
   the Room migrations 1 -> 9; add new ones instead).
4. Verify locally before pushing:

   ```sh
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
   ```

5. Open a pull request; CI runs unit tests, lint, and a debug build.

## Style

- Kotlin: follow the existing file conventions (no comments unless needed,
  2-space indent, `Timber` for logging — not raw `Log` except in `ReleaseTree`).
- Money is stored in minor units (`Money`); never conflate rupees and paise.