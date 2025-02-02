package knowledge.vectorDB

import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.sqrt


class Sqlite(dbName: String) {
    private val dbUrl = "jdbc:sqlite:$dbName.db"

    fun <R> connection(query: Connection.() -> R): R {
        val connection = DriverManager.getConnection(dbUrl)
        return connection.use { query.invoke(connection) }
    }
}

class CodeEmbedding {

}

class SimilaritySearch {
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
}
