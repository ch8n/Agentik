package memory.`working-memory`.data.local.dao

import memory.`working-memory`.data.local.entity.ConversationEntity

/**
 * Data Access Object for conversations.
 */
interface ConversationDao {
    /**
     * Retrieves a conversation by its ID.
     *
     * @param id The ID of the conversation to retrieve
     * @return The conversation entity if found, null otherwise
     */
    suspend fun getConversationById(id: String): ConversationEntity?
    
    /**
     * Inserts or updates a conversation.
     *
     * @param conversation The conversation entity to insert or update
     */
    suspend fun insertConversation(conversation: ConversationEntity)
    
    /**
     * Retrieves all conversations.
     *
     * @return A list of all conversation entities
     */
    suspend fun getAllConversations(): List<ConversationEntity>
    
    /**
     * Deletes a conversation by its ID.
     *
     * @param id The ID of the conversation to delete
     */
    suspend fun deleteConversation(id: String)
} 