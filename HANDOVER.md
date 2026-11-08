# HANDOVER — MNB Complete Roadmap Reconstruction (Audit + Planning)

ရက်စွဲ: 2026-11-08 · `main` @ `c67a45e` (Merge PR #36)
**ဤ HANDOVER သည် Repo ရှိ Root `HANDOVER.md` (Phase 2G, Stale) ကို အစားထိုးရန် ရေးထားသည်။** Repo တွင် မ commit ရသေးပါ — ZIP ထဲသာ။

## 0. ID စနစ် (မရောရန်)
| Prefix | အဓိပ္ပာယ် |
|---|---|
| `Phase 01–06`, `04-I*`, `06-A*/B*/C*`, `2A–2G`, `UI Phase 1` | **မူလ Repo Phase History** (မပြောင်း၊ မဖျက်) |
| `R-00 … R-17` | **Final Roadmap Phase အသစ်** (R = Roadmap) |
| `UD-xx` | User Decision (§4: Decided / Open ခွဲ) |
| `TD-xx` | Claude Technical Decision |
| `SB-x` Security Baseline · `G-x` Doc/Code ကွာဟချက် · `W-x` Architecture အားနည်းချက် | Audit/Roadmap အတွင်း Reference |

## 1. ဘာလုပ်ခဲ့သလဲ
- GitHub ကို ဖတ်ရုံသာ (Source/Build/Test/Commit/Push/PR/Dependency မထိ)။
- PR #1–#36 (title/branch/head/merge/size)၊ Branch ၄၂ ခု၊ Commit history အစောပိုင်း (Phase 01/02)၊ Docs ၁၆+ ဖိုင်၊ Source ဖိုင် ၁၀ ခု (အပြည့်) + Directory listing များ ကိုဖတ်။
- PR #27/#34/#35/#36 ၏ CI check run ကိုစစ်။
- ထုတ်လုပ်သည့် ဖိုင် ၇ ခု + ZIP (§6)။

## 2. အဓိကတွေ့ရှိချက်
1. **ပြီးသည်** (Code + CI Unit Test; **Device Test ၀**): Multi-tab Browser၊ Google Search၊ Bookmarks/History၊ Shortcuts (၅ site)၊ Renderer-crash recovery၊ DOM Extraction (Preview ဖြင့်)၊ Core Reader၊ Translation Engine (Chunk/Memory/Quality/Instant/Background)၊ Direct Gemini BYOK၊ Secure Key Storage။
2. **Vision နှင့်အဓိကကွာဟချက်:** In-page Translation မရှိ။ Extraction ရလဒ်တွင် DOM Node reference မပါ၊ Reader သည် သီးခြား Compose Screen၊ Extract ခလုတ်→Preview→Reader flow။
3. **မရှိသေး:** Page/Tab Identity၊ Paragraph-level Cache၊ Novel Library/Favorites/Progress/Chapter nav၊ Safe Mode + Shortcut Groups၊ Release Signing/Versioning/R8/Release CI။
4. **Phase History ပြဿနာ:** PR #35 နှင့် #36 Branch တူ (`phase-06-c1-settings-screen-foundation`) ဖြစ်ပြီး #36 = Phase 2G; "Phase 02" vs "2A–2G" ရောထွေး; UI Phase 1 သီးခြား; **Phase 05 ကို Repo ထဲ အထောက်အထားမတွေ့**။
5. **Docs Stale:** README (multi-tab မပါ)၊ HANDOVER (2G pre-merge)၊ PHASE-06-FINAL-REPORT ("B4 CI မရှိ" — PR #27 CI အစိမ်းရှိ)၊ versionName `0.2.0-phase02`။
6. **Schema JSON မ commit** (`app/schemas` မတွေ့) → `MnbSchemaMigrationTest` skip ဖြစ်နေနိုင် [I]။
7. **Security Baseline ကောင်း:** `addJavascriptInterface` မသုံး၊ File/Content access off၊ SSL cancel၊ HTTPS-only၊ Key header-only/AES-GCM။ (အားလုံး Device/Real-Keystore မစမ်းရသေး)။

## 3. Roadmap အဓိကအချက်များ (R-00 … R-16)
Part A တွင် မူလ Phase History ကို မူလအမည်ဖြင့်ထား၊ Replaced-Purpose Ledger ပါ။ Part C တွင် Final Roadmap:
| R | အမည် | Lineage |
|---|---|---|
| R-00 | Baseline Verification & Doc Reconciliation (Device test, Schema JSON, Docs) | 2A–2G, 06-B4 ကျန်ကိစ္စ |
| R-01 | Settings Consolidation & Gemini Model Allow-list | 06-C2…C4, UD-04 |
| R-02 | Page/Tab Identity & Reader-Mode State Model | အသစ် |
| R-03 | DOM Extraction v2 (Anchored) | Phase 02 တိုးတက် |
| R-04 | In-page Reader Mode (Translation မပါ) | Phase 03 |
| R-05 | In-page Translation + Paragraph Cache (Room v5) | Phase 04 + 06 |
| R-06 | Translation Quality/Safety/Resilience | 04-I7, 06-B3 |
| R-07 | Novel Library, Chapter Nav, Progress (Room v6) | Phase 03, 04-I3 |
| R-08 | Reader Display Modes & In-page Settings | 03 Part 2, 04-I10-B |
| R-09 | Shortcut Groups & Safe Mode | 2C |
| R-10 | Browser Completeness Decisions | 2F gaps |
| R-11 | Design System & UX Polish | UI Phase 1, 2D |
| R-12 | Data Lifecycle/Migration/Cache | 2G, 04-I3 |
| R-13 | Security Audit & Privacy | 06-A/B |
| R-14 | Performance/Memory/Compatibility | — |
| R-15 | Test Automation & Release Engineering (Signing/Versioning/R8/Release CI/Update process) | 06-B4, UD-09 |
| R-16 | Direct APK Beta → Stable v1.0.0 → Maintenance | UD-09 |
| R-17 | Google Play (v1.0 ပြင်ပ သီးခြား Milestone) | UD-09 |

Critical path: `R-00 → R-02 → R-03 → R-04 → R-05 → R-06 → R-08 → R-11 → R-13/14 → R-15 → R-16`။

## 4. User Decisions

### 4.1 ဆုံးဖြတ်ပြီး (User, 2026-11-08) — Docs တွင် မှတ်ပြီး
| ID | ဆုံးဖြတ်ချက် | သက်ရောက်မှု |
|---|---|---|
| UD-01 ✔ | Phase 05 = Gateway; Direct Gemini BYOK က အစားထိုး; Gateway ပြန်မထည့် | Roadmap Part A Ledger; Phase 05 = Superseded |
| UD-02 ✔ | Discovery ပထမအဆင့် = လက်ရှိ Page မှ Novel metadata သိမ်းခြင်းသာ | R-07 Scope |
| UD-03 ✔ | Preview Screen = Debug/Dev-only; Focus Reader = Optional; အဓိက = In-page translation | R-04, R-08 |
| UD-04 ✔ | Gemini Model = Allow-list; `gemini-2.5-flash` မအတည်ပြု; စစ်ပြီး Default သတ်မှတ် | Decision Log C.1: Provisional Default `gemini-3.8-flash`, Economy `gemini-3.5-flash-lite` — R-00/R-01 Live Evaluation ပြီးမှ Lock |
| UD-09 ✔ | ပထမ Release = Direct APK Beta (Signing/Versioning/R8/Release CI/Update process ပြည့်စုံ); Google Play = နောက် Milestone | R-15, R-16 ပြန်ရေး; **R-17 Google Play** အသစ် (v1.0 ပြင်ပ) |

### 4.2 ဖွင့်ထားဆဲ — **Claude မှ အတည်မပြုပါ** (အကြံပြုချက်/အကျိုးဆက်ကို Decision Log B + B.2)
UD-05 Safety settings · UD-06 Quality target/Languages · UD-07 Safe Mode group · UD-08 UI language · UD-10 Telemetry · UD-11 Downloads/File chooser · UD-12 Backup/Export · UD-13 minSdk · UD-14 R8 · UD-15 Coroutines/OkHttp · UD-16 Branch cleanup · UD-17 Root docs ရွှေ့ · UD-18 Signing key ownership · UD-19 JS test harness (Node+jsdom) · **UD-20 (အသစ်) Beta Update နည်းလမ်း** · **UD-21 (အသစ်) Beta Hosting/Tester channel**။
ဖြေရန်အရေးကြီးဆုံး (Beta ပိတ်ဆို့): **UD-18** (Key ပျောက်=Update မရ)၊ **UD-14** (R8)၊ **UD-20/21** (Update/Hosting)၊ **UD-06** (R-06 Target)၊ **UD-05** (Safety)။

### 4.3 UD-04 တွင် ဖွင့်ထားသော Verification (R-00)
Official page နှင့် Third-party သတင်း ကွဲလွဲ (2.5-flash ၏ Dec 1 Reroute) → Primary source ဖြင့်ပြန်အတည်ပြု; Pricing Official page မဖတ်ရ [U]; Gemini 3 Request shape (TD-18) Live စမ်း။

## 5. အတည်ပြုနိုင်သည် vs ခန့်မှန်း
**အတည်ပြု [C/CI]:** PR 1–36 merged; HEAD `c67a45e`; PR #27/#34/#35/#36 CI success; DB v4 (entities ၁၇); `addJavascriptInterface` မသုံး; WebView settings; Extraction script အလုပ်လုပ်ပုံ + Extract→Preview→Reader flow; Default shortcuts ၅ ခု (DuckDuckGo မပါ); CI = unit+debug only; release unsigned, R8 off, versionName `0.2.0-phase02`; `app/schemas` မတွေ့။
**ခန့်မှန်း [I]:** Phase 05=Gateway; Schema test skip; I10-C branch အမည်ရင်းမြစ်; Chunker/Quality ၏ အသေးစိတ်လုပ်ဆောင်ပုံ။
**မစစ်ရသေး [U]:** Device behavior အားလုံး; PR #1–#26,#28–#33 ၏ CI; Test အရေအတွက်/ရလဒ်; `SettingsScreen` wiring; Chapter navigation; Live Gemini; Real Keystore; PR descriptions; ကျန် Source ဖိုင်အများစု (Audit §1 ကိုကြည့်)။
**ကန့်သတ်ချက်:** GitHub code search သည် `incomplete_results` ပြန်ခဲ့၍ "မတွေ့" ဟူသော ရလဒ်များကို "မရှိ" ဟုမယူပါနှင့်။

## 6. ဖန်တီးထားသော ဖိုင်များ (ZIP: `MNB-Roadmap-Docs.zip`)
1. MNB-COMPLETE-PROJECT-ROADMAP.md
2. MNB-CURRENT-STATE-AUDIT.md
3. MNB-FINAL-ARCHITECTURE.md
4. MNB-PHASE-DEPENDENCY-MAP.md
5. MNB-DECISION-LOG.md
6. MNB-FINAL-ACCEPTANCE-CHECKLIST.md
7. HANDOVER.md

## 7. နောက်တစ်ဆင့် စတင်ရမည့်နေရာ
1. UD-18, UD-14, UD-20, UD-21, UD-06, UD-05 (§4.2) ကို ဦးစွာဖြေပါ — ကျန်ကို R-00 အတွင်း ဖြည့်နိုင်။ UD-01/02/03/04/09 ကို မှတ်ပြီး။
2. ဤ Docs ကို Repo `docs/roadmap/` သို့ ထည့်ရန် ခွင့်ပြုပါ (ယခု မထည့်ရသေး — Commit/PR မလုပ်ပါ)။ Root HANDOVER.md ကို အစားထိုးရန်လည်း ခွင့်ပြုချက်လို။
3. **R-00 စမည်:** (က) Device Test ဇယား (2B/2F/2G/06-B4 စာရင်းအတိုင်း) (ခ) Schema JSON commit + Migration test passed (ဂ) မဖတ်ရသေးသော ဖိုင်များ Audit (ဃ) Docs ညှိ (င) Model ID Live စစ်။
4. R-00 ပြီးမှ R-01 / R-02 သို့။ Code မရေးမီ Phase တစ်ခုချင်းကို Roadmap ၏ Acceptance/Gate ဖြင့်သာ ပိတ်ပါ။

## 8. လုပ်ဆောင်ရာတွင် လိုက်နာခဲ့သော စည်းကမ်း
မူလ Phase History မဖျက်၊ Phase နံပါတ်အသစ်ကို မူလနှင့်ကွဲစေရန် `R-xx` ဟုသုံး; Gateway/Google Cloud Translation/DuckDuckGo မထည့်; WebView Security မလျှော့; Source/Dependency/Build/Test/Commit/Push/PR မလုပ်။
