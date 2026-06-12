package com.chatmod.mobile.data.local

import com.chatmod.mobile.data.local.dao.ModerationLogDao
import com.chatmod.mobile.data.local.entity.BotRuntimeEventEntity
import com.chatmod.mobile.data.local.entity.ChatMessageLogEntity
import com.chatmod.mobile.data.local.entity.ModerationLogEntity
import com.chatmod.mobile.runtime.BotLogSink
import java.util.UUID

class LocalLogRepository(
    private val dao: ModerationLogDao
) : BotLogSink {
    override suspend fun recordChatMessage(
        sessionId: String,
        youtubeMessageId: String,
        authorChannelId: String,
        authorName: String,
        text: String,
        receivedAtIso: String?
    ) {
        dao.insertChatMessage(
            ChatMessageLogEntity(
                id = "$sessionId:$youtubeMessageId",
                sessionId = sessionId,
                youtubeMessageId = youtubeMessageId,
                authorChannelId = authorChannelId,
                authorName = authorName,
                text = text,
                receivedAtIso = receivedAtIso,
                createdAt = System.currentTimeMillis()
            )
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
        dao.insertModerationLog(
            ModerationLogEntity(
                id = logId ?: UUID.randomUUID().toString(),
                sessionId = sessionId,
                youtubeMessageId = youtubeMessageId,
                authorChannelId = authorChannelId,
                authorName = authorName,
                messageText = messageText,
                actionType = actionType,
                reason = reason,
                confidence = confidence,
                metadataJson = metadataJson,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun recordRuntimeEvent(
        sessionId: String,
        type: String,
        message: String,
        metadataJson: String?
    ) {
        dao.insertRuntimeEvent(
            BotRuntimeEventEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                type = type,
                message = message,
                metadataJson = metadataJson,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
