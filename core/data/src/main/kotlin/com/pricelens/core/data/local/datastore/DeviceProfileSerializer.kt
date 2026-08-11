package com.pricelens.core.data.local.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.pricelens.core.data.model.DeviceProfileProto
import java.io.InputStream
import java.io.OutputStream

object DeviceProfileSerializer : Serializer<DeviceProfileProto> {
    override val defaultValue: DeviceProfileProto = DeviceProfileProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): DeviceProfileProto {
        try {
            return DeviceProfileProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: DeviceProfileProto, output: OutputStream) = t.writeTo(output)
}
