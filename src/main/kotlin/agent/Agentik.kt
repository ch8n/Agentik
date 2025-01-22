package agent

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.tool.ToolExecution
import memory.sessions.SessionStorage
import models.AgentikModel
import models.chatLanguageModel


private interface AiAssistant {

    fun chat(prompt: String): String
}

data class Agentik(
    val systemPrompt: String = "",
    val modelType: AgentikModel = AgentikModel.Ollama,
    val modelName: String = "llama3.2:latest",
    val tools: List<AgentikTool> = emptyList()
) {

    private var assistantAgent: AiAssistant? = null
    private val sessionStorage = SessionStorage(100)

    init {
        val chatLanguageModel = chatLanguageModel(
            agentikModel = modelType,
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

    fun messagesL4j() = sessionStorage.l4jChatMemoryMessages()

    fun messages() = messagesL4j().map {
        when(it.type()){
            ChatMessageType.SYSTEM -> (it as SystemMessage).text()
            ChatMessageType.USER -> (it as UserMessage).singleText()
            ChatMessageType.AI -> (it as AiMessage).text()
            ChatMessageType.TOOL_EXECUTION_RESULT -> (it as ToolExecutionResultMessage).text()
        }
    }

    fun execute(userPrompt: String): String {
        return try {
            assistantAgent?.chat(userPrompt) ?: "Failed to response"
        } catch (e: Exception) {
            e.message ?: "Failed to response"
        }
    }
}

interface AgentikTool

