package memory.`working-memory`.data.repository

import com.google.gson.Gson
import memory.`working-memory`.data.local.dao.ConversationDao
import memory.`working-memory`.data.local.dao.MessageDao
import memory.`working-memory`.data.local.entity.ConversationEntity
import memory.`working-memory`.data.local.entity.MessageEntity
import memory.`working-memory`.domain.model.Conversation
import memory.`working-memory`.domain.model.Message
import memory.`working-memory`.domain.model.Role
import memory.`working-memory`.domain.repository.ConversationRepository

/**
 * Implementation of the ConversationRepository interface.
 *
 * @property conversationDao DAO for conversations
 * @property messageDao DAO for messages
 * @property gson JSON serializer/deserializer
 */
class ConversationRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val gson: Gson
) : ConversationRepository {
    
    override suspend fun getConversation(id: String): Conversation? {
        val conversationEntity = conversationDao.getConversationById(id) ?: return null
        val messageEntities = messageDao.getMessagesForConversation(id)
        return mapToDomain(conversationEntity, messageEntities)
    }
    
    override suspend fun saveConversation(conversation: Conversation) {
        val conversationEntity = mapToEntity(conversation)
        val messageEntities = conversation.messages.map { mapToMessageEntity(it, conversation.id) }
        
        conversationDao.insertConversation(conversationEntity)
        messageDao.insertMessages(messageEntities)
    }
    
    override suspend fun getAllConversations(): List<Conversation> {
        val conversationEntities = conversationDao.getAllConversations()
        return conversationEntities.map { entity ->
            val messageEntities = messageDao.getMessagesForConversation(entity.id)
            mapToDomain(entity, messageEntities)
        }
    }
    
    override suspend fun deleteConversation(id: String) {
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteConversation(id)
    }
    
    /**
     * Maps a conversation entity and message entities to a domain conversation.
     *
     * @param conversationEntity The conversation entity
     * @param messageEntities The message entities
     * @return A domain conversation
     */
    private fun mapToDomain(
        conversationEntity: ConversationEntity,
        messageEntities: List<MessageEntity>
    ): Conversation {
        val messages = messageEntities.map { mapToMessageDomain(it) }
        
        @Suppress("UNCHECKED_CAST")
        val metadata = try {
            gson.fromJson(conversationEntity.metadataJson, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            mapOf<String, Any>()
        }
        
        return Conversation(
            id = conversationEntity.id,
            title = conversationEntity.title,
            messages = messages,
            createdAt = conversationEntity.createdAt,
            updatedAt = conversationEntity.updatedAt,
            metadata = metadata
        )
    }
    
    /**
     * Maps a message entity to a domain message.
     *
     * @param messageEntity The message entity
     * @return A domain message
     */
    private fun mapToMessageDomain(messageEntity: MessageEntity): Message {
        @Suppress("UNCHECKED_CAST")
        val metadata = try {
            gson.fromJson(messageEntity.metadataJson, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            mapOf<String, Any>()
        }
        
        return Message(
            id = messageEntity.id,
            content = messageEntity.content,
            role = Role.valueOf(messageEntity.role),
            timestamp = messageEntity.timestamp,
            metadata = metadata
        )
    }
    
    /**
     * Maps a domain conversation to a conversation entity.
     *
     * @param conversation The domain conversation
     * @return A conversation entity
     */
    private fun mapToEntity(conversation: Conversation): ConversationEntity {
        val metadataJson = gson.toJson(conversation.metadata)
        
        return ConversationEntity(
            id = conversation.id,
            title = conversation.title,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            metadataJson = metadataJson
        )
    }
    
    /**
     * Maps a domain message to a message entity.
     *
     * @param message The domain message
     * @param conversationId The ID of the conversation the message belongs to
     * @return A message entity
     */
    private fun mapToMessageEntity(message: Message, conversationId: String): MessageEntity {
        val metadataJson = gson.toJson(message.metadata)
        
        return MessageEntity(
            id = message.id,
            conversationId = conversationId,
            content = message.content,
            role = message.role.name,
            timestamp = message.timestamp,
            metadataJson = metadataJson
        )
    }
} 