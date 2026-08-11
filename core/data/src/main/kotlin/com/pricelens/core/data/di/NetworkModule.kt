package com.pricelens.core.data.di

import com.pricelens.core.common.serialization.BigDecimalSerializer
import com.pricelens.core.common.serialization.UUIDSerializer
import com.pricelens.core.data.remote.api.CaptureApi
import com.pricelens.core.data.remote.api.PriceApi
import com.pricelens.core.data.remote.api.SyncApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        serializersModule = SerializersModule {
            contextual(BigDecimal::class, BigDecimalSerializer)
            contextual(UUID::class, UUIDSerializer)
        }
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Provides
    @Singleton
    fun providePriceApi(): PriceApi = PriceApi()

    @Provides
    @Singleton
    fun provideCaptureApi(): CaptureApi = CaptureApi()

    @Provides
    @Singleton
    fun provideSyncApi(): SyncApi = SyncApi()
}
