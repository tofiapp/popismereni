plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

/** Jediný zdroj pravdy: soubor VERSION v kořeni repa (např. 0.1.0). */
fun readAppVersion(): Pair<String, Int> {
    val versionFile = rootProject.file("VERSION")
    require(versionFile.exists()) { "Chybí soubor VERSION v kořeni projektu" }
    val name = versionFile.readText().trim()
    require(name.matches(Regex("""\d+\.\d+\.\d+"""))) {
        "VERSION musí být ve formátu MAJOR.MINOR.PATCH, je: '$name'"
    }
    val parts = name.split(".").map { it.toInt() }
    val code = parts[0] * 10_000 + parts[1] * 100 + parts[2]
    return name to code
}

val (appVersionName, appVersionCode) = readAppVersion()

android {
    namespace = "cz.mereni.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "cz.mereni.app"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        // Pro CI a pojmenování APK: mereni-v0.1.0-debug.apk
        buildConfigField("String", "VERSION_NAME", "\"$appVersionName\"")
        setProperty("archivesBaseName", "mereni-v$appVersionName")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // debug i release sdílí applicationId cz.mereni.app → aktualizace na zařízení bez přeinstalace
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
