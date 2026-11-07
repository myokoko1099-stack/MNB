# MNB Phase 2G — Report

**Status: code written, NOT built, NOT tested, NOT committed.**
Nothing here is verified by a build or a test run. The sandbox that produced it had no network, no
Gradle and no Android SDK; the code was written against `main` @ `2c34351` as read through GitHub.
Do not treat any item below as "done" until `./gradlew testDebugUnitTest` (or CI) passes.

## What was changed (by task)

| Task | Change |
|---|---|
| Google only | `HOME_URL` is now `https://www.google.com/` (only a crash-fallback address; Phase 2F keeps `view.loadUrl(UrlPolicy.HOME_URL)`). Search was already Google. New guard test `NoDuckDuckGoGuardTest`. |
| D. New Tab | New `UrlPolicy.NEW_TAB_URL` (`https://mnb.invalid/new-tab`, can never resolve) is the app's own page. A new tab loads **nothing**; Home loads `about:blank`; `about:blank` is reported to the tab list as `NEW_TAB_URL`. `isNewTab` (BrowserScreen, PageRules, TabDisplay) now means "no address / about:blank / NEW_TAB_URL", so a real Google page shows the Google site, not the New Tab UI. Extract is disabled on the New Tab page. Tabs stored by older versions on the old home page are replaced by fresh new tabs (new id, so the old saved page state is dropped). |
| A. History pruning | `BrowserDataRepository.pruneHistory(now, maxAge = 90 days, maxEntries = 2000)` in one transaction (DAO: `deleteOlderThan`, `trimToNewest`). `BrowserLibrary` runs it inside the same IO task as the first stored visit, then every 25 stored visits (`PRUNE_EVERY`). No schema change (`visitedAtMillis` was already indexed). |
| B. History UI | Search box (DB-level `LIKE`, `%` `_` `\` escaped, newest answer wins) + date groups Today / Yesterday / Last 7 days / Last 30 days / Older (`HistoryGrouping`, `java.util.Calendar`, minSdk 24 safe). Search and groups work together. |
| C. Migration check | `MnbBrowserMigrationTest`: real `MIGRATION_3_4` on SQLite with existing data (always runs). `MnbSchemaMigrationTest`: `MigrationTestHelper` against schema JSON (**skipped until 3.json and 4.json are committed**, see below). DB version stays **4** (no new migration needed). |
| E. Open in new tab | Long press on a link (also a link around an image) → dialog → `TabManager.newTab(link)`. Link checked by `UrlPolicy.linkTarget` (https only, http upgraded, real host, no spaces, max 4096 chars). Tab limit respected (button disabled + message). WebView security settings untouched. |

## How to apply (Termux)

```
pkg install python unzip
cd ~/MNB
git status                      # should be clean, on main @ 2c34351
unzip -o /sdcard/Download/phase-2g.zip -d ~/MNB
python3 tools/apply_phase_2g.py ~/MNB
git status
```
`apply_phase_2g.py` checks every edit first and writes **nothing** if any piece of text does not match
exactly once; it then lists what did not match. `git diff` shows the result. No commit/push was made.

## Schema JSON (needed for `MnbSchemaMigrationTest`)

1. Build once (CI or `./gradlew testDebugUnitTest`). KSP writes
   `app/schemas/com.mnb.browser.translation.db.MnbDatabase/4.json`. Check `.gitignore` does not ignore `schemas/`.
2. `python3 tools/derive-v3-schema.py app/schemas/com.mnb.browser.translation.db.MnbDatabase/4.json app/schemas/com.mnb.browser.translation.db.MnbDatabase/3.json`
   (3.json = 4.json without the three browser tables; Room cannot generate 3.json because only the current version is built.)
3. Commit both, run the tests again. The test report must show `MnbSchemaMigrationTest` as **passed**, not skipped.

## Files

New: `library/HistoryView.kt`, `ui/LinkDialog.kt`, `tools/apply_phase_2g.py`, `tools/derive-v3-schema.py`, tests
`HistoryGroupingTest`, `BrowserHistoryManagementTest`, `MnbBrowserMigrationTest`, `MnbSchemaMigrationTest`,
`NewTabAndLinkTest`, `TabManagerNewTabTest`, `NewTabWebViewGuardTest`, `NoDuckDuckGoGuardTest`.
Replaced whole: `navigation/NavigationController.kt` (only `home()` changed), `tabs/TabDisplay.kt`.
Edited by script: `UrlPolicy`, `PageRules`, `TabManager`, `WebViewController`, `BrowserScreen`, `BrowserLibrary`,
`BrowserDataDaos`, `BrowserDataRepository`, `LibraryScreens`, `app/build.gradle.kts`, and the tests
`PageRulesTest`, `BrowserLibraryTest`, `TabDisplayTest`, `UrlPolicyTest` (they assumed HOME_URL was the new-tab page).

Not touched: translation architecture, Gemini BYOK, Novel Reader, chapter extraction, translation tables,
WebView security settings, Phase 2F crash recovery, `LiveTabPolicy`.

## Audit notes

- Phase 2F: read only; no integration bug found besides the new-tab/HOME_URL coupling above. 2F itself was never built either.
- DuckDuckGo left on purpose, only to recognise OLD data: `UrlPolicy.LEGACY_HOME_URL` (old saved tabs) and
  `DefaultShortcuts.LEGACY_DEFAULTS` (old default shortcut list). Typing a DuckDuckGo address still opens it (direct entry is kept).
- Not read line by line (GitHub code search was unavailable): `AddressBar.kt`, `ErrorHandler`, `PremiumSettings`, `UiPreferences`,
  `res/`, `README`/docs. `NoDuckDuckGoGuardTest` will fail on the first run if any of them mention it.
- Guard tests `WebViewSecurityGuardTest` / `WebViewResilienceGuardTest` were read and the edits were shaped to keep their
  checks, but never run.

## Known risks (all unverified)

1. Compile errors are possible anywhere (no compiler was available).
2. `app/build.gradle.kts`: `sourceSets["test"].assets.srcDir("$projectDir/schemas")` plus a `mustRunAfter` on KSP. If Gradle
   reports a missing task dependency, remove those two blocks; `MnbSchemaMigrationTest` then fails to find its assets, so also delete or `@Ignore` it.
3. `androidx.test:core:1.5.0` and `room-testing:2.6.1` are new test dependencies (need network on first build).
4. `MnbSchemaMigrationTest` uses `MigrationTestHelper` under Robolectric (not on a device); `3.json` is derived, its hash is a placeholder.
5. `BrowserLibraryTest.repositoryIsNeverTouchedOnTheCallingThread` expects 4 queued tasks; pruning was kept inside the existing task for that reason.

## Device tests still needed (nothing was run on a device)

- New tab: open several tabs, check that no network request is made (airplane mode: no error page); the Home button shows the New Tab UI.
- Type a search: Google results (not the New Tab UI). Open `google.com` directly: the Google site. A DuckDuckGo address typed by hand still opens.
- Update from the previous build with tabs open: old home tabs turn into New Tab pages; other tabs and their back/forward history stay.
- Long press: plain link, image link, `javascript:` link (nothing shown), at 20 tabs (message, button disabled), rotate with the dialog open.
- History: search (also `%`, `_`, Burmese text), date groups with search on, delete while searching, clear all, rotate while searching.
- Crash recovery (2F) still works; reader/extract/translation unaffected; bookmarks and shortcuts intact.
