# Scheduled Rules Design

Scheduled blocking is partially modeled in the v2 enforcement layer. It is not yet exposed in the Android UI.

## Current Model

The current model is named `SleepPolicy` because the first scheduled use case was a sleep lock. It can already represent general daily time windows:

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

To make scheduled blocking user-facing:

1. Rename or wrap `SleepPolicy` as a more general `SchedulePolicy` without breaking existing persisted rules.
2. Add a settings screen for daily time windows.
3. Decide how scheduled rules combine with per-app daily limits, session limits, cooldowns, and challenges.
4. Add UI and storage migration tests.
5. Add screenshots and documentation once the UI exists.
