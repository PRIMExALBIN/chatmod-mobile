package com.chatmod.mobile.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerSchedulerTest {
    private val scheduler = TimerScheduler()

    @Test
    fun returnsEnabledTimersPastIntervalAndActivityThreshold() {
        val timers = listOf(
            ScheduledTimer(
                id = "rules",
                message = "Keep chat friendly.",
                intervalMinutes = 10,
                minChatMessages = 5,
                enabled = true,
                lastSentAtMillis = 0
            ),
            ScheduledTimer(
                id = "socials",
                message = "Follow after stream.",
                intervalMinutes = 30,
                minChatMessages = 5,
                enabled = true,
                lastSentAtMillis = 9 * 60_000L
            )
        )

        val due = scheduler.dueTimers(
            timers = timers,
            messagesSinceLastTimer = 6,
            nowMillis = 10 * 60_000L
        )

        assertEquals(listOf("rules"), due.map { it.id })
    }

    @Test
    fun blocksTimersWhenChatActivityIsTooLow() {
        val timers = listOf(
            ScheduledTimer(
                id = "rules",
                message = "Keep chat friendly.",
                intervalMinutes = 10,
                minChatMessages = 5,
                enabled = true,
                lastSentAtMillis = null
            )
        )

        val due = scheduler.dueTimers(
            timers = timers,
            messagesSinceLastTimer = 2,
            nowMillis = 10 * 60_000L
        )

        assertEquals(emptyList<ScheduledTimer>(), due)
    }

    @Test
    fun rotatesBySelectingOneDueTimer() {
        val timers = listOf(
            ScheduledTimer(
                id = "rules",
                message = "Keep chat friendly.",
                intervalMinutes = 10,
                minChatMessages = 3,
                enabled = true,
                lastSentAtMillis = null
            ),
            ScheduledTimer(
                id = "socials",
                message = "Follow after stream.",
                intervalMinutes = 10,
                minChatMessages = 3,
                enabled = true,
                lastSentAtMillis = null
            )
        )

        val due = scheduler.rotatedDueTimers(
            timers = timers,
            messagesSinceLastTimer = 3,
            nowMillis = 10 * 60_000L,
            pickIndex = { 1 }
        )

        assertEquals(listOf("socials"), due.map { it.id })
    }

    @Test
    fun clampsRotatedTimerIndexToDueList() {
        val timers = listOf(
            ScheduledTimer(
                id = "rules",
                message = "Keep chat friendly.",
                intervalMinutes = 10,
                minChatMessages = 3,
                enabled = true,
                lastSentAtMillis = null
            ),
            ScheduledTimer(
                id = "socials",
                message = "Follow after stream.",
                intervalMinutes = 10,
                minChatMessages = 3,
                enabled = true,
                lastSentAtMillis = null
            )
        )

        val due = scheduler.rotatedDueTimers(
            timers = timers,
            messagesSinceLastTimer = 3,
            nowMillis = 10 * 60_000L,
            pickIndex = { 99 }
        )

        assertEquals(listOf("socials"), due.map { it.id })
    }
}
