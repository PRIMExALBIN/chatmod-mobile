package com.chatmod.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bot_runtime_events",
    indices = [
        Index(value = ["sessionId", "createdAt"]),
        Index(value = ["type"])
    ]
)
data class BotRuntimeEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,
    val message: String,
    val metadataJson: String?,
    val createdAt: Long
)
