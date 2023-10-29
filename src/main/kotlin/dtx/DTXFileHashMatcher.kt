package dtx

import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriterBuilder
import dtx.models.Row
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import okio.use
import utils.FileUtils
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

/**
 * Find any row from my rows that is not exist in other rows.
 */
fun main(): Unit = runBlocking {
    val csvPath = Path.of("""D:\drum\hash.csv""")
    val rows = csvPath.bufferedReader(Charset.forName("gbk")).use { reader ->
        CSVReaderBuilder(reader).build().use { csvReader ->
            csvReader.readAll().map {
                Row(path = it[0], title = it[1], hash = it[2])
            }
        }
    }

    val dupeRows = rows.groupBy { it.hash }.filter { it.value.size > 1 }.flatMap { it.value }.toList()

    val outCsvPath = Path.of("""D:\drum\hash-dupes.csv""")
    outCsvPath.bufferedWriter(Charset.forName("gbk")).use { writer ->
        CSVWriterBuilder(writer).build().use { csvWriter ->
            dupeRows.forEach { row ->
                csvWriter.writeNext(arrayOf(row.path, row.title, row.hash))
            }
        }
    }
}