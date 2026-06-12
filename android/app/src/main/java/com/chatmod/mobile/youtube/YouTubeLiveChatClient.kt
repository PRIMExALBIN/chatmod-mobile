package com.chatmod.mobile.youtube

data class ActiveLiveChat(
    val liveChatId: String,
    val videoId: String,
    val title: String
)

enum class LiveChatMessageType {
    Text,
    SuperChat,
    SuperSticker,
    NewMember,
    MemberMilestone,
    MessageDeleted,
    UserBanned,
    System
}

data class LiveChatMessage(
    val id: String,
    val authorChannelId: String,
    val authorName: String,
    val text: String,
    val publishedAtIso: String,
    val type: LiveChatMessageType = LiveChatMessageType.Text,
    val isOwner: Boolean = false,
    val isModerator: Boolean = false,
    val isMember: Boolean = false,
    val isVerified: Boolean = false,
    val purchaseAmountMicros: Long? = null,
    val purchaseCurrency: String? = null,
    val targetMessageId: String? = null,
    val targetChannelId: String? = null
)

data class LiveChatPage(
    val messages: List<LiveChatMessage>,
    val nextPageToken: String?,
    val pollingIntervalMillis: Long,
    val chatEnded: Boolean = false
)

open class YouTubeApiRetryException(
    message: String,
    val retryAfterMillis: Long? = null
) : RuntimeException(message)

class YouTubeRateLimitException(
    retryAfterMillis: Long? = null,
    message: String = "YouTube is rate limiting live chat requests."
) : YouTubeApiRetryException(message, retryAfterMillis)

class YouTubeQuotaExceededException(
    retryAfterMillis: Long? = null,
    message: String = "YouTube API quota is exhausted. Try again after the quota resets."
) : YouTubeApiRetryException(message, retryAfterMillis)

open class YouTubeTerminalException(
    message: String
) : RuntimeException(message)

class YouTubeAuthenticationException(
    message: String = "YouTube authentication expired. Reconnect the creator account."
) : YouTubeTerminalException(message)

class YouTubePermissionException(
    message: String = "ChatMod Mobile is not allowed to chat or moderate this live chat."
) : YouTubeTerminalException(message)

interface YouTubeLiveChatClient {
    suspend fun findActiveLiveChat(channelId: String): ActiveLiveChat?
    suspend fun listMessages(liveChatId: String, pageToken: String? = null): LiveChatPage
    suspend fun sendMessage(liveChatId: String, text: String): String
    suspend fun deleteMessage(messageId: String)
    suspend fun hideUser(liveChatId: String, authorChannelId: String, durationSeconds: Int? = null)
}
