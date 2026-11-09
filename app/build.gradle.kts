plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pixeltoonz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixeltoonz.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures { buildConfig = true }
    buildConfigField("String", "SUPABASE_URL", "\"\u0024{project.findProperty("SUPABASE_URL") ?: System.getenv("SUPABASE_URL") ?: ""}\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"\u0024{project.findProperty("SUPABASE_ANON_KEY") ?: System.getenv("SUPABASE_ANON_KEY") ?: ""}\"")

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
}
