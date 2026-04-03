plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.quietzone_app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.quietzone_app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    // Room (SQLite) dependencies
    implementation("androidx.room:room-runtime:2.6.1")
    implementation(libs.firebase.messaging)
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    // For Kotlin use kapt instead of annotationProcessor
    testImplementation("androidx.room:room-testing:2.6.1")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // SpeedView library for gauge displays
    implementation("com.github.anastr:speedviewlib:1.6.1")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}