package gdrive

data class DownloadTask(
    val id: String,
    val name: String,
    val relativePath: String,
    val mimeType: String,
    val sha1Sum: String = ""
) {
    companion object {
        fun parse(arr: Array<String>): DownloadTask =
            DownloadTask(arr[0], arr[1], arr[2], arr[3], arr[4])
    }

    fun marshall(): Array<String> = arrayOf(id, name, relativePath, mimeType, sha1Sum)
}