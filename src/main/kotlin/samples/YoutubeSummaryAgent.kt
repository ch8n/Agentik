package samples

import kotlinx.coroutines.runBlocking
import `01-chat-models`.AgentikModel
import `01-chat-models`.OllamaAgentikModel
import `02-functions`.AgentikTool
import `02-functions`.youtube.YoutubeKtx
import `03-agents`.Agentik


class YoutubeAgent(
    val chatModel: AgentikModel,
    val tools: List<AgentikTool> = emptyList()
) {
    fun execute(prompt: String): String? {
        val summarizePrompt = """
            You are helpful assistant who summarizes youtube videos. 
            User will provide youtube video url, and you will extract english transcription from the video.
            Analyze the english transcription of the provided YouTube video and summarize it comprehensively. 
            Extract the following details:

            1. **Summary**: Provide a brief summary of the video's content, highlighting the main topic and key ideas discussed.
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
            chatModel = chatModel,
            tools = listOf(YoutubeKtx()) + tools
        )
        return agent.execute(prompt)
    }
}


// Usage Example
fun main() = runBlocking {
    val youtubeAgent = YoutubeAgent(chatModel = OllamaAgentikModel)

    try {
        val result = youtubeAgent.execute("""
            https://www.youtube.com/watch?v=NL1FREvENw4&ab_channel=AICodeKing
        """.trimIndent())
        println("Youtube Video Summary:\n$result")
    } catch (e: Exception) {
        println("Agent failed: ${e.message}")
    }
}