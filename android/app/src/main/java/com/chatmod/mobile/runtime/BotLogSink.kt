package com.chatmod.mobile.runtime

interface BotLogSink {
    suspend fun recordChatMessage(
        sessionId: String,
        youtubeMessageId: String,
        authorChannelId: String,
        authorName: String,
        text: String,
        receivedAtIso: String?
    ) = Unit

    suspend fun recordModerationAction(
        sessionId: String,
        youtubeMessageId: String?,
        authorChannelId: String?,
        authorName: String?,
        messageText: String?,
        actionType: String,
        reason: String,
        confidence: Double?,
        logId: String? = null,
        metadataJson: String? = null
    )

    suspend fun recordRuntimeEvent(
        sessionId: String,
        type: String,
        message: String,
        metadataJson: String? = null
    )
}
