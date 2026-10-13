plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.pdf.peace_space"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pdf.peace_space"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
    implementation(libs.constraintlayout)
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    implementation("com.google.mediapipe:tasks-text:0.10.14")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    val pdfVersion = "1.0.0-beta01"
    implementation("androidx.pdf:pdf-viewer-fragment:$pdfVersion")
}
