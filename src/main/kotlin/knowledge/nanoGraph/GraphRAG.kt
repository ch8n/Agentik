package knowledge.nanoGraph

import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

data class QueryParam(val topK: Int = 5)

class GraphRAG(
    private val workingDir: String,
    private val ollamaClient: OllamaClient,
    private val graphStorage: BaseGraphStorage,
    private val vectorStorage: BaseVectorStorage
) {
    private val splitter = SeparatorSplitter(listOf("\n\n", "\n", ".", " "), 1024, 128)

    suspend fun index(texts: List<String>) {
        val chunks = texts.flatMap { splitter.splitText(it) }
            .mapIndexed { i, content -> "chunk-$i" to TextChunkSchema(content, i, "doc") }
            .toMap()
        extractEntities(chunks, graphStorage, vectorStorage, ollamaClient)
        graphStorage.clustering()
        generateCommunityReports()
    }

    suspend fun query(query: String, param: QueryParam = QueryParam()): String {
        val results = vectorStorage.query(query, param.topK)
        val context = results.joinToString("\n") { it["content"] as String }
        return ollamaClient.generateCompletion(
            "MHKetbi/Unsloth-Phi-4-mini-instruct",
            "Answer based on this context: $context\nQuery: $query"
        )
    }

    private suspend fun generateCommunityReports() {
        val communities = graphStorage.communitySchema()
        communities.forEach { (key, schema) ->
            val report = ollamaClient.generateCompletion(
                "MHKetbi/Unsloth-Phi-4-mini-instruct",
                """
                    Generate a detailed report for community $key:
                    - Nodes: ${schema.nodes.joinToString(", ")}
                    - Edges: ${schema.edges.joinToString(", ") { it.joinToString(" -> ") }}
                    - Summary: Summarize the community's key insights.
                """.trimIndent()
            )
            val reportFile = File(workingDir, "community_${key}_report.txt")
            reportFile.writeText(report)
            logger.info { "Community $key Report: $report" }
            // Optionally save to file or process further
        }
    }
}