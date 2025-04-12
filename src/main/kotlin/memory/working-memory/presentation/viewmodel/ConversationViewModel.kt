package memory.`working-memory`.presentation.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import memory.`working-memory`.domain.memory.WorkingMemory
import memory.`working-memory`.domain.model.Message
import memory.`working-memory`.domain.model.Role
import memory.`working-memory`.domain.service.LlmService
import memory.`working-memory`.presentation.state.ConversationState
import java.util.UUID

/**
 * ViewModel for managing conversation state and interactions.
 *
 * @property workingMemory Service for working memory operations
 * @property llmService Service for interacting with an LLM
 */
class ConversationViewModel(
    private val workingMemory: WorkingMemory,
    private val llmService: LlmService
) {
    
    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Loading)
    val conversationState: StateFlow<ConversationState> = _conversationState
    
    private var currentConversationId: String? = null
    
    /**
     * Loads a conversation.
     *
     * @param conversationId The ID of the conversation to load
     */
    suspend fun loadConversation(conversationId: String) {
        currentConversationId = conversationId
        
        try {
            val context = workingMemory.getConversationContext(conversationId, MAX_TOKENS)
            _conversationState.value = ConversationState.Loaded(context)
        } catch (e: Exception) {
            _conversationState.value = ConversationState.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Sends a user message and generates an LLM response.
     *
     * @param userMessage The message from the user
     */
    suspend fun sendMessage(userMessage: String) {
        val conversationId = currentConversationId ?: UUID.randomUUID().toString()
        currentConversationId = conversationId
        
        try {
            // Create and add user message
            val message = Message(
                id = UUID.randomUUID().toString(),
                content = userMessage,
                role = Role.USER,
                timestamp = System.currentTimeMillis()
            )
            workingMemory.addMessage(conversationId, message)
            
            // Update UI state
            val updatedContext = workingMemory.getConversationContext(conversationId, MAX_TOKENS)
            _conversationState.value = ConversationState.Loaded(updatedContext)
            
            // Generate LLM response
            val response = generateLlmResponse(conversationId)
            
            // Add assistant response to memory
            val assistantMessage = Message(
                id = UUID.randomUUID().toString(),
                content = response,
                role = Role.ASSISTANT,
                timestamp = System.currentTimeMillis()
            )
            workingMemory.addMessage(conversationId, assistantMessage)
            
            // Update UI state again
            val finalContext = workingMemory.getConversationContext(conversationId, MAX_TOKENS)
            _conversationState.value = ConversationState.Loaded(finalContext)
        } catch (e: Exception) {
            _conversationState.value = ConversationState.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Generates a response from the LLM.
     *
     * @param conversationId The ID of the conversation to generate a response for
     * @return The generated response
     */
    private suspend fun generateLlmResponse(conversationId: String): String {
        val context = workingMemory.getConversationContext(conversationId, MAX_TOKENS)
        return llmService.generateResponse(context)
    }
    
    /**
     * Clears the memory for the current conversation.
     */
    suspend fun clearMemory() {
        currentConversationId?.let {
            workingMemory.clearMemory(it)
            _conversationState.value = ConversationState.Loaded(emptyList())
        }
    }
    
    companion object {
        private const val MAX_TOKENS = 4000
    }
} 