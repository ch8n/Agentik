package memory.`working-memory`.presentation.state

import memory.`working-memory`.domain.model.Message

/**
 * Represents the state of a conversation in the UI.
 */
sealed class ConversationState {
    /**
     * The conversation is loading.
     */
    object Loading : ConversationState()
    
    /**
     * The conversation has been loaded.
     *
     * @property messages The messages in the conversation
     */
    data class Loaded(val messages: List<Message>) : ConversationState()
    
    /**
     * An error occurred while loading the conversation.
     *
     * @property message The error message
     */
    data class Error(val message: String) : ConversationState()
} 