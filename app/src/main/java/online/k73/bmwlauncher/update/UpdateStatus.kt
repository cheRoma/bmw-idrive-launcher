package online.k73.bmwlauncher.update

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(val versionName: String, val apkUrl: String, val notes: String) : UpdateStatus
    data class Error(val reason: String) : UpdateStatus
}
