package com.drivescan.kmp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import com.drivescan.shared.DriveApi
import com.drivescan.shared.FolderItem
import com.drivescan.shared.ScanDocument
import java.io.File
import java.io.FileOutputStream

/** One OCR text block with its bounding box in page-bitmap pixel coordinates. */
data class OcrTextBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val lines: List<String> = emptyList()
)

/**
 * Local-first persistent store.
 *
 * Layout on disk:
 *   filesDir/index.json            — metadata for folders + documents
 *   filesDir/scans/<docId>/pageN.jpg — page images
 *   filesDir/scans/<docId>/doc.pdf   — generated PDF (if any)
 *
 * All mutating methods persist the index immediately. File writes that can
 * be slow (PDF rendering) are exposed separately so callers can run them
 * off the main thread.
 */
class PersistentStore private constructor(context: Context) {

    private val filesDir: File = context.filesDir
    private val scansDir: File = File(filesDir, "scans").apply { mkdirs() }
    private val indexFile: File = File(filesDir, "index.json")

    private val folderList = mutableListOf<FolderItem>()
    private val docList = mutableListOf<ScanDocument>()
    private var idCounter = System.currentTimeMillis() % 100000

    init {
        load()
    }

    @Synchronized
    fun folders(): List<FolderItem> = folderList.toList()

    @Synchronized
    fun folderById(id: String?): FolderItem? = folderList.firstOrNull { it.id == id }

    @Synchronized
    fun documents(includeBin: Boolean = false): List<ScanDocument> =
        docList.filter { includeBin || !it.inBin }.sortedByDescending { it.modifiedAtEpochMs }

    @Synchronized
    fun binDocuments(): List<ScanDocument> =
        docList.filter { it.inBin }.sortedBy { it.deletedAtEpochMs ?: 0L }

    @Synchronized
    fun vaultDocuments(): List<ScanDocument> =
        docList.filter { it.inVault && !it.inBin }.sortedByDescending { it.modifiedAtEpochMs }

    @Synchronized
    fun documentById(id: String): ScanDocument? = docList.firstOrNull { it.id == id }

    @Synchronized
    fun docForTemplate(templateId: String): ScanDocument? =
        docList.firstOrNull { it.vaultTemplateId == templateId && !it.inBin }

    fun docDir(docId: String): File = File(scansDir, docId).apply { mkdirs() }

    fun pageFile(doc: ScanDocument, index: Int): File? {
        val name = doc.pageFiles.getOrNull(index) ?: return null
        return File(docDir(doc.id), name)
    }

    fun pdfFile(doc: ScanDocument): File? {
        val name = doc.pdfFile ?: return null
        return File(docDir(doc.id), name)
    }

    @Synchronized
    fun addFolder(name: String, parentId: String? = null): FolderItem {
        val folder = FolderItem(newId("folder"), name, parentId)
        folderList.add(folder)
        save()
        return folder
    }

    /**
     * Imports processed page image files into a new document.
     * Pages are copied in; the caller keeps ownership of the source files.
     */
    @Synchronized
    fun importDocument(
        name: String,
        folderId: String?,
        pageSources: List<File>,
        createPdf: Boolean,
        vaultTemplateId: String?,
        nowMs: Long
    ): ScanDocument {
        val id = newId("doc")
        val dir = docDir(id)
        val pageNames = mutableListOf<String>()
        pageSources.forEachIndexed { idx, src ->
            val dest = File(dir, "page${idx + 1}.jpg")
            src.copyTo(dest, overwrite = true)
            pageNames.add(dest.name)
        }
        var pdfName: String? = null
        if (createPdf && pageNames.isNotEmpty()) {
            val pdf = File(dir, "doc.pdf")
            if (renderPdf(pageNames.map { File(dir, it) }, pdf)) pdfName = pdf.name
        }
        val doc = ScanDocument(
            id = id,
            name = name,
            folderId = folderId,
            pageCount = pageNames.size,
            createdAtEpochMs = nowMs,
            modifiedAtEpochMs = nowMs,
            inVault = vaultTemplateId != null,
            vaultTemplateId = vaultTemplateId,
            pageFiles = pageNames,
            pdfFile = pdfName
        )
        docList.add(doc)
        save()
        return doc
    }

    /** Imports a standalone PDF file as a new document (Dashboard upload). */
    @Synchronized
    fun importPdfDocument(
        name: String,
        folderId: String?,
        sourcePdf: File,
        vaultTemplateId: String?,
        nowMs: Long
    ): ScanDocument {
        val id = newId("doc")
        val dir = docDir(id)
        val dest = File(dir, "doc.pdf")
        sourcePdf.copyTo(dest, overwrite = true)
        val doc = ScanDocument(
            id = id,
            name = name,
            folderId = folderId,
            pageCount = 1,
            createdAtEpochMs = nowMs,
            modifiedAtEpochMs = nowMs,
            inVault = vaultTemplateId != null,
            vaultTemplateId = vaultTemplateId,
            pageFiles = emptyList(),
            pdfFile = dest.name
        )
        docList.add(doc)
        save()
        return doc
    }

    /** Stores OCR text for a document (capped; searched from the Dashboard). */
    @Synchronized
    fun setOcrText(docId: String, text: String) {
        val idx = docList.indexOfFirst { it.id == docId }
        if (idx >= 0) {
            docList[idx] = docList[idx].copy(ocrText = text.take(OCR_TEXT_CAP))
            save()
        }
    }

    /** Marks an externally rendered PDF file as the document's PDF. */
    @Synchronized
    fun attachPdf(docId: String, pdf: File) {
        val doc = documentById(docId) ?: return
        val dest = File(docDir(docId), "doc.pdf")
        if (pdf.absolutePath != dest.absolutePath) pdf.copyTo(dest, overwrite = true)
        replace(doc.copy(pdfFile = dest.name))
        save()
    }

    /**
     * Replaces a document's page list (viewer reorder / single-page delete):
     * updates pageFiles + pageCount, removes page image files that are no
     * longer referenced, and regenerates the PDF from the new order.
     * Callers rename/keep files so the names in [orderedNames] still exist.
     */
    @Synchronized
    fun replacePages(docId: String, orderedNames: List<String>) {
        val doc = documentById(docId) ?: return
        if (orderedNames.isEmpty()) return
        val dir = docDir(docId)
        val removed = doc.pageFiles.filter { it !in orderedNames }
        removed.forEach { File(dir, it).delete() }
        var pdfName = doc.pdfFile
        if (pdfName != null) {
            val out = File(dir, pdfName)
            pdfName = if (renderPdf(orderedNames.map { File(dir, it) }, out)) pdfName else null
        }
        replace(
            doc.copy(
                pageFiles = orderedNames,
                pageCount = orderedNames.size,
                pdfFile = pdfName,
                modifiedAtEpochMs = System.currentTimeMillis()
            )
        )
        save()
    }

    /** Creates a metadata-only document for a file pulled from Drive. */
    @Synchronized
    fun addRemoteDocument(
        name: String,
        folderId: String?,
        driveId: String,
        inVault: Boolean,
        nowMs: Long
    ): ScanDocument {
        val doc = ScanDocument(
            id = newId("doc"),
            name = name,
            folderId = folderId,
            pageCount = 0,
            createdAtEpochMs = nowMs,
            modifiedAtEpochMs = nowMs,
            inVault = inVault,
            driveId = driveId
        )
        docList.add(doc)
        save()
        return doc
    }

    /** Attaches downloaded content (PDF or a single page image) to a remote doc. */
    @Synchronized
    fun attachDownloadedFile(docId: String, file: File, isPdf: Boolean) {
        val doc = documentById(docId) ?: return
        val dir = docDir(docId)
        val updated = if (isPdf) {
            val dest = File(dir, "doc.pdf")
            file.copyTo(dest, overwrite = true)
            doc.copy(pdfFile = dest.name, pageCount = if (doc.pageCount == 0) 1 else doc.pageCount)
        } else {
            val dest = File(dir, "page1.jpg")
            file.copyTo(dest, overwrite = true)
            doc.copy(pageFiles = listOf(dest.name), pageCount = 1)
        }
        replace(updated)
        save()
    }

    @Synchronized
    fun rename(docId: String, newName: String) =
        update(docId) { it.copy(name = newName, modifiedAtEpochMs = System.currentTimeMillis()) }

    @Synchronized
    fun moveToFolder(docId: String, folderId: String?) =
        update(docId) { it.copy(folderId = folderId, modifiedAtEpochMs = System.currentTimeMillis()) }

    @Synchronized
    fun setVault(docId: String, templateId: String?) =
        update(docId) {
            it.copy(
                inVault = templateId != null || it.inVault,
                vaultTemplateId = templateId,
                modifiedAtEpochMs = System.currentTimeMillis()
            )
        }

    /** Detaches a doc from its template slot (the doc itself stays in the vault). */
    @Synchronized
    fun clearTemplate(docId: String) =
        update(docId) { it.copy(vaultTemplateId = null, modifiedAtEpochMs = System.currentTimeMillis()) }

    @Synchronized
    fun removeFromVault(docId: String) =
        update(docId) {
            it.copy(inVault = false, vaultTemplateId = null, modifiedAtEpochMs = System.currentTimeMillis())
        }

    @Synchronized
    fun moveToBin(docId: String) =
        update(docId) { it.copy(deletedAtEpochMs = System.currentTimeMillis()) }

    @Synchronized
    fun restoreFromBin(docId: String) =
        update(docId) { it.copy(deletedAtEpochMs = null, modifiedAtEpochMs = System.currentTimeMillis()) }

    @Synchronized
    fun setDriveId(docId: String, driveId: String) =
        update(docId) { it.copy(driveId = driveId) }

    @Synchronized
    fun deleteForever(docId: String) {
        docList.removeAll { it.id == docId }
        docDir(docId).deleteRecursively()
        save()
    }

    /** Permanently removes bin items older than [retentionMs]. Returns how many were purged. */
    @Synchronized
    fun purgeExpiredBin(nowMs: Long, retentionMs: Long): Int {
        val expired = docList.filter {
            it.inBin && (nowMs - (it.deletedAtEpochMs ?: nowMs)) > retentionMs
        }
        expired.forEach { docDir(it.id).deleteRecursively() }
        docList.removeAll(expired.toSet())
        if (expired.isNotEmpty()) save()
        return expired.size
    }

    fun totalStorageBytes(): Long {
        fun dirSize(f: File): Long =
            if (f.isDirectory) f.listFiles()?.sumOf { dirSize(it) } ?: 0L else f.length()
        return dirSize(scansDir)
    }

    // ---- index persistence -------------------------------------------------

    @Synchronized
    private fun load() {
        if (!indexFile.exists()) return
        try {
            val parsed = JsonParser(indexFile.readText()).parse() as? Map<String, Any?> ?: return
            folderList.clear()
            docList.clear()
            parsed.objList("folders").forEach { m ->
                val id = m.str("id") ?: return@forEach
                folderList.add(FolderItem(id, m.str("name") ?: "Folder", m.str("parentId")))
            }
            parsed.objList("documents").forEach { m ->
                val id = m.str("id") ?: return@forEach
                val deleted = m["deletedAt"] as? Number
                docList.add(
                    ScanDocument(
                        id = id,
                        name = m.str("name") ?: "Scan",
                        folderId = m.str("folderId"),
                        pageCount = m.long("pageCount").toInt(),
                        createdAtEpochMs = m.long("createdAt"),
                        modifiedAtEpochMs = m.long("modifiedAt").let { if (it == 0L) m.long("createdAt") else it },
                        inVault = m.bool("inVault"),
                        deletedAtEpochMs = deleted?.toLong(),
                        vaultTemplateId = m.str("vaultTemplateId"),
                        driveId = m.str("driveId"),
                        pageFiles = m.strList("pageFiles"),
                        pdfFile = m.str("pdfFile"),
                        ocrText = m.str("ocrText") ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            // Corrupt index: start clean rather than crash the app.
            folderList.clear()
            docList.clear()
        }
    }

    @Synchronized
    private fun save() {
        val sb = StringBuilder()
        sb.append("{\"folders\":[")
        folderList.forEachIndexed { i, f ->
            if (i > 0) sb.append(',')
            sb.append("{\"id\":\"${esc(f.id)}\",\"name\":\"${esc(f.name)}\"")
            f.parentId?.let { sb.append(",\"parentId\":\"${esc(it)}\"") }
            sb.append('}')
        }
        sb.append("],\"documents\":[")
        docList.forEachIndexed { i, d ->
            if (i > 0) sb.append(',')
            sb.append("{\"id\":\"${esc(d.id)}\",\"name\":\"${esc(d.name)}\"")
            d.folderId?.let { sb.append(",\"folderId\":\"${esc(it)}\"") }
            sb.append(",\"pageCount\":${d.pageCount}")
            sb.append(",\"createdAt\":${d.createdAtEpochMs}")
            sb.append(",\"modifiedAt\":${d.modifiedAtEpochMs}")
            sb.append(",\"inVault\":${d.inVault}")
            d.deletedAtEpochMs?.let { sb.append(",\"deletedAt\":$it") }
            d.vaultTemplateId?.let { sb.append(",\"vaultTemplateId\":\"${esc(it)}\"") }
            d.driveId?.let { sb.append(",\"driveId\":\"${esc(it)}\"") }
            sb.append(",\"pageFiles\":[")
            d.pageFiles.forEachIndexed { j, p ->
                if (j > 0) sb.append(',')
                sb.append("\"${esc(p)}\"")
            }
            sb.append(']')
            d.pdfFile?.let { sb.append(",\"pdfFile\":\"${esc(it)}\"") }
            if (d.ocrText.isNotEmpty()) {
                sb.append(",\"ocrText\":\"${esc(d.ocrText.take(OCR_TEXT_CAP))}\"")
            }
            sb.append('}')
        }
        sb.append("]}")
        val tmp = File(filesDir, "index.json.tmp")
        tmp.writeText(sb.toString())
        tmp.renameTo(indexFile)
    }

    private fun esc(s: String): String = DriveApi.escapeJson(s)

    private fun newId(prefix: String): String {
        idCounter += 1
        return "${prefix}_${System.currentTimeMillis()}_${idCounter}"
    }

    private fun update(docId: String, transform: (ScanDocument) -> ScanDocument) {
        val idx = docList.indexOfFirst { it.id == docId }
        if (idx >= 0) {
            docList[idx] = transform(docList[idx])
            save()
        }
    }

    private fun replace(doc: ScanDocument) {
        val idx = docList.indexOfFirst { it.id == doc.id }
        if (idx >= 0) docList[idx] = doc
    }

    companion object {
        /** Upper bound for stored OCR text per document (index stays lean). */
        const val OCR_TEXT_CAP = 20_000

        @Volatile
        private var instance: PersistentStore? = null

        fun get(context: Context): PersistentStore =
            instance ?: synchronized(this) {
                instance ?: PersistentStore(context.applicationContext).also { instance = it }
            }

        /** Renders page images into a simple PDF (one image per page). */
        fun renderPdf(pages: List<File>, out: File): Boolean {
            val pdf = PdfDocument()
            return try {
                pages.forEachIndexed { idx, pageFile ->
                    val bmp = BitmapFactory.decodeFile(pageFile.absolutePath) ?: return@forEachIndexed
                    val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, idx + 1).create()
                    val page = pdf.startPage(pageInfo)
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    pdf.finishPage(page)
                    bmp.recycle()
                }
                FileOutputStream(out).use { pdf.writeTo(it) }
                true
            } catch (e: Exception) {
                false
            } finally {
                pdf.close()
            }
        }

        /**
         * Like [renderPdf], but also draws each page's OCR text invisibly
         * (fully transparent paint) at the recognized block positions, so the
         * exported PDF carries a searchable text layer aligned with the image.
         * Coordinates in [blocksPerPage] are in page-bitmap pixels, matching
         * the PDF page size (one point per pixel, as in [renderPdf]).
         */
        fun renderPdfWithText(pages: List<File>, blocksPerPage: List<List<OcrTextBlock>>, out: File): Boolean {
            val pdf = PdfDocument()
            return try {
                pages.forEachIndexed { idx, pageFile ->
                    val bmp = BitmapFactory.decodeFile(pageFile.absolutePath) ?: return@forEachIndexed
                    val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, idx + 1).create()
                    val page = pdf.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawBitmap(bmp, 0f, 0f, null)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.TRANSPARENT
                        isAntiAlias = false
                    }
                    for (block in blocksPerPage.getOrNull(idx).orEmpty()) {
                        val lines = block.lines.ifEmpty { listOf(block.text) }
                        val boxH = (block.bottom - block.top).coerceAtLeast(1)
                        val lineH = boxH.toFloat() / lines.size
                        paint.textSize = lineH * 0.9f
                        lines.forEachIndexed { li, lineText ->
                            if (lineText.isNotBlank()) {
                                // PdfDocument text origin is the baseline.
                                val baseline = block.top + lineH * (li + 1) - lineH * 0.15f
                                canvas.drawText(lineText, block.left.toFloat(), baseline, paint)
                            }
                        }
                    }
                    pdf.finishPage(page)
                    bmp.recycle()
                }
                FileOutputStream(out).use { pdf.writeTo(it) }
                true
            } catch (e: Exception) {
                false
            } finally {
                pdf.close()
            }
        }

        /** Decodes a sampled thumbnail (never the full-resolution image). */
        fun decodeThumbnail(file: File, targetPx: Int = 240): Bitmap? {
            if (!file.exists()) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0) return null
            var sample = 1
            while (opts.outWidth / sample > targetPx * 2 || opts.outHeight / sample > targetPx * 2) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        }
    }
}
