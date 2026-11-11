plugins {
    id("com.android.application")
}
android {
    namespace = "dev.observatory.xui"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.observatory.xui"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
