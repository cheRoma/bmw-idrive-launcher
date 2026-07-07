package online.k73.bmwlauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import online.k73.bmwlauncher.diag.AppLog
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Decorative live map used as the home-screen background, rendered by MapLibre from OUR own style
 * (`map-style.json` on k73.online → OpenFreeMap vector tiles). The style is deterministic — we set
 * every colour ourselves (near-black land, teal water, amber roads), unlike Yandex's black box — and
 * hosting it on the server means colours can be retuned without shipping a new APK.
 *
 * Gestures are disabled (it's a backdrop, not a map you touch); the camera follows the car's GPS.
 * Everything is guarded and skipped under @Preview/Paparazzi, so a failure just shows the solid
 * launcher background. No API key, no Google Play Services.
 */
@SuppressLint("MissingPermission")
@Composable
fun MapBackground(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) return
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        runCatching {
            MapLibre.getInstance(ctx)                 // no API key needed
            MapView(ctx).apply { onCreate(null) }
        }.getOrNull()
    } ?: return

    // Load our style, lock interaction, and follow the car's position.
    DisposableEffect(mapView) {
        var lm: LocationManager? = null
        var listener: LocationListener? = null
        runCatching {
            mapView.getMapAsync { map ->
                map.uiSettings.apply {
                    setAllGesturesEnabled(false)                 // decorative backdrop — non-interactive
                    isLogoEnabled = false                        // hide the MapLibre logo + attribution "i"
                    isAttributionEnabled = false
                }
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(55.751244, 37.618423)).zoom(13.0).build()   // Moscow until first fix
                map.setStyle(Style.Builder().fromUri(STYLE_URL)) {
                    AppLog.d("MAP", "MapLibre style loaded")
                }
                lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val l = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        runCatching {
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.5),
                            )
                        }
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                listener = l
                runCatching {
                    (lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                        ?.let { l.onLocationChanged(it) }
                    lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 12f, l)
                }
            }
        }.onFailure { AppLog.w("MAP", "map setup failed: ${it.message}") }
        onDispose { runCatching { listener?.let { lm?.removeUpdates(it) } } }
    }

    // Forward the Compose lifecycle to the MapView (MapLibre requires it).
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            runCatching {
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            runCatching { mapView.onStop(); mapView.onDestroy() }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val STYLE_URL = "https://k73.online/newBMW/map-style.json"
