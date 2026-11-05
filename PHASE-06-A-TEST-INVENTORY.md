# Phase 06-A4 — Test inventory

Status of every test below: **written, not run, not built** (no Gradle, no emulator in this session).

The numbered requirements (1-7) of the original task were not part of the handover, so each test is mapped to the area it covers instead.

## Counts

| File | New `@Test` methods |
|---|---|
| `app/src/test/java/com/mnb/browser/security/AesGcmCipherExtendedTest.kt` | 26 |
| `app/src/test/java/com/mnb/browser/security/ApiKeyLeakageTest.kt` | 23 |
| `app/src/test/java/com/mnb/browser/security/ApiKeyProviderFailureTest.kt` | 13 |
| `app/src/test/java/com/mnb/browser/security/ApiKeyTest.kt` | 18 |
| `app/src/test/java/com/mnb/browser/security/SecureApiKeyStoreFailureTest.kt` | 23 |
| `app/src/test/java/com/mnb/browser/security/SecureApiKeyStoreLifecycleTest.kt` | 23 |
| `app/src/test/java/com/mnb/browser/settings/GeminiKeySettingsErrorHandlingTest.kt` | 10 |
| `app/src/test/java/com/mnb/browser/settings/GeminiKeySettingsStateTest.kt` | 15 |
| **Total new** | **151** |

Existing tests (A1–A3, e.g. `ApiKeyProviderTest`, `GeminiKeySettingsControllerHardeningTest`) were not edited and were not counted here: the repo could not be cloned in this sandbox.

## AesGcmCipherExtendedTest

`app/src/test/java/com/mnb/browser/security/AesGcmCipherExtendedTest.kt` — AES-GCM cipher: round trip, envelope, IV, tampering, key provider failures — 26 tests, written, not run

- `emptyPlaintextRoundTrips`
- `envelopeSizeIsHeaderPlusIvPlusPlaintextPlusTag`
- `everyByteValueRoundTrips`
- `aLargePlaintextRoundTrips`
- `nonAsciiTextRoundTripsAsUtf8Bytes`
- `twoCipherObjectsWithTheSameKeyAndContextAreInterchangeable`
- `anEmptyContextWorksButIsNotInterchangeableWithANonEmptyOne`
- `theContextIsCaseSensitive`
- `manyEncryptionsOfTheSameTextNeverRepeatAnIv`
- `twoEncryptionsOfTheSameTextGiveDifferentCiphertext`
- `changingAnySingleByteAfterTheHeaderIsDetected`
- `changingTheHeaderIsReportedAsMalformed`
- `extraBytesAppendedToTheEnvelopeAreDetected`
- `bytesRemovedFromTheStartOfTheCiphertextAreDetected`
- `swappingInAnotherMessagesTagIsDetected`
- `encryptDoesNotChangeTheInputArray`
- `decryptDoesNotChangeTheEnvelope`
- `aFailedDecryptDoesNotChangeTheEnvelopeEither`
- `aKeyProviderThatThrowsWhenReadingGivesKeyUnavailableWithoutItsMessage`
- `aKeyThatAesRejectsOnDecryptIsADecryptFailure`
- `aKeyThatStaysBrokenIsReplacedOnlyOnceAndThenEncryptFails`
- `aHealthyKeyIsNeverDeletedWhileEncrypting`
- `encryptCreatesTheKeyOnFirstUseAndReusesItAfterwards`
- `afterTheKeyIsReplacedOldEnvelopesCanNoLongerBeRead`
- `deletingTheKeyTwiceIsHarmless`
- `cipherExceptionsNeverContainDataForAnyReason`

## ApiKeyLeakageTest

`app/src/test/java/com/mnb/browser/security/ApiKeyLeakageTest.kt` — Leakage guards: at rest, console/logs, error text, buffer zeroing, source scan — 23 tests, written, not run

- `storedBytesHoldNoPartOfTheKey`
- `theKeystoreKeyIsNotPartOfTheStoredBytes`
- `anUpdateLeavesNoTraceOfTheOldKeyInTheStoredBytes`
- `theWholeFlowPrintsAndLogsNothingThatContainsTheKey`
- `theWholeFlowIsSilentOnTheConsole`
- `exceptionMessagesFromAPoisonedKeystoreNeverReachResultsOrState`
- `exceptionMessagesFromAPoisonedStorageNeverReachResultsOrState`
- `exceptionMessagesFromAPoisonedCipherNeverReachResultsOrState`
- `exceptionMessagesFromAPoisonedStoreNeverReachTheControllerState`
- `typedTextThatIsRejectedIsNeverKeptInTheState`
- `theBytesHandedToTheCipherOnSaveAreClearedAfterwards`
- `theBytesHandedToTheCipherAreClearedEvenWhenEncryptionFails`
- `theBytesHandedToTheCipherAreClearedEvenWhenTheWriteFails`
- `theDecryptedBytesAreClearedAfterASuccessfulRead`
- `theDecryptedBytesAreClearedWhenTheContentIsNotAValidKey`
- `noLoggingOrPrintingInTheKeyHandlingCode`
- `theKeyHandlingSourcesWereFound`
- `theTypedKeyIsNeverPutIntoSavedInstanceState`
- `theKeyFieldIsMaskedByDefaultAndUsesAPasswordKeyboard`
- `theTypedKeyIsClearedWhenTheAppStops`
- `appBackupIsOffSoTheEncryptedFileIsNotCopiedToOtherDevices`
- `onlyTheSecureStoreReadsThePlainKey`
- `noKeyTextFieldExistsInTheSettingsStateOrResults`

## ApiKeyProviderFailureTest

`app/src/test/java/com/mnb/browser/security/ApiKeyProviderFailureTest.kt` — Read-only provider: failure mapping, no writes — 13 tests, written, not run

- `corruptedBytesAreUnavailableAsCorruptedData`
- `anUnreadableStoredTextIsUnavailableAsCorruptedData`
- `tamperedDataIsUnavailableAsDecryptionFailed`
- `aDifferentKeystoreKeyIsUnavailableAsDecryptionFailed`
- `anExplodingCipherIsUnavailableAndNothingEscapes`
- `failedLookupsNeverWriteOrDeleteAnything`
- `successfulLookupsNeverWriteEither`
- `repeatedLookupsReturnTheSameKey`
- `lookupFollowsSaveUpdateAndDelete`
- `aUnavailableLookupBecomesAvailableAgainAfterTheKeyIsSavedAgain`
- `theProviderInterfaceHasOnlyLookup`
- `theStoreBackedProviderHasNoWayToChangeTheKey`
- `everyLookupResultIsSafeToPrint`

## ApiKeyTest

`app/src/test/java/com/mnb/browser/security/ApiKeyTest.kt` — ApiKey value type: validation, redaction, equality — 18 tests, written, not run

- `aTypicalKeyIsAcceptedUnchanged`
- `theFirstAndLastAllowedCharactersAreAccepted`
- `everyPrintableAsciiCharacterWithoutSpaceIsAccepted`
- `surroundingWhitespaceIsTrimmedOff`
- `maxLengthIsAcceptedAndOneMoreIsRejected`
- `lengthIsCheckedAfterTrimming`
- `aSingleCharacterIsAccepted`
- `emptyAndWhitespaceOnlyTextIsRejected`
- `spacesAndLineBreaksInsideTheKeyAreRejected`
- `controlCharactersAreRejected`
- `nonAsciiTextIsRejected`
- `aBadCharacterAnywhereRejectsTheWholeKey`
- `equalTextGivesEqualKeysWithEqualHashCodes`
- `aKeyIsNotEqualToItsPlainText`
- `toStringNeverShowsTheKey`
- `theKeyStaysHiddenInStringTemplatesAndCollections`
- `revealIsTheOnlyWayToGetTheText`
- `parsedKeysEqualKeysBuiltFromTheTrimmedText`

## SecureApiKeyStoreFailureTest

`app/src/test/java/com/mnb/browser/security/SecureApiKeyStoreFailureTest.kt` — Store read failures, corruption, recovery — 23 tests, written, not run

- `anEmptyBlobIsCorrupted`
- `everyBlobShorterThanTheMinimumEnvelopeIsCorrupted`
- `aBlobOfRandomLookingBytesIsCorruptedOrFailsAuthentication`
- `aWrongVersionByteIsCorrupted`
- `aWrongIvLengthByteIsCorrupted`
- `changingAnyByteAfterTheHeaderIsADecryptionFailure`
- `aTruncatedEnvelopeThatStillParsesIsADecryptionFailure`
- `anEnvelopeWithExtraBytesIsADecryptionFailure`
- `anEnvelopeMadeForAnotherPurposeIsADecryptionFailure`
- `authenticContentThatIsNotAValidKeyIsCorrupted`
- `authenticContentThatIsNotValidUtf8IsCorrupted`
- `authenticContentThatIsAValidKeyAfterTrimmingStillReads`
- `aMissingKeystoreKeyIsKeyUnavailable`
- `aKeystoreThatThrowsWhenAskedForTheKeyIsKeyUnavailable`
- `aKeyOfTheWrongSizeIsADecryptionFailure`
- `aDifferentKeyOfTheRightSizeIsADecryptionFailure`
- `failedReadsNeverChangeTheStoredBytesOrTheKeystoreKey`
- `failedReadsDoNotLeakTheKeyOrTheStoredBytesIntoTheResultText`
- `theFailureCarriesOnlyAClassNameOfTheCause`
- `updateReplacesDataThatCannotBeDecrypted`
- `saveReplacesCorruptedBytes`
- `saveReplacesDataWhoseKeystoreKeyIsGone`
- `invalidInputIsRejectedBeforeAnyEncryptionHappens`

## SecureApiKeyStoreLifecycleTest

`app/src/test/java/com/mnb/browser/security/SecureApiKeyStoreLifecycleTest.kt` — Store lifecycle, storage failures, concurrency — 23 tests, written, not run

- `saveReadUpdateReadDeleteReadSequence`
- `theWholeCycleCanRunManyTimes`
- `readingDoesNotWriteAnything`
- `hasKeyNeedsNoKeystoreKey`
- `hasKeyIsTrueForAnythingStoredEvenIfItIsGarbage`
- `savingTheSameKeyTwiceStoresDifferentBytesButTheSameKey`
- `updateKeepsUsingTheSameKeystoreKey`
- `anInvalidUpdateLeavesTheStoredBytesUntouched`
- `anInvalidSaveLeavesTheStoredBytesUntouched`
- `updatingWhenNothingIsStoredDoesNotCreateAKeystoreKey`
- `aFailedFirstSaveLeavesNothingStored`
- `savingWorksAgainOnceTheStorageRecovers`
- `deleteTwiceIsFine`
- `aDeleteThatFailedCanBeRetriedAfterTheStorageRecovers`
- `deleteRemovesDataThatCanNoLongerBeDecrypted`
- `deleteRemovesGarbageToo`
- `theOldKeyCannotComeBackAfterDeleteAndANewSave`
- `aStorageThatThrowsOnExistsMakesHasKeyFalseAndUpdateAStorageFailure`
- `aStorageThatThrowsOnWriteIsAStorageFailure`
- `aStorageThatThrowsOnClearIsAStorageFailureAndTheKeystoreKeyIsKept`
- `aStorageThatThrowsOnReadIsAStorageFailure`
- `concurrentSavesAndReadsNeverFailAndNeverMixValues`
- `concurrentDeletesAndSavesEndInAConsistentState`

## GeminiKeySettingsErrorHandlingTest

`app/src/test/java/com/mnb/browser/settings/GeminiKeySettingsErrorHandlingTest.kt` — Controller error matrix, throwables, factory/executor failures — 10 tests, written, not run

- `everySaveFailureMapsToAFixedMessageAndNeverCountsAsAChange`
- `everyUpdateFailureMapsToAFixedMessageAndKeepsTheSavedStatus`
- `anUpdateThatFindsNothingFallsBackToSaveAndCountsOnlyIfThatWorks`
- `everyReadFailureMapsToNotSetOrUnreadableWithoutAMessage`
- `everyDeleteFailureGivesDeleteFailedAndTheStatusFromTheStorage`
- `aDeleteFailureWithNothingStoredStillSaysDeleteFailedButNotSet`
- `aThrowableFromAnyStoreFunctionEndsInAFixedStateWithoutKeyText`
- `anOutOfMemoryErrorIsRethrownButTheControllerIsNotLeftBusy`
- `aStoreFactoryThatFailsOnceIsRetriedOnTheNextOperation`
- `aRejectedExecutorLeavesTheDefaultStateAndNotBusy`

## GeminiKeySettingsStateTest

`app/src/test/java/com/mnb/browser/settings/GeminiKeySettingsStateTest.kt` — Controller UI state, transitions, executors — 15 tests, written, not run

- `defaultStateIsCheckingAndIdle`
- `copyAndEqualsFollowTheFields`
- `theStateHasNoFieldThatCouldHoldKeyText`
- `statusAndMessageEnumsHaveExactlyTheExpectedValues`
- `onlyTheThreeFailureMessagesAreErrors`
- `completedChangesCountsOnlySuccessfulChanges`
- `refreshKeepsThePreviousMessage`
- `aNewMessageReplacesTheOldOne`
- `clearMessageWithoutAMessageKeepsTheSameStateInstance`
- `statusMovesFromCheckingToNotSetToSavedAndBackToNotSet`
- `aLostKeystoreKeyMakesASavedKeyUnreadableAndSavingFixesIt`
- `savingWhileStillCheckingWithAStoredKeyReportsSavedNotUpdated`
- `aFailingRefreshKeepsSavedAndTheMessage`
- `whileASaveIsQueuedTheStateIsBusyAndOtherwiseUnchanged`
- `theStoreIsOnlyTouchedOnTheExecutorThread`
