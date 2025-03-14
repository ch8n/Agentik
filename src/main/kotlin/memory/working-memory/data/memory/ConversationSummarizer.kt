package memory.`working-memory`.data.memory

import memory.`working-memory`.domain.model.Message
import memory.`working-memory`.domain.model.Role
import memory.`working-memory`.domain.service.LlmService
import java.util.UUID

/**
 * Service for generating summaries of conversation segments.
 *
 * @property llmService Service for interacting with an LLM
 */
class ConversationSummarizer(
    private val llmService: LlmService
) {
    /**
     * Summarizes a segment of a conversation.
     *
     * @param messages The messages to summarize
     * @return A system message containing the summary
     */
    suspend fun summarizeConversationSegment(messages: List<Message>): Message {
        val prompt = buildSummarizationPrompt(messages)
        val summary = llmService.generateText(prompt)
        
        return Message(
            id = UUID.randomUUID().toString(),
            content = summary,
            role = Role.SYSTEM,
            timestamp = System.currentTimeMillis(),
            metadata = mapOf("type" to "summary")
        )
    }
    
    /**
     * Builds a prompt for summarizing a conversation segment.
     *
     * @param messages The messages to summarize
     * @return A prompt for the LLM to generate a summary
     */
    private fun buildSummarizationPrompt(messages: List<Message>): String {
        val conversationText = messages.joinToString("\n") { 
            "${it.role.name}: ${it.content}" 
        }
        
        return """
            Please provide a concise summary of the following conversation segment.
            Focus on key points, decisions, and important information.
            
            $conversationText
            
            Summary:
        """.trimIndent()
    }
} 