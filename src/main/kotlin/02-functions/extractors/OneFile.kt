package `02-functions`.extractors

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

