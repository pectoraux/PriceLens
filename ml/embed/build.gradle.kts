plugins {
    id("pricelens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.ml.embed"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":ml:runtime"))
    implementation(project(":ml:pipeline-api"))

    implementation(libs.litert)
    implementation(libs.litert.api)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
