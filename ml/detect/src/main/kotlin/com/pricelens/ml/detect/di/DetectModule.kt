package com.pricelens.ml.detect.di

import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.pricelens.core.data.repository.ModelRepository
import com.pricelens.ml.detect.interpreter.LiteRtDetector
import com.pricelens.ml.pipeline.contract.Detector
import com.pricelens.ml.pipeline.fake.FakeDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DetectModule {
    
    @Provides
    @Singleton
    fun provideDetector(
        modelRepository: ModelRepository,
        liteRtDetector: LiteRtDetector
    ): Detector {
        return if (modelRepository.isModelDownloaded("detector")) {
            liteRtDetector
        } else {
            FakeDetector()
        }
    }

    @Provides
    @Singleton
    fun provideFaceDetector(): FaceDetector {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        return FaceDetection.getClient(options)
    }
}
