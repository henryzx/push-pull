package dtx

import com.opencsv.CSVWriterBuilder
import dtx.models.Row
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import okio.use
import utils.CmdUtils
import utils.FileUtils
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

/**
 * walk through dtx folder, hash .dtx .gda file and export to a csv file.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun main(args: Array<String>): Unit = runBlocking {
    val arguments = CmdUtils.groupArguments(args)
    if (!arguments["h"].isNullOrBlank()) {
        println("example: -d D:\\drum -o D:\\drum.csv")
        return@runBlocking
    }
    val directoryPathString = arguments["d"] ?: """D:\drum"""
    val outputCsvPathString = arguments["o"] ?: """D:\drum.csv"""

    val titlePattern = Regex("""#TITLE:?\s+(?<title>.*)""")
    val directoryPath = Path.of(directoryPathString)
    val outputCsvPath = Path.of(outputCsvPathString)

    val rowFlow = directoryPath.toFile().walk().asFlow()
        .filter { path -> path.isFile && path.extension.lowercase() in listOf("dtx", "gda") }
        .flatMapMerge(concurrency = DEFAULT_CONCURRENCY) { path ->
            flow {
                val relativePath = path.toPath().relativeToOrNull(directoryPath)?.invariantSeparatorsPathString ?: return@flow
                val title = async { titlePattern.find(path.readText(Charset.forName("shift_jis")))?.groups?.get("title")?.value }
                val digestString = async { FileUtils.digestFile(path.toPath()) }
                emit(
                    Row(
                        path = relativePath,
                        title = title.await().toString(),
                        hash = digestString.await()
                    )
                )
            }
        }
    outputCsvPath.bufferedWriter(charset = Charset.forName("gbk")).use { bw ->
        CSVWriterBuilder(bw).build().use { csvWriter ->
            rowFlow.collect { row ->
                csvWriter.writeNext(arrayOf(row.path, row.title, row.hash))
            }
            csvWriter.flush()
            bw.flush()
        }
    }
}

