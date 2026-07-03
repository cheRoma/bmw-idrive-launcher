package online.k73.bmwlauncher.update

import java.io.File

class ApkDownloader(private val http: HttpClient, private val cacheDir: File) {
    /** Downloads [url] to cacheDir/update.apk, reporting percent. Returns the file. */
    fun download(url: String, onProgress: (Int) -> Unit): File {
        val dest = File(cacheDir, "update.apk")
        if (dest.exists()) dest.delete()
        http.download(url, dest, onProgress)
        return dest
    }
}
