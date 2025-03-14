package memory.`working-memory`.domain.model

/**
 * Represents a single message in a conversation.
 *
 * @property id Unique identifier for the message
 * @property content The actual text content of the message
 * @property role The role of the entity that sent the message (user, assistant, or system)
 * @property timestamp When the message was created
 * @property metadata Additional information about the message
 */
data class Message(
    val id: String,
    val content: String,
    val role: Role,
    val timestamp: Long,
    val metadata: Map<String, Any> = mapOf()
) 