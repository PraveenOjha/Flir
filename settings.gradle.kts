rootProject.name = "flir-thermal-sdk"

// The Android module folder was renamed to `Flir` (capital F) — keep Gradle project path consistent
include(":Flir")
project(":Flir").projectDir = file("android/Flir")
