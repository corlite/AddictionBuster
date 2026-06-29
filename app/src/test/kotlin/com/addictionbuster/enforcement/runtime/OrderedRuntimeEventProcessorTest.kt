package com.addictionbuster.enforcement.runtime

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.addictionbuster.enforcement.EnforcementAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderedRuntimeEventProcessorTest {
    @Test
    fun pendingQuitGuardSuppressesOnlyMatchingAppChallenge() {
        assertTrue(
            shouldSuppressQuitPendingChallenge(
                pendingIdentityKey = "blocked-app",
                targetIdentityKey = "blocked-app",
                action = EnforcementAction.SHOW_APP_CHALLENGE
            )
        )
        assertFalse(
            shouldSuppressQuitPendingChallenge(
                pendingIdentityKey = "blocked-app",
                targetIdentityKey = "launcher",
                action = EnforcementAction.SHOW_APP_CHALLENGE
            )
        )
        assertFalse(
            shouldSuppressQuitPendingChallenge(
                pendingIdentityKey = "blocked-app",
                targetIdentityKey = "blocked-app",
                action = EnforcementAction.SHOW_PHONE_LIMIT_BLOCK
            )
        )
    }

    @Test
    fun processesEventsInSubmissionOrderWhileFirstTransactionIsBlocked() = runBlocking {
        val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val processed = mutableListOf<String>()
        val processor = OrderedRuntimeEventProcessor<String>(processorScope, handler = { event ->
            if (event == "app") {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
            processed += event
            if (processed.size == 3) completed.complete(Unit)
        })

        processor.submit("app")
        firstEntered.await()
        processor.submit("launcher")
        processor.submit("tick")
        releaseFirst.complete(Unit)
        withTimeout(1_000L) { completed.await() }

        assertEquals(listOf("app", "launcher", "tick"), processed)
        processor.close()
        processorScope.cancel()
    }

    @Test
    fun launcherTransitionPreventsQueuedTickFromCountingOrRestoringOldApp() = runBlocking {
        val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val blockerEntered = CompletableDeferred<Unit>()
        val releaseBlocker = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        var foreground = "blocked-app"
        var sliceStartedAtMillis = 0L
        var countedMillis = 0L
        var tickObservedForeground = ""
        val processor = OrderedRuntimeEventProcessor<ProbeEvent>(processorScope, handler = { event ->
            if (event.kind == ProbeKind.BLOCKER) {
                blockerEntered.complete(Unit)
                releaseBlocker.await()
            } else {
                val elapsed = event.receivedAtMillis - sliceStartedAtMillis
                if (foreground != "launcher") countedMillis += elapsed
                sliceStartedAtMillis = event.receivedAtMillis
                when (event.kind) {
                    ProbeKind.LAUNCHER -> foreground = "launcher"
                    ProbeKind.TICK -> tickObservedForeground = foreground
                    ProbeKind.BLOCKER -> Unit
                }
                if (event.kind == ProbeKind.TICK) completed.complete(Unit)
            }
        })

        processor.submit(ProbeEvent(ProbeKind.BLOCKER, 0L))
        blockerEntered.await()
        processor.submit(ProbeEvent(ProbeKind.LAUNCHER, 1_000L))
        processor.submit(ProbeEvent(ProbeKind.TICK, 1_500L))
        releaseBlocker.complete(Unit)
        withTimeout(1_000L) { completed.await() }

        assertEquals(1_000L, countedMillis)
        assertEquals("launcher", tickObservedForeground)
        processor.close()
        processorScope.cancel()
    }

    @Test
    fun failedOrCancelledEventDoesNotStopLaterEvents() = runBlocking {
        val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failures = mutableListOf<String>()
        val completed = CompletableDeferred<Unit>()
        val processor = OrderedRuntimeEventProcessor<String>(
            scope = processorScope,
            handler = { event ->
                when (event) {
                    "failed" -> error("expected")
                    "cancelled" -> throw CancellationException("expected")
                    "continued" -> completed.complete(Unit)
                }
            },
            failureHandler = { event, _ -> failures += event }
        )

        processor.submit("failed")
        processor.submit("cancelled")
        processor.submit("continued")
        withTimeout(1_000L) { completed.await() }

        assertEquals(listOf("failed", "cancelled"), failures)
        processor.close()
        processorScope.cancel()
    }

    private data class ProbeEvent(val kind: ProbeKind, val receivedAtMillis: Long)

    private enum class ProbeKind {
        BLOCKER,
        LAUNCHER,
        TICK
    }
}
