# MNB — Final Acceptance Checklist (Direct APK Beta → Stable v1.0.0)

ပြီးစီးမှု စစ်ဆေးရန်။ Item တစ်ခုချင်းတွင် **Evidence** (CI run link / Device log / Test report / Commit) ကို ဖြည့်ရမည်။ Evidence မရှိ = ✔ မထည့်ရ။
Legend: ☐ မပြီး · ✔ ပြီး (Evidence ပါ) · ◐ Accepted-risk (User လက်မှတ်ပါ)
Device = အမှန်တကယ်ဖုန်း (Emulator သာဆို Evidence တွင်ဖော်ပြ)။

## 0. လက်ရှိအခြေအနေ (2026-11-08 Audit အရ) — မည်သည့် Item မျှ မပြီးသေး
CI အစိမ်း (#27/#34/#35/#36) သည် Automated အပိုင်း ၁ ခုကိုသာ ထောက်ခံ။ အောက်ပါ ☐ အားလုံး စတင်အခြေအနေ။

## 1. Core Vision
- ☐ Google သည် Default Search; DuckDuckGo သည် Default Search/Shortcut မဟုတ် (`NoDuckDuckGoGuardTest` ✔ + Device)
- ☐ Multi-tab (Open/Switch/Close/Restore after kill) Device
- ☐ Bookmarks + History (search/groups/prune) Device
- ☐ Novel Shortcuts (Add/Edit/Delete) Device
- ☐ Safe Mode ON/OFF သည် User ရွေး Shortcut Group များကိုသာ ပြောင်း၊ Content Unlocker မဟုတ်ကြောင်း Guard Test
- ☐ Direct Gemini BYOK သာ (Gateway/Google Cloud Translation သည် Request Path တွင် မရှိ — Guard/Code scan)

## 2. In-page Translation (အဓိက)
- ☐ Reader Mode ON သည် လက်ရှိ WebView ကိုသုံး (Preview Screen မလို)
- ☐ DOM Extraction Background တွင်၊ UI မခိုင်း (frame jank Device)
- ☐ Paragraph Ordering/Filtering Fixture Matrix pass
- ☐ Existing Chunking + Context Memory ကိုသုံးကြောင်း Integration Test
- ☐ Progressive Replacement (ပထမ Chunk ရလဒ်မှ စပြ)
- ☐ Original ပြန်ကြည့်ရ၊ Reader Mode OFF → Original 100% ပြန် (DOM text equality)
- ☐ Layout/Images/Links/Buttons/Navigation မပျက် (Site ≥ ၅)
- ☐ Scroll position မပျက် (Drift < 1 paragraph)
- ☐ Navigation / Reload / Back-Forward / Tab Switch / Cancel / Renderer-kill တွင် Stale write မရှိ
- ☐ Paragraph Cache hit ဖြင့် Network မခေါ် (Test + Device)
- ☐ Output ကို `textContent` ဖြင့်သာ (JS asset Guard)
- ☐ Display: Original / Myanmar / Bilingual

## 3. Translation Quality & Engine
- ☐ Live Gemini ဖြင့် EN/ZH/KO/JA နမူနာ ၅ ပိုဒ်စီ Human review ≥ Target (UD-06)
- ☐ Output Unicode (Zawgyi မဟုတ်) စစ်
- ☐ Error handling: Missing/Rejected key, Quota, Rate-limit, Timeout, Offline, Blocked content — Device
- ☐ Retry/Backoff bounded; Quota/Key error မ retry
- ☐ Background translation: Process kill နောက် Worker ပြန်စ (Device)
- ☐ Gemini Model Allow-list: Default သည် R-00/R-01 Live Evaluation ရလဒ်ဖြင့် Lock (Provisional `gemini-3.8-flash` မဟုတ်မီ); Free-text မရ; Model ပျောက်/Reroute → Default Fallback + Notice
- ☐ Gemini 3 Request shape (TD-18) Live ✔; Thinking-token Cost တိုင်းတာ

## 4. Reader & Library
- ☐ Font/Size/Spacing/Theme (In-page + Focus Reader အကယ်၍ထား)
- ☐ Saved reading position (Update ပြီးနောက်ပါ မပျောက်)
- ☐ Chapter Next/Prev (Site ≥ ၅)
- ☐ Library/Favorites/Continue reading/Progress (UD-02 scope အတိုင်း)

## 5. Browser Quality
- ☐ WebView Lifecycle/Memory: Live ≤ 6; `onTrimMemory` unload; Renderer-gone recovery (Device)
- ☐ Crash-loop guard (၂ ကြိမ်/60 s → Home + Retry) Device
- ☐ UrlPolicy Fuzz test pass (userinfo, IDN, intent:, javascript:, data:, overlong)
- ☐ R-10 ရွေး Item များ Device + Guard; မလုပ်ဟုရွေး Item များ Known-limitation တွင်မှတ်

## 6. Security (WebView + Key + Privacy)
- ☐ SB-1…SB-10 အားလုံး (Roadmap §B.1) — Guard Tests အစိမ်း + Manual review
- ☐ `addJavascriptInterface` မရှိ (Source scan)
- ☐ `allowFileAccess=false`, `allowContentAccess=false`, mixed content never, SSL cancel, permissions deny (Guard + Device)
- ☐ Key: AES-GCM/Keystore, Log/URL/State/Exception ထဲမပါ; `adb logcat` Key/Chapter text မပါ
- ☐ Release APK Secrets scan (Key/Keystore/Password မပါ)
- ☐ Manifest: INTERNET only, allowBackup=false, cleartext off, exported components စစ်
- ☐ Dependency vulnerability scan ရလဒ်; High/Medium ၀
- ☐ Security Audit (R-13) Finding များ Severity ခွဲပြီး ပိတ်/Accepted-risk
- ☐ Privacy statement: Chapter စာသားသည် Google သို့သွားကြောင်း ရှင်းလင်းဖော်ပြ
- ☐ Real Android Keystore Device စမ်း (Key lost/unreadable flow)

## 7. Database & Migration
- ☐ Schema JSON အားလုံး commit (1…N)
- ☐ Migration Tests (`MigrationTestHelper`) **passed, skipped ၀**
- ☐ Update-from-every-previous-version Device test (Tabs/Bookmarks/History/Settings/Progress/Key မပျောက်)
- ☐ `fallbackToDestructiveMigration` မရှိ (Guard)
- ☐ Cache eviction + Clear cache; Large-data test

## 8. Performance / Compatibility
- ☐ Long chapter (≥ 5k paragraphs) ဖြင့် Jank/ANR မရှိ
- ☐ Memory ceiling (6 live WebView) Baseline နှင့်နှိုင်း
- ☐ Cold start Target
- ☐ Site Matrix (Bundled ၅ + Noveldex + CJK ≥ ၂ + User-named) ဇယား — Pass/Known-fail
- ☐ Android API 24, 28, 31, 34 (+latest) အနည်းဆုံး Device ၃ ခု
- ☐ Myanmar font rendering (OS/WebView version အလိုက်)
- ☐ Background worker Battery/Doze

## 9. UI/UX
- ☐ Design tokens/Components consistent
- ☐ Dark/Light, Font scale 200%, Touch target ≥ 48dp, TalkBack ဖြင့် Primary flow ၃ ခု
- ☐ Placeholder / "Not available yet" UI ၀
- ☐ UI strings resource (UD-08)

## 10. Build / CI / Release
- ☐ CI (Lint + Unit + Release build) အစိမ်း — **Exact RC commit**
- ☐ Test skipped/ignored/deleted ၀ (Last green ပြီးနောက်)
- ☐ Gradle Wrapper Pin
- ☐ R8 (UD-14) ဆုံးဖြတ်ပြီး; On ဖြစ်လျှင် Full pass + Device
- ☐ Versioning: Beta `1.0.0-beta.N` / Stable `1.0.0`; versionCode အမြဲတိုး (Guard)
- ☐ Signed Release APK (Owner Key, Repo ပြင်ပ — UD-18)
- ☐ Clean Device တွင် Install + Smoke
- ☐ **Install-over Update test** (beta.N-1 → beta.N: Package/Cert တူ; Tabs/Bookmarks/History/Library/Settings/API Key မပျောက်)
- ☐ SHA-256 Checksum + Release notes + Known limitations + Unknown-sources Install လမ်းညွှန် ထုတ်ပြီး
- ☐ Signing Key Backup (Owner၊ ၂ နေရာ) အတည်ပြု (UD-18)
- ☐ Debug/Preview Screen သည် Release APK တွင် မပါ (UD-03 Guard)

## 11. Documentation
- ☐ README/ARCHITECTURE/SECURITY/TESTING/RELEASE-NOTES/RELEASE-CHECKLIST Code နှင့်ကိုက်
- ☐ Known limitations ရိုးသားစွာဖော်ပြ
- ☐ Phase History (Root Reports) `docs/history/` သို့ စုစည်း (UD-17)
- ☐ HANDOVER.md Final ဗားရှင်း

## 12. Maintenance Readiness
- ☐ Hotfix branch policy + Bug triage SLA
- ☐ Gemini Model/API deprecation Watch နည်းလမ်း
- ☐ Site Compatibility Bugfix = Fixture-first လုပ်ငန်းစဉ်
- ☐ Release notes template

## 13. လက်မှတ် (Sign-off)
| Item | Owner | ရက်စွဲ | Evidence link |
|---|---|---|---|
| Functional | | | |
| Security | | | |
| Device/Compat | | | |
| Release | | | |

**Beta Gate (R-16A):** Section 1–10 ပြည့်မီ သို့မဟုတ် မပြည့်မီ Item ကို User လက်မှတ်ဖြင့် "Beta-known" မှတ်ပြီးမှ `1.0.0-beta.N` ဖြန့်။
**Stable Gate (R-16C):** Section 1–10 အားလုံး ✔ (သို့မဟုတ် ◐ Accepted-risk လက်မှတ်ပါ) မှသာ `v1.0.0` Tag။ Google Play (R-17) သည် ဤ Checklist ပြင်ပ။
