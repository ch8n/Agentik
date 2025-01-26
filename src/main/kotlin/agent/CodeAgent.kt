package agent

import agent.KotlinScriptExecutor.CommandResult
import agent.planningAgent.AgentikAgent
import agent.planningAgent.CODER_DESCRIPTION
import agent.planningAgent.TASK_PLANNER_DESCRIPTION
import agent.planningAgent.createAgentsDescriptions
import models.AgentikModel
import java.io.File
import java.nio.file.Files
import java.nio.file.Path


class KotlinScriptExecutor {

    fun executeCode(code: String?): CommandResult? {
        code ?: return null
        return try {
            val tempDir: Path = Files.createTempDirectory("kotlin_compile_")
            println("Created temporary directory at: $tempDir")

            val kotlinFile = tempDir.resolve("DynamicProgram.kt").toFile()
            kotlinFile.writeText(code)
            println("Wrote Kotlin code to: ${kotlinFile.absolutePath}")

            val compileCommand =
                listOf("kotlinc", kotlinFile.absolutePath, "-include-runtime", "-d", "DynamicProgram.jar")
            println("Executing compile command: ${compileCommand.joinToString(" ")}")

            val compileResult = executeCommand(compileCommand, tempDir.toFile())

            println("Compiler Output:")
            println(compileResult.stdout)

            if (compileResult.exitCode != 0) {
                println("Compiler Errors:")
                println(compileResult.stderr)
                throw RuntimeException("Compilation failed with exit code ${compileResult.exitCode}")
            }

            // Step 4: Optionally, run the compiled JAR
            val runCommand = listOf("java", "-jar", "DynamicProgram.jar")
            println("Executing run command: ${runCommand.joinToString(" ")}")

            val runResult = executeCommand(runCommand, tempDir.toFile())

            println("Program Output:")
            println(runResult.stdout)

            if (runResult.exitCode != 0) {
                println("Program Errors:")
                println(runResult.stderr)
                throw RuntimeException("Program execution failed with exit code ${runResult.exitCode}")
            }

            // Step 5: Clean up temporary files (optional)
            kotlinFile.delete()
            File(tempDir.toFile(), "DynamicProgram.jar").delete()
            Files.delete(tempDir)
            println("Cleaned up temporary files.")
            runResult
        } catch (e: Exception) {
            println("IO Exception occurred: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Executes a shell command and captures its output.
     *
     * @param command The command and its arguments to execute.
     * @param workingDir The working directory where the command will be executed.
     * @return A CommandResult containing the exit code, standard output, and standard error.
     */
    fun executeCommand(command: List<String>, workingDir: File): CommandResult {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(workingDir)
        processBuilder.redirectErrorStream(false)

        println("Starting process: ${command.joinToString(" ")} in ${workingDir.absolutePath}")

        val process = processBuilder.start()

        // Capture standard output
        val stdout = process.inputStream.bufferedReader().readText()

        // Capture standard error
        val stderr = process.errorStream.bufferedReader().readText()

        // Wait for the process to complete
        val exitCode = process.waitFor()

        return CommandResult(exitCode, stdout, stderr)
    }

    /**
     * Data class to hold the result of a command execution.
     *
     * @property exitCode The exit code of the process.
     * @property stdout The standard output produced by the process.
     * @property stderr The standard error produced by the process.
     */
    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )
}


class CodeAgent(
    override val name: String = "Coding Agent",
    override val description: String = CODER_DESCRIPTION,
    private val modelName: String = "hermes3:3b",
    private val modelType: AgentikModel = AgentikModel.Ollama,
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
            modelName = modelName,
            modelType = modelType
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

    fun fixCode(task: String, code: String?, pastExecutionResult: CommandResult?): String? {
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
        var executorResult: CommandResult? = kotlinExecution.executeCode(generatedCode)
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
    val codeAgent = CodeAgent(tools = emptyList(), agents = emptyList())
    codeAgent.execute("Calculate 2^3.7384 and round to 2 decimal places",1)
}