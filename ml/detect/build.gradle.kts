plugins {
    id("pricelens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.ml.detect"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":ml:runtime"))
    implementation(project(":ml:pipeline-api"))

    implementation(libs.mlkitface)
    implementation(libs.mlkitvisioncommon)
    implementation(libs.play.tasks)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.litert)
    implementation(libs.litert.api)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
