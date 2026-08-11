plugins {
    id("pricelens.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens.core.trust"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:attest"))
    implementation(project(":core:geo"))
    implementation(project(":core:data"))
    implementation(project(":domain"))
    
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
