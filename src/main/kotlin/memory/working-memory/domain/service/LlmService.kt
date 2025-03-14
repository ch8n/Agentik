package memory.`working-memory`.domain.service

import memory.`working-memory`.domain.model.Message

/**
 * Interface for interacting with an LLM.
 */
interface LlmService {
    /**
     * Generates a response from the LLM based on a list of messages.
     *
     * @param messages The conversation context to generate a response for
     * @return The generated response text
     */
    suspend fun generateResponse(messages: List<Message>): String
    
    /**
     * Generates a text completion from the LLM based on a prompt.
     *
     * @param prompt The prompt to generate text from
     * @return The generated text
     */
    suspend fun generateText(prompt: String): String
} 