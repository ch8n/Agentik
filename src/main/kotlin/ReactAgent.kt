import agent.Agentik
import agent.AgentikTool
import models.AgentikModel
import kotlin.jvm.java


sealed interface AgenticStep {
    val name: String
    val description: String
}

class Action() : AgenticStep {
    override val name: String = this::class.java.simpleName
    override val description: String = """
        
    """.trimIndent()
}

class PlanningAgent(
    override val name: String,
    override val description: String,
    private val model: AgentikModel = AgentikModel.Ollama,
    private val modelName: String = "llama3.2:latest",
    private val tools: List<AgentikTool> = emptyList(),
) : AgenticStep {

    private val history = mutableListOf<Pair<String, String>>()

    fun append(plan: String, facts: String) {
        history.add(plan to facts)
    }

    fun getFacts(): List<String> = history.map { it.second }
    fun getPlan(): List<String> = history.map { it.first }

    fun execute(task: String) {
        val agent = Agentik(
            systemPrompt = PROMPT_FACTS,
            model = model,
            modelName = modelName,
            tools = tools
        )
        agent.execute(USER_PROMPT_TASK(task))
    }
}

class Task : AgenticStep {
    override val name: String
        get() = TODO("Not yet implemented")
    override val description: String
        get() = TODO("Not yet implemented")
}

class SystemPrompt : AgenticStep {
    override val name: String
        get() = TODO("Not yet implemented")
    override val description: String
        get() = TODO("Not yet implemented")
}

class Observation : AgenticStep {
    override val name: String
        get() = TODO("Not yet implemented")
    override val description: String
        get() = TODO("Not yet implemented")
}

data class MultiStepAgent(
    val systemPrompt: String = "",
    val model: AgentikModel = AgentikModel.Ollama,
    val modelName: String = "llama3.2:latest",
    val tools: List<AgentikTool> = emptyList(),
    val maxSteps: Int = 6,
) {

    private val planner = PlanningAgent(
        name = "Task Planner",
        description = TASK_PLANNER_DESCRIPTION,
        model = model,
        modelName = modelName,
        tools = tools
    )

    private val actions = mutableListOf<AgenticStep>()


    private val agents = mutableListOf<AgenticStep>(
        planner
    )

    private val agentik = Agentik(
        systemPrompt = systemPrompt,
        model = model,
        modelName = modelName,
        tools = tools
    )

    fun getAgentsDescriptions(): String {
        return agents.fold(LOCAL_AGENT_DESCRIPTION) { prompt: String, agent: AgenticStep ->
            "$prompt\n- ${agent}: ${agent.description}"
        }
    }

    private fun innerMessage(currentStep: AgenticStep) {
        when (currentStep) {
            is Action -> TODO()
            is PlanningAgent -> TODO()
            is SystemPrompt -> TODO()
            is Task -> TODO()
            is Observation -> TODO()
        }
    }

    fun execute(task: String) {

        var currentStep = maxSteps
        val toolsDescription = ""
        val agentsDescription = getAgentsDescriptions()

        while (currentStep >= 0) {
            val isInitialStep = currentStep == maxSteps
            val remainingStep = currentStep - 1

            if (isInitialStep) {
                val systemPromptFactsBuilder = StringBuilder()
                    .append(PROMPT_FACTS)

                val userPromptTaskBuilder = StringBuilder()
                    .append(USER_PROMPT_TASK(task))

                val promptAnswerFactsBuilder = StringBuilder()
                    .append(systemPromptFactsBuilder.toString())
                    .appendLine()
                    .append(userPromptTaskBuilder.toString())

                val answerFacts = agentik.execute(promptAnswerFactsBuilder.toString())

                val systemPromptPlanBuilder = StringBuilder()
                    .append(PROMPT_PLAN)

                val userPromptPlanBuilder = StringBuilder().append(
                    USER_PROMPT_TASK(
                        task = task,
                        toolsDescriptions = toolsDescription,
                        agentsDescriptions = agentsDescription,
                        answerFacts = answerFacts
                    )
                )

                val answerPlanPromptBuilder = StringBuilder()
                    .append(systemPromptPlanBuilder.toString())
                    .appendLine()
                    .append(userPromptPlanBuilder.toString())

                val answerPlan = agentik.execute(answerPlanPromptBuilder.toString())
                    .split("<end_plan>").firstOrNull() ?: ""

                val finalPlanRedaction = FINAL_PLAN_REDACTION(answerPlan)
                val finalFactsRedaction = FINAL_FACTS_REDACTION(answerFacts)

                planner.append(plan = finalPlanRedaction, facts = finalFactsRedaction)
            } else {

                val systemFactsUpdatePromptBuilder = StringBuilder()
                    .append(SYSTEM_PROMPT_FACTS_UPDATE)

                val userFactsUpdatePromptBuilder = StringBuilder()
                    .append(USER_PROMPT_FACTS_UPDATE)

                val updateFactsBuilder = StringBuilder()
                    .append(systemFactsUpdatePromptBuilder.toString())
                    .appendLine()
                    .append(GET_FACTS_LIST(planner.getFacts()))
                    .appendLine()
                    .append(userFactsUpdatePromptBuilder.toString())

                val updatedFacts = agentik.execute(updateFactsBuilder.toString())

                val systemPlanUpdatePromptBuilder = StringBuilder()
                    .append(SYSTEM_PROMPT_PLAN_UPDATE(task))

                val userPlanUpdatePromptBuilder = StringBuilder()
                    .append(
                        USER_PROMPT_PLAN_UPDATE(
                            task = task,
                            toolsDescriptions = toolsDescription,
                            agentsDescriptions = getAgentsDescriptions(),
                            updatedFacts = updatedFacts,
                            remainingSteps = remainingStep
                        )
                    )

                val updatePlanBuilder = StringBuilder()
                    .append(systemPlanUpdatePromptBuilder.toString())
                    .appendLine()
                    .append(GET_PLAN_LIST(planner.getPlan()))
                    .appendLine()
                    .append(userPlanUpdatePromptBuilder.toString())

                val updatedPlan = agentik
                    .execute(updatePlanBuilder.toString())
                    .split("<end_plan>").firstOrNull() ?: ""

                val finalPlanRedaction = PLAN_UPDATE_FINAL_PLAN_REDACTION(
                    task = task,
                    updatedPlan = updatedPlan
                )
                val finalFactsRedaction = FACTS_UPDATE_FINAL_FACTS_REDACTION(
                    updatedFacts = updatedFacts
                )
                planner.append(plan = finalPlanRedaction, facts = finalFactsRedaction)
            }

            currentStep = remainingStep
        }
    }
}