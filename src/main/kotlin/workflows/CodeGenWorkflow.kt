package workflows

import dev.langchain4j.model.chat.ChatLanguageModel
import kotlinx.coroutines.runBlocking
import models.chatLanguageModel
import java.io.File


class ManagerAgent(
    private val chatModel: ChatLanguageModel
) {
    fun initiateCodeGeneration(requirement: String): String {
        val prompt = """
            As a project manager, help generate Kotlin code for the following requirement:
            $requirement
            
            Break down the requirement and provide clear guidance for code generation.
            You provide requirement only don't generate the code.
        """.trimIndent()

        return chatModel.generate(prompt)
    }

    fun requestClarification(doubt: String): String {
        val clarificationPrompt = """
            Clarify the following doubt about code generation:
            $doubt
            
            Provide detailed explanation and guidance.
        """.trimIndent()

        return chatModel.generate(clarificationPrompt)
    }
}

class CodeGenerationAgent(
    private val chatModel: ChatLanguageModel
) {
    fun generateCode(requirement: String): String {
        val codeGenerationPrompt = """
            You are Senior Software Engineer, expert in Kotlin
            Generate Kotlin code for the following requirement:
            $requirement
            
            Provide clean, efficient, and well-structured code.
            Don't Add explanation just the code 
        """.trimIndent()

        return chatModel.generate(codeGenerationPrompt)
    }

    fun improveCode(existingCode: String, failureReason: String): String {
        val improveCodePrompt = """
            Improve the following Kotlin code based on test failures:
            $existingCode
            
            Failure Reason: $failureReason
            
            Refactor the code to address the issues.
        """.trimIndent()

        return chatModel.generate(improveCodePrompt)
    }
}

class TestGenerationAgent(
    private val chatModel: ChatLanguageModel
) {
    fun generateTestCases(code: String): String {
        val testCasePrompt = """
            You are Senior Software Engineer, expert in Kotlin and QA.
            Generate comprehensive inline test cases for the following Kotlin code:
            $code
            
            Don't Add explanation just the code, any explanation or note should be in comment
           
            
            
            fun main() {
                // Generate inline testcases below
            }
        """.trimIndent()

        val testCasesString = chatModel.generate(testCasePrompt)
        return testCasesString
    }
}

class CodeExecutionAgent {

    
}

// Main Code Generation and Testing Workflow
class CodeGenerationWorkflow(
    private val managerAgent: ManagerAgent,
    private val codeGenerationAgent: CodeGenerationAgent,
    private val testGenerationAgent: TestGenerationAgent,
    private val codeExecutionAgent: CodeExecutionAgent
) {
    fun generateAndTestCode(requirement: String, maxAttempts: Int = 3): String {
        var currentCode = ""
        var attempts = 0

        // Manager Agent initiates code generation
        val managerGuidance = managerAgent.initiateCodeGeneration(requirement)

        // Code Generation Agent generates code
        currentCode = codeGenerationAgent.generateCode(managerGuidance)

        // Test Generation Agent creates test cases
        val codeWithTestCases = testGenerationAgent.generateTestCases(currentCode)

        val codeFile = File("code.kts")
        codeFile.delete()
        codeFile.createNewFile()
        codeFile.writeText(currentCode)

        val codeFileTestCase = File("codeTestCase.kts")
        codeFileTestCase.delete()
        codeFileTestCase.createNewFile()
        codeFileTestCase.writeText(codeWithTestCases)

        return ""

        while (attempts < maxAttempts) {
            // Manager Agent initiates code generation
            val managerGuidance = managerAgent.initiateCodeGeneration(requirement)

            // Code Generation Agent generates code
            currentCode = codeGenerationAgent.generateCode(managerGuidance)

            // Test Generation Agent creates test cases
            val codeWithTestCases = testGenerationAgent.generateTestCases(currentCode)

            val codeFile = File("code.kts")
            codeFile.createNewFile()
            codeFile.writeText(currentCode)

            val codeFileTestCase = File("codeTestCase.kts")
            codeFile.createNewFile()
            codeFile.writeText(codeWithTestCases)

            // Code Execution Agent runs test cases
            //val testResult = codeExecutionAgent.runTestCases(currentCode, codeWithTestCases)

//            if (testResult) {
//                println("Code generation successful!")
//                return currentCode
//            } else {
//                println("Test cases failed. Attempting to improve code...")
//                currentCode = codeGenerationAgent.improveCode(currentCode, "Test cases failed")
//                attempts++
//            }
        }

        throw Exception("Unable to generate working code after $maxAttempts attempts")
    }
}

// Usage Example
fun main() = runBlocking {
    val ollamaModel = chatLanguageModel(modelName = "phi3.5:3.8b-mini-instruct-q4_K_M")

    val workflow = CodeGenerationWorkflow(
        ManagerAgent(ollamaModel),
        CodeGenerationAgent(ollamaModel),
        TestGenerationAgent(ollamaModel),
        CodeExecutionAgent()
    )

    try {
        val generatedCode = workflow.generateAndTestCode(
            "Create a function to calculate the factorial of a number"
        )
        println("Generated Code:\n$generatedCode")
    } catch (e: Exception) {
        println("Code generation failed: ${e.message}")
    }
}