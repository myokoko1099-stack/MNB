# MNB — Phase Dependency Map & Implementation Order

အခြေခံ: MNB-COMPLETE-PROJECT-ROADMAP (R-00…R-16)။ မူလ Phase (Phase 02, 04-I*, 06-*, 2A–2G) နှင့် Lineage ကို Roadmap Part A တွင်ကြည့်ပါ။

## 1. ရှိပြီးသား (Baseline) မှ R-Phase သို့ ချိတ်ဆက်မှု

| မူလ Phase (ပြီး — Device မစစ်) | နောက်ဆုံး R-Phase က မှီခို/ဆက်လက် |
|---|---|
| Phase 02 Extraction, 03 Reader | R-03, R-04, R-08 |
| Phase 04-I1…I10-D Translation | R-05, R-06 |
| Phase 06-A Secure Storage | R-01 (UI ရွှေ့), R-13 |
| Phase 06-B Gemini Direct | R-01 (Model), R-05, R-06 |
| Phase 06-C1 Settings | R-01 (C2–C4) |
| UI Phase 1, 2D | R-11 |
| 2A, 2E, 2G DB/History | R-00 (Schema JSON), R-12 |
| 2B, 2F Tabs/Resilience | R-00 (Device), R-02 |
| 2C Shortcuts | R-09 |
| Phase 05 (Gateway) | မမှီခို — အစားထိုးပြီး (UD-01) |

## 2. R-Phase Dependency Graph

```
                R-00 Baseline Verification
              ┌────────┼───────────────┬─────────────┬──────────────┐
              ▼        ▼               ▼             ▼              ▼
           R-01      R-02            R-09          R-10           (R-12 အစောပိုင်း
        Settings   Page/Tab ID    Shortcut/Safe  Browser gaps      Schema JSON အပိုင်း)
           │        │   │              │             │
           │        ▼   │              │             │
           │      R-03 DOM Extraction v2│             │
           │        │                   │             │
           │        ▼                   │             │
           │      R-04 In-page Reader Mode            │
           │        │                   │             │
           └──┬─────┘                   │             │
              ▼                         │             │
           R-05 In-page Translation + Cache           │
              │         │                             │
              ▼         ▼                             │
           R-06       R-07 Novel Library/Progress ◄── R-02
        Quality/Safety   │
              │          │
              └────┬─────┘
                   ▼
                 R-08 Reader Display Modes ──────────┐
                   │                                 │
                   └─────────────┬───────────────────┤
                                 ▼                   ▼
                       R-11 Design/UX Polish ◄── R-09, R-10
                                 │
              R-12 Data Lifecycle ◄ (R-05, R-07 Schema Freeze)
                                 │
                                 ▼
                    R-13 Security Audit  ──┐
                    R-14 Perf/Compat    ───┤
                                           ▼
                         R-15 Test Automation & Release Engineering
                                           ▼
                         R-16 Direct APK Beta → v1.0.0 → Maintenance
                                           ▼ (v1.0 ပြင်ပ သီးခြား Milestone)
                         R-17 Google Play
```

## 3. Dependency Table

| Phase | Hard Dependencies | Soft (ဦးစားပေးရန်) | Blocks |
|---|---|---|---|
| R-00 | — | — | အားလုံး |
| R-01 | R-00 | — | R-05 (Model config), R-09 (settings entry), R-11 |
| R-02 | R-00 | — | R-03, R-04, R-07 |
| R-03 | R-02 | — | R-04, R-05 |
| R-04 | R-02, R-03 | — | R-05, R-08 |
| R-05 | R-01, R-03, R-04 | — | R-06, R-07, R-08, R-12 |
| R-06 | R-05 | R-01 | R-08, R-13 |
| R-07 | R-02, R-05 | R-01 | R-08, R-12 |
| R-08 | R-05, R-06 | R-07 | R-11 |
| R-09 | R-01 | — | R-11 |
| R-10 | R-00 | R-01 | R-11, R-13 |
| R-11 | R-08, R-09, R-10 | — | R-13, R-14 |
| R-12 | R-05, R-07 | R-00 (Schema JSON) | R-13, R-14 |
| R-13 | R-08..R-12 | — | R-15 |
| R-14 | R-11, R-12 | R-13 | R-15 |
| R-15 | R-13, R-14 | — | R-16 |
| R-16 | R-15 | — | R-17 |
| R-17 (Play, v1.0 ပြင်ပ) | R-16C Stable | UD-09 Play ဆုံးဖြတ်ချက် | — |

## 4. အကြံပြုသော Implementation Order

**Critical Path (အရှည်ဆုံး — Final Vision ၏ အဓိကလမ်းကြောင်း):**
`R-00 → R-02 → R-03 → R-04 → R-05 → R-06 → R-08 → R-11 → R-13/R-14 → R-15 → R-16`

**လမ်းကြောင်း ၃ ခု (တစ်ပြိုင်နက်လုပ်နိုင်, တစ်ဦးတည်းလုပ်လျှင် အစီအစဉ်အတိုင်း):**
- **Track A (In-page Translation):** R-02 → R-03 → R-04 → R-05 → R-06 → R-08
- **Track B (Product Shell):** R-01 → R-09 → R-10 (R-00 ပြီးသည်နှင့်စနိုင်)
- **Track C (Library/Data):** R-07 (R-05 ပြီးမှ) → R-12

**တစ်ဦးတည်း လုပ်ငန်းအစီအစဉ် (လက်တွေ့အကြံပြု):**
1. R-00
2. R-01 (အမြန်ပြီး၊ Risk နည်း၊ Gemini Config အတည်ပြုရ)
3. R-02 → R-03 → R-04 (Translation မပါ — Mechanics သက်သေပြ)
4. R-05 → R-06
5. R-07 → R-08
6. R-09, R-10 (ကြားဖြတ်လုပ်ကွက်)
7. R-11 → R-12
8. R-13 + R-14
9. R-15 → R-16

## 5. အဘယ့်ကြောင့် ဤအစီအစဉ်လဲ (အကျဉ်း — အပြည့်အစုံ DECISION-LOG)
- R-00 ကို အရင်: ယခု Device Test ၀ ဖြစ်သော Code ပေါ်တွင် Feature ထပ်တင်ခြင်းသည် Bug ကို ပုံစံမတူပေါင်းစုံဖြစ်စေ (TD-09)။
- R-01 ကို R-05 ရှေ့: Translation Live စမ်းရန် Model/Key Config ယုံကြည်ရမည်။
- R-04 ကို Translation မပါဘဲ အရင်လုပ်: DOM Replace/Restore/Scroll/Cancel ကို ဘာသာပြန်အမှားနှင့် မရောဘဲ သက်သေပြရန် (TD-06)။
- R-07 ကို R-05 နောက်: Chapter identity နှင့် Cache Schema တည်ငြိမ်မှ Library Schema ချရန် (Migration နှစ်ကြိမ် မလိုရန်)။
- R-12 ကို နောက်ပိုင်း: Schema Freeze လိုသည်၊ သို့သော် Schema JSON commit သည် R-00 တွင် စပြီးသား။
- Security Audit (R-13) ကို Feature Freeze နောက်: Audit ပြီးမှ Feature ထပ်ထည့်ခြင်းမဖြစ်ရန်။ သို့သော် Security Baseline (SB-1…10) သည် Phase တိုင်း၏ Acceptance တွင်ပါပြီးသား။

## 6. Parallelisation / Cut-line
- Release မဖြစ်မနေလိုသည့် Minimum: R-00..R-06, R-08, R-11 (အနည်းဆုံး), R-12, R-13, R-14, R-15, R-16။
- ဖြတ်လျှော့နိုင် (v1.0 မှ ဖယ်ရန် ဆုံးဖြတ်နိုင်): R-07 ၏ Discovery အပိုင်း (UD-02)၊ R-09 (Safe Mode, UD-07)၊ R-10 ၏ Optional Items (UD-11)။ ဖြတ်လျှင် Vision နှင့်ကွာဟချက်ကို User လက်မှတ်ဖြင့် မှတ်။
