package com.chatmod.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moderation_logs",
    indices = [
        Index(value = ["sessionId", "createdAt"]),
        Index(value = ["authorChannelId"]),
        Index(value = ["reviewStatus"])
    ]
)
data class ModerationLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val youtubeMessageId: String?,
    val authorChannelId: String?,
    val authorName: String?,
    val messageText: String?,
    val actionType: String,
    val reason: String,
    val confidence: Double?,
    val metadataJson: String? = null,
    val createdAt: Long,
    val reviewStatus: String? = null,
    val reviewedAt: Long? = null,
    val reviewNote: String? = null
)
