package agent

import agent.KotlinScriptExecutor.CommandResult
import agent.planningAgent.AgentikAgent
import agent.planningAgent.createAgentsDescriptions
import dev.langchain4j.agent.tool.Tool
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class FinalAnswer : AgentikTool {

    @Tool("Called to submit final answer")
    fun finalAnswer(value: String): String {
        return value
    }
}


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
    private val tools: List<AgentikTool>,
    private val agents: List<AgentikAgent>
) {

    private val kotlinExecution = KotlinScriptExecutor()


    fun generateCode(task: String): String? {
        val systemPrompt = MultiStepAgentPrompts.CODE_SYSTEM_PROMPT(
            managedAgentsDescriptions = createAgentsDescriptions(agents)
        )
        val agent = Agentik(
            tools = tools,
            systemPrompt = systemPrompt
        )
        val taskPrompt = buildString {
            append(task)
        }
        val llmResponse = agent.execute(taskPrompt)
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

    fun execute(task: String): String {

        var generatedCode = generateCode(task)

        println(
            """
            initCode:
            $generatedCode
        """.trimIndent()
        )

        // Execute code
        var executorResult: CommandResult? = kotlinExecution.executeCode(generatedCode)
        var retry = 0
        val isCodeValid = generatedCode != null && executorResult?.stdout?.isNotEmpty() == false
        while (!isCodeValid && retry < 3) {
            generatedCode = fixCode(task, generatedCode, executorResult)

            println(
                """
            fixed code $retry:
            $generatedCode
        """.trimIndent()
            )
            executorResult = kotlinExecution.executeCode(generatedCode ?: "")

            retry += 1
        }

        println(executorResult?.stdout)

        return ""
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
    codeAgent.execute("Calculate 2^3.7384 and round to 2 decimal places")
}