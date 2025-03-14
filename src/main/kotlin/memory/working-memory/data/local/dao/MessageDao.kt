package memory.`working-memory`.data.local.dao

import memory.`working-memory`.data.local.entity.MessageEntity

/**
 * Data Access Object for messages.
 */
interface MessageDao {
    /**
     * Retrieves all messages for a conversation.
     *
     * @param conversationId The ID of the conversation to retrieve messages for
     * @return A list of message entities for the conversation
     */
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>
    
    /**
     * Inserts or updates a list of messages.
     *
     * @param messages The message entities to insert or update
     */
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    /**
     * Inserts or updates a single message.
     *
     * @param message The message entity to insert or update
     */
    suspend fun insertMessage(message: MessageEntity)
    
    /**
     * Deletes all messages for a conversation.
     *
     * @param conversationId The ID of the conversation to delete messages for
     */
    suspend fun deleteMessagesForConversation(conversationId: String)
} 