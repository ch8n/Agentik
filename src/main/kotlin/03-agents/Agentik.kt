package `03-agents`

import `01-chat-models`.AgentikModel
import `02-functions`.AgentikTool
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.service.AiServices
import memory.sessions.SessionStorage


private interface AiAssistant {
    fun chat(prompt: String): String
}

data class Agentik(
    val systemPrompt: String = "",
    val chatModel: AgentikModel,
    val tools: List<AgentikTool> = emptyList(),
    val sessionStorage: SessionStorage = SessionStorage(100),
    val isStreaming: Boolean = false
) {

    private var assistant: AiAssistant = AiServices
        .builder(AiAssistant::class.java)
        .let {
            if (systemPrompt.isNotEmpty()) {
                it.systemMessageProvider { systemPrompt }
            } else {
                it
            }
        }.let {
            if (isStreaming) {
                it.streamingChatLanguageModel(chatModel.streamingModel())
            } else {
                it.chatLanguageModel(chatModel.chatModel())
            }
        }.chatMemory(sessionStorage.l4jChatMemory())
        .tools(*tools.toTypedArray())
        .build()

    fun messagesL4j() = sessionStorage.l4jChatMemoryMessages()

    fun messages() = messagesL4j().map {
        when (it.type()) {
            ChatMessageType.SYSTEM -> (it as SystemMessage).text()
            ChatMessageType.USER -> (it as UserMessage).singleText()
            ChatMessageType.AI -> (it as AiMessage).text()
            ChatMessageType.TOOL_EXECUTION_RESULT -> (it as ToolExecutionResultMessage).text()
        }
    }

    fun execute(userPrompt: String): String {
        return try {
            assistant.chat(userPrompt)
        } catch (e: Exception) {
            e.message ?: "Failed to response"
        }
    }
}



