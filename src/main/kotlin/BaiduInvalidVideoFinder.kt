import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk

/**
 * 查找有没有被 baidu pan 和谐的视频
 * 1748974 size of content is overritten by baidu.
 * 需要指定 sample 文件!
 */
@OptIn(ExperimentalPathApi::class)
fun main() {
    val sampleFilePath = Path.of("""E:\Programs\cloud back up\baidusample\sample.mp4""")
    val sampleLength = Files.size(sampleFilePath) // 1748974

    val rootPath = Path.of("""E:\Programs\cloud back up\""")

    // "mp4", "avi", "m4v", "mov", "rmvb"
    rootPath.walk().filter { it.isRegularFile() && it.extension in listOf("avi") }.forEach { path ->
        if (Files.mismatch(path, sampleFilePath) >= sampleLength) {
            // found one
            println(path)
            Files.delete(path)
        }
    }
}

