plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Where the daily files are published: this repository's feed/ folder, written
// by .github/workflows/daily.yml. Change it here if the repository moves.
val feedUrl = "https://raw.githubusercontent.com/richie505/groupsca/main/feed/"

android {
    namespace = "com.appsc.ca"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.appsc.ca"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
        buildConfigField("String", "FEED_URL", "\"$feedUrl\"")
    }

    signingConfigs {
        // Fixed key kept in the repo (as in the other APPSC apps), so every
        // build installs over the last one and keeps saved items.
        create("app") {
            storeFile = rootProject.file("keystore/appsc-ca.jks")
            storePassword = "appscca"
            keyAlias = "appsc-ca"
            keyPassword = "appscca"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("app")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("app")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
