package memory.`working-memory`.domain.memory

import memory.`working-memory`.domain.model.Conversation

/**
 * Interface for caching conversations in memory.
 * This provides fast access to frequently used conversations.
 */
interface MemoryCache {
    /**
     * Caches a conversation.
     *
     * @param conversation The conversation to cache
     */
    fun cacheConversation(conversation: Conversation)
    
    /**
     * Retrieves a cached conversation by its ID.
     *
     * @param conversationId The ID of the conversation to retrieve
     * @return The cached conversation if found, null otherwise
     */
    fun getConversation(conversationId: String): Conversation?
    
    /**
     * Removes a conversation from the cache.
     *
     * @param conversationId The ID of the conversation to remove
     */
    fun removeConversation(conversationId: String)
    
    /**
     * Clears all conversations from the cache.
     */
    fun clear()
} 