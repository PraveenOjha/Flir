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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    buildFeatures {
        viewBinding = true
    }

   
    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

dependencies {
    // React Native
    implementation("com.facebook.react:react-native:+")
    
    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // FLIR SDK - Use the actual AAR files from libs folder
    // Using 'api' to expose SDK classes to consumers and Java source files
    // flatDir must be configured in settings.gradle or root build.gradle
    api(files("libs/androidsdk-release.aar"))
    api(files("libs/thermalsdk-release.aar"))
    
    // Minimal compile deps to satisfy source references
    implementation("androidx.annotation:annotation:1.5.0")

    // Prevent duplicate SLF4J classes when a consumer also brings `org.slf4j:slf4j-api`
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
