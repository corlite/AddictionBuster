# Acceptance Auditor Review Prompt

Audit the implementation against the approved specification and project constraints. Do not use conversation context.

Read:

- `_bmad-output/implementation-artifacts/spec-fix-v2-runtime-accounting-and-overlay-reentry.md`
- `README.md`
- `docs/测试清单与已知限制.md`

Construct the implementation diff with:

```powershell
git diff d91fb16ae1ea11d90658bad068e83f5e22f9a48d -- app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt 'docs/测试清单与已知限制.md'
git diff --no-index -- NUL app/src/test/kotlin/com/addictionbuster/enforcement/runtime/OrderedRuntimeEventProcessorTest.kt
git diff --no-index -- NUL _bmad-output/implementation-artifacts/spec-fix-v2-runtime-accounting-and-overlay-reentry.md
```

Treat exit code 1 from `git diff --no-index` as expected for new files. Verify every frozen constraint, matrix row, task, and acceptance criterion. Check that evidence actually proves the behavior rather than only testing a helper. Return findings only, classified as `intent_gap`, `bad_spec`, or `patch`, with severity, path:line, violated requirement, evidence, and required correction. If all requirements are satisfied, state `No actionable findings`.
