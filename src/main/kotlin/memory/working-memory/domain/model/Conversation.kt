package memory.`working-memory`.domain.model

/**
 * Represents a complete conversation between a user and the LLM.
 *
 * @property id Unique identifier for the conversation
 * @property title A descriptive title for the conversation
 * @property messages List of messages in the conversation
 * @property createdAt When the conversation was created
 * @property updatedAt When the conversation was last updated
 * @property metadata Additional information about the conversation
 */
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<Message>,
    val createdAt: Long,
    val updatedAt: Long,
    val metadata: Map<String, Any> = mapOf()
) 