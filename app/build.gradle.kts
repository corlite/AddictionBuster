plugins {
    id("com.android.application")
}

android {
    namespace = "com.addictionbuster"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.addictionbuster.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.1.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
