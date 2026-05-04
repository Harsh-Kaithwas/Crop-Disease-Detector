
// App/build.gradle.kts (Plugins Block)
plugins {
    id("com.android.application")
    // Version ko 1.9.24 se badal kar 2.0.21 karei
    id("org.jetbrains.kotlin.android") version "2.0.21"
}

android {
    namespace = "com.harsh.cropdiseasedetector"
    compileSdk = 36 // Fix: Ab latest Android API 36 se compile ho raha hai

    defaultConfig {
        applicationId = "com.harsh.cropdiseasedetector"
        minSdk = 25
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    aaptOptions {
        noCompress("tflite") // TFLite compression fix
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

    // Java/Kotlin Alignment (JVM Target Fix)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11" // Match compileOptions
    }

    buildFeatures {
        compose = false
    }
}

dependencies {
    // Core Android Libraries (Simple version)
    // core-ktx 1.12.0 is included here.
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // TensorFlow Lite (Model compatibility fix)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation(libs.androidx.activity)

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // NOTE: Hataayi gayi dependencies:
    // implementation(libs.core.ktx), implementation(libs.androidx.ui.graphics.android)
    // aur 'constraints' block ko hata diya gaya hai.
}

// Global Dependency Resolution Strategy (FINAL TFLITE VERSION FORCE)
configurations.all {
    // TFLite ko force karein taaki woh 2.16.1 hi use kare
    resolutionStrategy.force("org.tensorflow:tensorflow-lite:2.16.1")
    resolutionStrategy.force("org.tensorflow:tensorflow-lite-support:0.4.4")
    resolutionStrategy.force("org.tensorflow:tensorflow-lite-gpu:2.16.1") // GPU version ko bhi force kiya gaya hai

    // Kotlin resolution strategy ki ab zaroorat nahi hai kyonki humne plugin version update kar diya
}