import agent.Agentik
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import functions.maths.MathsKtx
import functions.websearch.WebSearchKtx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    MaterialTheme {

        Column(
            modifier = Modifier.padding(40.dp)
        ) {


            val agentik = remember {
                Agentik(
                    systemPrompt = """
                        You run in a loop of Thought, agent.planningAgent.Action, PAUSE, agent.planningAgent.Observation.
                        At the end of the loop you output an Answer
                        Use Thought to describe your thoughts about the question you have been asked.
                        Use agent.planningAgent.Action to run one of the actions available to you - then return PAUSE.
                        agent.planningAgent.Observation will be the result of running those actions.
                        
                        Example session:

                        Question: What is the mass of Earth times 2?
                        Thought: I need to find the mass of Earth
                        agent.planningAgent.Action: get_planet_mass: Earth
                        PAUSE 

                        You will be called again with this:

                        agent.planningAgent.Observation: 5.972e24

                        Thought: I need to multiply this by 2
                        agent.planningAgent.Action: calculate: 5.972e24 * 2
                        PAUSE

                        You will be called again with this: 

                        agent.planningAgent.Observation: 1,1944×10e25

                        If you have the answer, output it as the Answer.

                        Answer: The mass of Earth times 2 is 1,1944×10e25.

                        Now it's your turn:
                    """.trimIndent(),
                    tools = mutableListOf(
                        WebSearchKtx(), MathsKtx()
                    )
                )
            }


            var inputState = remember { mutableStateOf("what is weather in delhi?") }
            var outState = remember { mutableStateOf("Output:") }

            OutlinedTextField(
                value = inputState.value,
                onValueChange = {
                    inputState.value = it
                }
            )

            var isStreaming = remember { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isStreaming.value,
                    onCheckedChange = {
                        isStreaming.value = it
                    }
                )

                Text("Steaming?")
            }

            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    val response = agentik.execute(inputState.value)
                    outState.value = """
                        $response
                        ----
                        ${
                        agentik.messages().joinToString("\n") {
//                            when (it.type()) {
//                                ChatMessageType.SYSTEM -> "system | ${(it as SystemMessage).text()}"
//                                ChatMessageType.USER -> "user | ${(it as UserMessage).singleText()}"
//                                ChatMessageType.AI -> "ai | ${(it as AiMessage).text()}"
//                                ChatMessageType.TOOL_EXECUTION_RESULT -> "tool | ${(it as ToolExecutionResultMessage).text()}"
//                            }
                            it.toString()
                        }
                    }
                    """.trimIndent()
                }
            }) {
                Text("Send")
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(outState.value)
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
