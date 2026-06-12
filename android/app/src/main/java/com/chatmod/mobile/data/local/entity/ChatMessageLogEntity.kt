package com.chatmod.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_message_logs",
    indices = [
        Index(value = ["sessionId", "createdAt"]),
        Index(value = ["authorChannelId"]),
        Index(value = ["sessionId", "youtubeMessageId"], unique = true)
    ]
)
data class ChatMessageLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val youtubeMessageId: String,
    val authorChannelId: String,
    val authorName: String,
    val text: String,
    val receivedAtIso: String?,
    val createdAt: Long
)
