plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.core)

    testRuntimeOnly(libs.junit.jupiter.engine)
}

/*dependencies {
  implementation(Dependencies.kotlinStdLib)

  testCompile(Dependencies.junit5Params)
  testRuntime(Dependencies.junit5Engine)
  testImplementation(Dependencies.mockito)
  testImplementation(Dependencies.truth)
}*/
