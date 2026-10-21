package com.drivescan.kmp.ocr

import android.content.Context
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.scan.ImageOps
import java.io.File

/**
 * Runs [OcrEngine] over every page image of a saved document, stores the
 * recognized text in the index (making it searchable from the Dashboard)
 * and — when [wantPdf] is set — regenerates the document's PDF with an
 * invisible OCR text layer. Best-effort: any failure leaves the document
 * exactly as it was. Background thread only.
 */
object OcrHelper {

    fun recognizeAndAttach(context: Context, store: PersistentStore, docId: String, wantPdf: Boolean) {
        try {
            val doc = store.documentById(docId) ?: return
            if (doc.pageFiles.isEmpty()) return
            val pageFiles = doc.pageFiles.mapNotNull { name ->
                File(store.docDir(doc.id), name).takeIf { it.exists() }
            }
            if (pageFiles.isEmpty()) return
            val texts = mutableListOf<String>()
            val blocksPerPage = mutableListOf<List<com.drivescan.kmp.data.OcrTextBlock>>()
            for (page in pageFiles) {
                val bmp = ImageOps.loadBitmap(page, 2000)
                if (bmp == null) {
                    texts.add("")
                    blocksPerPage.add(emptyList())
                    continue
                }
                try {
                    val result = OcrEngine.recognize(bmp)
                    texts.add(result.text)
                    blocksPerPage.add(result.blocks)
                } catch (e: Exception) {
                    texts.add("")
                    blocksPerPage.add(emptyList())
                }
            }
            val combined = texts.joinToString("\n").trim()
            if (combined.isNotEmpty()) {
                store.setOcrText(docId, combined)
            }
            val hasBlocks = blocksPerPage.any { blocks -> blocks.any { it.text.isNotBlank() } }
            if (wantPdf && hasBlocks) {
                val tmp = File(context.cacheDir, "ocr_${docId}.pdf")
                if (PersistentStore.renderPdfWithText(pageFiles, blocksPerPage, tmp)) {
                    store.attachPdf(docId, tmp)
                }
            }
        } catch (e: Exception) {
            // OCR is an enhancement; never let it break the save/import flow.
        }
    }
}
