# Contributing

Thanks for helping improve AddictionBuster.

## Good First Contributions

- Improve installation and troubleshooting docs.
- Report device compatibility issues.
- Add tested-device notes.
- Improve English and Chinese wording.
- Add focused unit tests for rule and usage accounting logic.

## Before Opening An Issue

Please include:

- App version or commit.
- Device brand and model.
- Android version.
- Whether Accessibility Service is enabled.
- Whether notification access is enabled.
- The controlled app package name, if relevant.
- Steps to reproduce.
- Relevant diagnostic log lines, with anything sensitive removed.

## Pull Request Workflow

1. Open or reference an issue when the change is more than a small typo.
2. Create a focused branch.
3. Keep changes small and reviewable.
4. Update docs when behavior changes.
5. Run tests when touching enforcement logic.

Useful commands:

```bash
./gradlew test
./gradlew assembleDebug
```

Android instrumentation tests require an emulator or device:

```bash
./gradlew connectedDebugAndroidTest
```

## Privacy And Safety

Be careful with any change involving Accessibility Service, notification access, overlays, local logs, app package names, or imported file URIs. Do not add network telemetry or analytics without updating `PRIVACY.md` and discussing the change first.
