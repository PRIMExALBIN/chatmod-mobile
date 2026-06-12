package com.chatmod.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "commands",
    indices = [
        Index(value = ["profileId", "name"], unique = true)
    ]
)
data class CommandEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val response: String,
    val aliasesJson: String,
    val cooldownSeconds: Int,
    val accessLevel: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
