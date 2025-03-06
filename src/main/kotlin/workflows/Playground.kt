package workflows

import `01-chat-models`.OllamaAgentikModel
import `03-agents`.Agentik
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking


fun main(): Unit = runBlocking {
    val agent = Agentik(
        systemPrompt = """
            You are helpful AI assistant.
        """.trimIndent(),
        chatModel = OllamaAgentikModel,
        tools = emptyList(),
        isStreaming = true
    )
    agent.executeStreaming(
        """
            Write a kotlin function that extract json block from a markdown content,
            # Extraction guideline
            - it should extract only first json block defined in markdown
            - it there is none json block in markdown then return empty string
            - extract with regex of pattern ```json <content> ```
            
            example if markdown string contains content
            ````json
{
  "a": {
    "b": "c"
  },
  "d": "e"
}
```
            
            Expected output from function:
            {
  "a": {
    "b": "c"
  },
  "d": "e"
}
        """
    ).onStart {
        println("===== Started =======")
    }.onCompletion {
        println("===== Completed =======")
    }.onEach { token ->
        print(token)
    }.catch { error ->
        error.printStackTrace()
    }.launchIn(this)
}