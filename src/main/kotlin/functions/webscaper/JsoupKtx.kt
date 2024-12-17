package functions.webscaper

import dev.langchain4j.agent.tool.Tool
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

fun main() {
    val site = JsoupKtx().scrapeSiteAsString("https://www.phidata.com")
    println(site)
}

class JsoupKtx {

    fun scrape(url: String): Document? {
        return runCatching {
            Jsoup.connect(url)
                .userAgent(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/58.0.3029.110 Safari/537.3"
                )
                .timeout(10_000) // 10 seconds timeout
                .get()
        }.getOrNull()
    }

    @Tool("Scrapes the provided URL and returns entire website body as string")
    fun scrapeSiteAsString(url: String): String {
        return scrape(url)?.body()?.text() ?: "failed to scape site $url"
    }

    @Tool("Scrapes site from provided URL and returns list of string urls")
    fun extractImagesFromSite(url: String): List<String> {
        return scrape(url)?.select("img[src]")
            ?.map { it.attr("abs:src") }
            ?.distinct() ?: emptyList()
    }

    @Tool("Scrapes site from provided URL and returns list of links in the page")
    fun extractLinks(url: String): List<String> {
        return scrape(url)?.select("a[href]")
            ?.map { it.attr("abs:href") }
            ?.distinct() ?: emptyList()
    }

    @Tool("Scrapes site from provided URL and returns page title")
    fun extractPageTitle(url: String): String {
        return scrape(url)?.title() ?: "failed to scrape title from site: $url"
    }

}