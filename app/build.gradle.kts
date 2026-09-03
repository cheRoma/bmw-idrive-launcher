import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.paparazzi)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

// `-PpublicBuild=true` blanks every credential baked into the app. A private key is only as private
// as the APK it ships in, and release APKs are attached to public GitHub releases — so the copy that
// goes there is built this way: identical code, no secrets, remote access simply inert.
val publicBuild = (project.findProperty("publicBuild") as String?)?.toBoolean() ?: false

fun secret(name: String): String = if (publicBuild) "" else keystoreProps.getProperty(name) ?: ""

android {
    namespace = "online.k73.bmwlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "online.k73.bmwlauncher"
        minSdk = 26
        targetSdk = 33
        versionCode = 72
        versionName = "1.6.44"

        // Diagnostic log upload endpoint (token kept out of VCS via keystore.properties).
        buildConfigField("String", "LOG_UPLOAD_URL", "\"https://k73.online/newBMW/logs/upload\"")
        buildConfigField("String", "LOG_UPLOAD_TOKEN", "\"${secret("logUploadToken")}\"")
        // Remote sing-box profile for the YouTube VPN. The URL's path IS the credential (anyone
        // holding it can use the tunnel), so it lives in keystore.properties, not in VCS.
        buildConfigField("String", "VPN_PROFILE_URL", "\"${secret("vpnProfileUrl")}\"")
        // Reverse tunnel to the VPS: the launcher's own remote access, replacing the Termux autossh
        // the head unit used to depend on. All of it is credentials, so it comes from
        // keystore.properties and disappears in a public build.
        buildConfigField("String", "TUNNEL_HOST", "\"72.56.92.199\"")
        buildConfigField("int", "TUNNEL_PORT", "22")
        buildConfigField("String", "TUNNEL_USER", "\"hu\"")
        buildConfigField("String", "TUNNEL_KEY_B64", "\"${secret("tunnelKeyB64")}\"")
        buildConfigField("String", "TUNNEL_KNOWN_HOST", "\"${secret("tunnelKnownHost")}\"")
        buildConfigField("String", "CONTROL_TOKEN", "\"${secret("controlToken")}\"")
        // Ports on the VPS side; both are pinned in the key's authorized_keys (permitlisten).
        buildConfigField("int", "REMOTE_ADB_PORT", "20055")
        buildConfigField("int", "REMOTE_CONTROL_PORT", "20080")
        // Yandex MapKit key for the live map background (kept out of VCS via keystore.properties).
        buildConfigField("String", "YANDEX_MAPKIT_KEY", "\"${keystoreProps.getProperty("yandexMapkitKey") ?: ""}\"")
    }
    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// Paparazzi 1.3.3 bundles com.android.tools:common:31.2.2, whose ResourceType calls
// Guava's Sets.toImmutableEnumSet(). Guava 33.0.0-jre (dragged in transitively) changed the
// visibility of that method, producing an IllegalAccessError at layoutlib init time. Pin Guava
// to the version common:31.2.2 was compiled against so the call resolves at runtime.
configurations.configureEach {
    resolutionStrategy {
        force("com.google.guava:guava:32.0.1-jre")
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.nav.compose)
    implementation(libs.datastore.prefs)
    implementation(libs.coroutines.android)
    // MapLibre GL — decorative live map home background, rendered from our own deterministic style
    // (map-style.json on k73.online → free OpenFreeMap tiles). No API key, no Google Play Services.
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    // USB-serial (CP210x) for reading the car's I-Bus directly — our own on-board computer.
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
    // MediaBrowserCompat — connect to Yandex's MediaBrowserService to start playback WITHOUT its UI
    implementation("androidx.media:media:1.7.0")
    // SSH client for the launcher's own reverse tunnel (maintained JSch fork).
    implementation("com.github.mwiede:jsch:0.2.25")
    debugImplementation(libs.compose.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui)
}
