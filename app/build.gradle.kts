import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val hfToken = localProperties.getProperty("HF_TOKEN") ?: ""

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

        buildConfigField("String", "HF_TOKEN", "\"$hfToken\"")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        buildConfig = true
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
    testImplementation("org.robolectric:robolectric:4.16")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    val pdfVersion = "1.0.0-beta01"
    implementation("androidx.pdf:pdf-viewer-fragment:$pdfVersion")
}

// Robolectric downloads its Android test runtime from Maven Central.
tasks.withType<Test>().configureEach {
    val testCache = layout.buildDirectory.dir("test-runtime").get().asFile
    doFirst { testCache.mkdirs() }
    systemProperty("user.home", testCache.absolutePath)
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
