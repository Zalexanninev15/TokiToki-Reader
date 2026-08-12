package io.github.zalexanninev15.tokitoki.domain.readsync

import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId

/**
 * One pending "tell the origin server this was read" job. Persisted in Room so it
 * survives process death — a read acknowledgement lost to a swipe-away is a bug the
 * user notices immediately on their other device.
 */
data class ReadSyncJob(
    val itemId: FeedItemId,
    val enqueuedAtMillis: Long,
    val attempts: Int = 0,
    val nextAttemptAtMillis: Long = enqueuedAtMillis,
    val lastError: String? = null,
)

sealed interface ReadSyncOutcome {
    data object Success : ReadSyncOutcome
    /** 429 or 5xx — worth retrying. [retryAfterMillis] comes from the server when given. */
    data class Transient(val reason: String, val retryAfterMillis: Long? = null) : ReadSyncOutcome
    /** 401/403/404 — retrying will not help; surface to the user instead. */
    data class Permanent(val reason: String) : ReadSyncOutcome
}

/**
 * Exponential backoff with full jitter.
 *
 * Jitter matters here specifically: after a network outage every queued job for every
 * account becomes due at the same instant, and without it the app would fire a
 * synchronised burst straight into the 300-requests-per-5-minutes Mastodon limit.
 */
class BackoffPolicy(
    private val baseDelayMillis: Long = 2_000,
    private val maxDelayMillis: Long = 30 * 60 * 1_000,
    val maxAttempts: Int = 8,
    private val jitter: (Long) -> Long = { if (it <= 0) 0 else (0..it).random() },
) {
    fun nextDelayMillis(attempts: Int): Long {
        val exponent = attempts.coerceIn(0, 20)
        val uncapped = baseDelayMillis shl exponent
        val capped = if (uncapped <= 0) maxDelayMillis else minOf(uncapped, maxDelayMillis)
        return jitter(capped)
    }

    fun shouldGiveUp(attempts: Int): Boolean = attempts >= maxAttempts

    /** Applies an outcome to a job, returning the updated job or null when it is done. */
    fun apply(job: ReadSyncJob, outcome: ReadSyncOutcome, nowMillis: Long): ReadSyncJob? =
        when (outcome) {
            is ReadSyncOutcome.Success -> null
            is ReadSyncOutcome.Permanent -> null
            is ReadSyncOutcome.Transient -> {
                val attempts = job.attempts + 1
                if (shouldGiveUp(attempts)) null
                else job.copy(
                    attempts = attempts,
                    nextAttemptAtMillis = nowMillis +
                        (outcome.retryAfterMillis ?: nextDelayMillis(attempts)),
                    lastError = outcome.reason,
                )
            }
        }
}
