plugins {
    id("pricelens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.ml.ocr"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":ml:pipeline-api"))
    
    implementation(libs.ml.kit.text)
    implementation(libs.ml.kit.barcode)
    implementation(libs.play.tasks)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
