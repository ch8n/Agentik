package `03-agents`.default

import `01-chat-models`.AgentikModel
import `01-chat-models`.OllamaAgentikModel
import `02-functions`.AgentikTool
import `02-functions`.codeExecutor.KotlinScriptExecutor
import `03-agents`.Agentik
import `03-agents`.AgentikAgent
import `03-agents`.default.planningAgent.CODER_DESCRIPTION
import `03-agents`.default.planningAgent.createAgentsDescriptions


class CodeAgent(
    override val name: String = "Coding Agent",
    override val description: String = CODER_DESCRIPTION,
    private val chatModel: AgentikModel,
    private val tools: List<AgentikTool> = emptyList(),
    private val agents: List<AgentikAgent> = emptyList(),
) : AgentikAgent {

    private val kotlinExecution = KotlinScriptExecutor()

    fun generateCode(taskPrompt: String): String? {
        val systemPrompt = MultiStepAgentPrompts.CODE_SYSTEM_PROMPT(
            managedAgentsDescriptions = createAgentsDescriptions(agents)
        )
        val agent = Agentik(
            tools = tools + agents,
            systemPrompt = systemPrompt,
            chatModel = chatModel
        )
        val llmResponse = agent.execute(taskPrompt)
        println(
            """
            llmResponse:
            $llmResponse
        """.trimIndent()
        )
        val code = extractCodeBlocks(llmResponse)
        return code
    }

    fun fixCode(task: String, code: String?, pastExecutionResult: KotlinScriptExecutor.CommandResult?): String? {
        val fixCodePrompt = buildString {
            appendLine("For task: $task")
            append("Previous generated code $code, encountered following issues:")
            appendLine(pastExecutionResult?.stderr)
            appendLine("fixes these with best of your abilities")
            appendLine("if code appears to be in stuck/wrong or unfixable state, start fresh.")
        }
        return generateCode(fixCodePrompt)
    }

    fun execute(task: String, maxRetry: Int): String {

        val taskPrompt = buildString {
            appendLine("Generate Kotlin programming lang to solve the following task:")
            append(task)
            appendLine("Don't use python programming lang")
        }
        var generatedCode = generateCode(taskPrompt)

        println(
            """
            initCode:
            $generatedCode
        """.trimIndent()
        )

        // Execute code
        var retry = 0
        var executorResult: KotlinScriptExecutor.CommandResult? = kotlinExecution.executeCode(generatedCode)
        var isCodeExecutable = generatedCode != null && executorResult?.stdout?.isNotEmpty() == true

        while (!isCodeExecutable && retry < maxRetry) {
            generatedCode = fixCode(task, generatedCode, executorResult)
            executorResult = kotlinExecution.executeCode(generatedCode)
            isCodeExecutable = generatedCode != null && executorResult?.stdout?.isNotEmpty() == true
            retry += 1
        }

        println(executorResult?.stdout)

        return executorResult?.stdout?: "Failed to respond!"
    }

    private fun extractCodeBlocks(response: String): String? {
        return Regex("```kotlin(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .find(response)
            ?.groupValues?.get(1)
            ?.trim()
    }
}

fun main() {
    val codeAgent = CodeAgent(chatModel = OllamaAgentikModel)
    codeAgent.execute("Calculate 2^3.7384 and round to 2 decimal places",1)
}