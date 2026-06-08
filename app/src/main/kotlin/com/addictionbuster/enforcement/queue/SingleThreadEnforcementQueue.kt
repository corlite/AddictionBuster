package com.addictionbuster.enforcement.queue

import com.addictionbuster.enforcement.EnforcementContext
import com.addictionbuster.enforcement.EnforcementProcessResult
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.UnifiedEnforcementProcessor
import com.addictionbuster.enforcement.storage.UsageCommitWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SingleThreadEnforcementQueue(
    private val processor: UnifiedEnforcementProcessor = UnifiedEnforcementProcessor(),
    private val usageCommitWriter: UsageCommitWriter? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val channel = Channel<QueuedEvent>(Channel.UNLIMITED)
    @Volatile
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            for (event in channel) {
                processQueuedEvent(event)
            }
        }
    }

    suspend fun submit(
        previousContext: EnforcementContext,
        currentContext: EnforcementContext
    ): EnforcementProcessResult {
        if (!started) {
            throw InvalidEnforcementContextException("SingleThreadEnforcementQueue must be started before submit")
        }
        val result = CompletableDeferred<EnforcementProcessResult>()
        channel.send(
            QueuedEvent(
                previousContext = previousContext,
                currentContext = currentContext,
                result = result
            )
        )
        return result.await()
    }

    private fun processQueuedEvent(event: QueuedEvent) {
        try {
            val processResult = processor.process(
                previousContext = event.previousContext,
                currentContext = event.currentContext
            )
            usageCommitWriter?.commit(
                previousContext = event.previousContext,
                usageCommit = processResult.usageCommit
            )
            event.result.complete(processResult)
        } catch (throwable: Throwable) {
            event.result.completeExceptionally(throwable)
        }
    }

    private data class QueuedEvent(
        val previousContext: EnforcementContext,
        val currentContext: EnforcementContext,
        val result: CompletableDeferred<EnforcementProcessResult>
    )
}
