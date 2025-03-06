package `02-functions`.codeExecutor

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