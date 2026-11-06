# Security

## BYOK security model

Each user brings a personal Gemini API key. The app stores it on that device only and sends it only
to Google's Gemini API. It is never sent to an MNB server or to NB-Translation-Gateway, never shared
between users, and never part of the source code or the repository.

## Secure storage

- AES-256-GCM encryption. The AES key lives in the Android Keystore (alias `mnb.gemini_api_key.aes.v1`)
  and is non-exportable; no user authentication is required, so the background worker can use it.
- Only the encrypted envelope (Base64) is written, to a private SharedPreferences file.
  Associated data `mnb:gemini_api_key:v1` binds the envelope to this purpose.
- `allowBackup="false"`: the encrypted file is not copied to other devices, where the Keystore key
  would not exist.
- Keys are validated before saving (non-empty, bounded length, ASCII, no spaces).
- If the Keystore key is lost (for example after a reset), the stored key becomes "unreadable". The
  user is asked to enter it again. Nothing is deleted automatically.

## API key protection

- The key text is never part of any UI or controller state, is not saved in instance state, and is
  hidden when the screen pauses and cleared when the app stops.
- Errors carry fixed codes and fixed messages only. No exception text, response body or key text is
  copied into a result, state or log.
- No logging calls exist in the key-handling or Gemini sources (enforced by tests).
- Only `DirectGeminiProvider` reads the plain key, at the moment a request is sent.
- The Phase 06-B4 `ProjectSecretsGuardTest` scans all main sources and Gradle files for Google API key
  patterns, private key blocks and signing passwords, and checks `.gitignore` and the manifest.

## Network security

- HTTPS only (cleartext traffic is off in the manifest and refused in code). Redirects are not followed.
- The key goes only in the `x-goog-api-key` header, never in a URL.
- Connect timeout 15 s, read timeout 90 s, response size limit 2 MiB.
- Retries are bounded: up to 3 attempts, exponential backoff 1 s to 16 s with jitter, `Retry-After`
  honoured up to a cap. Quota exhaustion and key errors are not retried.
- The only permission is INTERNET.
- The system prompt tells the model to treat chapter text as content only and not to follow
  instructions inside it. This reduces, but cannot remove, prompt-injection risk.

## Known limitations

- Chapter text leaves the device and is processed by Google under the user's key and Google's terms.
- While a request is being built, the key exists as a Java `String` in memory. It cannot be wiped.
  A rooted device or a debugger could read process memory.
- Keystore hardware backing depends on the device; the app does not require StrongBox.
- A user with a rooted device can read the private SharedPreferences file, but only gets ciphertext.
- Release builds are not minified or obfuscated (R8 is off, `proguard-rules.pro` is empty).
- Background translation after Android restarts the process is verified by code and unit tests only;
  it has not been verified on a device (see RELEASE-CHECKLIST.md).
