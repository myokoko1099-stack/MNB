# Release checklist

Before any release build, tick every item.

## Automated
- [ ] GitHub Actions `build` is green on the exact commit to be released (unit tests + debug APK).
- [ ] No test was skipped, ignored or deleted since the last green run.

## Device (cannot be proven by unit tests)
- [ ] Save a Gemini key, queue a chapter in the background, force-stop the app (or swipe it away and
      let the system kill it), then wait: the chapter must still finish translating.
- [ ] Repeat with the device offline, then online again: work resumes and finishes.
- [ ] Repeat with no key saved: the chapter is not translated and no crash occurs.
- [ ] Delete the key while a job is queued: the job fails cleanly, no crash.
- [ ] Save, update, delete and show/hide the key; rotate the screen; background the app while typing:
      the typed text is hidden/cleared.
- [ ] Check `adb logcat` during a translation: no key text, no chapter text.
- [ ] Rate-limit / quota / wrong-key cases show the fixed messages (use a test key with low quota).

## Security
- [ ] No key, keystore, password or `local.properties` in the commit (`ProjectSecretsGuardTest` is green).
- [ ] Manifest: only INTERNET, `allowBackup=false`, cleartext off.
- [ ] Signing key and passwords are created and stored by the owner outside the repository.

## Build and packaging
- [ ] Decide `versionName` / `versionCode` (still `0.2.0-phase02` / 1).
- [ ] Decide on R8: either enable minification with rules and re-run all tests and a device pass,
      or accept an unminified release.
- [ ] Add a release build (and ideally lint) to CI, or run `assembleRelease` once by hand.
- [ ] Install the signed release APK on a clean device and run the device checks above.

## Documentation
- [ ] README, SECURITY, ARCHITECTURE, TESTING and RELEASE-NOTES still match the code.
- [ ] Known limitations in RELEASE-NOTES.md are still accurate.
