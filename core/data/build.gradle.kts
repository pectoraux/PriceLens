plugins {
    id("pricelens.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "com.pricelens.core.data"
    sourceSets {
        getByName("main") {
            java.srcDirs(file("${projectDir}/build/generated/openapi/src/main/kotlin"))
        }
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/api/openapi.yaml")
    outputDir.set("${projectDir}/build/generated/openapi")
    apiPackage.set("com.pricelens.core.data.remote.api")
    modelPackage.set("com.pricelens.core.data.remote.model")
    library.set("jvm-ktor")
    configOptions.set(mapOf(
        "serializationLibrary" to "kotlinx_serialization",
        "enumPropertyNaming" to "UPPERCASE"
    ))
    typeMappings.set(mapOf(
        "number" to "kotlin.Double",
        "decimal" to "kotlin.Double",
        "BigDecimal" to "kotlin.Double"
    ))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("openApiGenerate")
    source("${projectDir}/build/generated/openapi/src/main/kotlin")
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn("openApiGenerate")
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") {
                    option("lite")
                }
                register("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:attest"))
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.kotlin.lite)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
