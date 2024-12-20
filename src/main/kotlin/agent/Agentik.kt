package agent

import dev.langchain4j.model.StreamingResponseHandler
import dev.langchain4j.model.input.Prompt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import models.AgentikModel
import models.Ollama
import utils.Response


data class Agentik(
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

