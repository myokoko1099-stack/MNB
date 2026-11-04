# Phase 04 / I10-C2 — Background Pipeline Integration

Branch (intended): `phase-04-i10-c2-background-pipeline`
Overlay: copy these files into the MNB repo at the same paths. No git commit / push / PR was made.

## Files

Modified (main)
- `app/src/main/java/com/mnb/browser/MainActivity.kt` — one shared `translationProviderSource` (still `null`), background executor, `BackgroundTranslationWiring`, coordinator, holder listener.
- `app/src/main/java/com/mnb/browser/reader/translation/ReaderTranslationStateHolder.kt` — new optional constructor parameter `chapterListener` (default `null`); called after a chapter with an identity is opened.

New (main)
- `reader/translation/ReaderBackgroundTranslationCoordinator.kt` — navigation → queue + priority (HIGH current / NORMAL next 3 / LOW later).
- `reader/translation/BackgroundTranslationWiring.kt` — builds the ports from the existing repositories, `TranslationQueue`, `TranslationPriorityManager`, `WorkManagerTranslationScheduler`.

New (test)
- `app/src/test/java/com/mnb/browser/reader/translation/ReaderBackgroundTranslationCoordinatorTest.kt`
- `app/src/test/java/com/mnb/browser/reader/translation/ReaderTranslationStateHolderChapterListenerTest.kt`

Not touched: DB schema, TranslationQueue, TranslationPriorityManager, TranslationWorkScheduler, TranslationWorker, TranslationJobRunner, I10-C1 controller/wiring, ReaderScreen, BrowserScreen.

## Behaviour

When the reader opens a chapter (any thread-heavy work runs on a separate single background thread):
1. No provider -> report `NOT_CONFIGURED`; a mock provider -> `MOCK_ONLY`; nothing is queued, the database and WorkManager are not opened.
2. The background worker's provider (`TranslationWorkEnvironment.providerFactory`, which still defaults to the mock) must be non-mock and have the same id as the reader's provider, else `WORKER_PROVIDER_MISMATCH` and nothing starts.
3. The open chapter's original text is stored only if that chapter is not stored yet (existing text is never overwritten here).
4. Every stored chapter from the current one onwards goes to the existing `TranslationQueue` (it refuses duplicates and chapters with a completed translation): current = HIGH, next 3 = NORMAL, later = LOW. Earlier chapters are not touched.
5. Already waiting jobs are re-prioritised with `TranslationPriorityManager.onReaderChapterChanged`; PROCESSING jobs keep their priority.
6. If the reader has moved to another chapter, the sync for the old chapter stops (`SUPERSEDED`). The coordinator never writes the reader's translation state.
7. Later chapters whose last job FAILED or was CANCELLED are not queued again on every navigation (the current chapter is).

WorkManager behaviour (unique work per chapter, network constraint, backoff, cancel) is unchanged and reused through `WorkManagerTranslationScheduler`.

## Tests added
Priority recalculation on navigation, HIGH/NORMAL/LOW, agreement with the priority manager, running job keeps priority, no duplicate jobs/work, completed chapter not queued again, failed/cancelled not retried, no provider -> NOT_CONFIGURED, mock provider / mock worker / other worker provider / unsupported language / missing binding start nothing, work never runs on the caller's thread, old chapter results never touch the reader state, WorkManager offline wait + one unique work + cancellation kept, holder listener behaviour.

## NOT verified
- NOT compiled and NOT run here (no Kotlin/Gradle in the sandbox): run CI / `./gradlew testDebugUnitTest`.
- Test code assumes `SourceLanguage.CHINESE`, `MockTranslationProvider()` (no-arg), `TranslationRepository.getTranslation`, and the existing test helpers (`DbTestBase`, `FakeScheduler`, `FakeProvider`, `buildRunner`, `C1*` helpers) as read from the repo.

## Not finished / open points
- Real provider: none exists. When one is added, return it from `MainActivity.translationProviderSource` AND set `TranslationWorkEnvironment.providerFactory` to the same provider.
- `TranslationWorkEnvironment` still defaults to the mock provider (not changed, to keep Worker tests and behaviour). Nothing is enqueued while it is a mock, but a hard guard inside the worker is a candidate for a later phase.
- Chapters are queued only if their original text is already stored (the reader stores the open chapter; other chapters appear once opened). Fetching unopened chapters is not part of this phase.
- Background results reach the reader only through the stored translation (existing `onChapterShown`); the reader gateway in MainActivity is still `UnavailableReaderTranslationGateway`.
- A background job and an instant translation of the same chapter are not coordinated with each other (instant pipeline has its own in-memory duplicate guard).
- No cap on how many later chapters are queued as LOW.
