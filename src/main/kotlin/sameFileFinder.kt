import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.*
import java.util.zip.CRC32
import kotlin.collections.HashMap

/**
 * scans files on local disk and find duplicate files.
 * todo need to handle folder comparison
 */
fun main() {
    val map = TreeMap<Long, LinkedList<String>>(kotlin.Comparator { a, b -> -(a.compareTo(b)) })
    val roots = listOf(File("d:\\"), File("e:\\"), File("f:\\"), File("g:\\"), File("h:\\"), File("i:\\"))
    Observable.fromIterable(roots).map { File(it, "files.txt") }.flatMap {
        Observable.create { emitter: ObservableEmitter<Pair<Long, String>> ->
            it.bufferedReader().use {
                while (true) {
                    val line = it.readLine()
                    line ?: break
                    val spaceI = line.indexOf(" ")
                    val size = line.substring(0, spaceI).toLongOrNull()
                    size ?: break
                    val filePath = line.substring(spaceI + 1, line.length)

                    println("reading $filePath")

                    emitter.onNext(size to filePath)
                }
                emitter.onComplete()
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(Schedulers.single())
                .map { (key, value) ->
                    map.computeIfAbsent(key) { LinkedList() }.add(value)
                }
                .observeOn(Schedulers.io())
    }.subscribeOn(Schedulers.io()).toList().blockingGet()

    File("d:\\dup.txt").bufferedWriter().use { writer ->
        for ((key, values) in map) {
            if (values.filter { File(it).exists() }.size > 1) {
                // found duplicates

                //println("found dup: $values")
                val hashes = Observable.fromIterable(values).flatMap { value ->
                    Maybe.fromCallable {
                        if (!File(value).exists()) return@fromCallable null
                        return@fromCallable crcOfFirst(value, 1024 * 1024 * 1 /* 10 mb */) to value
                    }.subscribeOn(Schedulers.io()).onErrorComplete().toObservable()
                }.reduce(HashMap<Long, LinkedList<String>>()) { seed, (key, value) ->
                    seed.computeIfAbsent(key) { LinkedList() }.add(value)
                    seed
                }.blockingGet()

                for ((crc, filePaths) in hashes) {
                    if (filePaths.size > 1) {
                        // found real duplicates
                        println("found REAL dup: $filePaths")
                        writer.write("${crc.toString(16)}\t$filePaths\n")
                    }
                }
            }
        }
    }

    println("done")
}

fun crcOfFirst(filePath: String, bytesToReadMax: Long): Long {
    val crc = CRC32()
    var bytesToRead = bytesToReadMax

    val buffer = ByteArray(4096)
    File(filePath).inputStream().use { input ->
        do {
            val size = input.read(buffer)
            if (size <= 0)   else {
                val toRead = Math.min(bytesToRead, size.toLong())
                if (toRead == 0L) break
                crc.update(buffer, 0, toRead.toInt())
                bytesToRead -= size.toLong()
            }
        } while (true)
    }
    return crc.value
}
