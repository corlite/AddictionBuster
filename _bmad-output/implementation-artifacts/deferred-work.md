# Deferred Work

## v2 runtime durability hardening

- **Status:** Deferred (pre-existing)
- **Source:** Independent review of `spec-fix-v2-runtime-accounting-and-overlay-reentry.md`
- **Issue:** `SingleThreadEnforcementQueue.submit()` commits usage before the runtime executes the overlay, records the decision, persists the checkpoint, and updates `lastContext`. A process death or exception between those steps can leave a partially committed slice that may be replayed after restart.
- **Recommended follow-up:** Introduce an idempotent slice/transaction identifier and atomically checkpoint usage with runtime state, then add fault-injection tests at every post-commit boundary.

## v2 runtime overload policy

- **Status:** Deferred (pre-existing)
- **Source:** Independent edge-case review
- **Issue:** Accessibility events and ticks currently use unbounded queues. A stalled storage or overlay operation can accumulate a large backlog and delay current foreground decisions.
- **Recommended follow-up:** Define a bounded/coalescing policy for redundant ticks and same-window events while preserving ordered foreground and screen transitions.

## v2 runtime lifecycle shutdown

- **Status:** Deferred (pre-existing)
- **Source:** Independent lifecycle review
- **Issue:** Accessibility-service replacement cannot synchronously join an in-flight transaction on the main thread because overlay execution may itself be waiting for the main looper. The old holder is invalidated and prevented from accepting new work, but a transaction already past its active check can still finish.
- **Recommended follow-up:** Give runtime generations idempotent commit tokens and check generation before each external side effect; then provide an asynchronous stop-and-join lifecycle that cannot deadlock the main looper.

## overlay main-thread execution timeout

- **Status:** Deferred (pre-existing)
- **Source:** Independent edge-case review
- **Issue:** `SimpleOverlayController.runOnMainSynchronously()` waits without a timeout. A stalled main looper can therefore stall the runtime event consumer indefinitely.
- **Recommended follow-up:** Add a cancel-safe bounded wait and fail-closed result, with tests for a rejected post, timeout before execution, and a task racing the timeout.

## Deferred from: code review of spec-count-successful-interceptions (2026-06-29)

- **Runtime stop may discard an accepted Quit:** The pre-existing shutdown path sets `active=false` before the ordered processor drains buffered work. A Quit accepted immediately before accessibility runtime shutdown can therefore be discarded while shutdown removes the overlay. Follow up by serializing stop as a FIFO input and draining accepted actions before deactivation.
- **Home/result persistence is not crash-atomic:** A process death after Android accepts the Home action but before `INTERCEPTION_SUCCEEDED` is durably appended can undercount one successful exit. Strict resolution requires a small persistent `PendingQuit` transaction, idempotent recovery, and fault-injection tests across the Home/write boundary.
