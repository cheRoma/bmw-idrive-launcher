package online.k73.bmwlauncher.update

class UpdateChecker(
    private val http: HttpClient,
    private val manifestUrl: String,
) {
    /** Fetches the manifest and compares against [currentCode]. Never throws — returns Error. */
    fun fetch(currentCode: Int): UpdateStatus =
        try {
            val manifest = UpdateManifest.parse(http.getText(manifestUrl))
            compare(currentCode, manifest)
        } catch (t: Throwable) {
            UpdateStatus.Error(t.message ?: "update check failed")
        }

    companion object {
        fun compare(currentCode: Int, manifest: UpdateManifest): UpdateStatus =
            if (manifest.versionCode > currentCode)
                UpdateStatus.Available(manifest.versionName, manifest.apkUrl, manifest.notes)
            else UpdateStatus.UpToDate
    }
}
