# Phase 04 / I10-D — Reader Translation Integration QA

Branch (intended): `phase-04-i10-d-qa`. Overlay: copy these files into the MNB repo at the same paths.
No git commit / push / PR was made.

## Status summary

| Area | Result |
|---|---|
| Build `./gradlew :app:testDebugUnitTest :app:assembleDebug` | **NOT VERIFIED** (no Android SDK / Gradle / network in the sandbox) |
| New and existing unit tests | **NOT VERIFIED** (never compiled, never run) |
| Phone QA | **NOT VERIFIED** (no device) |
| Code changes | written and reviewed by reading only |

Nothing below is marked PASS, because nothing was executed.

## What was audited (read only these files)

Read in full: `MainActivity.kt`, `ReaderInstantTranslationController.kt`, `TranslationProviderGuard.kt`,
`TranslationWorker.kt` (incl. `TranslationWorkEnvironment`), `TranslationJobRunner.kt`,
`InstantTranslationPipeline.kt`, `TranslationProvider.kt`, `CHANGES_I10-C2.md`,
tests `TranslationWorkerTest.kt`, `TranslationJobRunnerTest.kt`, `BackgroundTestSupport.kt`.

**Not read, therefore NOT AUDITED:** `ReaderScreen.kt`, `ReaderTranslationStateHolder.kt`,
`ReaderBackgroundTranslationCoordinator.kt`, and the reader/coordinator/holder/display tests.
Spec sections A (Reader Display), B (chapter identity, switching) and C (queue priority, navigation)
were only checked where the files above touch them. Existing tests for them were not re-reviewed.

## Findings (code review, not observed at runtime)

1. **Worker had no provider guard.** `TranslationWorkEnvironment.providerFactory` defaults to the mock
   provider and `TranslationWorker` passed it to the pipeline unchecked. The pipeline would store a COMPLETED
   translation with `isMock = true`. Only the enqueue side (coordinator) prevented this.
2. **Instant and background were not coordinated.** The pipeline's duplicate guard was a per-instance set; the
   reader and the worker each build their own pipeline, so the same chapter could be translated by both at once
   (duplicate provider calls, concurrent writes to the same translation/chunk rows).
3. **A busy chapter used up a retry attempt.** `DUPLICATE_REQUEST` was "retryable", so a long instant
   translation could push a background job toward FAILED (max 5 attempts).
4. **A refused job would stay PENDING.** A PENDING row outranks other pending jobs, which delays them
   (turn wait up to 2 s per job) while it can never run.

## Fixes (minimal; no schema change, no new provider, no UI)

- `ChapterTranslationLock` (new, `translation/usecase`): process-wide, non-blocking `tryAcquire/release`, in memory only
  (a restart frees everything).
- `InstantTranslationPipeline`: the per-instance `inFlight` set is replaced by this lock (constructor parameter
  `lock`, default `ChapterTranslationLock.shared`, added last). Acquired before any work, released in `finally`
  (success, failure, cancellation, exception). Reader and worker pipelines now share it.
- `TranslationJobRunner`: `DUPLICATE_REQUEST` -> job back to PENDING, result `DEFERRED` (WorkManager retries later),
  no attempt used. Everything else unchanged.
- `TranslationWorkerGuard` (new): pure check -> `NO_PROVIDER`, `MOCK_PROVIDER`, `PROVIDER_MISMATCH`, `UNSUPPORTED_LANGUAGE`.
- `TranslationWorker` / `TranslationWorkEnvironment`: before building its own provider-backed runner the worker runs the
  guard; on refusal it returns `failure()`, calls no provider, stores nothing, and marks a PENDING/PROCESSING job FAILED
  (finished jobs are untouched). New environment hooks: `readerProviderIdSource` (default `{ null }`, so the worker refuses
  until a real provider is wired), `jobsFactory`; `reset()` restores them.
- `MainActivity` is **not** changed.

## Tests added (not run)

`TranslationWorkerProviderGuardTest` (12): guard rejects missing provider / no reader provider / mock (also with matching id) /
id mismatch / unsupported or missing language / allows a real matching provider; worker run directly with the untouched default
environment fails, calls no provider, stores no COMPLETED translation, marks the job FAILED; same for mock, mismatch, no reader
provider, unsupported language; finished job untouched; `reset()` restores safe defaults.

`InstantBackgroundConflictTest` (13): background defers while instant holds the chapter (no provider call, job PENDING, lock
not stolen); deferral uses no attempt (`maxAttempts = 1`); deferred job later reuses the instant result (no duplicate
translation); instant rejected while background holds it (nothing written); reader maps the busy outcome to Processing;
separately built pipelines share the default lock; lock released after success / failure / cancellation / stop+retry /
provider exception; different chapters independent.

Not duplicated: existing priority, retry and worker tests (`TranslationJobRunnerTest`, `TranslationWorkerTest`, priority tests) are
unchanged; `TranslationWorkerTest` injects `runnerFactory` and keeps its own provider.

## Known limitations / NOT fixed

- **Guard scope:** the guard runs only while the worker builds its own runner. A test (or future code) that injects
  `runnerFactory` supplies its own provider and bypasses it. Production never injects one.
- **No test of the "guard allows" path through the default runner**: it needs the real `MnbDatabase` singleton under
  Robolectric. Untested.
- **Reader stays on Processing** when an instant request hits a busy chapter (existing behaviour in `mapOutcome`). The
  translation finished by the background job is only picked up by `onChapterShown` while the state is OriginalOnly, so the
  reader may keep showing Processing until the chapter is reopened. Fixing it needs reader state changes (out of scope).
- **Lock is per process.** WorkManager runs in the app process by default; a multi-process setup is not covered.
- **Real provider later:** set `providerFactory`, `readerProviderIdSource` and the reader's provider source to the same provider.
- Shared static lock: a test that leaves a chapter locked would affect later tests with the same chapter id. The new code releases in `finally`.
- Test code assumes (from the read tests) `DbTestBase.db`, `seedChapter`, `TranslationJobRepository.get/updateProgress/latestForChapter`,
  `TranslationJob.errorCode/completedChunks/createdAtMillis`, `MockTranslationProvider()` with `id`. A compile error on any of these is possible.
- Sections A, B (partly) and C of the spec: NOT AUDITED (see above).

## Automated QA result

- Build: **NOT VERIFIED**
- Unit tests (existing + new): **NOT VERIFIED**
- Phone QA: **NOT VERIFIED**
