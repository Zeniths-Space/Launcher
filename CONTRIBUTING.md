# Contributing

1. Read [ARCHITECTURE.md](ARCHITECTURE.md) and [PRIVACY.md](PRIVACY.md).
2. Keep changes small and focused, with one topic per pull request.
3. Add or update unit tests for any behavior change in `Apps.kt`.
4. Run `./gradlew assembleDebug testDebugUnitTest lintDebug` before opening a PR.
5. Do not add network access, telemetry, background services, or polling.
6. Explain any new dependency in the PR: why it is needed, its license, and the alternatives.
7. Test on a real device, as a home app. Emulators miss many launcher and OEM behaviors.
