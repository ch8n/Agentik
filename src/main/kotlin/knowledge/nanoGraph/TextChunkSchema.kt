package knowledge.nanoGraph

import BaseGraphStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

suspend fun mergeNodesThenUpsert(
    entityName: String,
    nodesData: List<Map<String, Any>>,
    knowledgeGraph: BaseGraphStorage,
    ollamaClient: OllamaClient
) = withContext(Dispatchers.IO) {
    val entityType = nodesData.groupBy { it["entity_type"] }.maxByOrNull { it.value.size }?.key ?: "UNKNOWN"
    val descriptions = nodesData.map { it["description"] as String }.toSet()
    val sourceIds = nodesData.flatMap { (it["source_id"] as String).split(",") }.toSet()
    val summary = ollamaClient.generateCompletion(
        "MHKetbi/Unsloth-Phi-4-mini-instruct",
        "Summarize the following descriptions: ${descriptions.joinToString("\n")}"
    )
    knowledgeGraph.upsertNode(entityName, mapOf(
        "entity_type" to entityType,
        "description" to summary,
        "source_id" to sourceIds.joinToString(",")
    ))
}

suspend fun mergeEdgesThenUpsert(
    srcId: String,
    tgtId: String,
    edgesData: List<Map<String, Any>>,
    knowledgeGraph: BaseGraphStorage,
    ollamaClient: OllamaClient
) = withContext(Dispatchers.IO) {
    val weights = edgesData.map { it["weight"] as Float }
    val descriptions = edgesData.map { it["description"] as String }.toSet()
    val sourceIds = edgesData.flatMap { (it["source_id"] as String).split(",") }.toSet()
    val summary = ollamaClient.generateCompletion(
        "MHKetbi/Unsloth-Phi-4-mini-instruct",
        "Summarize the following relationship descriptions: ${descriptions.joinToString("\n")}"
    )
    knowledgeGraph.upsertEdge(srcId, tgtId, mapOf(
        "weight" to weights.average(),
        "description" to summary,
        "source_id" to sourceIds.joinToString(",")
    ))
}

suspend fun extractEntities(
    chunks: Map<String, TextChunkSchema>,
    knowledgeGraph: BaseGraphStorage,
    vectorStorage: BaseVectorStorage,
    ollamaClient: OllamaClient
) {
    val entityPrompt = """
        Extract entities and relationships from the following text. Return in JSON format with entities (name, type, description) and relationships (source, target, description, weight).
        Text: {TEXT}
    """.trimIndent()

    chunks.forEach { (chunkId, chunk) ->
        val response = ollamaClient.generateCompletion(
            "MHKetbi/Unsloth-Phi-4-mini-instruct",
            entityPrompt.replace("{TEXT}", chunk.content)
        )
        val json = Json.parseToJsonElement(response).jsonObject
        val entities = json["entities"]?.jsonArray?.map { ent ->
            mapOf(
                "name" to ent.jsonObject["name"]!!.jsonPrimitive.content,
                "entity_type" to ent.jsonObject["type"]!!.jsonPrimitive.content,
                "description" to ent.jsonObject["description"]!!.jsonPrimitive.content,
                "source_id" to chunkId
            )
        } ?: emptyList()

        val relationships = json["relationships"]?.jsonArray?.map { rel ->
            mapOf(
                "source" to rel.jsonObject["source"]!!.jsonPrimitive.content,
                "target" to rel.jsonObject["target"]!!.jsonPrimitive.content,
                "description" to rel.jsonObject["description"]!!.jsonPrimitive.content,
                "weight" to rel.jsonObject["weight"]!!.jsonPrimitive.float,
                "source_id" to chunkId
            )
        } ?: emptyList()

        val entityGroups = entities.groupBy { it["name"] }
        entityGroups.forEach { (name, nodes) ->
            mergeNodesThenUpsert(name as String, nodes, knowledgeGraph, ollamaClient)
        }

        relationships.groupBy { it["source"] to it["target"] }.forEach { (pair, edges) ->
            val (src, tgt) = pair
            mergeEdgesThenUpsert(src as String, tgt as String, edges, knowledgeGraph, ollamaClient)
        }

        vectorStorage.upsert(mapOf(chunkId to mapOf("content" to chunk.content, "source_id" to chunk.sourceId)))
    }
}