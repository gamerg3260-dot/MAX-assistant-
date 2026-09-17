package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoResponderDao {
    @Query("SELECT * FROM auto_responder_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<AutoResponderEvent>>

    @Query("SELECT * FROM auto_responder_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 50): Flow<List<AutoResponderEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AutoResponderEvent): Long

    @Update
    suspend fun updateEvent(event: AutoResponderEvent)

    @Query("DELETE FROM auto_responder_events")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM auto_responder_events WHERE status = 'SUCCESS'")
    fun getSuccessfulCount(): Flow<Int>
}
