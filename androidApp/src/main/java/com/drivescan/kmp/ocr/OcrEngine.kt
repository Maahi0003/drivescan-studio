package com.drivescan.kmp.ocr

import android.graphics.Bitmap
import com.drivescan.kmp.data.OcrTextBlock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/** OCR output for one page: the full text plus positioned blocks for the PDF text layer. */
data class PageOcr(val text: String, val blocks: List<OcrTextBlock>)

/**
 * On-device OCR with ML Kit Text Recognition v2 (bundled models — the
 * recognizers and their .tflite models ship inside the APK, so this works
 * fully offline and costs nothing). Latin runs first; if the page contains
 * any Devanagari characters, the Devanagari recognizer's result wins (it
 * handles mixed Devanagari + Latin pages better).
 *
 * All calls block — invoke from a background thread only.
 */
object OcrEngine {

    private const val TIMEOUT_SECONDS = 45L

    @Volatile
    private var latinClient: com.google.mlkit.vision.text.TextRecognizer? = null

    @Volatile
    private var devanagariClient: com.google.mlkit.vision.text.TextRecognizer? = null

    private fun latin() = latinClient ?: synchronized(this) {
        latinClient ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .also { latinClient = it }
    }

    private fun devanagari() = devanagariClient ?: synchronized(this) {
        devanagariClient ?: TextRecognition.getClient(
            DevanagariTextRecognizerOptions.Builder().build()
        ).also { devanagariClient = it }
    }

    /** Recognizes the text of one page bitmap. Throws on recognizer failure. */
    fun recognize(bitmap: Bitmap): PageOcr {
        val latinResult = runRecognizer(latin(), bitmap)
        if (!containsDevanagari(latinResult.text)) return latinResult
        return try {
            val devaResult = runRecognizer(devanagari(), bitmap)
            if (devaResult.text.isNotBlank()) devaResult else latinResult
        } catch (e: Exception) {
            latinResult
        }
    }

    private fun runRecognizer(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap
    ): PageOcr {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(image), TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val blocks = visionText.textBlocks.map { block ->
            val box = block.boundingBox
            OcrTextBlock(
                text = block.text,
                left = box?.left ?: 0,
                top = box?.top ?: 0,
                right = box?.right ?: 0,
                bottom = box?.bottom ?: 0,
                lines = block.lines.map { it.text }
            )
        }
        return PageOcr(visionText.text, blocks)
    }

    private fun containsDevanagari(text: String): Boolean =
        text.any { it in '\u0900'..'\u097F' }
}
