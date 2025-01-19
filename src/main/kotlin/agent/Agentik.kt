package agent

import dev.langchain4j.service.AiServices
import memory.sessions.SessionStorage
import models.AgentikModel
import models.chatLanguageModel


private interface AiAssistant {

    fun chat(prompt: String): String
}

data class Agentik(
    val systemPrompt: String = "",
    val model: AgentikModel = AgentikModel.Ollama,
    val modelName: String = "llama3.2:latest",
    val tools: List<AgentikTool> = emptyList()
) {

    private var assistantAgent: AiAssistant? = null
    private val sessionStorage = SessionStorage(100)

    init {
        val chatLanguageModel = chatLanguageModel(
            agentikModel = model,
            modelName = modelName
        )
        assistantAgent = AiServices
            .builder(AiAssistant::class.java)
            .let {
                if (systemPrompt.isNotEmpty()) {
                    it.systemMessageProvider { systemPrompt }
                } else {
                    it
                }
            }
            .chatLanguageModel(chatLanguageModel)
            .chatMemory(sessionStorage.l4jChatMemory())
            .tools(*tools.toTypedArray())
            .build()
    }

    fun messages() = sessionStorage.l4jChatMemoryMessages()

    fun execute(userPrompt: String): String {
        return try {
            assistantAgent?.chat(userPrompt) ?: "Failed to response"
        } catch (e: Exception){
            e.message ?: "Failed to response"
        }
    }
}

interface AgentikTool

