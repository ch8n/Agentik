package `02-functions`.extractors

import java.util.regex.Pattern

fun extractJsonFromMarkdown(markdownContent: String): String {
    // Define the regex pattern for matching JSON block
    val jsonBlockRegex = Regex("```json\\s*([\\s\\S]*?)```")
    // Try to find the first match
    val matchResult = jsonBlockRegex.find(markdownContent)
    // If a match is found, return the extracted JSON content, else return an empty string
    return matchResult?.groups?.get(1)?.value?.trim() ?: ""
}


fun main() {
    extractJsonFromMarkdown(
        """
        ```json
        {"name":"chetan"}
        ```
    """.trimIndent()
    ).let(::println)
}