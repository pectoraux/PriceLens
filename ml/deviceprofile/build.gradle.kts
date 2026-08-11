plugins {
    id("pricelens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.ml.deviceprofile"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:trust"))
    implementation(project(":domain"))
    implementation(project(":ml:pipeline-api"))
    implementation(project(":ml:runtime"))

    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.litert.api)

    implementation(libs.protobuf.kotlin.lite)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:trust"))
}
