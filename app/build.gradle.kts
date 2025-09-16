plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.talkifyy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.talkifyy"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Fix for INSTALL_FAILED_TEST_ONLY - Allow installation from unknown sources
        setProperty("archivesBaseName", "Talkifyy-v$versionName")
    }

    buildTypes {
        debug {
            isDebuggable = true
            versionNameSuffix = "-debug"
            // Fix test-only APK issue for debug builds
            // Removed applicationIdSuffix to fix Firebase configuration
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Ensure release APK is properly signed and installable
            isShrinkResources = false
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
    implementation("com.hbb20:ccp:2.7.3")
    implementation(libs.constraintlayout)

    // ✅ Firebase BOM (keeps versions aligned)
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-storage")

    // Firebase UI
    implementation("com.firebaseui:firebase-ui-firestore:9.0.0")
    
    // Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // Fragment
    implementation("androidx.fragment:fragment:1.8.5")
    
    // HTTP client for FCM notifications
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
