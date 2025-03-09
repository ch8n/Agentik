package knowledge.nanoGraph

import kotlinx.coroutines.runBlocking


fun main() = runBlocking {
    val ollamaClient = OllamaClient()
    val graphStorage = KuzuDBStorage("cache/nano_graph/graph.db")
    val vectorStorage = SQLiteVectorStorage("cache/nano_graph/vectors.db") { texts ->
        ollamaClient.generateEmbeddings("nomic-embed-text", texts)
    }
    val graphRAG = GraphRAG("working_dir", ollamaClient, graphStorage, vectorStorage)

    val texts = listOf("Sample text about AI and machine learning.")
    graphRAG.index(texts)
    val answer = graphRAG.query("What is AI?", QueryParam(topK = 3))
    println(answer)
}