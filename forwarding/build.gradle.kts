plugins {
    kotlin("jvm")
}

val attributeCachingFilesystemVersion: String by project
version = attributeCachingFilesystemVersion

dependencies {
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)

    testRuntimeOnly(libs.junit.jupiter.engine)
}

kotlin {
    explicitApi()
}
