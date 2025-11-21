plugins {
    id("com.android.library")
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
    // FLIR SDK binary artifacts placed in libs/
    // On CI (JitPack) we install these AARs into mavenLocal before publishing - use maven coordinates
    implementation("com.flir:thermalsdk:1.0.0")
    implementation("com.flir:androidsdk:1.0.0")
    // minimal compile deps to satisfy source references
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
