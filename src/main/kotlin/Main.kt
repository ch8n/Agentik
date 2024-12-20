import agent.Agentik
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import memory.sessions.SessionStorage
import models.Ollama
import utils.Response

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
                    model = Ollama("qwen2.5:0.5b-instruct"),
                    sessionStorage = SessionStorage(10)
                )
            }

            val response: Response<String> by agentik.response.collectAsState(Response.Success(""))

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
                        agentik.executeStreaming(inputState.value)
                    } else {
                        agentik.execute(inputState.value)
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
