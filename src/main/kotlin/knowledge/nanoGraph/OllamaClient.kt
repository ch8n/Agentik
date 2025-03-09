package knowledge.nanoGraph

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

data class Message(val role: String, val content: String)

class OllamaClient(private val baseUrl: String = "http://localhost:11434") {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun generateCompletion(
        model: String,
        prompt: String,
        systemPrompt: String? = null,
        historyMessages: List<Message> = emptyList(),
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): String {
        val messages = mutableListOf<Message>()
        systemPrompt?.let { messages.add(Message("system", it)) }
        messages.addAll(historyMessages)
        messages.add(Message("user", prompt))

        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(JsonElement.serializer(), buildJsonObject {
                put("model", model)
                put("messages", JsonArray(messages.map { msg ->
                    buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                }))
                put("temperature", temperature)
                maxTokens?.let { put("max_tokens", it) }
            }))
        }
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return json["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw Exception("No response from Ollama")
    }

    suspend fun generateEmbeddings(model: String, texts: List<String>): List<List<Float>> {
        val response = client.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(JsonElement.serializer(), buildJsonObject {
                put("model", model)
                put("texts", JsonArray(texts.map { JsonPrimitive(it) }))
            }))
        }
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return json["embeddings"]?.jsonArray?.map { arr ->
            arr.jsonArray.map { it.jsonPrimitive.float }
        } ?: throw Exception("No embeddings from Ollama")
    }

    fun close() {
        client.close()
    }
}