package memory.sessions

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ContentType
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore
import messages.AgentMessage
import messages.AgentikMessage
import messages.SystemMessage
import messages.UserMessage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SessionStorage(
    maxMessage: Int
) {
    private val sessionId: String = Uuid.random().toString()

    private val chatMemory = MessageWindowChatMemory
        .builder()
        .id(sessionId)
        .maxMessages(maxMessage)
        .chatMemoryStore(InMemoryChatMemoryStore())
        .build()

    fun l4jChatMemory(): MessageWindowChatMemory = chatMemory

    private fun _messages(): List<AgentikMessage> {
        return chatMemory.messages().mapNotNull { chatMessage ->
            return@mapNotNull when (chatMessage) {
                is AiMessage -> AgentMessage(chatMessage.text())
                is dev.langchain4j.data.message.UserMessage -> {
                    var prompt = chatMessage.contents()
                        .filter { it.type() == ContentType.TEXT }
                        .filterIsInstance<TextContent>()
                        .firstOrNull()?.text() ?: ""
                    val images = chatMessage.contents()
                        .filter { it.type() == ContentType.IMAGE }
                        .filterIsInstance<ImageContent>()
                        .map { it.image().url() }
                    UserMessage(
                        prompt = prompt,
                        imagePath = images
                    )
                }

                is dev.langchain4j.data.message.SystemMessage -> SystemMessage(chatMessage.text())
                else -> null
            }
        }
    }

    fun l4jChatMemoryMessages(): List<ChatMessage> {
        return chatMemory.messages() ?: emptyList()
    }
}

