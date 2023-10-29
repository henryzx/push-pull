import utils.Bytes
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/**
 * 扫描磁盘文件，按照文件名，文件大小，列出可能重复的文件
 */
fun main(){
    val reportPath = Path.of("""E:\duplicates.csv""")
    val rootPaths: List<Path> = listOf("""D:\""", """E:\""").map { Path.of(it) }

    val groupedFile = rootPaths
        .map { it.toFile().walk() }
        .asSequence()
        .flatten()
        .filter { it.isFile && !it.isHidden && Files.size(it.toPath()) != 0L }
        .onEach { file ->
            println("$file")
        }
        .groupBy { UniqueFileKey(it.name, Files.size(it.toPath())) }
    val reportText = generateReadableReport(groupedFile.filter { it.value.size  > 1})
    reportPath.toFile().writeText(reportText, Charset.forName("gbk"))
    println("report saved to $reportPath")
}

fun generateReadableReport(groupedFile: Map<UniqueFileKey, List<File>>): String = buildString {
    groupedFile.toList().sortedByDescending { it.first.size }.flatMap { entry -> entry.second.map { entry.first to it } }.forEach { (t, u) ->
        append(t.name)
        append(", ")
        append(Bytes.format(t.size))
        append(", ")
        append(t.size)
        append(", ")
        append(u.toString())
        appendLine()
    }
}


data class UniqueFileKey(
    val name: String,
    val size: Long
)