package com.chatmod.mobile.runtime

import org.json.JSONObject

class BotSessionStats(
    private val startedAtMillis: Long
) {
    private var messagesProcessed = 0
    private var duplicateMessagesSkipped = 0
    private var moderationActionsTaken = 0
    private var messagesDeleted = 0
    private var usersHidden = 0
    private var autoRepliesSent = 0
    private var commandsSent = 0
    private var timersSent = 0
    private val commandsUsed = mutableMapOf<String, Int>()
    private val timersUsed = mutableMapOf<String, Int>()
    private val triggeredRules = mutableMapOf<String, Int>()

    @Synchronized
    fun record(result: BotRunResult) {
        messagesProcessed += result.messagesProcessed
        duplicateMessagesSkipped += result.duplicateMessagesSkipped
        moderationActionsTaken += result.actionsTaken
        messagesDeleted += result.messagesDeleted
        usersHidden += result.usersHidden
        autoRepliesSent += result.autoRepliesSent
        commandsSent += result.commandsSent
        timersSent += result.timersSent
        result.commandsUsed.forEach { (commandId, count) -> commandsUsed.increment(commandId, count) }
        result.timersUsed.forEach { (timerId, count) -> timersUsed.increment(timerId, count) }
        result.triggeredRules.forEach { (ruleKey, count) -> triggeredRules.increment(ruleKey, count) }
    }

    @Synchronized
    fun metadataPairs(endedAtMillis: Long): List<Pair<String, Any?>> {
        return listOf(
            "startedAtMillis" to startedAtMillis,
            "endedAtMillis" to endedAtMillis,
            "durationMillis" to (endedAtMillis - startedAtMillis).coerceAtLeast(0),
            "messagesProcessed" to messagesProcessed,
            "duplicateMessagesSkipped" to duplicateMessagesSkipped,
            "moderationActionsTaken" to moderationActionsTaken,
            "messagesDeleted" to messagesDeleted,
            "usersHidden" to usersHidden,
            "usersTimedOutOrHidden" to usersHidden,
            "autoRepliesSent" to autoRepliesSent,
            "commandsSent" to commandsSent,
            "timersSent" to timersSent,
            "commandsUsedJson" to countsJson(commandsUsed),
            "timersUsedJson" to countsJson(timersUsed),
            "topTriggeredRulesJson" to topCountsJson(triggeredRules)
        )
    }

    private fun MutableMap<String, Int>.increment(key: String, count: Int) {
        this[key] = (this[key] ?: 0) + count
    }

    private fun countsJson(counts: Map<String, Int>): String {
        val json = JSONObject()
        counts.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    private fun topCountsJson(counts: Map<String, Int>, limit: Int = 5): String {
        val json = JSONObject()
        counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .forEach { entry -> json.put(entry.key, entry.value) }
        return json.toString()
    }
}
