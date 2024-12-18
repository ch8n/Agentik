package agent

import dev.langchain4j.model.language.LanguageModel

data class Agent(
    val model: LanguageModel,
    val tools: List<AgentikTool>,
    val debug: Boolean,
    val systemPrompt: String
) {

    fun execute(userPrompt: String) {

    }

    fun executeStreaming(userPrompt: String) {

    }
}

interface AgentikTool

