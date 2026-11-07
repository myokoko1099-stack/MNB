# MNB Phase 2F — Tab Lifecycle & WebView Resilience

Base: `main` @ 3a54087 (Phase 2A–2E). Status: **CODE WRITTEN, NOT BUILT, NOT TESTED.**
The Android SDK and network access were not available where this was written, so no Gradle build or
unit test has run. Before calling 2F done run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
Nothing was committed, pushed or opened as a PR.

## 1. Targeted audit (files read)
MainActivity, BrowserScreen, WebViewController, WebSessionStore, TabManager/TabPersistence/TabSessionFiles,
BrowserLibrary/PageRules/LibraryText, UrlPolicy, NavigationController, BrowserStateManager,
MnbDatabase (+ migrations), BrowserData{Entities,Daos,Repository}, UiPreferences, TabSwitcher,
BottomMenu/PremiumSettings (skimmed), WebViewSecurityGuardTest, TabManagerTest, MnbMigrationsTest.
Translation, Gemini BYOK, Reader and the WebView security settings were not changed.

What is solid: UrlPolicy (https only, http upgraded, bad schemes blocked); SSL errors always cancelled;
permissions/geolocation denied; one secured WebView construction path; Room migration 3→4 is additive;
tab list writes are coalesced on one IO thread; library calls are single-threaded and failure-safe.

## 2. Gap analysis
**Bugs / crash risks**
- G1 (high) No `onRenderProcessGone`. A renderer crash or system kill of any WebView takes the whole app
  down (default returns false). Worse with up to 20 live WebViews. → **fixed in 2F**
- G2 (high) Every tab keeps a live WebView (max 20): no unloading under memory pressure; no
  `onTrimMemory`. → **fixed in 2F**
- G3 (medium) A WebView whose renderer keeps crashing would loop if it were simply rebuilt. → **guarded in 2F**

**Incomplete features**
- G4 `browser_history` grows without limit (only the newest 200 are shown, nothing is pruned).
- G5 No history search or date grouping; bookmarks cannot be edited or foldered.
- G6 No downloads (`DownloadListener`), no file chooser, no find-in-page, no share, no desktop mode.
- G7 "Open link in new tab" (long-press) is not available; multiple windows disabled by design.

**Known limitations (documented earlier)**
- Error overlay is not remembered per tab. Cookies are shared between tabs (no isolation).
- Phase 01 `web_session.bin` is no longer read.

**Architecture / integration**
- G8 `isNewTab` compares the page address with `UrlPolicy.HOME_URL`: a user who really opens
  https://duckduckgo.com/ sees the new-tab page instead of the site, and every new tab makes a request to it.
- G9 `exportSchema = true` but no `app/schemas` folder is in git, and no test migrates real v3 data to v4
  (`MnbMigrationsTest` only checks that the chain has no gap).
- G10 No `UiPreferences` test; no `WebViewController` behaviour test (needs a device or Robolectric).

**UI/UX**
- Tab switcher has no "close all", undo, reordering, or thumbnails. Search bar has no suggestions.

## 3. Chosen phase and why
**Phase 2F — Tab Lifecycle & WebView Resilience** (G1–G3). Highest severity (app crash), smallest
regression surface (one class plus two small hooks), builds on the per-tab session files from 2B,
needs no schema change, and does not touch Translation, Gemini, Reader or the security settings.

## 4. What was implemented
New: `web/LiveTabPolicy.kt` (`LiveTabPolicy`, `CrashLoopGuard`, pure Kotlin).
Changed:
- `web/WebViewController.kt`
  - `onRenderProcessGone` returns true; the dead WebView is detached/destroyed (never saved).
    Active tab → `recoveryTick` changes → the screen remounts it from its saved state.
    Background tab → rebuilt lazily when shown.
  - 2nd real crash of a tab within 60 s → no restore; the tab restarts on the home page and, once it
    has loaded, shows "This page keeps crashing" with Retry (retry address must pass `UrlPolicy`).
  - At most `maxLiveViews` (6) WebViews stay alive; least recently shown background tabs are unloaded
    (state saved first, kept in memory until reused so a fast switch-back cannot read an older file).
  - `trimBackgroundViews()` unloads all background tabs.
  - `closeTab`/`destroy` also forget the new per-tab data. New constructor params have defaults, so
    existing call sites are unchanged.
- `ui/BrowserScreen.kt`: the host is wrapped in `key(webController.recoveryTick)`.
- `MainActivity.kt`: `onTrimMemory` calls `trimBackgroundViews()` only for RUNNING_LOW/RUNNING_CRITICAL
  (not when the app merely goes to the background).
- `error/ErrorHandler.kt`: `renderGone(failingUrl)`.

Tests added: `web/LiveTabPolicyTest` (policy + crash guard), `error/ErrorHandlerRenderGoneTest`,
`web/WebViewResilienceGuardTest` (source guard, same style as `WebViewSecurityGuardTest`).
Existing `WebViewSecurityGuardTest` rules were kept in mind: one `WebView(context)`, `createWebView`
directly before `applySecureSettings`, `destroyView` before `storeFor`, no `handler.proceed`.

## 5. Not verified (needs a device)
1. Open 8+ tabs: only 6 stay live; switching to an unloaded tab restores its page and Back history.
2. Trigger a renderer kill (`adb shell am kill` is not enough; use chrome://crash-style test page or
   kill the WebView sandbox process): the app stays open and the active tab reloads.
3. Crash the same page twice within a minute: home page + "This page keeps crashing" + working Retry.
4. Low-memory simulation (`adb shell am send-trim-memory <pkg> RUNNING_LOW`): background tabs unload.
5. Rotate during recovery; Reader/Extraction on a tab that was unloaded and rebuilt.
Risk to watch: `key(recoveryTick)` rebuilds the FrameLayout host only after a crash; the WebView is
moved to the new host by `mount()`, which already detaches it from any old parent.

## 6. Suggested next phase (2G)
Pruning of `browser_history` (G4) with a repository test, exporting the Room schema JSON + a real
3→4 `MigrationTestHelper` test (G9), history search/date groups (G5), fix for G8, "open link in new tab" (G7).
