plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val appVersion = java.util.Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val releaseEnvironment = listOf("ANDROID_RELEASE_KEYSTORE", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotEmpty) }
val releaseSigningAvailable = releaseEnvironment.values.all { it != null }
require(releaseSigningAvailable || releaseEnvironment.values.all { it == null }) {
    "Incomplete release signing environment; configure all four signing variables or none"
}

android {
    namespace = "com.danishbears.bulgariantrainer"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.danishbears.bulgariantrainer"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersion.getProperty("VERSION_CODE").toInt().also { require(it in 1..2100000000) }
        versionName = appVersion.getProperty("VERSION_NAME").also { require(it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) }
    }

    if (releaseSigningAvailable) {
        signingConfigs.create("personalRelease") {
            storeFile = file(releaseEnvironment.getValue("ANDROID_RELEASE_KEYSTORE")!!).also {
                require(it.isFile) { "Release keystore file is unavailable" }
            }
            storePassword = releaseEnvironment.getValue("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = releaseEnvironment.getValue("ANDROID_KEY_ALIAS")
            keyPassword = releaseEnvironment.getValue("ANDROID_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("personalRelease")
    }

    buildFeatures {
        compose = true
    }

    sourceSets.getByName("test").resources.srcDirs("schemas", "src/main/assets", "../parity/fixtures")

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("androidx.room:room-paging:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    ksp("androidx.room:room-compiler:2.7.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("androidx.paging:paging-testing:3.3.6")
}
