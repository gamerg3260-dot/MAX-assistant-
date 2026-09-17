package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auto_responder_events")
data class AutoResponderEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "SMS_RECEIVED", "MISSED_CALL", "INCOMING_CALL"
    val senderOrNumber: String,
    val incomingContent: String,
    val generatedReply: String? = null,
    val status: String = "SUCCESS", // "SUCCESS", "FAILED", "SKIPPED", "PENDING"
    val errorMessage: String? = null
)
