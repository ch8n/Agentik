package samples

import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import functions.maths.MathsKtx
import functions.websearch.WebSearchKtx
import memory.sessions.SessionStorage


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

    val assitant = AiServices
        .builder(Assistant::class.java)
        .chatLanguageModel(streamingLanguageModel)
        .chatMemory(SessionStorage(100).l4jChatMemory())
        .tools(MathsKtx(), WebSearchKtx())
        .build()

    val result = assitant.chat("give me 5 best source to learn langchain4j with jetpack compose? share links to sources")
    print(result)
}