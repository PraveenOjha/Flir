plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "flir.android"
    compileSdk = property("compileSdkVersion").toString().toInt()

    defaultConfig {
        minSdk = property("minSdkVersion").toString().toInt()
        targetSdk = property("targetSdkVersion").toString().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Packaging options kept minimal
    packaging {
        jniLibs {
            val enableLegacy = (findProperty("expo.useLegacyPackaging") as? String ?: "false").toBoolean()
            useLegacyPackaging = enableLegacy
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    // Provide React Native Android APIs to compile RN bridge/view classes
    implementation("com.facebook.react:react-android")
    // Vendored FLIR SDK AARs (copied into libs/ by copyFlirAars task)
    implementation(files("libs/thermalsdk-release.aar"))
    implementation(files("libs/androidsdk-release.aar"))
}

// Ensure Kotlin and Java compile tasks target JVM 21 using Gradle toolchains.
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
}

// Configure Gradle Java toolchain for Java compilation (use Java 21 compiler).
val javaLanguageVersion = JavaLanguageVersion.of(21)
tasks.withType(JavaCompile::class.java).configureEach {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(javaLanguageVersion)
    })
}

// Also configure Kotlin JVM toolchain to ensure the Kotlin compiler uses Java 21 runtime.
kotlin {
    jvmToolchain(21)
}

// Follow FLIR sample style: rely on `compileOptions` + `org.gradle.java.home`
// instead of setting explicit toolchains or fork executables here.

// Copy FLIR AARs from workspace flir/lib/android into this module's libs folder
tasks.register("copyFlirAars") {
    doLast {
        val projectRoot = rootDir.parentFile.absolutePath
        val flirAarsSrc = file("$projectRoot/flir/lib/android")
        val flirLibsDst = file("${projectDir}/libs")
        if (!flirAarsSrc.exists()) return@doLast
        if (!flirLibsDst.exists()) flirLibsDst.mkdirs()
        copy {
            from(flirAarsSrc)
            include("*.aar")
            into(flirLibsDst)
        }
    }
}

// Ensure AARs are available before compiling
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(tasks.named("copyFlirAars")) }
