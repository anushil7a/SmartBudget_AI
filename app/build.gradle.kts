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

        // OpenAI API key - set in local.properties as OPENAI_API_KEY=your_key.
        // Known limitation: local.properties keeps the key out of version control, but a
        // buildConfigField bakes it into the APK, where anyone who unpacks the build can
        // recover it. Acceptable for coursework; a real release needs a server-side proxy.
        buildConfigField("String", "OPENAI_API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
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

// room-testing parses the exported schema JSON with kotlinx-serialization. A transitive BOM
// pins serialization-core to "strictly 1.7.3" while json resolves to 1.8.1. Room 2.8.4's
// generated bundle serializers are built against 1.8.x, where GeneratedSerializer no longer
// declares typeParametersSerializers() abstract — running them on 1.7.3 core throws
// AbstractMethodError inside MigrationTestHelper. Lift the whole family to 1.8.1 so the
// serializers match the runtime they were compiled for.
configurations.configureEach {
    if (name.contains("AndroidTest")) {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
            force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
            force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")
        }
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
    
    // Lifecycle: ViewModels + LiveData, so no screen touches a DAO directly
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.core.testing)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    annotationProcessor(libs.room.compiler)
}