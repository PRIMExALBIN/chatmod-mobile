package com.chatmod.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timers",
    indices = [
        Index(value = ["profileId", "name"], unique = true)
    ]
)
data class TimerEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val message: String,
    val intervalMinutes: Int,
    val minChatMessages: Int,
    val quietStartMinutes: Int?,
    val quietEndMinutes: Int?,
    val enabled: Boolean,
    val lastSentAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
