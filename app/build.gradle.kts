import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zero.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zero.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 170707
        versionName = "17.7.07"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.biometric:biometric:1.4.0-alpha02")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

tasks.register("packageDebugZip") {
    group = "distribution"
    description = "Builds and validates the Zero-Assistant.zip debug download."
    dependsOn("assembleDebug")

    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        check(apk.isFile && apk.length() > 0L) { "Debug APK is missing or empty: $apk" }

        ZipFile(apk).use { apkZip ->
            val requiredEntries = listOf("AndroidManifest.xml", "classes.dex", "resources.arsc")
            requiredEntries.forEach { entry -> check(apkZip.getEntry(entry) != null) { "APK is missing $entry" } }
            val entries = apkZip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                apkZip.getInputStream(entry).use { input -> input.transferTo(OutputStream.nullOutputStream()) }
            }
        }

        val staging = layout.buildDirectory.dir("downloadable").get().asFile
        check(staging.isDirectory || staging.mkdirs()) { "Unable to create package staging directory" }
        val stagedApk = File(staging, "Zero-Assistant.apk")
        Files.copy(apk.toPath(), stagedApk.toPath(), StandardCopyOption.REPLACE_EXISTING)
        check(stagedApk.length() == apk.length()) { "Staged APK copy is incomplete" }

        val stagedZip = File(staging, "Zero-Assistant.zip.tmp")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(stagedZip))).use { output ->
            output.putNextEntry(ZipEntry("Zero-Assistant.apk"))
            stagedApk.inputStream().use { it.copyTo(output) }
            output.closeEntry()
        }
        ZipFile(stagedZip).use { packageZip ->
            check(packageZip.size() == 1) { "Download ZIP must contain exactly one root-level APK" }
            val entry = packageZip.getEntry("Zero-Assistant.apk")
                ?: throw GradleException("Download ZIP is missing root entry Zero-Assistant.apk")
            check(entry.size == apk.length()) { "ZIP APK size does not match the built APK" }
            packageZip.getInputStream(entry).use { it.transferTo(OutputStream.nullOutputStream()) }
        }

        val finalZip = File(staging, "Zero-Assistant.zip")
        Files.move(stagedZip.toPath(), finalZip.toPath(), StandardCopyOption.REPLACE_EXISTING)
        check(finalZip.isFile && finalZip.length() > 0L) { "Download ZIP is missing or empty" }
        logger.lifecycle("Validated ${finalZip.absolutePath} (${finalZip.length()} bytes) containing Zero-Assistant.apk (${apk.length()} bytes)")
    }
}