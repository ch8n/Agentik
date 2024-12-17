package functions.websearch

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.net.URLEncoder

data class SearchResult(val title: String, val url: String)

enum class SearchProvider {
    Google,
    DuckDuckGo
}

class WebSearchKtx {

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

    fun searchWeb(query: String, topK: Int, searchProvider: SearchProvider): List<SearchResult> {
        val playwright = Playwright.create()

        val browser = playwright
            .chromium()
            .launch(LaunchOptions().setHeadless(true)) // Headless mode

        // Create a new browser context and page
        val context = browser.newContext()
        val page = context.newPage()

        val searchResults = when(searchProvider){
            SearchProvider.Google -> performGoogleSearch(page, query, topK)
            SearchProvider.DuckDuckGo -> performDuckDuckGoSearch(page, query, topK)
        }

        browser.close()
        playwright.close()

        return searchResults
    }
}