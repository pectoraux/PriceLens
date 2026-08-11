plugins {
    id("pricelens.android.library")
}

android {
    namespace = "com.pricelens.ml.pipeline.api"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
}
