plugins {
    id("pricelens.jvm.library")
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.konsist)
    implementation(libs.junit)
}
