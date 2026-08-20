plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.project3_aadhika8_sguragai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.project3_aadhika8_sguragai"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OpenAI API key - set in local.properties as OPENAI_API_KEY=your_key
        buildConfigField("String", "OPENAI_API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
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
    buildFeatures {
        buildConfig = true
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
    implementation(libs.room.common.jvm)
    implementation(libs.room.runtime)
    
    // Charts
    implementation(libs.mpandroidchart)
    
    // ML Kit OCR
    implementation(libs.mlkit.text.recognition)
    
    // HTTP Client for OpenAI
    implementation(libs.okhttp)
    
    // Image loading
    implementation(libs.glide)
    
    // UI Components
    implementation(libs.viewpager2)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    annotationProcessor(libs.room.compiler)
}