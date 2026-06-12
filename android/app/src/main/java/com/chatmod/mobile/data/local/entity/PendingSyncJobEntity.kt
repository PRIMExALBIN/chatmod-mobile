package com.chatmod.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_sync_jobs",
    indices = [
        Index(value = ["type"]),
        Index(value = ["nextAttemptAt", "createdAt"])
    ]
)
data class PendingSyncJobEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payloadJson: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)
