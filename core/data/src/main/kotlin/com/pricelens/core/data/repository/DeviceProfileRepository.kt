package com.pricelens.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.pricelens.core.data.local.datastore.DeviceProfileSerializer
import com.pricelens.core.data.model.DeviceProfileProto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deviceProfileStore: DataStore<DeviceProfileProto> by dataStore(
    fileName = "device_profile.pb",
    serializer = DeviceProfileSerializer
)

@Singleton
class DeviceProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val deviceProfile: Flow<DeviceProfileProto> = context.deviceProfileStore.data

    suspend fun updateProfile(profile: DeviceProfileProto) {
        context.deviceProfileStore.updateData { profile }
    }
}
