package samples

import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import `02-functions`.maths.MathsKtx
import `02-functions`.websearch.WebSearchKtx
import memory.sessions.SessionStorage


enum class Agent {
    Math,
    Search
}

data class Work(val agent: Agent, val step: String)

interface Assistant {

    @SystemMessage("you are helpful assistant")
    fun chat(prompt: String): String
}

fun main() {
    val modelName = "llama3.2:latest"
    val baseUrl = "http://localhost:11434"

    val streamingLanguageModel = OllamaChatModel
        .OllamaChatModelBuilder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()

    val mathsAgent = AiServices
        .builder(Assistant::class.java)
        .chatLanguageModel(streamingLanguageModel)
        .chatMemory(SessionStorage(100).l4jChatMemory())
        .tools(MathsKtx())
        .build()

    val webAgent = AiServices
        .builder(Assistant::class.java)
        .chatLanguageModel(streamingLanguageModel)
        .chatMemory(SessionStorage(100).l4jChatMemory())
        .tools(WebSearchKtx())
        .build()
}