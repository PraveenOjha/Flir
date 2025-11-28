plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "flir.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

repositories {
    google()
    mavenCentral()
    // Resolve local AARs which will be installed into mavenLocal by CI (JitPack)
    mavenLocal()
    flatDir { dirs("libs") }
}

dependencies {
    // React Native
    implementation("com.facebook.react:react-native:+")
    
    // Kotlin coroutines for async downloads
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Play Feature Delivery (optional - for Play Store SDK delivery)
    implementation("com.google.android.play:feature-delivery:2.1.0")
    implementation("com.google.android.play:feature-delivery-ktx:2.1.0")
    
    // FLIR SDK binary artifacts - compileOnly since they're downloaded on-demand at runtime
    // These are needed for compilation but NOT bundled with the app
    // Users download them at runtime via FlirDownload.download()
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    
    // Minimal compile deps to satisfy source references
    implementation("androidx.annotation:annotation:1.5.0")


    // Prevent duplicate SLF4J classes when a consumer also brings `org.slf4j:slf4j-api`
    // The vendor AAR may embed slf4j classes; exclude the API from being pulled transitively
    configurations.all {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
                groupId = "com.github.PraveenOjha"
                artifactId = "Flir"
                version = project.version.toString().ifEmpty { "unspecified" }
            }
        }
    }
}
