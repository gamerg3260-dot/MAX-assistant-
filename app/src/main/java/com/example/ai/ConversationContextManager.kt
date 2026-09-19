package com.example.ai

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Data class representing a single conversation turn in local memory history.
 */
data class ConversationTurn(
    val timestamp: Long = System.currentTimeMillis(),
    val role: String, // "user" or "assistant"
    val text: String,
    val intent: String? = null
)

/**
 * Local conversation state manager for MAX Assistant.
 * Maintains a sliding window memory of recent commands, inputs, and assistant responses
 * to support context-aware NLP intent routing and follow-up understanding.
 */
class ConversationContextManager {

    private val tag = "ConversationContextManager"

    companion object {
        const val MAX_HISTORY_TURNS = 20

        @Volatile
        private var instance: ConversationContextManager? = null

        fun getInstance(): ConversationContextManager {
            return instance ?: synchronized(this) {
                instance ?: ConversationContextManager().also { instance = it }
            }
        }
    }

    private val historyQueue = ConcurrentLinkedQueue<ConversationTurn>()

    private val _historyState = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val historyState: StateFlow<List<ConversationTurn>> = _historyState.asStateFlow()

    /**
     * Records a new user query or assistant response in context memory.
     */
    fun addTurn(role: String, text: String, intent: String? = null) {
        if (text.isBlank()) return
        val turn = ConversationTurn(role = role, text = text.trim(), intent = intent)
        historyQueue.add(turn)

        // Maintain sliding window capacity
        while (historyQueue.size > MAX_HISTORY_TURNS) {
            historyQueue.poll()
        }

        val updatedList = historyQueue.toList()
        _historyState.value = updatedList
        Log.d(tag, "Added turn [$role]: \"${text.take(40)}\" (Total history size: ${updatedList.size})")
    }

    /**
     * Returns recent conversation history up to the specified limit.
     */
    fun getRecentHistory(limit: Int = 10): List<ConversationTurn> {
        val list = historyQueue.toList()
        return if (list.size > limit) list.takeLast(limit) else list
    }

    /**
     * Formats recent turns into a prompt-ready context string for Gemini API or local rule engine.
     */
    fun getFormattedHistoryForPrompt(limit: Int = 6): String {
        val turns = getRecentHistory(limit)
        if (turns.isEmpty()) return ""

        return buildString {
            appendLine("RECENT CONVERSATION HISTORY (FOR CONTEXT & PRONOUN RESOLUTION):")
            turns.forEach { turn ->
                val speaker = if (turn.role.equals("user", ignoreCase = true)) "User" else "MAX Assistant"
                appendLine("$speaker: ${turn.text}")
            }
        }
    }

    /**
     * Retrieves the last user query recorded in context memory.
     */
    fun getLastUserQuery(): String? {
        return historyQueue.toList().lastOrNull { it.role.equals("user", ignoreCase = true) }?.text
    }

    /**
     * Retrieves the last assistant response recorded in context memory.
     */
    fun getLastAssistantResponse(): String? {
        return historyQueue.toList().lastOrNull { it.role.equals("assistant", ignoreCase = true) }?.text
    }

    /**
     * Clears all stored conversation context memory.
     */
    fun clearHistory() {
        historyQueue.clear()
        _historyState.value = emptyList()
        Log.i(tag, "Conversation context memory cleared.")
    }
}
