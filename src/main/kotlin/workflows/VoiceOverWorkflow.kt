package workflows

import `01-chat-models`.OllamaAgentikModel
import `02-functions`.kokoroTTS
import `02-functions`.youtube.YoutubeKtx
import `03-agents`.Agentik
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI

import kotlin.io.readText


fun getTranscriptionFromCacheOrRemote(videoUrl: String): String {
    val youtubeKtx = YoutubeKtx()
    val videoId = youtubeKtx.extractVideoId(videoUrl) ?: return ""
    val file = File("$videoId.txt")
    if (!file.exists()) file.createNewFile()
    val content = file.readText()
    if (content.isEmpty()) {
        val transcription = YoutubeKtx().englishTranscriptionContent(videoUrl) ?: ""
        file.writeText(transcription)
        return transcription
    }
    return content
}

suspend fun voiceOverWorkflow(
    videoUrl: String,
    outputFileName: String
) {
    val transcription = getTranscriptionFromCacheOrRemote(videoUrl)
    println("""
        transcription
        $transcription
        ==============
    """.trimIndent())

    val systemPrompt = """
        Convert the following video transcript into a voiceover.
        - Voiceover Style: Casual and friendly, with a fun but sarcastic comments.
        - Voice Characteristics: Female voice.
        - Tone: The tone should be light-hearted and engaging, but with a touch of sarcasm to add some humor.
        - Pacing & Speed: Keep a steady pace, ensuring clarity and natural flow of speech.
        - Accent & Language: Use Indian English accent. The pronunciation should follow Indian English norms.
        - Pronunciation & Emphasis: Emphasize the climax or key moments in the transcript, building up to them with appropriate tone shifts.
        - Background Music: No background noise or music, the focus should be solely on the voiceover.
        - Video Context: Interpret the context of the video from the transcript and ensure the voiceover flows naturally with the events and tone of the video.
        Please make sure that the voiceover accurately matches the mood and energy of the transcript while highlighting the climax moments for extra impact.
    """.trimIndent()

    val agent = Agentik(
        systemPrompt = systemPrompt,
        chatModel = OllamaAgentikModel,
        tools = emptyList()
    )

    val userPrompt = """
        Create voice over from the following transcription:
        $transcription
        under 200 words, just return voice over content nothing else.
    """.trimIndent()

    val voiceOverScript = agent.execute(userPrompt)


    println("""
        voiceOverScript
        $voiceOverScript
        ==============
    """.trimIndent())

    kokoroTTS(voiceOverScript, outputFileName)
}


fun main() = runBlocking {
    voiceOverWorkflow(
        videoUrl = "https://www.youtube.com/watch?v=CUmGaesHng0&ab_channel=Anim3Senpai",
        outputFileName = "Top 10 Anime Where MC is Overpowered but Pretends to be Weak"
    )
}