package knowledge.querybuilder


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IntentDecompose(
    @SerialName("intents")
    val intents: List<Intent>,
    @SerialName("query")
    val query: String // Find all Kotlin files in the project, extract functions from them, and generate unit tests for each function.
) {
    @Serializable
    data class Intent(
        @SerialName("depends_on")
        val dependsOn: List<String>,
        @SerialName("intent")
        val intent: String // Find Kotlin files
    )
}