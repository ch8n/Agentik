package knowledge.nanoGraph

import KuzuDBStorage
import kotlinx.coroutines.runBlocking
import java.io.File


fun main() = runBlocking {
    val ollamaClient = OllamaClient()
    val graphStorage = KuzuDBStorage("cache/nano_graph/graph.db")
    val vectorStorage = SQLiteVectorStorage("cache/nano_graph/vectors.db") { texts ->
        ollamaClient.generateEmbeddings("nomic-embed-text", texts)
    }
    val graphRAG = GraphRAG("working_dir", ollamaClient, graphStorage, vectorStorage)
    val testFile = File("/Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/cache/02-functions.md")
    val testFileContent = testFile.readText()
    graphRAG.index(listOf(testFileContent))
    val answer = graphRAG.query("What is AI?", QueryParam(topK = 3))
    println(answer)
}