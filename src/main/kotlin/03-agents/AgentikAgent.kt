package `03-agents`

import `02-functions`.AgentikTool

interface AgentikAgent : AgentikTool {
    val name: String
    val description: String
}
