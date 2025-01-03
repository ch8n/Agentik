package functions.websearch

import agent.AgentikTool
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import dev.langchain4j.agent.tool.Tool
import functions.webscaper.JsoupKtx
import java.net.URLEncoder

data class SearchResult(val title: String, val url: String)

enum class SearchProvider {
    Google,
    DuckDuckGo
}


class WebSearchKtx : AgentikTool {

    fun performDuckDuckGoSearch(page: Page, query: String, topK: Int): List<SearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"

        // Navigate to DuckDuckGo's HTML version
        page.navigate(url)

        // Wait for search results to load
        page.waitForSelector("a.result__a")

        // Extract search results
        val results = mutableListOf<SearchResult>()

        val resultElements = page.querySelectorAll("a.result__a")

        for (element in resultElements) {
            if (results.size >= topK) break
            val title = element.textContent()?.trim() ?: "No title"
            val href = element.getAttribute("href") ?: "No URL"
            results.add(SearchResult(title, href))
        }

        page.close()

        return results
    }

    fun performGoogleSearch(page: Page, query: String, topK: Int): List<SearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.google.com/search?q=$encodedQuery"

        // Navigate to Google
        page.navigate(url)

        // Wait for search results to load
        page.waitForSelector("div.g") // Google's search results are within divs with class 'g'

        // Extract search results
        val results = mutableListOf<SearchResult>()

        val resultElements = page.querySelectorAll("div.g")

        for (element in resultElements) {
            if (results.size >= topK) break
            val aTag = element.querySelector("a") ?: continue
            val title = aTag.textContent()?.trim() ?: "No title"
            val href = aTag.getAttribute("href") ?: "No URL"
            results.add(SearchResult(title, href))
        }

        // Close the page to free resources
        page.close()

        return results
    }


    fun searchWebForResult(searchQuery: String, minimumSearchResultToLookFor: Int, searchProvider: SearchProvider): List<SearchResult> {
        println("searchWebForResult called $searchQuery $minimumSearchResultToLookFor $searchProvider")
        val playwright = Playwright.create()

        val minimumSearchResultToLookFor = if (minimumSearchResultToLookFor == 0) 5 else minimumSearchResultToLookFor

        val browser = playwright
            .chromium()
            .launch(LaunchOptions().setHeadless(false)) // Headless mode

        // Create a new browser context and page
        val context = browser.newContext()
        val page = context.newPage()

        val searchResults = when(searchProvider){
            SearchProvider.Google -> performGoogleSearch(page, searchQuery, minimumSearchResultToLookFor)
            SearchProvider.DuckDuckGo -> performDuckDuckGoSearch(page, searchQuery, minimumSearchResultToLookFor)
        }

        browser.close()
        playwright.close()

        return searchResults
    }

    @Tool
    fun searchWeb(searchQuery: String): List<String> {
        println("searchWeb query: $searchQuery")
        val results = searchWebForResult(searchQuery, 3, SearchProvider.Google)
        println("searchResults $results")
        val jsoup = JsoupKtx()
        return results.map {
            """
                title: ${it.title}
                content: ${jsoup.scrapeSiteAsString(it.url)}
            """.trimIndent()
        }
    }

}

fun main() {
    val results = WebSearchKtx().searchWeb("what is kotlin kmp?")
    println(results.joinToString("\n"))
}


