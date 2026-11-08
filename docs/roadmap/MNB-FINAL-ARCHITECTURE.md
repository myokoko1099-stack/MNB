# MNB — Final Architecture (အကြံပြု)

အခြေခံ: လက်ရှိ Architecture [C/D] ကို **ဆက်သုံး**၊ ပြန်မရေး။ ပြောင်းသည်များကို "CHANGE" ဟု၊ မပြောင်းသည်များကို "KEEP" ဟု ခွဲထား။ Planning သာ — Code မရေး။
Tag: [C] Code ဖတ်ပြီး · [D] Doc · [I] အကြံပြု/ခန့်မှန်း

## 1. အခြေခံမူ
1. KEEP: Direct Gemini BYOK၊ `TranslationProvider` interface၊ Chunking/Memory/Quality၊ Room၊ WorkManager၊ Secure Key Storage၊ Executor-based (Coroutines မသုံး — ပြောင်းလိုလျှင် UD)။
2. CHANGE (နည်းအောင်): Browser ↔ Reader ချိတ်ဆက်မှု (In-page)၊ Page/Tab Identity၊ DOM Extraction ကို Anchored ဖြစ်စေ၊ Paragraph Cache။
3. မထည့်: NB-Translation-Gateway၊ Google Cloud Translation၊ JavascriptInterface၊ Remote config။

## 2. Module / Package Structure
Single Gradle module `:app` ကို ဆက်သုံး (Multi-module ခွဲခြင်းသည် v1.0 အတွက် မလိုအပ် — TD-16: Build/CI ရှုပ်ထွေးမှုထပ်တိုးမည်၊ အကျိုးနည်း)။ Package နယ်နိမိတ်ကိုသာ တင်းကျပ်ပါ။

```
com.mnb.browser
├─ MainActivity, MnbApplication            KEEP
├─ web/            WebViewController, LiveTabPolicy, WebSessionStore   KEEP
│                  + JsDispatcher (evaluateJavascript wrapper)         NEW
├─ tabs/, navigation/, state/, error/      KEEP
├─ readermode/                                                          NEW
│   ├─ PageSession.kt          (tabId, navGeneration, normUrl, docToken)
│   ├─ ReaderModeController.kt (per-tab state machine)
│   ├─ ReplaceDispatcher.kt    (batch DOM writes, scroll anchor)
│   └─ ParagraphMapper.kt      (DOM paragraph ↔ Chunk ↔ Cache row)
├─ extraction/     ExtractionController, ContentParser, ExtractionState  KEEP
│                  ExtractionScript → assets/js/extract.js              CHANGE
├─ reader/         ReaderScreen(Focus Reader), Settings/Position repos   KEEP (optional UI)
├─ translation/    Service, Provider, Chunking, Memory, Quality, Background  KEEP
│   ├─ gemini/     Direct provider                                       KEEP (+PromptBuilder extend)
│   ├─ cache/      ParagraphCache (repo + eviction)                      NEW
│   └─ db/         MnbDatabase v4→v5→v6                                  CHANGE (additive)
├─ novellibrary/   Library, Favorites, Progress, Metadata                 NEW  (library/ = browser bookmarks/history; နာမည်မရောရန်)
├─ library/        BrowserLibrary (bookmarks/history)                     KEEP
├─ security/, settings/                                                  KEEP
└─ ui/             Compose screens, design tokens                        CHANGE (consolidate)
assets/js/         extract.js, apply.js, restore.js, style.js            NEW
```

## 3. Browser ↔ Reader Integration
- **Primary:** In-page Reader Mode (WebView ထဲမှာ DOM replace)။ BottomBar ၏ "Extract" ခလုတ်ကို "Reader Mode" toggle နှင့်အစားထိုး။
- **Secondary (optional):** `ReaderScreen` ကို "Focus Reader" အဖြစ်ထား (Extraction payload ကိုပြန်သုံး; Network မလို Cache မှဖတ်)။ `ExtractionPreviewScreen` = Debug build/Developer setting သာ (UD-03)။
- `ReaderModeController` သည် Browser Layer (tab-scoped) တွင်နေ၊ `ReaderStateManager` (Compose Reader) နှင့် သီးခြား။ နှစ်ခုသည် `ParagraphUnit` list ကိုသာ မျှဝေ။

## 4. DOM Extraction Architecture
```
[Kotlin]                          [WebView page, isolated closure]
ReaderModeController
  └─ JsDispatcher.run(extract.js, sessionToken)  ─────►  IIFE: scan DOM → registry (closure-held Node[])
                                                         returns { sessionToken, docToken,
         ◄──── JSON via evaluateJavascript callback ──── paragraphs:[{i,hash,text,h,kind}] }
```
- Registry သည် JS closure ထဲ — DOM attribute မရေး (Page က မြင်/ပြင်မရ ဖြစ်နိုင်သမျှ)။ ID = index + text hash။
- Container Selection: လက်ရှိ Ancestor-scoring Heuristic ကို KEEP; Chapter-ness score နှင့် Site-rule override table (Fixture-driven) ကို ထပ်ထည့်။
- Dynamic content: Extract ပြီးနောက် MutationObserver (observe-only, bounded, auto-disconnect) ဖြင့် Container ပျက်/ပြန်ရေးမှု detect → `STALE` ပို့ → Re-extract (Cache hit ကြောင့်ဈေးသက်သာ)။
- Caps: Paragraph ≤ N၊ စာလုံး/Paragraph ≤ M၊ စုစုပေါင်း ≤ cap (DoS)။
- JS ကို `assets/js/*.js` သို့ရွှေ့ (TD-12) — Lint/Test လွယ်၊ Kotlin raw string `$` ကန့်သတ်မှုကျော်။

## 5. WebView JavaScript Bridge (**Bridge မသုံး**)
- `addJavascriptInterface` **မသုံး** (SB-1; လက်ရှိအတိုင်း)။ `androidx.webkit` WebMessageListener ကိုလည်း v1.0 တွင် မထည့် (Dependency မလို)။
- Kotlin→JS: `evaluateJavascript(applyScript(token, [{i,text}...]))` — payload ကို `JSON` encode (string escape Kotlin ဘက်မှ တစ်နေရာတည်း)၊ JS တွင် `textContent` သာ။
- JS→Kotlin: `evaluateJavascript` callback ၏ return value သာ (Extraction result)။ Streaming ရလဒ်များကို Kotlin→JS တစ်လမ်းသာ — Page ဘက်မှ Native သို့ Call မရှိ → Attack surface ပိုမလာ။
- ထို့ကြောင့် JS ဘက်မှ "ဘာသာပြန်တောင်းသည်" ဟု Native သို့ပို့စရာမလို။

## 6. Page Identity / Tab Identity
`PageSession = (tabId, navGeneration:Int, normalizedUrl, docToken)`
- `navGeneration` တိုးသည့် Event: onPageStarted (main frame)၊ reload၊ history navigation၊ tab WebView ပြန်တည်ဆောက် (unload/renderer-gone)။
- `docToken`: Extract တိုင်း JS ထဲ random ထုတ်ပြီး Replace Script တိုင်းတွင် ပြန်စစ်; မကိုက်လျှင် JS က no-op နှင့် `STALE` ပြန်။
- Kotlin ဘက် Result လက်ခံချိန် `session == current` မကိုက်လျှင် ပယ် (Stale write ကာကွယ်)။
- SPA (pushState) = same-document: `doUpdateVisitedHistory` ဖြင့်သိ၊ Chapter ပြောင်းလျှင် Re-extract ကို Prompt/Auto (Setting)။

## 7. Translation Pipeline
```
ParagraphUnit[] ──► ParagraphCache.lookup(key) ──hit──► Replace immediately
        │ miss
        ▼
Existing Chunker ──► Context Memory inject ──► TranslationService ──► GeminiTranslationProvider
        ▼                                                    (Direct, header key, retry/backoff)
   Response parse (index-tagged N lines) ─► Quality Checker ─► ParagraphCache.put ─► ReplaceDispatcher (batched)
```
- Provider interface/Guards KEEP; `GeminiPromptBuilder` ကို index-tagged output format သို့ Extend။
- Prompt Version + Model ID ကို Cache Key တွင်ထည့် → Prompt/Model ပြောင်းလျှင် အလိုအလျောက် Invalidate။
- Output count ≠ Input count → Chunk ကို ထပ်ခွဲပြန်ကြိုး (≤ ၂ ကြိမ်) နောက် Failure ဟုမှတ်၊ Original ထား။

## 8. Background Job Management
- **Instant (Reader Mode ON):** Foreground, Executor ပေါ်၊ `PageSession` ကိုင်၊ Cancel ရ။
- **Background (WorkManager):** လက်ရှိ Queue/Priority ကို KEEP။ In-page Mode ပိတ်/App ကိုပိတ်သွားလျှင် ကျန် Chunk များကို Queue ထဲ ဆက်ပို့ → **Cache ထဲသာ** ရေး (DOM မထိ)။ နောက်ပြန်ဖွင့်လျှင် Cache hit။
- Shared chapter lock (I10-D) ကို Cache key သို့ တိုးချဲ့ (Instant နှင့် Background ထပ်မဘာသာပြန်)။
- `MnbApplication` တွင် Provider/Env ကို ချိတ် (06-B4) — KEEP; Cold-start work ကို အနည်းဆုံး။

## 9. Room Database Design
- v4 (လက်ရှိ) → **v5** `paragraph_translation_cache` (additive) → **v6** `library_novels` + `reading_progress` (additive; SharedPrefs→Room data migration)။
- Migration တိုင်းအတွက်: Schema JSON commit + `MigrationTestHelper` test + Guard (`fallbackToDestructiveMigration` မရှိ)။
- Cache Row: `(cacheKey PK, text, createdAt, lastUsedAt, bytes)`; Index `lastUsedAt`; Eviction = size cap (UD ဖြင့် default) + LRU + User "Clear cache"။
- Chapter-level `ChapterTranslation` ကို KEEP (Focus Reader/Export အတွက်); Cache ကို Source of truth ဟုမသတ်မှတ် — Reconcile rule ကို R-05 တွင် သတ်မှတ်။
- API Key သည် Room/Export ထဲ **မပါ** (Keystore + private prefs သာ)။

## 10. Translation Cache Strategy
Key = `H(normalizedHost | normalizedPath | paraHash | srcLang | tgtLang | modelId | promptVersion)`။ `paraHash` = normalized text SHA-256။ URL query/fragment ဖယ် (tracking param)။ Same text ကွဲ Chapter တွင် Cache hit ပေးနိုင် (Cross-chapter dedup — အကျိုးကျေးဇူး + Glossary context မတူသောအန္တရာယ်ကို R-05 တွင်ဆုံးဖြတ်)။

## 11. Error Recovery
| ဖြစ်ရပ် | လုပ်ဆောင်ချက် |
|---|---|
| Renderer gone | Existing 2F Recover; Reader Mode state → OFF၊ Cache ကို ထိန်း၊ Banner "Reader Mode ပိတ်သွား။ ပြန်ဖွင့်" |
| Navigation/Reload mid-translate | Token++ → Cancel (Network disconnect)၊ ရလဒ်ပယ်; Cache သို့ပြီးသားပိုင်းသိမ်း |
| Tab switch | Tab တစ်ခုချင်း State; Background tab သည် Replace မလုပ် (Active ဖြစ်မှ apply) |
| Gemini error | Fixed messages (KEEP)၊ Inline banner + Retry; Key error → Settings သို့ |
| Partial result | ရပြီးသား Paragraph ထား၊ ကျန်သည် Original; Retry ဖြင့်ဆက် |
| Process death | Cache မှ Resume; DOM ကို ပြန်မထူထောင် (User ပြန်ဖွင့်လျှင် Cache hit) |

## 12. Security Boundaries
- **Trust boundary:** Page content = untrusted။ Script injection သည် Kotlin-ဆောက် Static asset သာ; Page string ကို Script အဖြစ် မ eval။
- Page → Prompt: "content only" + Prompt injection residual risk (SECURITY.md ပါပြီး) ကို Docs ထပ်ဖော်ပြ။
- Model Output → DOM: `textContent` သာ။ Output ကို HTML ဟု မသတ်မှတ်ရ။
- Key: Provider တစ်နေရာတည်းသာ `reveal()` (Guard Test KEEP)။
- Network: HTTPS only၊ Redirect မလိုက်၊ Header key (KEEP)။
- Permissions: INTERNET တစ်ခုတည်း (ပြောင်းလျှင် UD)။
- Safe Mode: UI Group Switch သာ — `UrlPolicy`/WebView settings ကို မထိ (Guard Test)။

## 13. Performance / Memory
- Live WebView ≤ 6 (KEEP); Reader Mode state ကို Tab unload တွင် Reset (Registry သည် JS heap ထဲ — WebView ပျက်လျှင်အတူပျက်)။
- Replace ကို requestAnimationFrame batch (≈ ၂၀–၅၀ paragraph/frame, Tunable)၊ Long chapter တွင် Visible-first (viewport အနီး ဦးစားပေး)။
- Cache IO ကို IO Executor; Main thread တွင် DOM dispatch သာ။
- Memory: Extraction payload ကို Chunk ပြီးသည်နှင့် Release; Registry cap။
- Startup: `MnbApplication.onCreate` Keystore/File/Network မလုပ် (KEEP)။
- Background: WorkManager constraint (network)၊ Rate limiter။

## 14. Build / Release Architecture
Gradle Wrapper pin; CI: Lint + Unit + `assembleRelease` (unsigned) → Owner Signing (CI Secret သို့မဟုတ် Local)။ R8 = UD။ SemVer (`1.0.0-beta.N` → `1.0.0`) + versionCode အမြဲတိုး။ Release artefact = Owner-signed APK + SHA-256 (UD-09 ✔ Direct APK Beta; AAB/Play = R-17 နောက်ပိုင်း)။ Update = Install-over (Package+Cert တူ; TD-19)။
Gemini Model = Allow-list Config (`GeminiConfig` တစ်နေရာ; Free-text မရ; UD-04 ✔)။ Preview/Debug Screen = Debug build only (UD-03 ✔)။

## 15. KEEP vs CHANGE အကျဉ်း
**KEEP:** Provider/Service/Chunk/Memory/Quality၊ Gemini Direct၊ Secure storage၊ WorkManager queue၊ Multi-tab/WebView Controller/Resilience၊ UrlPolicy၊ DuckDuckGo-free Google search၊ Room (additive only)။
**CHANGE (အနည်းဆုံး):** Extraction (anchored; assets)၊ Reader integration (In-page)၊ Page/Tab Identity၊ Cache granularity၊ Settings consolidation၊ Docs/CI/Release။
**NOT ADDED:** Gateway၊ Google Cloud Translation၊ JS bridge၊ Multi-module split၊ DI framework၊ Coroutines/OkHttp (UD သာ)။
