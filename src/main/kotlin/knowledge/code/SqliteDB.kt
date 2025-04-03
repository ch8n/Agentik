package knowledge.code

import data.codeKtx.parsers.CodeBreakDown
import data.jsonClient.jsonClient
import data.ktx.byteArrayToDoubleArray
import data.ktx.cosineSimilarity
import data.ktx.doubleArrayToByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException


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

    fun connectToSQLite(dbPath: String = "./cache/VectorV2.sqlite"): Connection {
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
            pstmt.setString(1, jsonClient.encodeToString(embeddingEntity.codeBreakdown))
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
                val breakdown = jsonClient.decodeFromString<CodeBreakDown>(rs.getString("breakdown"))
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