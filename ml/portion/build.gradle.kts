plugins {
    id("pricelens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.ml.portion"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:geo"))
    implementation(project(":domain"))
    implementation(project(":ml:pipeline-api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
