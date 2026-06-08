package com.addictionbuster.enforcement.executor

import com.addictionbuster.enforcement.InvalidEnforcementContextException

class FailClosedHomeLimiter(
    private val maxAttempts: Int = 3,
    private val windowMillis: Long = 5_000L
) {
    private val attemptsByIdentity = mutableMapOf<String, MutableList<Long>>()

    init {
        if (maxAttempts <= 0) {
            throw InvalidEnforcementContextException("maxAttempts must be > 0")
        }
        if (windowMillis <= 0L) {
            throw InvalidEnforcementContextException("windowMillis must be > 0")
        }
    }

    @Synchronized
    fun canAttempt(identityKey: String, nowMillis: Long): Boolean {
        requireIdentity(identityKey)
        requireTime(nowMillis)
        val attempts = attemptsByIdentity.getOrPut(identityKey) { mutableListOf() }
        attempts.removeAll { nowMillis - it > windowMillis }
        return attempts.size < maxAttempts
    }

    @Synchronized
    fun recordAttempt(identityKey: String, nowMillis: Long) {
        requireIdentity(identityKey)
        requireTime(nowMillis)
        attemptsByIdentity.getOrPut(identityKey) { mutableListOf() }.add(nowMillis)
    }

    private fun requireIdentity(identityKey: String) {
        if (identityKey.isBlank()) {
            throw InvalidEnforcementContextException("identityKey is required")
        }
    }

    private fun requireTime(nowMillis: Long) {
        if (nowMillis < 0L) {
            throw InvalidEnforcementContextException("nowMillis must be >= 0")
        }
    }
}
