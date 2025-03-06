package samples

import `01-chat-models`.OllamaAgentikModel
import `03-agents`.default.RoleBasedAgentik

fun main() {
    val qaAgent = RoleBasedAgentik(
        name = "Dharma",
        description = """
            You are Dharma, expert Quality Assurance Engineer, 
            your are expert in writing unit test cases for a given piece of Kotlin code.
        """.trimIndent(),
        chatModel = OllamaAgentikModel,
    )

    val result = qaAgent.execute("""
        Write testcases for the following code:
        fun greet(name: String?) {
            println("hello ${'$'}name)
        }
    """.trimIndent())

    println(result)
}