package data.llmServers

import data.httpClient.httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement


@Serializable
private data class EmbeddingResponse(val embedding: DoubleArray)


object OllamaEmbeddingServer {

    // Calls Ollama embedding API to get a float vector for the input text
    suspend fun getEmbedding(text: String): DoubleArray {
        try {
            val response: HttpResponse = httpClient.post("http://localhost:11434/v1/embed") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToJsonElement(mapOf("text" to text)))
            }
            return Json.decodeFromString<EmbeddingResponse>(response.bodyAsText()).embedding
        } catch (e: Exception) {
            println("Error getting embedding: ${e.message}")
            throw e
        }
    }
}