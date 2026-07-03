package online.k73.bmwlauncher.update

import java.io.File

interface HttpClient {
    /** Fetch a URL body as text. Throws on network/HTTP error. */
    fun getText(url: String): String

    /** Download a URL to [dest], reporting integer percent [0..100]. Throws on error. */
    fun download(url: String, dest: File, onProgress: (percent: Int) -> Unit)
}
