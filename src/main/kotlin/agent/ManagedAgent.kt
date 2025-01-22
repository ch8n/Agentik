package agent

import models.AgentikModel
import opennlp.tools.util.model.ModelType

class MultiStepAgent

object AgentDefaults {
    val modelName: String = "llama3.2:latest"
    val modelType: AgentikModel = AgentikModel.Ollama
}

data class ManagedAgent(
    val agentHumanName: String,
    val additionalPrompting: String? = null,
    val modelType: AgentikModel = AgentDefaults.modelType,
    val modelName: String = AgentDefaults.modelName,
    val tools : List<AgentikTool> = emptyList()
) {
    private val agent = Agentik(
        modelType = modelType,
        modelName = modelName,
        tools = tools
    )

    fun getTaskSummary(request: String): String {
        val output = execute(request)
        val summary = buildString {
            appendLine("Here is the final answer from your managed agent '$agentHumanName':")
            append(output)
            appendLine()
            appendLine("For more detail, find below a summary of this agent's work:")
            appendLine("SUMMARY OF WORK FROM AGENT '$agentHumanName':")
            for (message in agent.messages()) {
                appendLine(message)
            }
            appendLine("END OF SUMMARY OF WORK FROM AGENT '$agentHumanName'.")
        }
        return summary
    }

    fun execute(request: String): String {
        val prompt = ManagedAgentPrompts.MANAGED_AGENT_PROMPT(
            name = agentHumanName,
            task = request,
            additionalPrompting = additionalPrompting ?: ""
        )
        return agent.execute(prompt)
    }
}


object ManagedAgentPrompts {
    fun MANAGED_AGENT_PROMPT(
        name: String,
        task: String,
        additionalPrompting: String
    ) = """
You're a helpful agent named '${name}'.
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

Put all these in your final_answer tool, 
everything that you do not pass as an argument to final_answer will be lost.
And even if your task resolution is not successful, 
please return as much context as possible, so that your manager can act upon this feedback.
$additionalPrompting"""
}