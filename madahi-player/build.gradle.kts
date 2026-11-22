plugins {
    id("com.android.application")
}

android {
    namespace = "com.nava.madahiplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nava.madahiplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.work:work-runtime:2.12.0")
}
