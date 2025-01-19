import agent.Agentik


sealed interface AgenticStep

class Action : AgenticStep
class Planning : AgenticStep
class Task : AgenticStep
class SystemPrompt : AgenticStep


data class MultiStepAgent(
    val agentik: Agentik
)