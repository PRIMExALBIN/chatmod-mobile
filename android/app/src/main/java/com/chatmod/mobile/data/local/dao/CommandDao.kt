package com.chatmod.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.chatmod.mobile.data.local.entity.CommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands WHERE profileId = :profileId ORDER BY name ASC")
    fun observeForProfile(profileId: String): Flow<List<CommandEntity>>

    @Query("SELECT * FROM commands WHERE profileId = :profileId AND enabled = 1 ORDER BY name ASC")
    suspend fun enabledForProfile(profileId: String): List<CommandEntity>

    @Upsert
    suspend fun upsert(command: CommandEntity)

    @Delete
    suspend fun delete(command: CommandEntity)

    @Query("DELETE FROM commands WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM commands WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String): Int

    @Query("DELETE FROM commands")
    suspend fun deleteAll(): Int
}
