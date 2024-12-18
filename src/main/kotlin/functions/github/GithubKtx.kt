package functions.github

import dev.langchain4j.agent.tool.Tool
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.ByteArrayOutputStream
import java.io.File

class GithubKtx {

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