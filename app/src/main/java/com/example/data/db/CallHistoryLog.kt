package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_history_logs")
data class CallHistoryLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val phoneNumber: String,
    val callerName: String,
    val actionTaken: String, // "ACCEPTED", "REJECTED", "BLOCKED_AUTO_REJECTED", "SILENCED"
    val voiceCommandUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
