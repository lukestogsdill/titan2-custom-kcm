plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The layout stays at the repo root (so it can still be pasted into ExKeyMo) and is
// copied into res/raw at build time. Resource names must be lowercase, no dashes.
val kcmResDir = layout.buildDirectory.dir("generated/kcm-res").get().asFile
val copyKcm by tasks.registering(Copy::class) {
    from(rootProject.file("../titan2-custom.kcm"))
    into(File(kcmResDir, "raw"))
    rename { "titan2_custom.kcm" }
}

android {
    namespace = "dev.titan2.keys"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.titan2.keys"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets["main"].res.srcDir(kcmResDir)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.named("preBuild") { dependsOn(copyKcm) }
