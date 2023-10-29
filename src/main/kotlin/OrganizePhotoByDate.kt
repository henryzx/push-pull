import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.*


/**
 * 将图片文件按创建时间分组到文件夹中
 */
@OptIn(ExperimentalPathApi::class)
fun main(){
    val acceptExtensions = listOf(
                "jpg",
                "m4v",
                "mov",
                "jpeg",
                "mov",
                "heic",
                "png",
                "jpg",
                "mp4",
                "png",
                "aae",
                "rar",
                "rmvb",
                "bmp",
                "m1v",
                "mpg",
                "avi",
                "wmv",
    )
    val recoveryFolderPath = Path.of("""D:\RECOVERY_UDISK\Orphaned utils.Files""")
    val outFolderPath = Path.of("""D:\RECOVERY_UDISK\bydate""")
    Files.createDirectories(outFolderPath)

    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")
    // assume we have only one layer

    recoveryFolderPath.walk().filter { it.isRegularFile() && it.extension.lowercase() in acceptExtensions && !it.isHidden() }.forEach { filePath ->
        val attr = Files.readAttributes(filePath, BasicFileAttributes::class.java)
        val groupName = dateTimeFormatter.format(LocalDate.ofInstant(attr.creationTime().toInstant(), ZoneId.systemDefault()))
        val groupDirectory = Path.of(outFolderPath.pathString, groupName)
        Files.createDirectories(groupDirectory)
        val toPath = run {
            var path = Path.of(groupDirectory.pathString, filePath.name)
            while (path.exists() && Files.mismatch(path, filePath) != -1L) {
                path =  Path.of(groupDirectory.pathString, "another ${path.name}")
            }
            path
        }
        val success = Files.move(filePath, toPath)
        println("moved $filePath to $toPath $success")
    }
}