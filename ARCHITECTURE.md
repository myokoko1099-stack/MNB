# Architecture

## Application structure

Package `com.mnb.browser`: `web` / `navigation` / `state` (browser), `extraction`, `reader`
(reader and translation UI wiring), `translation` (core, database, queue, Gemini provider),
`security` (secure storage), `settings` (key settings controller), `ui` (Compose screens).
`MnbApplication` (06-B4) installs process-wide background wiring; `MainActivity` wires the UI.

## Translation flow

1. The reader reports the opened chapter. `ReaderInstantTranslationController` (on demand) or
   `ReaderBackgroundTranslationCoordinator` (queue) asks for a provider.
2. The provider source returns the Gemini provider only while a key is saved; otherwise null and the
   reader shows "not configured". The mock provider is never used for real work: `TranslationProviderGuard`
   and `TranslationWorkerGuard` refuse it.
3. The chapter text is split into chunks and each chunk goes through the translation engine to the
   `TranslationProvider`, which validates the request first.
4. `GeminiTranslationProvider` adapts `DirectGeminiProvider` to the engine. It refuses to run on the
   main thread and maps every `GeminiError` to a fixed message.
5. Results are stored in the Room database (translation, jobs, memory).

Note on names: `ReaderTranslationGateway` / `UnavailableReaderTranslationGateway` is the reader's own
internal port. It has nothing to do with the NB-Translation-Gateway backend, which is not used for
translation requests.

## Gemini provider

`DirectGeminiProvider` builds the request (`GeminiPromptBuilder`), reads the key through the
read-only `ApiKeyProvider`, sends it with `HttpUrlConnectionGeminiClient` wrapped in
`RetryingGeminiHttpClient`, and parses the answer (`GeminiResponseParser`). Default model
`gemini-2.5-flash` (`GeminiConfig`), endpoint on `generativelanguage.googleapis.com`.

## Secure key flow

Settings UI -> `GeminiKeySettingsController` (single background thread) -> `SecureApiKeyStore`
(AES-GCM, Keystore key, private SharedPreferences). The controller state holds status codes only
(checking / not set / saved / unreadable). The provider reads the key via `StoreApiKeyProvider`.
There is one `SecureApiKeyStore` per process, owned by `MnbApplication`.

## Error handling flow

Network or HTTP result -> `GeminiResponseParser.errorForStatus` / parser -> fixed `GeminiError`
(missing or unavailable key, key rejected, permission, model not found, bad request, rate limited,
quota, server error, timeouts, no network, TLS, empty / malformed / truncated / blocked response,
oversized response, internal) -> `TranslationResult.Failure(PROVIDER_FAILURE, fixed message)`.
Temporary errors (HTTP 429 rate limit, 500/502/503/504, network, one read-timeout retry) are retried
by `RetryingGeminiHttpClient`; the queue's WorkManager retry handles what remains.

## Background execution

`TranslationWorker` runs in WorkManager. In production it first asks `TranslationWorkerGuard`
(provider present, not mock, same id as the reader's, language supported). Both
`TranslationWorkEnvironment.providerFactory` and `readerProviderIdSource` are installed by
`MnbApplication.onCreate`, so they exist even when Android starts the process only to run a worker
(Phase 06-B4). The worker's "is a key saved" check asks the secure storage directly, because the
settings screen's in-memory status does not exist without an Activity. `onCreate` does no Keystore,
file or network work.
