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

/**
 * hev-socks5-tunnel is built from a copy of the submodule with our patches applied, so the
 * submodule stays pristine upstream. Patches apply in filename order:
 *   hev-udp-reject.patch      adds `udp: 'reject'` (see below)
 *   hev-zz-dns-blocklist.patch adds a `mapdns.blocklist` file whose domains (and their
 *                             subdomains) get NXDOMAIN, for ad/tracker blocking.
 * hev-udp-reject.patch adds `udp: 'reject'`, which answers
 * UDP with ICMP port unreachable (Tor can't carry UDP), so apps drop QUIC for TCP immediately.
 */
val hevPatchedDir = layout.buildDirectory.dir("hev-socks5-tunnel")
val hevPatches = file("src/main/jni/patches")
val prepareHev by tasks.registering(Sync::class) {
    from("src/main/jni/hev-socks5-tunnel") { exclude("**/.git", "**/.git/**") }
    into(hevPatchedDir)
    inputs.dir(hevPatches)
    val patches = hevPatches.listFiles { f -> f.extension == "patch" }!!.sorted()
    val target = hevPatchedDir
    doLast {
        patches.forEach { patch ->
            // The copy sits inside this repo, where `git apply` would silently skip its files;
            // the ceiling makes git treat it as a plain directory, like `patch`.
            val dir = target.get().asFile
            val proc = ProcessBuilder("git", "apply", "--verbose", patch.absolutePath)
                .directory(dir).redirectErrorStream(true)
                .apply { environment()["GIT_CEILING_DIRECTORIES"] = dir.parentFile.absolutePath }
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            check(proc.waitFor() == 0 && "Applied patch" in out) { "Applying ${patch.name} failed: $out" }
        }
    }
}
tasks.matching { it.name.startsWith("configureNdkBuild") || it.name.startsWith("buildNdkBuild") }
    .configureEach { dependsOn(prepareHev) }

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

        externalNativeBuild {
            ndkBuild {
                // Only the JNI library; upstream also defines a standalone binary
                targets("hev-socks5-tunnel")
                // Build from the patched copy (see prepareHev below), not the submodule itself
                arguments("HEV_SRC=${hevPatchedDir.get().asFile.absolutePath}", "REV_ID=2.18.0-centium")
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
    // ABIs are set per flavor because flavor abiFilters add to defaultConfig's, never replace them.
    // github and fdroid ship one APK, so they carry ARM only: the pluggable transports add ~24 MB
    // per ABI, and x86 devices (Chromebooks, emulators) get Centium from Play instead.
    // Play splits the bundle per device, so it keeps all four.
    val armAbis = setOf("arm64-v8a", "armeabi-v7a")
    val allAbis = armAbis + setOf("x86_64", "x86")
    flavorDimensions += "distribution"
    productFlavors {
        // ADS_BLOCKING_ALLOWED gates the ad-network list. It's off for Play, where blocking ads
        // in other apps risks removal; Play keeps only the tracker/malware list. F-Droid and
        // GitHub face no such policy, so they can block ad networks too.
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
            buildConfigField("boolean", "ADS_BLOCKING_ALLOWED", "true")
            ndk { abiFilters.addAll(armAbis) }
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
            buildConfigField("boolean", "ADS_BLOCKING_ALLOWED", "true")
            ndk { abiFilters.addAll(armAbis) }
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
            buildConfigField("boolean", "ADS_BLOCKING_ALLOWED", "false")
            ndk { abiFilters.addAll(allAbis) }
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
    // Snowflake, obfs4 and WebTunnel clients (Lyrebird + Snowflake), as used by Orbot
    implementation("com.netzarchitekten:IPtProxy:5.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
