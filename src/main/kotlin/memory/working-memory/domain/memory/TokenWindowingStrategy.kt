package memory.`working-memory`.domain.memory

import memory.`working-memory`.domain.model.Message
import memory.`working-memory`.domain.model.Role
import memory.`working-memory`.domain.service.Tokenizer

/**
 * Strategy for selecting messages to include in a context window based on token limits.
 *
 * @property tokenizer Service for counting tokens in text
 * @property maxTokens Maximum number of tokens allowed in the context window
 */
class TokenWindowingStrategy(
    private val tokenizer: Tokenizer,
    private val maxTokens: Int
) {
    /**
     * Selects messages to include in the context window.
     * Prioritizes system messages and recent messages.
     *
     * @param messages List of all messages in the conversation
     * @return List of selected messages that fit within the token limit
     */
    fun selectMessages(messages: List<Message>): List<Message> {
        // Always include system messages
        val systemMessages = messages.filter { it.role == Role.SYSTEM }
        var remainingTokens = maxTokens - systemMessages.sumOf { tokenizer.countTokens(it.content) }
        
        // Prioritize recent messages
        val userAssistantMessages = messages
            .filter { it.role != Role.SYSTEM }
            .sortedByDescending { it.timestamp }
        
        val selectedMessages = mutableListOf<Message>()
        selectedMessages.addAll(systemMessages)
        
        for (message in userAssistantMessages) {
            val tokens = tokenizer.countTokens(message.content)
            if (tokens <= remainingTokens) {
                selectedMessages.add(message)
                remainingTokens -= tokens
            } else {
                // If a message is too large, we skip it
                continue
            }
        }
        
        // Return in chronological order
        return selectedMessages.sortedBy { it.timestamp }
    }
} 