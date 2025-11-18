plugins {
    id("com.android.library")
    id("kotlin-android")
    id("maven-publish")
}

android {
    namespace = "flir.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
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
    compileOnly("com.facebook.react:react-android:0.76.0")
    // FLIR SDK AARs - extract and repackage
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}

// Configure Java 21 toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Ensure Kotlin also uses Java 21
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

// Maven publishing configuration for JitPack
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.PraveenOjha"
                artifactId = "flir-thermal-sdk"
                version = "1.0.0"

                pom {
                    name.set("FLIR Thermal SDK for Android")
                    description.set("FLIR Thermal SDK React Native Android wrapper")
                    url.set("https://github.com/PraveenOjha/Flir")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("PraveenOjha")
                            name.set("Praveen Ojha")
                        }
                    }
                }
            }
        }
    }
}
