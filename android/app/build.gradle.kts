import java.util.Properties

plugins {
    id("com.android.application")
}

val centiumVersionName = providers.gradleProperty("centium.versionName").get()
val centiumVersionCode = providers.gradleProperty("centium.versionCode").get().toInt()

/** 1.2.3 -> 10203; must only ever increase, Android rejects lower codes as updates. */
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toInt() }
    require(parts.size == 3 && parts.all { it in 0..99 }) { "centium.versionName must be x.y.z with parts 0-99" }
    return parts[0] * 10000 + parts[1] * 100 + parts[2]
}

/**
 * Release signing: CI passes CENTIUM_* env vars; locally, local.properties may point
 * `centium.signing` at a properties file outside the repo. Without either, release is unsigned.
 */
fun releaseSigning(): Map<String, String>? {
    val env = System.getenv()
    if (env["CENTIUM_KEYSTORE"] != null) {
        return mapOf(
            "storeFile" to env.getValue("CENTIUM_KEYSTORE"),
            "storePassword" to env.getValue("CENTIUM_KEYSTORE_PASSWORD"),
            "keyAlias" to env.getValue("CENTIUM_KEY_ALIAS"),
            "keyPassword" to env.getValue("CENTIUM_KEY_PASSWORD")
        )
    }
    val local = rootProject.file("local.properties")
    if (!local.exists()) return null
    val path = Properties().apply { local.inputStream().use { load(it) } }.getProperty("centium.signing") ?: return null
    val props = Properties().apply { file(path).inputStream().use { load(it) } }
    return props.stringPropertyNames().associateWith { props.getProperty(it) }
}

android {
    namespace = "org.centium.vpn"
    // tor-android 0.4.9.x declares minCompileSdk 37.1. targetSdk 36 is Google Play's minimum since 31 Aug 2026.
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.centium.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = centiumVersionCode.also {
            require(it == versionCodeOf(centiumVersionName)) {
                "centium.versionCode must be ${versionCodeOf(centiumVersionName)} for versionName $centiumVersionName"
            }
        }
        versionName = centiumVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
        }

        externalNativeBuild {
            ndkBuild {
                // Only the JNI library; upstream also defines a standalone binary
                targets("hev-socks5-tunnel")
            }
        }
    }

    ndkVersion = "26.3.11579264"

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    val signing = releaseSigning()
    if (signing != null) {
        signingConfigs {
            create("release") {
                storeFile = file(signing.getValue("storeFile"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signing != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Debug builds have their own package and key, so release updates can't apply
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
            applicationIdSuffix = ".debug"
        }
    }

    // github: self-updates from GitHub Releases. fdroid and play: the store delivers updates,
    // so the updater is switched off and its permission/receivers are stripped.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Tor daemon (libtor.so + TorService) and its control-protocol client
    implementation("info.guardianproject:tor-android:0.4.9.14")
    implementation("info.guardianproject:jtorctl:0.4.5.7")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
