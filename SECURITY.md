# Security

## Supported Versions

Security fixes are backported to the latest published release only.

| Version | Supported |
|---|---|
| Latest release | :white_check_mark: |
| Older releases | :x: |

## Reporting a Vulnerability

Please do **not** file a public issue for security vulnerabilities. Instead email
the maintainers privately:

- **Email:** maintainers@localhost (replace with project contact before publishing)

You should receive an acknowledgment within 3 business days. If the report is a
valid vulnerability, we will coordinate a fix, release schedule, and disclosure.

## App security posture

- All money is held as `Long` paise inside Room; every mutation to balances flows
  through `AccountingEngine`/`AccountingService`. There is no direct
  balance-field write from UI code.
- No secrets, API keys, or credentials are committed to the repository.
- The app stores all data locally (Room); CSV import/export requires user action.
- Signed releases use a release keystore (`release.jks`) that is **never**
  committed. See `RELEASE_CHECKLIST.md` for setup.

## Self-signed / import safety

- CSV import parses with strict, validated parsing (OpenCSV) and rejected malformed
  rows are reported — never partially applied.
- Launcher icon, fonts, and assets are project-original or MIT/Apache-2.0 licensed
  (see `THIRD_PARTY_NOTICES.md`).

## HTTP / network

The app is fully offline. If a feature later introduces networking:

- require HTTPS,
- pin or carefully validate certificates,
- keep user data local by default.