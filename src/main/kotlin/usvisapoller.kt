import java.io.File
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * US Visa Appointment Poller.
 */
fun main() {

    while (true) {
        val resultString = queryOnce()

        if (resultString.substringAfterLast("Please choose a new date and time for your appointment.", "").contains("September")) {
            println("found one")
            sendMail("found one")
            println("email sent")
            break
        }

        println("retry in 5 min")
        TimeUnit.MINUTES.sleep(5)
    }
}

fun queryOnce(): String {

    val pb = ProcessBuilder("""cmd /c C:\Users\zx\IdeaProjects\playground\src\main\resources\fetch-bash.bat""".split(" "))
    pb.directory(File("""C:\Users\zx\IdeaProjects\playground\src\main\resources"""))
    val p = pb.start()

    val resultString = p.inputStream.bufferedReader(Charset.forName("gb2312")).readText()

    val result = p.waitFor()
    return if (result == 0) {
        resultString
    } else ""
}

fun sendMail(bodyText: String) {

    val props = Properties().apply {
        put("mail.smtp.host", "smtp.live.com")
        put("mail.smtp.socketFactory.port", "25")
        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
        put("mail.smtp.auth", "true")
        put("mail.smtp.port", "25")
        put("mail.smtp.starttls.enable", "true")
    }
    val auth = object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication("henryzx@hotmail.com", "103hzx@live")
        }
    }
    val session = Session.getDefaultInstance(props, auth)
    Transport.send(MimeMessage(session).apply {
        setFrom(InternetAddress("henryzx@hotmail.com", "me"))
        subject = "hello from windows"
        addRecipients(Message.RecipientType.TO, InternetAddress.parse("zhengxiao1127@foxmail.com", false))
        setContent(MimeMultipart().apply {
            addBodyPart(MimeBodyPart().apply {
                setText(bodyText)
            })
        })
    })
}