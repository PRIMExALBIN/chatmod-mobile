package com.chatmod.mobile.data.local

import com.chatmod.mobile.runtime.BotLogSink
import java.util.UUID

class SyncingLogRepository(
    private val local: BotLogSink,
    private val cloudSyncQueue: PendingCloudSyncQueue
) : BotLogSink {
    override suspend fun recordChatMessage(
        sessionId: String,
        youtubeMessageId: String,
        authorChannelId: String,
        authorName: String,
        text: String,
        receivedAtIso: String?
    ) {
        local.recordChatMessage(
            sessionId = sessionId,
            youtubeMessageId = youtubeMessageId,
            authorChannelId = authorChannelId,
            authorName = authorName,
            text = text,
            receivedAtIso = receivedAtIso
        )
        cloudSyncQueue.enqueueStreamMessage(
            sessionId = sessionId,
            youtubeMessageId = youtubeMessageId,
            authorChannelId = authorChannelId,
            authorName = authorName,
            text = text,
            receivedAtIso = receivedAtIso
        )
    }

    override suspend fun recordModerationAction(
        sessionId: String,
        youtubeMessageId: String?,
        authorChannelId: String?,
        authorName: String?,
        messageText: String?,
        actionType: String,
        reason: String,
        confidence: Double?,
        logId: String?,
        metadataJson: String?
    ) {
        val stableLogId = logId ?: UUID.randomUUID().toString()
        local.recordModerationAction(
            sessionId = sessionId,
            youtubeMessageId = youtubeMessageId,
            authorChannelId = authorChannelId,
            authorName = authorName,
            messageText = messageText,
            actionType = actionType,
            reason = reason,
            confidence = confidence,
            logId = stableLogId,
            metadataJson = metadataJson
        )
        cloudSyncQueue.enqueueModerationAction(
            logId = stableLogId,
            sessionId = sessionId,
            youtubeMessageId = youtubeMessageId,
            authorChannelId = authorChannelId,
            actionType = actionType,
            reason = reason,
            confidence = confidence,
            metadataJson = metadataJson
        )
    }

    override suspend fun recordRuntimeEvent(
        sessionId: String,
        type: String,
        message: String,
        metadataJson: String?
    ) {
        local.recordRuntimeEvent(
            sessionId = sessionId,
            type = type,
            message = message,
            metadataJson = metadataJson
        )
        cloudSyncQueue.enqueueRuntimeEvent(
            sessionId = sessionId,
            type = type,
            message = message,
            metadataJson = metadataJson
        )
    }
}
