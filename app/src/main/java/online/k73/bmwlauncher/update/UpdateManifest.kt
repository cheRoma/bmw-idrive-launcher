package online.k73.bmwlauncher.update

import org.json.JSONObject

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
) {
    companion object {
        fun parse(json: String): UpdateManifest {
            val o = JSONObject(json)
            return UpdateManifest(
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                apkUrl = o.getString("apkUrl"),
                notes = o.optString("notes", ""),
            )
        }
    }
}
