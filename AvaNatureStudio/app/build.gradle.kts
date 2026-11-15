plugins {
    id("com.android.application")
}
android {
    namespace = "com.avanature.soundscape.v2"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.avanature.soundscape.studio"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        debug { }
        release {
            isMinifyEnabled = false
        }
    }
}
