package com.pricelens.ml.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pricelens.ml.pipeline.contract.OcrReader
import com.pricelens.ml.pipeline.model.CanonicalFrame
import com.pricelens.ml.pipeline.model.Detection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitOcrReader @Inject constructor() : OcrReader {

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128
                )
                .build()
        )
    }

    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun read(frame: CanonicalFrame, detection: Detection): OcrReader.OcrResult = coroutineScope {
        // 1. Crop to dominant region
        val crop = cropBitmap(frame.bitmap, detection.boundingBox)
        val inputImage = InputImage.fromBitmap(crop, 0)

        // 2. Parallel processing
        val barcodeJob = async {
            try {
                barcodeScanner.process(inputImage).await()
                    .firstOrNull()?.rawValue
            } catch (e: Exception) {
                null
            }
        }

        val textJob = async {
            try {
                textRecognizer.process(inputImage).await().text
            } catch (e: Exception) {
                null
            }
        }

        val barcode = barcodeJob.await()
        val rawText = textJob.await()

        val massResult = rawText?.let { parseMass(it) }
        val priceResult = rawText?.let { parsePrice(it) }

        OcrReader.OcrResult(
            barcode = barcode,
            text = rawText,
            massGrams = massResult,
            priceMinor = priceResult?.first,
            currencyCode = priceResult?.second
        )
    }

    private val weightRegex = Regex("""(\d+)\s*(g|kg|ml|l)""", RegexOption.IGNORE_CASE)
    private val priceRegex = Regex("""(KES|USD|\$)\s*(\d+([.,]\d{2})?)""", RegexOption.IGNORE_CASE)

    private fun parseMass(text: String): Float? {
        val match = weightRegex.find(text) ?: return null
        val value = match.groupValues[1].toFloatOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        
        return when (unit) {
            "g" -> value
            "kg" -> value * 1000f
            "ml" -> value // Assume density ~1 for liquids
            "l" -> value * 1000f
            else -> value
        }
    }

    private fun parsePrice(text: String): Pair<Long, String>? {
        val match = priceRegex.find(text) ?: return null
        val currency = when (val c = match.groupValues[1].uppercase()) {
            "$" -> "USD"
            else -> c
        }
        val valueStr = match.groupValues[2].replace(",", ".")
        val value = valueStr.toDoubleOrNull() ?: return null
        
        return (value * 100).toLong() to currency
    }

    private fun cropBitmap(bitmap: Bitmap, rect: android.graphics.RectF): Bitmap {
        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val width = rect.width().toInt().coerceAtMost(bitmap.width - left)
        val height = rect.height().toInt().coerceAtMost(bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }
}
