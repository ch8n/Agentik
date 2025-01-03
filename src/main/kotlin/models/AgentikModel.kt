package models

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.language.StreamingLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaStreamingLanguageModel


enum class AgentikModel {
    Ollama
}

fun chatLanguageModel(
    agentikModel: AgentikModel = AgentikModel.Ollama,
    modelName: String = "qwen2.5:0.5b-instruct",
    baseUrl: String = "http://localhost:11434",
) : ChatLanguageModel {
    return when(agentikModel){
        AgentikModel.Ollama -> {
            OllamaChatModel.OllamaChatModelBuilder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build()
        }
    }
}

fun streamingLanguageModel(
    agentikModel: AgentikModel = AgentikModel.Ollama,
    modelName: String = "qwen2.5:0.5b-instruct",
    baseUrl: String = "http://localhost:11434",
) : StreamingLanguageModel {
    return when(agentikModel){
        AgentikModel.Ollama -> {
            OllamaStreamingLanguageModel
                .OllamaStreamingLanguageModelBuilder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build()
        }
    }
}
