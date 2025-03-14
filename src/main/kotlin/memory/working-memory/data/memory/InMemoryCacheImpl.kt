package memory.`working-memory`.data.memory

import memory.`working-memory`.domain.memory.MemoryCache
import memory.`working-memory`.domain.model.Conversation
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the MemoryCache interface.
 * Uses a ConcurrentHashMap for thread-safe caching.
 */
class InMemoryCacheImpl : MemoryCache {
    private val cache = ConcurrentHashMap<String, Conversation>()
    
    override fun cacheConversation(conversation: Conversation) {
        cache[conversation.id] = conversation
    }
    
    override fun getConversation(conversationId: String): Conversation? {
        return cache[conversationId]
    }
    
    override fun removeConversation(conversationId: String) {
        cache.remove(conversationId)
    }
    
    override fun clear() {
        cache.clear()
    }
} 