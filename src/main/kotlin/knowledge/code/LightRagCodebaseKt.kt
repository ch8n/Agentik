package knowledge.code

import com.kuzudb.*
import com.kuzudb.Value
import data.codeKtx.parsers.*
import data.httpClient.httpClient
import data.jsonClient.jsonClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.regex.Pattern


// ==================== Data Models ====================

@Serializable
private data class Entity(
    val name: String,
    val entType: String,
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
private data class EmbeddingResponse(val embeddings: List<Double>)

// For LLM completions (used by both entity extraction and answer generation)
@Serializable
private data class LLMResponse(val result: String)


// ==================== Phase 1: Code Preprocessing using TreeSitter ====================
const val OLLAMA_EMBEDDING = "nomic-embed-text:v1.5"
const val OLLAMA_CHAT = "hermes3:3b-llama3.2-q8_0"

/**
 * CodeParser uses TreeSitter to parse a given Android codebase (Java, Kotlin, XML).
 * It extracts production–grade code “chunks” (classes, functions, and XML elements) to feed into RAG.
 *
 * Note: This uses a hypothetical TreeSitter Kotlin binding. Adjust the API calls as per your actual library.
 */
private object CodeParser {

    // Recursively scan a directory for files with given extensions.
    fun scanCodebase(
        rootPath: String,
        //extensions: List<String> = listOf("kt", "java", "xml")
        extensions: List<String> = listOf("kt")
    ): List<File> {
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
    fun parseKotlinFile(file: File): List<CodeBreakDown> {
        val chunks = mutableListOf<CodeBreakDown>()
        try {
            val breakdown = parseKotlinCode(file.readText())
            chunks.add(breakdown)
        } catch (e: Exception) {
            println("Error parsing Kotlin file ${file.absolutePath}: ${e.message}")
        }
        return chunks
    }

    // For Java files, extract classes/interfaces/enums and method declarations.
    fun parseJavaFile(file: File): List<CodeBreakDown> {
        val chunks = mutableListOf<CodeBreakDown>()
        try {
            val breakdown = parseJavaCode(file.readText())
            chunks.add(breakdown)
        } catch (e: Exception) {
            println("Error parsing Java file ${file.absolutePath}: ${e.message}")
        }
        return chunks
    }

    // For XML files, use regex to extract all XML tags.
    fun parseXmlFile(file: File): List<CodeBreakDown> {
        return emptyList()
        val chunks = mutableListOf<CodeBreakDown>()
        try {
            val content = file.readText()
            // General regex to match any XML tag with its content (non-greedy for inner content)
            val xmlPattern = Pattern.compile("(?s)<([A-Za-z][A-Za-z0-9]*)(\\s+[^>]+)?>(.*?)</\\1>")
            val matcher = xmlPattern.matcher(content)
            while (matcher.find()) {
                val match = matcher.group().trim()
                //chunks.add("File: ${file.absolutePath}\n$match")
            }
        } catch (e: Exception) {
            println("Error parsing XML file ${file.absolutePath}: ${e.message}")
        }
        return chunks
    }

    // Choose parser based on file extension.
    fun parseFile(file: File): List<CodeBreakDown> {
        return when (file.extension.lowercase()) {
            "kt" -> parseKotlinFile(file)
            "java" -> parseJavaFile(file)
            "xml" -> parseXmlFile(file)
            else -> emptyList()
        }
    }

    // Process an entire codebase directory and return all extracted chunks.
    fun parseCodebase(rootPath: String): List<CodeBreakDown> {
        val allChunks = mutableListOf<CodeBreakDown>()
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

    /**
     * Get the embedding for the given text using Ollama Nomic model.
     * Uses endpoint "http://localhost:11434/v1/embed" with model "nomic-embed-text".
     */
    suspend fun getEmbedding(input: String): DoubleArray {
        try {
            println(input)
            val body = buildJsonObject {
                put("model", OLLAMA_EMBEDDING)
                put("input", input)
                put("stream", "false")
                put("options", buildJsonObject {
                    put("temperature", 1f)
                })
            }
            val response = httpClient.post("http://localhost:11434/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val responseJson = jsonClient.parseToJsonElement(response.bodyAsText())
            val embeddingString = responseJson.jsonObject["embeddings"].toString()
            val doubled = jsonClient.decodeFromString<List<List<Double>>>(embeddingString)
            return doubled.first().toDoubleArray()
        } catch (e: Exception) {
            println("Error getting embedding for text: ${e.message}")
            throw e
        }
    }
}

private object EntityExtractor {

    /**
     * Extract entities and relationships from a code chunk using Ollama Qwen 2.5.
     * The prompt is tailored for code analysis (classes, functions, etc.).
     */
    suspend fun extractEntitiesAndRelations(
        entireCode: String,
        codeChunk: String,
        chunk: KotlinFileBreakdown
    ): Pair<List<Entity>, List<Relation>> {
        val prompt = """
            You are an expert code analyzer. You have the following code:
            $entireCode
            
            Extract a JSON object with two arrays from the following code snippet:
            - "entities": each object should have "name", "type" (e.g., "Class", "Function", "XMLLayout"), "description" (explain its role), and "source" (the file path if available).
            - "relations": each object should describe relationships between entities, with "sourceEntity", "targetEntity", "relationType" (e.g., "calls", "inherits", "contains"), "description", and "source".
            Code Snippet:
            $codeChunk
        """.trimIndent()
        try {
            val response = httpClient.post("http://localhost:11434/api/generate") {
                contentType(ContentType.Application.Json)
                // Use the Ollama Qwen 2.5 model for advanced entity extraction

                val jsonRequest = buildJsonObject {
                    put("prompt", prompt)
                    put("model", OLLAMA_CHAT)
                    put("stream", false)

                    put("format", buildJsonObject {
                        put("title", "LLMResponse")
                        put("type", "object")

                        put("properties", buildJsonObject {
                            put("entities", buildJsonObject {
                                put("type", "array")
                                put("items", buildJsonObject {
                                    put("\$ref", "#/\$defs/Entity")
                                })
                            })

                            put("relations", buildJsonObject {
                                put("type", "array")
                                put("items", buildJsonObject {
                                    put("\$ref", "#/\$defs/Relation")
                                })
                            })
                        })

                        putJsonArray("required") {
                            add("entities")
                            add("relations")
                        }

                        put("\$defs", buildJsonObject {
                            put("Entity", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("name", buildJsonObject { put("type", "string") })
                                    put("entType", buildJsonObject { put("type", "string") })
                                    put("description", buildJsonObject { put("type", "string") })
                                    put("source", buildJsonObject { put("type", "string") })
                                })

                                putJsonArray("required") {
                                    add("name")
                                    add("entType")
                                    add("description")
                                    add("source")
                                }
                            })

                            put("Relation", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("sourceEntity", buildJsonObject { put("type", "string") })
                                    put("targetEntity", buildJsonObject { put("type", "string") })
                                    put("relationType", buildJsonObject { put("type", "string") })
                                    put("description", buildJsonObject { put("type", "string") })
                                    put("source", buildJsonObject { put("type", "string") })
                                })

                                putJsonArray("required") {
                                    add("sourceEntity")
                                    add("targetEntity")
                                    add("relationType")
                                    add("description")
                                    add("source")
                                }
                            })
                        })
                    })
                }
                setBody(jsonRequest)
            }
            val jsonObject = jsonClient.parseToJsonElement(response.bodyAsText()).jsonObject
            val responseText = jsonObject["response"]?.jsonPrimitive?.content ?: ""
            val llmResponse = LLMResponse(responseText)
            return parseExtractionResponse(llmResponse.result)
        } catch (e: Exception) {
            println("Error during entity extraction: ${e.message}")
            throw e
        }
    }

    // Parse the JSON result into lists of Entity and Relation objects.
    private fun parseExtractionResponse(result: String): Pair<List<Entity>, List<Relation>> {
        return try {
            val jsonElement = jsonClient.parseToJsonElement(result)
            val entitiesJson = jsonElement.jsonObject["entities"] ?: JsonArray(emptyList())
            val relationsJson = jsonElement.jsonObject["relations"] ?: JsonArray(emptyList())
            val entities = jsonClient.decodeFromJsonElement<List<Entity>>(entitiesJson)
            val relations = jsonClient.decodeFromJsonElement<List<Relation>>(relationsJson)
            Pair(entities, relations)
        } catch (e: Exception) {
            println("Error parsing extraction response: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }
}

// ==================== Phase 3: Graph Construction & Deduplication (Neo4j) ====================

private object GraphService {
    private val db = Database("./cache/graph_db")
    private val conn = Connection(db)

    fun String.prep(): PreparedStatement = conn.prepare(this)

    // Create schema
    init {
        conn.execute(
            "CREATE NODE TABLE Entity(name STRING, entType STRING, description STRING, source STRING, PRIMARY KEY (name))".prep(),
            mutableMapOf()
        )
        conn.execute(
            "CREATE REL TABLE Relation(FROM Entity TO Entity, type STRING, description STRING, source STRING)".prep(),
            mutableMapOf()
        )
    }

    fun close() {
        conn.close()
    }

    // Upsert an entity (by name) into Kùzu.
    fun upsertEntity(entity: Entity) {
        val result = conn.execute(
            """
            MERGE (e:Entity {name: ${'$'}name})
            ON CREATE SET 
                e.entType = ${'$'}entType, 
                e.description = ${'$'}description, 
                e.source = ${'$'}source
            ON MATCH SET 
                e.entType = ${'$'}entType, 
                e.description = ${'$'}description, 
                e.source = ${'$'}source
            RETURN e
            """.trimIndent().prep(),
            mutableMapOf(
                "name" to entity.name,
                "entType" to entity.entType,
                "description" to entity.description,
                "source" to entity.source,
            ).map { it.key to Value(it.value) }.toMap()
        )
    }

    // Insert a relation between two entities. Assumes both entities exist.
    fun insertRelation(relation: Relation) {
        val result = conn.execute(
            """
            MATCH (a:Entity {name: ${'$'}sourceName}), (b:Entity {name: ${'$'}targetName})
            MERGE (a)-[r:Relation {type: ${'$'}relationType}]->(b)
            ON CREATE SET r.description = ${'$'}description, r.source = ${'$'}source
            ON MATCH SET r.description = ${'$'}description, r.source = ${'$'}source
            RETURN r
            """.trimIndent().prep(),
            mapOf(
                "sourceName" to relation.sourceEntity,
                "targetName" to relation.targetEntity,
                "relationType" to relation.relationType,
                "description" to relation.description,
                "source" to relation.source,
            ).map { it.key to Value(it.value) }.toMap()
        )
    }

    // Retrieve entities using a low-level keyword search (by name).
    fun retrieveEntitiesByKeyword(keyword: String): List<Entity> {
        val result = conn.execute(
            """
            MATCH (e:Entity)
            WHERE LOWER(e.name) CONTAINS LOWER(${'$'}keyword)
            RETURN e.name as name, e.entType as entType, e.description as description, e.source as source
            """.trimIndent().prep(),
            mapOf("keyword" to keyword).map { it.key to Value(it.value) }.toMap()
        )

        return buildList {
            while (result.hasNext()) {
                val value: FlatTuple = result.next
                add(
                    Entity(
                        name = value.getValue(0).toString(),
                        entType = value.getValue(1).toString(),
                        description = value.getValue(2).toString(),
                        source = value.getValue(3).toString(),
                    )
                )
            }
        }
    }

    // Retrieve entities by theme (searching within descriptions).
    fun retrieveEntitiesByTheme(theme: String): List<Entity> {
        val result = conn.execute(
            """
            MATCH (e:Entity)
            WHERE LOWER(e.description) CONTAINS LOWER(${'$'}theme)
            RETURN e.name as name, e.entType as entType, e.description as description, e.source as source
            """.trimIndent().prep(),
            mapOf("theme" to theme).map { it.key to Value(it.value) }.toMap()
        )

        return buildList {
            while (result.hasNext()) {
                val value: FlatTuple = result.next
                add(
                    Entity(
                        name = value.getValue(0).toString(),
                        entType = value.getValue(1).toString(),
                        description = value.getValue(2).toString(),
                        source = value.getValue(3).toString(),
                    )
                )
            }
        }
    }

    fun retrieveAllEntities(): List<Entity> {
        val result = conn.execute(
            """
        MATCH (e:Entity)
        RETURN e.name as name, e.entType as entType, e.description as description, e.source as source 
        """.trimIndent().prep(),
            emptyMap()
        )

        return buildList {
            while (result.hasNext()) {
                val value: FlatTuple = result.next
                add(
                    Entity(
                        name = value.getValue(0).toString(),
                        entType = value.getValue(1).toString(),
                        description = value.getValue(2).toString(),
                        source = value.getValue(3).toString(),
                    )
                )
            }
        }
    }
}


// ==================== Phase 4: Query Processing & Dual-Level Retrieval ====================

private object QueryProcessor {

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
            val response = httpClient.post("http://localhost:11434/api/generate") {
                contentType(ContentType.Application.Json)
                val jsonRequest = buildJsonObject {
                    put("prompt", prompt)
                    put("model", OLLAMA_CHAT)
                    put("stream", false)
                    put(
                        "format",
                        buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("local", buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject {
                                        put("type", "string")
                                    })
                                })
                                put("global", buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject {
                                        put("type", "string")
                                    })
                                })
                            })
                            put("required", buildJsonArray {
                                add("local")
                                add("global")
                            })
                            put("additionalProperties", false)
                        }
                    )
                }
                setBody(jsonRequest)
            }
            val jsonObject = jsonClient.parseToJsonElement(response.bodyAsText()).jsonObject
            val responseText = jsonObject["response"]?.jsonPrimitive?.content ?: ""
            val llmResponse = LLMResponse(responseText)
            return parseKeywordResponse(llmResponse.result)
        } catch (e: Exception) {
            println("Error extracting query keywords: ${e.message}")
            throw e
        }
    }

    private fun parseKeywordResponse(result: String): Pair<List<String>, List<String>> {
        return try {
            val jsonElement = jsonClient.parseToJsonElement(result)
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
            val response = httpClient.post("http://localhost:11434/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("prompt", prompt)
                    put("model", OLLAMA_CHAT)
                    put("stream", false)
                })
            }
            val jsonObject = jsonClient.parseToJsonElement(response.bodyAsText()).jsonObject
            val responseText = jsonObject["response"]?.jsonPrimitive?.content ?: ""
            val llmResponse = LLMResponse(responseText)
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
        val chunks: List<CodeBreakDown> = CodeParser.parseFile(file)
        for (chunk in chunks) {
            // Get embedding (if needed for future vector search)
            val embedding = EmbeddingService.getEmbedding(jsonClient.encodeToString(chunk))
            // Extract entities and relations using the advanced LLM extraction
            val (entities, relations) = Pair(listOf<Entity>(), listOf<Relation>())
            //EntityExtractor.extractEntitiesAndRelations(chunk)
            // Upsert entities and insert relations into the graph
            entities.forEach { GraphService.upsertEntity(it) }
            relations.forEach { GraphService.insertRelation(it) }
        }
    }
}

// ==================== Main Flow: Tying It All Together ====================


fun indexCodebase(basePath: String) = try {
    runBlocking {
        // ---------- Phase 1: Code Ingestion & Preprocessing ----------
        // Assume the Android project root is provided (adjust the path as needed)
        val sqlite = SqliteDB()
        val connection = sqlite.connectToSQLite()
        sqlite.createEmbeddingsTable(connection)
        val codeRootPath = File(basePath).absolutePath
        println(codeRootPath)
        val codeChunks = CodeParser.parseCodebase(codeRootPath)

        if (codeChunks.isEmpty()) {
            println("No code chunks extracted from the project.")
            return@runBlocking
        }

        println(codeChunks)

        // ---------- Phase 2 & 3: Process Each Code Chunk, Extract Entities & Build Graph ----------
        codeChunks.map { chunk: CodeBreakDown ->
            async {
                try {
                    // Get embedding for the chunk using Ollama Nomic
                    val embedding = EmbeddingService.getEmbedding(
                        when (chunk) {
                            is JavaFileBreakdown -> chunk.entireFileCode
                            is KotlinFileBreakdown -> chunk.entireFileCode
                            else -> ""
                        }
                    )
                    // Extract entities and relations from the code chunk using Qwen 2.5
                    val entities = mutableListOf<Entity>()
                    val relations = mutableListOf<Relation>()
                    when {
                        chunk is KotlinFileBreakdown -> {
                            chunk.kotlinTopLevelFunctions.onEach { it ->
                                val (ent, rln) = EntityExtractor.extractEntitiesAndRelations(
                                    chunk.entireFileCode,
                                    it.entireFunctionBody,
                                    chunk
                                )
                                entities.addAll(ent)
                                relations.addAll(rln)
                            }
                            chunk.kotlinTopLevelProperties.onEach {
                                val (ent, rln) = EntityExtractor.extractEntitiesAndRelations(
                                    chunk.entireFileCode,
                                    it.entirePropertyBody,
                                    chunk
                                )
                                entities.addAll(ent)
                                relations.addAll(rln)
                            }
                            chunk.kotlinClassBreakdowns.onEach { clazz ->
                                clazz.classMethods.onEach { meth ->
                                    val (ent, rln) = EntityExtractor.extractEntitiesAndRelations(
                                        clazz.entireClassBody,
                                        meth.entireMethodBody,
                                        chunk
                                    )
                                    entities.addAll(ent)
                                    relations.addAll(rln)
                                }
                                val (ent, rln) = EntityExtractor.extractEntitiesAndRelations(
                                    chunk.entireFileCode,
                                    clazz.entireClassBody,
                                    chunk
                                )
                                entities.addAll(ent)
                                relations.addAll(rln)
                            }
                        }
                    }
                    // Upsert each entity and insert relations into the Neo4j graph
                    entities.forEach {
                        println("inserting entity = $it")
                        GraphService.upsertEntity(it)
                    }
                    relations.forEach {
                        println("inserting relation = $it")
                        GraphService.insertRelation(it)
                    }
                    sqlite.insertCodeBreakdown(
                        connection, EmbeddingEntitySQLite(
                            codeBreakdown = chunk,
                            embedding = embedding
                        )
                    )
                } catch (ex: Exception) {
                    println("Error processing chunk: ${ex.message}")
                    ex.printStackTrace()
                }
            }
        }.awaitAll()

        connection.close()
    }

} catch (e: Exception) {
    e.printStackTrace()
}

fun main() = runBlocking {
    try {
        //indexCodebase("/Users/chetan.gupta/Desktop/ch8n/rough/Agentik/src/main/kotlin/01-chat-models")

        val allEntities = GraphService.retrieveAllEntities()
        println(
            """
            all entities:
            $allEntities
        """.trimIndent()
        )

        // ---------- Phase 4: Query Processing & Dual-Level Retrieval ----------
        val query = "What does AgentikModel class do?"
        val (localKeywords, globalKeywords) = QueryProcessor.extractQueryKeywords(query)

        println(localKeywords)
        println(globalKeywords)
        // Retrieve matching entities based on local keywords (entity-specific search)
        val lowLevelResults = mutableListOf<Entity>()
        for (keyword in localKeywords) {
            lowLevelResults.addAll(GraphService.retrieveEntitiesByKeyword(keyword))
        }

        println(
            """
            lowLevelResults
            $lowLevelResults
        """.trimIndent()
        )
        // Retrieve matching entities based on global keywords (thematic search)
        val highLevelResults = mutableListOf<Entity>()
        for (theme in globalKeywords) {
            highLevelResults.addAll(GraphService.retrieveEntitiesByTheme(theme))
        }

        println(
            """
            highLevelResults
            $highLevelResults
        """.trimIndent()
        )

        SqliteDB.withConnection { conn ->
            val allEmbed = fetchAllEmbeddings(conn)
            println(
                """
                all Sqlite Embed
                ${allEmbed.map { it.codeBreakdown }}
            """.trimIndent()
            )
        }

        val sementicResults = mutableListOf<CodeBreakDown>()
        SqliteDB.withConnection { conn ->
            runBlocking {
                val embedding = EmbeddingService.getEmbedding(query)
                val result = getTopNSimilarParallel(conn, embedding)
                sementicResults.addAll(result.map { it.codeBreakdown })
            }
        }

        // Combine and deduplicate results to form a comprehensive context
        val combinedEntities = (lowLevelResults + highLevelResults).distinctBy { it.name }
        val context = buildString {
            append(combinedEntities.joinToString("\n") { "${it.name} (${it.entType}): ${it.description}" })
            append(sementicResults)
        }

        println("""
            final context
            $context
        """.trimIndent())
        // ---------- Phase 5: Generate Final Answer ----------
        val answer = AnswerGenerator.generateAnswer(query, context)
        println("Final Answer:\n$answer")

//        // ---------- Advanced: Incremental Update Example ----------
//        // For demonstration, update index with a new file (adjust the path as needed)
//        val newFile = File("/path/to/your/android/project/app/src/main/java/com/example/NewFeature.kt")
//        if (newFile.exists()) {
//            IncrementalUpdater.updateIndexForNewFile(newFile)
//            println("Incremental update completed for file: ${newFile.absolutePath}")
//        } else {
//            println("New file for incremental update not found: ${newFile.absolutePath}")
//        }
    } catch (e: Exception) {
        println("An error occurred in the LightRAG process: ${e.message}")
    } finally {
        GraphService.close()
    }
}
