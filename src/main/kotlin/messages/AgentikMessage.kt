package messages

import java.net.URI

sealed class AgentikMessage


data class UserMessage(
    val prompt: String,
    val imagePath: List<URI> = emptyList(),
) : AgentikMessage()

data class SystemMessage(
    val prompt: String
) : AgentikMessage()

data class AgentMessage(
    val prompt: String
) : AgentikMessage()