plugins {
    `kotlin-dsl`
}

group = "com.pricelens.gradle"

dependencies {
    implementation("com.android.tools.build:gradle:9.3.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.0")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.11")
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "pricelens.android.library"
            implementationClass = "com.pricelens.gradle.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "pricelens.android.application"
            implementationClass = "com.pricelens.gradle.AndroidApplicationConventionPlugin"
        }
        register("androidFeature") {
            id = "pricelens.android.feature"
            implementationClass = "com.pricelens.gradle.AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "pricelens.jvm.library"
            implementationClass = "com.pricelens.gradle.JvmLibraryConventionPlugin"
        }
        register("androidTest") {
            id = "pricelens.android.test"
            implementationClass = "com.pricelens.gradle.AndroidTestConventionPlugin"
        }
    }
}
