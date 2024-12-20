package agent

import dev.langchain4j.model.StreamingResponseHandler
import dev.langchain4j.model.input.Prompt
import dev.langchain4j.model.language.LanguageModel
import dev.langchain4j.model.language.StreamingLanguageModel
import dev.langchain4j.model.ollama.OllamaLanguageModel
import dev.langchain4j.model.ollama.OllamaStreamingLanguageModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*


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

sealed class Response<out T> {
    data class Success<T>(val value: T) : Response<T>()
    data class Error(val error: Throwable) : Response<Nothing>()

    fun onSuccess(action: (value: T) -> Unit) {
        if (this is Success) {
            action.invoke(this.value)
        }
    }

    fun onError(action: (error: Throwable) -> Unit) {
        if (this is Error) {
            action.invoke(this.error)
        }
    }

    companion object {
        fun <T> build(action: () -> T): Response<T> {
            return try {
                Success(action.invoke())
            } catch (e: Throwable) {
                Error(e)
            }
        }
    }
}


data class Agent(
    val model: AgentikModel,
//    val tools: List<AgentikTool>,
//    val debug: Boolean,
//    val systemPrompt: String,
//    val chatHistory: List<String>,
//    val knowledgeBase: List<KnowledgeBase>,
//    val toolCallHistory: List<String>,
) {

    val response = MutableSharedFlow<Response<String>>()

    suspend fun execute(userPrompt: String) {
        when (model) {
            is Ollama -> {
                val modelResponse = Response.build {
                    model.languageModel
                        .generate(Prompt(userPrompt))
                        .content()
                }
                response.emit(modelResponse)
            }
        }
    }

    suspend fun executeStreaming(userPrompt: String) {
        when (model) {
            is Ollama -> {
                callbackFlow {
                    val responseBuilder = StringBuilder()
                    val modelResponse = object : StreamingResponseHandler<String> {
                        override fun onNext(chunk: String) {
                            responseBuilder.append(chunk)
                            trySend(responseBuilder.toString())
                        }

                        override fun onError(error: Throwable?) {
                            if (error != null) throw error
                        }
                    }
                    model.streamingLanguageModel.generate(Prompt(userPrompt), modelResponse)
                    awaitClose {}
                }.catch { error ->
                    response.emit(Response.Error(error))
                }.onEach { _response ->
                    response.emit(Response.Success(_response))
                }.collect()
            }
        }
    }
}

interface AgentikTool

