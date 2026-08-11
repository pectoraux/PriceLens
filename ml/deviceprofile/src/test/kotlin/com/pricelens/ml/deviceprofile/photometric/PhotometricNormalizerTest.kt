package com.pricelens.ml.deviceprofile.photometric

import android.graphics.Bitmap
import android.graphics.Color
import com.pricelens.ml.deviceprofile.optical.CanonicalReprojector
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotometricNormalizerTest {

    private val reprojector = mockk<CanonicalReprojector>()
    private lateinit var normalizer: PhotometricNormalizer

    @Before
    fun setup() {
        normalizer = PhotometricNormalizer(reprojector)
    }

    @Test
    fun `applyShadesOfGrey should map known neutral input to near-neutral output`() = runTest {
        // Create a 10x10 bitmap with a distinct blue tint
        // R=100, G=100, B=150
        val pixels = IntArray(100) { Color.rgb(100, 100, 150) }
        val bitmap = Bitmap.createBitmap(pixels, 10, 10, Bitmap.Config.ARGB_8888)

        // Reflection to access private method for testing logic
        val method = normalizer.javaClass.getDeclaredMethod("applyShadesOfGrey", Bitmap::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        val result = method.invoke(normalizer, bitmap, 6) as Bitmap

        val outPixel = result.getPixel(5, 5)
        val r = Color.red(outPixel)
        val g = Color.green(outPixel)
        val b = Color.blue(outPixel)

        // After shades-of-grey, R, G, B should be very close to each other
        val maxDiff = maxOf(Math.abs(r - g), Math.abs(g - b), Math.abs(r - b))
        assertTrue("Max RGB diff was $maxDiff, expected < 5", maxDiff <= 5)
    }
}
