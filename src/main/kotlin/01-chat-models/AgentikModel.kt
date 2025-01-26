package `01-chat-models`

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.language.StreamingLanguageModel


sealed interface AgentikModel {
    fun chatModel(): ChatLanguageModel
    fun streamingModel(): StreamingChatLanguageModel
}