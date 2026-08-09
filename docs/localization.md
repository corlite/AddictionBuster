# Localization Status

AddictionBuster is moving from a Chinese-only Android UI toward bilingual Simplified Chinese and English support.

## Current Coverage

The following strings are now backed by Android resources with English translations in `app/src/main/res/values-en/strings.xml`:

- App name and Android service labels.
- Accessibility service description.
- Main dashboard title, subtitle, sections, status rows, actions, service status, counters, and duration labels.
- Settings screen title, subtitle, permission sections, action buttons, diagnostics entry, and enabled/disabled status labels.
- Initial setup gate.
- Add app, active apps, app rule, today report, phone usage limit, and whitelist screens.
- Scheduled limits screen.
- Notification access guide.
- Diagnostic Center UI and generated diagnostic report labels.
- Mascot and voice settings labels.
- Legacy challenge activity labels used when the v2 engine is disabled.

The default resource file remains Simplified Chinese, so Chinese users keep the existing wording. English devices use the `values-en` translations for the covered screens.

## Still To Localize

The main user-facing Android screens are now backed by Simplified Chinese and English resources. Remaining work is mostly future-facing:

- Any newly added screen should be localized before release.
- Diagnostic log category names and some internal event reason text remain developer-facing English.
- User-imported mascot names, app labels, package names, and diagnostic log lines are displayed as provided by the device.

## Contributor Notes

When localizing another screen:

1. Move visible UI strings into `app/src/main/res/values/strings.xml`.
2. Add matching English strings to `app/src/main/res/values-en/strings.xml`.
3. Keep dynamic values formatted through string resources.
4. Run `./gradlew lint`, `./gradlew test`, and `./gradlew assembleDebug`.
5. If possible, capture before/after screenshots for the affected screen.
