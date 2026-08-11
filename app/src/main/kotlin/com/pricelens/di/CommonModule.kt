package com.pricelens.di

import android.content.Context
import android.hardware.SensorManager
import com.pricelens.core.common.dispatchers.DefaultDispatcherProvider
import com.pricelens.core.common.dispatchers.DispatcherProvider
import com.pricelens.core.common.logging.DefaultLogger
import com.pricelens.core.common.logging.Logger
import com.pricelens.logging.AndroidLogDelegate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider {
        return DefaultDispatcherProvider()
    }

    @Provides
    @Singleton
    fun provideApplicationScope(dispatcherProvider: DispatcherProvider): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcherProvider.default)
    }

    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return DefaultLogger(AndroidLogDelegate())
    }

    @Provides
    @Singleton
    fun provideSensorManager(@ApplicationContext context: Context): SensorManager {
        return context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
}
