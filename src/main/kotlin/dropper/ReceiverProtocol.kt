package dropper

import utils.CmdUtils
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.ServerSocketChannel
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * Long | File Path String (utf8) | Long | File (as stream)
 *
 * Note:
 * remaining = limit - position
 * Reading: remaining
 * flip(): 0 <content written> position limit => position(0) <content written> limit (prepare for read)
 * compact(): 0 position <content> limit => position(0) <content> limit (remove 0 to position)
 */
class ReceiverProtocol(private val inputChannel: ReadableByteChannel) {

    private val charSet = Charset.forName("utf-8")

    private val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)

    /**
     * fileNameLength [sizeOf(int)]
     * fileName       [fileNameLength]
     * fileLength     [sizeOf(Long)]
     * file           [fileLength]
     */
    fun receiveFile(basePath: Path): Path? {
        if (inputChannel.readEnsuringRemaining(buffer, Int.SIZE_BYTES) < 0) return null
        buffer.flip()
        val fileNameLength = buffer.getInt()
        buffer.compact()
        val fileName = ByteArray(fileNameLength).let {
            if (inputChannel.readEnsuringRemaining(buffer, fileNameLength) < 0) return null
            buffer.flip()
            buffer.get(it, 0, fileNameLength)
            buffer.compact()
            String(it, charSet)
        }
        if (inputChannel.readEnsuringRemaining(buffer, Long.SIZE_BYTES) < 0) return null
        buffer.flip()
        val fileLength = buffer.getLong()
        buffer.compact()

        val filePath = basePath.resolve(fileName.replace('\\', '/'))
            .also { it.parent.createDirectories() }
        FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { fc ->
            var bytesRemaining = fileLength
            while (bytesRemaining > 0) {
                if (inputChannel.read(buffer) < 0) break
                buffer.flip()
                val readLimit = buffer.limit()
                if (readLimit > bytesRemaining) {
                    buffer.limit(readLimit.toLong().coerceAtMost(bytesRemaining).toInt())
                }
                val bytesWritten = fc.write(buffer)
                buffer.limit(readLimit) // restore previous limit for the compact later
                buffer.compact()
                bytesRemaining -= bytesWritten
            }
            fc.force(true)
        }
        return filePath
    }

    @Throws(IOException::class)
    private fun ReadableByteChannel.readEnsuringRemaining(dst: ByteBuffer, expectedRemaining: Int): Int {
        var totalRead = 0
        while (dst.position() < expectedRemaining) {
            val read = read(dst)
            if (read < 0) {
                return totalRead
            }
            totalRead += read
        }
        return totalRead
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val arguments = CmdUtils.groupArguments(args)
            if (arguments.isEmpty()) {
                println("usage: -d directory to store received files")
                return
            }
            val basePath = arguments["d"] ?: Path.of(System.getProperty("user.home")).resolve("Download").pathString
            val port = arguments["p"] ?: "8888"
            listen(Path.of(basePath), port.toInt())
        }

        fun listen(basePath: Path, port: Int) {
            val executor = Executors.newCachedThreadPool()
            println("Listening at port $port, save files to $basePath")
            NetworkInterface.networkInterfaces().flatMap { it.inetAddresses() }.filter { it.isSiteLocalAddress }
                .forEach {
                    println("Local address: ${it.hostAddress}")
                }
            basePath.createDirectories()
            ServerSocketChannel.open().use { server ->
                server.bind(InetSocketAddress("0.0.0.0", port))
                while (true) {
                    println("Listening connection")
                    val client = server.accept()
                    executor.submit {
                        client.use { client ->
                            println("Accept incoming connection")
                            val protocol = ReceiverProtocol(client)
                            while (client.isOpen) {
                                val receivedFile = protocol.receiveFile(basePath)
                                println("Received file $receivedFile")
                            }
                        }
                    }
                }
            }
        }
    }
}