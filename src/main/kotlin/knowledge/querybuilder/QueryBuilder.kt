package knowledge.querybuilder

import `01-chat-models`.OllamaAgentikModel
import `02-functions`.extractors.extractJsonFromMarkdown
import `03-agents`.Agentik
import knowledge.code.jsonClient

class QueryBuilder {
    private val systemPrompt = """
        Given a user query, break it down into multiple **intents** in a structured and ordered way to ensure successful query completion.  

        ### **Decomposition Guidelines:**  
        1. **Identify Core Intents:** Extract all distinct **actions** or **goals** within the user query.  
        2. **Determine Dependencies:** Establish relationships between intents, such as whether one intent depends on the result of another.  
        3. **Handle Nested Intents:** If an intent is a subtask of another, capture this hierarchical relationship.  
        4. **Order Execution:** Arrange the intents in a logical sequence, ensuring that dependent intents are executed in the correct order.  
        5. **Optimize for Efficiency:** Group related intents where possible to minimize redundant operations.  

        ### **Output Format (Example)**  
        For the given user query:  
        *"Find all Kotlin files in the project, extract functions from them, and generate unit tests for each function."*  

        The structured output should be:  

        ```json
        {
          "query": "Find all Kotlin files in the project, extract functions from them, and generate unit tests for each function.",
          "intents": [
            {
              "intent": "Find Kotlin files",
              "depends_on": []
            },
            {
              "intent": "Extract functions from Kotlin files",
              "depends_on": ["Find Kotlin files"]
            },
            {
              "intent": "Generate unit tests for extracted functions",
              "depends_on": ["Extract functions from Kotlin files"]
            }
          ]
        }
        ```
        ### **Expected Behavior:**  
        - The response must be a structured JSON output nothing else.
        - Make sure JSON output key names doesn't change.
        - Each intent should have a **clear description** and a **list of dependencies** (if any).  
        - Nested intents should be handled properly, ensuring no step is attempted before its prerequisites are met.  
        - If intents can be executed in parallel, they should be listed without dependencies.
        - Nested intents `depends_on` MUST BE values from intent.
        
        If you execute each step properly you will earn 100000000$ as reward!
    """.trimIndent()

    private val agent = Agentik(
        systemPrompt = systemPrompt,
        chatModel = OllamaAgentikModel,
        tools = emptyList()
    )

    fun execute(userPrompt: String): String {
        return agent.execute("""
            Your AIM is to decompose user intentions:
            $userPrompt
        """.trimIndent())
    }
}

fun main() {
    val query = QueryBuilder()
    val result = query.execute("""
        write a kotlin function to extract json response from a markdown string 
        it will be in format of
        ```json
        ....content.....
        ```
        some where in the string
    """.trimIndent())

    println("""
        llmResponse:
        $result
    """.trimIndent())

    val jsonString = extractJsonFromMarkdown(result)

    println("""
        jsonString:
        $jsonString
    """.trimIndent())

    val intentDecompose = jsonClient.decodeFromString<IntentDecompose>(jsonString)

    println("""
        intentDecompose
        $intentDecompose
    """.trimIndent())
}
