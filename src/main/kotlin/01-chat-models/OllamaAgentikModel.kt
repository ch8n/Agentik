package `01-chat-models`

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.language.StreamingLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import dev.langchain4j.model.ollama.OllamaStreamingLanguageModel

object OllamaDefaults {
    const val BASE_URL = "http://localhost:11434"
    const val DEFAULT_MODEL = "hermes3:3b"
}

object OllamaAgentikModel : AgentikModel {

    override fun chatModel(): ChatLanguageModel {
        return OllamaChatModel.OllamaChatModelBuilder()
            .baseUrl(OllamaDefaults.BASE_URL)
            .modelName(OllamaDefaults.DEFAULT_MODEL)
            .build()
    }

    override fun streamingModel(): StreamingChatLanguageModel {
        return OllamaStreamingChatModel
            .OllamaStreamingChatModelBuilder()
            .baseUrl(OllamaDefaults.BASE_URL)
            .modelName(OllamaDefaults.DEFAULT_MODEL)
            .build()
    }
}


