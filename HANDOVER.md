# HANDOVER — MNB Phase 2G

Written because the work stops at "code written, unverified". It was not triggered by the 85% usage rule.

## Done (written, not verified)
All five Phase 2G tasks (A pruning, B history search + date groups, C migration tests, D new-tab logic, E open link
in new tab) plus the Google-only cleanup. See PHASE-2G-REPORT.md for the design and the file list.

## Not done
- No build, no unit test run, no device test, no commit/push/PR.
- `app/schemas/.../4.json` and `3.json` are not in the repository; `MnbSchemaMigrationTest` skips until they are.
- Files not audited line by line: AddressBar.kt, ErrorHandler, PremiumSettings, UiPreferences, res/, docs.

## Next
1. In ~/MNB (clean main @ 2c34351): unzip the package, run `python3 tools/apply_phase_2g.py ~/MNB`.
2. Build and run the unit tests; fix compile errors and failing guard tests first.
3. Commit the schema JSON (4.json from the build, 3.json via tools/derive-v3-schema.py), re-run, confirm `MnbSchemaMigrationTest` passes.
4. Device tests listed in the report. Only then ask for commit / PR approval.

## Verification state
Build: not run. Unit tests: not run. Migration tests: written, not run. Device: not run.
