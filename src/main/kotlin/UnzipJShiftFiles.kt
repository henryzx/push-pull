import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
fun main() {
    val directoryPath = Path.of("""E:\Downloads\0qudam""")

    directoryPath.walk().filter { it.extension.lowercase() == "zip" }.forEach { path ->
        try {
            val zipFile = ZipFile(path.pathString, Charset.forName("shift_jis"))
            zipFile.stream().forEach {
                if (it.isDirectory) {
                    Files.createDirectory(directoryPath.resolve(it.name))
                } else {
                    Files.newOutputStream(directoryPath.resolve(it.name).apply { parent.createDirectories() })
                        .use { fileOutputStream ->
                            zipFile.getInputStream(it).use { inputStream ->
                                inputStream.copyTo(fileOutputStream)
                                fileOutputStream.flush()
                            }
                        }
                }
            }
        } catch (e: ZipException) {
            e.printStackTrace(System.err)
        }
    }
}