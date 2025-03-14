package memory.`working-memory`.data.local.entity

/**
 * Entity class for storing conversations in a database.
 *
 * @property id Unique identifier for the conversation
 * @property title A descriptive title for the conversation
 * @property createdAt When the conversation was created
 * @property updatedAt When the conversation was last updated
 * @property metadataJson JSON string containing additional information about the conversation
 */
data class ConversationEntity(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val metadataJson: String
) 