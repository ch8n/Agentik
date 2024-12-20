package memory.sessions

import dev.langchain4j.memory.chat.MessageWindowChatMemory

class SessionStorage(
    maxMessage: Int
) {
    private val chatMemory = MessageWindowChatMemory
        .withMaxMessages(maxMessage)



}

