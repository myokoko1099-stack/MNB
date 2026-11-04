plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mnb.browser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mnb.browser"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0-phase02"
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Phase 04-I9-A: background translation queue
    implementation("androidx.work:work-runtime:2.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    // JVM implementation of org.json (the Android one is only a stub in unit tests)
    testImplementation("org.json:json:20231013")
    testImplementation("androidx.work:work-testing:2.9.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
