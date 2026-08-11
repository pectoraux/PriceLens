plugins {
    id("pricelens.android.feature")
}

android {
    namespace = "com.pricelens.feature.capture"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:geo"))
    implementation(project(":core:trust"))
    implementation(project(":core:attest"))
    implementation(project(":ml:runtime"))
    implementation(project(":ml:pipeline"))
    implementation(project(":ml:pipeline-api"))
    implementation(project(":ml:deviceprofile"))

    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.camera2)
}
