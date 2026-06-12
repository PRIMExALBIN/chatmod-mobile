package com.chatmod.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chatmod.mobile.data.local.entity.PendingSyncJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: PendingSyncJobEntity)

    @Query("SELECT * FROM pending_sync_jobs WHERE nextAttemptAt <= :nowMillis ORDER BY createdAt ASC LIMIT :limit")
    suspend fun dueJobs(nowMillis: Long, limit: Int): List<PendingSyncJobEntity>

    @Query("UPDATE pending_sync_jobs SET attempts = :attempts, nextAttemptAt = :nextAttemptAt, lastError = :lastError, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markFailed(
        id: String,
        attempts: Int,
        nextAttemptAt: Long,
        lastError: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM pending_sync_jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_sync_jobs")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM pending_sync_jobs")
    fun observePendingCount(): Flow<Int>
}
