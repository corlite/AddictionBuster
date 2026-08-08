# Localization Status

AddictionBuster is moving from a Chinese-only Android UI toward bilingual Simplified Chinese and English support.

## Current Coverage

The following strings are now backed by Android resources with English translations in `app/src/main/res/values-en/strings.xml`:

- App name and Android service labels.
- Accessibility service description.
- Main dashboard title, subtitle, sections, status rows, actions, service status, counters, and duration labels.
- Settings screen title, subtitle, permission sections, action buttons, diagnostics entry, and enabled/disabled status labels.

The default resource file remains Simplified Chinese, so Chinese users keep the existing wording. English devices use the `values-en` translations for the covered screens.

## Still To Localize

These screens still contain hardcoded Chinese text and should be handled in later pull requests:

- Initial setup gate.
- Add app screen.
- Active apps screen.
- App rule screen.
- Challenge and overlay flows.
- Phone usage limit and whitelist screens.
- Today report screen.
- Diagnostic Center.
- Notification access guide.
- Mascot and voice configuration.

## Contributor Notes

When localizing another screen:

1. Move visible UI strings into `app/src/main/res/values/strings.xml`.
2. Add matching English strings to `app/src/main/res/values-en/strings.xml`.
3. Keep dynamic values formatted through string resources.
4. Run `./gradlew lint`, `./gradlew test`, and `./gradlew assembleDebug`.
5. If possible, capture before/after screenshots for the affected screen.
