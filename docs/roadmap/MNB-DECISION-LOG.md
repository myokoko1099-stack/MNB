# MNB — Decision Log

ရက်စွဲ: 2026-11-08။ ဤစာတမ်းတွင် (A) Claude ကိုယ်တိုင်ချမှတ်သော Technical Decision များ (TD)၊ (B) User ဆုံးဖြတ်ရန်လိုသည်များ (UD) ပါသည်။
**မပြောင်းရသော ကန့်သတ်ချက်များ** (ခွင့်ပြုချက်မရှိ မပြောင်း): Google-only Default Search · Direct Gemini BYOK (Gateway/Google Cloud Translation မထည့်) · WebView Security · Safe Mode ≠ Content Unlocker · မူလ Phase History မဖျက်။

Format: ၁) လက်ရှိပြဿနာ ၂) ရွေးချယ်စရာ ၃) အကြံပြုချက် ၄) အကြောင်းပြချက် ၅) အန္တရာယ် ၆) လက်ရှိ Code နှင့် Compatibility

---

# A — Claude Technical Decisions

## TD-01 Roadmap နံပါတ်စနစ်
1. **ပြဿနာ:** "Phase 02" vs "2A–2G"၊ PR #35/#36 Branch တူ၊ UI Phase 1 သီးခြား (Audit §2.3)။
2. **ရွေးချယ်စရာ:** (a) မူလအမည်ကို ပြန်နံပါတ်ပေး (b) မူလအမည် အပြည့်ထား + Roadmap အသစ်ကို `R-xx` ဖြင့် ခွဲ (c) မူလအမည်ဆက်သုံးပြီး ဆက်ထည့်
3. **အကြံပြု:** (b) — Lineage ကွက်ဖြင့် ပြန်ချိတ်၊ `06-C2…C4` ကို R-01 ဖြင့်ဆက်။
4. **အကြောင်း:** User က မူလ History မဖျက်ရဟုမှာ; (a) သည် History ဖျက်ရာရောက်; (c) သည် ရောထွေးမှုထပ်ဆောင်။
5. **အန္တရာယ်:** နံပါတ်စနစ် ၂ ခုကို ခေါင်းထဲ တပြိုင်နက်ကိုင်ရ။ → Roadmap Part A ဇယားဖြင့်ပြေ၊ Branch ကို `r-xx-*` ဟုအမည်ပေး။
6. **Compatibility:** Code/Branch မထိ။ Docs သာ။

## TD-02 Translation Architecture ကို မပြန်ရေး
1. **ပြဿနာ:** In-page လိုအပ်သော်လည်း Engine က Chapter-level/Reader Screen ကို ဦးတည်ပြီးသား။
2. **ရွေးချယ်စရာ:** (a) Engine အသစ်ရေး (b) Provider/Service/Chunk/Memory/Quality KEEP + DOM-side layer အသစ်ထပ် (c) Reader Screen တွင်သာကျန်
3. **အကြံပြု:** (b)
4. **အကြောင်း:** Provider interface၊ Guards၊ Error handling၊ Tests (CI အစိမ်း) ရှိပြီး။ ပြဿနာက DOM နှင့်ချိတ်ရာတွင်သာ။
5. **အန္တရာယ်:** Chunker ၏ Input အမျိုးအစား (Chapter text) နှင့် Paragraph list မကိုက်နိုင်။ → R-05 Task 1 တွင် Adapter (`ParagraphUnit`) ရေး၊ မကိုက်လျှင် Chunker ကို Extend (ပြန်မရေး)။
6. **Compat:** `TranslationProvider` signature မပြောင်း။ `GeminiPromptBuilder` Extend သာ။

## TD-03 JS Bridge မသုံး — `evaluateJavascript` တစ်လမ်းသာ
1. **ပြဿနာ:** In-page Replace အတွက် Native↔Page ဆက်သွယ်ရန်လို။
2. **ရွေးချယ်စရာ:** (a) `addJavascriptInterface` (b) `androidx.webkit` `WebMessageListener` (Dependency အသစ်) (c) `evaluateJavascript` Kotlin→JS + Callback return
3. **အကြံပြု:** (c)
4. **အကြောင်း:** (a) သည် Security Baseline ကိုဖောက် (Page က Native ကို ခေါ်နိုင်); (b) Dependency ထည့်ရ + Origin allow-list ရှုပ်; (c) Surface ပိုမလာ၊ လက်ရှိ `runExtraction` နှင့် ပုံစံတူ။
5. **အန္တရာယ်:** Payload ကြီးလျှင် `evaluateJavascript` String ကြီး; Streaming ကို Batch ဖြင့်ပြေရ။ → Batch cap၊ Chunk တစ်ခုချင်း dispatch။
6. **Compat:** `WebViewSecurityGuardTest` (one WebView path) မပျက်၊ JS dispatch method တစ်ခုထည့်သာ။ `androidx.webkit` ထည့်လိုလျှင် UD ဖြင့်သာ။

## TD-04 Paragraph Anchoring = JS closure registry (DOM attribute မထည့်)
1. **ပြဿနာ:** Extraction ရလဒ်သည် Node သို့ပြန်မညွှန်း (W-1)။
2. **ရွေးချယ်စရာ:** (a) DOM `data-mnb-id` attribute (b) Closure-held Node array + token (c) XPath/CSS selector မှတ်
3. **အကြံပြု:** (b)၊ Fallback (c) (Node ပျက်လျှင် Re-extract)။
4. **အကြောင်း:** (a) Page က မြင်/ပြင်/ဖျက်နိုင်၊ Site CSS/Script နှင့်တိုက်; (c) Page re-render တွင် ပျက်လွယ်။
5. **အန္တရာယ်:** Page JS က Node ကို Replace (React) → Registry stale။ → MutationObserver `STALE` + Re-extract (Cache hit ကြောင့် ဈေးသက်သာ)။
6. **Compat:** `ExtractionScript` Output (`paragraphs[].t/h`) ကို Backward-compatible ထား → Preview/Reader မပျက်။

## TD-05 Page Identity model
1. **ပြဿနာ:** Navigation/Reload အလယ် Stale ရလဒ်ထည့်နိုင် (W-2)။
2. **ရွေးချယ်စရာ:** (a) URL ကိုသာ နှိုင်းယှဉ် (b) `tabId` + `navGeneration` + `docToken`
3. **အကြံပြု:** (b)
4. **အကြောင်း:** Same URL reload၊ SPA၊ Redirect တို့ကို URL ဖြင့်ခွဲမရ။ `docToken` ကို JS ဘက်တွင်လည်းစစ်၍ Double guard။
5. **အန္တရာယ်:** Android callbacks အစီအစဉ်ရှုပ် (`onPageFinished` ထပ်ခါ — 2E note)။ → Table-driven state-machine tests (R-02)။
6. **Compat:** `WebViewController` callbacks ကိုသာ ဖတ်၊ API မပြောင်း။

## TD-06 Reader Mode ကို Browser layer တွင် state machine ဖြင့်; ReaderScreen ကို Optional "Focus Reader" ထား; Translation မပါသော R-04 ကို အရင်
1. **ပြဿနာ:** Vision က Preview Screen မလိုရ၊ ရှိပြီးသား ReaderScreen အရင်းအမြစ်ကို ပစ်ရန်လား။
2. **ရွေးချယ်စရာ:** (a) ReaderScreen ဖျက် (b) ထားပြီး In-page ကို Primary လုပ် (c) In-page မလုပ်၊ ReaderScreen ကိုသာတိုးတက်
3. **အကြံပြု:** (b) + R-04 ကို Translation မပါဘဲ ခွဲလုပ်။
4. **အကြောင်း:** (a) သည် ကုန်ကျပြီးသား Reader Settings/Position ဖျက်ရာ; (c) သည် Vision ကိုမလိုက်နာ။ R-04 ခွဲခြင်းဖြင့် DOM Mechanics ကို ဘာသာပြန်အမှားမရောဘဲသက်သေပြ။
5. **အန္တရာယ်:** UI ၂ မျိုးထိန်းရ → UD-03 ဆုံးဖြတ်ပြီး: Focus Reader = Optional Mode။
6. **Compat:** `ReaderStateManager`, `ReaderScreen` မပြောင်း။ `BrowserScreen` ၏ Extract button အစား Toggle (R-04 တွင်သာ)။

## TD-07 Translation Cache = Room v5 additive table (Paragraph-level)
1. **ပြဿနာ:** Cache chapter-level သာ (W-5)။
2. **ရွေးချယ်စရာ:** (a) Chapter-level ကိုသာဆက် (b) Paragraph-level table (c) File cache
3. **အကြံပြု:** (b)၊ Key = host+path+paraHash+lang+modelId+promptVersion။
4. **အကြောင်း:** Progressive replace/resume/dedup အတွက်လို; Room တွင်ရှိပြီး Migration test ပုံစံရှိ။
5. **အန္တရာယ်:** Cache ကြီးထွား၊ Key collision၊ Cross-chapter dedup က Glossary context မတူစေ။ → Size cap + LRU + Prompt version invalidation; Dedup scope ကို R-05 တွင် Test ဖြင့်ဆုံးဖြတ်။
6. **Compat:** Additive migration 4→5 (`fallbackToDestructiveMigration` မသုံး)။ `ChapterTranslation` ကျန်။

## TD-08 R-01 (Settings) ကို In-page ရှေ့ထား
1. **ပြဿနာ:** Settings ၂ ခု၊ Gemini Config placeholder (W-6)။
2. **ရွေးချယ်စရာ:** (a) နောက်ဆုံးမှ Polish အနေဖြင့် (b) R-05 မတိုင်မီ
3. **အကြံပြု:** (b)
4. **အကြောင်း:** Live Translation စမ်းရန် Model/Key Config ယုံကြည်ရမည်။ Risk နည်း၊ မူလ 06-C1 မှ ဆက်ရန်ညွှန်းထား။
5. **အန္တရာယ်:** Key UI ရွှေ့ရာ Leakage regression → `ApiKeyLeakageTest` Gate။
6. **Compat:** Key flow (controller→store) မပြောင်း၊ UI ထားရာသာ။

## TD-09 Phase အသစ် R-00 (Baseline Verification) ထည့်
1. **ပြဿနာ:** 2B/2F/2G/06-B1/06-C1 Report များ "NOT BUILT/TESTED" ဖြင့်စပြီး CI အစိမ်းသာရ၊ Device Test ၀၊ Schema test skip၊ Docs Stale။
2. **ရွေးချယ်စရာ:** (a) တိုက်ရိုက် Feature ဆက် (b) R-00 Verification phase အရင်
3. **အကြံပြု:** (b)
4. **အကြောင်း:** User ၏ "Code ရေးပြီးရုံ Completed မသတ်" စည်းကမ်းနှင့်ကိုက်; Bug ကို Feature အပေါ်ပုံနေပါက ရှာခက်။
5. **အန္တရာယ်:** အချိန်ကုန်၊ Bug အများကြီးတွေ့ → ကောင်းသော အရာ၊ Fix ကို R-00 ထဲ ကန့်သတ်ထား။
6. **Compat:** Feature မပြောင်း။

## TD-10 Schema JSON commit ကို R-00 သို့ ရှေ့ရွှေ့၊ Migration Hardening ကို R-12
1. **ပြဿနာ:** 2G ၏ schema test skip (G-5)။
2. **ရွေးချယ်စရာ:** (a) R-12 တွင်သာ (b) စာရင်း commit ကို R-00၊ ကျန်ကို R-12
3. **အကြံပြု:** (b)
4. **အကြောင်း:** v5/v6 မပြောင်းမီ Baseline v3→v4 သက်သေရှိမှ နောက် Migration များ ယုံကြည်ရ။
5. **အန္တရာယ်:** `3.json` သည် derive (placeholder hash) — Test ကို Robolectric တွင်ပျက်နိုင်။ → R-00 တွင်ဖြေရှင်း (`@Ignore` ဖြင့် မဖုံး)။
6. **Compat:** `app/build.gradle.kts` ၏ sourceSets/mustRunAfter ရှိပြီး။

## TD-11 Gateway / Google Cloud Translation မထည့်
1. **ပြဿနာ:** B-notes တွင် Gateway ကို "alternative path" ဟုဖော်ပြ — Request path ထဲပြန်ဝင်လာနိုင်သည့် Risk။
2. **ရွေးချယ်စရာ:** (a) Fallback provider အဖြစ်ထား (b) လုံးဝမထည့်
3. **အကြံပြု:** (b) — User ခွင့်ပြုချက်မရှိ။
4. **အကြောင်း:** User ၏ ပြတ်သားသောညွှန်ကြားချက်။ Guard Test များက Gateway ကို Request path တွင်မရှိကြောင်း ထိန်း။
5. **အန္တရာယ်:** Gemini ပြတ်တောက်လျှင် Fallback မရှိ။ → Retry/Error UX (R-06)။
6. **Compat:** `ReaderTranslationGateway` (Reader internal port) အမည်ကို အမှားမခေါ်အောင် Docs ရှင်း (ရှိပြီး)။

## TD-12 Extraction Script ကို `assets/js/` သို့ရွှေ့ + JS Fixture Test
1. **ပြဿနာ:** Kotlin raw string (`$`/`"""` ကန့်သတ်၊ Test ခက်) (W-9)။
2. **ရွေးချယ်စရာ:** (a) ဆက်ထား (b) assets ဖိုင်၊ Robolectric/JVM JS engine (Dependency) (c) Instrumented test (d) CI တွင် Node + jsdom
3. **အကြံပြု:** (b) assets ဖိုင်သို့ရွှေ့ရန်သာ ယခုဆုံးဖြတ်။ JS Test harness (d: Node+jsdom, CI-only) သည် Dependency/Tool အသစ်ဖြစ်၍ **UD-19 ဖြင့် User ခွင့်ပြုမှသာ R-03 တွင် ထည့်** (ယခု Dependency မထည့်ရဟု ညွှန်ကြားထား)။
4. **အကြောင်း:** Lint/Test/Review လွယ်; Raw string ကန့်သတ်မှ ကင်းလွတ်။
5. **အန္တရာယ်:** Asset load error (path)၊ Test harness ရွေးချယ်မှု ရှုပ်။
6. **Compat:** `WebViewController.runExtraction` မပြောင်း၊ Script source ကိုသာ Asset မှဖတ်။

## TD-13 Cancellation = token + HTTP disconnect (Coroutines မသုံး)
1. **ပြဿနာ:** Provider sync/blocking (W-4)။ Navigation တွင် Network ပိတ်ရ။
2. **ရွေးချယ်စရာ:** (a) Coroutines (B1 Decision 1 ကို ပြန်ပြောင်း) (b) Executor + token + `disconnect()`
3. **အကြံပြု:** (b)
4. **အကြောင်း:** Dependency မထပ်; လက်ရှိ Guard/Worker ပုံစံနှင့်ကိုက်; Cancel ကို Token ဖြင့်လုံလောက်။
5. **အန္တရာယ်:** Blocking read ကို Cancel ရန်ခက် → `HttpURLConnection` ကို Client ၏ Handle ထုတ်ပေး၊ Read timeout 90 s ကို Reader Mode တွင်လျှော့ (Config)။
6. **Compat:** `GeminiHttpClient` interface တွင် `cancel()` ထည့်ရန်လို (additive)၊ Fakes ကို update။ Coroutines လိုလျှင် UD-15။

## TD-14 Output → DOM ကို `textContent` သာ (SB-9)
1. **ပြဿနာ:** Model Output/Page စာသား XSS။
2. **ရွေးချယ်စရာ:** (a) `innerHTML`+sanitize (b) `textContent`
3. **အကြံပြု:** (b)
4. **အကြောင်း:** Bilingual/formatting လိုအပ်လျှင်လည်း Element ကို Native ဖြင့်ဆောက်၍ `textContent` ထည့်။
5. **အန္တရာယ်:** Inline formatting (italic/bold) ဆုံးရှုံး → Paragraph ထဲ Inline node ရှိပါက Strategy ကို R-05 တွင် Fixture ဖြင့်ဆုံးဖြတ် (Plain text paragraphs သာ replace)။
6. **Compat:** Guard Test ထည့် (JS asset တွင် `innerHTML`/`eval`/`Function(` မရှိ)။

## TD-15 Versioning
1. **ပြဿနာ:** `0.2.0-phase02` / versionCode 1 (G-2)။
2. **ရွေးချယ်စရာ:** (a) Phase နာမည်တွဲ (b) SemVer
3. **အကြံပြု:** (b) — UD-09 ဆုံးဖြတ်ချက်အရ ပထမ Release = Direct APK Beta: `1.0.0-beta.N` → Stable `1.0.0`; `versionCode` သည် Update-in-place အတွက် အမြဲတိုးရမည် (monotonic, CI/Formula)။
4. **အကြောင်း:** Phase နာမည်များ ရောထွေးပြီး Release ဗားရှင်းနှင့်မသက်ဆိုင်။
5. **အန္တရာယ်:** versionCode ကျော်/ပြန်ကျဆင်း → Store/Update ပြဿနာ။ → CI ဖြင့်တွက်၊ Guard Test။
6. **Compat:** `app/build.gradle.kts` တစ်နေရာ။

## TD-16 Multi-module မခွဲ
1. **ပြဿနာ:** Package များစွာကြီးထွားလာ။
2. **ရွေးချယ်စရာ:** (a) `:app` တစ်ခုတည်း (b) Module ခွဲ
3. **အကြံပြု:** (a) + Package boundary Guard Test
4. **အကြောင်း:** Build/CI ရှုပ်ထွေးမှု၊ Release ရှေ့ Risk; အကျိုးနည်း။
5. **အန္တရာယ်:** ကြီးထွားလျှင် Build ကြာ။ → v1.0 နောက် ပြန်သုံးသပ်။
6. **Compat:** မပြောင်း။

## TD-17 Library နာမည်ခွဲ `novellibrary/`
1. **ပြဿနာ:** `library/` ရှိပြီးသား (Browser bookmarks/history)။
2. **အကြံပြု:** Novel Library အတွက် `novellibrary/` package ကို သီးခြားအမည်ပေး။
3. **အကြောင်း:** Phase အမည်ရောထွေးမှုကဲ့သို့ Package အမည်ရောထွေးမှု မဖြစ်စေရန်။ (အန္တရာယ်: ၀။ Compat: ၀။)

---

## TD-18 Gemini 3 API အသစ်နှင့် လက်ရှိ Request Shape Compatibility စစ်ရန် (R-00/R-01)
1. **ပြဿနာ:** Third-party အစီရင်ခံစာများအရ Gemini 3 API တွင် `thinking_level` သည် `thinking_budget` ကိုအစားထိုး၊ Sampling parameter အချို့ ဖယ်ရှား၊ Function-calling တင်းကျပ်ဟု ဆိုသည် [U — Official docs မဖတ်ရသေး]။ လက်ရှိ `GeminiConfig`/`GeminiPromptBuilder` တွင် `temperature` နှင့် `generationConfig` ပါ [D: SESSION-06-B1-REPORT]။ Thinking token များကို Output အဖြစ် ငွေတောင်းနိုင်။
2. **ရွေးချယ်စရာ:** (a) Model ID သာ ပြောင်း (b) Official docs ဖတ်ပြီး Request shape ပြင် + Live စမ်း
3. **အကြံပြု:** (b) — R-00 တွင် Live စမ်း၊ R-01 တွင် Config ပြင်။
4. **အကြောင်း:** Model ID ပြောင်းရုံဖြင့် 400 Error/Behaviour ပြောင်း/Cost တိုးနိုင်။
5. **အန္တရာယ်:** `GeminiError.BAD_REQUEST` ပေါ်လာ၊ Prompt output ပုံစံပြောင်း → R-05 index-tagged format ကိုထိ။
6. **Compat:** `GeminiConfig` ၏ model/generation field ကိုသာ ထိ (Provider interface မပြောင်း)။

## TD-19 Direct APK Beta — Update-in-place စည်းမျဉ်း
1. **ပြဿနာ:** UD-09 ဆုံးဖြတ်ချက်အရ Store မပါ၊ Update ကို APK ဖြင့်လုပ်ရ။
2. **ရွေးချယ်စရာ:** (a) Beta တိုင်း Uninstall→Install (b) Same package + Same signing key + versionCode တိုး၍ Install-over
3. **အကြံပြု:** (b)
4. **အကြောင်း:** Uninstall လျှင် Room data၊ Keystore key (API key) ပျောက်။ Install-over သာ Data ထိန်း။
5. **အန္တရာယ်:** Signing key ပျောက်/ပြောင်းလျှင် Install-over မရ → User Data ဆုံးရှုံး; Room Migration မှားလျှင် Update ပြိုကွဲ; Downgrade မရ (DB schema forward-only)။ → Key backup Owner တာဝန် (UD-18 ဖွင့်ထား)၊ R-12 Migration chain test၊ Rollback = Hotfix ဗားရှင်းအသစ်ထုတ် (Downgrade မလုပ်)။
6. **Compat:** `applicationId com.mnb.browser` မပြောင်း; `allowBackup=false` ကြောင့် Reinstall တွင် Key မပါ (သိရှိရန် Docs)။

# B — USER DECISION REQUIRED (ဖွင့်ထားဆဲ — Claude မှ အတည်မပြု)
> UD-05 … UD-19 နှင့် အသစ် UD-20/21 သည် **User မဆုံးဖြတ်ရသေး**။ အောက်ပါ "အကြံပြုချက်" သည် အကြံပြုချက်သာ — ဆုံးဖြတ်ချက်မဟုတ်။ UD-01/02/03/04/09 ကို Section C တွင် Decided အဖြစ်ကြည့်ပါ။

| ID | အကြောင်းအရာ | ရွေးချယ်စရာ | Claude အကြံပြု | အရေးကြီးမှု |
|---|---|---|---|---|
| **UD-05** | **Gemini Safety settings** | (a) Default ထား (b) Novel အတွက် ချိန်ညှိ (User Setting) | **(a) Default + Blocked UX** ဖြင့်စ၊ လိုမှ (b) | Content block ကြောင့် Chapter ပြန်မရ |
| **UD-06** | **အရည်အသွေးပစ်မှတ် + Source languages** | EN/ZH/KO/JA ပဲ? Auto-detect ထည့်? Myanmar Quality Target (Human review အမှတ်) | လက်ရှိ ၄ ဘာသာ; Auto-detect = v1.1; Target ကိုသင်သတ်မှတ် | R-06 Acceptance |
| **UD-07** | **Safe Mode / Shortcut Group သတ်မှတ်ချက်** | Group ဘာတွေလဲ? Safe Mode ON က ဘာကိုပြ/ဖျောက်? (Unlocker မဟုတ်) | Group = User-defined ၂ ခု (Default/Safe); Safe ON = Safe group Tile များပြ | R-09 |
| **UD-08** | **App UI ဘာသာစကား** + Recents Screenshot privacy | UI: English သာ / Myanmar / နှစ်မျိုး; FLAG_SECURE Setting | Strings resource ထုတ်ပြီး EN + MY (MY Review လို) | R-11 |
| **UD-10** | **Crash reporting / Telemetry** | မလုပ် / Opt-in local log export | **မလုပ်** (Privacy; INTERNET-only) + Manual log export | R-16 Maintenance |
| **UD-11** | **Downloads / File chooser / Desktop mode** | v1.0 တွင် လုပ်/မလုပ် | **မလုပ်** (Security surface); Find/Share/Bookmark edit/Tab undo = လုပ် | R-10 |
| **UD-12** | **Backup/Export** (allowBackup=false လက်ရှိ) | (a) မလုပ် (b) Library/Settings Export (Key မပါ) | **(b) v1.0 နောက်** သို့မဟုတ် Optional | R-12 |
| **UD-13** | **minSdk 24 ထိန်းမလား** | 24 / 26 / 28 | 24 ဆက်ထား၊ Myanmar font/WebView ပြဿနာ R-14 တွင်တွေ့လျှင်ပြန်သုံးသပ် | R-14 |
| **UD-14** | **R8/Minify** | Off ထား / On+Rules | **On** (R-15 တွင် Full pass ဖြင့်) | Release size/security |
| **UD-15** | **Coroutines/OkHttp မိတ်ဆက်မလား** | မသုံး (TD-13) / သုံး | **မသုံး** | Dependency |
| **UD-16** | **Branch ရှင်းလင်းမှု** (Merge ပြီး ~၄၀ Branch) | Archive tag ဖြင့်ဖျက် / ထား | Tag ပြီးမှဖျက် — **User ခွင့်ပြုမှ** | R-00 |
| **UD-17** | **Root Docs ပြန်စုံ** | `docs/history/` သို့ရွှေ့ / ထား | ရွှေ့ | R-00 |
| **UD-18** | **Signing Key ပိုင်ဆိုင်မှု** | Owner ကိုယ်တိုင်ဖန်တီး/သိမ်း (Repo ပြင်ပ) | ဤအတိုင်း (Assistant Key မကိုင်) | R-15 |
| **UD-19** | **JS Test harness Dependency** (TD-12) | Node/jsdom (CI only) / Robolectric JS ကိုမသုံး / Instrumented | Node+jsdom CI-only | R-03 |
| **UD-20** (အသစ်) | **Beta Update နည်းလမ်း** | (a) Manual: APK + SHA-256 ကို Download ပြီး Install-over (b) In-app "Update ရှိ/မရှိ စစ်" (HTTPS version JSON) — Network Destination အသစ် (c) In-app Install (`REQUEST_INSTALL_PACKAGES` Permission) | **(a)** ဖြင့် beta.1; (b) ကို နောက်မှ | R-15 |
| **UD-21** (အသစ်) | **Beta Release Hosting / Tester Channel** | GitHub Releases (Private/Public) / ကိုယ်ပိုင် Link / Tester တစ်ဦးချင်းပေး | သင်ရွေးပါ (Repo Private/Public အပေါ်မူတည်) | R-15/R-16 |

## B.2 ဖွင့်ထားသော UD များ၏ အကျိုးဆက် (မဆုံးဖြတ်မီ သိရန်)

| ID | အကြံပြုချက် ရွေးလျှင် | အခြားရွေးလျှင် / မဆုံးဖြတ်လျှင် ဖြစ်မည့်အရာ |
|---|---|---|
| UD-05 Safety settings | Default + "Blocked" UX: Code နည်း၊ Policy ကိုမဖောက်။ Novel အချို့ (အကြမ်းဖက်/လူကြီးအကြောင်းအရာ) Block ဖြစ်နိုင် | Threshold လျှော့လျှင် Block နည်းသော်လည်း Google Policy/Account Risk၊ UI Setting ထပ်လို |
| UD-06 Quality Target + Languages | ၄ ဘာသာ၊ Auto-detect နောက်မှ: R-06 Acceptance တိကျ | Target မသတ်လျှင် R-06 "ပြီး" ကို တိုင်းလို့မရ |
| UD-07 Safe Mode group | Group ၂ ခု: R-09 ရိုးရှင်း | Group အများကြီး/Rule ပါလျှင် UI/Data model ကြီး; Unlocker အဖြစ်နားလည်မှု Risk |
| UD-08 UI language | EN+MY: Strings ထုတ်ရ၊ MY Review လို | EN သာလျှင် မြန်သော်လည်း Myanmar User UX ကျ |
| UD-10 Telemetry | မလုပ်: Privacy ကောင်း၊ Beta Bug စုဆောင်းမှု Manual (UD-21 နှင့်ဆက်) | Crash reporting ထည့်လျှင် Dependency+Privacy+Network Destination အသစ် |
| UD-11 Downloads/File chooser/Desktop | မလုပ်: Attack surface မတိုး | လုပ်လျှင် Manifest/Permission/Security Review ထပ်လို |
| UD-12 Backup/Export | v1.0 နောက်: Scope ထိန်း | မလုပ်လျှင် Reinstall/ဖုန်းပြောင်းတွင် Library/Settings ပျောက် (Beta Tester ထိခိုက်) |
| UD-13 minSdk | 24 ဆက်ထား: User ပိုများ | မြှင့်လျှင် Test Matrix ကျုံ့၊ Myanmar font ပြဿနာ လျော့နိုင်၊ User လျော့ |
| UD-14 R8 | On (R-15 Full pass): APK သေး/Obfuscate | Off ဆက်လျှင် Risk နည်းသော်လည်း Beta APK ကြီး/Reverse လွယ်; Direct APK Beta အတွက်ပင် "On" ကို R-15 တွင် Device pass ဖြင့်သာ |
| UD-15 Coroutines/OkHttp | မသုံး (TD-13): Dependency မတိုး | သုံးလျှင် Cancel/Streaming လွယ်၊ Refactor ကြီး၊ Test ပြန်ရေး |
| UD-16 Branch cleanup | Tag ပြီးဖျက်: Repo ရှင်း | မဖျက်လျှင် Branch ၄၀+ ရှုပ်ပြီး Phase အမည်ရောထွေးမှု ဆက် |
| UD-17 Root Docs ရွှေ့ | `docs/history/`: Root ရှင်း၊ History မဖျက် | မရွှေ့လျှင် Stale Report ၁၅+ ဖိုင် ဆက်ရှိ |
| UD-18 Signing Key | Owner ကိုယ်တိုင်၊ Repo ပြင်ပ၊ **Backup ၂ နေရာ**: **Direct APK တွင် Key ပျောက်=Update မရ=Data ဆုံး** | Assistant/Repo ထဲ Key မထားရ (SB: Secrets guard) |
| UD-19 JS Test harness (Node+jsdom CI-only) | R-03 Extraction Regression ကို CI တွင်ဖမ်းနိုင် | မလုပ်လျှင် Extraction Test ကို Device Instrumented သာ — နှေး/ပျက်လွယ် |
| UD-20 Update နည်းလမ်း | Manual: Permission/Network မတိုး | In-app Check/Install လျှင် Manifest/Security Surface တိုး |
| UD-21 Hosting | — | Private Repo လျှင် GitHub Release Public မဖြစ်၊ Tester Access စီမံရ |

# C — USER DECISIONS ချမှတ်ပြီး (User ကိုယ်တိုင် 2026-11-08)

| ID | User ဆုံးဖြတ်ချက် | Roadmap/Docs အပေါ် သက်ရောက်မှု |
|---|---|---|
| **UD-01** ✔ | Phase 05 = **Gateway** (User အတည်ပြု)။ လက်ရှိ Translation Architecture = Direct Gemini BYOK။ Gateway ပြန်မထည့်။ | Audit/Roadmap Part A တွင် Phase 05 ကို "Gateway၊ Phase 06 က အစားထိုး" ဟုတည်ဆောက် (ခန့်မှန်း [I] မှ User-confirmed သို့)။ TD-11 အတည်ပြု။ Phase 05 ကို "Completed" ဟုမသတ် — **အစားထိုးခံရသည် (Superseded)** |
| **UD-02** ✔ | Discovery ပထမအဆင့် = လက်ရှိဖွင့်ထားသော Page မှ Novel metadata သိမ်းဆည်းခြင်းသာ ဦးစားပေး | R-07 Scope = Page-metadata capture + Library/Favorites/Progress။ Site Search/Parsers/Cross-site Discovery = v1.0 Scope ပြင်ပ (နောက်ပိုင်း Milestone) |
| **UD-03** ✔ | Preview Screen = Debug/Development-only။ Focus Reader = Optional Mode။ အဓိက Reading Experience = Original WebView ထဲ In-page translation | R-04 တွင် Preview ကို Release build မှ ဖယ်/Debug flag နောက်ဝှက်၊ R-08 Focus Reader Optional။ TD-06 အတည်ပြု |
| **UD-04** ✔ | Gemini Model = **Allow-list** ထိန်း။ `gemini-2.5-flash` ကို အတည်ပြုပြီးဟု မယူ။ API support/စျေးနှုန်း/quality စစ်ပြီး Default သတ်မှတ် | အောက်ပါ C.1 |
| **UD-09** ✔ | ပထမ Release = **Direct APK Beta**။ Signing, Versioning, R8, Release CI, Update process ပြည့်စုံရမည်။ Google Play = နောက်ပိုင်း သီးခြား Milestone | R-15/R-16 ပြန်ရေး; **R-17 (Google Play Milestone) အသစ်ထည့်** (v1.0 Criteria ပြင်ပ)။ TD-15/TD-19 |

### C.1 UD-04 — Model စစ်ဆေးမှု (Web research 2026-11-08; Official page + Third-party)

**တွေ့ရှိချက်:**
- **Official Deprecations page** (ဖတ်ခဲ့): `gemini-2.5-flash` = "not deprecated, access limited to past users"၊ Shutdown date မရှိ။ New project များအတွက် `3.5 Flash-Lite` သို့မဟုတ် `3.8 Flash` ကို အကြံပြု။ `gemini-3.8-flash` (2026-09-02 ထွက်)၊ `gemini-3.6-flash` (07-21)၊ `gemini-3.5-flash-lite` (07-21) တည်ရှိ၊ Shutdown မကြေညာ။ `gemini-3.7-flash`/`3.5-flash` = Deprecated + Auto-reroute။
- **Official Changelog** (2026-10-30 အထိ): 3.8 Flash GA၊ 3.5 Flash-Lite GA၊ `gemini-4-argon` GA (10-13)။
- **ကွဲလွဲမှု (ဖြေရှင်းရန်):** Third-party သတင်းများ (AICoder စသည်) က 2026-11-02 တွင် `gemini-2.5-flash`/`2.5-pro` ကို Deprecate၊ **2026-12-01** မှ Request များကို `2.5-flash→3.5-flash-lite`၊ `2.5-pro→3.8-flash` သို့ Auto-reroute ဟုဆို။ ကျွန်ုပ်ဖတ်ခဲ့သော Official page တွင် ထိုကြေညာချက် မတွေ့ (Cache/ရက်စွဲကွာနိုင်) — **Primary source ဖြင့် R-00 တွင် ပြန်အတည်ပြုရန် [U]**။ ထိုနေ့မှစ၍ `gemini-2.5-flash` ကို ဆက်မသုံးသင့်။
- **စျေးနှုန်း (Third-party Aggregator, 2026-10-25 စစ်၊ Official Pricing page မဖတ်ရ [U]):** 3.8 Flash $0.75 in / $3.75 out per 1M (2027-01-01 မှ ၂ ဆဟုဆို → $1.50/$7.50)၊ 3.5 Flash-Lite $0.30/$2.50၊ 2.5 Flash $0.30/$2.50၊ `gemini-4-argon` $2/$10 (12-01 မှ $4/$20ဟုဆို)။
- **Gemini 3 API Breaking changes (Third-party [U]):** `thinking_level` ကို `thinking_budget` အစား၊ Sampling param အချို့ဖယ်၊ Function-calling တင်းကျပ် → TD-18။

**အကြံပြု Allow-list (ဆုံးဖြတ်ပြီးမဟုတ် — R-00/R-01 Live Evaluation ပြီးမှ Lock):**
| Slot | Model ID | အခန်းကဏ္ဍ | မှတ်ချက် |
|---|---|---|---|
| **Default (Provisional)** | `gemini-3.8-flash` | Quality tier | GA၊ Official အကြံပြုသည့် 2 ခုအနက်၊ စျေးနှုန်း 2027-01 တိုးမည်ဟု Third-party ဆို |
| Economy | `gemini-3.5-flash-lite` | စျေးသက်သာ | 2.5-flash ၏ Reroute target ဟုဆို၊ Quality Myanmar အတွက် စစ်ရန် |
| (Excluded v1) | `gemini-2.5-flash` | Legacy | Dec 1 Reroute Risk၊ Official "limited to past users" |
| (Excluded v1) | `gemini-4-argon`, Pro Preview | Premium | စျေးကြီး (Output $10→$20 ဟုဆို)၊ Preview ရှောင် |
`3.6-flash` ကို Fallback အဖြစ် Evaluation ထဲထည့်စမ်းနိုင် (Allow-list ထဲထည့်/မထည့်မှာ ရလဒ်အပေါ်)။

**Default သတ်မှတ်နည်း (Decision Rule):** R-00/R-01 တွင် Golden Set (EN/ZH/KO/JA × ၅ စာပိုဒ်) ကို Candidate များဖြင့် User ၏ Key ဖြင့်စမ်း → (၁) Paragraph-count ကိုက်ညီမှု (၂) Myanmar Unicode (၃) Human Quality အမှတ် (၄) Latency (၅) Token (Thinking token ပါ) နှင့် Cost/Chapter → Quality Target (UD-06 ဖွင့်ထား) ကိုကျော်သည့်အနက် Cost အနိမ့်ဆုံးကို Default။ **`gemini-3.8-flash` သည် Provisional သာ။**
**Cost ခန့်မှန်း [I — Chapter ≈ 3,000 စာလုံး၊ Output ≈ 8–12k token ဟုယူ (Myanmar Tokenization မစစ်ရ)]:** 3.8 Flash ≈ $0.03–0.045/Chapter (2027-01 နောက် ≈ $0.06–0.09)၊ 3.5 Flash-Lite ≈ $0.02–0.03။ Thinking token ပါလျှင် တိုး။ ပမာဏများသည် Evaluation တွင် အစားထိုးရမည်။
**Config စည်းမျဉ်း:** Model ID များသည် Config (Code constant တစ်နေရာ + Allow-list) — Hard-coded ပြန့်ကျဲမထား; Allow-list ပြင်ရန် App Update သာ (Free-text မရ)။ Model ပျောက်/Reroute ဖြစ်လျှင် Default သို့ Fallback + User ကို အသိပေး (R-06)။

**Sources (Web research):**
- [Gemini API Deprecations (Official)](https://ai.google.dev/gemini-api/docs/deprecations?hl=en)
- [Gemini API Release notes (Official)](https://ai.google.dev/gemini-api/docs/changelog)
- [AICoder — 2.5 Pro/Flash Dec 1 reroute (Third-party)](https://aicoder.com/news/news-20261102-gemini-api-deprecates-gemini-2-5-pro-flash-dec-1-auto-route-3-8-flash-3-5-flash-lite)
- [MorphLLM — Gemini API pricing, Oct 2026 (Third-party)](https://www.morphllm.com/gemini-api-pricing)
