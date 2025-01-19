package workflows

import agent.Agentik
import dev.langchain4j.model.chat.ChatLanguageModel
import functions.youtube.YoutubeKtx
import kotlinx.coroutines.runBlocking
import models.AgentikModel
import models.chatLanguageModel


class YoutubeAgent(
    private val model: AgentikModel = AgentikModel.Ollama,
    private val modelName: String = "qwen2.5-coder:7b-instruct-q4_K_M",
) {
    fun execute(prompt: String): String? {
        val summarizePrompt = """
            You are helpful assistant who summarizes youtube videos. 
            User will provide youtube video url, and you will extract english transcription from the video.
            Analyze the english transcription of the provided YouTube video and summarize it comprehensively. 
            Extract the following details:

            1. **Summary**: Provide a concise summary of the video's content, highlighting the main topic and key ideas discussed.
            2. **Key Points**: List the most important takeaways or conclusions presented in the video.
            3. **Link**: Extract the video URL.
            4. **Resource Name**: Identify the name or title of the resource mentioned (e.g., the YouTube channel name, course, book, or tool).
            5. **Techniques Mentioned/Used**: List any specific techniques, methodologies, or practices discussed in the video.
            6. **Technologies/Tools**: Identify any technologies, programming languages, frameworks, or tools referenced in the video.
            7. **Additional Resources**: Include links or references to additional materials mentioned in the video (e.g., blogs, documentation, GitHub repositories).

            Ensure the extracted information is clear, structured, and easy to understand.
        """.trimIndent()

        val agent = Agentik(
            systemPrompt = summarizePrompt,
            model = model,
            modelName = modelName,
            tools = listOf(YoutubeKtx()),
        )

        return agent.execute(prompt)
    }
}


// Usage Example
fun main() = runBlocking {
    val youtubeAgent = YoutubeAgent()

    try {
        val result = youtubeAgent.execute("""
            https://www.youtube.com/watch?v=8GlHhwtgjKY&list=PL6r74Q3sfWBk2HY3etLKNCqzOMF6PVSWI&index=3&ab_channel=StephFrance
        """.trimIndent())
        println("Youtube Video Summary:\n$result")
    } catch (e: Exception) {
        println("Agent failed: ${e.message}")
    }
}