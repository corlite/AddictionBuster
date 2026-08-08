# Privacy Policy

AddictionBuster is designed as a local-first Android app. It does not require an account, does not include analytics SDKs, and does not send usage data to a server in the current implementation.

## Data Stored On Device

The app stores local settings needed for enforcement:

- Selected controlled apps and package names.
- Per-app rules, daily budgets, session limits, waiting times, and challenge settings.
- Phone-wide usage limit settings and whitelist selections.
- Temporary pass-through state after a challenge succeeds.
- Local usage counters and enforcement events.
- Mascot profile settings and user-imported media URIs.
- Diagnostic logs used for troubleshooting.

This data is stored in app-private storage such as SharedPreferences and files under the app data directory.

## Accessibility Service

The accessibility service is used to detect foreground window changes, show intervention overlays, and perform the home action when a time limit expires.

The current accessibility service configuration does not request full window-content retrieval (`canRetrieveWindowContent=false`). The app primarily relies on package/window events rather than reading screen content.

## Notification Access

Notification access is optional. It is used for the background media blocking feature.

Android grants notification listener services broad access, but AddictionBuster's current implementation uses active media sessions to inspect media playback state and package names, then attempts to pause media from controlled apps when they are not in an allowed session.

## Diagnostic Logs

Diagnostic logs are stored locally in:

```text
files/diagnostic.log
```

They can include timestamps, package names, service state, challenge state, media-session events, rule decisions, and usage-limit events. Logs are intended for troubleshooting when the user chooses to inspect, copy, or share them.

The app automatically prunes diagnostic logs older than about one hour and trims the file when it grows beyond its size limit.

## Network And Accounts

The current implementation does not include:

- Account registration or login.
- Cloud sync.
- Remote analytics.
- Advertising SDKs.
- Server-side telemetry.

## User-Imported Media

Mascot icons and voice clips are selected by the user through Android's document picker. The app stores persistent URI references so Android can load the selected files later. If the original file is moved or permission is revoked, the user may need to import it again.

## Deleting Data

Users can clear diagnostic logs from the Diagnostic Center. Android app settings can also be used to clear all app data, which removes local rules, counters, imported media references, and logs.

## Changes

This policy should be updated whenever the app adds networking, analytics, crash reporting, cloud sync, or new categories of stored data.
