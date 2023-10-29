package utils

import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriterBuilder
import com.opencsv.ICSVWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

inline fun csvWriter(path: Path, writeTasks: (ICSVWriter) -> Unit) {
    Files.newBufferedWriter(path, Charset.forName("gbk")).use { writer ->
        CSVWriterBuilder(writer).build().use { csvWriter ->
            writeTasks(csvWriter)
        }
    }
}

inline fun <T> csvReader(path: Path, readTasks: (CSVReader) -> T): T {
    return Files.newBufferedReader(path, Charset.forName("gbk")).use { reader ->
        CSVReaderBuilder(reader).build().use { csvReader ->
            readTasks(csvReader)
        }
    }
}