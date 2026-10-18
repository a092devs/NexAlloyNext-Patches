plugins {
    id("nexalloy.bundle")
}

android {
    namespace = "io.github.nexalloy.patches.nexfusion"

    defaultConfig {
        applicationId = "io.github.nexalloy.patches.nexfusion"
        versionCode = 14
        versionName = "3.2.1"
    }
}

nexalloyBundle {
    entry = "io.github.nexalloy.nexfusion.NexFusionPatches"
    author = "a092devs"
    descriptionResource = "bundle_description"
    resourcePackageId = 0x4e
    compatibilitySeries = "3.2"
    minManagerVersion = "3.2.0"
}

dependencies {
    compileOnly(libs.nexalloy.patcher)
}

// Fingerprint tests run against the APKs in `binaries/`, named `<package name>…apk`.
android.sourceSets.named("test") {
    java.directories += "../testing/java"
    kotlin.directories += "../testing/java"
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.library.path", file("../testing/jniLibs").path)
}

dependencies {
    testImplementation(libs.nexalloy.patcher)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.jadx.core)
    testImplementation(libs.slf4j.simple)
}
