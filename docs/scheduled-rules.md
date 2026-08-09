# Scheduled Rules Design

Scheduled blocking is modeled in the v2 enforcement layer and is exposed in the Android UI as a single editable weekly time window.

## Current Model

The persisted model is still named `SleepPolicy` because the first scheduled use case was a sleep lock. The UI presents it as scheduled limits so the same model can cover bedtime, work, and study windows:

```kotlin
data class SleepPolicy(
    val enabled: Boolean,
    val windows: List<SleepWindow>
)

data class SleepWindow(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val activeDays: Set<Int>
)
```

Rules:

- `startMinuteOfDay` and `endMinuteOfDay` are minutes after midnight.
- Valid minute values are `0..1439`.
- `activeDays` uses ISO day numbers: Monday is `1`, Sunday is `7`.
- The start minute is inclusive.
- The end minute is exclusive.
- A window where `startMinuteOfDay > endMinuteOfDay` crosses midnight.
- A disabled policy is never active.

## Android UI

The main screen links to **Scheduled limits** from the Usage and Reports section. The screen supports:

- Enabling or disabling scheduled limits.
- Editing one `HH:mm` start time.
- Editing one `HH:mm` end time.
- Selecting active days from Monday through Sunday.
- Cross-midnight windows, such as `22:30` to `07:00`.

When saved, the screen writes one `SleepWindow` into `RuleSnapshot.sleepPolicy`. Disabling scheduled limits writes a disabled `SleepPolicy` with no windows.

## Runtime Behavior

`SleepScheduleEvaluator` evaluates the current time against the configured windows. The v2 accessibility runtime passes the result into the enforcement engine as `usage.sleepLockActive`.

When both of these are true:

- `rules.sleepPolicy.enabled`
- `usage.sleepLockActive`

the enforcement engine returns a block decision for non-emergency usage.

## Storage

`LocalRuleRepository` already serializes and deserializes `sleepPolicy` inside the v2 `RuleSnapshot` JSON.

## Tests

The current tests cover:

- Same-day windows.
- Cross-midnight windows.
- Disabled policies.
- End-exclusive boundaries.
- Preventing cross-day windows from leaking into unselected days.

## Remaining Work

1. Rename or wrap `SleepPolicy` as a more general `SchedulePolicy` without breaking existing persisted rules.
2. Support multiple named windows instead of a single editable window.
3. Decide how scheduled rules should combine with future cooldowns and page-specific rules.
4. Add UI and storage migration tests.
5. Add screenshots once the scheduled limits screen settles.
