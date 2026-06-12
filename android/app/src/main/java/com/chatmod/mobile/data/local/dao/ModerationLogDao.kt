package com.chatmod.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chatmod.mobile.data.local.entity.BotRuntimeEventEntity
import com.chatmod.mobile.data.local.entity.ChatMessageLogEntity
import com.chatmod.mobile.data.local.entity.ModerationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModerationLogDao {
    @Query("SELECT * FROM chat_message_logs WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    fun observeSessionChatMessages(sessionId: String, limit: Int = 200): Flow<List<ChatMessageLogEntity>>

    @Query("SELECT * FROM chat_message_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentChatMessages(limit: Int = 200): Flow<List<ChatMessageLogEntity>>

    @Query("SELECT * FROM moderation_logs WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    fun observeSessionLogs(sessionId: String, limit: Int = 200): Flow<List<ModerationLogEntity>>

    @Query("SELECT * FROM moderation_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentModerationLogs(limit: Int = 200): Flow<List<ModerationLogEntity>>

    @Query("SELECT * FROM bot_runtime_events WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    fun observeRuntimeEvents(sessionId: String, limit: Int = 200): Flow<List<BotRuntimeEventEntity>>

    @Query("SELECT * FROM bot_runtime_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentRuntimeEvents(limit: Int = 200): Flow<List<BotRuntimeEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModerationLog(log: ModerationLogEntity)

    @Query(
        """
        UPDATE moderation_logs
        SET reviewStatus = :status,
            reviewedAt = :reviewedAt,
            reviewNote = :note
        WHERE id = :id
        """
    )
    suspend fun updateModerationLogReview(
        id: String,
        status: String,
        reviewedAt: Long,
        note: String?
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRuntimeEvent(event: BotRuntimeEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageLogEntity)

    @Query("DELETE FROM chat_message_logs WHERE createdAt < :before")
    suspend fun deleteChatMessagesBefore(before: Long)

    @Query("DELETE FROM moderation_logs WHERE createdAt < :before")
    suspend fun deleteModerationLogsBefore(before: Long)

    @Query("DELETE FROM bot_runtime_events WHERE createdAt < :before")
    suspend fun deleteRuntimeEventsBefore(before: Long)

    @Query("DELETE FROM moderation_logs")
    suspend fun deleteAllModerationLogs(): Int

    @Query("DELETE FROM bot_runtime_events")
    suspend fun deleteAllRuntimeEvents(): Int

    @Query("DELETE FROM chat_message_logs")
    suspend fun deleteAllChatMessages(): Int
}
