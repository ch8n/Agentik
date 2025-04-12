package samples

import `01-chat-models`.OllamaAgentikModel
import `03-agents`.Agentik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

private fun main() : Unit = runBlocking {

    val tutorPromptFile = File("src/main/kotlin/prompts/socratic_tutor_prompt.md")
    if (!tutorPromptFile.exists()) IllegalStateException("Prompt file not found! at ${tutorPromptFile.path}")
    val agent = Agentik(
        systemPrompt = tutorPromptFile.readText(),
        chatModel = OllamaAgentikModel,
        isStreaming = true
    )

    val userPrompt = """
        I'd like to learn about Kotlin programming language.
    """.trimIndent()

    val mutableState = MutableStateFlow(StringBuilder())

    launch(Dispatchers.IO) {
        agent.executeStreaming(userPrompt)
            .onEach { mutableState.update { it.append(it) } }
            .launchIn(this)
    }

    mutableState.collect {
        println(it.toString())
    }
}