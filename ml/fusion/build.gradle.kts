plugins {
    id("pricelens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.ml.fusion"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":ml:pipeline-api"))
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
