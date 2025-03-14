package memory.`working-memory`.domain.repository

import memory.`working-memory`.domain.model.Conversation

/**
 * Repository interface for conversation persistence.
 */
interface ConversationRepository {
    /**
     * Retrieves a conversation by its ID.
     *
     * @param id The ID of the conversation to retrieve
     * @return The conversation if found, null otherwise
     */
    suspend fun getConversation(id: String): Conversation?
    
    /**
     * Saves a conversation.
     *
     * @param conversation The conversation to save
     */
    suspend fun saveConversation(conversation: Conversation)
    
    /**
     * Retrieves all conversations.
     *
     * @return A list of all conversations
     */
    suspend fun getAllConversations(): List<Conversation>
    
    /**
     * Deletes a conversation by its ID.
     *
     * @param id The ID of the conversation to delete
     */
    suspend fun deleteConversation(id: String)
} 