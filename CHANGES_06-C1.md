# MNB Phase 06-C1 — Settings Screen Foundation

## ဖန်တီးထားသော ဖိုင်များ (အသစ် ၄ ဖိုင်)

| ဖိုင် | လုပ်ဆောင်ချက် |
|---|---|
| `app/src/main/java/com/mnb/browser/ui/settings/SettingsScreen.kt` | Settings Screen (Compose + Material 3)။ Section ၆ ခု |
| `app/src/main/java/com/mnb/browser/ui/settings/SettingsComponents.kt` | Section card၊ info row၊ note၊ placeholder |
| `app/src/main/java/com/mnb/browser/ui/settings/SettingsScreenText.kt` | `SettingsSection` enum နဲ့ စာသားများ (pure Kotlin) |
| `app/src/test/java/com/mnb/browser/ui/settings/SettingsScreenTextTest.kt` | Unit test ၈ ခု (ရေးထားရုံသာ၊ မ run ရသေး) |

ရှိပြီးသား ဖိုင် မည်သည့်ဖိုင်ကိုမှ မပြင်ထားပါ။

## Section များ

1. **General Settings** — Theme (System / Light / Dark)။ ရှိပြီးသား `ThemeMode` နဲ့ `SettingsText.themeChoices` ကို ပြန်သုံးထားသည်။ တကယ်အလုပ်လုပ်သည့် တစ်ခုတည်းသော section။
2. **Translation Settings** — `SourceLanguage` / `TargetLanguage` enum ကနေ ဖတ်ပြသည် (read-only)။
3. **Translation Provider** — "Gemini (direct)" စာသားသာ (read-only)၊ Gateway မပါ။
4. **Gemini Configuration** — placeholder (control မပါ၊ "Not available yet")။
5. **Translation Behavior** — placeholder (control မပါ)။
6. **About MNB** — App name၊ version၊ license notice။

## စည်းမျဉ်း လိုက်နာမှု

- Translation Core၊ Reader၊ Extraction၊ Worker၊ Room၊ Browser UI၊ Secure Storage မထိပါ။
- API Key Storage / UI မရေးပါ (C3 / C4 အတွက် ချန်ထား)။
- Navigation မချိတ်ပါ (C2 အတွက် ချန်ထား)။ `SettingsScreen` ကို ဘယ်နေရာကမှ မခေါ်သေးပါ။
- Animation မထည့်ပါ။ Dark / Light သည် `MaterialTheme.colorScheme` ကို လိုက်သည်။
- Responsive: content ကို `widthIn(max = 640.dp)` ဖြင့် အလယ်ထား၊ scroll လုပ်နိုင်သည်။
- Commit / Push / PR မလုပ်ပါ။

## သတိပြုရန်

- ရှိပြီးသား `PremiumSettings` မှာ Gemini API key section (06-A2) ပါပြီးသား ဖြစ်သည်။ အသစ်ရေးသော screen ရဲ့ "Gemini Configuration" placeholder က C3 မှာ ဒီ key section ကို ဘယ်လို ပြောင်း/ရွှေ့မလဲ ဆုံးဖြတ်ရန်လိုမည်။
- `SettingsScreen` မှာ `versionName` ကို parameter အဖြစ် လက်ခံသည်။ C2 မှာ ခေါ်သည့်နေရာက `PackageManager` ကနေ ဖတ်ပေးရမည်။

## Verification (အမှန်အတိုင်း)

- Build: **မ run ပါ** (ဒီ session မှာ Gradle / network မရှိ)။
- Unit test: **မ run ပါ**။
- ရေးပြီး ပြန်ဖတ်စစ်ထားရုံသာ။ Import များကို ရှိပြီးသား `PremiumSettings.kt`၊ `UiText.kt`၊ `TranslationLanguage.kt` နဲ့ ကိုက်ညီအောင် ရေးထားသော်လည်း compile မစစ်ရသေးသဖြင့် Local build ဖြင့် အတည်ပြုပါ။

## Local မှာ စစ်ရန်

```
./gradlew :app:testDebugUnitTest --tests "com.mnb.browser.ui.settings.*"
./gradlew :app:assembleDebug
```

## နောက်တစ်ဆင့် — C2

`SettingsScreen(themeMode, onThemeModeChange, onBack, versionName)` ကို ရှိပြီးသား Settings ဝင်ပေါက် (`PremiumSettings` ခေါ်သည့်နေရာ) မှာ ချိတ်ရန်။
