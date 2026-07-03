package online.k73.bmwlauncher.update

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val versionName: String, val apkUrl: String, val notes: String) : UpdateUiState
    data class Downloading(val percent: Int) : UpdateUiState
    data object Installing : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}
