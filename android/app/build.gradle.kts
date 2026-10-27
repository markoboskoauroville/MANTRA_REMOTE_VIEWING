plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = (project.findProperty("appVersion") as String? ?: "1").trim().toInt()

android {
    namespace = "com.mantra.remote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mantra.remote"
        minSdk = 34
        targetSdk = 35
        versionCode = appVersion
        versionName = appVersion.toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // the build names the APK from the one number (versioning.md §2, §6): 2-mantra-remote-vnc-v2.apk
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "$appVersion-mantra-remote-vnc-v$appVersion.apk"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // v16: the Pixel 7 driven over ADB (scrcpy's picture and touches), the Camera Remote's link
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.81")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}
