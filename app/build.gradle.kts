import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.lineageos.generatebp.GenerateBpPlugin
import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module

plugins {
    id("com.android.application")
    id("kotlin-android")
}

apply {
    plugin<GenerateBpPlugin>()
}

buildscript {
    repositories {
        maven("https://raw.githubusercontent.com/lineage-next/gradle-generatebp/v1.3/.m2")
    }

    dependencies {
        classpath("org.lineageos:gradle-generatebp:1.3")
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "org.lineageos.updater"
        minSdk = 32
        targetSdk = 33
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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

dependencies {
    compileOnly(fileTree(mapOf("dir" to "../system_libs", "include" to listOf("*.jar"))))
    annotationProcessor("androidx.room:room-compiler:2.8.3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.room:room-ktx:2.8.3")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.work:work-runtime:2.11.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

configure<GenerateBpPluginExtension> {
    targetSdk.set(android.defaultConfig.targetSdk!!)
    availableInAOSP.set { module: Module ->
        when {
            module.group.startsWith("androidx") -> true
            module.group.startsWith("org.jetbrains") -> true
            module.group == "com.google.android.material" -> true
            module.group == "com.google.errorprone" -> true
            module.group == "com.google.guava" -> true
            module.group == "org.jspecify" -> true
            module.group == "junit" -> true
            else -> false
        }
    }
}
