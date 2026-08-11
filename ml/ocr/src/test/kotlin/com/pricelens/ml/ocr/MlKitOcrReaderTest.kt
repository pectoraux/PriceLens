package com.pricelens.ml.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MlKitOcrReaderTest {

    private val reader = MlKitOcrReader()

    @Test
    fun `parseMass should correctly parse various weight strings`() {
        val method = reader.javaClass.getDeclaredMethod("parseMass", String::class.java)
        method.isAccessible = true

        assertEquals(500f, method.invoke(reader, "Net weight: 500g") as Float, 0.1f)
        assertEquals(1000f, method.invoke(reader, "1 kg") as Float, 0.1f)
        assertEquals(250f, method.invoke(reader, "250 ml") as Float, 0.1f)
        assertEquals(2000f, method.invoke(reader, "2L") as Float, 0.1f)
        assertNull(method.invoke(reader, "No weight here"))
    }
}
