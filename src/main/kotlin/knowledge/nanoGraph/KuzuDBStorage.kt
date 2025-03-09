import com.kuzudb.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import nl.cwts.networkanalysis.LeidenAlgorithm
import nl.cwts.networkanalysis.Network
import org.jgrapht.alg.clustering.LabelPropagationClustering
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleWeightedGraph
import java.util.*



private val logger = KotlinLogging.logger {}

data class SingleCommunitySchema(
    val level: Int?,
    val title: String?,
    val nodes: List<String>,
    val edges: List<List<String>>,
    val chunkIds: List<String>,
    val occurrence: Float,
    val subCommunities: List<String>
)

interface BaseGraphStorage {
    suspend fun hasNode(nodeId: String): Boolean
    suspend fun hasEdge(sourceId: String, targetId: String): Boolean
    suspend fun getNode(nodeId: String): Map<String, Any>?
    suspend fun upsertNode(nodeId: String, nodeData: Map<String, Any>)
    suspend fun upsertEdge(sourceId: String, targetId: String, edgeData: Map<String, Any>)
    suspend fun clustering()
    suspend fun communitySchema(level: Int = 0, parentCommunity: String? = null): Map<String, SingleCommunitySchema>
}

class KuzuDBStorage(private val dbPath: String) : BaseGraphStorage {
    private val conn: Connection

    init {
        val db = Database(dbPath)
        conn = Connection(db) // Updated for KùzuDB 0.8.0
        initializeSchema()
    }

    private fun initializeSchema() {
        conn.query("CREATE NODE TABLE IF NOT EXISTS Entity(id STRING, entity_type STRING, description STRING, source_id STRING, communityId STRING, subCommunityId STRING, PRIMARY KEY (id))")
        conn.query("CREATE REL TABLE IF NOT EXISTS RELATED(FROM Entity TO Entity, weight FLOAT, description STRING, source_id STRING)")
    }

    override suspend fun hasNode(nodeId: String): Boolean = withContext(Dispatchers.IO) {
        val query = "MATCH (n:Entity {id: '$nodeId'}) RETURN EXISTS(n)"
        val result = conn.query(query)
        var exists = false
        while (result.hasNext()) {
            val tuple = result.getNext()
            exists = tuple.getValue(0).toString().toBooleanStrict()
        }
        exists
    }

    override suspend fun hasEdge(sourceId: String, targetId: String): Boolean = withContext(Dispatchers.IO) {
        val query = "MATCH (s:Entity {id: '$sourceId'})-[r:RELATED]->(t:Entity {id: '$targetId'}) RETURN EXISTS(r)"
        val result = conn.query(query)
        var exists = false
        while (result.hasNext()) {
            val tuple = result.getNext()
            exists = tuple.getValue(0).toString().toBooleanStrict()
        }
        exists
    }

    override suspend fun getNode(nodeId: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val query = "MATCH (n:Entity {id: '$nodeId'}) RETURN n.id, n.entity_type, n.description, n.source_id, n.communityId, n.subCommunityId"
        val result = conn.query(query)
        if (result.hasNext()) {
            val tuple = result.getNext()
            mapOf(
                "id" to tuple.getValue(0).toString(),
                "entity_type" to tuple.getValue(1).toString(),
                "description" to tuple.getValue(2).toString(),
                "source_id" to tuple.getValue(3).toString(),
                "communityId" to tuple.getValue(4).toString(),
                "subCommunityId" to tuple.getValue(5).toString()
            )
        } else {
            null
        }
    }

    override suspend fun upsertNode(nodeId: String, nodeData: Map<String, Any>): Unit = withContext(Dispatchers.IO) {
        val props = nodeData.entries.joinToString(", ") { "${it.key}: '${it.value}'" }
        val query =
            "MERGE (n:Entity {id: '$nodeId'}) ON CREATE SET n = {id: '$nodeId', $props} ON MATCH SET n += {$props}"
        conn.query(query)
    }

    override suspend fun upsertEdge(sourceId: String, targetId: String, edgeData: Map<String, Any>): Unit =
        withContext(Dispatchers.IO) {
            val props = edgeData.entries.joinToString(", ") { "${it.key}: '${it.value}'" }
            val query = """
            MATCH (s:Entity {id: '$sourceId'}), (t:Entity {id: '$targetId'})
            MERGE (s)-[r:RELATED]->(t)
            ON CREATE SET r = {$props}
            ON MATCH SET r += {$props}
        """.trimIndent()
            conn.query(query)
        }

    /** Helper function to copy a FlatTuple's values into a List<Value> */
    private fun copyFlatTuple(tuple: FlatTuple, tupleLen: Long): List<Value> {
        val ret = mutableListOf<Value>()
        for (i in 0 until tupleLen) {
            ret.add(tuple.getValue(i).clone()) // Clone each value
        }
        return ret
    }

    override suspend fun clustering() = withContext(Dispatchers.IO) {
        val nodesQuery = "MATCH (n:Entity) RETURN n.id"
        val nodesResult = conn.query(nodesQuery)
        val nodes = mutableListOf<String>()
        while (nodesResult.hasNext()) {
            val tuple = nodesResult.getNext()
            nodes.add(tuple.getValue(0).toString())
        }

        // Extract edges with weights
        val edgesQuery = "MATCH (s:Entity)-[r:RELATED]->(t:Entity) RETURN s.id, t.id, r.weight"
        val edgesResult = conn.query(edgesQuery)
        val edges = mutableListOf<Triple<String, String, Float>>()
        while (edgesResult.hasNext()) {
            val tuple = edgesResult.getNext()
            edges.add(Triple(tuple.getValue(0).toString(), tuple.getValue(1).toString(), tuple.getValue(2).toString().toFloat()))
        }

        // Build the JGraphT graph
        val graph = SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
        nodes.forEach { graph.addVertex(it) }
        edges.forEach { (src, tgt, weight) ->
            val edge = graph.addEdge(src, tgt)
            if (edge != null) {
                graph.setEdgeWeight(edge, weight.toDouble())
            }
        }

        // Apply Label Propagation algorithm
        val labelPropagation = LabelPropagationClustering(graph, 10) // 10 iterations
        val communities = labelPropagation.clustering

        // Assign community IDs to nodes in the database
        communities.forEachIndexed { commId, community ->
            community.forEach { nodeId ->
                conn.query("MATCH (n:Entity {id: '$nodeId'}) SET n.communityId = 'community_$commId'")
            }
        }
    }

    override suspend fun communitySchema(level: Int, parentCommunity: String?): Map<String, SingleCommunitySchema> =
        withContext(Dispatchers.IO) {
            // Get community IDs
            val communitiesQuery = if (parentCommunity == null) {
                "MATCH (n:Entity) WHERE n.communityId IS NOT NULL RETURN DISTINCT n.communityId"
            } else {
                "MATCH (n:Entity {communityId: '$parentCommunity'}) RETURN DISTINCT n.subCommunityId"
            }
            val communitiesResult = conn.query(communitiesQuery)
            val communityIds = mutableListOf<String>()
            while (communitiesResult.hasNext()) {
                val tuple = communitiesResult.getNext()
                communityIds.add(tuple.getValue(0).toString()) // Process immediately
            }

            // Build schema for each community
            communityIds.associateWith { commId ->
                // Get nodes
                val nodesQuery = "MATCH (n:Entity {communityId: '$commId'}) RETURN n.id"
                val nodesResult = conn.query(nodesQuery)
                val nodes = mutableListOf<String>()
                while (nodesResult.hasNext()) {
                    val tuple = nodesResult.getNext()
                    nodes.add(tuple.getValue(0).toString()) // Process immediately
                }

                // Get edges
                val edgesQuery =
                    "MATCH (s:Entity {communityId: '$commId'})-[r:RELATED]->(t:Entity {communityId: '$commId'}) RETURN s.id, t.id"
                val edgesResult = conn.query(edgesQuery)
                val edges = mutableListOf<List<String>>()
                while (edgesResult.hasNext()) {
                    val tuple = edgesResult.getNext()
                    edges.add(
                        listOf(
                            tuple.getValue(0).toString(),
                            tuple.getValue(1).toString()
                        )
                    ) // Process immediately
                }

                // Recursively get sub-communities
                val subCommunities = if (level < 2) communitySchema(level + 1, commId) else emptyMap()

                SingleCommunitySchema(
                    level = level,
                    title = "Community $commId",
                    nodes = nodes,
                    edges = edges,
                    chunkIds = nodes.map { "chunk_$it" },
                    occurrence = nodes.size.toFloat(),
                    subCommunities = subCommunities.keys.toList()
                )
            }
        }
}