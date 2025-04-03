package data.ktx

import kotlin.math.sqrt

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