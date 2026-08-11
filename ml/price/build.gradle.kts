plugins {
    id("pricelens.android.library")
}

android {
    namespace = "com.pricelens.ml.price"
}

dependencies {
    implementation(project(":core:common"))
}
