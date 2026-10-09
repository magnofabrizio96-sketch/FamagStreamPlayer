plugins {
    id("com.android.application")
}

android {
    namespace = "com.famag.streamplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.famag.streamplayer"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
