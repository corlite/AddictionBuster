# Real-Device Testing Guide

This guide is for people testing AddictionBuster on their own Android device.

## Goal

We need real-device feedback because AddictionBuster relies on Android accessibility events, overlays, foreground services, and optional notification listener access. OEM builds can treat these capabilities differently.

High-priority devices:

- Xiaomi / Redmi / Poco with MIUI or HyperOS.
- OPPO / OnePlus / Realme with ColorOS or OxygenOS.
- Vivo / iQOO with OriginOS or Funtouch OS.
- Samsung One UI.
- Pixel or AOSP-like builds as a baseline.

## Install

1. Download the APK from the latest GitHub Release:
   <https://github.com/corlite/AddictionBuster/releases/latest>
2. Install the APK.
3. Open AddictionBuster.
4. Follow the setup screen.

## Basic Test

1. Enable the AddictionBuster accessibility service.
2. Optional: enable notification access if you want to test background media blocking.
3. Add one distracting app from the Add app screen.
4. Configure a simple rule:
   - Daily budget: 5 minutes.
   - Session limit: 1 minute.
   - Wait time: 5 seconds.
   - Required taps: 1.
   - Hidden count: 0.
   - Confirm text: leave empty for the first test.
5. Open the controlled app.
6. Confirm that the intervention appears.
7. Complete the wait/challenge.
8. Allow temporary access.
9. Stay in the app until the session expires.
10. Confirm that AddictionBuster returns to home.

## Phone-Wide Usage Test

1. Open Phone usage limit.
2. Set a very small daily phone limit or per-unlock limit.
3. Add essential apps to the whitelist if needed.
4. Open a non-whitelisted app.
5. Confirm that the phone-wide limit triggers when the limit is reached.

## Scheduled Limits Test

1. Open Scheduled limits from the main screen.
2. Enable scheduled limits.
3. Set a window that includes the next few minutes.
4. Select today's weekday and save.
5. Open a controlled app.
6. Confirm that the app is blocked during the active window.
7. Return to Scheduled limits, disable the schedule, and save.
8. Confirm that the controlled app goes back to its normal rule flow.

## Background Media Test

1. Enable notification access for AddictionBuster.
2. Add a media app as a controlled app.
3. Start playback in that app.
4. Leave the app without allowing access.
5. Check whether AddictionBuster pauses playback.

This feature is best-effort. Some apps do not expose controllable Android media sessions.

## After Reboot Or Screen-Off

1. Turn the screen off for at least 5 minutes.
2. Unlock the device and open the controlled app again.
3. Reboot the phone and repeat the same test.
4. Note whether the accessibility service stayed enabled.

## Report Results

Open a device compatibility issue:

<https://github.com/corlite/AddictionBuster/issues/new?template=device_compatibility.md>

Include:

- Device brand and model.
- Android version.
- OEM skin and version.
- AddictionBuster version.
- Whether each test passed.
- Relevant diagnostic log lines.

## Privacy

Diagnostic logs may include app names and package names. They are useful for debugging. Before posting, remove anything private such as messages, account names, notification contents, or full file paths.
