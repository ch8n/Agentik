import agent.Agent
import agent.AgentikModel
import agent.Ollama
import agent.Response
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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


            val agent = remember {
                Agent(
                    model = Ollama("qwen2.5:0.5b-instruct")
                )
            }

            val response: Response<String> by agent.response.collectAsState(Response.Success(""))

            when (response) {
                is Response.Error -> {
                    Text(
                        ((response as Response.Error).error).message
                            ?: "Error occured! ${(response as Response.Error).error}"
                    )
                }

                is Response.Success<*> -> {
                    Text((response as Response.Success<String>).value)
                }
            }

            var inputState = remember { mutableStateOf("Hi! who are you?") }

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
                    if (isStreaming.value) {
                        agent.executeStreaming(inputState.value)
                    } else {
                        agent.execute(inputState.value)
                    }
                }
            }) {
                Text("Send")
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
