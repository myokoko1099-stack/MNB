# MNB

MNB is an Android web-novel reader. It opens pages in an in-app browser, extracts the chapter text,
shows it in a reader, and translates chapters into Myanmar (Burmese) with Google Gemini, using the
**user's own API key** (bring your own key, BYOK).

## Features

- In-app browser with navigation and session restore.
- Chapter text extraction and a reader with saved position and reader settings.
- Chapter translation into Myanmar. Source languages: English, Chinese, Korean, Japanese.
- Instant translation (on demand) and a background queue (WorkManager) with priorities.
- Translations, chapters, jobs and translation memory are stored locally (Room database).
- Gemini API key settings: save, update, delete, show/hide while typing. The key is stored encrypted
  (Android Keystore + AES-256-GCM).
- Clear, fixed error messages for missing key, rejected key, quota, rate limit, timeout, network and
  server problems. Automatic retry with bounded backoff for temporary failures.

## Architecture (short)

```
User -> MNB app -> user's securely stored Gemini API key -> Google Gemini API -> translation
```

- Every translation request goes directly from the device to Google. No MNB server is involved.
- NB-Translation-Gateway is not used for translation requests.
- Details: [ARCHITECTURE.md](ARCHITECTURE.md). Security model: [SECURITY.md](SECURITY.md).

## Build

Requirements used by the CI workflow: JDK 17 (Temurin), Gradle 8.7. Android Gradle Plugin 8.5.2,
Kotlin 1.9.24, compileSdk/targetSdk 34, minSdk 24.

- Debug APK and unit tests: `gradle :app:testDebugUnitTest :app:assembleDebug`
  (this is exactly what `.github/workflows/build.yml` runs on every push).
- A release build has no signing configuration in the repository. It is unsigned until the owner
  adds signing outside the repository. Never commit a keystore or a password.

## API key setup

1. Create a Gemini API key in Google AI Studio (https://aistudio.google.com/apikey).
2. In the app, open Settings and find the **Gemini API Key** section.
3. Paste the key (no spaces or line breaks) and tap **Save**. Use **Show** to check what you typed.
4. The status line reads "Key saved (stored encrypted on this device)". The field is cleared and the
   key is never shown again. To replace it, type a new key and tap **Update**. **Delete** removes it.

Chapter text is sent to Google under your key. Costs and quota are those of your Google account.

## Documentation

[ARCHITECTURE.md](ARCHITECTURE.md), [SECURITY.md](SECURITY.md), [TESTING.md](TESTING.md),
[RELEASE-NOTES.md](RELEASE-NOTES.md), [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md),
[PHASE-06-FINAL-REPORT.md](PHASE-06-FINAL-REPORT.md), [FINAL-TEST-REPORT.md](FINAL-TEST-REPORT.md).
