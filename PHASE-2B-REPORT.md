# MNB Phase 2B — Multi-Tab Browser

Status: CODE WRITTEN, NOT YET BUILT OR TESTED. Run the build command below before calling Phase 2B done.

Build: ./gradlew :app:testDebugUnitTest :app:assembleDebug

## Design
- No schema change: Room stays at version 4. Tab list = Phase 2A `browser_tabs` table (BrowserTabEntity/BrowserTabDao).
  New repository method `replaceTabs` (one transaction).
- Per-tab WebView state (page + back/forward history) = one file per tab in filesDir/tab_sessions/<tabId>.bin
  (existing FileWebSessionStore + Parcel format, as Phase 01 did for one session).
- One WebView per tab (independent URL and history). One host FrameLayout in Compose; the previous WebView is
  detached before the next is attached; a WebView is destroyed only after detaching.
- Cookies/web storage are shared by all tabs (one app profile). Only history/page state is per tab. This is NOT cookie isolation.
- Background-tab events never touch the UI state; SSL cancel, permission deny, URL policy apply to every tab.
- Max 20 tabs (every tab keeps a live WebView).

## New files
app/src/main/java/com/mnb/browser/tabs/{TabModels,TabPersistence,TabManager,TabSessionFiles,TabDisplay}.kt
app/src/main/java/com/mnb/browser/ui/tabs/TabSwitcher.kt
app/src/test/java/com/mnb/browser/tabs/{TabManagerTest,TabDisplayTest,TabSessionFilesTest}.kt
app/src/test/java/com/mnb/browser/state/BrowserStateManagerTest.kt
app/src/test/java/com/mnb/browser/web/WebViewSecurityGuardTest.kt
app/src/test/java/com/mnb/browser/translation/db/RoomTabPersistenceTest.kt

## Modified files (full replacement)
MainActivity.kt, ui/BrowserScreen.kt, ui/premium/BottomMenu.kt, web/WebViewController.kt, web/WebSessionStore.kt,
state/BrowserStateManager.kt, translation/db/BrowserDataRepository.kt
Review with `git diff` after applying; unrelated lines should be unchanged.

## Not covered by unit tests (needs a device)
Real WebView back/forward independence, session restore of WebView history after process death, tab switcher look/animation,
Reader + Extraction on a non-first tab. Manual checklist:
1. Open 3 tabs, load different sites, navigate 2 pages in each; switch and check Back/Forward per tab.
2. Close the app from recents, reopen: tabs, active tab and each tab's Back history return.
3. Close all tabs one by one: a Home tab appears.
4. Extract + Reader on tab 2, then switch to tab 1.
5. Rotate the device, open an http:// link (must upgrade) and an SSL-broken site (must show error).

## Known limits
- Error overlay is not remembered per tab (dropped on switch).
- Old single-session file web_session.bin from Phase 01 is no longer read.
- No "open link in new tab" (multiple windows stay disabled by design).
