package com.pricelens.ml.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.pricelens.core.data.model.OpticalProfileProto
import com.pricelens.ml.pipeline.contract.*
import com.pricelens.ml.pipeline.model.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InferencePipelineTest {

    private val qualityGate = mockk<QualityGate>()
    private val frameNormalizer = mockk<FrameNormalizer>()
    private val detector = mockk<Detector>()
    private val embedder = mockk<Embedder>()
    private val retriever = mockk<Retriever>()
    private val textMatcher = mockk<TextMatcher>()
    private val ocrReader = mockk<OcrReader>()
    private val portionEstimator = mockk<PortionEstimator>()
    private val fuser = mockk<Fuser>()

    private lateinit var pipeline: InferencePipeline

    @Before
    fun setup() {
        pipeline = InferencePipeline(
            qualityGate, frameNormalizer, detector, embedder,
            retriever, textMatcher, ocrReader, portionEstimator, fuser
        )
    }

    @Test
    fun `processFrame should return null if quality gate fails`() = runTest {
        val bitmap = mockk<Bitmap>()
        coEvery { qualityGate.assess(any()) } returns QualityGate.QualityResult(false, 0f, 0f, listOf("BLUR"))

        val result = pipeline.processFrame(bitmap, mockk())

        assertNull(result)
        coVerify(exactly = 1) { qualityGate.assess(bitmap) }
        coVerify(exactly = 0) { frameNormalizer.normalize(any(), any(), any()) }
    }

    @Test
    fun `processFrame should handle stage timeout gracefully`() = runTest {
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val opticalProfile = mockk<OpticalProfileProto>()
        val canonicalBitmap = Bitmap.createBitmap(448, 448, Bitmap.Config.ARGB_8888)
        val canonicalFrame = CanonicalFrame(canonicalBitmap, 0L)

        coEvery { qualityGate.assess(any()) } returns QualityGate.QualityResult(true, 100f, 0.5f, emptyList())
        coEvery { frameNormalizer.normalize(any(), any(), any()) } returns canonicalFrame
        
        // Simulate a slow detector that exceeds 90ms budget
        coEvery { detector.detect(any()) } coAnswers {
            delay(500) // Exceeds 90ms
            emptyList()
        }

        val result = pipeline.processFrame(bitmap, opticalProfile)

        // In the current implementation, if the detector times out, it returns null, 
        // and selectDominantRegion isn't called, and processFrame returns null.
        assertNull(result)
    }

    @Test
    fun `processFrame should continue if non-critical stage times out`() = runTest {
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val opticalProfile = mockk<OpticalProfileProto>()
        val canonicalBitmap = Bitmap.createBitmap(448, 448, Bitmap.Config.ARGB_8888)
        val canonicalFrame = CanonicalFrame(canonicalBitmap, 0L)
        val detection = Detection(RectF(100f, 100f, 200f, 200f), 0.9f, "tomato")

        coEvery { qualityGate.assess(any()) } returns QualityGate.QualityResult(true, 100f, 0.5f, emptyList())
        coEvery { frameNormalizer.normalize(any(), any(), any()) } returns canonicalFrame
        coEvery { detector.detect(any()) } returns listOf(detection)
        coEvery { embedder.embed(any(), any()) } returns FloatArray(512)
        
        // OCR is non-critical, let's time it out
        coEvery { ocrReader.read(any(), any()) } coAnswers {
            delay(500) // Exceeds 120ms
            OcrReader.OcrResult("123", "text")
        }
        
        coEvery { textMatcher.match(any(), any()) } returns emptyMap()
        coEvery { portionEstimator.estimate(any(), any(), any()) } returns PortionEstimator.PortionResult(250f, 0.9f)
        coEvery { retriever.retrieve(any()) } returns listOf(Retriever.Candidate("tomato", 0.8f))
        coEvery { fuser.fuse(any(), any(), any()) } returns listOf(Fuser.Prediction("tomato", 0.85f))

        val result = pipeline.processFrame(bitmap, opticalProfile)

        assertNotNull(result)
        val ocrTiming = result?.timings?.find { it.stageName == "OcrReader" }
        assertEquals(StageStatus.TIMEOUT, ocrTiming?.status)
        assertEquals("tomato", result?.label)
    }

    @Test
    fun `selectDominantRegion should pick the sharpest and most central object`() = runTest {
        val d1 = Detection(RectF(100f, 100f, 200f, 200f), 0.9f, "item1", focusSharpness = 10f)
        val d2 = Detection(RectF(150f, 150f, 250f, 250f), 0.9f, "item2", focusSharpness = 50f) // Sharpest
        val d3 = Detection(RectF(0f, 0f, 50f, 50f), 0.9f, "item3", focusSharpness = 100f) // Very sharp but far from center

        // Using reflection to test private method
        val method = pipeline.javaClass.getDeclaredMethod("selectDominantRegion", List::class.java)
        method.isAccessible = true
        val result = method.invoke(pipeline, listOf(d1, d2, d3)) as Detection?

        assertEquals("item2", result?.label)
    }

    @Test
    fun `calculateFocusSharpness should return higher values for high variance`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        
        // Draw a high-contrast pattern (alternating black and white)
        for (i in 0 until 100 step 2) {
            paint.color = Color.BLACK
            canvas.drawRect(i.toFloat(), 0f, (i + 1).toFloat(), 100f, paint)
            paint.color = Color.WHITE
            canvas.drawRect((i + 1).toFloat(), 0f, (i + 2).toFloat(), 100f, paint)
        }

        val rect = RectF(0f, 0f, 100f, 100f)
        val method = pipeline.javaClass.getDeclaredMethod("calculateFocusSharpness", Bitmap::class.java, RectF::class.java)
        method.isAccessible = true
        val sharpness = method.invoke(pipeline, bitmap, rect) as Float

        assertTrue("Sharpness should be high for pattern, was $sharpness", sharpness > 1000f)

        // Clear and draw a uniform color
        bitmap.eraseColor(Color.GRAY)
        val blurredSharpness = method.invoke(pipeline, bitmap, rect) as Float
        assertTrue("Sharpness should be low for uniform color, was $blurredSharpness", blurredSharpness < 1f)
    }
}
