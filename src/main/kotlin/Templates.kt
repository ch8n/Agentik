import androidx.compose.ui.util.fastJoinToString

const val PROMPT_FACTS = """Below I will present you a task.

You will now build a comprehensive preparatory survey of which facts we have at our disposal and which ones we still need.
To do so, you will have to read the task and identify things that must be discovered in order to successfully complete it.
Don't make any assumptions. For each item, provide a thorough reasoning. Here is how you will structure this survey:

---
### 1. Facts given in the task
List here the specific facts given in the task that could help you (there might be nothing here).

### 2. Facts to look up
List here any facts that we may need to look up.
Also list where to find each of these, for instance a website, a file... - maybe the task contains some sources that you should re-use here.

### 3. Facts to derive
List here anything that we want to derive from the above by logical reasoning, for instance computation or simulation.

Keep in mind that "facts" will typically be specific names, dates, values, etc. Your answer should use the below headings:
### 1. Facts given in the task
### 2. Facts to look up
### 3. Facts to derive
Do not add anything else."""

const val PROMPT_PLAN = """
You are a world expert at making efficient plans to solve any task using a set of carefully crafted tools.

Now for the given task, develop a step-by-step high-level plan taking into account the above inputs and list of facts.
This plan should involve individual tasks based on the available tools, that if executed correctly will yield the correct answer.
Do not skip steps, do not add any superfluous steps. Only write the high-level plan, DO NOT DETAIL INDIVIDUAL TOOL CALLS.
After writing the final step of the plan, write the '\n<end_plan>' tag and stop there.
"""


fun USER_PROMPT_TASK(
    task: String,
    toolsDescriptions: String,
    agentsDescriptions: String,
    answerFacts: String
): String = """
Here is your task:

Task:
```
$task
```

Your plan can leverage any of these tools:
${toolsDescriptions}

${agentsDescriptions}

List of facts that you know:
```
${answerFacts}
```
Now begin! Write your plan below."""

fun USER_PROMPT_TASK(
    task: String
): String = """
Here is your task:

Task:
```
$task
```
Now begin!
"""


const val LOCAL_AGENT_DESCRIPTION = """
You can also give requests to team members.
Calling a team member works the same as for calling a tool: simply, the only argument you can give in the call is 'request', a long string explaining your request.
Given that this team member is a real human, you should be very verbose in your request.
Here is a list of the team members that you can call:
"""

const val TASK_PLANNER_DESCRIPTION = """
An expert system for breaking down and planning complex tasks. 
This tool excels analyzing and decomposing task into manageable subtasks
"""

fun FINAL_PLAN_REDACTION(answerPlan: String) = """
Here is the plan of action that I will follow to solve the task:
```
$answerPlan
```
"""

fun FINAL_FACTS_REDACTION(answerFacts: String) = """
Here are the facts that I know so far:
```
$answerFacts
```
"""

const val SYSTEM_PROMPT_FACTS_UPDATE = """
You are a world expert at gathering known and unknown facts based on a conversation.
Below you will find a task, and a history of attempts made to solve the task. You will have to produce a list of these:
### 1. Facts given in the task
### 2. Facts that we have learned
### 3. Facts still to look up
### 4. Facts still to derive
Find the task and history below."""

const val USER_PROMPT_FACTS_UPDATE = """Earlier we've built a list of facts.
But since in your previous steps you may have learned useful new facts or invalidated some false ones.
Please update your list of facts based on the previous history, and provide these headings:
### 1. Facts given in the task
### 2. Facts that we have learned
### 3. Facts still to look up
### 4. Facts still to derive

Now write your new list of facts below."""

fun GET_FACTS_LIST(factList: List<String>) = """
    [FACTS LIST]:
    ${factList.fastJoinToString("\n")}
""".trimIndent()

fun SYSTEM_PROMPT_PLAN_UPDATE(task: String) = """
You are a world expert at making efficient plans to solve any task using a set of carefully crafted tools.
You have been given a task:
```
$task
```
Find below the record of what has been tried so far to solve it. Then you will be asked to make an updated plan to solve the task.
If the previous tries so far have met some success, you can make an updated plan based on these actions.
If you are stalled, you can make a completely new plan starting from scratch.
"""

fun USER_PROMPT_PLAN_UPDATE(
    task: String,
    toolsDescriptions: String,
    agentsDescriptions: String,
    updatedFacts: String,
    remainingSteps: Int,
) = """
You're still working towards solving this task:
```
${task}
```

You have access to these tools and only these:
${toolsDescriptions}

${agentsDescriptions}

Here is the up to date list of facts that you know:
```
${updatedFacts}
```

Now for the given task, develop a step-by-step high-level plan taking into account the above inputs and list of facts.
This plan should involve individual tasks based on the available tools, that if executed correctly will yield the correct answer.
Beware that you have ${remainingSteps} steps remaining.
Do not skip steps, do not add any superfluous steps. Only write the high-level plan, DO NOT DETAIL INDIVIDUAL TOOL CALLS.
After writing the final step of the plan, write the '\n<end_plan>' tag and stop there.

Now write your new plan below."""

fun GET_PLAN_LIST(planList: List<String>) = """
    [PLAN LIST]:
    ${planList.fastJoinToString("\n")}
""".trimIndent()


fun PLAN_UPDATE_FINAL_PLAN_REDACTION(
    task: String,
    updatedPlan: String
) = """
I still need to solve the task I was given:
```
${task}
```

Here is my new/updated plan of action to solve the task:
```
${updatedPlan}
```"""

fun FACTS_UPDATE_FINAL_FACTS_REDACTION(updatedFacts: String) = """
Here is the updated list of the facts that I know:
```
${updatedFacts}
```"""