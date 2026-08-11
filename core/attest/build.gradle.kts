plugins {
    id("pricelens.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.core.attest"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.play.integrity)
    implementation(libs.kotlinx.coroutines.play.services)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
