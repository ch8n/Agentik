package knowledge.nanoGraph

import org.sqlite.SQLiteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

interface BaseVectorStorage {
    suspend fun upsert(data: Map<String, Map<String, Any>>)
    suspend fun query(query: String, topK: Int): List<Map<String, Any>>
}

class SQLiteVectorStorage(
    private val dbPath: String,
    private val embeddingFunc: suspend (List<String>) -> List<List<Float>>
) : BaseVectorStorage {
    private val conn: java.sql.Connection

    init {
        val ds = SQLiteDataSource()
        ds.url = "jdbc:sqlite:$dbPath"
        conn = ds.getConnection()
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS vectors (
                id TEXT PRIMARY KEY,
                content TEXT,
                embedding TEXT,
                metadata TEXT
            )
        """.trimIndent())
    }

    override suspend fun upsert(data: Map<String, Map<String, Any>>):Unit = withContext(Dispatchers.IO) {
        val contents = data.values.map { it["content"] as String }
        val embeddings = embeddingFunc(contents)
        val stmt = conn.prepareStatement("INSERT OR REPLACE INTO vectors (id, content, embedding, metadata) VALUES (?, ?, ?, ?)")
        data.entries.forEachIndexed { index, (id, value) ->
            stmt.setString(1, id)
            stmt.setString(2, value["content"] as String)
            stmt.setString(3, embeddings[index].joinToString(","))
            stmt.setString(4, Json.encodeToString(value.filterKeys { it != "content" }))
            stmt.addBatch()
        }
        stmt.executeBatch()
    }

    override suspend fun query(query: String, topK: Int): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val queryEmbedding = embeddingFunc(listOf(query)).first()
        val stmt = conn.createStatement()
        val rs = stmt.executeQuery("SELECT id, content, embedding, metadata FROM vectors")
        val results = mutableListOf<Pair<Map<String, Any>, Float>>()
        while (rs.next()) {
            val id = rs.getString("id")
            val content = rs.getString("content")
            val embedding = rs.getString("embedding").split(",").map { it.toFloat() }
            val metadata = Json.decodeFromString<Map<String, Any>>(rs.getString("metadata"))
            val similarity = cosineSimilarity(queryEmbedding, embedding)
            results.add((mapOf("id" to id, "content" to content) + metadata) to similarity)
        }
        results.sortedByDescending { it.second }.take(topK).map { it.first + ("similarity" to it.second) }
    }

    private fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float {
        val dotProduct = vec1.zip(vec2).sumOf { it.first * it.second.toDouble() }.toFloat()
        val norm1 = sqrt(vec1.sumOf { it * it.toDouble() }.toFloat())
        val norm2 = sqrt(vec2.sumOf { it * it.toDouble() }.toFloat())
        return if (norm1 == 0f || norm2 == 0f) 0f else dotProduct / (norm1 * norm2)
    }
}