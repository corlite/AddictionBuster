# Blind Hunter Review Prompt

Use the `bmad-review-adversarial-general` skill. Review only the change diff; do not read the specification, conversation, README, other project files, commit history, or test reports.

Construct the complete review input from these commands in `E:\Dev\projects\AddictionBuster`:

```powershell
git diff d91fb16ae1ea11d90658bad068e83f5e22f9a48d -- app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt 'docs/测试清单与已知限制.md'
git diff --no-index -- NUL app/src/test/kotlin/com/addictionbuster/enforcement/runtime/OrderedRuntimeEventProcessorTest.kt
git diff --no-index -- NUL _bmad-output/implementation-artifacts/spec-fix-v2-runtime-accounting-and-overlay-reentry.md
```

Treat exit code 1 from `git diff --no-index` as normal when a new file differs from `NUL`. After collecting the output, inspect no other source.

Find concrete correctness, concurrency, cancellation, deadlock, ordering, lifecycle, test-quality, and maintainability defects introduced by the diff. Do not report style preferences. Return findings only, each with severity, changed file and line, failure mechanism, and smallest safe correction. If no actionable finding exists, state `No actionable findings`.
