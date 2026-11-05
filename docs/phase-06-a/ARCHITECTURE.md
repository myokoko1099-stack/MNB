# Phase 06-A — Architecture of the Gemini API key storage

Status: written in Phase 06-A4 from the code merged in Phase 06-A1..A3 (repo `MNB`, HEAD `49dd364`).
No source file was changed by this phase.

## 1. Layers

```
GeminiApiKeySection (Compose)          MainActivity (wiring)
        │ state / actions                      │
GeminiKeySettingsController ──────────► ApiKeyStore (interface)
                                              ▲
                              StoreApiKeyProvider : ApiKeyProvider (read-only lookup)
                                              │
                                      SecureApiKeyStore
                                       │             │
                                  AeadCipher   EncryptedBlobStorage
                                       │             │
                       AesGcmCipher (KeyProvider)   SharedPreferencesEncryptedBlobStorage
                                       │
                            AndroidKeystoreKeyProvider
```

| Layer | Responsibility |
|---|---|
| `ApiKey` | Redacted value type. `parseOrNull` trims, accepts 1..512 printable ASCII characters without space (0x21..0x7E). `toString()` is `ApiKey(***)`. Only `reveal()` returns the text. |
| `KeyProvider` → `AndroidKeystoreKeyProvider` | Owns the Keystore key: alias `mnb.gemini_api_key.aes.v1`, AES-256, non-exportable, no user authentication required. |
| `AeadCipher` → `AesGcmCipher` | `AES/GCM/NoPadding`, 96-bit random IV, 128-bit tag, AAD `mnb:gemini_api_key:v1`. Envelope: `[version=1][ivLength=12][iv][ciphertext+tag]`. After an `InvalidKeyException` it replaces the Keystore key and retries once. |
| `EncryptedBlobStorage` → `SharedPreferencesEncryptedBlobStorage` | Stores the envelope. File `mnb_secure_storage`, entry `gemini_api_key.v1`, Base64 `NO_WRAP`, written with `commit()`. |
| `SecureApiKeyStore : ApiKeyStore` | `hasKey / save / update / read / delete`. `synchronized` per instance. Delete clears the storage first, then deletes the Keystore key and tolerates a failure of that second step. Plain-text byte buffers are zeroed in `finally`. Never throws: everything comes back as `SecureResult`. |
| `SecureResult` / `SecureStorageError` | `Success(value)` or `Failure(error, causeType)`. Errors: `INVALID_INPUT, NOT_FOUND, KEY_UNAVAILABLE, ENCRYPTION_FAILED, DECRYPTION_FAILED, CORRUPTED_DATA, STORAGE_FAILURE`. `causeType` is a class name only, never a message. |
| `ApiKeyProvider` / `StoreApiKeyProvider` | Read-only view for future consumers: `lookup()` returns `Available(key)`, `Missing` or `Unavailable(code)`. It has no way to write or delete. |
| `GeminiKeySettingsController` | UI-facing controller (A2) with A3 hardening: one executor, `busy` gating (calls while busy are ignored), one state lock, delete re-checks the storage before reporting success, every `Throwable` on the executor is contained except `VirtualMachineError`. State holds codes only. |
| `GeminiApiKeySection` | Compose UI: plain `remember` (never `rememberSaveable`), masked field, hidden on `ON_PAUSE`, input cleared on `ON_STOP`, delete confirmation dialog. |
| `MainActivity` | Creates the controller with a single-thread executor named `mnb-gemini-key-settings` and exposes it through `LocalGeminiKeySettings`. Nothing reads the key for requests yet. |

## 2. Data flow

**Save / update** (UI thread → executor thread):
1. The section calls `controller.save(text)`; the controller sets `busy = true` under the state lock and posts to the executor.
2. On the executor: if the status is `SAVED` it calls `store.update(text)` (on `NOT_FOUND` it falls back to `store.save(text)`), otherwise `store.save(text)`.
3. The store parses the text with `ApiKey.parseOrNull`, encrypts the UTF-8 bytes with `AesGcmCipher`, writes the envelope, zeroes the plain bytes.
4. The controller re-reads the status (`hasKey`, then one `read`), then publishes status + message + `completedChanges` in one state update and clears `busy`.

**Refresh**: `hasKey()`; if true one `read()` to confirm it can be decrypted. `NOT_FOUND` → `NOT_SET`, any other failure → `UNREADABLE`, success → `SAVED`. The decrypted key is dropped immediately.

**Delete**: `store.delete()`, then the status is checked again. `DELETED` is reported only if the delete succeeded **and** the storage is empty.

**Threading**: the store (and its lazy factory) is only touched on the controller's executor. The UI thread only reads `state` and calls the four controller functions. All writes to `state` go through `update { }` under `stateLock`; only cheap work happens inside the lock.

## 3. Error → state mapping

| Operation | Outcome | `status` | `message` | `completedChanges` |
|---|---|---|---|---|
| refresh | `hasKey=false` | `NOT_SET` | unchanged | unchanged |
| refresh | read ok | `SAVED` | unchanged | unchanged |
| refresh | read `NOT_FOUND` | `NOT_SET` | unchanged | unchanged |
| refresh | read any other error | `UNREADABLE` | unchanged | unchanged |
| refresh | unexpected throwable | `UNREADABLE` only if it was `CHECKING`, otherwise unchanged | unchanged | unchanged |
| save (status ≠ `SAVED`) | success | re-checked | `SAVED` | +1 |
| save (status = `SAVED`) | update success | re-checked | `UPDATED` | +1 |
| save / update | `INVALID_INPUT` | re-checked | `INVALID_INPUT` | unchanged |
| save / update | any other error | re-checked | `SAVE_FAILED` | unchanged |
| update | `NOT_FOUND` | falls back to `save`, then as above | | |
| save / update | unexpected throwable | as for refresh | `SAVE_FAILED` | unchanged |
| delete | success and storage empty | `NOT_SET` | `DELETED` | +1 |
| delete | failure, or storage still not empty | re-checked | `DELETE_FAILED` | unchanged |
| delete | unexpected throwable | as for refresh | `DELETE_FAILED` | unchanged |
| any | `OutOfMemoryError` / `VirtualMachineError` | `busy` reset, then rethrown | | |
| any | executor rejects the task | unchanged, `busy = false` | | |

`GeminiKeyMessage.isError` is true only for `INVALID_INPUT`, `SAVE_FAILED`, `DELETE_FAILED`.
No message, cause or stack trace from a failure is ever kept in the state.
