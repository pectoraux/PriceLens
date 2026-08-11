pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PriceLens"

include(":app")

// Features
include(":feature:capture")
include(":feature:review")
include(":feature:contribute")
include(":feature:explore")

// Domain
include(":domain")

// ML
include(":ml:pipeline")
include(":ml:pipeline-api")
include(":ml:deviceprofile")
include(":ml:portion")
include(":ml:price")
include(":ml:detect")
include(":ml:embed")
include(":ml:retrieval")
include(":ml:ocr")
include(":ml:fusion")
include(":ml:runtime")

// Core
include(":core:trust")
include(":core:attest")
include(":core:geo")
include(":core:data")
include(":core:designsystem")
include(":core:common")
include(":core:testing")
include(":benchmark")
