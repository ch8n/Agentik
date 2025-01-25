package agent

import agent.planningAgent.AgentikAgent
import models.AgentikModel

object AgentDefaults {
    val modelName: String = "llama3.2:latest"
    val modelType: AgentikModel = AgentikModel.Ollama
}

data class RoleBasedAgent(
    override val name: String,
    override val description: String,
    val modelType: AgentikModel = AgentDefaults.modelType,
    val modelName: String = AgentDefaults.modelName,
    val tools: List<AgentikTool> = emptyList(),
) : AgentikAgent {

    private val agent = Agentik(
        modelType = modelType,
        modelName = modelName,
        tools = tools
    )

    fun execute(task: String): String {
        val prompt = ManagedAgentPrompts.AGENT_PROMPT(
            name = name, task = task, description = description
        )
        val output = agent.execute(prompt)
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
    fun AGENT_PROMPT(
        name: String,
        task: String,
        description: String
    ) = """
You're a helpful agent named '${name}', $description.
You have been submitted this task by your manager.
---
Task:
${task}
---
You're helping your manager solve a wider task: so make sure to not provide a one-line answer, 
but give as much information as possible to give them a clear understanding of the answer.

Your final_answer WILL HAVE to contain these parts:
### 1. Task outcome (short version):
### 2. Task outcome (extremely detailed version):
### 3. Additional context (if relevant):

And even if your task resolution is not successful, 
please return as much context as possible, so that your manager can act upon this feedback.
"""
}