package `02-functions`

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

suspend fun kokoroTTS(input: String, outFileName: String = "output") {
    // Create a client using the CIO engine
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000  // 30 seconds
            connectTimeoutMillis = 60_000  // Optional: 30s for establishing a connection
            socketTimeoutMillis = 60_000   // Optional: 30s for data transfer
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }


    try {
        // Make the API call
        val response: HttpResponse = client.post("http://localhost:8880/v1/audio/speech") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("Authorization", "Bearer not-needed")  // Replace if an API key is needed
            val requestBody = buildJsonObject {
                put("model", "kokoro")
                //put("voice", "af_bella+af_sky")
                put("voice", "hf_alpha+hf_beta+af_sky")
                put("input", input)  // Inject the input string here
                put("response_format", "mp3")
                put("lang_code", "h")
                put("speed", "1")
            }
            setBody(requestBody)
        }

        // Get the response body as bytes
        val responseBody = response.body<ByteArray>()

        // Write the response to a file
        val outFileName = outFileName.replace(" ", "_")
        File("$outFileName.mp3").writeBytes(responseBody)
        println("Audio saved as $outFileName.mp3")
    } finally {
        client.close()  // Close the client after usage
    }
}


fun main() = runBlocking {
    kokoroTTS("hi chetan kasee ho tum!")
}