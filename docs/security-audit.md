# Security Audit Notes

This document summarizes the current Android security surface so reviewers and contributors can quickly see which sensitive capabilities exist and why.

## Permissions

| Permission | Required | Reason |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Optional on Android 13+ | Shows service and permission health notifications. |
| `SYSTEM_ALERT_WINDOW` | Conditional | Supports overlay flows where the device requires normal overlay permission. Accessibility overlay paths use the accessibility service context. |
| `FOREGROUND_SERVICE` | Yes | Keeps enforcement visible while the app monitors usage state. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Yes | Declares the foreground service type used by the enforcement service. |

## Exported Components

| Component | Exported | Protection | Notes |
| --- | --- | --- | --- |
| `MainActivity` | `true` | Launcher intent only | Public entry point for opening the app. |
| `BusterAccessibilityService` | `true` | `android.permission.BIND_ACCESSIBILITY_SERVICE` | Required by Android so the system can bind the accessibility service. Third-party apps cannot bind without the platform permission. |
| `BusterNotificationListenerService` | `true` | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | Required by Android so the system can bind the notification listener service. Third-party apps cannot bind without the platform permission. |
| `V2EnforcementForegroundService` | `false` | App-internal | Enforcement foreground service. |
| `ChallengeActivity` | `false` | App-internal | Full-screen intervention flow. |
| Setup, settings, diagnostics, app selection, stats, and mascot activities | `false` | App-internal | Not directly launchable by other apps. |

## Local Data

AddictionBuster is local-first:

- Rules are stored on the device.
- Usage state is stored on the device.
- Diagnostic logs are stored on the device.
- Mascot media slots store local URI references.
- Mascot settings display imported/not-imported status instead of full `content://` URI strings.
- There is no account system, analytics backend, or cloud sync in the current release.

See [PRIVACY.md](../PRIVACY.md) for the user-facing privacy policy.

## Release Signing

Release signing secrets are intentionally not committed. `app/build.gradle.kts` reads release signing values from local-only `local.properties` entries or environment variables:

- `ADDICTIONBUSTER_RELEASE_STORE_FILE`
- `ADDICTIONBUSTER_RELEASE_STORE_PASSWORD`
- `ADDICTIONBUSTER_RELEASE_KEY_ALIAS`
- `ADDICTIONBUSTER_RELEASE_KEY_PASSWORD`

See [release-checklist.md](release-checklist.md).

## Open Review Items

- Review accessibility event handling for accidental collection of unnecessary page text.
- Keep exported component changes under review whenever new Android components are added.
- Expand instrumentation tests around setup, overlay display, timeout-to-home, and notification listener behavior.
- Add a documented process for responsible disclosure responses once external users start reporting issues.
