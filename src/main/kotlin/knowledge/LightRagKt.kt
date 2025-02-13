package knowledge

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.neo4j.driver.*

// ==================== Data Models ====================

@Serializable
data class Entity(
    val name: String,
    val type: String,
    val description: String,
    val source: String
)

@Serializable
data class Relation(
    val sourceEntity: String,
    val targetEntity: String,
    val relationType: String,
    val description: String,
    val source: String
)

// Response model for embedding calls
@Serializable
data class EmbeddingResponse(val embedding: List<Float>)

// Response model for LLM completions (entity extraction, query keywords, answer generation)
@Serializable
data class LLMResponse(val result: String)

// Response model for keyword extraction (if needed)
@Serializable
data class KeywordResponse(val result: String)


// ==================== Phase 1: Document Preprocessing ====================

object DocumentProcessor {
    // Split document into chunks by word count (default chunk size = 1200 words)
    fun chunkDocument(document: String, chunkSize: Int = 1200): List<String> {
        val words = document.split("\\s+".toRegex())
        val chunks = mutableListOf<String>()
        var index = 0
        while (index < words.size) {
            val end = minOf(index + chunkSize, words.size)
            val chunk = words.subList(index, end).joinToString(" ")
            chunks.add(chunk)
            index = end
        }
        return chunks
    }
}

// ==================== Phase 2: Embedding Generation & Entity Extraction ====================

object EmbeddingService {
    private val client = HttpClient(CIO)

    // Calls Ollama embedding API to get a float vector for the input text
    suspend fun getEmbedding(text: String): List<Float> {
        try {
            val response: HttpResponse = client.post("http://localhost:11434/v1/embed") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToJsonElement(mapOf("text" to text)))
            }
            return Json.decodeFromString<EmbeddingResponse>(response.bodyAsText()).embedding
        } catch (e: Exception) {
            println("Error getting embedding: ${e.message}")
            throw e
        }
    }
}

object EntityExtractor {
    private val client = HttpClient(CIO)

    /**
     * Calls the LLM (Ollama Qwen 2.5) to extract entities and their relationships from a text chunk.
     * The prompt instructs the model to return a JSON string with two arrays: "entities" and "relations".
     */
    suspend fun extractEntitiesAndRelations(chunk: String): Pair<List<Entity>, List<Relation>> {
        val prompt = """
            Extract entities and their relationships from the following text.
            For each entity, output its name, type, description, and source identifier.
            For each relationship, output the source entity name, target entity name, relation type, description, and source identifier.
            Text: $chunk
        """.trimIndent()
        try {
            val response = client.post("http://localhost:11434/v1/complete") {
                contentType(ContentType.Application.Json)
                // Specify the Qwen 2.5 model for extraction
                setBody(Json.encodeToJsonElement(mapOf("prompt" to prompt, "model" to "qwen2.5")))
            }
            val llmResponse = Json.decodeFromString<LLMResponse>(response.bodyAsText())
            return parseExtractionResponse(llmResponse.result)
        } catch (e: Exception) {
            println("Error extracting entities: ${e.message}")
            throw e
        }
    }

    // Parses the LLM's JSON result into lists of Entity and Relation objects.
    private fun parseExtractionResponse(result: String): Pair<List<Entity>, List<Relation>> {
        return try {
            val jsonElement = Json.parseToJsonElement(result)
            val entitiesJson = jsonElement.jsonObject["entities"] ?: JsonArray(emptyList())
            val relationsJson = jsonElement.jsonObject["relations"] ?: JsonArray(emptyList())
            val entities = Json.decodeFromJsonElement<List<Entity>>(entitiesJson)
            val relations = Json.decodeFromJsonElement<List<Relation>>(relationsJson)
            Pair(entities, relations)
        } catch (e: Exception) {
            println("Error parsing extraction response: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }
}

// ==================== Phase 3: Graph Construction & Deduplication (Neo4j) ====================

object GraphService {
    // Connect to local Neo4j database (adjust credentials as needed)
    private val driver: Driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "password"))

    fun close() {
        driver.close()
    }

    // Upsert an entity into Neo4j (deduplication by entity name)
    fun upsertEntity(entity: Entity) {
        driver.session().use { session ->
            session.writeTransaction { tx ->
                tx.run(
                    """
                    MERGE (e:Entity {name: $${'$'}name})
                    ON CREATE SET e.type = $${'$'}type, e.description = $${'$'}description, e.source = $${'$'}source
                    ON MATCH SET e.type = $${'$'}type, e.description = $${'$'}description, e.source = $${'$'}source
                    """.trimIndent(),
                    mapOf(
                        "name" to entity.name,
                        "type" to entity.type,
                        "description" to entity.description,
                        "source" to entity.source
                    )
                )
                null
            }
        }
    }

    // Insert a relation between two entities. Assumes both entities already exist.
    fun insertRelation(relation: Relation) {
        driver.session().use { session ->
            session.writeTransaction { tx ->
                tx.run(
                    """
                    MATCH (a:Entity {name: $${'$'}sourceName}), (b:Entity {name: $${'$'}targetName})
                    MERGE (a)-[r:RELATION {type: $${'$'}relationType}]->(b)
                    ON CREATE SET r.description = $${'$'}description, r.source = $${'$'}source
                    ON MATCH SET r.description = $${'$'}description, r.source = $${'$'}source
                    """.trimIndent(),
                    mapOf(
                        "sourceName" to relation.sourceEntity,
                        "targetName" to relation.targetEntity,
                        "relationType" to relation.relationType,
                        "description" to relation.description,
                        "source" to relation.source
                    )
                )
                null
            }
        }
    }

    // Retrieve entities via low-level (local) keyword search on names.
    fun retrieveEntitiesByKeyword(keyword: String): List<Entity> {
        return driver.session().use { session ->
            session.readTransaction { tx ->
                val result = tx.run(
                    """
                    MATCH (e:Entity)
                    WHERE toLower(e.name) CONTAINS toLower($${'$'}keyword)
                    RETURN e.name as name, e.type as type, e.description as description, e.source as source
                    """.trimIndent(), mapOf("keyword" to keyword)
                )
                result.list { record ->
                    Entity(
                        name = record["name"].asString(),
                        type = record["type"].asString(),
                        description = record["description"].asString(),
                        source = record["source"].asString()
                    )
                }
            }
        }
    }

    // Retrieve entities via high-level (thematic) search on descriptions.
    fun retrieveEntitiesByTheme(theme: String): List<Entity> {
        return driver.session().use { session ->
            session.readTransaction { tx ->
                val result = tx.run(
                    """
                    MATCH (e:Entity)
                    WHERE toLower(e.description) CONTAINS toLower($${'$'}theme)
                    RETURN e.name as name, e.type as type, e.description as description, e.source as source
                    """.trimIndent(), mapOf("theme" to theme)
                )
                result.list { record ->
                    Entity(
                        name = record["name"].asString(),
                        type = record["type"].asString(),
                        description = record["description"].asString(),
                        source = record["source"].asString()
                    )
                }
            }
        }
    }
}

// ==================== Phase 4: Query Processing & Dual-Level Retrieval ====================

object QueryProcessor {
    private val client = HttpClient(CIO)

    /**
     * Uses an LLM call to extract two lists of keywords from a query:
     * – Local keywords (for entity-specific search)
     * – Global keywords (for thematic retrieval)
     */
    suspend fun extractQueryKeywords(query: String): Pair<List<String>, List<String>> {
        val prompt = """
            Extract two lists of keywords from the following query:
            1. Local keywords for entity-specific search.
            2. Global keywords for thematic search.
            Query: "$query"
            Output in JSON format:
            { "local": ["keyword1", "keyword2"], "global": ["theme1", "theme2"] }
        """.trimIndent()
        try {
            val response = client.post("http://localhost:11434/v1/complete") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToJsonElement(mapOf("prompt" to prompt, "model" to "qwen2.5")))
            }
            val llmResponse = Json.decodeFromString<LLMResponse>(response.bodyAsText())
            return parseKeywordResponse(llmResponse.result)
        } catch (e: Exception) {
            println("Error extracting query keywords: ${e.message}")
            // Fallback: very simple split (advanced implementations should use the LLM)
            val words = query.split("\\s+".toRegex())
            val local = words.take(2)
            val global = words.drop(2).take(2)
            return Pair(local, global)
        }
    }

    private fun parseKeywordResponse(result: String): Pair<List<String>, List<String>> {
        return try {
            val jsonElement = Json.parseToJsonElement(result)
            val local = jsonElement.jsonObject["local"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val global = jsonElement.jsonObject["global"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            Pair(local, global)
        } catch (e: Exception) {
            println("Error parsing keyword response: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }
}

// ==================== Phase 5: Retrieval-Augmented Answer Generation ====================

object AnswerGenerator {
    private val client = HttpClient(CIO)

    /**
     * Generates a final answer by providing the combined context (from dual-level retrieval)
     * and the original query to the LLM (Ollama Qwen 2.5).
     */
    suspend fun generateAnswer(query: String, context: String): String {
        val prompt = """
            Given the following context:
            $context
            
            And the user query: "$query"
            
            Generate a detailed, contextually accurate answer.
        """.trimIndent()
        try {
            val response = client.post("http://localhost:11434/v1/complete") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToJsonElement(mapOf("prompt" to prompt, "model" to "qwen2.5")))
            }
            val llmResponse = Json.decodeFromString<LLMResponse>(response.bodyAsText())
            return llmResponse.result
        } catch (e: Exception) {
            println("Error generating answer: ${e.message}")
            throw e
        }
    }
}

// ==================== Phase 6: Incremental Updates ====================

object IncrementalUpdater {
    /**
     * Processes a new document by chunking, extracting embeddings, entities, and relations,
     * and then upserting the results into the Neo4j graph.
     */
    suspend fun updateIndexForNewDocument(document: String) {
        val chunks = DocumentProcessor.chunkDocument(document)
        for (chunk in chunks) {
            // Optional: retrieve embedding (could be stored for vector search)
            val embedding = EmbeddingService.getEmbedding(chunk)
            // Extract entities and relations from this chunk
            val (entities, relations) = EntityExtractor.extractEntitiesAndRelations(chunk)
            // Upsert entities and insert relations into the graph
            for (entity in entities) {
                GraphService.upsertEntity(entity)
            }
            for (relation in relations) {
                GraphService.insertRelation(relation)
            }
        }
    }
}

// ==================== Main Flow: Tying It All Together ====================

fun main() = runBlocking {
    try {
        // ---------- Phase 1: Document Ingestion & Preprocessing ----------
        val sampleDocument = """
            [Your long document text goes here...]
            This document describes sustainable agricultural practices including crop rotation,
            water conservation, organic fertilizers, and integrated pest management.
        """.trimIndent()
        val chunks = DocumentProcessor.chunkDocument(sampleDocument)

        // ---------- Phase 2 & 3: Process Each Chunk, Extract Entities & Build Graph ----------
        for (chunk in chunks) {
            // Retrieve embedding (if needed for vector retrieval)
            val embedding = EmbeddingService.getEmbedding(chunk)
            // Extract entities and relations using advanced LLM-based extraction
            val (entities, relations) = EntityExtractor.extractEntitiesAndRelations(chunk)
            // Insert (or upsert) each entity into Neo4j
            for (entity in entities) {
                GraphService.upsertEntity(entity)
            }
            // Insert each relation into the graph
            for (relation in relations) {
                GraphService.insertRelation(relation)
            }
        }

        // ---------- Phase 4: Query Processing & Dual-Level Retrieval ----------
        val query = "What are the main themes in the document regarding sustainable agriculture?"
        // Extract local and global keywords from the query via LLM
        val (localKeywords, globalKeywords) = QueryProcessor.extractQueryKeywords(query)

        // Retrieve entities matching local keywords (entity-specific search)
        val lowLevelResults = mutableListOf<Entity>()
        for (keyword in localKeywords) {
            lowLevelResults.addAll(GraphService.retrieveEntitiesByKeyword(keyword))
        }
        // Retrieve entities matching global keywords (thematic search)
        val highLevelResults = mutableListOf<Entity>()
        for (theme in globalKeywords) {
            highLevelResults.addAll(GraphService.retrieveEntitiesByTheme(theme))
        }
        // Combine and deduplicate results to form the context for the LLM
        val combinedEntities = (lowLevelResults + highLevelResults).distinctBy { it.name }
        val context = combinedEntities.joinToString("\n") { "${it.name} (${it.type}): ${it.description}" }

        // ---------- Phase 5: Generate Final Answer ----------
        val answer = AnswerGenerator.generateAnswer(query, context)
        println("Final Answer:\n$answer")

        // ---------- Advanced: Incremental Update Example ----------
        val newDocument = """
            [New document text with additional insights on sustainable practices in agriculture...]
            This update includes recent innovations in water-saving irrigation and organic pest control.
        """.trimIndent()
        IncrementalUpdater.updateIndexForNewDocument(newDocument)

    } catch (e: Exception) {
        println("An error occurred in the LightRAG process: ${e.message}")
    } finally {
        GraphService.close()
    }
}
