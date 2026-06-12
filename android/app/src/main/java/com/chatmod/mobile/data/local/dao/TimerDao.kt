package com.chatmod.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.chatmod.mobile.data.local.entity.TimerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers WHERE profileId = :profileId ORDER BY name ASC")
    fun observeForProfile(profileId: String): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers WHERE profileId = :profileId AND enabled = 1 ORDER BY lastSentAt ASC")
    suspend fun enabledForProfile(profileId: String): List<TimerEntity>

    @Upsert
    suspend fun upsert(timer: TimerEntity)

    @Delete
    suspend fun delete(timer: TimerEntity)

    @Query("UPDATE timers SET lastSentAt = :sentAt, updatedAt = :sentAt WHERE id = :id")
    suspend fun markSent(id: String, sentAt: Long)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM timers WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String): Int

    @Query("DELETE FROM timers")
    suspend fun deleteAll(): Int
}
