package com.pricelens.ml.ocr.di

import com.pricelens.ml.ocr.MlKitOcrReader
import com.pricelens.ml.pipeline.contract.OcrReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    @Singleton
    abstract fun bindOcrReader(mlKitOcrReader: MlKitOcrReader): OcrReader
}
