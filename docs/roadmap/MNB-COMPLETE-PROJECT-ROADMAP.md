# MNB — Complete Project Roadmap (လက်ရှိအခြေအနေ → Final Release v1.0.0)

ရက်စွဲ: 2026-11-08 · အခြေခံ: `main` @ `c67a45e` · Planning သာ (Implementation မစ)
ဆက်စပ်စာတမ်း: MNB-CURRENT-STATE-AUDIT · MNB-FINAL-ARCHITECTURE · MNB-PHASE-DEPENDENCY-MAP · MNB-DECISION-LOG · MNB-FINAL-ACCEPTANCE-CHECKLIST · HANDOVER
Evidence Tag: **[C]** Code ဖတ်ပြီး · **[D]** Doc သာ · **[CI]** CI အစိမ်း · **[U]** မစစ်ရသေး · **[I]** ခန့်မှန်း

---

# PART A — မူလ Phase History (မဖျက်ပါ၊ မပြောင်းပါ)

ဤအပိုင်းသည် Repository ၏ မူလအမည်များကို မူလအတိုင်း မှတ်တမ်းတင်ထားသည်။ Roadmap အသစ်နံပါတ် (`R-xx`, Part B) သည် မူလအမည်များကို **အစားထိုးခြင်းမဟုတ်** — "Lineage" ကွက်ဖြင့် မူလ Phase သို့ ပြန်ချိတ်ထားသည်။

## A.1 မူလ Phase အလိုက် ရည်ရွယ်ချက်၊ အထောက်အထား၊ အခြေအနေ

| မူလ Phase | မူလရည်ရွယ်ချက် (အထောက်အထားပါ) | PR / Commit | အခြေအနေ |
|---|---|---|---|
| **Phase 01** | Android WebView Browser အခြေခံ (single session `web_session.bin`) | Commit `0255cee` upload (zip ကို `43d4a3a` ဖြင့်ဖျက်) — PR မရှိ | Code ရှိ [C]; `web_session.bin` ကို 2B က အစားထိုး |
| **Phase 02** | Website text extraction (DOM) | `cec1e73`, `4d5c196`, `61dda2a`, `97cb889` direct commit + PR #3 fix | Extraction ရှိ [C]; Preview Screen အသုံးပြုပုံ |
| **Phase 03 Part 1–2** | Core Novel Reader (saved position) + Reader Settings (font/margin/spacing/theme) | PR #1, #2 | Code ရှိ [D/CI-မစစ်]; Chapter nav [U] |
| **Phase 04-I1…I10-D** | Translation Engine (Core, Models, DB, Mock Provider, Chunking, Context Memory, Quality, Instant, Background, Priority, Reader Integration, QA) | PR #4–#19 | Code ရှိ၊ Mock → Gemini ပြောင်းပြီး [D] |
| **Phase 05** | **[User-confirmed UD-01]** Gateway (`NB-Translation-Gateway`) အခြေခံ ဘာသာပြန်စနစ် (05-A…05-B3) | ဤ Repo တွင် PR/Branch/Doc မရှိ | **UD-01 ✔ User အတည်ပြု (2026-11-08): Phase 05 = Gateway၊ Phase 06 Direct BYOK က အစားထိုး (Superseded) — Completed မသတ်၊ ပြန်မထည့်** |
| **Phase 06-A1…A4** | Secure API Key Storage + Key Settings UI + Tests | PR #20–#23 | Code ရှိ၊ Real Keystore [U] |
| **Phase 06-B1…B4** | Direct Gemini Provider + Engine Integration + Error handling + Final QA/Worker fix | PR #24–#27 | Code ရှိ [CI: #27]; Device check [U] |
| **UI Phase 1** | Premium Browser UI shell | PR #28 | Code ရှိ [D] |
| **Browser 2A** | Bookmarks/History/Tab DB (v4) | PR #29 | Code ရှိ [C schema] |
| **Browser 2B** | Multi-tab (WebView per tab) | PR #30 | [D]; Device [U] |
| **Browser 2C** | Google search + Novel shortcuts home | PR #31 | [D] |
| **Browser 2D** | Settings + Bottom menu polish | PR #32 | [D] |
| **Browser 2E** | Bookmarks + History UI | PR #33 | [D] |
| **Browser 2F** | Tab lifecycle & WebView resilience | PR #34 [CI] | [CI]; Device [U] |
| **Phase 06-C1** | Settings Screen foundation (Section ၆ ခု) | PR #35 [CI] | Code ရှိ၊ Wiring [U]၊ **06-C2…C4 မလုပ်ရသေး** |
| **Browser 2G** | History pruning/search/groups, New Tab, Google-only, Link→New Tab, Migration test | PR #36 [CI] | [CI]; schema JSON မပါ |

## A.2 အစားထိုးခံရသော ရည်ရွယ်ချက်များ မှတ်တမ်း (Replaced-Purpose Ledger)

| မူလအရာ | ဘာကြောင့်ပြောင်း | ဘာနဲ့အစားထိုး | ကျန်သေးသည်များ |
|---|---|---|---|
| Phase 05 Gateway-based translation | User ၏ ဆုံးဖြတ်ချက် — Direct BYOK (README/SECURITY ရေးထား) [D]; Backend မလို | Phase 06 Direct Gemini BYOK | Gateway သည် ဤ Repo ပြင်ပ။ Request Path ထဲ **ပြန်မထည့်ရ**။ UD-01 ✔ User အတည်ပြုပြီး (2026-11-08) |
| Phase 01 single `web_session.bin` | Multi-tab လိုအပ်၍ | 2B per-tab session file | ဖိုင်ဟောင်း မဖတ်တော့ (Known limit) |
| Mock provider (04-I4, I10-C) | တကယ်ဘာသာပြန်ရန် | Gemini provider | Mock = Test only (Guard တားထား) |
| Phase 02/03 Extract→Preview→Reader (Screen ၂ ခု) | Vision: Preview Screen မလို၊ In-page | Reader Mode (R-04/R-05) | `ReaderScreen` ကို Optional "Focus Reader" အဖြစ်ထား (TD-06); Preview = Debug only (UD-03) |
| DuckDuckGo default (အစောပိုင်း) | Google-only | 2C/2G Google Search | Legacy data recognition သာ ထား (Default မဟုတ်) |
| Home URL = New Tab page | Real Google page ကို New Tab ဟု မမှတ်ရန် | 2G `NEW_TAB_URL` | — |
| Settings (PremiumSettings) vs SettingsScreen (06-C1) | နှစ်ခုပေါင်းရန် | R-01 (06-C2…C4 lineage) | ပေါင်းမစပြီး |

## A.3 နံပါတ်စနစ် ရောထွေးမှု ဖြေရှင်းချက် (TD-01)
- မူလ Label များ (`Phase 02`, `2A–2G`, `04-I*`, `06-*`, `UI Phase 1`) ကို **မပြောင်း**။
- Roadmap အသစ်တွင် `R-00 … R-17` ကို သုံးသည် (R-17 = Google Play၊ v1.0 ပြင်ပ သီးခြား Milestone) (R = Roadmap)။ ရှေ့မှာ "Phase" မထည့် — မူလ Phase နံပါတ်နှင့် မရောရန်။
- `06-C2…06-C4` (06-C1 မှ လက်ဆင့်ကမ်း) ကို R-01 က **ထိန်းသိမ်းသုံး** (Lineage ပြ)။
- Branch အမည်တွင် `r-xx-` ဖြင့်စပြီး Phase အမည်တူ Branch ပြန်မသုံးရန် (PR #35/#36 ပြဿနာမထပ်ရန်)။

---

# PART B — Final Product Vision အကျဉ်း

Premium Browser + Novel Reader + Translation Platform (Novel ဖတ်ခြင်းအဓိက)။
**မပြောင်းရသော ကန့်သတ်ချက်များ** (User ခွင့်ပြုချက်မရှိ ပြောင်းမရ): Google-only Default Search (DuckDuckGo မထည့်) · Direct Gemini BYOK (Gateway/Google Cloud Translation မထည့်) · WebView Security (§4 Security Baseline) · Safe Mode ≠ Content Unlocker။

**In-page Translation (အဓိက):** Reader Mode ON → လက်ရှိ WebView ကိုသုံး → DOM Extraction (Background) → Ordering/Filtering → Existing Chunking/Memory → Gemini BYOK → Progressive Replacement → Original ပြန်ကြည့်/OFF ရ → Layout/Image/Link/Button ထိန်း → Scroll မပျက် → Navigation/Reload/Tab Switch/Cancel မှန်ကန်။ Preview Screen မဖြစ်မနေမလို။

## B.1 Security Baseline (အားလုံး Phase တွင် မဖောက်ရ)
SB-1 `addJavascriptInterface` မသုံး · SB-2 File/Content access off · SB-3 Mixed content never · SB-4 SSL error အမြဲ cancel · SB-5 Permissions deny · SB-6 `UrlPolicy` https-only · SB-7 Key ကို UI/State/Log/URL/Exception ထဲမထည့် · SB-8 Page စာသားကို Prompt ထဲထည့်ရာတွင် "content only" ညွှန်ကြား · SB-9 **DOM ထဲရေးရာတွင် `textContent` သာ — `innerHTML` မသုံး** · SB-10 INTERNET Permission တစ်ခုတည်း (ပြောင်းလျှင် UD)။

---

# PART C — Final Roadmap (R-00 … R-17; v1.0 Critical path = R-00 … R-16၊ R-17 = Post-v1.0 Google Play Milestone)

Phase တိုင်း Format: Number · Name · Objective · Current Status · Existing Implementation · Missing Features · Scope · Tasks · Dependencies · Files/Modules · Security · Testing · Acceptance · Completion · Risks · Expected Final Result။
**Completion Criteria အမြဲ ၄ ချက်:** (1) Acceptance အားလုံး ✔ (2) CI အစိမ်း (exact commit) (3) Phase အလိုက် Device Test မှတ်တမ်း (4) Docs/ဖိုင်ကိစ္စ ပြန်ညှိ။ Code ရေးပြီးရုံ = Completed မဟုတ်။

---

## R-00 — Baseline Verification & Documentation Reconciliation
- **Lineage:** 2A–2G, 06-B4, 06-C1 ၏ "Device tests still needed" အားလုံးကို ပိတ်ရန်။ (Phase အသစ်ဖြစ်သည် — TD-09)
- **Objective:** အခုရှိပြီးသား Code ကို Device ပေါ်တွင် သက်သေပြ၊ ကျန် Code ကိုဖတ်၊ Doc/History ကို ပြန်ညှိ။
- **Current Status:** CI အစိမ်း (#27/#34/#35/#36) [CI]; Device Test ၀ [U]; Docs Stale (G-1…G-9)။
- **Existing:** Tests, CI, Release checklist [D]။
- **Missing:** Device pass၊ Schema JSON၊ Docs ညှိ၊ Branch ရှင်း၊ မဖတ်ရသေးသောဖိုင် Audit။
- **Scope:** Verification + Housekeeping သာ (Feature အသစ်မပါ)။
- **Tasks:**
  1. 2B/2F/2G/06-B4 Device Test စာရင်း (Report များတွင်ပါ) အားလုံးလုပ်၊ ရလဒ်မှတ်။
  2. `app/schemas/.../4.json` Build မှထုတ်ကာ commit; `3.json` derive; `MnbSchemaMigrationTest` **passed (not skipped)** ဟုပြ။
  3. မဖတ်ရသေးသောဖိုင်များ (TabManager, UrlPolicy, ReaderScreen, Chunking/Memory/Quality, Navigation, Tests count) Audit ပြီးစီး။
  4. README/ARCHITECTURE/TESTING/RELEASE-NOTES/HANDOVER ကို အမှန်ပြင်; Phase Report များကို `docs/history/` သို့ရွှေ့ (Root clutter ရှင်း)။
  5. Merge ပြီး Branch များ (Archive tag ဖြင့်) ရှင်းရန် — **USER ခွင့်ပြုမှ**။ `tools/apply_phase_2g.py`, `derive-v3-schema.py` ရွှေ့/ဖယ်။
  6. `SettingsScreen` Wiring အခြေအနေ အတည်ပြု။
  7. **Gemini Model (UD-04 ✔ Decided: Allow-list):** (က) Official docs ကို ပြန်ဖတ် — `gemini-2.5-flash` ၏ Dec 1 Reroute ကြေညာချက်ကို Primary source ဖြင့်အတည်ပြု (Decision Log C.1 ကွဲလွဲမှု) (ခ) Candidate (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, (+`3.6-flash`)) ကို Golden Set ဖြင့် Live စမ်း — Quality/Latency/Token/Cost (ဂ) Gemini 3 API Request shape Compatibility (TD-18) (ဃ) Default Lock (Provisional = `gemini-3.8-flash`)။
- **Dependencies:** မရှိ (ပထမဆုံး)။
- **Files:** `docs/`, `app/schemas/`, Root *.md, `.gitignore` (schemas မ ignore)၊ Test များ (Fix သာ)။
- **Security:** Secrets scan (`ProjectSecretsGuardTest`) အစိမ်း၊ Log ထဲ Key/Chapter text မပါကြောင်း `adb logcat` စစ်။
- **Testing:** Device checklist (RELEASE-CHECKLIST ၏ Device အပိုင်း အားလုံး + 2F အချက် ၅ ချက် + 2G/2B စာရင်း)။
- **Acceptance:** Device Test ရလဒ်ဇယား၊ Schema JSON နှစ်ခု commit၊ Migration Test passed၊ Doc/Code ကွာဟချက် ၀၊ ဖတ်မရသေးဖိုင် ၀။
- **Risks:** Device Test တွင် 2B–2G Bug အသစ်တွေ့နိုင် (ကောင်းသော အရာ — ထို Bug များကို R-00 ထဲ Fix)။ Migration Test Robolectric တွင် မအောင်နိုင်။
- **Expected Final Result:** "ယုံကြည်စရာ Baseline" — နောက် Phase များ မည်သည့်အပေါ်တွင် တည်ဆောက်သည်ကို သက်သေပြနိုင်။

## R-01 — Settings Consolidation & Model Configuration
- **Lineage:** Phase 06-C2 / C3 / C4 (06-C1 မှ "C2 wiring, C3/C4 key section ရွှေ့" ဟုညွှန်းထား [D])။
- **Objective:** Settings တစ်နေရာတည်း၊ Gemini Config ကို တကယ်အလုပ်လုပ်စေ။
- **Current:** Settings ၂ ခု (`PremiumSettings` + `SettingsScreen`); Gemini Configuration/Translation Behavior = Placeholder [D]။
- **Existing:** `GeminiApiKeySection`, `GeminiKeySettingsController`, `ThemeMode`, Language enums [C listing/D]။
- **Missing:** Wiring၊ Gemini Model selection (UD-04)၊ Translation Behavior (cache ပိတ်/ဖွင့်၊ Concurrency)၊ Reader Settings entry၊ Safe Mode entry placeholder၊ About version ဖတ်ခြင်း။
- **Scope:** Settings UI + Preferences storage။ Translation Core မပြောင်း။
- **Tasks:** ၁) `SettingsScreen` ကို Settings ဝင်ပေါက်တွင်ချိတ် (`versionName` ကို PackageManager မှ)။ ၂) Gemini key section ကို `Gemini Configuration` သို့ ရွှေ့ (Key flow မပြောင်း)။ ၃) Model setting = **Allow-list ပြည့်စုံစွာ** (UD-04 ✔; Free-text မရ; Default = R-00 Evaluation ရလဒ်; Model ပျောက်/Reroute → Default သို့ Fallback + Notice) — `GeminiConfig` ကို TD-18 အတိုင်းညှိ ၄) `PremiumSettings` ဟောင်းကို ဖယ်/ပေါင်း ၅) Preferences ကို `UiPreferences` တစ်ခုတည်းသို့ ပေါင်း။
- **Dependencies:** R-00။
- **Files:** `ui/settings/*`, `ui/premium/PremiumSettings.kt`, `ui/GeminiApiKeySection.kt`, `settings/*`, `translation/gemini/GeminiConfig.kt`။
- **Security:** Key UI သည် 06-A ၏ Guard (`ApiKeyLeakageTest`) ကို မဖောက်; Model name ကို safe pattern validate (existing)၊ UI မှ endpoint မပြောင်းနိုင်ရ (HTTPS fixed)။
- **Testing:** Key save/update/delete/rotate/background clear (Device)၊ `ApiKeyLeakageTest` အစိမ်း၊ Settings navigation test။
- **Acceptance:** Settings Screen တစ်ခုတည်း၊ Key လုပ်ငန်း ၄ ခု Device တွင်အလုပ်၊ Model ရွေးပြီး Translation တစ်ကြိမ်ကျော် (Live API) အောင်၊ Placeholder ၀ (သို့မဟုတ် "Coming soon" ကို Release UI တွင်မပြ)။
- **Risks:** Key UI ရွှေ့ရာတွင် Leakage Regression; Model မရှိတော့ (404 `MODEL_NOT_FOUND`)။
- **Result:** ယုံကြည်ရသော Settings နှင့် Live-verified Gemini Config။

## R-02 — Page Identity, Tab Identity & Reader-Mode State Model
- **Lineage:** အသစ် (Architecture W-2)။ 2B/2F ၏ Tab/WebView lifecycle ကို အခြေခံ။
- **Objective:** "ဘယ် Tab / ဘယ် Page / ဘယ် Navigation Generation" ကိုသတ်မှတ်ပေး၊ Stale ရလဒ်ကို ပယ်နိုင်စေ။
- **Current:** `tabId` + URL string သာ; Document ဖြစ်တိုင်း Token မရှိ [C]။
- **Existing:** `TabManager`, `WebViewController` callbacks (`onPageStarted/Finished`, `doUpdateVisitedHistory`) [C]။
- **Missing:** `PageSession(tabId, navGeneration, normalizedUrl, documentToken)`; ReaderMode state machine (OFF/EXTRACTING/TRANSLATING/READY/ERROR/CANCELLING) per tab; Same-document (SPA) vs new-document ခွဲခြား။
- **Scope:** State model သာ (UI/Translation မပါ)။ Pure Kotlin၊ Unit-testable။
- **Tasks:** ၁) Identity model ၂) navGeneration တိုးသည့် Event များ သတ်မှတ် (page start, reload, history nav, tab unload/recreate, renderer-gone) ၃) ReaderMode controller (tab တစ်ခုလျှင် state တစ်ခု) ၄) Cancellation token propagate။
- **Dependencies:** R-00။
- **Files:** `web/`, `tabs/`, `state/`, new `readermode/` package။
- **Security:** Token သည် Page JS မှ ခန့်မှန်း/လိမ်လည်၍မရအောင် (random, per-injection)။
- **Testing:** State-machine unit tests (navigation race, tab switch, renderer crash, unload)၊ Existing `WebViewSecurityGuardTest`/`WebViewResilienceGuardTest` မပျက်။
- **Acceptance:** Event အစီအစဉ်အားလုံးအတွက် Table-driven test အစိမ်း; WebView API Call သည် Existing Guard Test (one `WebView(context)`, etc.) မဖောက်။
- **Risks:** Android WebView Callbacks ၏ မမှန်ကန်သော အစီအစဉ် (onPageFinished ထပ်ခါထပ်ခါ) [D 2E note]။
- **Result:** In-page လုပ်ငန်းအားလုံးအတွက် Safe foundation။

## R-03 — DOM Extraction v2 (Anchored, In-page)
- **Lineage:** Phase 02 ကို တိုးတက်စေ (မူလ Extraction ကို အစားထိုးမဟုတ် — Output Compatible ထား)။
- **Objective:** Paragraph တစ်ခုချင်းကို DOM Node သို့ ပြန်ညွှန်းနိုင်သော Extraction။
- **Current:** `{t,h}` စာသားသာ၊ Node reference မပါ [C]။
- **Existing:** Container scoring, noise filter, link-density filter, title heuristic [C]; `ContentParser`, `ExtractionController` [C listing/C]။
- **Missing:** Stable paragraph index + text hash၊ JS-side registry (DOM attribute မထည့်)၊ Chapter vs non-chapter detection score၊ Dynamic/lazy content (infinite scroll/re-render) ကို စောင့်/ပြန်စစ်၊ Extraction session token၊ Fixtures။
- **Scope:** Script + Kotlin parser + Fixtures။ Replacement မပါ (R-04/R-05)။
- **Tasks:** ၁) Script ကို `assets/js/` ဖိုင်သို့ရွှေ့ (TD-12) ၂) `window`-scoped closure registry (namespace `__mnb_<token>`; WeakRef/Array of Node) ၃) Output: `{sessionToken, docToken, paragraphs:[{i, hash, text, h, kind}]}` ၄) Container ပျောက်/Rerender detection (MutationObserver — observe only, bounded) ၅) HTML Fixture suite (Royal Road/Wattpad/Wuxiaworld/Gutenberg/Standard Ebooks + Noveldex + CJK site ၂ ခု) ၆) JS Test harness (UD/TD-12)။
- **Dependencies:** R-02 (token)။
- **Files:** `extraction/*`, `app/src/main/assets/js/*`, `web/WebViewController.runExtraction`, tests + fixtures။
- **Security:** Script သည် network မခေါ်; Page script နှင့် Global တိုက်မှု မရှိ (closure/IIFE, non-enumerable); Page က Registry ကို ဖတ်/ပြင်မရ ဖြစ်နိုင်သမျှ; Paragraph count/size cap (DoS)။
- **Testing:** Fixtures ≥ 10 site shape၊ Output golden test၊ Hidden/ad/nav ဖယ်၊ Zero-width chars၊ Myanmar/CJK text၊ ပျက်စီးသော HTML၊ 15 s timeout path။
- **Acceptance:** Fixture Matrix pass; Anchored paragraph ၁၀၀% Node ပြန်ရှာနိုင် (Test); Legacy `ExtractionState` Output (Preview/Reader) မပျက်။
- **Risks:** Site Heuristic အားနည်း (Compatibility) → R-14 Matrix; Page JS က DOM ကိုပြန်ရေး (React re-render) → Node reference ပျက်။
- **Result:** Replace လုပ်နိုင်သော Extraction Payload။

## R-04 — In-page Reader Mode (Toggle + Restore, Translation မပါ)
- **Lineage:** Phase 03 Reader ၏ ရည်ရွယ်ချက်ကို Browser ထဲသို့ ဆွဲလာ။
- **Objective:** Reader Mode ON/OFF ကို WebView အတွင်း၊ Preview Screen မလိုဘဲ။
- **Current:** Extract ခလုတ် → Preview → Reader Screen [C]။
- **Existing:** BottomBar, `ExtractionController`, `ReaderStateManager`, `ReaderScreen` [C]။
- **Missing:** Reader Mode toggle၊ Original text snapshot/Restore၊ Scroll anchor ထိန်း၊ Tab switch/Reload/Navigation တွင် Mode state၊ Reader Style CSS injection (Optional) ။
- **Scope:** Mode lifecycle + Original/Restore။ Translation မပါ — Placeholder transform (ဥပမာ marker) ဖြင့် Restore စမ်း။
- **Tasks:** ၁) Toggle UI (Extract button အစား) ၂) Replace API (JS): `setText(i, text)` → `node.textContent`; Original ကို JS registry ထဲမှတ် (Node ကိုသာ — Network မလို) ၃) Restore all ၄) Scroll anchor: Replace မတိုင်မီ/ပြီး viewport-top paragraph ကို မှတ်၍ offset ပြန်ညှိ ၅) Navigation/Reload/Tab-switch/Unload/Renderer-gone → Cancel + State reset (R-02) ၆) ExtractionPreviewScreen = **Debug/Development-only** (UD-03 ✔; Release build မှ ဖယ်ရှား/Build flag နောက်ဝှက်; Guard Test ဖြင့်သက်သေ)။
- **Dependencies:** R-02, R-03။
- **Files:** `ui/BrowserScreen.kt`, `ui/premium/BottomMenu.kt`, `readermode/*`, `web/WebViewController.kt` (JS dispatch method တစ်ခုသာ)၊ `extraction/*`။
- **Security:** SB-9 (`textContent` သာ); Mode state ကို Page က မပြောင်းနိုင်; Replacement script ကို Token စစ်။
- **Testing:** State machine tests၊ Device: Scroll မရွေ့၊ Images/Links/Buttons မပျက်၊ Toggle ON/OFF 20 ကြိမ်၊ Tab switch၊ Rotate၊ Reload၊ Back/Forward၊ Renderer kill။
- **Acceptance:** OFF → Original 100% ပြန် (DOM text equality Test)၊ Scroll drift < 1 paragraph၊ Preview Screen မလိုဘဲ Mode ON လို့ရ။
- **Risks:** Page JS ပြန်ရေးခြင်း၊ Fixed header/Anchor navigation၊ Rotation တွင် Layout shift။
- **Result:** Translation မပါသော်လည်း Reader Mode ၏ "Mechanics" သက်သေပြပြီး။

## R-05 — In-page Translation Pipeline (Progressive Replacement + Paragraph Cache)
- **Lineage:** Phase 04 (Chunking/Memory/Quality/Instant/Background) + Phase 06 (Gemini) ကို DOM ဘက်နှင့် ပေါင်းစပ်။
- **Objective:** Reader Mode ON → Gemini ဖြင့်ဘာသာပြန် → Paragraph များကို တဖြည်းဖြည်း အစားထိုး။
- **Current:** Chapter-level translation + Reader Screen display [D]။
- **Existing:** `TranslationService`, Chunking, Memory, Quality, Instant/Background pipeline, `DirectGeminiProvider`, WorkManager [D/C listing]။
- **Missing:** DOM paragraph → Chunk mapping၊ Chunk ရလဒ် → paragraph index mapping (output paragraph count ကိုက်ညီမှု)၊ Progressive replace dispatcher၊ Paragraph-level Cache (Room v5)၊ Chapter identity (URL → Novel/Chapter id)၊ Cancellation→Network၊ Resume။
- **Scope:** Instant-in-page path အရင်၊ Background (Reader Mode ပိတ်/App ပိတ်နောက်) ကို Cache ထဲရေးသာ — DOM မထိ။
- **Tasks:** ၁) `ParagraphUnit(i, hash, text)` → Existing chunker input ၂) Gemini Prompt ကို "paragraph index tag ဖြင့် N ကြောင်းပြန်" ပုံစံ (Existing `GeminiPromptBuilder` ကို Extend; ပြန်ရေးမဟုတ်) ၃) Response parse → index မှန်ကန်မှု စစ် (Quality) ၄) Chunk တစ်ခုပြီးတိုင်း Replace dispatch (Main thread; batch) ၅) Cache: Room v5 additive migration `paragraph_translation_cache(key=host+normalizedUrl+paraHash+targetLang+modelId+promptVersion, text, createdAt)` ၆) Page Identity token မကိုက်လျှင် ရလဒ်ပယ် ၇) Cancel: token + `HttpURLConnection.disconnect` ၈) Error UI (inline banner, Retry)။
- **Dependencies:** R-01, R-03, R-04။
- **Files:** `translation/*`, `translation/db/*` (+ Migration 4→5 + schema 5.json)၊ `readermode/*`, `reader/translation/*`။
- **Security:** Key flow မပြောင်း (SB-7); Page text ကို Prompt တွင် "content only" (SB-8); Output သည် `textContent` (SB-9); Cache ထဲ Key/Header မပါ; Gateway/Google Cloud မသုံး (TD-11)။
- **Testing:** Fake Gemini ဖြင့် End-to-end (Robolectric + JS fixtures); Chunk-index mismatch၊ Partial failure၊ Rate limit၊ Cancel mid-flight၊ Stale token၊ Cache hit/miss၊ Migration 4→5 (real `MigrationTestHelper`)၊ Device + Live Gemini ၃ site။
- **Acceptance:** ၁) Reader Mode ON → ပထမ Paragraph ရလဒ် ≤ ပထမ Chunk ပြန်ချိန် ၂) Cache hit ဖြင့် ဒုတိယအကြိမ် Network မခေါ် ၃) Navigation/Reload/Tab switch တွင် စာမှားမထည့် ၄) OFF → Original ၅) Key Leakage Test အစိမ်း ၆) Layout/Images/Links မပျက်။
- **Risks:** Gemini ၏ paragraph-count မကိုက်; Latency; Cost; Cache Key Collision; Main thread jank (DOM batch)။
- **Result:** Product ၏ အဓိက Feature လုပ်ဆောင်ပြီ။

## R-06 — Translation Quality, Safety & Resilience
- **Lineage:** 04-I7 Quality + 06-B3 Error handling ကို တိုးချဲ့။
- **Objective:** Myanmar Output အရည်အသွေး၊ Content Blocking၊ Rate/Quota၊ Cost ကိုင်တွယ်။
- **Current:** Fixed error messages၊ Retry ≤3၊ Default safety [D]။
- **Missing:** Myanmar Unicode vs Zawgyi စစ်၊ မဘာသာပြန်ဘဲကျန်စာကြောင်း Detection၊ Glossary/Character UI၊ Safety setting (UD-05)၊ Token/Cost estimate၊ Concurrency + RPM limiter၊ Prompt version management၊ အရည်အသွေး Evaluation Set။
- **Tasks:** ၁) Quality Checker rules တိုး ၂) Prompt Version ကို Cache Key တွင်ထည့်ပြီးသား (R-05) — Prompt ပြောင်းလျှင် Invalidate ၃) Safety config (UD-05) ၄) Rate limiter ၅) Golden Evaluation set (EN/ZH/KO/JA ×၅ စာပိုဒ်)၊ Human review checklist ၆) Glossary UI (Context Memory ကိုပြ/ပြင်) ၇) Cost hint (စာလုံးရေ)။
- **Dependencies:** R-05။
- **Files:** `translation/quality/*`, `translation/gemini/*`, `translation/memory/*`, `ui/settings/*`။
- **Security:** Safety setting သည် User ရွေးချယ်မှုသာ (Default မပြောင်း UD-05); Log တွင် Chapter စာသားမပါ။
- **Testing:** Golden set၊ Quality unit tests၊ 429/Quota fake၊ Blocked-content UX၊ Device Live။
- **Acceptance:** Zawgyi/Unicode စစ်ပြီး Unicode သာ Output; Blocked/Quota/Key error အားလုံး User-friendly; Evaluation set ဖြင့် Human အမှတ်ပေး ≥ Target (UD-06 ဖြင့်သတ်မှတ်)။
- **Risks:** Gemini Policy blocking၊ Model drift၊ Myanmar Font Rendering (WebView/OS) → R-14။
- **Result:** ယုံကြည်ရသော ဘာသာပြန်အရည်အသွေး။

## R-07 — Novel Library, Chapter Navigation & Reading Progress
- **Lineage:** Phase 03 (Saved position) + Phase 04-I3 (Novel/Chapter entities) ကို User Library အဖြစ်တိုးချဲ့။
- **Objective:** Favorites, Library, Progress, Chapter Next/Prev, Novel Details (Discovery scope UD-02)။
- **Current:** Reading position (SharedPreferences) [D]; `NovelRepository` (translation info) [C]; Library UI မရှိ။
- **Missing:** Library table/UI၊ Favorite၊ Progress per novel/chapter၊ Chapter detection (Next/Prev link)၊ Novel metadata capture (title/author/cover/description — လက်ရှိ Page ထဲမှသာ)၊ Continue reading။
- **Scope (UD-02 ✔ Decided):** ပထမအဆင့် = **လက်ရှိဖွင့်ထားသော Page မှ Novel metadata သိမ်းဆည်းခြင်းသာ** + Library/Favorites/Progress။ Site-specific Search/Parsers/Cross-site Discovery = v1.0 Scope ပြင်ပ (နောက်ပိုင်း Milestone၊ User ဆုံးဖြတ်ပြီးမှ)။
- **Tasks:** ၁) Room v6 (additive) `library_novels`, progress ကို Room သို့ ရွှေ့ (SharedPrefs → Room migration) ၂) Metadata extractor (JSON-LD/OpenGraph/Meta) ၃) Next/Prev detection (`rel=next`, text heuristic, Site rule) ၄) Library Screen (Favorites, Recent, Continue) ၅) Progress %။
- **Dependencies:** R-02, R-05 (Chapter identity)၊ R-01 (settings)။
- **Files:** `translation/db/*`, `library/*` (Browser library နှင့် နာမည်မတူအောင်— TD: `novellibrary/`), `ui/library/*`, `reader/*`။
- **Security:** Metadata Text ကို `textContent`/Compose Text သာပြ; Cover image URL https-only; Page-provided URL သည် `UrlPolicy` ဖြတ်။
- **Testing:** Migration 5→6 test၊ SharedPrefs→Room data migration test၊ Metadata fixtures၊ Device flow။
- **Acceptance:** Favorite/Unfavorite၊ Continue reading ပြန်ဆက်၊ Next/Prev ≥ Fixture site ၅ ခု၊ Data မပျောက် (Update from previous build)။
- **Risks:** Site Compatibility၊ Scope creep (Discovery)၊ Position Migration data loss။
- **Result:** Novel Reader ကို Library အဖြစ်သုံးနိုင်။

## R-08 — Reader Display Modes & In-page Reader Settings
- **Lineage:** Phase 03 Part 2 (Reader Settings) + 04-I10-B (Translation display) ကို In-page သို့ဆက်။
- **Objective:** Original / Myanmar / Bilingual၊ Font/Size/Spacing/Theme ကို In-page တွင်။
- **Current:** Reader Screen တွင်သာ [D]။
- **Missing:** In-page CSS (Injected style tag; Page style နှင့်မပြေး)၊ Bilingual (paragraph အောက် original သို့ side-by-side)၊ Myanmar font handling၊ Mode toggle ကို Cache မှ ချက်ချင်းပြ (Network မလို)။
- **Tasks:** ၁) Scoped CSS (`[data-mnb]` မသုံး — Registry node ကို class ထည့်သည့်နည်း TD-04 နှင့်ညှိ) ၂) 3-way display ၃) Settings (Reader Settings Repo ပြန်သုံး) ၄) Focus Reader (ReaderScreen) = **Optional Mode** (UD-03 ✔) — အဓိက Reading Experience သည် Original WebView ထဲ In-page translation။
- **Dependencies:** R-05, R-06။
- **Files:** `readermode/*`, `reader/ReaderSettings*`, `ui/ReaderScreen.kt`, JS assets။
- **Security:** CSS string ကို User Value (number/enum) မှသာ build; HTML မဆောက်။
- **Testing:** Fixtures ပေါ် Style test; Device: Font size ပြောင်းရာ Scroll ထိန်း; Dark/Light; Burmese font render ကို OS ၃ မျိုး။
- **Acceptance:** Mode ၃ ခုအကြား ပြောင်းလဲမှု Network မလို၊ Scroll မပျက်၊ Page Layout မပျက်။
- **Risks:** Site CSS `!important` က Override; Bilingual layout မျက်နှာပြင်ကျဉ်း။
- **Result:** Premium Reading Experience။

## R-09 — Shortcut Groups & Safe Mode
- **Lineage:** Browser 2C (Shortcuts home) ကို ဆက်။
- **Objective:** User ဖန်တီးသော Shortcut Group များအကြား Safe Mode ON/OFF ပြောင်း။ **Content Unlocker မဟုတ်၊ Filter/Bypass မလုပ်။**
- **Current:** Group/Safe Mode မရှိ [U]; ၅ Default site + edit [C]။
- **Tasks:** ၁) Group model (UD-07 သတ်မှတ်ချက်) ၂) Safe Mode toggle = Visible group ရွေးပြောင်းရုံ (NewTabPage Tile များ) ၃) Settings entry (R-01)။
- **Dependencies:** R-01။ Files: `ui/premium/{Shortcut,DefaultShortcuts,NewTabPage,UiPreferences}.kt`။
- **Security:** Safe Mode သည် Navigation/UrlPolicy/WebView setting ကိုမပြောင်း (Test ဖြင့်သက်သေ); URL ထည့်ရာတွင် `UrlPolicy`။
- **Testing:** Group CRUD, Toggle persistence, Guard test "Safe Mode မှ UrlPolicy မထိ"။
- **Acceptance:** Toggle ၁ tap ဖြင့်ပြောင်း၊ Data မပျောက်၊ Unlocker အပြုအမူ ၀။
- **Risks:** "Safe Mode" ကို User Content Filter ဟုနားလည်ခြင်း — UI စာသားတွင် ရှင်းပြ။
- **Result:** Shortcut စီမံမှု ပြည့်စုံ။

## R-10 — Browser Completeness Decisions
- **Lineage:** 2F Gap G5–G7။
- **Objective:** ကျန် Browser Feature များကို "လုပ်မည်/မလုပ်မည်" တစ်ခုချင်းဆုံးဖြတ်၊ ပြီးလျှင် လုပ်။
- **Items:** Downloads (UD-11)၊ File chooser (UD-11)၊ Find-in-page၊ Share link/page၊ Bookmark edit/folders၊ Tab close-all/undo/reorder/thumbnails၊ Desktop mode၊ Per-tab error overlay memory၊ Cookie isolation (မလုပ်ကြောင်း စာရွက်စာတမ်းတင်)။
- **Recommendation:** Find-in-page၊ Share၊ Bookmark edit၊ Tab close-all/undo = လုပ်; Downloads/File chooser/Desktop mode = v1.0 တွင် **မလုပ်** (Security surface; Novel Reader ရည်ရွယ်ချက်နှင့် မဆက်စပ်) — UD-11။
- **Dependencies:** R-00။ Security: Download/Chooser လုပ်လျှင် Permission/Storage Review၊ INTERNET-only manifest (SB-10) ပြောင်းလျှင် UD။
- **Testing/Acceptance:** Item တစ်ခုချင်း Device Test + Guard Test မပျက်; "မလုပ်" ဟုရွေးသည့် Item များကို Docs "Known limitation" တွင်မှတ်။
- **Risks:** Scope creep။ **Result:** Browser Feature set ပိတ်ပြီး။

## R-11 — Design System & UX Polish
- **Lineage:** UI Phase 1 (Premium shell), 2D Polish။
- **Objective:** Consistent Design System၊ Animations၊ Dark/Light၊ Accessibility၊ Empty/Error/Loading states၊ UI language (UD-08)။
- **Tasks:** Tokens (Color/Type/Spacing/Shape) တစ်နေရာ၊ Component audit (BottomBar, TabSwitcher, Dialogs, Library, Settings, Reader controls, Banner)၊ Motion spec (≤ 300 ms, Reduce-motion ဆက်ဆံ)၊ TalkBack/Contrast/Touch target ≥ 48dp၊ String resource ထုတ် (hard-coded English → `strings.xml`)၊ Icon/Launcher icon/Splash။
- **Dependencies:** R-08, R-09, R-10။ Files: `ui/**`, `res/**`။
- **Security:** UI စာသားတွင် Key/Chapter မပါ; Screenshot/Recents privacy (FLAG_SECURE) ကို Settings ရွေးချယ်ခွင့်ဖြင့် ဆုံးဖြတ် (UD-08b)။
- **Testing:** Compose UI tests (key flows), Accessibility scan, Screenshot review ၃ Screen size ၊ Font scale 200%။
- **Acceptance:** Design checklist ✔၊ Hard-coded string ၀ (Allow-list)၊ TalkBack ဖြင့် Primary flows ၃ ခုလုပ်နိုင်။
- **Risks:** Polish အဆုံးမရှိ → Checklist ဖြင့်ပိတ်။ **Result:** Premium Look & Feel။

## R-12 — Data Lifecycle, Migration & Cache Stability
- **Lineage:** 2G Migration test + 04-I3 Database။
- **Objective:** v4→v5→v6→… Migration chain ကို Real data ဖြင့်သက်သေ; Cache Eviction; Backup/Export (UD-12)။
- **Tasks:** ၁) Schema JSON ၁–N အားလုံး ၂) `MigrationTestHelper` ကွင်းဆက်အပြည့် (1→N) ၃) Large-data test (Chapters 10k, cache 100MB) ၄) Cache eviction (LRU/Size cap/User "Clear cache") ၅) "Clear all data" လမ်းကြောင်း (Keystore key ကိုလည်းဖျက်ရန် UD) ၆) Export/Import (UD-12 — ရွေးလျှင်)၊ ၇) `fallbackToDestructiveMigration` မသုံးကြောင်း Guard Test ထည့်။
- **Dependencies:** R-05, R-07 (Schema တည်ငြိမ်)။ Security: Export ဖိုင်တွင် API Key မပါ (Guard Test)။
- **Acceptance:** Update-from-every-previous-version Device Test; Migration Tests အားလုံး passed (skipped ၀)။ **Risks:** Data loss; Cache ကြီးထွားခြင်း။ **Result:** Update လုပ်သော User Data မပျောက်။

## R-13 — Security Audit & Privacy
- **Lineage:** 06-A/B Security + SECURITY.md + 2F WebView security။
- **Objective:** Feature အားလုံး Freeze နောက် လွတ်လပ်စွာ Audit။
- **Checklist:** Threat model (Malicious page → Extraction script/Replace/Prompt injection/XSS via translation output)၊ WebView settings ပြန်စစ်၊ `UrlPolicy` Fuzz (userinfo, IDN/punycode, `intent:`, `javascript:`, `data:`, long URL)၊ Manifest (INTERNET only, allowBackup=false, exported components)၊ Network Security Config၊ Dependency vulnerability scan (Network ဖွင့်ရမည် — ခွင့်ပြုချက်)၊ R8/obfuscation (UD)၊ Log scan (`adb logcat` Key/Chapter)၊ Keystore invalidation behaviour၊ Release APK secrets scan၊ Privacy statement (Chapter text သည် Google သို့သွား)၊ Screenshots/Recents။
- **Dependencies:** R-08, R-09, R-10, R-12။
- **Acceptance:** Finding များ Severity ခွဲ၊ High/Medium ပိတ်၊ Low ကို Accepted-risk လက်မှတ်ဖြင့် မှတ်; `SECURITY.md` ပြန်ရေး။ **Risks:** Late finding ကြောင့် Rework။ **Result:** Release-grade Security။

## R-14 — Performance, Memory & Compatibility Matrix
- **Objective:** အမှန်တကယ် Novel Site/Device များတွင် အလုပ်လုပ်ကြောင်းသက်သေ။
- **Performance:** Long chapter (5k paragraphs)၊ DOM batch replace (jank < 16ms frame budget target)၊ 6 live WebView memory ceiling၊ Cold start၊ Background worker battery/Doze၊ Cache IO၊ ANR watch။
- **Compatibility:** Site Matrix (Bundled ၅ + Noveldex + CJK ၂+ User-named) × Reader Mode ON/OFF/Translate; Android API 24, 28, 31, 34, (latest) × ကိုယ်ပိုင်ဖုန်းအနည်းဆုံး ၃ မျိုး; WebView version အဟောင်း/အသစ်; Myanmar font (Pyidaungsu/Noto) rendering။
- **Dependencies:** R-12 (Schema Freeze)၊ R-11။ **Acceptance:** Matrix ဇယားတွင် Pass/Known-fail မှတ်; Known-fail ကို Release notes တွင်ဖော်ပြ; Memory/Startup Target (R-00 Baseline နှင့်နှိုင်း)။
- **Risks:** Device ရရှိမှု; Site ပြောင်းလဲမှု။ **Result:** "ဘယ်မှာအလုပ်လုပ်သည်" ပြတ်သားသော ပြောဆိုချက်။

## R-15 — Test Automation Consolidation & Release Engineering (Direct APK Beta)
- **Lineage:** Phase 06-B4 Final QA + RELEASE-CHECKLIST။ UD-09 ✔ (ပထမ Release = Direct APK Beta)။
- **Objective:** CI ကို Release-ready၊ Signing/Versioning/R8/Update process ပြည့်စုံစေ။
- **Current:** CI = Unit + Debug APK သာ; Release/Lint/Signing/R8 မရှိ; `0.2.0-phase02` [C]။
- **Tasks:**
  1. CI: Lint + Unit + `assembleRelease` (+ JS fixture test — UD-19 ခွင့်ပြုမှ) + Artifact upload။
  2. Gradle Wrapper Pin။
  3. **R8/Minify** — UD-14 ဖွင့်ထား (အကြံပြု On)။ On ဖြစ်လျှင် Rules (Room, WorkManager, Compose, org.json) + Full test + Device pass။
  4. **Versioning:** Beta = `1.0.0-beta.N`၊ Stable = `1.0.0`; `versionCode` အမြဲတိုး (Formula/CI) + Guard Test (TD-15, TD-19)။
  5. **Signing:** Owner ကိုယ်တိုင်ဖန်တီး၊ Repo ပြင်ပ၊ CI Secret/Local (UD-18 ဖွင့်ထား)။ **Key တစ်ခုတည်းဖြင့် Update အားလုံး Sign ရမည်** — Backup ၂ နေရာ Owner တာဝန် (ပျောက်လျှင် User Data ဆုံး)။
  6. **Update process (Direct APK):** Signed APK + SHA-256 + Release notes + Known limitations ကို Hosting (UD-21 ဖွင့်ထား) တွင်ထုတ်; Install-over စစ်ဆေး (Package `com.mnb.browser` တူ၊ Cert တူ၊ versionCode တိုး၊ Data မပျောက်); "Unknown sources" Install လမ်းညွှန်; Downgrade မရကြောင်း + Rollback = Hotfix ဗားရှင်းအသစ်ထုတ် (TD-19)။ In-app Update check/Install = UD-20 ဖွင့်ထား (အကြံပြု Manual)။
  7. Release notes + Known limitations template။ 8) Test inventory ပြန်ရေး (အရေအတွက်စစ်မှန်)။
- **Dependencies:** R-13, R-14။
- **Security:** Keystore/Password Repo ထဲမဝင် (`ProjectSecretsGuardTest`); Release APK Secrets scan; Manifest INTERNET-only မပြောင်း (UD-20 (c) ရွေးမှသာပြောင်း)။
- **Testing:** Release build CI အစိမ်း; Signed APK ကို Clean Device တွင် Install + Smoke; **Install-over test: beta.N-1 → beta.N (Data/Key/Tabs/Library မပျောက်)**; R8 On Full pass။
- **Acceptance:** Signed Beta APK + Checksum + Notes ထုတ်ပြီး; Install-over ✔; CI Release build အစိမ်း; Update လမ်းညွှန်စာ ရှိ။
- **Risks:** R8 က Reflection/Room/WorkManager ပျက်; Signing key ဆုံးရှုံး; Install-over Migration ပျက်။
- **Result:** ထပ်ခါထပ်ခါ ထုတ်နိုင်ပြီး Update လုပ်နိုင်သော Direct-APK Release Pipeline။

## R-16 — Direct APK Beta → Stable v1.0.0 + Maintenance
- **Lineage:** အသစ် (UD-09 ✔: ပထမ Release = Direct APK Beta)။
- **Objective:** Beta ထုတ်ပြီး Feedback ဖြင့် Stable `1.0.0` သို့ရောက်၊ ထိန်းသိမ်းမှုစနစ်သတ်မှတ်။
- **Sub-steps:**
  - **R-16A Beta.1 (`1.0.0-beta.1`):** Full Device Test (Final Acceptance Checklist §1–10)၊ Signed APK ဖြန့်ဝေ (UD-21)၊ Known limitations ရိုးသားစွာဖော်ပြ။ Beta Gate = Checklist ပြည့်မီ **သို့မဟုတ်** မပြည့်မီ Item များကို User လက်မှတ်ဖြင့် "Beta-known" မှတ်။
  - **R-16B Beta.N:** Bug Fix/Compat Fix သာ (Feature freeze); Beta တစ်ခုချင်း Install-over test (R-15)။
  - **R-16C Stable `1.0.0`:** Checklist ၁၀၀% ✔ (သို့မဟုတ် ◐ Accepted-risk လက်မှတ်ပါ)၊ P0/P1 Bug ၀၊ Tag `v1.0.0`။
- **Maintenance Strategy:** SemVer; Hotfix branch `hotfix/x.y.z`; Bug triage (P0 Key/Data loss/Crash → 48h); Gemini Model/API deprecation Watch (Allow-list + Default ကို App Update ဖြင့်ပြောင်း; Model Reroute/ပျောက်ခြင်းကို Release တိုင်း Live စစ်); WebView/Android API Compatibility Rerun; Site Compat Fix = Fixture-first; Bug Feedback channel (UD-10/UD-21 ဖွင့်ထား)။
- **Dependencies:** R-15။
- **Testing:** Device Matrix (R-14) + Install-over + Live Gemini။
- **Acceptance:** Beta Gate (16A) / Stable Gate (16C) အထက်အတိုင်း။
- **Risks:** Beta တွင် Data-loss Bug; Gemini API/Model ပြောင်း; Tester Feedback မစုံ။
- **Result:** Direct APK ဖြင့် Beta → Stable `1.0.0` ထုတ်ပြီး ထိန်းသိမ်းနိုင်။

## R-17 — Google Play Release (Post-v1.0 သီးခြား Milestone)
- **Lineage:** UD-09 ✔ ("Google Play ကို နောက်ပိုင်း သီးခြား Milestone")။ **v1.0 Release Criteria ထဲမပါ။**
- **Objective:** Stable `1.0.0` ပြီးနောက် Play Store သို့ ထုတ်ဝေရန် ပြင်ဆင်။
- **Scope (Planning-level — Stable ပြီးမှ Detail):** Target API requirement ကို ထိုနေ့ရက်ဖြင့်စစ် (လက်ရှိ `targetSdk 34`)၊ AAB၊ Play App Signing (Beta APK Key နှင့် ဆက်ဆံပုံ — **Beta သုံးသူများ Play version သို့ Update မရနိုင်သော Signing ကွာဟမှုကို** ကြိုစီစဉ်)၊ Data-safety form၊ Privacy policy URL၊ Content/Browser app policy review၊ Testing tracks၊ Store listing၊ Manifest/Permission ပြောင်းလဲမှု။
- **Dependencies:** R-16C (Stable)။ **USER DECISION လိုအပ်** (Play account၊ Key/Signing ဗျူဟာ၊ Policy) — ယခု မစီစဉ်သေး။
- **Acceptance/Risks:** Stable ပြီးမှ Roadmap Revision ဖြင့် ဖြည့်။ Risk = Play Policy Rejection၊ Key ဆက်ဆံမှု၊ Target API ပြောင်းမှု။

# PART D — Roadmap Phase တစ်ခုပြီးလျှင် နောက်တစ်ခု ဆက်နိုင်ကြောင်း သက်သေ ("Gate")

| Gate | ဆက်ရန် သက်သေ |
|---|---|
| R-00 → R-01..R-02 | Device Test ဇယား + Migration Test passed + Doc/Code ကွာဟချက် ၀ |
| R-02 → R-03 | State-machine tests အစိမ်း၊ Token model Design ကို Review |
| R-03 → R-04 | Fixture Matrix pass၊ Node-anchor ၁၀၀% |
| R-04 → R-05 | Restore/Scroll/Cancel Device pass |
| R-05 → R-06 | Live Gemini ၃ site pass; Cache hit သက်သေ; Key Leakage Test အစိမ်း |
| R-05/06 → R-07/08 | Chapter identity + Cache Schema တည်ငြိမ် |
| R-08..R-10 → R-11 | Feature Freeze လက်မှတ် |
| R-11 + R-12 → R-13 | Schema/UI Freeze |
| R-13 + R-14 → R-15 | High/Medium Finding ၀; Matrix ရလဒ်ရှိ |
| R-15 → R-16 | Signed Beta APK + Checksum + Install-over test ✔ |
| R-16C → R-17 | Stable `1.0.0` + User ၏ Play ဆုံးဖြတ်ချက် |

# PART E — Final Release Criteria (အကျဉ်း — အပြည့်အစုံ MNB-FINAL-ACCEPTANCE-CHECKLIST)
၁) In-page Translation (R-05) Device + Live ၃ Site+ ၂) Original/OFF/Restore မှန် ၃) Direct Gemini BYOK သာ၊ Gateway ၀ ၄) Google-only default search ၅) WebView Security Baseline မပျက် ၆) Migration Update Test ၇) CI Green on exact Release commit + Owner-signed APK (Direct APK Beta → Stable; Play = R-17 သီးခြား) ၈) Device Matrix ရလဒ် ၉) Docs မှန် ၁၀) Known limitations ရိုးသားစွာဖော်ပြ။
