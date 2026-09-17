package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_history_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallHistoryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallHistoryLog): Long

    @Query("DELETE FROM call_history_logs")
    suspend fun clearAllCallLogs()

    @Query("SELECT COUNT(*) FROM call_history_logs WHERE actionTaken = 'ACCEPTED'")
    fun getAcceptedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_history_logs WHERE actionTaken = 'REJECTED' OR actionTaken = 'BLOCKED_AUTO_REJECTED'")
    fun getRejectedCount(): Flow<Int>
}
