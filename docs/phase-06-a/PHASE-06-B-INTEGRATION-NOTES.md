# Phase 06-B — Integration notes for a Direct Gemini provider

Notes only. No code was changed. Facts below come from the repo at `49dd364`.

## 1. Getting the key
- Depend on `ApiKeyProvider` (`StoreApiKeyProvider(store)`), never on `ApiKeyStore`.
- Use the **same `SecureApiKeyStore` instance** as the settings screen. Its lock is per instance, so two instances could race. `MainActivity` currently creates the store inside the controller's `storeFactory`; Phase 06-B must hoist it (for example one lazily created store shared by the controller and the provider).
- `lookup()` blocks (Keystore + file). Call it off the main thread, at send time. Call `reveal()` only while building the request. Do not keep the key in state, logs, exceptions or caches.
- Map `Missing` → "add a key in Settings". Map `Unavailable(code)` → fixed messages per code. Never delete or repair the stored key from the provider.
- `StoreApiKeyProvider.lookup()` catches `Exception` only; an `Error` (for example `LinkageError`) can escape. Wrap the call in the provider.

## 2. Provider contract today
- `TranslationProvider { id, displayName, isMock, supportedSourceLanguages, translate(request): TranslationResult }` is synchronous (there is no coroutines dependency).
- `TranslationError`: `EMPTY_TEXT, TEXT_TOO_LONG, UNSUPPORTED_SOURCE_LANGUAGE, PROVIDER_FAILURE`.
- `TranslationResult.Failure.message` is free text: it must never contain the key or raw HTTP error text. Use fixed messages.
- `isMock` must be `false` for a real provider.

## 3. Wiring
- `MainActivity.translationProviderSource` returns `null` by design today. When a real provider exists, return it there **and** set `TranslationWorkEnvironment.providerFactory` to the same provider (same `id`). The background coordinator refuses a mismatching or mock provider.

## 4. Network
- The manifest has `INTERNET` and `usesCleartextTraffic=false`: HTTPS only.
- Send the key in a header (for example `x-goog-api-key`), never in a URL or query string, because URLs get logged.
- No new dependency without an explicit decision (no OkHttp, no coroutines yet). `HttpURLConnection` is available.
- Do not put response bodies or exception messages into `TranslationResult.Failure`.

## 5. Tests to update
- `ApiKeyLeakageTest.onlyTheSecureStoreReadsThePlainKey`: add the one new provider file to the allowlist of files that may call `reveal()`, and keep the source scan (no `Log`, `println`, `printStackTrace`) covering it.
- Add provider tests with a fake HTTP layer: missing key, unavailable key, key only in the header, no key in any failure message.

## 6. Gateway
`NB-Translation-Gateway` (Phase 05-B3) is a separate backend the app already uses. Direct Gemini is an alternative path: keep it behind the provider interface and do not modify the Gateway repository.
