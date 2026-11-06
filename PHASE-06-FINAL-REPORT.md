# Phase 06 final report (06-B4)

Base: `main` at `4d11cd2df3c2e4e07527911a45b840a2f4cd9940` (confirmed before starting).
Nothing was committed or pushed. No test or build was run locally.

## 1. Phase 06 completion summary

06-A1 to 06-B3 are merged (B3 = PR #26, its CI run was green). 06-B4 adds one functional fix, two
guard-test classes, a `.gitignore` hardening and the documentation set. See RELEASE-NOTES.md.

## 2. Architecture review

Confirmed by reading the code: User -> app -> securely stored user key -> Google Gemini -> result.
BYOK, direct connection, Keystore + AES-GCM, background execution, error handling, timeouts and
rate-limit protection are all present. NB-Translation-Gateway is not used for translation requests
(`ReaderTranslationGateway` is the reader's own port; the app binds the "Unavailable" one).

**Finding F-1 (functional, high, FIXED in B4).** The worker's provider and reader-id were set only in
`MainActivity.onCreate`. When WorkManager starts the process with no Activity (app killed, retry due),
the worker saw the mock default, `TranslationWorkerGuard` refused, and the job was marked FAILED.
Fix: new `MnbApplication` installs both at every process start; the key store moved there so the
Activity, the reader and the worker share one instance; the worker's "key saved" check reads the
secure storage directly. Architecture, Keystore, AES-GCM and the direct Gemini path are unchanged.
Evidence level: code reading plus `MnbApplicationTest`; **not proven on a device**.

## 3. Security audit

Files read: secure store, key provider, Keystore key provider, SharedPreferences storage, key settings
controller and UI section, `DirectGeminiProvider`, HTTP client, retry, config, response parser, prompt
builder, result codes, translation adapter and wiring, worker, worker guard, manifest, Gradle files,
`.gitignore`, CI workflow.

| Check | Result |
| --- | --- |
| Key hardcoded / in Git | None found in files read; `ProjectSecretsGuardTest` now scans the whole project |
| Key logged | No logging in key, settings, Gemini sources (guard tests) |
| Key in errors / exceptions | Fixed messages only; exception text never copied |
| Key in URL | Header `x-goog-api-key` only |
| Shared backend / other users | No; direct to Google, per-device storage |
| Plaintext storage | None; only the encrypted envelope is written |
| Backup / cleartext | `allowBackup=false`, cleartext off |
| Permissions | INTERNET only |
| **F-2 (low, FIXED)** | `.gitignore` lacked keystore/credential patterns; added |
| F-3 (info) | Key is a Java `String` while a request is built; cannot be wiped (SECURITY.md) |
| F-4 (info) | Chapter text goes to Google under the user's key (documented) |

## 4. Test coverage summary

See FINAL-TEST-REPORT.md: 141 tests counted (116 existing read, 25 new), all unverified for B4 until
CI runs. Remaining test classes were not counted. Coverage of secure storage, key settings, direct
provider, HTTP errors, timeout, rate limit, retry, redaction, mock provider and the worker is present.

## 5. Build configuration review

- minSdk 24, target/compile 34, Java 17, AGP 8.5.2, Kotlin 1.9.24. No signing config in the repository (correct).
- **F-5 (medium, NOT changed):** release has `isMinifyEnabled=false` and an empty `proguard-rules.pro`.
  Turning R8 on without a build and device pass would be a guess.
- **F-6 (low, NOT changed):** `versionName` is `0.2.0-phase02`, `versionCode` 1. Owner's decision.
- **F-7 (low, NOT changed):** CI runs `testDebugUnitTest` and `assembleDebug` only; no lint, no release build.
- Not checked: dependency versions for known vulnerabilities (no network tools used).

## 6. Documentation summary

README, SECURITY, ARCHITECTURE, TESTING, RELEASE-NOTES written from the code read. Features that do
not exist (model selector, cloud sync, StrongBox requirement) are not claimed. Minor: the
`PHASE-06-A-TEST-INVENTORY.md` still lists `onlyTheSecureStoreReadsThePlainKey`; the test is now named
`onlyTheSecureStoreAndTheGeminiProviderReadThePlainKey` (not edited, historical file).

## 7. Known limitations

F-1 device verification, F-3 to F-7 above, fixed default model, and the audit coverage limit below.

**Audit coverage:** not every file in the repository was read. Not read: browser/web, extraction,
navigation, most of the reader package, the database layer, chunking, memory, quality, `AesGcmCipher`,
`ApiKey`, `GeminiHttp`, and about half of the test classes. For the security-critical unread files the
repository's scan tests were relied on, and those have not been run for B4.

## 8. Release readiness assessment

**Not release-ready yet; ready for internal debug testing once CI is green.**

Evidence for: security model and error handling match the requirements in the files read; B3 CI was
green; F-1 is fixed in code with regression tests.
Blocking before a production release: (1) a green GitHub Actions run on the 06-B4 branch;
(2) the device check for F-1 (RELEASE-CHECKLIST.md); (3) owner-created signing and a decided
`versionName`; (4) a decision on R8. None of these can be completed by this change.
