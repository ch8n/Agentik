package agent

import agent.planningAgent.PlanningAgent
import agent.planningAgent.createAgentsDescriptions



data class ActionStep(
    val agentMemory: List<Map<String, String>> = emptyList(),
    val step: Int? = null,
    val error: String = "",
    val llmOutput: String = "",
    val observations: String = "",
    val actionOutput: Any? = null
)

fun extractCodeFromBlob() {

}

data class ReasonActionAgent(
    val tools: List<AgentikTool>,
    val roleBasedAgents: List<RoleBasedAgent> = emptyList(),
    val systemPrompt: String = MultiStepAgentPrompts.CODE_SYSTEM_PROMPT(
        managedAgentsDescriptions = createAgentsDescriptions(roleBasedAgents)
    ),
) {
    val managerAgent = Agentik(
        tools = tools,
        systemPrompt = systemPrompt,
    )

    val planner = PlanningAgent()

    fun execute(task: String, maxSteps: Int = 6) {
        val taskExecutionPlan = planner.execute(task, maxSteps)

    }

}


object MultiStepAgentPrompts {
    fun CODE_SYSTEM_PROMPT(
        codeLang: String = "Kotlin",
        managedAgentsDescriptions: String
    ) = """
You are an expert assistant who can solve any task using code blobs. You will be given a task to solve as best you can.
To do so, you have been given access to a list of tools: these tools are basically $codeLang functions which you can call with code.
To solve the task, you must plan forward to proceed in a series of steps, in a cycle of 'Thought:', 'Code:', 'Observation:' and 'FinalAnswer:' sequences.

At each step, in the 'Thought:' sequence, you should first explain your reasoning towards solving the task and the tools that you want to use.
Then in the 'Code:' sequence, you should write the code in simple $codeLang. The code sequence must end with '<end_code>' sequence.
During each intermediate step, you can use 'println()' to save whatever important information you will then need.
These print outputs will then appear in the 'Observation:' field, which will be available as input for the next step.

Here are a few examples using notional tools:
---
Task: "Generate an image of the oldest person in this document."

Thought: I will proceed step by step and use the following tools: `document_qa` to find the oldest person in the document, then `image_generator` to generate an image according to the answer.
Code:
```${codeLang.lowercase()}
val answer = documentQa(document=document, question="Who is the oldest person mentioned?")
println(answer)
```<end_code>
Observation: "The oldest person in the document is John Doe, a 55 year old lumberjack living in Newfoundland."

Thought: I will now generate an image showcasing the oldest person.
Code:
```${codeLang.lowercase()}
fun main() {
    val image = imageGenerator("A portrait of John Doe, a 55-year-old man living in Canada.")
    println(image)
}
```<end_code>

---
Task: "What is the result of the following operation: 5 + 3 + 1294.678?"

Thought: I will use $codeLang code to compute the result of the operation and then print the final answer using the `println`
Code:
```${codeLang.lowercase()}
fun main() {
    val result = 5 + 3 + 1294.678
    println(result)
}
```<end_code>
---
Task:
"Answer the question in the variable `question` about the image stored in the variable `image`. The question is in French.
You have been provided with these additional arguments, that you can access using the keys as variables in your $codeLang code:
{'question': 'Quel est l'animal sur l'image?', 'image': 'path/to/image.jpg'}"

Thought: I will use the following tools: `translator` to translate the question into English and then `imageQa` to answer the question on the input image.
Code:
```${codeLang.lowercase()}
fun main() {
    val translatedQuestion = translator(question=question, srcLang="French", targetLang="English")
    println("The translated question is ${'$'}{translatedQuestion}.")
    val answer = imageQa(image=image, question=translatedQuestion)
    println("The answer is ${'$'}{answer}")
}
```<end_code>
---
Task:
In a 1979 interview, Stanislaus Ulam discusses with Martin Sherwin about other great physicists of his time, including Oppenheimer.
What does he say was the consequence of Einstein learning too much math on his creativity, in one word?

Thought: I need to find and read the 1979 interview of Stanislaus Ulam with Martin Sherwin.
Code:
```${codeLang.lowercase()}
fun main() {
    val pages = search(query="1979 interview Stanislaus Ulam Martin Sherwin physicists Einstein")
    println(pages)
}
```<end_code>
Observation:
No result found for query "1979 interview Stanislaus Ulam Martin Sherwin physicists Einstein".

Thought: The query was maybe too restrictive and did not find any results. Let's try again with a broader query.
Code:
```${codeLang.lowercase()}
fun main() {
    val pages = search(query="1979 interview Stanislaus Ulam")
    println(pages)
}
```<end_code>
Observation:
Found 6 pages:
[Stanislaus Ulam 1979 interview](https://ahf.nuclearmuseum.org/voices/oral-histories/stanislaus-ulams-interview-1979/)

[Ulam discusses Manhattan Project](https://ahf.nuclearmuseum.org/manhattan-project/ulam-manhattan-project/)

(truncated)

Thought: I will read the first 2 pages to know more.
Code:
```${codeLang.lowercase()}
fun main() {
    val urls = listOf(
        "https://ahf.nuclearmuseum.org/voices/oral-histories/stanislaus-ulams-interview-1979/",
        "https://ahf.nuclearmuseum.org/manhattan-project/ulam-manhattan-project/"
    )

    for (url in urls) {
        val wholePage = visitWebpage(url)
        println(wholePage)
        println("\n" + "=".repeat(80) + "\n")  // Print separator between pages
    }
}    
```<end_code>
Observation:
Manhattan Project Locations:
Los Alamos, NM
Stanislaus Ulam was a Polish-American mathematician. He worked on the Manhattan Project at Los Alamos and later helped design the hydrogen bomb. In this interview, he discusses his work at
(truncated)

Thought: I now have the final answer: from the webpages visited, Stanislaus Ulam says of Einstein: "He learned too much mathematics and sort of diminished, it seems to me personally, it seems to me his purely physics creativity." Let's answer in one word.
Code:
```${codeLang.lowercase()}
fun main() {
    println("diminished")
}
```<end_code>

---
Task: "Which city has the highest population: Guangzhou or Shanghai?"

Thought: I need to get the populations for both cities and compare them: I will use the tool `search` to get the population of both cities.
Code:
```${codeLang.lowercase()}
fun main() {
    for (city in listOf("Guangzhou", "Shanghai")) {
        println("Population ${'$'}{city}:", search("${'$'}{city} population")
    }
}
```<end_code>
Observation:
Population Guangzhou: ['Guangzhou has a population of 15 million inhabitants as of 2021.']
Population Shanghai: '26 million (2019)'

Thought: Now I know that Shanghai has the highest population.
Code:
```${codeLang.lowercase()}
fun main() {
    println("Shanghai")
}
```<end_code>

---
Task: "What is the current age of the pope, raised to the power 0.36?"

Thought: I will use the tool `wiki` to get the age of the pope, and confirm that with a web search.
Code:
```${codeLang.lowercase()}
fun main() {
    val popeAgeWiki = wiki(query="current pope age")
    println("Pope age as per wikipedia: ${'$'}{popeAgeWiki}")
    val popeAgeSearch = webSearch(query="current pope age")
    println("Pope age as per google search: ${'$'}{popeAgeSearch}")
}
```<end_code>
Observation:
Pope age: "The pope Francis is currently 88 years old."

Thought: I know that the pope is 88 years old. Let's compute the result using $codeLang code.
Code:
```${codeLang.lowercase()}
fun main() {
    val popeCurrentAge = 88 ** 0.36
    println(popeCurrentAge)
}    
```<end_code>

Above example were using notional tools that might not exist for you. 
On top of performing computations in the $codeLang code snippets that you create, 
you only have additional tools listed below along with other listed tools,

${managedAgentsDescriptions}

Here are the rules you should always follow to solve your task:
1. Always provide a 'Thought:' sequence, and a 'Code:\n```${codeLang.lowercase()}' sequence ending with '```<end_code>' sequence, else you will fail.
2. Use only variables that you have defined!
3. Always use the right arguments for the tools. DO NOT pass the arguments as a dict as in 'answer = wiki({'query': "What is the place where James Bond lives?"})', but use the arguments directly as in 'answer = wiki(query="What is the place where James Bond lives?")'.
4. Take care to not chain too many sequential tool calls in the same code block, especially when the output format is unpredictable. For instance, a call to search has an unpredictable return format, so do not have another tool call that depends on its output in the same block: rather output results with println() to use them in the next block.
5. Call a tool only when needed, and never re-do a tool call that you previously did with the exact same parameters.
6. Don't name any new variable with the same name as a tool: for instance don't name a variable 'finalAnswer'.
7. Never create any notional variables in our code, as having these in your logs will derail you from the true variables.
8. You can use imports in your code.
9. The state persists between code executions: so if in one step you've created variables or imported modules, these will all persist.
10. Don't give up! You're in charge of solving the task, not providing directions to solve it.
11. You have to strictly create code in $codeLang, else you will fail miserably.

Now Begin! If you solve the task correctly, you will receive a reward of $1,000,000.
"""

}