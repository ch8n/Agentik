============
Directory Structure:
============
├── codeExecutor
│   └── KotlinScriptExecutor.kt
├── commandline
│   └── CommandLineKtx.kt
├── extractors
│   ├── Extractors.kt
│   └── OneFile.kt
├── github
│   └── GithubKtx.kt
├── maths
│   └── MathsKtx.kt
├── webscaper
│   └── JsoupKtx.kt
├── websearch
│   └── WebSearchKtx.kt
├── youtube
│   └── YoutubeKtx.kt
├── AgentikTool.kt
└── korokoTTS.kt

============
JsoupKtx.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/webscaper/JsoupKtx.kt
============
package `02-functions`.webscaper

import `02-functions`.AgentikTool
import dev.langchain4j.agent.tool.Tool
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

fun main() {
    val site = JsoupKtx().scrapeSiteAsString("https://www.phidata.com")
    println(site)
}

class JsoupKtx : AgentikTool {

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

============
WebSearchKtx.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/websearch/WebSearchKtx.kt
============
package `02-functions`.websearch

import `02-functions`.AgentikTool
import `02-functions`.webscaper.JsoupKtx
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import dev.langchain4j.agent.tool.Tool
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




============
YoutubeKtx.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/youtube/YoutubeKtx.kt
============
package `02-functions`.youtube

import `02-functions`.AgentikTool
import `02-functions`.webscaper.JsoupKtx
import dev.langchain4j.agent.tool.Tool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

fun main() {
    val transcript =
        YoutubeKtx().englishTranscriptionContent("https://www.youtube.com/watch?v=4cCwuBsqfTI&ab_channel=PhilippLackner")
    println(transcript)
}

class YoutubeKtx : AgentikTool {

    @Tool("Returns english transcription of youtube video as string")
    fun englishTranscriptionContent(youtubeUrl: String) = englishTranscriptionUrl(youtubeUrl)
        ?.let { url ->
            println(url)
            JsoupKtx().scrape(url)?.text()
        }

    fun englishTranscriptionUrl(youtubeUrl: String) = transcriptionUrls(youtubeUrl)
        .firstOrNull { it.contains("lang=en") }

    fun transcriptionUrls(youtubeUrl: String): List<String> {
        val videoId = extractVideoId(youtubeUrl) ?: return emptyList()
        val url = "https://youtu.be/$videoId"
        println(url)
        val responseString = makeGetRequest(url) ?: return emptyList()
        return extractBaseUrls(responseString)
    }

    fun extractVideoId(youtubeUrl: String): String? {
        // Regular expressions for different common YouTube URL formats
        val regexPatterns = listOf(
            "youtube\\.com/watch\\?v=([\\w-]{11})",  // Matches https://www.youtube.com/watch?v=VIDEO_ID
            "youtu\\.be/([\\w-]{11})",               // Matches https://youtu.be/VIDEO_ID
            "youtube\\.com/embed/([\\w-]{11})",      // Matches https://www.youtube.com/embed/VIDEO_ID
            "youtube\\.com/v/([\\w-]{11})"           // Matches https://www.youtube.com/v/VIDEO_ID
        )

        for (pattern in regexPatterns) {
            val regex = Regex(pattern)
            val matchResult = regex.find(youtubeUrl)
            if (matchResult != null) {
                // Return the first captured group which is the videoId
                return matchResult.groupValues[1]
            }
        }

        // Return null if no videoId is found
        return null
    }

    fun makeGetRequest(url: String): String? {
        // Create an OkHttpClient instance
        val client = OkHttpClient()

        // Build the Request
        val request = Request.Builder()
            .url(url)
            .build()

        // Execute the request
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code $response")
        }

        // Print response details
        println("Response code: ${response.code}")

        return response.body?.string()
    }

    fun extractBaseUrls(text: String): List<String> {
        // Regular expression to match baseUrl links
        val regexPattern = """\"baseUrl\":\"(https://www\.youtube\.com/api/timedtext\?[^"]+)\""""
        val regex = Regex(regexPattern)

        // Extract matches
        return regex.findAll(text)
            .map { it.groupValues[1].replace("\\u0026", "&") }
            .distinct()
            .toList()
    }

}

============
CommandLineKtx.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/commandline/CommandLineKtx.kt
============
package `02-functions`.commandline

import `02-functions`.AgentikTool
import dev.langchain4j.agent.tool.Tool
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

fun main() {
    CommandLineKtx().runTerminalCommand("echo", "Hello, Testcontainers!")
}

class CommandLineKtx : AgentikTool {

    @Tool("runs command line operation and return output of terminal as string")
    fun runTerminalCommand(vararg commandParts: String): String? {
        val imageName = DockerImageName.parse("alpine:latest")
        val container = GenericContainer(imageName)
            .withCommand(*commandParts)
        container.start()
        val containerLog: String? = container.logs
        container.stop()
        return containerLog
    }
}

============
korokoTTS.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/korokoTTS.kt
============
package `02-functions`

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

suspend fun kokoroTTS(input: String, outFileName: String = "output") {
    // Create a client using the CIO engine
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000  // 30 seconds
            connectTimeoutMillis = 60_000  // Optional: 30s for establishing a connection
            socketTimeoutMillis = 60_000   // Optional: 30s for data transfer
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }


    try {
        // Make the API call
        val response: HttpResponse = client.post("http://localhost:8880/v1/audio/speech") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("Authorization", "Bearer not-needed")  // Replace if an API key is needed
            val requestBody = buildJsonObject {
                put("model", "kokoro")
                //put("voice", "af_bella+af_sky")
                put("voice", "hf_alpha+hf_beta+af_sky")
                put("input", input)  // Inject the input string here
                put("response_format", "mp3")
                put("lang_code", "h")
                put("speed", "1")
            }
            setBody(requestBody)
        }

        // Get the response body as bytes
        val responseBody = response.body<ByteArray>()

        // Write the response to a file
        val outFileName = outFileName.replace(" ", "_")
        File("$outFileName.mp3").writeBytes(responseBody)
        println("Audio saved as $outFileName.mp3")
    } finally {
        client.close()  // Close the client after usage
    }
}


fun main() = runBlocking {
    kokoroTTS("hi chetan kasee ho tum!")
}

============
GithubKtx.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/github/GithubKtx.kt
============
package `02-functions`.github

import `02-functions`.AgentikTool
import dev.langchain4j.agent.tool.Tool
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.ByteArrayOutputStream
import java.io.File

class GithubKtx : AgentikTool {

    @Tool("Returns File of locally cloned github repository url")
    fun cloneRepository(repoUrl: String): File? {
        val localPath = File("src/main/resources")
        println("Cloning from $repoUrl to ${localPath.absolutePath}")
        return runCatching {
            val git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(localPath)
                .call()
            git.repository.directory
        }.getOrNull()
    }

    fun getDifferencesBetweenHead(repoDirectory: File): List<DiffEntry> {
        val repository: Repository = FileRepositoryBuilder()
            .setGitDir(File(repoDirectory, ".git"))
            .build()

        Git(repository).use { git ->
            val head = repository.resolve("HEAD^{tree}")
            val treeParser = CanonicalTreeParser().apply {
                val reader = repository.newObjectReader()
                reset(reader, head)
            }

            return git.diff()
                .setOldTree(treeParser)
                .call()
                .apply {
                    val outputStream = ByteArrayOutputStream()
                    val diffFormatter = DiffFormatter(outputStream)
                    diffFormatter.setRepository(repository)
                    forEach { diffEntry ->
                        diffFormatter.format(diffEntry)
                        println(outputStream.toString("UTF-8"))
                        outputStream.reset()
                    }
                }
        }
    }

}

============
MathsKtx.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/maths/MathsKtx.kt
============
package `02-functions`.maths

import `02-functions`.AgentikTool
import dev.langchain4j.agent.tool.Tool
import kotlin.math.*


class MathsKtx : AgentikTool {
    @Tool("Returns the absolute value of a number")
    fun absoluteValueOfNumber(number: Double): Double {
        return abs(number)
    }

    @Tool("Returns minimum from list of numbers")
    fun minimumOf(vararg number: Double): Double {
        println("called minimumOf ${number.joinToString()}")
        val take1 = number.first()
        val rest = number.drop(1)
        return minOf(take1, *rest.toTypedArray())
    }

    @Tool("Returns maximum from list of numbers")
    fun maximumOf(vararg number: Double): Double {
        val take1 = number.first()
        val rest = number.drop(1)
        return maxOf(take1, *rest.toTypedArray())
    }

    @Tool("Computes the square root of a number")
    fun squareRootOf(number: Double): Double = sqrt(number)

    @Tool("Raises a number to the power of another number")
    fun powerOf(number: Double, power: Double): Double = number.pow(power).also {
        print("called powerOf $number $power")
    }

    @Tool("Round a floating point number to nearest integer, example: 2.5 to 3.0")
    fun roundOff(number: Double) = round(number)

    @Tool("Floor a floating point number to down integer, example: 2.9 to 2.0")
    fun floorOf(number: Double) = floor(number)

    @Tool("Ceil a floating point number to nearest integer, example: 2.1 to 3.0")
    fun cielOf(number: Double) = ceil(number)

    @Tool("Returns sum from list of numbers")
    fun sumOf(vararg number: Double): Double = number.sum()

    @Tool("Returns minus from list of numbers")
    fun minusOf(vararg number: Double): Double = number.reduce { acc, it -> acc - it }

    @Tool("Returns product from list of numbers")
    fun productOf(vararg number: Double): Double = number.reduce { acc, it -> acc * it }

    @Tool("Returns division of two numbers")
    fun productOf(number1: Double, number2: Double): Double = number1.div(number2)
}




============
KotlinScriptExecutor.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/codeExecutor/KotlinScriptExecutor.kt
============
package `02-functions`.codeExecutor

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class KotlinScriptExecutor {

    fun executeCode(code: String?): CommandResult? {
        code ?: return null
        return try {
            val tempDir: Path = Files.createTempDirectory("kotlin_compile_")
            println("Created temporary directory at: $tempDir")

            val kotlinFile = tempDir.resolve("DynamicProgram.kt").toFile()
            kotlinFile.writeText(code)
            println("Wrote Kotlin code to: ${kotlinFile.absolutePath}")

            val compileCommand =
                listOf("kotlinc", kotlinFile.absolutePath, "-include-runtime", "-d", "DynamicProgram.jar")
            println("Executing compile command: ${compileCommand.joinToString(" ")}")

            val compileResult = executeCommand(compileCommand, tempDir.toFile())

            println("Compiler Output:")
            println(compileResult.stdout)

            if (compileResult.exitCode != 0) {
                println("Compiler Errors:")
                println(compileResult.stderr)
                throw RuntimeException("Compilation failed with exit code ${compileResult.exitCode}")
            }

            // Step 4: Optionally, run the compiled JAR
            val runCommand = listOf("java", "-jar", "DynamicProgram.jar")
            println("Executing run command: ${runCommand.joinToString(" ")}")

            val runResult = executeCommand(runCommand, tempDir.toFile())

            println("Program Output:")
            println(runResult.stdout)

            if (runResult.exitCode != 0) {
                println("Program Errors:")
                println(runResult.stderr)
                throw RuntimeException("Program execution failed with exit code ${runResult.exitCode}")
            }

            // Step 5: Clean up temporary files (optional)
            kotlinFile.delete()
            File(tempDir.toFile(), "DynamicProgram.jar").delete()
            Files.delete(tempDir)
            println("Cleaned up temporary files.")
            runResult
        } catch (e: Exception) {
            println("IO Exception occurred: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Executes a shell command and captures its output.
     *
     * @param command The command and its arguments to execute.
     * @param workingDir The working directory where the command will be executed.
     * @return A CommandResult containing the exit code, standard output, and standard error.
     */
    fun executeCommand(command: List<String>, workingDir: File): CommandResult {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(workingDir)
        processBuilder.redirectErrorStream(false)

        println("Starting process: ${command.joinToString(" ")} in ${workingDir.absolutePath}")

        val process = processBuilder.start()

        // Capture standard output
        val stdout = process.inputStream.bufferedReader().readText()

        // Capture standard error
        val stderr = process.errorStream.bufferedReader().readText()

        // Wait for the process to complete
        val exitCode = process.waitFor()

        return CommandResult(exitCode, stdout, stderr)
    }

    /**
     * Data class to hold the result of a command execution.
     *
     * @property exitCode The exit code of the process.
     * @property stdout The standard output produced by the process.
     * @property stderr The standard error produced by the process.
     */
    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )
}

============
AgentikTool.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/AgentikTool.kt
============
package `02-functions`

interface AgentikTool

============
OneFile.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/extractors/OneFile.kt
============
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher


class OneFile() {

    fun execute(
        rootDirectory: File,
        ignorePatterns: List<String>,
        outputDirectoryPath: String = "src/main/resources",
        outfileName: String = "${rootDirectory.name}.md"
    ): File {
        if (!rootDirectory.isDirectory) throw IllegalArgumentException("The provided path is not a directory.")

        val excludePatterns = ignorePatterns
            .filter { !it.startsWith("!") }
            .map { adjustPattern(it) }

        val includePatterns = ignorePatterns
            .filter { it.startsWith("!") }
            .map { adjustPattern(it.removePrefix("!")) }

        val excludeMatchers = excludePatterns
            .map { pattern ->
                FileSystems.getDefault().getPathMatcher("glob:$pattern")
            }

        val includeMatchers = includePatterns
            .map { pattern ->
                FileSystems.getDefault().getPathMatcher("glob:$pattern")
            }

        var fileCount = 0
        var directoryCount = 0
        var totalLines = 0
        var totalWords = 0

        val outputFile = File(outputDirectoryPath, outfileName)
        outputFile.bufferedWriter().use { writer ->
            // Write directory structure
            writer.write("============\n")
            writer.write("Directory Structure:\n")
            writer.write("============\n")
            writer.write(
                getDirectoryStructure(
                    rootDirectory.toPath(),
                    rootDirectory.toPath(),
                    excludeMatchers,
                    includeMatchers
                )
            )
            writer.write("\n")

            // Write file contents
            rootDirectory.walkTopDown()
                .filter {
                    it.isFile &&
                            it.absolutePath != outputFile.absolutePath &&
                            !isExcluded(it.toPath(), rootDirectory.toPath(), excludeMatchers, includeMatchers)
                }
                .forEach { file ->
                    writer.write("============\n")
                    writer.write("${file.name} : ${file.absolutePath}\n")
                    writer.write("============\n")
                    val content = file.readText()
                    writer.write(content)
                    writer.write("\n\n")

                    fileCount++
                    totalLines += content.lineSequence().count()
                    totalWords += content.split(Regex("\\s+")).count()
                }

            // Count directories excluding the root
            directoryCount = rootDirectory.walkTopDown()
                .filter {
                    it.isDirectory &&
                            it != rootDirectory &&
                            !isExcluded(it.toPath(), rootDirectory.toPath(), excludeMatchers, includeMatchers)
                }
                .count()

            // Write summary
            writer.write("========\n")
            writer.write("Summary\n")
            writer.write("========\n")
            writer.write("Repository: ${rootDirectory.name}\n")
            writer.write("Files analyzed: $fileCount\n")
            writer.write("Directories analyzed: $directoryCount\n")
            writer.write("Total lines of content: $totalLines\n")
            writer.write("Total words: $totalWords\n")
            if (ignorePatterns.isNotEmpty()) {
                writer.write("Excluded/Inclusion patterns:\n")
                ignorePatterns.forEach { pattern ->
                    writer.write("- $pattern\n")
                }
            }
        }

        return outputFile
    }

    /**
     * Adjusts the pattern to ensure directories are fully excluded by appending '**' if needed.
     */
    private fun adjustPattern(pattern: String): String {
        return if (pattern.endsWith("/")) {
            "${pattern}**"
        } else {
            pattern
        }
    }

    /**
     * Generates a directory structure string similar to the `tree` command,
     * respecting the exclusion and inclusion patterns.
     */
    private fun getDirectoryStructure(
        currentPath: java.nio.file.Path,
        rootPath: java.nio.file.Path,
        excludeMatchers: List<PathMatcher>,
        includeMatchers: List<PathMatcher>,
        prefix: String = ""
    ): String {
        val builder = StringBuilder()
        val files = currentPath.toFile().listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: return builder.toString()

        files.forEachIndexed { index, file ->
            val isLast = index == files.lastIndex
            val branch = if (isLast) "└── " else "├── "
            val newPrefix = prefix + branch
            val childPrefix = prefix + if (isLast) "    " else "│   "

            // Get the relative path from the root directory
            val relativePath = rootPath.relativize(file.toPath())

            if (isExcluded(file.toPath(), rootPath, excludeMatchers, includeMatchers)) {
                // Skip excluded files/directories
                return@forEachIndexed
            }

            builder.append("$newPrefix${file.name}\n")
            if (file.isDirectory) {
                builder.append(
                    getDirectoryStructure(
                        file.toPath(),
                        rootPath,
                        excludeMatchers,
                        includeMatchers,
                        childPrefix
                    )
                )
            }
        }
        return builder.toString()
    }

    /**
     * Determines whether a given path should be excluded based on the excludeMatchers and includeMatchers.
     * Inclusion patterns (!patterns) override exclusion patterns.
     */
    private fun isExcluded(
        path: java.nio.file.Path,
        rootPath: java.nio.file.Path,
        excludeMatchers: List<PathMatcher>,
        includeMatchers: List<PathMatcher>
    ): Boolean {
        val relativePath = rootPath.relativize(path).toString().replace(File.separatorChar, '/')

        // Check inclusion patterns first
        if (includeMatchers.any { matcher -> matcher.matches(FileSystems.getDefault().getPath(relativePath)) }) {
            return false
        }

        // Then check exclusion patterns
        return excludeMatchers.any { matcher -> matcher.matches(FileSystems.getDefault().getPath(relativePath)) }
    }
}


fun main1() {
    val rootDirectoryPath = "/Users/chetan.gupta/Desktop/ch8n/rough/1fileKt"
    val rootDirectory = File(rootDirectoryPath)
    val ignorePatterns = mutableListOf<String>(
        "merged_content.md",
        "**/build/", // Exclude all build directories
        "build/",     // Exclude build directories at root
        "gradlew", "gradlew.bat",
        ".gradle/",   // Exclude .gradle directory and its contents
        "gradle/",    // Exclude gradle directory and its contents
        "**/resources",
        ".idea/",     // Exclude .idea directory and its contents
        ".kotlin/",   // Exclude .kotlin directory and its contents
        ".gitignore", ".git/", ".git", // Exclude git related files/directories
        "*.iws", "*.iml", "*.ipr",
        "out/",             // Exclude out directories and contents
        "**/src/main/**/out/", "**/src/test/**/out/",
        "*.classpath", "*.factorypath",
        ".apt_generated", ".project", ".settings", ".springBeans", ".sts4-cache",
        "bin/",             // Exclude bin directories and contents
        "**/src/main/**/bin/", "**/src/test/**/bin/",
        "nbproject/private/", "nbbuild/", "dist/", "nbdist/", ".nb-gradle/",
        ".vscode/",
        ".DS_Store"
    )
    val oneFile = OneFile()
    val outputFile = oneFile.execute(rootDirectory, ignorePatterns)
    print(outputFile.readText())
}

fun main() {
    val rootDirectoryPath = "/Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions"
    val rootDirectory = File(rootDirectoryPath)
    val ignorePatterns = mutableListOf<String>(
        ".fleet/", ".github/", ".gradle/", ".husky/", ".idea/",
        ".kotlin/", "build/", "**/.gradle/", "**/build/",
        "iosApp/", "kmp-xcframework-dest/", "node_modules/", ".editorconfig",
        ".env", ".gitignore", "gradle.properties", "gradlew",
        "gradlew.bat", "LICENSE", "list.json", "local.properties",
        "package-lock.json", "README.md", "yarn.lock", ".git/",
        "gradle/", ".DS_Store", "**/resources/", "composeApp/",
        "**.podspec", "cache/", ".run"
    )
    val oneFile = OneFile()
    val outputFile = oneFile.execute(
        rootDirectory,
        ignorePatterns,
        outputDirectoryPath = "cache/"
    )
    print(outputFile.readText())
}



============
Extractors.kt : /Users/chetan.gupta/Desktop/chetan/ch8n/rough/Agentik/src/main/kotlin/02-functions/extractors/Extractors.kt
============
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

========
Summary
========
Repository: 02-functions
Files analyzed: 11
Directories analyzed: 8
Total lines of content: 841
Total words: 2393
Excluded/Inclusion patterns:
- .fleet/
- .github/
- .gradle/
- .husky/
- .idea/
- .kotlin/
- build/
- **/.gradle/
- **/build/
- iosApp/
- kmp-xcframework-dest/
- node_modules/
- .editorconfig
- .env
- .gitignore
- gradle.properties
- gradlew
- gradlew.bat
- LICENSE
- list.json
- local.properties
- package-lock.json
- README.md
- yarn.lock
- .git/
- gradle/
- .DS_Store
- **/resources/
- composeApp/
- **.podspec
- cache/
- .run
