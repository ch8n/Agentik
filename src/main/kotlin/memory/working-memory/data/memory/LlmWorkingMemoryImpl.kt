package memory.`working-memory`.data.memory

import memory.`working-memory`.domain.memory.MemoryCache
import memory.`working-memory`.domain.memory.TokenWindowingStrategy
import memory.`working-memory`.domain.memory.WorkingMemory
import memory.`working-memory`.domain.model.Conversation
import memory.`working-memory`.domain.model.Message
import memory.`working-memory`.domain.repository.ConversationRepository
import memory.`working-memory`.domain.service.Tokenizer
import java.util.UUID

/**
 * Implementation of the WorkingMemory interface for LLM applications.
 *
 * @property conversationRepository Repository for conversation persistence
 * @property tokenizer Service for counting tokens in text
 * @property memoryCache Cache for frequently accessed conversations
 * @property summarizer Service for generating summaries of conversation segments
 * @property maxTokens Maximum number of tokens allowed in the context window
 */
class LlmWorkingMemoryImpl(
    private val conversationRepository: ConversationRepository,
    private val tokenizer: Tokenizer,
    private val memoryCache: MemoryCache,
    private val summarizer: ConversationSummarizer,
    private val maxTokens: Int = 4000
) : WorkingMemory {
    
    private val tokenWindowingStrategy = TokenWindowingStrategy(tokenizer, maxTokens)
    
    override suspend fun addMessage(conversationId: String, message: Message) {
        val conversation = conversationRepository.getConversation(conversationId) 
            ?: Conversation(
                id = conversationId,
                title = "New Conversation",
                messages = emptyList(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        
        val updatedMessages = conversation.messages + message
        val updatedConversation = conversation.copy(
            messages = updatedMessages,
            updatedAt = System.currentTimeMillis()
        )
        
        conversationRepository.saveConversation(updatedConversation)
        memoryCache.cacheConversation(updatedConversation)
    }
    
    override suspend fun getConversationContext(conversationId: String, maxTokens: Int): List<Message> {
        // Try to get from cache first
        val cachedConversation = memoryCache.getConversation(conversationId)
        val conversation = cachedConversation ?: conversationRepository.getConversation(conversationId) ?: return emptyList()
        
        // If not in cache, add it
        if (cachedConversation == null) {
            memoryCache.cacheConversation(conversation)
        }
        
        // Apply token windowing strategy
        return tokenWindowingStrategy.selectMessages(conversation.messages)
    }
    
    override suspend fun summarizeConversation(conversationId: String): String {
        val conversation = conversationRepository.getConversation(conversationId) ?: return ""
        
        // If the conversation is small enough, no need to summarize
        if (conversation.messages.sumOf { tokenizer.countTokens(it.content) } <= maxTokens) {
            return "This conversation is short and doesn't need summarization."
        }
        
        // Split the conversation into chunks and summarize each chunk
        val chunks = conversation.messages.chunked(10)
        val summaries = chunks.map { summarizer.summarizeConversationSegment(it) }
        
        // Combine the summaries
        return summaries.joinToString("\n\n") { it.content }
    }
    
    override suspend fun clearMemory(conversationId: String) {
        memoryCache.removeConversation(conversationId)
    }
    
    /**
     * Prunes a conversation by summarizing older messages.
     * This helps manage memory growth for long conversations.
     *
     * @param conversationId The ID of the conversation to prune
     * @param maxMessages The maximum number of messages to keep without summarization
     */
    suspend fun pruneConversation(conversationId: String, maxMessages: Int) {
        val conversation = conversationRepository.getConversation(conversationId) ?: return
        
        if (conversation.messages.size <= maxMessages) {
            return
        }
        
        // Messages to keep without summarization (most recent)
        val messagesToKeep = conversation.messages.takeLast(maxMessages / 2)
        
        // Messages to summarize (older messages)
        val messagesToSummarize = conversation.messages
            .dropLast(maxMessages / 2)
            .chunked(10)
            .map { summarizer.summarizeConversationSegment(it) }
        
        // Create new conversation with summaries + recent messages
        val prunedConversation = conversation.copy(
            messages = messagesToSummarize + messagesToKeep,
            updatedAt = System.currentTimeMillis()
        )
        
        conversationRepository.saveConversation(prunedConversation)
        memoryCache.cacheConversation(prunedConversation)
    }
} 