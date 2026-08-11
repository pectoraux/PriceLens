plugins {
    id("pricelens.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pricelens"
    defaultConfig {
        applicationId = "com.pricelens"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":feature:capture"))
    implementation(project(":feature:review"))
    implementation(project(":feature:contribute"))
    implementation(project(":feature:explore"))
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:trust"))
    implementation(project(":core:attest"))
    implementation(project(":core:geo"))
    implementation(project(":core:designsystem"))

    implementation(project(":ml:pipeline"))
    implementation(project(":ml:pipeline-api"))
    implementation(project(":ml:deviceprofile"))
    implementation(project(":ml:runtime"))
    implementation(project(":ml:detect"))
    implementation(project(":ml:embed"))
    implementation(project(":ml:retrieval"))
    implementation(project(":ml:ocr"))
    implementation(project(":ml:portion"))
    implementation(project(":ml:fusion"))
    implementation(project(":ml:price"))

    implementation(libs.google.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    implementation(libs.androidx.profileinstaller)
}
