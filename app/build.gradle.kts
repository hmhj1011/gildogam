import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val vMajor = versionProps.getProperty("VERSION_MAJOR").trim().toInt()
val vMinor = versionProps.getProperty("VERSION_MINOR").trim().toInt()
val vPatch = versionProps.getProperty("VERSION_PATCH").trim().toInt()

// Release signing comes from environment variables (GitHub Actions secrets).
// Without them the release build is signed with the debug key so the APK still installs.
val releaseKeystore: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "kr.gildogam"
    compileSdk = 35

    // Android Automotive car API (CarPropertyManager) for speed / wiper / turn signal.
    useLibrary("android.car", false)

    defaultConfig {
        applicationId = "kr.gildogam"
        minSdk = 28 // Pleos Connect requirement: API 28 or higher
        targetSdk = 35
        versionCode = vMajor * 10_000 + vMinor * 100 + vPatch
        versionName = "$vMajor.$vMinor.$vPatch"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi.helper)
    implementation(libs.pleos.fused.location)
    implementation(libs.pleos.text.to.speech)
    implementation(libs.pleos.speech.to.text)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}

// Contest rule: apk/<팀명>_<앱이름>_release.apk. Set TEAM_NAME in gradle.properties.
// ASCII by default so GitHub Releases keeps the file name intact.
val teamName = (findProperty("TEAM_NAME") as String?)?.trim().orEmpty().ifEmpty { "Team" }
val appFileName = (findProperty("APP_FILE_NAME") as String?)?.trim().orEmpty().ifEmpty { "Gildogam" }
val submissionName = "${teamName}_${appFileName}_release.apk"

tasks.register<Copy>("packageSubmissionApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { submissionName }
    doLast { println("Submission APK: apk/$submissionName") }
}
