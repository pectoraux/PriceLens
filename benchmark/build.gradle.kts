plugins {
    id("pricelens.android.test")
}

android {
    namespace = "com.pricelens.benchmark"
    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.espresso.core)
}
