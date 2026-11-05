# Phase 06-B1 — Direct Gemini Provider Core (report)

## Status
Code and tests were **written only. Nothing was compiled, built or run.** No commit, push or PR was made. Everything below marked NOT VERIFIED stays unverified until you run the build.

## What was built
New package `com.mnb.browser.translation.gemini` (7 files, ~540 lines):

| Piece | Role |
|---|---|
| `GeminiProvider` (interface) / `DirectGeminiProvider` | `translate(TranslationRequest): GeminiResult`. Never throws. |
| `GeminiConfig` | Model (default `gemini-2.5-flash`), HTTPS base URL, `x-goog-api-key` header name, timeouts, temperature. Validates HTTPS and a safe model-name pattern. |
| `GeminiHttpClient` + `HttpUrlConnectionGeminiClient` | Network layer behind an interface (fakeable). Platform `HttpURLConnection`, no new dependency. |
| `GeminiPromptBuilder` / `GeminiResponseParser` | Request JSON (system instruction + user text, glossary/characters/story/instructions from `TranslationContext`) and response parsing. |
| `GeminiResult` / `GeminiError` | Success or one of 17 error codes, each with one fixed message and a `retryable` flag. |

## How the requirements were met
1. **Architecture studied first.** Existing facts used: `TranslationProvider` is synchronous, no coroutines/HTTP library in `build.gradle.kts`, `TranslationWorker` is a plain `Worker`, manifest has `INTERNET` and cleartext off.
2. **Key source.** Only `ApiKeyProvider.lookup()` (Phase 06-A). No other source, no shared key, no hardcoded key, no `BuildConfig`.
3. **Direct call.** `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`.
4. **Key only in a header.** `x-goog-api-key`. `GeminiHttpRequest`/`GeminiHttpResponse` print no headers/body. Errors are fixed `GeminiError` messages; no exception message, HTTP error body or response text is copied into a result. Redirects are not followed, so the header cannot be forwarded elsewhere.
5. **Separate interface/class.** `GeminiProvider` / `DirectGeminiProvider`.
6. **Coroutines / networking architecture.** See "Decisions" 1.
7. **HTTPS only.** `GeminiConfig` rejects non-HTTPS base URLs; the client returns `INSECURE_URL` before connecting for any other scheme.
8. **No key → no request.** Missing → `MISSING_API_KEY`; unavailable (any `SecureStorageError`, or a throwing lookup including an `Error`) → `API_KEY_UNAVAILABLE`. The HTTP client is not called. Invalid text (empty/too long) is refused even before the key is read.
9. **Kept separate from the translation layer.** Not a `TranslationProvider`; nothing outside `gemini/` references it (a guard test checks this).
10. **Existing translation untouched.** No existing main file changed.

## Decisions (please confirm or overrule)
1. **No coroutines.** The task asked to follow the project's coroutine/networking architecture, but the project has no coroutines dependency, `TranslationProvider` is documented as synchronous, and "no unneeded dependencies" applies. So `translate` is blocking and must run off the main thread (e.g. in the WorkManager worker). A later `suspend` wrapper (`withContext(Dispatchers.IO)`) can be added when coroutines are introduced on purpose.
2. **Default model `gemini-2.5-flash`** was chosen from memory and is NOT VERIFIED as current. It is one constant in `GeminiConfig`.
3. **No custom safety settings.** Gemini's defaults may block some novel content (violence, adult themes); that returns `CONTENT_BLOCKED`. Tuning this is a product decision for a later session.
4. **A truncated answer (`MAX_TOKENS`) is a failure,** never a partial translation.
5. **Adapter not included.** `GeminiResult` → `TranslationResult` mapping is left for the wiring session.

## Tests (written, NOT RUN) — 46 `@Test`
- `DirectGeminiProviderTest` (22): missing/unavailable/throwing key sends nothing; key only in header (not URL/body/other headers); endpoint is HTTPS; status-code table; 400 "API key not valid"; error body never copied; transport-failure table; throwing HTTP client; response problems (malformed, empty, blocked, truncated, JSON null, thought parts).
- `GeminiRequestBuildingTest` (16): config validation; request JSON; exact text (quotes, newlines, Myanmar/Chinese, U+2028); language names; context placement; HTTPS-only client; redacted `toString`.
- `GeminiSourceGuardTest` (8): no logging/printing, no `http://`, no key in URL, only `DirectGeminiProvider.kt` calls `reveal()`, provider never saves/updates/deletes the key, nothing hardcoded, not wired into the app.
- `ApiKeyLeakageTest` (modified): allowlist now lists `DirectGeminiProvider.kt`, as planned in the 06-A notes.

## Not verified / risks
- Nothing compiled or run: compile errors are possible in all 11 files.
- No real call to Gemini was made. Request shape (`systemInstruction`, `generationConfig`) and the error handling follow the public REST format from memory. NOT VERIFIED against the live API.
- The patch was generated with `git` from a base rebuilt from the file contents read through GitHub, and applies to that base. It was NOT checked against your real checkout (Termux).
- `org.json` behaviour on a device differs slightly from the test JVM library; the null-text case is handled explicitly, other differences are NOT VERIFIED.
- `HttpURLConnection` timeouts apply per read, so a slow trickle can exceed `readTimeoutMillis` in total.
- The Gemini API key is sent from the device straight to Google: the user's own quota and billing apply.

## For the next session (wiring)
- Add a `TranslationProvider` adapter: map `GeminiResult.Success` → `TranslationResult.Success(isMock = false)`, every `Failure` → `PROVIDER_FAILURE` with `GeminiError.message` as the fixed message.
- Use the same `SecureApiKeyStore` instance as the settings screen (hoist it out of `MainActivity`'s controller `storeFactory`).
- Return the provider from `MainActivity.translationProviderSource` and set `TranslationWorkEnvironment.providerFactory` and `readerProviderIdSource` to the same id (`gemini-direct`), otherwise `TranslationWorkerGuard` refuses.

## How to run later (for you to run)
```
./gradlew :app:testDebugUnitTest --tests "com.mnb.browser.translation.gemini.*" --tests "com.mnb.browser.security.*"
```
