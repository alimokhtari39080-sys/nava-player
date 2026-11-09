plugins {
    id("com.android.application")
}

android {
    namespace = "com.nava.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nava.player"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
}
