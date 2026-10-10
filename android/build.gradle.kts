// Top-level build file for Centium Android VPN project
plugins {
    // AGP 9 compiles Kotlin itself (no separate kotlin-android plugin).
    // tor-android 0.4.9.x requires compileSdk 37, which needs AGP 9.
    id("com.android.application") version "9.4.1" apply false
}
