package com.focuslockout.data

/**
 * Immutable snapshot of everything persisted in DataStore.
 *
 * A session is only *engaged* while [isSessionActive] is set and the end time has not passed, so a
 * stale flag left behind by a killed process can never keep apps locked forever.
 */
data class LockState(
    val isSessionActive: Boolean = false,
    val sessionStartTime: Long = 0L,
    val sessionEndTime: Long = 0L,
    val blockedPackages: Set<String> = emptySet(),
    val selectedChallengeType: ChallengeType = ChallengeType.DEFAULT,
    val durationMinutes: Int = LockPolicy.DEFAULT_DURATION_MINUTES,
    /** Package name -> epoch millis until which that app is temporarily allowed. */
    val graceExpiries: Map<String, Long> = emptyMap(),
) {
    fun isEngaged(now: Long): Boolean = isSessionActive && now < sessionEndTime

    fun isExpired(now: Long): Boolean = isSessionActive && now >= sessionEndTime

    fun remainingMillis(now: Long): Long = if (isEngaged(now)) sessionEndTime - now else 0L

    /** Fraction of the session still remaining, from 1 (just armed) down to 0. */
    fun remainingFraction(now: Long): Float {
        val total = sessionEndTime - sessionStartTime
        if (total <= 0L) return 0f
        return (remainingMillis(now).toFloat() / total).coerceIn(0f, 1f)
    }

    fun elapsedMinutes(now: Long): Int =
        if (sessionStartTime <= 0L) 0 else ((now - sessionStartTime).coerceAtLeast(0L) / 60_000L).toInt()

    fun isInGrace(packageName: String, now: Long): Boolean = (graceExpiries[packageName] ?: 0L) > now

    fun shouldBlock(packageName: String, now: Long): Boolean =
        isEngaged(now) && packageName in blockedPackages && !isInGrace(packageName, now)
}

/** Stores grace entries as a string set of `package|expiry` pairs (package names never contain '|'). */
object GraceCodec {
    private const val SEPARATOR = '|'

    fun encode(entries: Map<String, Long>): Set<String> =
        entries.mapTo(mutableSetOf()) { (pkg, until) -> "$pkg$SEPARATOR$until" }

    fun decode(raw: Set<String>): Map<String, Long> = raw.mapNotNull { entry ->
        val split = entry.lastIndexOf(SEPARATOR)
        if (split <= 0) return@mapNotNull null
        val until = entry.substring(split + 1).toLongOrNull() ?: return@mapNotNull null
        entry.substring(0, split) to until
    }.toMap()
}
