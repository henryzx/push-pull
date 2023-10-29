package checker

import com.opencsv.CSVWriterBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import utils.Bytes
import utils.FileUtils
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo


fun main(): Unit = runBlocking {
    val dirPath = Path.of("""D:\drum""")
    val outputCSVPath = Path.of("""D:\filelist.csv""")

    val rowFlow = channelFlow<CheckedFiles> {
        dirPath.toFile().walkTopDown().filter { it.isFile }.forEach { file ->
            val digestHex = async(Dispatchers.Default) { FileUtils.digestFile(file.toPath()) }
            launch(Dispatchers.IO) {
                send(
                    CheckedFiles(
                        relativePath = file.toPath().relativeTo(dirPath).invariantSeparatorsPathString,
                        size = file.length(),
                        sizeDisplay = Bytes.format(file.length()),
                        sha256 = digestHex.await()
                    )
                )
            }
        }
    }.buffer()

    launch(Dispatchers.IO) {
        outputCSVPath.bufferedWriter().use { writer ->
            CSVWriterBuilder(writer).build().use { csvWriter ->
                rowFlow.collect { row ->
                    csvWriter.writeNext(row.marshallToArray())
                    println("wrote $row")
                }
                csvWriter.flush()
            }
        }
    }
}

data class CheckedFiles(
    val relativePath: String,
    val size: Long,
    val sizeDisplay: String,
    val sha256: String
) {
    fun marshallToArray(): Array<String> = arrayOf(relativePath, size.toString(), sizeDisplay, sha256)

    companion object {
        fun parseFromArray(array: Array<String>): CheckedFiles =
            CheckedFiles(array[0], array[1].toLong(), array[2], array[3])
    }
}