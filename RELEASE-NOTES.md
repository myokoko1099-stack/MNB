# Release notes - Phase 06 (BYOK direct Gemini)

## Phase 06 features

- 06-A1 Secure storage core: AES-256-GCM with an Android Keystore key.
- 06-A2 Gemini API key settings UI (save, update, delete, show/hide).
- 06-A3 Integration and security hardening (read-only key provider, state locking, lifecycle clearing).
- 06-A4 Tests and documentation for the above.
- 06-B1 Direct Gemini provider (request, response parsing, fixed error codes).
- 06-B2 Translation engine integration (`GeminiTranslationProvider`, reader wiring, main-thread guard).
- 06-B3 Error handling, timeouts, rate-limit handling, bounded retry.
- 06-B4 Final audit, documentation, regression guards and the background-worker fix below.

## Completed work in 06-B4

- Fixed: background translation did not work when Android started the process only to run the
  worker (for example after the app was killed). The provider wiring lived in `MainActivity.onCreate`;
  it now lives in the new `MnbApplication`, which runs at every process start.
- `MnbApplicationTest` (17 tests) covers the process-start wiring; `ProjectSecretsGuardTest` (8 tests)
  scans the whole project for secrets and risky manifest settings.
- `.gitignore` now excludes keystores and credential files.
- Documentation: README, SECURITY, ARCHITECTURE, TESTING, RELEASE-NOTES, RELEASE-CHECKLIST, final reports.
- Stale code comments corrected (`TranslationWorkEnvironment`).

## Known limitations

- The 06-B4 fix is proven on the JVM only. WorkManager restarting a killed process must be checked on
  a device (RELEASE-CHECKLIST.md).
- The model is the default of `GeminiConfig` (`gemini-2.5-flash`); no model selector exists.
- Release builds are unsigned, not minified (R8 off) and `versionName` is still `0.2.0-phase02`.
- CI runs unit tests and a debug build only (no lint, no release build).
- The key exists as an unwipeable `String` while a request is built (SECURITY.md).

## Release readiness

Not declared ready by this document. It depends on a green GitHub Actions run for the 06-B4 branch,
the device check above, and the owner's signing/versioning decisions. See PHASE-06-FINAL-REPORT.md.
