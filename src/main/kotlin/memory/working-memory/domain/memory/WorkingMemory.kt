package memory.`working-memory`.domain.memory

import memory.`working-memory`.domain.model.Message

/**
 * Interface for working memory operations in an LLM application.
 * Working memory manages conversation context and provides relevant information to the LLM.
 */
interface WorkingMemory {
    /**
     * Adds a message to a conversation.
     *
     * @param conversationId The ID of the conversation to add the message to
     * @param message The message to add
     */
    suspend fun addMessage(conversationId: String, message: Message)
    
    /**
     * Retrieves the context for a conversation, limited by token count.
     *
     * @param conversationId The ID of the conversation to get context for
     * @param maxTokens The maximum number of tokens to include in the context
     * @return A list of messages that fit within the token limit
     */
    suspend fun getConversationContext(conversationId: String, maxTokens: Int): List<Message>
    
    /**
     * Generates a summary of a conversation.
     *
     * @param conversationId The ID of the conversation to summarize
     * @return A summary of the conversation
     */
    suspend fun summarizeConversation(conversationId: String): String
    
    /**
     * Clears the memory for a conversation.
     *
     * @param conversationId The ID of the conversation to clear memory for
     */
    suspend fun clearMemory(conversationId: String)
} 