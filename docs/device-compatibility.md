# Device Compatibility

AddictionBuster depends on Android accessibility events, overlays, foreground services, and optional notification listener access. These Android capabilities can behave differently across OEM builds, so device feedback is part of the release process.

## Supported Android Range

- Minimum supported Android version: Android 8.0, API 26.
- Target Android version: Android 15, API 35.
- Build SDK: Android SDK 35.

## Current Test Coverage

Automated checks:

- JVM unit tests through `./gradlew test`.
- Android lint through `./gradlew lint`.
- Debug APK build through `./gradlew assembleDebug`.
- CI runs the same checks on every push to `main` and every pull request.

Development-device coverage recorded in the project notes:

| Environment | Coverage | Status |
| --- | --- | --- |
| Android emulator, API 36 | Storage instrumentation tests, setup gate, foreground service startup, basic launch smoke tests | Passed during development |

## Needs Community Testing

The following areas still need real-device reports before the project can claim broad compatibility:

| OEM / Android Build | Priority | Why it matters |
| --- | --- | --- |
| Xiaomi / Redmi / Poco, MIUI or HyperOS | High | Aggressive background restrictions can delay or stop accessibility services. |
| OPPO / OnePlus / Realme, ColorOS or OxygenOS | High | Background startup and battery restrictions vary by model. |
| Vivo / iQOO, OriginOS or Funtouch OS | High | Accessibility services may need additional manual permission steps. |
| Samsung One UI | Medium | Generally stable, but battery optimization settings still matter. |
| Pixel / AOSP-like builds | Medium | Useful baseline for debugging OEM-specific behavior. |

See [Real-Device Testing Guide](testing-guide.md) for the exact testing flow.

## Feedback Template

When reporting device compatibility, include:

- Device brand and model.
- Android version.
- OEM skin version, if visible.
- AddictionBuster version.
- Whether the accessibility service can be enabled.
- Whether the overlay appears when opening a controlled app.
- Whether waiting, challenge, allow, and timeout-to-home work.
- Whether background media blocking works, if notification access was enabled.
- Whether the service stops after screen-off, reboot, or several hours.
- Relevant diagnostic log lines from the in-app Diagnostic Center.

Use the [device compatibility issue template](../.github/ISSUE_TEMPLATE/device_compatibility.md).
