package dropper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.runBlocking
import utils.CmdUtils
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

class SenderProtocol(private val outputChannel: WritableByteChannel) {
    private val chatSet = Charset.forName("utf-8")
    private val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)

    /**
     * fileNameLength :sizeOf(int)
     * fileName       :fileNameLength
     * fileLength     :sizeOf(Long)
     * file           :fileLength
     */
    fun sendFile(filePath: Path, basePath: Path) {
        buffer.clear()
        val fileName = filePath.relativeTo(basePath.parent)
        val fileNameByteArray = chatSet.encode(fileName.invariantSeparatorsPathString)
        val fileNameByteArraySize = fileNameByteArray.remaining()
        val fileSize = Files.size(filePath)
        buffer.putInt(fileNameByteArraySize)
        buffer.put(fileNameByteArray)
        buffer.putLong(fileSize)
        while (buffer.position() != 0) {
            buffer.flip()
            outputChannel.write(buffer)
            buffer.compact()
        }
        FileChannel.open(filePath).use { fc ->
            while (fc.read(buffer) >= 0 || buffer.position() != 0) {
                buffer.flip()
                outputChannel.write(buffer)
                buffer.compact()
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val arguments = CmdUtils.groupArguments(args)
            val dirPath = arguments["d"] ?: """D:\drum\GITADORA\HIGH VOLTAGE"""
            val hostName = arguments["h"] ?: "192.168.31.51"
            val port = arguments["p"] ?: "8888"
            sendFiles(Path.of(dirPath), hostName, port.toInt())
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun sendFiles(dirPath: Path, hostName: String, port: Int) = runBlocking {
            println("Sending files in $dirPath to $hostName:$port")
            val fileChannel = produce(Dispatchers.IO, Int.MAX_VALUE) {
                for (file in Files.walk(dirPath).filter { it.isRegularFile() }) {
                    println("Found file $file")
                    send(file)
                }
            }

            SocketChannel.open(InetSocketAddress(hostName, port)).use { socket ->
                val protocol = SenderProtocol(socket)
                fileChannel.consumeEach { file ->
                    protocol.sendFile(file, dirPath)
                    println("Sent file $file")
                }
            }
        }
    }
}