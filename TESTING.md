# Testing

## Test structure

Unit tests live in `app/src/test/java/com/mnb/browser/` and mirror the main packages: `extraction`,
`navigation`, `reader`, `security`, `settings`, `translation` (with `background`, `chunking`, `db`,
`gemini`, `memory`, `quality`, `reader`, `usecase`) and, since 06-B4, `MnbApplicationTest`.
JUnit 4 with Robolectric (SDK 34) where Android classes are needed (`DbTestBase` gives an in-memory
Room database).

Conventions: only fake keys; no real network (a fake `GeminiHttpClient` is injected); no real Keystore
(fake `AeadCipher` / storage); source-scan guard tests read the main sources and fail on logging,
cleartext, key-in-URL, second readers of the plain key and committed secrets.

## Test coverage

| Area | Main test classes |
| --- | --- |
| Secure storage, encryption | `security/*` (cipher, store, storage, provider, `ApiKeyLeakageTest`) |
| API key settings | `settings/GeminiKeySettings*Test` (controller, state, hardening, error handling) |
| Direct Gemini provider | `DirectGeminiProviderTest`, `GeminiRequestBuildingTest` |
| HTTP errors, timeout, rate limit, retry | `GeminiErrorHandlingTest`, `GeminiRetryTest` |
| Translation integration | `GeminiTranslationProviderTest`, `GeminiTranslationIntegrationTest` |
| Mock provider | `MockTranslationProviderTest`, `TranslationServiceTest` |
| Background worker | `TranslationWorkerTest`, `TranslationWorkerProviderGuardTest`, `MnbApplicationTest` |
| Redaction / secrets | `ApiKeyLeakageTest`, `GeminiSourceGuardTest`, `ProjectSecretsGuardTest` |
| Reader flow | `reader/*`, `translation/reader/*` |

Exact names and counts: FINAL-TEST-REPORT.md.

## GitHub Actions

`.github/workflows/build.yml` runs on every push and on manual dispatch: JDK 17, Gradle 8.7,
`gradle :app:testDebugUnitTest :app:assembleDebug`, then uploads the debug APK. It does not run lint
or a release build. Tests are not run locally in this project's workflow; a phase counts as passed only
when this workflow is green.

## Regression test guide

- Changed anything under `security/` or the key UI: run the whole suite; watch `ApiKeyLeakageTest`.
- Changed `translation/gemini/`: `GeminiSourceGuardTest`, `GeminiErrorHandlingTest`, `GeminiRetryTest`.
- Changed `MainActivity` or `MnbApplication`: `MnbApplicationTest`, `GeminiSourceGuardTest`,
  `TranslationWorkerProviderGuardTest`.
- Changed Gradle, manifest or `.gitignore`: `ProjectSecretsGuardTest`.
- Never add a real key, URL or network call to a test.
- Not coverable by unit tests: WorkManager restarting a killed process on a real device.
