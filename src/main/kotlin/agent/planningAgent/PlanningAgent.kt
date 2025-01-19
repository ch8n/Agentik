package agent.planningAgent

import agent.Agentik
import agent.AgentikTool
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import models.AgentikModel


interface AgentikAgent {
    val name: String
    val description: String
}


@JvmInline
value class Plan(val value: String)

@JvmInline
value class Fact(val value: String)

internal data class PlanningDecision(
    val plan: Plan,
    val fact: Fact
)

fun createAgentsDescriptions(agents: List<AgentikAgent>): String {
    return agents.fold(LOCAL_AGENT_DESCRIPTION) { prompt: String, agent: AgentikAgent ->
        "$prompt\n- ${agent}: ${agent.description}"
    }
}

class PlanningAgent(
    override val name: String = "Task Planner",
    override val description: String = TASK_PLANNER_DESCRIPTION,
    private val modelName: String = "llama3.2:latest",
    private val modelType: AgentikModel = AgentikModel.Ollama,
    private val tools: List<AgentikTool> = emptyList(),
    private val agents: List<AgentikAgent> = emptyList(),
) : AgentikAgent {

    private val planningHistory = mutableListOf<PlanningDecision>()
    private val agentsDescription = createAgentsDescriptions(agents)

    private fun initialPlan(task: String): PlanningDecision {
        val planningAgentPhase1 = Agentik(
            modelType = modelType,
            modelName = modelName,
            tools = tools,
            systemPrompt = SYSTEM_PROMPT_FACTS
        )

        val answerFacts = planningAgentPhase1
            .execute(USER_PROMPT_TASK(task))

        val planningAgentPhase2 = Agentik(
            modelType = modelType,
            modelName = modelName,
            tools = tools,
            systemPrompt = SYSTEM_PROMPT_PLAN
        )

        val userPromptPlanBuilder = USER_PROMPT_TASK(
            task = task,
            agentsDescriptions = agentsDescription,
            answerFacts = answerFacts
        )

        val answerPlan = planningAgentPhase2
            .execute(userPromptPlanBuilder.toString())
            .split("<end_plan>").firstOrNull() ?: ""

        val finalPlanRedaction = Plan(FINAL_PLAN_REDACTION(answerPlan))
        val finalFactsRedaction = Fact(FINAL_FACTS_REDACTION(answerFacts))

        return PlanningDecision(
            plan = finalPlanRedaction,
            fact = finalFactsRedaction
        )
    }

    private fun updatePlan(
        task: String,
        currentFacts: List<Fact>,
        currentPlans: List<Plan>,
        remainingStep: Int
    ): PlanningDecision {

        val systemPromptFactsWithHistory = buildString {
            append(SYSTEM_PROMPT_FACTS_UPDATE)
            appendLine()
            append(GET_FACTS_LIST(currentFacts.map { it.value }))
        }

        val planningAgentPhase1 = Agentik(
            modelType = modelType,
            modelName = modelName,
            tools = tools,
            systemPrompt = systemPromptFactsWithHistory
        )

        val updatedFacts = planningAgentPhase1.execute(USER_PROMPT_FACTS_UPDATE)

        val systemPromptPlanWithHistory = buildString {
            append(SYSTEM_PROMPT_PLAN_UPDATE(task))
            appendLine()
            append(GET_PLAN_LIST(currentPlans.map { it.value }))
        }

        val planningAgentPhase2 = Agentik(
            modelType = modelType,
            modelName = modelName,
            tools = tools,
            systemPrompt = systemPromptPlanWithHistory
        )

        val updatePlanBuilder = USER_PROMPT_PLAN_UPDATE(
            task = task,
            agentsDescriptions = agentsDescription,
            updatedFacts = updatedFacts,
            remainingSteps = remainingStep
        )

        val updatedPlan = planningAgentPhase2
            .execute(updatePlanBuilder)
            .split("<end_plan>").firstOrNull() ?: ""

        val finalPlanRedaction = Plan(
            PLAN_UPDATE_FINAL_PLAN_REDACTION(
                task = task,
                updatedPlan = updatedPlan
            )
        )
        val finalFactsRedaction = Fact(
            FACTS_UPDATE_FINAL_FACTS_REDACTION(
                updatedFacts = updatedFacts
            )
        )

        return PlanningDecision(
            plan = finalPlanRedaction,
            fact = finalFactsRedaction
        )
    }

    @Tool(TASK_PLANNER_DESCRIPTION)
    fun execute(
        @P("The task to perform")
        task: String,
        @P("Maximum number steps/retries LLM can take for planning, use default max is 6")
        maxSteps: Int
    ): String {
        var currentStep = maxSteps
        while (currentStep >= 0) {
            val isInitialStep = currentStep == maxSteps
            val remainingStep = currentStep - 1
            if (isInitialStep) {
                val initialPlan = initialPlan(task)
                planningHistory.add(initialPlan)
            } else {
                val updatedPlan = updatePlan(
                    task = task,
                    currentFacts = planningHistory.map { it.fact },
                    currentPlans = planningHistory.map { it.plan },
                    remainingStep = remainingStep
                )
                planningHistory.add(updatedPlan)
                println(planningHistory.last())
            }
            currentStep = remainingStep
        }
        return planningHistory.last().plan.value
    }
}

fun main() {
    val plannerAgent = PlanningAgent()
    plannerAgent.execute(
        "Which is the 2nd largest planet? and how many moons it has? name 3 moons bigger than earths?",
        6
    )
}