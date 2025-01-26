package `03-agents`.default

import `01-chat-models`.AgentikModel
import `02-functions`.AgentikTool
import `03-agents`.Agentik
import `03-agents`.AgentikAgent

data class RoleBasedAgentik(
    override val name: String,
    override val description: String,
    val chatModel: AgentikModel,
    val tools: List<AgentikTool> = emptyList(),
) : AgentikAgent {

    fun execute(task: String): String {
        val systemPrompt = ManagedAgentPrompts.ROLE_BASE_AGENT_PROMPT(
            name = name,
            description = description
        )

        val agent = Agentik(
            chatModel = chatModel,
            tools = tools,
            systemPrompt = systemPrompt
        )

        val taskPrompt = buildString {
            appendLine(
                """
                Manager has given you following task:
                ${task}
            """.trimIndent()
            )
        }
        val output = agent.execute(taskPrompt)

        val summary = buildString {
            appendLine("Here is the final answer from your managed agent '$name':")
            append(output)
            appendLine()
            appendLine("For more detail, find below a summary of this agent's work:")
            appendLine("SUMMARY OF WORK FROM AGENT '$name':")
            for (message in agent.messages()) {
                appendLine(message)
            }
            appendLine("END OF SUMMARY OF WORK FROM AGENT '$name'.")
        }

        return summary
    }
}


object ManagedAgentPrompts {
    fun ROLE_BASE_AGENT_PROMPT(
        name: String,
        description: String
    ) = """
You're a helpful agent named '${name}', $description.
You will be provided with a task by your manager. 
your aim is to help your manager solve a wider task: so make sure to not provide a one-line answer, 
but give as much information as possible to give them a clear understanding of the answer.

Your final_answer WILL HAVE to contain these parts:
### 1. Task outcome (short version):
### 2. Task outcome (extremely detailed version):
### 3. Additional context (if relevant):

And even if your task resolution is not successful, 
please return as much context as possible, so that your manager can act upon this feedback.
"""
}