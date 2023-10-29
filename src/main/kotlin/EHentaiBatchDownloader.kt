import kotlinx.coroutines.*
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
fun main() = runBlocking {
    val loggerDispatcher = newSingleThreadContext("logger")
    val downloadingFile = mutableListOf<String>()
    val limitedIODispatcher = Dispatchers.IO.limitedParallelism(3)
    val downloadDirPath = Path.of("""D:\Downloads""")
    var documentUrl = "https://e-hentai.org/s/1a63af4b21/2714731-1"
    val session = Jsoup.newSession()
        .proxy("127.0.0.1", 7890)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        )
        .timeout(Duration.ofMinutes(5L).toMillis().toInt())
    while (true) {
        val fileName = Path.of(URL(documentUrl).file).last().nameWithoutExtension
        val document = withContext(Dispatchers.IO) { session.newRequest().url(documentUrl).get() }
        val imageSrcUrl = document.selectFirst("#img")?.attr("src")
        if (imageSrcUrl != null) {
            launch(limitedIODispatcher) {
                val downloadFilePath = downloadDirPath.resolve("$fileName.jpg")
                launch(loggerDispatcher) {
                    downloadingFile.add(downloadFilePath.pathString)
                    println("downloading task: $downloadingFile")
                }
                runCatching {
                    enqueueImageUrl(
                        imageSrcUrl,
                        downloadFilePath,
                        session
                    )
                }.exceptionOrNull()?.let { e ->
                    System.err.println("error while downloading $downloadFilePath:\n${e.stackTraceToString()}")
                }
                launch(loggerDispatcher) {
                    downloadingFile.remove(downloadFilePath.pathString)
                    println("downloading task: $downloadingFile")
                }
            }
        }
        val nextDocumentUrl = document.selectFirst("#next")?.attr("href")
        if (nextDocumentUrl == null || nextDocumentUrl == documentUrl) {
            break
        }
        documentUrl = nextDocumentUrl
    }
}

fun enqueueImageUrl(imageUrl: String, downloadDirPath: Path, session: Connection) {
    if (downloadDirPath.exists()) return
    val tempFile = Files.createTempFile("e-hentai-", ".jpg")

    session.newRequest()
        .url(imageUrl)
        .ignoreContentType(true)
        .header("Referer", "https://e-hentai.org/")
        .execute()
        .bodyStream()
        .use { inputStream ->
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)
        }

    Files.move(tempFile, downloadDirPath)
}
