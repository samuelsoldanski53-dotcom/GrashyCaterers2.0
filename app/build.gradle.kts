import java.util.Base64
import java.util.Properties
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun loadEnvValue(key: String): String {
    val envProps = Properties()
    val defaultFile = rootProject.file(".env.example")
    if (defaultFile.exists()) {
        defaultFile.inputStream().use { envProps.load(it) }
    }
    val localFile = rootProject.file(".env")
    if (localFile.exists()) {
        localFile.inputStream().use { envProps.load(it) }
    }
    val raw = System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: envProps.getProperty(key, "")
    return raw.trim().removeSurrounding("\"").replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.grashystudio.kxmpzq"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "VITE_SUPABASE_URL", "\"${loadEnvValue("VITE_SUPABASE_URL")}\"")
        buildConfigField("String", "VITE_SUPABASE_ANON_KEY", "\"${loadEnvValue("VITE_SUPABASE_ANON_KEY")}\"")
    }

    val releaseKeystoreBase64 = System.getenv("ANDROID_KEYSTORE_BASE64")?.trim().orEmpty()
    val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD").orEmpty()
    val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")?.trim().orEmpty()
    val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD").orEmpty()
    val hasReleaseSigningSecrets = releaseKeystoreBase64.isNotEmpty() &&
        releaseStorePassword.isNotEmpty() &&
        releaseKeyAlias.isNotEmpty() &&
        releaseKeyPassword.isNotEmpty()

    val isReleaseTaskRequested = gradle.startParameter.taskNames.any { task ->
        task.contains("Release", ignoreCase = true)
    }

    if (isReleaseTaskRequested && !hasReleaseSigningSecrets) {
        val missing = buildList {
            if (releaseKeystoreBase64.isEmpty()) add("ANDROID_KEYSTORE_BASE64")
            if (releaseStorePassword.isEmpty()) add("ANDROID_KEYSTORE_PASSWORD")
            if (releaseKeyAlias.isEmpty()) add("ANDROID_KEY_ALIAS")
            if (releaseKeyPassword.isEmpty()) add("ANDROID_KEY_PASSWORD")
        }
        throw GradleException(
            "Release signing secrets are missing: ${missing.joinToString(", ")}. " +
                "Configure them as environment variables or GitHub Actions secrets before running a release build."
        )
    }

    val decodedReleaseKeystoreFile = if (hasReleaseSigningSecrets) {
        layout.buildDirectory.file("signing/release-keystore.jks").get().asFile.apply {
            parentFile?.mkdirs()
            writeBytes(Base64.getMimeDecoder().decode(releaseKeystoreBase64))
        }
    } else {
        null
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigningSecrets && decodedReleaseKeystoreFile != null) {
            create("release") {
                storeFile = decodedReleaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningSecrets) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // implementation(libs.androidx.compose.material3.windowsize)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
