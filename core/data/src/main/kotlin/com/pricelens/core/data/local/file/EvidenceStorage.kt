package com.pricelens.core.data.local.file

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvidenceStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val evidenceDir = File(context.filesDir, "evidence")

    init {
        if (!evidenceDir.exists()) {
            evidenceDir.mkdirs()
        }
    }

    fun saveEvidence(id: String, bitmap: Bitmap): File {
        val file = File(evidenceDir, "$id.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }

    fun getEvidenceFile(id: String): File {
        return File(evidenceDir, "$id.jpg")
    }

    fun deleteEvidence(id: String) {
        val file = File(evidenceDir, "$id.jpg")
        if (file.exists()) {
            file.delete()
        }
    }
}
