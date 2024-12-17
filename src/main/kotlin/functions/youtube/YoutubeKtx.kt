package functions.youtube

import dev.langchain4j.agent.tool.Tool
import functions.webscaper.JsoupKtx
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

fun main() {
    val transcript =
        YoutubeKtx().englishTranscriptionContent("https://www.youtube.com/watch?v=4cCwuBsqfTI&ab_channel=PhilippLackner")
    println(transcript)
}

class YoutubeKtx {

    @Tool("Returns english transcription of youtube video url")
    fun englishTranscriptionUrl(youtubeUrl: String) = transcriptionUrls(youtubeUrl)
        .firstOrNull { it.contains("lang=en") }

    @Tool("Returns english transcription of youtube video url")
    fun englishTranscriptionContent(youtubeUrl: String) = transcriptionUrls(youtubeUrl)
        .firstOrNull { it.contains("lang=en") }
        ?.let { url -> JsoupKtx().scrape(url)?.text() }

    fun transcriptionUrls(youtubeUrl: String): List<String> {
        val videoId = extractVideoId(youtubeUrl) ?: return emptyList()
        val url = "https://youtu.be/$videoId"
        val responseString = makeGetRequest(url) ?: return emptyList()
        return extractBaseUrls(responseString)
    }

    fun extractVideoId(youtubeUrl: String): String? {
        // Regular expressions for different common YouTube URL formats
        val regexPatterns = listOf(
            "youtube\\.com/watch\\?v=([\\w-]{11})",  // Matches https://www.youtube.com/watch?v=VIDEO_ID
            "youtu\\.be/([\\w-]{11})",               // Matches https://youtu.be/VIDEO_ID
            "youtube\\.com/embed/([\\w-]{11})",      // Matches https://www.youtube.com/embed/VIDEO_ID
            "youtube\\.com/v/([\\w-]{11})"           // Matches https://www.youtube.com/v/VIDEO_ID
        )

        for (pattern in regexPatterns) {
            val regex = Regex(pattern)
            val matchResult = regex.find(youtubeUrl)
            if (matchResult != null) {
                // Return the first captured group which is the videoId
                return matchResult.groupValues[1]
            }
        }

        // Return null if no videoId is found
        return null
    }

    fun makeGetRequest(url: String): String? {
        // Create an OkHttpClient instance
        val client = OkHttpClient()

        // Build the Request
        val request = Request.Builder()
            .url(url)
            .build()

        // Execute the request
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code $response")
        }

        // Print response details
        println("Response code: ${response.code}")

        return response.body?.string()
    }

    fun extractBaseUrls(text: String): List<String> {
        // Regular expression to match baseUrl links
        val regexPattern = """\"baseUrl\":\"(https://www\.youtube\.com/api/timedtext\?[^"]+)\""""
        val regex = Regex(regexPattern)

        // Extract matches
        return regex.findAll(text)
            .map { it.groupValues[1].replace("\\u0026", "&") }
            .distinct()
            .toList()
    }

}