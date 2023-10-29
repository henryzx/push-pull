//import dropper.ReceiverProtocol
//import dropper.SenderProtocol
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking
//import java.nio.file.Path
//
//fun main(): Unit = runBlocking {
//    launch(Dispatchers.IO) {
//        ReceiverProtocol.listen(Path.of("""C:\Users\zx\Download"""))
//    }
//    SenderProtocol.sendFiles(Path.of("""D:\drum\DTXFiles_approved"""), "localhost")
//}