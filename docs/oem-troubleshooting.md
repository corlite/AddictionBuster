# OEM Troubleshooting

Android device makers can add battery, background-start, and permission controls that affect accessibility-based apps. This page collects practical checks for diagnosing those issues without overstating what a normal APK can control.

## General Checklist

1. Enable the AddictionBuster accessibility service in Android settings.
2. Open AddictionBuster once after enabling the service.
3. Make sure Android has not restricted AddictionBuster's battery usage.
4. Keep the foreground service notification enabled.
5. If background media blocking is needed, enable notification access.
6. Reboot the phone once, then confirm the accessibility service is still enabled.
7. Use the in-app Diagnostic Center after reproducing the problem.

## Battery Optimization

If interception works at first and later stops, check battery optimization:

- Set AddictionBuster battery usage to unrestricted, not optimized.
- Allow background activity if the OEM exposes a separate toggle.
- Do not hide or block the foreground service notification.
- Reopen AddictionBuster after system updates, because some OEM builds reset permissions.

AddictionBuster cannot programmatically disable these restrictions for the user. The app can only detect risk, show guidance, and fail closed where the Android API allows it.

## Xiaomi / HyperOS / MIUI

On Xiaomi, Redmi, and Poco devices, users may need extra manual steps:

1. Enable accessibility service.
2. Allow display over other apps if the ROM requires it.
3. Open App info for AddictionBuster.
4. Set battery saver to no restrictions or equivalent.
5. Enable autostart if the ROM exposes it.
6. Lock the app in the recent-apps screen if the ROM uses that as a keep-alive hint.
7. Reboot and verify the accessibility service stayed enabled.

Common symptoms:

| Symptom | Likely cause | What to capture |
| --- | --- | --- |
| Overlay never appears | Accessibility service disabled or not receiving window events | Diagnostic Center logs around `service`, `event`, and `challenge` |
| Works once, then stops later | Battery or background restrictions | Time since last app launch, screen-off duration, battery setting screenshot |
| Timeout does not return home | Accessibility home action rejected or delayed | Diagnostic logs around timeout and home action |
| Background audio keeps playing | Target app does not expose a controllable media session | Diagnostic logs containing `[media]` |

## What AddictionBuster Cannot Do

A regular Android APK cannot reliably:

- Force-stop another app.
- Freeze packages.
- Bypass OEM background restrictions.
- Keep accessibility permission enabled after the system or user disables it.
- Guarantee media pause for apps that do not expose media sessions.

Those limits are Android platform boundaries, not just missing app code.
