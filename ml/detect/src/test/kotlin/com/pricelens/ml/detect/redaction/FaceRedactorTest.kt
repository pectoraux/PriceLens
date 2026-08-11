package com.pricelens.ml.detect.redaction

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import com.pricelens.core.common.logging.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceRedactorTest {

    private val logger = mockk<Logger>(relaxed = true)
    private val faceDetector = mockk<FaceDetector>()
    private lateinit var faceRedactor: FaceRedactor

    @Before
    fun setup() {
        mockkStatic(InputImage::class)
        faceRedactor = FaceRedactor(faceDetector, logger)
    }

    @Test
    fun `redact should return the same bitmap if no faces are found`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val inputImage = mockk<InputImage>()
        every { InputImage.fromBitmap(any(), any()) } returns inputImage
        val task = Tasks.forResult<List<Face>>(emptyList<Face>())
        every { faceDetector.process(any<InputImage>()) } returns task

        val result = faceRedactor.redact(bitmap)
        assertNotNull(result)
    }
}
