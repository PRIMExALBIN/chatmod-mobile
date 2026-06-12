package com.chatmod.mobile.youtube

import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.ChatModHttpException
import com.chatmod.mobile.data.remote.YouTubeLiveChatMessagesRequest
import com.chatmod.mobile.data.remote.YouTubeLiveChatMessageRecord
import com.chatmod.mobile.data.remote.YouTubeMessageSendRequest
import com.chatmod.mobile.data.remote.YouTubeMessageDeleteRequest
import com.chatmod.mobile.data.remote.YouTubeUserHideRequest

class BackendYouTubeLiveChatClient(
    private val api: ChatModApiClient,
    private val accessTokenProvider: suspend () -> String?,
    private val refreshAccessTokenProvider: suspend () -> String? = { null }
) : YouTubeLiveChatClient {
    override suspend fun findActiveLiveChat(channelId: String): ActiveLiveChat? {
        val discovery = withAccessToken { token ->
            api.discoverYouTubeLiveChat(token, channelId)
        }
        val activeChat = discovery.activeChat ?: return null
        val title = discovery.broadcasts
            .firstOrNull { broadcast -> broadcast.videoId == activeChat.videoId }
            ?.title
            ?: "YouTube Live"

        return ActiveLiveChat(
            liveChatId = activeChat.liveChatId,
            videoId = activeChat.videoId,
            title = title
        )
    }

    override suspend fun listMessages(liveChatId: String, pageToken: String?): LiveChatPage {
        val page = withAccessToken { token ->
            api.listYouTubeLiveChatMessages(
                token,
                YouTubeLiveChatMessagesRequest(
                    liveChatId = liveChatId,
                    pageToken = pageToken
                )
            )
        }

        return LiveChatPage(
            messages = page.messages.map { it.toLiveChatMessage() },
            nextPageToken = page.nextPageToken,
            pollingIntervalMillis = page.pollingIntervalMillis
        )
    }

    override suspend fun sendMessage(liveChatId: String, text: String): String {
        return withAccessToken { token ->
            api.sendYouTubeLiveChatMessage(
                token,
                YouTubeMessageSendRequest(
                    liveChatId = liveChatId,
                    text = text
                )
            ).messageId
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        withAccessToken { token ->
            api.deleteYouTubeLiveChatMessage(
                token,
                YouTubeMessageDeleteRequest(
                    messageId = messageId,
                    reason = "runtime_rule_action"
                )
            )
        }
    }

    override suspend fun hideUser(liveChatId: String, authorChannelId: String, durationSeconds: Int?) {
        withAccessToken { token ->
            api.hideYouTubeLiveChatUser(
                token,
                YouTubeUserHideRequest(
                    liveChatId = liveChatId,
                    authorChannelId = authorChannelId,
                    durationSeconds = durationSeconds,
                    reason = "runtime_rule_action"
                )
            )
        }
    }

    private suspend fun <T> withAccessToken(block: suspend (String) -> T): T {
        val firstToken = accessTokenProvider() ?: throw YouTubeAuthenticationException()

        try {
            return block(firstToken)
        } catch (error: Throwable) {
            if (error !is ChatModHttpException || error.statusCode != 401) {
                throw error.toYouTubeRuntimeException()
            }

            val refreshedToken = refreshAccessTokenProvider() ?: throw error.toYouTubeRuntimeException()
            try {
                return block(refreshedToken)
            } catch (refreshedError: Throwable) {
                throw refreshedError.toYouTubeRuntimeException()
            }
        }
    }
}

private fun YouTubeLiveChatMessageRecord.toLiveChatMessage(): LiveChatMessage {
    return LiveChatMessage(
        id = id,
        authorChannelId = authorChannelId,
        authorName = authorName,
        text = text,
        publishedAtIso = publishedAt,
        type = messageType.toLiveChatMessageType(),
        isOwner = isOwner,
        isModerator = isModerator,
        isMember = isMember,
        isVerified = isVerified,
        purchaseAmountMicros = purchaseAmountMicros?.toLongOrNull(),
        purchaseCurrency = purchaseCurrency,
        targetMessageId = targetMessageId,
        targetChannelId = targetChannelId
    )
}

private fun String.toLiveChatMessageType(): LiveChatMessageType {
    return when (this) {
        "superChatEvent" -> LiveChatMessageType.SuperChat
        "superStickerEvent" -> LiveChatMessageType.SuperSticker
        "newSponsorEvent" -> LiveChatMessageType.NewMember
        "memberMilestoneChatEvent" -> LiveChatMessageType.MemberMilestone
        "messageDeletedEvent" -> LiveChatMessageType.MessageDeleted
        "userBannedEvent" -> LiveChatMessageType.UserBanned
        "systemEvent" -> LiveChatMessageType.System
        else -> LiveChatMessageType.Text
    }
}

private fun Throwable.toYouTubeRuntimeException(): Throwable {
    if (this !is ChatModHttpException) {
        return this
    }

    val safeMessage = message ?: "YouTube live chat request failed."
    return when {
        statusCode == 401 || errorCode == "YOUTUBE_NOT_CONNECTED" -> YouTubeAuthenticationException(safeMessage)
        statusCode == 403 || errorCode == "YOUTUBE_PERMISSION_DENIED" -> YouTubePermissionException(safeMessage)
        statusCode == 429 && errorCode == "YOUTUBE_QUOTA_EXCEEDED" -> YouTubeQuotaExceededException(retryAfterMillis, safeMessage)
        statusCode == 429 -> YouTubeRateLimitException(retryAfterMillis, safeMessage)
        errorCode == "YOUTUBE_LIVE_CHAT_ENDED" -> YouTubePermissionException(safeMessage)
        else -> this
    }
}
