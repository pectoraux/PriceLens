package com.pricelens.ml.pipeline.fake

import android.graphics.Bitmap
import android.graphics.RectF
import com.pricelens.core.data.model.OpticalProfileProto
import com.pricelens.core.data.model.PhotometricProfileProto
import com.pricelens.ml.pipeline.contract.*
import com.pricelens.ml.pipeline.model.CanonicalFrame
import com.pricelens.ml.pipeline.model.Detection

class FakeQualityGate : QualityGate {
    override suspend fun assess(
        bitmap: Bitmap,
        imuWindow: List<Triple<Float, Float, Float>>?
    ): QualityGate.QualityResult =
        QualityGate.QualityResult(true, 100f, 0.5f, 1.0f, emptyList())
}

class FakeFrameNormalizer : FrameNormalizer {
    override suspend fun normalize(
        bitmap: Bitmap,
        opticalProfile: OpticalProfileProto,
        photometricProfile: PhotometricProfileProto?,
        zoom: Float
    ): CanonicalFrame = CanonicalFrame(bitmap, 0L)
}

class FakeDetector : Detector {
    override suspend fun detect(frame: CanonicalFrame): List<Detection> = listOf(
        Detection(RectF(100f, 100f, 348f, 348f), 0.95f, "fake_item")
    )
}

class FakeEmbedder : Embedder {
    override suspend fun embed(frame: CanonicalFrame, detection: Detection): FloatArray = FloatArray(512)
}

class FakeRetriever : Retriever {
    override suspend fun retrieve(embedding: FloatArray): List<Retriever.Candidate> = listOf(
        Retriever.Candidate("tomato", 0.92f),
        Retriever.Candidate("bread", 0.88f),
        Retriever.Candidate("apple", 0.15f)
    )
}

class FakeTextMatcher : TextMatcher {
    override suspend fun match(frame: CanonicalFrame, detection: Detection): Map<String, Float> = emptyMap()
}

class FakeOcrReader : OcrReader {
    override suspend fun read(frame: CanonicalFrame, detection: Detection): OcrReader.OcrResult =
        OcrReader.OcrResult(null, null, null)
}

class FakePortionEstimator : PortionEstimator {
    override suspend fun estimate(
        frame: CanonicalFrame,
        detection: Detection,
        distanceMm: Float
    ): PortionEstimator.PortionResult = PortionEstimator.PortionResult(250f, 0.9f)
}

class FakeFuser : Fuser {
    override suspend fun fuse(
        visualCandidates: List<Retriever.Candidate>,
        textMatches: Map<String, Float>,
        priors: Map<String, Float>
    ): List<Fuser.Prediction> = visualCandidates.map { 
        Fuser.Prediction(it.label, it.score)
    }
}
