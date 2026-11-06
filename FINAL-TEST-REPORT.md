# Final test report - Phase 06-B4

## Status: tests NOT run by the author

No test or build was run locally (project rule). **Nothing in this report is a pass result.** The only
authority is GitHub Actions on the 06-B4 branch. Until that run is green, the status of every test
below, new or old, is *unverified for B4*.

Last result seen: workflow `build` on PR #26 (Phase 06-B3) finished `success` (run 42127639051,
2026-11-05). No run exists yet for the 06-B4 changes.

## New and changed tests in 06-B4

### MnbApplicationTest (17 tests) - background worker wiring in a process with no Activity
- theManifestApplicationIsMnbApplication
- theManifestDeclaresMnbApplication
- afterProcessStartTheWorkerProviderIsGeminiAndNotTheMock
- theWorkerProviderIsTheSameInstanceOnEveryCall
- withoutASavedKeyAFreshProcessWorkerIsRefusedAsNoProvider
- withASavedKeyAFreshProcessWorkerMayTranslate
- theWorkerReaderIdIsTheGeminiIdOnlyWhileAKeyIsSaved
- anUnsupportedLanguageIsStillRefusedWithAKeySaved
- beforeTheInstallTheMockDefaultIsNeverUsedForRealWork
- installingAgainAfterAResetRestoresTheGeminiProvider
- installingTwiceChangesNothing
- startingTheProcessWritesNothingToTheSecureStorage
- theKeyStoreIsOneInstancePerProcess
- mainActivityNoLongerSetsTheWorkEnvironment
- mainActivityUsesTheApplicationsKeyStore
- onCreateOnlyInstallsTheEnvironment
- theApplicationNeverLogsOrReadsThePlainKey

### ProjectSecretsGuardTest (8 tests) - project-wide secret and manifest scan
- theProjectLayoutWasFound
- noGoogleApiKeyIsWrittenInTheMainSourcesOrBuildFiles
- noPrivateKeyBlockIsWrittenInTheMainSourcesOrBuildFiles
- noSigningPasswordIsWrittenInTheGradleFiles
- noKeystoreOrCredentialFileIsInTheProject
- gitignoreKeepsSigningSecretsOutOfGit
- theManifestAsksOnlyForTheInternetPermission
- cleartextTrafficAndBackupAreOff

### GeminiSourceGuardTest (8 tests, 1 renamed and widened)
- `onlyMainActivityUsesTheGeminiPackage` is now `onlyTheAppWiringFilesUseTheGeminiPackage`: allows exactly MainActivity.kt and MnbApplication.kt (compared as a set).

New tests: 25.

## Existing tests read and counted by the author (by reading the source, not by running)

| Class | @Test count | Purpose |
| --- | --- | --- |
| `GeminiErrorHandlingTest` | 32 | every HTTP status, timeout, network, TLS, malformed/empty response, fixed messages, retry never reads the key |
| `GeminiSourceGuardTest` | 8 | no logging, no cleartext, key never in URL, one reader of the plain key, wiring files |
| `ApiKeyLeakageTest` | 23 | key never in buffers, state, logs, instance state, error text; masked field; lifecycle clearing |
| `GeminiTranslationProviderTest` | 15 | Gemini-to-engine adapter, error mapping, main-thread guard |
| `GeminiTranslationIntegrationTest` | 20 | service to Gemini chain, instant pipeline with a real in-memory database, wiring, mock regression |
| `TranslationWorkerProviderGuardTest` | 12 | worker refuses missing, mock, mismatched, unsupported providers |
| `TranslationWorkerTest` | 6 | worker success, retry, give-up, WorkManager constraints |

Subtotal counted: 116 existing + 25 new = 141 tests.

## Existing test classes NOT counted (files exist; names and counts not read in this session)

`DirectGeminiProviderTest`, `GeminiRequestBuildingTest`, `GeminiRetryTest`; `security/` (cipher, store,
storage, provider tests); `settings/` (`GeminiKeySettingsControllerTest`, `...HardeningTest`,
`...ErrorHandlingTest`, `...StateTest`); `translation/` core, `background/` (queue, runner, priority,
conflict), `chunking`, `db`, `memory`, `quality`, `reader`, `usecase`; `extraction`, `navigation`, `reader`.
A-phase names and counts are listed in `PHASE-06-A-TEST-INVENTORY.md`.

## Not verified / cannot be verified here

- Whether any of the 141 counted tests passes: not run. GitHub Actions decides.
- Risk to watch in the first run: Robolectric now creates `MnbApplication` for every Robolectric test.
  `onCreate` only stores lambdas, and the existing worker tests either inject a runner or call
  `TranslationWorkEnvironment.reset()`, so no change is expected; the first CI run is the proof.
  `TranslationWorkerProviderGuardTest.defaultEnvironmentWorkerRefusesTheMockDefaultAndStoresNothing`
  still passes for a different reason (no key saved, so `NO_PROVIDER`).
- WorkManager restarting a killed process on a real device (RELEASE-CHECKLIST.md).
- Real Keystore behaviour (unit tests use fakes; Robolectric has no Android Keystore).
- Real calls to the Gemini API (no network in tests).
