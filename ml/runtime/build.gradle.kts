plugins {
    id("pricelens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.ml.runtime"
}

dependencies {
    implementation(project(":core:common"))
    
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.litert.api)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
