# Phase 06-B1 — Changed files

Base: `myokoko1099-stack/MNB` at `a9e862d` (Phase 06-A merged). Nothing was compiled, built or run.

## New — main (7)
- `app/src/main/java/com/mnb/browser/translation/gemini/DirectGeminiProvider.kt` — `GeminiProvider` interface + `DirectGeminiProvider` (key from `ApiKeyProvider`, header-only key)
- `app/src/main/java/com/mnb/browser/translation/gemini/GeminiConfig.kt` — model, HTTPS base URL, header name, timeouts
- `app/src/main/java/com/mnb/browser/translation/gemini/GeminiResult.kt` — `GeminiResult`, `GeminiError` (fixed messages)
- `app/src/main/java/com/mnb/browser/translation/gemini/GeminiHttp.kt` — `GeminiHttpClient` interface and request/response types (redacted `toString`)
- `app/src/main/java/com/mnb/browser/translation/gemini/HttpUrlConnectionGeminiClient.kt` — HTTPS-only `HttpURLConnection` client
- `app/src/main/java/com/mnb/browser/translation/gemini/GeminiPromptBuilder.kt` — request JSON + system instruction
- `app/src/main/java/com/mnb/browser/translation/gemini/GeminiResponseParser.kt` — response parsing, HTTP status mapping

## New — tests (3, 46 `@Test`, not run)
- `app/src/test/java/com/mnb/browser/translation/gemini/DirectGeminiProviderTest.kt` (22)
- `app/src/test/java/com/mnb/browser/translation/gemini/GeminiRequestBuildingTest.kt` (16)
- `app/src/test/java/com/mnb/browser/translation/gemini/GeminiSourceGuardTest.kt` (8)

## Modified (1)
- `app/src/test/java/com/mnb/browser/security/ApiKeyLeakageTest.kt` — one test renamed and its allowlist extended with `DirectGeminiProvider.kt` (planned in `docs/phase-06-a/PHASE-06-B-INTEGRATION-NOTES.md`, section 5). No other line changed.

## Deliverable documents (2)
- `CHANGED_FILES.md` (this file; replaces the previous phase's file at the repo root)
- `SESSION-06-B1-REPORT.md`

## Not touched
`NB-Translation-Gateway`, Phase 06-A secure storage (`security/`, `settings/`, `ui/`), `MainActivity`, `TranslationWorker`, all translation code outside the new `gemini/` package, `build.gradle.kts`, the manifest.
