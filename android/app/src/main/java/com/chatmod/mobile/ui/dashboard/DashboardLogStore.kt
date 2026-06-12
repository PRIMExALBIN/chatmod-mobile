package com.chatmod.mobile.ui.dashboard

import com.chatmod.mobile.data.local.dao.ModerationLogDao
import com.chatmod.mobile.data.local.entity.BotRuntimeEventEntity
import com.chatmod.mobile.data.local.entity.ChatMessageLogEntity
import com.chatmod.mobile.data.local.entity.ModerationLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

const val StarterLocalHistoryLimit = 120
const val ProLocalHistoryLimit = 1000
const val CreatorLocalHistoryLimit = 2000

interface DashboardLogStore {
    val entries: Flow<List<LogEntrySummary>>
    val userHistory: Flow<List<UserHistorySummary>>

    suspend fun markFalsePositive(logId: String, note: String? = null): Boolean
}

class InMemoryDashboardLogStore : DashboardLogStore {
    override val entries: Flow<List<LogEntrySummary>> = flowOf(emptyList())
    override val userHistory: Flow<List<UserHistorySummary>> = flowOf(emptyList())

    override suspend fun markFalsePositive(logId: String, note: String?): Boolean = false
}

class RoomDashboardLogStore(
    private val dao: ModerationLogDao,
    limit: Int = CreatorLocalHistoryLimit
) : DashboardLogStore {
    private val historyLimit = limit.coerceIn(StarterLocalHistoryLimit, CreatorLocalHistoryLimit)

    override val entries: Flow<List<LogEntrySummary>> = combine(
        dao.observeRecentChatMessages(historyLimit),
        dao.observeRecentModerationLogs(historyLimit),
        dao.observeRecentRuntimeEvents(historyLimit)
    ) { chatMessages, moderationLogs, runtimeEvents ->
        (chatMessages.map { it.toSummary() } + moderationLogs.map { it.toSummary() } + runtimeEvents.map { it.toSummary() })
            .sortedByDescending { it.createdAtMillis }
            .take(historyLimit)
    }

    override val userHistory: Flow<List<UserHistorySummary>> = combine(
        dao.observeRecentChatMessages(historyLimit),
        dao.observeRecentModerationLogs(historyLimit)
    ) { chatMessages, moderationLogs ->
        buildUserHistory(chatMessages, moderationLogs).take(historyLimit)
    }

    override suspend fun markFalsePositive(logId: String, note: String?): Boolean {
        return dao.updateModerationLogReview(
            id = logId,
            status = FalsePositiveReviewStatus,
            reviewedAt = System.currentTimeMillis(),
            note = note
        ) > 0
    }
}

private fun ChatMessageLogEntity.toSummary(): LogEntrySummary {
    return LogEntrySummary(
        id = id,
        sessionId = sessionId,
        kind = LogEntryKind.Chat,
        title = "Chat message",
        detail = "$authorName: $text",
        createdAtMillis = createdAt,
        severity = Severity.Info,
        subjectKey = authorChannelId.ifBlank { "viewer:$authorName" },
        subjectLabel = authorName.ifBlank { "Unknown viewer" }
    )
}

private fun ModerationLogEntity.toSummary(): LogEntrySummary {
    val normalizedAction = actionType.lowercase()
    val severity = when {
        normalizedAction.contains("delete") || normalizedAction.contains("hide") -> Severity.Danger
        normalizedAction.contains("flag") || normalizedAction.contains("timeout") -> Severity.Warning
        else -> Severity.Info
    }
    val author = authorName?.takeIf { it.isNotBlank() } ?: "Unknown viewer"
    val message = messageText?.takeIf { it.isNotBlank() } ?: "No message text recorded"
    val ruleMatch = reason.isRuleMatchReason()
    val subjectKey = authorChannelId?.takeIf { it.isNotBlank() } ?: "viewer:$author"
    val falsePositive = reviewStatus == FalsePositiveReviewStatus

    return LogEntrySummary(
        id = id,
        sessionId = sessionId,
        kind = if (ruleMatch) LogEntryKind.RuleMatch else LogEntryKind.Moderation,
        title = if (ruleMatch) "Rule match: ${reason.ruleMatchLabel()}" else actionType.actionLabel(),
        detail = if (ruleMatch) {
            "$author: $message"
        } else {
            "$author: $message - $reason"
        },
        createdAtMillis = createdAt,
        severity = severity,
        reviewCandidate = ruleMatch && normalizedAction.contains("flag") && !falsePositive,
        actionType = actionType,
        reason = reason,
        subjectKey = subjectKey,
        subjectLabel = author,
        metadataJson = metadataJson,
        reviewStatus = reviewStatus,
        reviewedAtMillis = reviewedAt,
        reviewNote = reviewNote
    )
}

private fun BotRuntimeEventEntity.toSummary(): LogEntrySummary {
    val normalizedType = type.lowercase()
    val severity = when {
        normalizedType.contains("error") || normalizedType.contains("failed") -> Severity.Danger
        normalizedType.contains("warn") || normalizedType.contains("retry") -> Severity.Warning
        else -> Severity.Info
    }

    return LogEntrySummary(
        id = id,
        sessionId = sessionId,
        kind = LogEntryKind.Runtime,
        title = type.replace('_', ' ').replaceFirstChar { it.uppercase() },
        detail = message,
        createdAtMillis = createdAt,
        severity = severity,
        metadataJson = metadataJson
    )
}

private data class UserAccumulator(
    val channelId: String,
    var displayName: String,
    var messageCount: Int = 0,
    var moderationActionCount: Int = 0,
    var destructiveActionCount: Int = 0,
    var warningActionCount: Int = 0,
    var firstSeenMillis: Long = Long.MAX_VALUE,
    var lastSeenMillis: Long = 0L,
    var lastMessagePreview: String? = null
)

private fun buildUserHistory(
    chatMessages: List<ChatMessageLogEntity>,
    moderationLogs: List<ModerationLogEntity>
): List<UserHistorySummary> {
    val users = linkedMapOf<String, UserAccumulator>()

    chatMessages.forEach { message ->
        val key = userKey(message.authorChannelId, message.authorName)
        val user = users.getOrPut(key) {
            UserAccumulator(
                channelId = message.authorChannelId.ifBlank { key },
                displayName = message.authorName.ifBlank { "Unknown viewer" }
            )
        }

        user.displayName = message.authorName.ifBlank { user.displayName }
        user.messageCount += 1
        user.firstSeenMillis = minOf(user.firstSeenMillis, message.createdAt)
        if (message.createdAt >= user.lastSeenMillis) {
            user.lastSeenMillis = message.createdAt
            user.lastMessagePreview = message.text.take(140)
        }
    }

    moderationLogs.forEach { log ->
        val key = userKey(log.authorChannelId, log.authorName)
        val user = users.getOrPut(key) {
            UserAccumulator(
                channelId = log.authorChannelId?.takeIf { it.isNotBlank() } ?: key,
                displayName = log.authorName?.takeIf { it.isNotBlank() } ?: "Unknown viewer"
            )
        }
        val action = log.actionType.lowercase()

        user.moderationActionCount += 1
        if (action.contains("delete") || action.contains("hide") || action.contains("ban")) {
            user.destructiveActionCount += 1
        }
        if (action.contains("flag") || action.contains("warn") || action.contains("timeout")) {
            user.warningActionCount += 1
        }
        user.firstSeenMillis = minOf(user.firstSeenMillis, log.createdAt)
        user.lastSeenMillis = maxOf(user.lastSeenMillis, log.createdAt)
        if (user.lastMessagePreview.isNullOrBlank()) {
            user.lastMessagePreview = log.messageText?.take(140)
        }
    }

    return users.values
        .filter { user -> user.lastSeenMillis > 0L }
        .map { user ->
            UserHistorySummary(
                channelId = user.channelId,
                displayName = user.displayName,
                profileImageUrl = null,
                messageCount = user.messageCount,
                moderationActionCount = user.moderationActionCount,
                destructiveActionCount = user.destructiveActionCount,
                warningActionCount = user.warningActionCount,
                firstSeenMillis = user.firstSeenMillis.takeIf { it != Long.MAX_VALUE } ?: user.lastSeenMillis,
                lastSeenMillis = user.lastSeenMillis,
                lastMessagePreview = user.lastMessagePreview,
                severity = when {
                    user.destructiveActionCount > 0 -> Severity.Danger
                    user.warningActionCount > 0 || user.moderationActionCount > 0 -> Severity.Warning
                    else -> Severity.Info
                }
            )
        }
        .sortedWith(
            compareByDescending<UserHistorySummary> { it.severity.ordinal }
                .thenByDescending { it.lastSeenMillis }
        )
}

private fun userKey(channelId: String?, displayName: String?): String {
    return channelId
        ?.takeIf { it.isNotBlank() }
        ?: displayName?.takeIf { it.isNotBlank() }?.let { "viewer:$it" }
        ?: "viewer:unknown"
}

private fun String.actionLabel(): String {
    return replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun String.isRuleMatchReason(): Boolean {
    val normalizedReason = removePrefix("rate_limited:")
    return RuleMatchReasonPrefixes.any { prefix -> normalizedReason.startsWith(prefix) }
}

private fun String.ruleMatchLabel(): String {
    val label = removePrefix("rate_limited:").substringBefore(":")
    return label.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private val RuleMatchReasonPrefixes = setOf(
    "blocked_term",
    "regex_pattern",
    "link_policy",
    "blocked_domain",
    "domain_not_allowed",
    "excessive_caps",
    "repeated_characters",
    "emoji_spam",
    "mention_spam",
    "symbol_spam",
    "repeated_message_spam",
    "message_flood",
    "suspicious_new_user_burst"
)

const val FalsePositiveReviewStatus = "false_positive"
