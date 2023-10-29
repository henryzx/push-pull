package gdrive

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.GoogleUtils
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList
import com.opencsv.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapMerge
import utils.csvReader
import utils.csvWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.util.*
import kotlin.io.path.*


/* class to demonstrate use of Drive files list API */
object GoogleDriveDownload {
    /**
     * Application name.
     */
    private const val APPLICATION_NAME = "Google Drive API Java Quickstart"

    /**
     * Global instance of the JSON factory.
     */
    private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()

    /**
     * Directory to store authorization tokens for this application.
     */
    private const val TOKENS_DIRECTORY_PATH = "tokens"

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private val SCOPES = listOf(DriveScopes.DRIVE_READONLY, DriveScopes.DRIVE_METADATA_READONLY)
    private const val CREDENTIALS_FILE_PATH = "/google_credentials.json"

    /**
     * Creates an authorized Credential object.
     *
     * @param httpTransport The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    @Throws(IOException::class)
    private fun getCredentials(httpTransport: NetHttpTransport): Credential {
        // Load client secrets.
        val inputStream =
            GoogleDriveDownload::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
                ?: throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH")
        val clientSecrets =
            GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inputStream))

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport, JSON_FACTORY, clientSecrets, SCOPES
        )
            .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        //returns an authorized Credential object.
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalPathApi::class, DelicateCoroutinesApi::class)
    @Throws(IOException::class, GeneralSecurityException::class)
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {

        val outDirectoryPath = Path.of("""D:\Downloads""")
        val tempDirectoryPath = outDirectoryPath.resolve("tmp")
        if (tempDirectoryPath.exists()) {
            tempDirectoryPath.deleteRecursively()
        }
        tempDirectoryPath.createDirectories()

        // Build a new authorized API client service.
        // GoogleNetHttpTransport.newTrustedTransport()
        val httpTransport = NetHttpTransport.Builder().trustCertificates(GoogleUtils.getCertificateTrustStore())
            .setProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890))).build()

        val service = Drive.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
            .setApplicationName(APPLICATION_NAME)
            .build()

        val listFile = outDirectoryPath.resolve("download-id.csv")
        if (listFile.notExists()) {
            val folderIds = listOf("1AS3sKQM66aKT5mtngpfarspOAoj_aPQL")

            csvWriter(listFile) { csvWriter ->
                folderIds.asFlow().flatMapMerge { folderId ->
                    channelFlow {
                        suspend fun walk(folderId: String, currentPath: Path) {
                            val fileList: FileList = service.files().list().setQ("'$folderId' in parents").execute()
                            fileList.files.forEach {
                                when (it.mimeType) {
                                    "application/vnd.google-apps.folder" -> walk(
                                        it.id,
                                        currentPath.resolve(it.name)
                                    )

                                    else -> channel.send(
                                        DownloadTask(
                                            id = it.id,
                                            name = it.name,
                                            relativePath = currentPath.pathString,
                                            mimeType = it.mimeType
                                        )
                                    )
                                }
                            }
                        }
                        walk(folderId, Path.of(""))
                    }
                }.collect { task ->
                    csvWriter.writeNext(task.marshall())
                }
            }
        }

        newFixedThreadPoolContext(5, "download-tasks").use { downloadContext ->

            csvReader(listFile) { csvReader ->
                csvReader.readAll().map { DownloadTask.parse(it) }
            }.toList().distinctBy { it.id }.forEach { task ->
                launch(downloadContext) {
                    val tempFile = createTempFile(directory = tempDirectoryPath, "gd-", task.id)
                    val outFile = outDirectoryPath
                        .resolve(task.relativePath)
                        .apply { createDirectories() }
                        .resolve(task.name)
                    if (outFile.notExists()) {
                        try {
                            Files.newOutputStream(tempFile).use { outputStream ->
                                service.files()[task.id].executeMediaAndDownloadTo(outputStream)
                                outputStream.flush()
                                println("Downloaded $outFile")
                            }
                            Files.move(tempFile, outFile)
                        } catch (e: Throwable) {
                            System.err.println("Unable to download ${task.name} ${task.id} $outFile: ${e.stackTraceToString()}")
                        }
                    } else {
                        println("Existing $outFile")
                    }
                }
            }
        }

        // clear
    }
}