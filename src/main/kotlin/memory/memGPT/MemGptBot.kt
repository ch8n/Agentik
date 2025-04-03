package memory.memGPT

import `01-chat-models`.OllamaAgentikModel
import `03-agents`.Agentik
import data.ktx.doubleArrayToByteArray
import data.llmServers.OllamaEmbeddingServer
import data.sqlite.SqliteKtx
import data.sqlite.SqliteKtx.executeOperation
import data.sqlite.SqliteKtx.executeQuery
import kotlinx.coroutines.runBlocking
import java.sql.ResultSet
import java.sql.Statement

/**
 * Initializes the SQLite database, loads the sqlite-vec extension, and creates tables.
 */
fun initializeDatabase(dbPath: String = "./cache/VectorV2.sqlite", extensionPath: String) {
    SqliteKtx.withConnection(dbPath) {
        executeOperation("SELECT load_extension('$extensionPath')")

        // Create messages table
        executeQuery(
            """
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
            content TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """.trimIndent()
        )

        // Create virtual table for embeddings using sqlite-vec
        // Assuming 384 dimensions (common for models like all-MiniLM)
        executeQuery(
            """
        CREATE VIRTUAL TABLE IF NOT EXISTS message_embeddings 
        USING vec0(embedding FLOAT[384])
    """.trimIndent()
        )
    }
}

/**
 * Stores a message and its embedding in the database.
 */
fun storeMessage(
    role: String,
    content: String
) {
    SqliteKtx.withConnection {
        // Insert into messages table
        val insertMessageSql = "INSERT INTO messages (role, content) VALUES (?, ?)"
        val generatedKeys: ResultSet = prepareStatement(insertMessageSql, Statement.RETURN_GENERATED_KEYS)
            .use { messageStmt ->
                messageStmt.setString(1, role)
                messageStmt.setString(2, content)
                messageStmt.executeUpdate()
                messageStmt.generatedKeys
            }


        if (generatedKeys.next()) {
            val messageId = generatedKeys.getLong(1)

            // Generate embedding using Ollama
            val embedding = generateEmbeddings(content)
            val embeddingBlob = doubleArrayToByteArray(embedding)

            // Insert into message_embeddings table
            val insertEmbeddingSql = "INSERT INTO message_embeddings (rowid, embedding) VALUES (?, ?)"
            prepareStatement(insertEmbeddingSql).use { embeddingStmt ->
                embeddingStmt.setLong(1, messageId)
                embeddingStmt.setBytes(2, embeddingBlob)
                embeddingStmt.executeUpdate()
            }
        }
    }


}

/**
 * Simulates embedding generation. Replace with actual Ollama /api/embeddings call if needed.
 */
fun generateEmbeddings(text: String): DoubleArray {
    return runBlocking { OllamaEmbeddingServer.getEmbedding(text) }
}

/**
 * Retrieves the most relevant message IDs based on vector similarity to the query.
 */
fun retrieveRelevantMessages(
    query: String,
    limit: Int = 5
): List<Pair<Long, Double>> {
    val queryEmbedding = generateEmbeddings(query)
    val queryBlob = doubleArrayToByteArray(queryEmbedding)
    val results = mutableListOf<Pair<Long, Double>>()
    SqliteKtx.withConnection {
        val selectSql = """
        SELECT rowid, distance
        FROM message_embeddings
        WHERE embedding MATCH ?
        ORDER BY distance ASC
        LIMIT ?
    """.trimIndent()

        prepareStatement(selectSql).use { stmt ->
            stmt.setBytes(1, queryBlob)
            stmt.setInt(2, limit)
            val resultSet = stmt.executeQuery()
            while (resultSet.next()) {
                val rowid = resultSet.getLong("rowid")
                val distance = resultSet.getDouble("distance")
                results.add(Pair(rowid, distance))
            }
        }
    }
    return results
}

/**
 * Fetches message content by IDs.
 */
fun getMessagesByIds(ids: List<Long>): List<String> {
    if (ids.isEmpty()) return emptyList()
    val messages = mutableListOf<String>()
    SqliteKtx.withConnection {
        val selectSql = "SELECT content FROM messages WHERE id IN (${ids.joinToString(",")}) ORDER BY id"
        val resultSet = createStatement().executeQuery(selectSql)
        while (resultSet.next()) {
            messages.add(resultSet.getString("content"))
        }
    }
    return messages
}

/**
 * Generates a response by retrieving relevant context and querying the LLM.
 */
fun generateResponse(
    agent: Agentik,
    userInput: String
): String {
    // Store the user's input
    storeMessage("user", userInput)

    // Retrieve relevant past messages
    val relevantIds: List<Pair<Long, Double>> = retrieveRelevantMessages(userInput)
    val relevantMessages = getMessagesByIds(relevantIds.map { it.first })

    // Construct context
    val context = buildString {
        append(relevantMessages.joinToString("\n"))
        if (relevantMessages.isNotEmpty()) append("\n")
        append("User: $userInput\nAssistant:")
    }

    // Generate response using Ollama
    val response = agent.execute(context)

    // Store the assistant's response
    storeMessage("assistant", response)
    return response
}

/**
 * Main function to run the MemGPT conversational agent.
 */
fun main() {
    val dbPath = "memgpt.db"
    val extensionPath = "path/to/sqlite-vec.so" // Update this path
    val llmAgent = Agentik(chatModel = OllamaAgentikModel)
    // Initialize database
    initializeDatabase(dbPath, extensionPath)

    println("MemGPT Chatbot started. Type 'exit' to quit.")

    // Conversational loop
    while (true) {
        print("User: ")
        val userInput = readLine()?.trim() ?: continue
        if (userInput.equals("exit", ignoreCase = true)) break

        try {
            val response = generateResponse(llmAgent, userInput)
            println("Assistant: $response")
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
    println("Chatbot stopped.")
}