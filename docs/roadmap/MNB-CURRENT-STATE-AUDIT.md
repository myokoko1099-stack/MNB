# MNB — Current State Audit

Audit ရက်စွဲ: 2026-11-08 · Repository: `myokoko1099-stack/MNB` · `main` HEAD: `c67a45e` (Merge PR #36)
ဤစာတမ်းသည် Audit သာဖြစ်သည်။ Source Code၊ Build၊ Test၊ Commit မလုပ်ပါ။ GitHub ကို ဖတ်ရုံသာ ဖတ်ခဲ့သည်။

## 0. Evidence Tag များ (စာတမ်းတစ်ခုလုံးတွင် သုံးသည်)

| Tag | အဓိပ္ပာယ် |
|---|---|
| **[C]** | main HEAD ရှိ Source Code ကို ဤ Audit တွင် တိုက်ရိုက်ဖတ်ပြီး အတည်ပြုထား |
| **[D]** | Documentation / Report တွင်သာ ရေးထား။ Code ကို ဤ Audit တွင် မဖတ်ရသေး |
| **[CI]** | GitHub Actions `build` (testDebugUnitTest + assembleDebug) အောင်မြင်ကြောင်း PR check run ဖြင့် မြင်ရ |
| **[U]** | မစစ်ဆေးရသေး / Device Test မလုပ်ရသေး / အထောက်အထားမရှိ |
| **[I]** | ဤ Audit ၏ ခန့်မှန်းချက် (အတည်မပြု) |

အရေးကြီး: CI အစိမ်း = "Unit Test (Robolectric) + Debug APK build အောင်သည်" ဟုသာ ဆိုလိုသည်။ Device Behavior ကို မသက်သေပြပါ။

## 1. Audit Coverage (ရိုးရိုးသားသား)

**Code ကို တိုက်ရိုက်ဖတ်ခဲ့သော ဖိုင်များ [C]:** `WebViewController.kt`, `ExtractionScript.kt`, `ExtractionController.kt`, `BrowserScreen.kt`, `MnbDatabase.kt` (+`MnbMigrations`), `DefaultShortcuts.kt`, `ReaderStateManager.kt`, `NovelRepository.kt`, `app/build.gradle.kts`, `.github/workflows/build.yml`။

**Directory စာရင်းသာ ဖတ်ခဲ့ (ဖိုင်အတွင်း မဖတ်ရ):** `extraction/`, `reader/`, `web/`, `library/`, `translation/` (+ sub-packages), `ui/`, `ui/premium/`, `settings/`, `translation/db/`, `translation/reader/`။

**Documentation ဖတ်ခဲ့:** README, ARCHITECTURE, SECURITY, TESTING, RELEASE-NOTES, RELEASE-CHECKLIST, PHASE-06-FINAL-REPORT, FINAL-TEST-REPORT, PHASE-2B/2F/2G-REPORT, CHANGES_06-C1, SESSION-06-B1-REPORT, CHANGED_FILES, HANDOVER (Phase 2G), docs/phase-06-a/PHASE-06-B-INTEGRATION-NOTES.md။

**မဖတ်ရသေး:** I10-D-QA-REPORT, PHASE-06-A-FINAL-REPORT, PHASE-06-A-TEST-INVENTORY, CHANGES_I10-C1/C2, docs/phase-06-a/{ARCHITECTURE,SECURITY}, THIRD_PARTY_NOTICES, PR description/body များ (PR list တွင် body မယူခဲ့ပါ), TabManager, UrlPolicy, NavigationController, ReaderScreen, Chunking/Memory/Quality source, Test file အများစု။

ထို့ကြောင့် Layer တစ်ချို့ကို "[D] — Documentation အရ" ဟုသာ သတ်မှတ်ထားသည်။ Roadmap Phase R-00 တွင် ကျန်သည့်ဖိုင်များကို အပြည့်အစုံ စစ်ရန် Task ထည့်ထားသည်။

## 2. GitHub History

### 2.1 အခြေခံအချက်များ [C]
- `main` HEAD = `c67a45e` (Merge PR #36, 2026-11-08T00:39Z)။
- PR #1–#36 အားလုံး merged။ Open PR မရှိ။
- အလုပ်အားလုံး 2026-11-02 မှ 11-08 အတွင်း ဖြစ်သည်။
- Branch ၄၂ ခု ကျန်နေသည် (merge ပြီးသော branch များ မဖျက်ရသေး)။
- PR မပါဘဲ `main` သို့ တိုက်ရိုက် commit: Phase 01 upload၊ Phase 02 extraction (`cec1e73`, `4d5c196`, `61dda2a`, `97cb889`)။

### 2.2 PR #1–#36 အပြည့်အစုံ (title + branch ကို GitHub မှ ဖတ်ထား [C])

| PR | Title (အတိုချုပ်) | Branch | Merged | မူလ Phase အမည် (သတ်မှတ်ချက်) |
|---|---|---|---|---|
| 1 | Phase 03 Part 1: core novel reader + saved position | phase-03-part-1-core-reader | 11-03 | Phase 03 Part 1 |
| 2 | Phase 03 Part 2: font/margin/spacing/theme | phase-03-part-2-reader-v2 | 11-03 | Phase 03 Part 2 |
| 3 | Fix extraction noveldex final | fix-extraction-noveldex-final | 11-03 | Phase 02 ပြင်ဆင်မှု (Phase အမည်မပါ) |
| 4 | Phase 04-I1 Translation Core Foundation | phase-04-i1-translation-core | 11-03 | 04-I1 |
| 5 | 04-I2 Translation Data Models | phase-04-i2-data-models | 11-03 | 04-I2 |
| 6 | 04-I3 Local Translation Database | phase-04-i3-local-database | 11-03 | 04-I3 |
| 7 | 04-I4 Mock Provider Integration | phase-04-i4-mock-provider-integration | 11-03 | 04-I4 |
| 8 | 04-I5 Chunking Engine | phase-04-i5-chunking-engine | 11-03 | 04-I5 |
| 9 | 04-I6 Context Memory | phase-04-i6-context-memory | 11-03 | 04-I6 |
| 10 | 04-I7 Quality Checker | phase-04-i7-quality-checker | 11-04 | 04-I7 |
| 11 | 04-I8 Instant Translation | phase-04-i8-instant-translation | 11-04 | 04-I8 |
| 12 | Background translation queue | phase-04-i9-background-translation | 11-04 | 04-I9 |
| 13 | Priority system | phase-04-i9-b-priority-management | 11-04 | 04-I9-B |
| 14 | Dynamic priority management | phase-04-i9-b2-dynamic-priority | 11-04 | 04-I9-B2 |
| 15 | Reader translation state architecture | phase-04-i10-a-reader-integration | 11-04 | 04-I10-A |
| 16 | Translation display UI | phase-04-i10-b-translation-display | 11-04 | 04-I10-B |
| 17 | Instant translation pipeline integration | phase-04-i10-c1-instant-pipeline | 11-04 | 04-I10-C1 |
| 18 | 04-I10-C2 background pipeline integration | phase-04-i10-c2-background-pipeline | 11-04 | 04-I10-C2 |
| 19 | Worker provider guard + shared chapter lock (test) | phase-04-i10-d-qa | 11-04 | 04-I10-D |
| 20 | 06-A1 Secure API key storage (Keystore AES-GCM) | phase-06-a1-secure-storage | 11-05 | 06-A1 |
| 21 | 06-A2 Gemini API key settings UI | phase-06-a2-gemini-key-settings | 11-05 | 06-A2 |
| 22 | 06-A3 Secure storage integration/hardening | phase-06-a3-secure-storage-hardening | 11-05 | 06-A3 |
| 23 | 06-A4 tests + docs | phase-06-a4-tests-docs | 11-05 | 06-A4 |
| 24 | Direct Gemini provider core | phase-06-b1-gemini-provider-core | 11-05 | 06-B1 |
| 25 | 06-B2 Provider → translation engine | phase-06-b2-translation-engine-integration | 11-05 | 06-B2 |
| 26 | 06-B3 Error handling, timeout, rate limit | phase-06-b3-gemini-error-handling | 11-05 | 06-B3 |
| 27 | fix: install Gemini worker provider in MnbApplication | phase-06-b4-final-qa-release | 11-06 | 06-B4 (title တွင် Phase အမည်မပါ) |
| 28 | Add premium browser UI shell | ui-phase-1-premium-shell | 11-06 | "UI Phase 1" (သီးခြားနံပါတ်စနစ်) |
| 29 | Browser bookmarks, history, tab database | phase-2-tabs-bookmarks-history | 11-06 | Browser Phase 2A (title တွင် 2A မပါ) |
| 30 | Phase 2B multi-tab browser | phase-2b-multi-webview-tabs | 11-06 | 2B |
| 31 | Phase 2C Google search, shortcuts home | phase-2c-home-search | 11-06 | 2C |
| 32 | Polish settings and bottom menu | phase-2d-settings-bottom-menu | 11-06 | 2D (title တွင် 2D မပါ) |
| 33 | Add bookmarks and history | phase-2e-bookmarks-history | 11-07 | 2E |
| 34 | Phase 2F tab lifecycle & WebView resilience | phase-2f-tab-resilience | 11-07 | 2F |
| 35 | 06-C1 settings screen foundation | phase-06-c1-settings-screen-foundation | 11-07 | 06-C1 |
| **36** | **Complete phase 2G history and navigation** | **phase-06-c1-settings-screen-foundation** | 11-08 | **2G — Branch အမည်က 06-C1 အတိုင်း** |

### 2.3 အမည်ရောထွေးမှု / ဆန့်ကျင်မှု (အထောက်အထားပါ)

1. **PR #36 ↔ #35 Branch တူနေ။** #35 head=`82e8a7c`၊ #36 head=`d3736fb`၊ နှစ်ခုလုံး `phase-06-c1-settings-screen-foundation`။ #36 သည် Phase 2G (Browser) အလုပ်ဖြစ်သည် (2 commits: `6f6766f` 2G၊ `d3736fb` DuckDuckGo comment fix)။ ထို Branch ကို ယနေ့ကြည့်လျှင် 2G code ကိုညွှန်နေသဖြင့် "06-C1" ဟူသော နာမည်သည် Settings Screen ကို မညွှန်တော့။ `phase-2g-*` Branch မရှိ။
2. **"Phase 02" နှင့် "Phase 2A–2G" ရောထွေး။** Phase 02 = Website text extraction (11-03)။ Phase 2A–2G = Browser sub-phase (11-06 မှ)။ ဖိုင်အမည် `PHASE-2B-REPORT.md` နှင့် `PHASE-02` ကို ကြည့်ရုံဖြင့် ခွဲမရ။
3. **"Phase 2" Branch နှစ်ခု:** `phase-2-tabs-bookmarks-history` (PR #29 = 2A) နှင့် မူလ Phase 03 Part 2 တွင် branch `phase-03-part-2-reader-customization` နှင့် `...-reader-v2` နှစ်ခုရှိ (PR #2 သည် v2 ကိုသာ merge)။ `reader-customization` branch ကျန်နေ။
4. **Phase 06-C1 (Settings) သည် 2F နှင့် 2G ကြားတွင် Merge ဖြစ်** (#34 → #35 → #36)။ Browser Phase အစဉ်ကို 06 နံပါတ်က ဖြတ်ဝင်နေသည်။
5. **UI Phase 1 (PR #28)** သည် Phase 01–06 စနစ်နှင့်လည်း Browser 2A–2G စနစ်နှင့်လည်း မပါဘဲ တတိယ Label။
6. **PR မပါသော Branch များ:** `ci-extraction-probe`, `debug-extraction-noveldex`, `fix-extraction-noveldex` (PR #3 ၏ ရှေ့ပိုင်း debug), `phase-04-i10-c-pipeline-integration` (sha `03aee05` သည် PR #16 merge commit ကို ညွှန်နေ — I10-C ကို C1/C2 ခွဲပြီးနောက် အမည်ကျန်ရစ်သည့်ပုံ [I])။
7. **Phase 04 sub-label:** မူလဖော်ပြချက် "I1–I10" ဟု ရှိသော်လည်း တကယ်တွင် I9 သည် I9, I9-B, I9-B2 သုံးပိုင်း၊ I10 သည် A/B/C1/C2/D ပိုင်းဖြစ်သည်။
8. **Phase 05 ခွဲခြားမရ — အကြီးဆုံးကွာဟချက်။** PR၊ Branch၊ Report၊ Source ထဲတွင် "Phase 05" ဖိုင်/Commit မတွေ့ (GitHub code search တွင် `"Phase 05"` ရလဒ် 0၊ သို့သော် search ၏ `incomplete_results=true`)။ တစ်ခုတည်းသောရာဇဝင်အထောက်အထား = `PHASE-06-B-INTEGRATION-NOTES.md` : "`NB-Translation-Gateway` (Phase 05-B3) is a separate backend the app already uses. Direct Gemini is an alternative path"။ **[I]** Phase 05 = Gateway-ကိုအခြေခံသော ဘာသာပြန်စနစ် (05-A…05-B3)၊ ဤ Repo တွင် မပါ၊ ခုချိန် Direct BYOK (Phase 06) ကို အစားထိုးထား။ **UD-01 ✔ User အတည်ပြုပြီး (2026-11-08):** Phase 05 = Gateway၊ Phase 06 Direct BYOK က အစားထိုး၊ Gateway ပြန်မထည့် (ယခင် [I] ခန့်မှန်းချက်သည် ယခု User-confirmed)။ ဤ Repo တွင် Phase 05 ၏ Code/Doc အထောက်အထား မတွေ့ခြင်းမှာ မပြောင်းလဲ။

## 3. Documentation နှင့် Source Code ကွာဟချက်

| # | Documentation ပြောထားချက် | အမှန်တကယ် (Code/GitHub) | ဆုံးဖြတ်ချက် |
|---|---|---|---|
| G-1 | README: "In-app browser with navigation and session restore" | Multi-tab (max 20 tab၊ 6 live WebView)၊ Bookmarks၊ History၊ Shortcuts [C] | README စာသား မမှန်တော့ (Stale) |
| G-2 | RELEASE-NOTES / build.gradle: `versionName 0.2.0-phase02`, versionCode 1 | Code တွင်လည်း တူ [C] — ဗားရှင်း မမွမ်းမံထား | Release အတွက် ဗားရှင်းမရှိသေး |
| G-3 | PHASE-06-FINAL-REPORT: "No CI run yet for 06-B4" | PR #27 `build` = success (2026-11-06T05:18Z) [CI] | Report အဟောင်း — B4 CI ဖြတ်ပြီး (Device Check မပြီး) |
| G-4 | Root `HANDOVER.md` = "Phase 2G… code written, not built, not merged, `main @ 2c34351`" | 2G ကို PR #36 ဖြင့် merge ပြီး၊ CI success (run 42429677235) [CI] | HANDOVER အဟောင်း — ဤ Audit ၏ HANDOVER.md ဖြင့် အစားထိုးရန် |
| G-5 | PHASE-2G-REPORT: schema JSON (3.json/4.json) commit ရန်လို | `app/schemas` folder `main` တွင် မတွေ့ [C: path not found] | `MnbSchemaMigrationTest` ကျော်နေသည် (skip) [I] — CI အစိမ်းဖြစ်သော်လည်း Migration schema test မအလုပ်လုပ် |
| G-6 | CHANGES_06-C1: `SettingsScreen` ကို ဘယ်နေရာကမှ မခေါ်သေး | HEAD တွင် ခေါ်/မခေါ် မစစ်နိုင် (code search `incomplete_results`) | [U] Settings ၂ ခု ကိန်းဝပ်နေနိုင် (PremiumSettings + SettingsScreen) |
| G-7 | Phase 2B/2F/2G/06-C1/06-B1 Report များ "NOT BUILT, NOT TESTED" | နောက်ပိုင်း PR #27/#34/#35/#36 တွင် CI အစိမ်း [CI] | Unit Test အဆင့်သာ အတည်ပြုပြီး၊ Device Test မရှိ [U] |
| G-8 | RELEASE-CHECKLIST: build `green on exact commit` | #36 head `d3736fb` — success [CI] | ✔ Automated အပိုင်းတစ်ခု ပြည့်မီ |
| G-9 | `tools/apply_phase_2g.py`, `derive-v3-schema.py` | Repo ထဲတွင်ကျန်နေ [C: listing] | Temporary Tool — Repo clutter၊ Root တွင် CHANGED_FILES*.md, CHANGES_*.md, *-REPORT.md များစွာ |

## 4. Layer ၁၆ ခု — လက်ရှိအမှန်တကယ်အခြေအနေ

အဆင့်သတ်မှတ်ချက်: **DONE-CI** = Code ရှိ + CI Unit Test အစိမ်း (Device မစစ်)။ **PARTIAL** = တစ်စိတ်တစ်ပိုင်း။ **MISSING** = မရှိ။ "Completed" ဟု မသတ်ပါ — Device Verification မရှိသေး။

### L1 Android App Foundation — DONE-CI
- Kotlin 1.9.24၊ AGP 8.5.2၊ compile/target SDK 34၊ minSdk 24၊ Compose BOM 2024.06.00၊ Material3၊ Room 2.6.1 (KSP)၊ WorkManager 2.9.0 [C]။
- `MnbApplication` (06-B4) = process-wide wiring၊ `MainActivity` = UI wiring [D]။
- DI Framework မရှိ (လက်ဖြင့်ချိတ်)၊ Coroutines/OkHttp မသုံး (ရည်ရွယ်ချက်ရှိ — SESSION-06-B1 Decision 1) [C/D]။
- အားနည်းချက်: `targetSdk=34` — Store ထုတ်ဝေလျှင် လိုအပ်သော Target API ကို နောက်ဆုံးစစ်ရမည် (UD-09)။

### L2 Browser / WebView — DONE-CI, Device [U]
- Tab တစ်ခုလျှင် WebView တစ်ခု၊ Host `FrameLayout` တစ်ခုတည်းတွင် mount/detach [C]။
- Page lifecycle: `onPageStarted/Finished`, `doUpdateVisitedHistory`, error overlay (HTTP/SSL/Network) [C]။
- ကျန်: Downloads၊ File chooser၊ Find-in-page၊ Share၊ Desktop mode — **MISSING** (2F Gap G6) [D]။
- Cookies အားလုံး Tab များအကြား မျှဝေ [C comment]။

### L3 Navigation / Multi-Tab — DONE-CI, Device [U]
- Tab ≤ 20၊ Live WebView ≤ 6 (`LiveTabPolicy`)၊ `onTrimMemory`၊ `onRenderProcessGone` → `CrashLoopGuard` (60 s အတွင်း ၂ ကြိမ် → Home + Retry) [C]။
- Per-tab session file (`tab_sessions/<tabId>.bin`)၊ Room `browser_tabs` [D/C]။
- Long-press link → "Open in new tab" (`UrlPolicy.linkTarget`: https only, ≤4096) [C]။
- New Tab = `https://mnb.invalid/new-tab` ကို `about:blank` ဖြင့် ပြ (network မလို) [C/D]။
- Device Tests (2B/2F/2G Report ပါ စာရင်း) **တစ်ခုမှ မလုပ်ရသေး** [U]။
- ကျန်: Tab thumbnails/undo/reorder/close-all၊ Error overlay per-tab မမှတ် [D]။

### L4 Bookmarks / History / Settings — PARTIAL
- Bookmarks (add/remove/list၊ URL unique) [C schema]၊ Bookmark edit/folder **MISSING** [D]။
- History: pruning (90 ရက်/2000 entries)၊ Search (LIKE escaped)၊ Date groups [D/CI]။
- Settings: `PremiumSettings` (UI prefs + Gemini key section 06-A2) [D]။ `SettingsScreen` (06-C1, Section ၆ ခု၊ Theme သာအလုပ်လုပ်) — Wiring [U]။ Gemini Config/Translation Behavior = Placeholder [D]။

### L5 Novel Website Shortcuts — PARTIAL
- Bundled ၅ ခု: Wattpad, Royal Road, Wuxiaworld, Project Gutenberg, Standard Ebooks (Tile style offline)၊ user edit/delete/add ရ [C]။
- DuckDuckGo = Legacy data မှတ်သားရန်သာ (LEGACY_DEFAULTS, LEGACY_HOME_URL)၊ Default မဟုတ် [C/D]။ Google-only Search (2C/2G) + `NoDuckDuckGoGuardTest` [D/CI]။
- **Shortcut Group နှင့် Safe Mode ON/OFF — MISSING** (Code/Doc ထဲ အထောက်အထားမတွေ့) [U]။

### L6 Novel Text Extraction — PARTIAL (Vision နှင့် မကိုက်)
- `ExtractionScript` = Rendered DOM ကိုသာ ဖတ် (fetch မလုပ်)၊ Container ကို paragraph-length score ဖြင့်ရွေး (ancestor scoring)၊ Noise filter (tag/role/class regex)၊ hidden element skip၊ link-heavy line ဖယ်၊ zero-width char strip၊ Chapter title heuristic (Myanmar `အခန်း` ပါ)၊ ရလဒ် `{pageTitle, chapterTitle, url, paragraphs:[{t,h}]}` [C]။
- Trigger = **BottomBar "Extract" ခလုတ်** → `ExtractionPreviewScreen` → "Read" → `ReaderScreen` [C: BrowserScreen]။ 15 s timeout [C]။
- **အဓိကကန့်သတ်ချက်:** Paragraph တစ်ခုကို DOM Node သို့ပြန်ညွှန်းနိုင်သော ID/Reference မထုတ်ပေး (`t` စာသား + heading level သာ)၊ Extraction session/page token မရှိ၊ Chapter မဟုတ်သောစာမျက်နှာ ခွဲခြားမှု Heuristic သာ [C]။ ထို့ကြောင့် **In-page Replacement လုပ်၍မရသေး**။
- Compatibility Matrix (Site အလိုက်) မရှိ — Noveldex ဖြင့်သာ ပြင်ခဲ့ရာ (branch အမည်) [D/I]။

### L7 Core Novel Reader — PARTIAL
- `ReaderScreen` (Compose Full-screen overlay)၊ Position Repo (SharedPreferences KeyValue)၊ Settings Repo (font size, margin, paragraph spacing, theme) [D/C listing]။
- Browser bars ဖျောက် (Phase 03 Part 1 commit) [D]။
- **Chapter Navigation (Next/Prev) — အထောက်အထားမတွေ့ [U]**။ Reading Progress % [U]။
- Reader သည် Browser နှင့်သီးခြား Screen (WebView ထဲမဟုတ်) — Vision နှင့် ကွဲ။

### L8 Translation Engine — DONE-CI
- `TranslationService`, `TranslationProvider` (sync interface)၊ `TranslationRequest/Result/Error`, Language enum (EN/ZH/KO/JA → Myanmar)၊ Mock provider (Test only; `TranslationProviderGuard`/`WorkerGuard` ဖြင့်တားထား) [D/C listing]။
- Provider id/`isMock` guard၊ main-thread guard [D]။

### L9 Chunking / Context Memory / Quality — DONE-CI (Source မဖတ်ရ — [D])
- Chunking Engine (I5)၊ Context Memory ဇယား ၉ ခု: Character, Alias, Glossary, Relationship, Story, World, Style, Conflict, History (I6)၊ Quality Checker (I7) [C: MnbDatabase entities]။
- အရည်အသွေးအား Myanmar Output (Unicode vs Zawgyi၊ ကျန်ကျသော စာကြောင်း) အပေါ် Quality Checker က မည်မျှစစ်သည်ကို မဖတ်ရ [U]။

### L10 Background Translation — DONE-CI, Device [U]
- WorkManager `TranslationWorker`၊ Queue၊ Priority (I9-B)၊ Dynamic priority (I9-B2)၊ Shared chapter lock (I10-D)၊ `MnbApplication` တွင် provider ချိတ် (06-B4) [D]။
- **Device Check မလုပ်ရသေး** (Process kill နောက် Worker ပြန်စ) — RELEASE-CHECKLIST ပထမ Item [U]။

### L11 Gemini Direct BYOK — DONE-CI, Live API [U]
- `DirectGeminiProvider`, `GeminiConfig` (model `gemini-2.5-flash` — မှတ်ဉာဏ်မှရွေးထား၊ 2026-11-08 Web research အရ Official page က "limited to past users" ဟုဆို၊ Third-party က 2026-12-01 Reroute ကြေညာဟုဆို — Decision Log C.1၊ **Default ပြောင်းရန်လို**)၊ `HttpUrlConnectionGeminiClient` (HTTPS-only, no redirect, 15 s/90 s timeout, 2 MiB cap)၊ Retry (≤3, backoff 1–16 s)၊ Error ၁၇ မျိုး fixed message [D]။
- Header `x-goog-api-key` သာ [D]။
- အစစ် Gemini API ကို မခေါ်ဖူး (B1 Report) [U]။ Safety settings default (CONTENT_BLOCKED အန္တရာယ်) [D]။ Streaming မရှိ [D]။
- Gateway: Request Path ထဲ မပါ (README/ARCHITECTURE/SECURITY နှင့် `ReaderTranslationGateway` သည် Reader ၏ internal port ဟု ရှင်း) [D]။

### L12 Secure API Key Storage — DONE-CI, Real Keystore [U]
- AES-256-GCM, Keystore key alias `mnb.gemini_api_key.aes.v1`, AAD `mnb:gemini_api_key:v1`, private SharedPreferences, `allowBackup=false` [D]။
- Key ကို UI/State/Log/URL ထဲမထည့်ကြောင်း Guard Test များ (`ApiKeyLeakageTest`, `GeminiSourceGuardTest`, `ProjectSecretsGuardTest`) [D/CI]။
- Test များသည် Fake Cipher/Storage သုံး — Real Keystore မစမ်းရ [U]။

### L13 Room Database — PARTIAL
- `mnb.db` version **4**, entities ၁၇ ခု (Novel, Chapter, Translation, Chunk, Job, Memory×9, Bookmark, BrowserHistory, BrowserTab)၊ Migration 1→2, 2→3, 3→4 additive၊ `fallbackToDestructiveMigration` မသုံး (comment တားထား) [C]။
- `exportSchema=true` ဖြစ်သော်လည်း schema JSON မ commit ထား → Schema-based Migration Test အလုပ်မလုပ် [C/I]။
- **Translation Cache (paragraph-level)** မရှိ — `ChapterTranslation` (chapter-level) သာ [D/C listing]။ Reading progress ကို Room မဟုတ် SharedPreferences ထဲထား [D]။
- User Library table (favorites/progress per novel) **MISSING**။ `NovelRepository` သည် Translation အတွက် Novel info သာ [C]။

### L14 Reader UI — PARTIAL
- Reader screen၊ Translation display UI (I10-B)၊ Reader translation state (I10-A) [D]။ Original/Myanmar/Bilingual display mode အပြည့်အစုံ မအတည်ပြုနိုင် [U]။
- Reader-specific settings အချို့ ရှိ၊ In-page ဟုတ်မဟုတ်— In-page မဟုတ်။

### L15 WebView Security — DONE-CI, Device [U]
- JS on (လိုအပ်)၊ `allowFileAccess=false`, `allowContentAccess=false`, `MIXED_CONTENT_NEVER_ALLOW`, multi-window off, geolocation off, Safe Browsing on (API≥26) [C]။
- SSL error အမြဲ `cancel`; Camera/Mic/Geo permission အမြဲ deny; `UrlPolicy` (https only; http→upgrade; scheme ပိတ်) [C]။
- **`addJavascriptInterface` မသုံး** [C: WebViewController ထဲမတွေ့]။ Console message ဖျောက် [C]။
- Guard tests: `WebViewSecurityGuardTest`, `WebViewResilienceGuardTest`, `NewTabWebViewGuardTest` [D]။
- ကျန်: Network Security Config ရှင်းလင်းစစ်ရန်၊ Cookies isolation မရှိ၊ Extraction script သည် page JS world တွင်ပြေး (Page script နှင့် Global namespace တိုက်နိုင်) [C/I]။

### L16 Build / CI / Release — PARTIAL
- CI: JDK 17 + Gradle 8.7၊ `testDebugUnitTest` + `assembleDebug` + APK upload၊ push တိုင်း [C]။
- Lint မရှိ၊ Release build မရှိ၊ Signing မရှိ (ကောင်းသည် — secrets မ commit)၊ R8 off + `proguard-rules.pro` ဗလာ၊ versionName `0.2.0-phase02` [C]။
- Instrumented/Device test မရှိ၊ Gradle Wrapper ရှိသော်လည်း CI က `gradle` 8.7 ကို တိုက်ရိုက်သုံး [C]။
- Dependency vulnerability scan မလုပ်ရသေး [D]။

## 5. Feature အလိုက် အနှစ်ချုပ်

**ပြီး (Code + CI Unit Test၊ Device မစစ်):** Multi-tab Browser၊ Google Search၊ Bookmarks/History (pruning/search/groups)၊ New Tab၊ Link→New Tab၊ Renderer-crash recovery၊ Chapter DOM Extraction (Preview Screen ဖြင့်)၊ Core Reader + Settings၊ Translation Core/Chunking/Memory/Quality၊ Instant + Background Translation၊ Direct Gemini BYOK၊ Secure Key Storage၊ Premium UI Shell။

**တစ်စိတ်တစ်ပိုင်း:** Settings (Screen ၂ ခု)၊ Shortcuts (Group/Safe Mode မရှိ)၊ Reader (Chapter nav/Progress မအတည်ပြု)၊ Database (Cache/Library/Schema JSON မရှိ)၊ CI (Release/Lint မရှိ)။

**မရှိသေး (Final Vision အတွက်လိုအပ်):** In-page Translation (Progressive DOM Replacement)၊ Reader Mode toggle (Preview Screen မလိုသော)၊ Page/Tab Identity၊ Paragraph-level Cache၊ Novel Discovery/User Library/Favorites/Progress၊ Chapter Navigation၊ Safe Mode + Shortcut Groups၊ Downloads/Find/Share policy၊ Release Signing/Versioning/R8၊ Device & Compatibility Test။

## 6. Architecture အားနည်းချက်များ

| ID | အားနည်းချက် | အကျိုးသက်ရောက်မှု |
|---|---|---|
| W-1 | Extraction ရလဒ်တွင် DOM Reference/Stable ID မပါ | In-page Replace မဖြစ်နိုင် (R-03) |
| W-2 | Page/Tab Identity မရှိ (URL string + tabId သာ) | Navigation/Reload အလယ်တွင် Translation ရလဒ်မှားထည့်နိုင် (stale write) |
| W-3 | Reader သည် Browser ပြင်ပ Compose Overlay | Vision နှင့်ကွဲ |
| W-4 | Translation Provider Sync/Blocking, Coroutines မရှိ | Cancellation (HttpURLConnection) ခက်၊ UI/progress streaming ခက် |
| W-5 | Cache Granularity chapter-level | Paragraph-level Progressive Replace/Resume ခက် |
| W-6 | Settings အပြင်ပိုင်း ၂ နေရာ | UX ရှုပ်၊ Gemini key UI နေရာမသေချာ |
| W-7 | Schema JSON မ commit | Migration သက်သေမရှိ |
| W-8 | Device Test ၀ | Background/Keystore/WebView crash ကို မသက်သေပြ |
| W-9 | Extraction Script သည် Kotlin raw string ထဲ | JS Unit Test/Lint ခက်၊ `$` ကန့်သတ်ချက် |
| W-10 | Docs စနစ် ရှုပ် (Root တွင် Report ၁၅+ ဖိုင်၊ Stale) | Handover ခက် |

## 7. အတည်ပြုနိုင်သည်များ vs ခန့်မှန်းချက်များ

**အတည်ပြုပြီး:** PR 1–36 အားလုံး merged၊ HEAD `c67a45e`၊ PR #27/#34/#35/#36 CI success၊ DB v4၊ Library မှ DuckDuckGo default မဟုတ်ကြောင်း (Legacy ပဲ)၊ `addJavascriptInterface` မသုံးကြောင်း၊ Extraction trigger နည်း၊ Settings gating စသည့် [C] များ။
**ခန့်မှန်း [I]:** Schema test skip ဖြစ်နေခြင်း၊ I10-C branch အမည်ရင်းမြစ်။
**မစစ်ရသေး [U]:** PR #1–#26, #28–#33 ၏ CI ရလဒ်၊ Test အရေအတွက်/အစိမ်းရောင်၊ Device behavior အားလုံး၊ `SettingsScreen` wiring၊ Chapter navigation၊ Gemini model id တည်ရှိမှု။
