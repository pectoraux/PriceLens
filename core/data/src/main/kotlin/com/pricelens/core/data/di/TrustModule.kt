package com.pricelens.core.data.di

import com.pricelens.core.common.trust.SignatureVerifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TrustModule {

    @Provides
    @Singleton
    fun provideSignatureVerifier(): SignatureVerifier = SignatureVerifier()
}
