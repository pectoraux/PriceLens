plugins {
    id("pricelens.android.feature")
}

android {
    namespace = "com.pricelens.feature.explore"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:geo"))
    implementation(project(":domain"))

    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.hilt.navigation.compose)
}
