package onedrive

import com.azure.identity.InteractiveBrowserCredentialBuilder
import com.microsoft.graph.authentication.TokenCredentialAuthProvider
import com.microsoft.graph.httpcore.HttpClients
import com.microsoft.graph.models.DriveItem
import com.microsoft.graph.options.HeaderOption
import com.microsoft.graph.requests.DriveItemCollectionPage
import com.microsoft.graph.requests.DriveItemContentStreamRequestBuilder
import com.microsoft.graph.requests.GraphServiceClient
import gdrive.DownloadTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import okhttp3.Request
import utils.FileUtils
import utils.csvReader
import utils.csvWriter
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration


@OptIn(ExperimentalCoroutinesApi::class)
fun main(): Unit = runBlocking {

    val downloadDirPath = Path.of("D:\\Downloads\\dtxnew").createDirectories()

    val driveId = "f625ba2aa5c74b41"

    val downloadItem: GraphServiceClient<Request>.(String) -> DriveItemContentStreamRequestBuilder = { itemId ->
        drives(driveId).items(itemId).content()
    }

    val credential = InteractiveBrowserCredentialBuilder()
        .clientId(MyKeychain.clientId).tenantId("consumers").redirectUrl("http://localhost")
        .build()


    val authProvider = TokenCredentialAuthProvider(
        MyKeychain.scope.toList(), credential
    )

    val graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .httpClient(
            HttpClients.createDefault(authProvider).newBuilder()
                //.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)))
                .connectTimeout(1.minutes.toJavaDuration())
                .readTimeout(30.minutes.toJavaDuration())
                .callTimeout(1.hours.toJavaDuration())
                .build()
        )
        .buildClient()


    println("Walking download tasks")
    val taskListPath = downloadDirPath.resolve("download-tasks.csv")
    if (taskListPath.notExists()) {
        csvWriter(taskListPath) { csvWriter ->
            walkDriveChildren(graphClient, driveId, "F625BA2AA5C74B41!1848", Path.of("")).collect {
                csvWriter.writeNext(
                    DownloadTask(
                        requireNotNull(it.driveItem.id),
                        requireNotNull(it.driveItem.name),
                        requireNotNull(it.path.invariantSeparatorsPathString),
                        requireNotNull(it.driveItem.file?.mimeType),
                        requireNotNull(it.driveItem.file?.hashes?.sha1Hash),
                    ).marshall()
                )
            }
        }
    }

    println(graphClient.drive().buildRequest().get()?.owner)

    println("Reading download tasks and checking")
    val downloadTaskList = csvReader(taskListPath) { csvReader ->
        csvReader.readAll().map { DownloadTask.parse(it) }
    }
    val downloadTasks = downloadTaskList.asFlow().map { task ->
        flow {
            val fileName = requireNotNull(task.name)
            val filePath = downloadDirPath.resolve(task.relativePath).resolve(fileName)
            if (filePath.exists()) {
                val digest = FileUtils.digestFile(filePath).lowercase()
                val expectedDigest = task.sha1Sum.lowercase()
                if (digest == expectedDigest) {
                    // skip this file
                    println("file $filePath OK")
                    return@flow
                } else {
                    println("file is not matching $filePath: $digest expected $expectedDigest")
                }
            }
            emit(task)
        }
    }.flattenMerge()

    println("Processing download tasks of total ${downloadTaskList.size}")
    downloadTasks.map { task ->
        flow<Unit> {
            val itemId = requireNotNull(task.id)
            val fileName = requireNotNull(task.name)
            val filePath =
                downloadDirPath.resolve(task.relativePath).apply { createDirectories() }.resolve(fileName)

            withContext(Dispatchers.IO) {
                try {
                    graphClient.downloadItem(itemId)
                        .buildRequest(HeaderOption("Accept-Encoding", "identity")).async.await()?.use { inputStream ->
                        FileOutputStream(filePath.toFile()).use { outputStream ->
                            println("Downloading ${filePath.pathString} ${Thread.currentThread().name}")
                            inputStream.transferTo(outputStream)
                            outputStream.flush()
                            println("Downloading ${filePath.pathString} ${Thread.currentThread().name} OK")
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace(System.err)
                    // try clean up
                    filePath.deleteIfExists()
                }
            }
        }
    }.flattenMerge(concurrency = 5).collect()

    Thread.sleep(Duration.ofDays(1).toMillis())
}

data class DriveItemAndPath(
    val driveItem: DriveItem,
    val path: Path
)

fun walkDriveChildren(
    client: GraphServiceClient<Request>,
    driveId: String,
    itemId: String,
    path: Path
): Flow<DriveItemAndPath> =
    channelFlow {
        var page: DriveItemCollectionPage? =
            client.drives(driveId).items(itemId).children().buildRequest().async.await()

        while (page != null) {
            page.currentPage.forEach {
                if (it.folder != null) {
                    launch {
                        walkDriveChildren(
                            client,
                            driveId,
                            requireNotNull(it.id),
                            path.resolve(requireNotNull(it.name))
                        ).collect { send(it) }
                    }
                } else if (it.file != null) {
                    send(DriveItemAndPath(it, path))
                } else {
                    System.err.println("warning: detect a non folder nor file entry")
                }
            }
            page = page.nextPage?.buildRequest()?.async?.await()
        }

    }