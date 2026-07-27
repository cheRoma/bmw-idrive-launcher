package online.k73.bmwlauncher.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import online.k73.bmwlauncher.diag.AppLog

/**
 * Live state of the remote tunnel, shown in Settings.
 *
 * It matters more than a status line usually does: while the tunnel is down I cannot see the car at
 * all, so this row is the only way to tell whether the fault is on my side or the unit's — without
 * it, "why isn't it connecting" would need a trip to the car, which is the exact problem the tunnel
 * was built to remove.
 */
object RemoteStatus {
    private val _state = MutableStateFlow("выключен")
    val state: StateFlow<String> = _state

    fun set(text: String) {
        if (_state.value == text) return
        _state.value = text
        AppLog.d("REMOTE", text)
    }
}
