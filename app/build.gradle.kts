plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pixeltoonz.app"
    compileSdk = 35

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.pixeltoonz.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        val supabaseUrl = providers.gradleProperty("SUPABASE_URL").orElse(providers.environmentVariable("SUPABASE_URL")).orElse("").get()
        val supabaseKey = providers.gradleProperty("SUPABASE_ANON_KEY").orElse(providers.environmentVariable("SUPABASE_ANON_KEY")).orElse("").get()
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
    }

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
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
