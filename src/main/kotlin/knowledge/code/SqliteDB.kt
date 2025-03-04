package knowledge.code

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.math.sqrt
import kotlin.use

fun cosineSimilarity(vec1: DoubleArray, vec2: DoubleArray): Double {
    require(vec1.size == vec2.size) { "Vectors must be of same length" }
    var dotProduct = 0.0
    var normA = 0.0
    var normB = 0.0

    for (i in vec1.indices) {
        dotProduct += vec1[i] * vec2[i]
        normA += vec1[i] * vec1[i]
        normB += vec2[i] * vec2[i]
    }
    return if (normA == 0.0 || normB == 0.0) 0.0 else dotProduct / (sqrt(normA) * sqrt(normB))
}

data class EmbeddingEntitySQLite(
    val codeBreakdown: CodeBreakDown,
    val embedding: DoubleArray
)


class SqliteDB {

    companion object {
        fun withConnection(block: SqliteDB.(connection: Connection) -> Unit) {
            val sqliteDB = SqliteDB()
            val connection: Connection = sqliteDB.connectToSQLite()
            sqliteDB.createEmbeddingsTable(connection = connection)
            block.invoke(sqliteDB, connection)
            connection.close()
        }
    }

    fun connectToSQLite(dbPath: String = "VectorV2.sqlite"): Connection {
        val url = "jdbc:sqlite:$dbPath"
        return DriverManager.getConnection(url)
    }

    fun createEmbeddingsTable(connection: Connection) {
        val createTableSQL = """
        CREATE TABLE IF NOT EXISTS embeddings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            breakdown TEXT NOT NULL,
            embedding BLOB NOT NULL
        );
    """.trimIndent()

        connection.createStatement().use { stmt ->
            stmt.execute(createTableSQL)
        }
        println("Embeddings table is ready.")
    }

    fun close(connection: Connection) {
        connection.close()
    }

    /**
     * Converts a FloatArray to ByteArray.
     */
    fun doubleArrayToByteArray(embedding: DoubleArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(embedding.size * 8)
        byteBuffer.order(ByteOrder.BIG_ENDIAN)
        for (double in embedding) {
            byteBuffer.putDouble(double)
        }
        return byteBuffer.array()
    }

    /**
     * Converts ByteArray to FloatArray.
     */
    fun byteArrayToDoubleArray(bytes: ByteArray): DoubleArray {
        require(bytes.size % 8 == 0) { "Byte array size must be a multiple of 8." }

        val doubleArray = DoubleArray(bytes.size / 8)
        val byteBuffer = ByteBuffer.wrap(bytes)
        byteBuffer.order(ByteOrder.BIG_ENDIAN)
        for (i in doubleArray.indices) {
            doubleArray[i] = byteBuffer.double
        }
        return doubleArray
    }

    // Function to insert an embedded component into SQLite
    fun insertCodeBreakdown(
        connection: Connection,
        embeddingEntity: EmbeddingEntitySQLite
    ): Long {
        val insertSQL = """
        INSERT INTO embeddings (breakdown,embedding)
        VALUES (?,?);
    """.trimIndent()

        connection.prepareStatement(insertSQL, PreparedStatement.RETURN_GENERATED_KEYS).use { pstmt ->
            pstmt.setString(1, Json.encodeToString(embeddingEntity.codeBreakdown))
            pstmt.setBytes(2, doubleArrayToByteArray(embeddingEntity.embedding))
            val affectedRows = pstmt.executeUpdate()

            if (affectedRows == 0) {
                throw SQLException("Inserting embedded component failed, no rows affected.")
            }

            val generatedKeys = pstmt.generatedKeys
            if (generatedKeys.next()) {
                return generatedKeys.getLong(1)
            } else {
                throw SQLException("Inserting embedded component failed, no ID obtained.")
            }
        }
    }


    // Function to fetch all embeddings
    fun fetchAllEmbeddings(connection: Connection): List<EmbeddingEntitySQLite> {
        val selectSQL = "SELECT id, breakdown, embedding FROM embeddings;"
        val embeddings = mutableListOf<EmbeddingEntitySQLite>()

        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(selectSQL)
            while (rs.next()) {
                val id = rs.getLong("id")
                val breakdown = Json.decodeFromString<CodeBreakDown>(rs.getString("breakdown"))
                val embeddingBytes = rs.getBytes("embedding")
                val embedding = byteArrayToDoubleArray(embeddingBytes)
                embeddings.add(
                    EmbeddingEntitySQLite(
                        codeBreakdown = breakdown,
                        embedding = embedding
                    )
                )
            }
        }
        return embeddings
    }

    suspend fun getTopNSimilarParallel(
        connection: Connection,
        queryEmbedding: DoubleArray,
        topN: Int = 5
    ): List<EmbeddingEntitySQLite> = withContext(Dispatchers.Default) {
        val allEmbeddings = fetchAllEmbeddings(connection)

        // Compute similarities in parallel
        val similarities = allEmbeddings.map { entity ->
            async {
                val similarity = cosineSimilarity(queryEmbedding, entity.embedding)
                Pair(entity, similarity)
            }
        }.awaitAll()

        similarities
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
    }
}