package memory.`working-memory`.data.local.entity

/**
 * Entity class for storing messages in a database.
 *
 * @property id Unique identifier for the message
 * @property conversationId ID of the conversation this message belongs to
 * @property content The actual text content of the message
 * @property role The role of the entity that sent the message (user, assistant, or system)
 * @property timestamp When the message was created
 * @property metadataJson JSON string containing additional information about the message
 */
data class MessageEntity(
    val id: String,
    val conversationId: String,
    val content: String,
    val role: String,
    val timestamp: Long,
    val metadataJson: String
) 