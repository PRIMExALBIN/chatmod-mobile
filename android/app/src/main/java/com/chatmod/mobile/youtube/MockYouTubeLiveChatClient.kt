package com.chatmod.mobile.youtube

import java.time.Instant

class MockYouTubeLiveChatClient : YouTubeLiveChatClient {
    override suspend fun findActiveLiveChat(channelId: String): ActiveLiveChat {
        return ActiveLiveChat(
            liveChatId = "live-chat-$channelId",
            videoId = "demo-video",
            title = "Saturday build stream"
        )
    }

    override suspend fun listMessages(liveChatId: String, pageToken: String?): LiveChatPage {
        if (pageToken != null) {
            return LiveChatPage(
                messages = emptyList(),
                nextPageToken = pageToken,
                pollingIntervalMillis = 5000
            )
        }

        return LiveChatPage(
            messages = listOf(
                LiveChatMessage(
                    id = "demo-message-1",
                    authorChannelId = "viewer-1",
                    authorName = "ViewerOne",
                    text = "buy cheap views at www.example.com",
                    publishedAtIso = Instant.now().toString()
                )
            ),
            nextPageToken = "next-demo-page",
            pollingIntervalMillis = 5000
        )
    }

    override suspend fun sendMessage(liveChatId: String, text: String): String = "sent-demo-message"

    override suspend fun deleteMessage(messageId: String) = Unit

    override suspend fun hideUser(liveChatId: String, authorChannelId: String, durationSeconds: Int?) = Unit
}
