package functions.commandline

import dev.langchain4j.agent.tool.Tool
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

fun main() {
    CommandLineKtx().runTerminalCommand("echo", "Hello, Testcontainers!")
}

class CommandLineKtx {

    @Tool("runs command line operation and return output of terminal as string")
    fun runTerminalCommand(vararg commandParts: String): String? {
        val imageName = DockerImageName.parse("alpine:latest")
        val container = GenericContainer(imageName)
            .withCommand(*commandParts)
        container.start()
        val containerLog: String? = container.logs
        container.stop()
        return containerLog
    }
}