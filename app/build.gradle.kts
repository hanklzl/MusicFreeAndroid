import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val versionProps = Properties().also { props ->
    rootProject.file("version.properties").inputStream().use { stream -> props.load(stream) }
}
val appVersionCode: Int = versionProps.getProperty("versionCode")?.toIntOrNull()
    ?: throw GradleException("version.properties: versionCode missing or invalid")
val appVersionName: String = versionProps.getProperty("versionName")
    ?: throw GradleException("version.properties: versionName missing")

val releaseSigningEnvironmentVariables = listOf(
    "ANDROID_RELEASE_KEYSTORE_PATH",
    "ANDROID_RELEASE_STORE_PASSWORD",
    "ANDROID_RELEASE_KEY_ALIAS",
    "ANDROID_RELEASE_KEY_PASSWORD",
)
val releaseLoganEnvironmentVariables = listOf("LOGAN_AES_KEY", "LOGAN_AES_IV")

val releaseSigningRequested = gradle.startParameter.taskNames.any { taskName ->
    val normalizedTaskName = taskName.substringAfterLast(':')
    normalizedTaskName.equals("assembleRelease", ignoreCase = true) ||
        normalizedTaskName.equals("bundleRelease", ignoreCase = true) ||
        normalizedTaskName.equals("packageRelease", ignoreCase = true) ||
        normalizedTaskName.equals("build", ignoreCase = true) ||
        normalizedTaskName.endsWith("Release", ignoreCase = true)
}

fun requiredReleaseSigningEnv(name: String): String =
    providers.environmentVariable(name).orNull
        ?: throw org.gradle.api.GradleException(
            "Missing release signing environment variable: $name. " +
                "Set ${releaseSigningEnvironmentVariables.joinToString()} before running a release build."
        )

fun requiredReleaseLoganEnv(name: String): String =
    providers.environmentVariable(name).orNull
        ?: throw org.gradle.api.GradleException(
            "Missing release Logan environment variable: $name. " +
                "Set ${releaseLoganEnvironmentVariables.joinToString()} before running a release build."
        )

fun quotedBuildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.zili.android.musicfreeandroid"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.zili.android.musicfreeandroid"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "com.zili.android.musicfreeandroid.HiltTestRunner"

        buildConfigField("String", "LOGAN_AES_KEY", quotedBuildConfigString("0123456789abcdef"))
        buildConfigField("String", "LOGAN_AES_IV", quotedBuildConfigString("abcdef0123456789"))
    }

    signingConfigs {
        create("release") {
            if (releaseSigningRequested) {
                storeFile = file(requiredReleaseSigningEnv("ANDROID_RELEASE_KEYSTORE_PATH"))
                storePassword = requiredReleaseSigningEnv("ANDROID_RELEASE_STORE_PASSWORD")
                keyAlias = requiredReleaseSigningEnv("ANDROID_RELEASE_KEY_ALIAS")
                keyPassword = requiredReleaseSigningEnv("ANDROID_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "MF音乐(D)")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", "MF音乐")
            if (releaseSigningRequested) {
                buildConfigField(
                    "String",
                    "LOGAN_AES_KEY",
                    quotedBuildConfigString(requiredReleaseLoganEnv("LOGAN_AES_KEY")),
                )
                buildConfigField(
                    "String",
                    "LOGAN_AES_IV",
                    quotedBuildConfigString(requiredReleaseLoganEnv("LOGAN_AES_IV")),
                )
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Modules
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":player"))
    implementation(project(":plugin"))
    implementation(project(":feature:home"))
    implementation(project(":feature:player-ui"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))
    implementation(project(":logging"))
    implementation(project(":downloader"))
    implementation(project(":updater"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
