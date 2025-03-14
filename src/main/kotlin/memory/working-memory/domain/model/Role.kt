package memory.`working-memory`.domain.model

/**
 * Represents the role of a message sender in a conversation.
 */
enum class Role {
    /**
     * Message from the user
     */
    USER,
    
    /**
     * Message from the assistant (LLM)
     */
    ASSISTANT,
    
    /**
     * System message (instructions, summaries, etc.)
     */
    SYSTEM
} 