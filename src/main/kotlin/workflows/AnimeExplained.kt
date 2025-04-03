package workflows

/**
 * 1. convert video to screenshot of every 4 seconds
 * 2. extract subtitle from each screenshot us vllm
 * 3. using subtitle write a prompt to convert it to anime explained script
 * 4. using script ask vllm which frame will be best suited for each line from screenshots
 * 5. use text to speech to convert script to audio
 * 6. merge screenshots to make video
 * 7. merge audio to attach voice over
 */

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import data.httpClient.httpClient
import data.jsonClient.jsonClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

const val VISION_LLM = "llava-phi3:latest"
const val CHAT_LLM = "MHKetbi/Unsloth-Phi-4-mini-instruct:q8_0"

fun extractScreenshots(inputVideo: String, outputDir: String, frames: Float) {
    // Ensure the output directory exists
    val outputDirectory = File(outputDir)
    if (!outputDirectory.exists()) {
        outputDirectory.mkdirs()
    }

    // FFmpeg command to extract frames every 4 seconds
    val command = listOf(
        "ffmpeg",
        "-i", inputVideo,              // Input video file
        "-vf", "fps=1/$frames",        // Extract 1 frame every 4 seconds
        "$outputDir/output%d.png",    // Output file pattern
        "-hide_banner",               // Suppress FFmpeg banner
        "-loglevel", "error"          // Show only errors
    )

    try {
        // Execute the FFmpeg command
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true) // Merge error and output streams
        val process = processBuilder.start()

        // Read the output (optional, for debugging)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            println("Screenshots extracted successfully to $outputDir")
        } else {
            println("FFmpeg failed with output: $output")
        }
    } catch (e: IOException) {
        println("Error executing FFmpeg: ${e.message}")
    } catch (e: InterruptedException) {
        println("Process interrupted: ${e.message}")
    }
}

fun listSortedImages(outputDir: String): List<File> {
    val directory = File(outputDir)
    if (!directory.exists() || !directory.isDirectory) {
        println("Directory $outputDir does not exist or is not a directory.")
        return emptyList()
    }

    // Get list of PNG files and sort by last modified time
    val imageFiles = directory.listFiles { file ->
        file.isFile && file.extension.lowercase() == "png"
    }?.sortedBy { it.lastModified() }

    if (imageFiles.isNullOrEmpty()) {
        println("No PNG images found in $outputDir")
        return emptyList()
    }

    // Format for displaying timestamps
    println("Images in $outputDir sorted by creation (last modified) time:")
    return imageFiles
}

// Structured response format
@Serializable
data class ImageResponse(
    val imageDescription: String,
    val subTitle: String
)

@Serializable
data class StoryImageResponse(
    val imageFilePath: String,
    val imageResponse: ImageResponse
)

// Request payload for Ollama API
@Serializable
data class ChatMessage(
    val role: String = "user",
    val content: String,
    val images: List<String>? = null, // Base64-encoded images
)

@Serializable
data class ChatResponse(
    val model: String? = null,
    val created_at: String? = null,
    val message: MessageContent? = null,
    val done: Boolean? = null,
    val error: String? = null
)

@Serializable
data class MessageContent(
    val role: String? = null,
    val content: String
)

// Function to encode image file to base64
fun encodeImageToBase64(file: File): String {
    val bytes = file.readBytes()
    return Base64.getEncoder().encodeToString(bytes)
}

suspend fun processImage(imageFile: File): ImageResponse {
    val base64Image = encodeImageToBase64(imageFile)

    val prompt = """
        We are analyzing an anime episode frame by frame.
        
        We have provided you with the next frame. Your task is to analyze the image and provide a structured response with the following information:
        1. "imageDescription": A detailed description of the image.
        2. "subTitle": extract overlay text or subtitle text if visible in the screen.
        
        # Strict guidelines you need to follow
        - Please return the response in JSON format like this: 
        {
            "imageDescription": String, 
            "subTitle": String
        }
        - don't repeat context from previous frames.
        - extract overlay text or subtitle with best of your capabilities.
        - if you don't obey these guideline you will be fined $1000000.
    """.trimIndent()

    fun generateImageResponseSchema(): JsonElement = buildJsonObject {
        put("${'$'}schema", "http://json-schema.org/draft-07/schema#")
        put("title", "ImageResponse")
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("imageDescription") {
                put("type", "string")
                put("description", "A detailed description of the image")
            }
            putJsonObject("subTitle") {
                put("type", "string")
                put("description", "extract subtitle text if visible in the screen")
            }
        }
        putJsonArray("description") {
            add("imageDescription")
            add("subTitle")
        }
        put("additionalProperties", false)
    }

    val requestBody = buildJsonObject {
        put("model", VISION_LLM)
        put("stream", false)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                put("content", prompt)
                putJsonArray("images") { add(base64Image) }
            }
        }
        put("format", generateImageResponseSchema())
    }

    val response = httpClient.post("http://localhost:11434/api/chat") {
        contentType(ContentType.Application.Json)
        setBody(requestBody)
    }

    val rawResponse = response.bodyAsText()
    println("Raw response: $rawResponse")

    val chatResponse = jsonClient.decodeFromString<ChatResponse>(rawResponse)
    return if (chatResponse.message != null) {
        jsonClient.decodeFromString<ImageResponse>(chatResponse.message.content)
    } else if (chatResponse.error != null) {
        throw Exception("API error: ${chatResponse.error}")
    } else {
        throw Exception("No message field in response: $rawResponse")
    }
}

@Serializable
data class VoiceOverResponse(
    val sentences: List<VoiceOverSentence>
)

@Serializable
data class VoiceOverSentence(
    val voiceOverSentence: String,
    val voiceOverBackgroundImage: String
)

suspend fun processVoiceOverScript(prompt: String): VoiceOverResponse {

    println(
        """
        processing prompt :
        $prompt
        
        =======
        prompt word count ${prompt.split(" ").count()}
    """.trimIndent()
    )


    fun generateVoiceOverResponseSchema(): JsonElement = buildJsonObject {
        put("\$schema", "http://json-schema.org/draft-07/schema#")
        put("title", "VoiceOverResponse")
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("sentences") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("voiceOverSentence") {
                            put("type", "string")
                            put("description", "The text of the voice-over sentence")
                        }
                        putJsonObject("voiceOverBackgroundImage") {
                            put("type", "string")
                            put(
                                "description",
                                "The URL or path to the background image associated with the voice-over sentence"
                            )
                        }
                    }
                    putJsonArray("required") {
                        add("voiceOverSentence")
                        add("voiceOverBackgroundImage")
                    }
                    put("additionalProperties", false)
                }
            }
        }
        putJsonArray("required") {
            add("sentences")
        }
        put("additionalProperties", false)
    }

    val requestBody = buildJsonObject {
        //put("model", "MHKetbi/Unsloth-Phi-4-mini-instruct:q8_0")
        put("model", CHAT_LLM)
        put("stream", false)
        putJsonObject("options") {
            put("num_ctx", 8091)
        }
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                put("content", prompt)
            }
        }
        put("format", generateVoiceOverResponseSchema())
    }

    val response = httpClient.post("http://localhost:11434/api/chat") {
        contentType(ContentType.Application.Json)
        setBody(requestBody)
    }

    val rawResponse = response.bodyAsText()
    println("Raw response: $rawResponse")

    val chatResponse = jsonClient.decodeFromString<ChatResponse>(rawResponse)
    return if (chatResponse.message != null) {
        jsonClient.decodeFromString<VoiceOverResponse>(chatResponse.message.content)
    } else if (chatResponse.error != null) {
        throw Exception("API error: ${chatResponse.error}")
    } else {
        throw Exception("No message field in response: $rawResponse")
    }
}


fun refreshVoiceOver(coroutineScope: CoroutineScope): Unit = runBlocking {
//    val videoPath = "cache/video/videoplayback.mp4"
    val outputDirectory = "cache/output" // Set the output directory path
//    extractScreenshots(videoPath, outputDirectory, frames = 2f)
//    val imagesFiles = listSortedImages(outputDirectory)
//    """
//        [{
//            "imageDescription":String,
//            "subTitle":String,
//        }]
//    """.trimIndent()
//
//    val storyFrames = mutableListOf<StoryImageResponse>()
    val storylineFile = File("$outputDirectory/storyline.json")
//    imagesFiles.map { file ->
//        coroutineScope.async(Dispatchers.IO) {
//            println("Processing ${file.name}")
//            val imageResponse = processImage(file)
//            storyFrames.add(
//                StoryImageResponse(
//                    imageFilePath = file.absolutePath,
//                    imageResponse = imageResponse
//                )
//            )
//            val storyline = jsonClient.encodeToString(storyFrames)
//            storylineFile.writeText(storyline)
//        }
//    }.awaitAll()

    val storylineJson = storylineFile.readText()
    val storylines = jsonClient.decodeFromString<List<StoryImageResponse>>(storylineJson)

    val episodeSynopsis = """
       a transformed Garfiel (blond hair character) continues to fight Kurgan (blue skin character), 
       but is overwhelmed by the warrior, being further distracted by his visions of Elsa. 
       Knocked into the catacombs, Garfiel recalls a conversation with Wilhelm about Kurgan's life in the Vollachian Empire, 
       and how he only wields all his Devil Cleavers to opponents he respects, which Garfiel intends to make him do. 
       Their battle leads them into a shelter, where his half-siblings are, and he overcomes the Elsa illusions after defeating a loose demon.
       The civilians proceed to cheer on Garfiel to succeed, giving him confidence and gaining Kurgan's respect, who unleashes all his Cleavers. 
       After the two brutally beat each other, Kurgan briefly regains his sense of self to tell Garfiel "Well done" as he crumbles away.   
    """.trimIndent()

    val frameByFrameDescriptionAndSubtitle = storylines.joinToString("\n") {
        """
            frame file path:${it.imageFilePath}
            imageDescription:${it.imageResponse.imageDescription}
            subtitle:${it.imageResponse.subTitle}
        """.trimIndent()
    }

    val animeName = "Re:Zero season 3"
    val episodeNumber = "Season 3 Episode 13"

    val animeExplainedPrompt = """
        Your task is to write a voice-over script for an anime episode. Below are the details you need to work with:

        **Anime Details:**
        - **Anime Name:** ${animeName}
        - **Episode Number:** ${episodeNumber}

        **Episode Synopsis:**
        - **Summary:** ${episodeSynopsis}

        **Voice-over Script Guidelines:**
        - The voice-over script should detailed and each sentence should be approximately around **2,000 words**.
        - Write it as if it's a **voice-over** narrating the episode.
        - **Voice-over Style:** Casual with fun and sarcastic comments.
        - **Tone:** The tone should be **light-hearted and engaging**, with a touch of sarcasm for humor.
        - **Pacing & Speed:** Keep a **steady pace**, ensuring **clarity** and a **natural flow** of speech.
        - **Pronunciation & Emphasis:** Emphasize the **climactic moments** and key events, building up to them with appropriate **tone shifts**.

        **Video Context:**
        - Interpret the episode based on the **frame-by-frame descriptions** and **subtitles** provided.
        - Ensure the voice-over script matches the **mood and energy** of the episode, and highlight the **climactic moments** for extra impact.
        - Make sure the voice-over flows naturally with the events, pacing, and tone of the video.

        **Frame-by-Frame Description & Subtitle:**
        - ${frameByFrameDescriptionAndSubtitle}

        **Additional Task:**
        - Pick the **frame file path** from the descriptions provided that best matches the mood or moment being voice-overed. This should be used as the background image when that specific sentence is being read.
        - If you successfully follow instruction you will earn 1000000$ to feed your poor family.
    """.trimIndent()

    val voiceOverResponse = processVoiceOverScript(animeExplainedPrompt)

    val voiceOverFile = File("$outputDirectory/voiceOverJson.json")
    val voiceOverJson = jsonClient.encodeToString(voiceOverResponse)
    voiceOverFile.writeText(voiceOverJson)
}


@Composable
@Preview
fun AnimeExplainedApp() {
    val scope = rememberCoroutineScope()
    MaterialTheme {
        Column {
            var voiceOverResponse by remember { mutableStateOf(VoiceOverResponse(emptyList())) }

            Row {
                Button(onClick = {
                    val outputDirectory = "cache/output"
                    val voiceOverFile = File("$outputDirectory/voiceOverJson.json")
                    val voiceOverJson = voiceOverFile.readText()
                    voiceOverResponse = jsonClient.decodeFromString(voiceOverJson)
                }) {
                    Text("Load VoiceOver")
                }
                Spacer(Modifier.size(20.dp))

                Button(onClick = {
                    refreshVoiceOver(scope)
                    val outputDirectory = "cache/output"
                    val voiceOverFile = File("$outputDirectory/voiceOverJson.json")
                    val voiceOverJson = voiceOverFile.readText()
                    voiceOverResponse = jsonClient.decodeFromString(voiceOverJson)
                }) {
                    Text("refresh VoiceOver")
                }
            }

            HorizontalPagerScreen(voiceOverResponse)


        }
    }
}

fun loadImageFromFile(path: String): ImageBitmap? {
    return try {
        val file = File(path)
        if (file.exists()) {
            val bufferedImage: BufferedImage = ImageIO.read(file)
            bufferedImage.toComposeImageBitmap()
        } else {
            println("File not found: $path")
            null
        }
    } catch (e: Exception) {
        println("Error loading image from $path: ${e.message}")
        null
    }
}

@Composable
fun HorizontalPagerScreen(pages: VoiceOverResponse) {
    if (pages.sentences.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pages.sentences.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Load and display image
                val imageBitmap =
                    loadImageFromFile(".${pages.sentences[page].voiceOverBackgroundImage}")
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Page ${page + 1} Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)// Adjust size as needed
                            .padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        text = "Image not found: /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/${pages.sentences[page].voiceOverBackgroundImage}",
                        color = Color.Red
                    )
                }

                // Display text
                Text(
                    text = pages.sentences[page].voiceOverSentence,
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        // Next Button
        Button(
            onClick = {
                coroutineScope.launch {
                    val nextPage = (pagerState.currentPage + 1) % pages.sentences.size
                    pagerState.animateScrollToPage(nextPage)
                }
            },
            modifier = Modifier
                .padding(16.dp)
        ) {
            Text("Next")
        }
    }
}