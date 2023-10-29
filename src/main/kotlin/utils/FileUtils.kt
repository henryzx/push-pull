package utils

import okio.use
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.io.path.inputStream

object FileUtils {

    fun digestFile(path: Path, algorithm: String = "SHA-1"): String {
        val messageDigest = MessageDigest.getInstance(algorithm)
        val byteArray = ByteArray(4 * 1024)
        Files.newInputStream(path).use { inputStream ->
            while (true) {
                val read = inputStream.read(byteArray)
                if (read < 0) break
                messageDigest.update(byteArray, 0, read)
            }
        }
        val digest = messageDigest.digest()
        return "%0${messageDigest.digestLength * 2}X".format(BigInteger(1, digest))
    }

    fun printFileInHex(path: Path, startOffset: Long = 0, maxOffset: Long = Long.MAX_VALUE) {
        path.inputStream(StandardOpenOption.READ).buffered().use { ins ->
            var index = startOffset
            val lineLength = 64
            for (byte in ins) {
                if (index != 0L && index % lineLength == 0L) {
                    println()
                }
                print("%02x ".format(byte))

                if (index >= maxOffset - 1) {
                    break
                }
                index++
            }
        }
    }

    fun findLastNonZeroPosition(filePath: Path): Long {

        fun ByteBuffer.allZero(): Boolean {
            rewind()
            for (i in 0 until this.remaining()) {
                if (get(i).compareTo(0) != 0) {
                    return false
                }
            }
            return true
        }

        FileChannel.open(filePath).use { channel ->
            val cacheSize = 1024
            val cache = ByteBuffer.allocate(cacheSize)
            val size = channel.size()
            for (pos in 3 * size / 4 - 1L downTo 0L step cacheSize.toLong()) {
                channel.read(cache, pos)

                if (!cache.allZero()) {
                    //find first non-zero position
                    return pos
                }
            }
        }

        return -1
    }

    fun parentAsSequence(path: Path): Sequence<Path> = sequence {
        var curPath = path
        while (curPath.parent != null) {
            yield(curPath.parent)
            curPath = curPath.parent
        }
    }

}