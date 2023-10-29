import java.nio.file.Path

object DeleteFiles {

    fun deleteFilesInResourcesTxt() {
        this::class.java.getResourceAsStream("delete_files.txt")
            ?.bufferedReader()
            ?.readLines()
            .orEmpty()
            .filter { it.isNotEmpty() }
            .map { Path.of(it).toFile().parentFile }
            .toSet()
            .forEach {
                println("deleting $it")
                it.deleteRecursively()
            }
    }
}

fun main() {
    DeleteFiles.deleteFilesInResourcesTxt()
}