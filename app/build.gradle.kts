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

android {
    namespace = "online.k73.bmwlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "online.k73.bmwlauncher"
        minSdk = 26
        targetSdk = 33
        versionCode = 17
        versionName = "1.4.1"
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
    debugImplementation(libs.compose.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui)
}
