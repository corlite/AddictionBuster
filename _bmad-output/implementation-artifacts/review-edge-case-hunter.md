# Edge Case Hunter Review Prompt

Use the `bmad-review-edge-case-hunter` skill. Start with the complete diff below, then use read-only project access to trace every affected branch and boundary condition.

```powershell
git diff d91fb16ae1ea11d90658bad068e83f5e22f9a48d -- app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt 'docs/测试清单与已知限制.md'
git diff --no-index -- NUL app/src/test/kotlin/com/addictionbuster/enforcement/runtime/OrderedRuntimeEventProcessorTest.kt
git diff --no-index -- NUL _bmad-output/implementation-artifacts/spec-fix-v2-runtime-accounting-and-overlay-reentry.md
```

Treat exit code 1 from `git diff --no-index` as expected for new files. Trace event/tick/screen ordering, timestamp capture, coroutine cancellation, exception paths, service recreation, overlay callbacks, queue waits, screen transitions, and repeated accessibility events. Report only unhandled edge cases caused or exposed by this change. Each finding must include severity, path:line, exact triggering sequence, observed failure, and correction. If none exist, state `No actionable findings`.
