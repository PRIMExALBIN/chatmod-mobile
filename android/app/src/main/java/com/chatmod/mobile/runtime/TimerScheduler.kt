package com.chatmod.mobile.runtime

data class ScheduledTimer(
    val id: String,
    val message: String,
    val intervalMinutes: Int,
    val minChatMessages: Int,
    val quietStartMinutes: Int? = null,
    val quietEndMinutes: Int? = null,
    val enabled: Boolean,
    val lastSentAtMillis: Long?
)

class TimerScheduler {
    fun dueTimers(
        timers: List<ScheduledTimer>,
        messagesSinceLastTimer: Int,
        nowMillis: Long,
        streamStartedAtMillis: Long? = null
    ): List<ScheduledTimer> {
        return timers.filter { timer ->
            if (!timer.enabled) {
                return@filter false
            }

            if (messagesSinceLastTimer < timer.minChatMessages) {
                return@filter false
            }

            if (timer.isInQuietWindow(nowMillis, streamStartedAtMillis)) {
                return@filter false
            }

            val lastSentAt = timer.lastSentAtMillis
            lastSentAt == null || nowMillis - lastSentAt >= timer.intervalMinutes * MillisPerMinute
        }
    }

    fun rotatedDueTimers(
        timers: List<ScheduledTimer>,
        messagesSinceLastTimer: Int,
        nowMillis: Long,
        streamStartedAtMillis: Long? = null,
        pickIndex: (Int) -> Int = { size -> (0 until size).random() }
    ): List<ScheduledTimer> {
        val due = dueTimers(
            timers = timers,
            messagesSinceLastTimer = messagesSinceLastTimer,
            nowMillis = nowMillis,
            streamStartedAtMillis = streamStartedAtMillis
        )
        if (due.isEmpty()) {
            return emptyList()
        }

        val index = pickIndex(due.size).coerceIn(0, due.lastIndex)
        return listOf(due[index])
    }

    companion object {
        private const val MillisPerMinute = 60_000L
    }
}

private fun ScheduledTimer.isInQuietWindow(nowMillis: Long, streamStartedAtMillis: Long?): Boolean {
    val quietStart = quietStartMinutes ?: return false
    val quietEnd = quietEndMinutes ?: return false
    if (quietEnd <= quietStart || streamStartedAtMillis == null) {
        return false
    }

    val elapsedMinutes = ((nowMillis - streamStartedAtMillis) / 60_000L).toInt()
    return elapsedMinutes >= quietStart && elapsedMinutes < quietEnd
}
