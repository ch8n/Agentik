package knowledge.code

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.neo4j.driver.*
import java.io.File
import java.util.regex.Pattern

// ==================== Data Models ====================

@Serializable
private data class Entity(
    val name: String,
    val type: String,
    val description: String,
    val source: String
)

@Serializable
private data class Relation(
    val sourceEntity: String,
    val targetEntity: String,
    val relationType: String,
    val description: String,
    val source: String
)

// For embedding response from Ollama Nomic
@Serializable
private data class EmbeddingResponse(val embedding: List<Float>)

// For LLM completions (used by both entity extraction and answer generation)
@Serializable
private data class LLMResponse(val result: String)


// ==================== Phase 1: Code Preprocessing using TreeSitter ====================


/**
 * CodeParser uses TreeSitter to parse a given Android codebase (Java, Kotlin, XML).
 * It extracts production–grade code “chunks” (classes, functions, and XML elements) to feed into RAG.
 *
 * Note: This uses a hypothetical TreeSitter Kotlin binding. Adjust the API calls as per your actual library.
 */
private object CodeParser {

    // Recursively scan a directory for files with given extensions.
    fun scanCodebase(rootPath: String, extensions: List<String> = listOf("kt", "java", "xml")): List<File> {
        val files = mutableListOf<File>()
        File(rootPath).walkTopDown()
            .forEach { file ->
                if (file.isFile && extensions.any { file.extension.equals(it, ignoreCase = true) }) {
                    files.add(file)
                }
            }
        return files
    }


    // For Kotlin files, extract classes/objects/interfaces and function declarations.
    fun parseKotlinFile(file: File): List<String> {
        val chunks = mutableListOf<String>()
        try {
            val content = file.readText()
            // Regex for Kotlin classes, objects, interfaces (with optional modifiers)
            val classPattern =
                Pattern.compile("(?m)^\\s*(public\\s+|private\\s+|protected\\s+|internal\\s+)?(class|interface|object)\\s+\\w+")
            // Regex for Kotlin function declarations
            val funPattern =
                Pattern.compile("(?m)^\\s*(public\\s+|private\\s+|protected\\s+|internal\\s+)?fun\\s+\\w+\\s*\\(.*?\\)\\s*(\\{|=)")
            val classMatcher = classPattern.matcher(content)
            while (classMatcher.find()) {
                val match = classMatcher.group().trim()
                chunks.add("File: ${file.absolutePath}\n$match")
            }
            val funMatcher = funPattern.matcher(content)
            while (funMatcher.find()) {
                val match = funMatcher.group().trim()
                chunks.add("File: ${file.absolutePath}\n$match")
            }
        } catch (e: Exception) {
            println("Error parsing Kotlin file ${file.absolutePath}: ${e.message}")
        }
        return chunks
    }

    // For Java files, extract classes/interfaces/enums and method declarations.
    fun parseJavaFile(file: File): List<String> {
        val chunks = mutableListOf<String>()
        try {
            val content = file.readText()
            // Regex for Java class, interface, and enum declarations
            val classPattern =
                Pattern.compile("(?m)^\\s*(public\\s+|private\\s+|protected\\s+)?(class|interface|enum)\\s+\\w+")
            // Regex for Java method declarations (including return type, method name, parameters)
            val methodPattern =
                Pattern.compile("(?m)^\\s*(public\\s+|private\\s+|protected\\s+)?[\\w<>\\[\\]]+\\s+\\w+\\s*\\(.*?\\)\\s*(\\{|;)")
            val classMatcher = classPattern.matcher(content)
            while (classMatcher.find()) {
                val match = classMatcher.group().trim()
                chunks.add("File: ${file.absolutePath}\n$match")
            }
            val methodMatcher = methodPattern.matcher(content)
            while (methodMatcher.find()) {
                val match = methodMatcher.group().trim()
                chunks.add("File: ${file.absolutePath}\n$match")
            }
        } catch (e: Exception) {
            println("Error parsing Java file ${file.absolutePath}: ${e.message}")
        }
        return chunks
    }

    // For XML files, use regex to extract all XML tags.
    fun parseXmlFile(file: File): List<String> {
        val chunks = mutableListOf<String>()
        try {
            val content = file.readText()
            // General regex to match any XML tag with its content (non-greedy for inner content)
            val xmlPattern = Pattern.compile("(?s)<([A-Za-z][A-Za-z0-9]*)(\\s+[^>]+)?>(.*?)</\\1>")
            val matcher = xmlPattern.matcher(content)
            while (matcher.find()) {
                val match = matcher.group().trim()
                chunks.add("File: ${file.absolutePath}\n$match")
            }
        } catch (e: Exception) {
            println("Error parsing XML file ${file.absolutePath}: ${e.message}")
        }
        return chunks
    }

    // Choose parser based on file extension.
    fun parseFile(file: File): List<String> {
        return when (file.extension.lowercase()) {
            "kt" -> parseKotlinFile(file)
            "java" -> parseJavaFile(file)
            "xml" -> parseXmlFile(file)
            else -> emptyList()
        }
    }

    // Process an entire codebase directory and return all extracted chunks.
    fun parseCodebase(rootPath: String): List<String> {
        val allChunks = mutableListOf<String>()
        val files = scanCodebase(rootPath)
        files.forEach { file ->
            val fileChunks = parseFile(file)
            allChunks.addAll(fileChunks)
        }
        return allChunks
    }
}


// ==================== Phase 2: Embedding Generation & Entity Extraction ====================

private object EmbeddingService {
    private val client = HttpClient(CIO)

    /**
     * Get the embedding for the given text using Ollama Nomic model.
     * Uses endpoint "http://localhost:11434/v1/embed" with model "nomic-embed-text".
     */
    suspend fun getEmbedding(text: String): List<Float> {
        try {
            val response = client.post("http://localhost:11434/v1/embed") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToJsonElement(mapOf("text" to text, "model" to "nomic-embed-text")))
            }
            val embeddingResponse = Json.decodeFromString<EmbeddingResponse>(response.bodyAsText())
            return embeddingResponse.embedding
        } catch (e: Exception) {
            println("Error getting embedding for text: ${e.message}")
            throw e
        }
    }
}

private object EntityExtractor {
    private val client = HttpClient(CIO)

    /**
     * Extract entities and relationships from a code chunk using Ollama Qwen 2.5.
     * The prompt is tailored for code analysis (classes, functions, etc.).
     */
    suspend fun extractEntitiesAndRelations(chunk: String): Pair<List<Entity>, List<Relation>> {
        val prompt = """
            You are an expert code analyzer.
            Extract a JSON with two arrays from the following code snippet:
            - "entities": each object should have "name", "type" (e.g., "Class", "Function", "XMLLayout"), "description" (explain its role), and "source" (the file path if available).
            - "relations": each object should describe relationships between entities, with "sourceEntity", "targetEntity", "relationType" (e.g., "calls", "inherits", "contains"), "description", and "source".
            Code Snippet:
            $chunk
        """.trimIndent()
        try {
            val response = client.post("http://localhost:11434/v1/complete") {
                contentType(ContentType.Application.Json)
                // Use the Ollama Qwen 2.5 model for advanced entity extraction
                setBody(Json.encodeToJsonElement(mapOf("prompt" to prompt, "model" to "qwen2.5")))
            }

            val llmResponse = Json.decodeFromString<LLMResponse>(response.bodyAsText())
            return parseExtractionResponse(llmResponse.result)
        } catch (e: Exception) {
            println("Error during entity extraction: ${e.message}")
            throw e
        }
    }

    // Parse the JSON result into lists of Entity and Relation objects.
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

private object GraphService {
    // Connect to local Neo4j database – adjust credentials as necessary.
    private val driver: Driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "password"))

    fun close() {
        driver.close()
    }

    // Upsert an entity (by name) into Neo4j.
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

    // Insert a relation between two entities. Assumes both entities exist.
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

    // Retrieve entities using a low-level keyword search (by name).
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

    // Retrieve entities by theme (searching within descriptions).
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

private object QueryProcessor {
    private val client = HttpClient(CIO)

    /**
     * Extract local and global keywords from a query using the LLM.
     * The prompt instructs the model to output two arrays in JSON: one for local (entity) keywords and one for global (thematic) keywords.
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
            throw e
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
     * Generate an answer by combining the retrieved context and the original query.
     * The prompt instructs the LLM (Qwen 2.5) to generate a detailed and contextually accurate answer.
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

private object IncrementalUpdater {
    /**
     * Process a new code file/document and update the Neo4j graph incrementally.
     * Uses the CodeParser to extract chunks from the new file, then processes each chunk.
     */
    suspend fun updateIndexForNewFile(file: File) {
        val chunks = CodeParser.parseFile(file)
        for (chunk in chunks) {
            // Get embedding (if needed for future vector search)
            val embedding = EmbeddingService.getEmbedding(chunk)
            // Extract entities and relations using the advanced LLM extraction
            val (entities, relations) = EntityExtractor.extractEntitiesAndRelations(chunk)
            // Upsert entities and insert relations into the graph
            entities.forEach { GraphService.upsertEntity(it) }
            relations.forEach { GraphService.insertRelation(it) }
        }
    }
}

// ==================== Main Flow: Tying It All Together ====================

fun main() = runBlocking {
    try {
        // ---------- Phase 1: Code Ingestion & Preprocessing ----------
        // Assume the Android project root is provided (adjust the path as needed)
        val codeRootPath = File("").absolutePath
        println(codeRootPath)
        val codeChunks = CodeParser.parseCodebase(codeRootPath)
        if (codeChunks.isEmpty()) {
            println("No code chunks extracted from the project.")
            return@runBlocking
        }

        // ---------- Phase 2 & 3: Process Each Code Chunk, Extract Entities & Build Graph ----------
        codeChunks.forEach { chunk ->
            try {
                // Get embedding for the chunk using Ollama Nomic
                val embedding = EmbeddingService.getEmbedding(chunk)
                // Extract entities and relations from the code chunk using Qwen 2.5
                val (entities, relations) = EntityExtractor.extractEntitiesAndRelations(chunk)
                // Upsert each entity and insert relations into the Neo4j graph
                entities.forEach { GraphService.upsertEntity(it) }
                relations.forEach { GraphService.insertRelation(it) }
            } catch (ex: Exception) {
                println("Error processing chunk: ${ex.message}")
            }
        }

        // ---------- Phase 4: Query Processing & Dual-Level Retrieval ----------
        val query = "What are the key components and interactions in the Android project codebase?"
        val (localKeywords, globalKeywords) = QueryProcessor.extractQueryKeywords(query)

        // Retrieve matching entities based on local keywords (entity-specific search)
        val lowLevelResults = mutableListOf<Entity>()
        for (keyword in localKeywords) {
            lowLevelResults.addAll(GraphService.retrieveEntitiesByKeyword(keyword))
        }
        // Retrieve matching entities based on global keywords (thematic search)
        val highLevelResults = mutableListOf<Entity>()
        for (theme in globalKeywords) {
            highLevelResults.addAll(GraphService.retrieveEntitiesByTheme(theme))
        }
        // Combine and deduplicate results to form a comprehensive context
        val combinedEntities = (lowLevelResults + highLevelResults).distinctBy { it.name }
        val context = combinedEntities.joinToString("\n") { "${it.name} (${it.type}): ${it.description}" }

        // ---------- Phase 5: Generate Final Answer ----------
        val answer = AnswerGenerator.generateAnswer(query, context)
        println("Final Answer:\n$answer")

        // ---------- Advanced: Incremental Update Example ----------
        // For demonstration, update index with a new file (adjust the path as needed)
        val newFile = File("/path/to/your/android/project/app/src/main/java/com/example/NewFeature.kt")
        if (newFile.exists()) {
            IncrementalUpdater.updateIndexForNewFile(newFile)
            println("Incremental update completed for file: ${newFile.absolutePath}")
        } else {
            println("New file for incremental update not found: ${newFile.absolutePath}")
        }
    } catch (e: Exception) {
        println("An error occurred in the LightRAG process: ${e.message}")
    } finally {
        GraphService.close()
    }
}
