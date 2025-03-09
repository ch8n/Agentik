
package knowledge.nanoGraph

class SeparatorSplitter(
    private val separators: List<String>,
    private val maxLength: Int,
    private val overlap: Int
) {
    fun splitText(text: String): List<String> {
        var currentText = text
        val chunks = mutableListOf<String>()
        for (separator in separators) {
            val parts = currentText.split(separator)
            currentText = ""
            for (part in parts) {
                if (currentText.isNotEmpty()) currentText += separator
                if (currentText.length + part.length <= maxLength) {
                    currentText += part
                } else {
                    if (currentText.isNotEmpty()) chunks.add(currentText.trim())
                    currentText = part
                }
            }
            if (currentText.isNotEmpty()) chunks.add(currentText.trim())
            currentText = chunks.joinToString(separator)
            chunks.clear()
        }
        return if (currentText.isNotEmpty()) listOf(currentText.trim()) else emptyList()
    }
}

data class TextChunkSchema(val content: String, val index: Int, val sourceId: String)