package models

import dev.langchain4j.model.language.LanguageModel
import dev.langchain4j.model.language.StreamingLanguageModel
import dev.langchain4j.model.ollama.OllamaLanguageModel
import dev.langchain4j.model.ollama.OllamaStreamingLanguageModel

sealed class AgentikModel

data class Ollama(
    val modelName: String,
    val baseUrl: String = "http://localhost:11434",
) : AgentikModel() {

    val languageModel: LanguageModel = OllamaLanguageModel
        .OllamaLanguageModelBuilder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()

    val streamingLanguageModel: StreamingLanguageModel = OllamaStreamingLanguageModel
        .OllamaStreamingLanguageModelBuilder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()
}
