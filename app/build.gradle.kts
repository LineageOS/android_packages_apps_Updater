/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.lineageos.generatebp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "org.lineageos.updater"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
        }
        getByName("debug") {
            // Append .dev to package name so we won't conflict with AOSP build.
            applicationIdSuffix = ".dev"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    signingConfigs {
        create("release") {
            (keystoreProperties["keyAlias"] as String?)?.let {
                keyAlias = it
            }
            (keystoreProperties["keyPassword"] as String?)?.let {
                keyPassword = it
            }
            (keystoreProperties["storeFile"] as String?)?.let {
                storeFile = file(it)
            }
            (keystoreProperties["storePassword"] as String?)?.let {
                storePassword = it
            }
        }
    }
    namespace = "org.lineageos.updater"
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Exclude artifacts that should not appear as generated Soong runtime modules.
configurations.all {
    // Ktor does not need the Java 8 compatibility bridge on Android.
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
    // BOMs are metadata-only artifacts, not runtime jars for generated Soong static_libs.
    // external/kotlinx.coroutines/kotlinx-coroutines-bom
    // external/kotlinx.serialization/bom
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-bom")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-bom")
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "../system_libs", "include" to listOf("*.jar"))))

    debugImplementation(files("../system_libs/SettingsLib.jar", "../system_libs/SpaLib.jar"))
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)

    annotationProcessor(libs.androidx.room.compiler)
}

configure<GenerateBpPluginExtension> {
    targetSdk.set(android.defaultConfig.targetSdk!!)
    minSdk.set(android.defaultConfig.minSdk!!)
    versionCode.set(android.defaultConfig.versionCode!!)
    versionName.set(android.defaultConfig.versionName!!)
    availableInAOSP.set { module: Module ->
        when {
            module.group.startsWith("androidx") -> true

            // This module does not expose an Android Soong module.
            // external/kotlinx.coroutines/integration/kotlinx-coroutines-slf4j
            module.group == "org.jetbrains.kotlinx" &&
                    module.name.startsWith("kotlinx-coroutines-slf4j") -> false

            // These artifacts are not provided by the platform.
            module.group == "org.jetbrains.kotlinx" &&
                    module.name.startsWith("kotlinx-io") -> false

            module.group.startsWith("org.jetbrains") -> true
            module.group == "com.google.android.material" -> true
            module.group == "com.google.errorprone" -> true
            module.group == "com.google.guava" -> true
            module.group == "junit" -> true
            else -> false
        }
    }
}
