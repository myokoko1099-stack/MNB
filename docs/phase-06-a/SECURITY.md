# Phase 06-A — Security notes for the Gemini API key

All tests named below were **written in Phase 06-A4 and not run** (see `PHASE-06-A-FINAL-REPORT.md`).
Class names are in `com.mnb.browser.security` or `com.mnb.browser.settings`.

## 1. Threat model

| Area | Threat | Protection |
|---|---|---|
| At rest | Another app or a file dump reads the key from app storage | Only an AES-256-GCM envelope is stored. The AES key lives in the Android Keystore (non-exportable), so the file alone is useless. |
| At rest | Edited or swapped file content | GCM tag + AAD `mnb:gemini_api_key:v1` → `DECRYPTION_FAILED` / `CORRUPTED_DATA`, never a wrong key. |
| In memory | Key stays around after use | Plain `ByteArray` buffers are zeroed in `finally` (save, failed encrypt, failed write, read, invalid content). |
| Logs | Key reaches logcat, stdout, exception text | No logging in the key code; results and state carry codes and class names only, never messages. |
| UI / saved state | Key reaches `SavedStateHandle`, state classes | The settings state has no text field; the typed text uses plain `remember`, is masked, hidden on `ON_PAUSE` and cleared on `ON_STOP`. |
| Backup | Encrypted file copied to another device by backup | `allowBackup="false"`. The Keystore key would not travel anyway. |
| Device restore / Keystore loss | Blob exists but the Keystore key is gone | Status becomes `UNREADABLE`; the user can save a new key (nothing is deleted automatically). |
| Network | Key sent over cleartext | `usesCleartextTraffic="false"` (HTTPS only). |

## 2. What is NOT protected

- A rooted device, a debugger, or runtime memory inspection of the running process.
- The key is briefly held as a `String` inside `ApiKey` (e.g. after `reveal()`). Strings cannot be zeroed; only the byte buffers are.
- Anyone who can use the unlocked app can open Settings and replace or delete the key (no per-use user authentication is required by the Keystore key).
- Nothing in this phase reads the key for a request yet (Phase 06-B).

## 3. Guarantees → tests

| Guarantee | Tests |
|---|---|
| Key text is validated and redacted | `ApiKeyTest`: `toStringNeverShowsTheKey`, `theKeyStaysHiddenInStringTemplatesAndCollections`, `revealIsTheOnlyWayToGetTheText`, `maxLengthIsAcceptedAndOneMoreIsRejected`, `nonAsciiTextIsRejected`, `controlCharactersAreRejected` |
| No key bytes at rest | `ApiKeyLeakageTest`: `storedBytesHoldNoPartOfTheKey`, `theKeystoreKeyIsNotPartOfTheStoredBytes`, `anUpdateLeavesNoTraceOfTheOldKeyInTheStoredBytes` |
| Tampering is always detected | `AesGcmCipherExtendedTest`: `changingAnySingleByteAfterTheHeaderIsDetected`, `changingTheHeaderIsReportedAsMalformed`, `extraBytesAppendedToTheEnvelopeAreDetected`, `swappingInAnotherMessagesTagIsDetected`, `theContextIsCaseSensitive`; `SecureApiKeyStoreFailureTest`: `changingAnyByteAfterTheHeaderIsADecryptionFailure`, `anEnvelopeMadeForAnotherPurposeIsADecryptionFailure` |
| Fresh IV every time | `AesGcmCipherExtendedTest`: `manyEncryptionsOfTheSameTextNeverRepeatAnIv`, `twoEncryptionsOfTheSameTextGiveDifferentCiphertext` |
| Failed operations never damage stored data | `SecureApiKeyStoreLifecycleTest`: `anInvalidUpdateLeavesTheStoredBytesUntouched`, `anInvalidSaveLeavesTheStoredBytesUntouched`, `readingDoesNotWriteAnything`; `SecureApiKeyStoreFailureTest`: `failedReadsNeverChangeTheStoredBytesOrTheKeystoreKey` |
| Recovery after Keystore loss / corruption | `SecureApiKeyStoreFailureTest`: `saveReplacesDataWhoseKeystoreKeyIsGone`, `saveReplacesCorruptedBytes`, `updateReplacesDataThatCannotBeDecrypted`; `GeminiKeySettingsStateTest`: `aLostKeystoreKeyMakesASavedKeyUnreadableAndSavingFixesIt` |
| Delete really removes the key | `SecureApiKeyStoreLifecycleTest`: `deleteRemovesDataThatCanNoLongerBeDecrypted`, `theOldKeyCannotComeBackAfterDeleteAndANewSave`, `aDeleteThatFailedCanBeRetriedAfterTheStorageRecovers`; existing `GeminiKeySettingsControllerHardeningTest.deleteIsOnlyReportedWhenNothingIsStoredAnyMore` |
| Consumers can only read | `ApiKeyProviderFailureTest`: `theProviderInterfaceHasOnlyLookup`, `theStoreBackedProviderHasNoWayToChangeTheKey`, `failedLookupsNeverWriteOrDeleteAnything`, `everyLookupResultIsSafeToPrint` |
| Error text never leaks | `ApiKeyLeakageTest`: `exceptionMessagesFromAPoisonedKeystoreNeverReachResultsOrState`, `...PoisonedStorage...`, `...PoisonedCipher...`, `exceptionMessagesFromAPoisonedStoreNeverReachTheControllerState`; `SecureApiKeyStoreFailureTest`: `theFailureCarriesOnlyAClassNameOfTheCause`, `failedReadsDoNotLeakTheKeyOrTheStoredBytesIntoTheResultText`; `AesGcmCipherExtendedTest`: `cipherExceptionsNeverContainDataForAnyReason`; `GeminiKeySettingsErrorHandlingTest`: `aThrowableFromAnyStoreFunctionEndsInAFixedStateWithoutKeyText` |
| Console and log silence | `ApiKeyLeakageTest`: `theWholeFlowPrintsAndLogsNothingThatContainsTheKey`, `theWholeFlowIsSilentOnTheConsole`, `noLoggingOrPrintingInTheKeyHandlingCode`, `theKeyHandlingSourcesWereFound` |
| Plain buffers are zeroed | `ApiKeyLeakageTest`: `theBytesHandedToTheCipherOnSaveAreClearedAfterwards`, `...EvenWhenEncryptionFails`, `...EvenWhenTheWriteFails`, `theDecryptedBytesAreClearedAfterASuccessfulRead`, `theDecryptedBytesAreClearedWhenTheContentIsNotAValidKey` |
| No key text in UI state or saved state | `GeminiKeySettingsStateTest.theStateHasNoFieldThatCouldHoldKeyText`; `ApiKeyLeakageTest`: `noKeyTextFieldExistsInTheSettingsStateOrResults`, `typedTextThatIsRejectedIsNeverKeptInTheState`, `theTypedKeyIsNeverPutIntoSavedInstanceState`, `theKeyFieldIsMaskedByDefaultAndUsesAPasswordKeyboard`, `theTypedKeyIsClearedWhenTheAppStops` |
| Backup off | `ApiKeyLeakageTest.appBackupIsOffSoTheEncryptedFileIsNotCopiedToOtherDevices` |
| Only the store reads the plain key | `ApiKeyLeakageTest.onlyTheSecureStoreReadsThePlainKey` (an allowlist; must be updated in Phase 06-B) |
| Thread safety | `SecureApiKeyStoreLifecycleTest`: `concurrentSavesAndReadsNeverFailAndNeverMixValues`, `concurrentDeletesAndSavesEndInAConsistentState`; `GeminiKeySettingsStateTest`: `theStoreIsOnlyTouchedOnTheExecutorThread`, `whileASaveIsQueuedTheStateIsBusyAndOtherwiseUnchanged` |
| Failures never crash or stick | `GeminiKeySettingsErrorHandlingTest`: `anOutOfMemoryErrorIsRethrownButTheControllerIsNotLeftBusy`, `aStoreFactoryThatFailsOnceIsRetriedOnTheNextOperation`, `aRejectedExecutorLeavesTheDefaultStateAndNotBusy`, the `every...Failure...` matrix tests |

## 4. Manifest settings relied on

- `android:allowBackup="false"`
- `android:usesCleartextTraffic="false"`
- `INTERNET` permission is present (needed from Phase 06-B on).
