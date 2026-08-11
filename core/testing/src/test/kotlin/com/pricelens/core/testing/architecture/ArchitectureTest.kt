package com.pricelens.core.testing.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.verify.assertTrue
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

class ArchitectureTest {

    @Test
    fun `domain module should not import android classes`() {
        Konsist.scopeFromProject()
            .files
            .filter { it.path.contains("/domain/") }
            .assertTrue { file ->
                file.imports.none { 
                    it.name.startsWith("android.") || it.name.startsWith("androidx.") 
                }
            }
    }

    @Test
    fun `domain module should not depend on other modules`() {
        Konsist.scopeFromProject()
            .files
            .filter { it.path.contains("/domain/") }
            .assertTrue { file ->
                file.imports.none { it.name.startsWith("com.pricelens.core.data") }
            }
    }

    @Test
    fun `ml modules should not import camera classes directly`() {
        Konsist.scopeFromProject()
            .files
            .filter { it.path.contains("/ml/") }
            .assertTrue { file ->
                file.imports.none { it.name.startsWith("androidx.camera.") }
            }
    }

    @Test
    fun `feature modules should not depend on each other`() {
        val features = listOf("capture", "review", "contribute", "explore")
        features.forEach { feature ->
            Konsist.scopeFromProject()
                .files
                .filter { it.path.contains("/feature/$feature/") }
                .assertTrue { file ->
                    file.imports.none { import ->
                        features.any { other -> 
                            other != feature && import.name.startsWith("com.pricelens.feature.$other") 
                        }
                    }
                }
        }
    }

    @Test
    fun `no field named price should be Double or Float`() {
        Konsist.scopeFromProject()
            .classes()
            .properties()
            .filter { it.name.contains("price", ignoreCase = true) }
            .assertFalse { it.type?.name == "Double" || it.type?.name == "Float" }
    }

    @Test
    fun `no double bang operator in production code`() {
        Konsist.scopeFromProject()
            .files
            .filterNot { it.path.contains("/test/") || it.path.contains("/androidTest/") }
            .assertTrue { file ->
                !file.text.contains("!!")
            }
    }
}
