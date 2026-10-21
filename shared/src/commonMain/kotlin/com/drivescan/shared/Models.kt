package com.drivescan.shared

/**
 * A scanned document. Local-first: page images and the PDF live on-device;
 * [driveId] is set once the doc has been uploaded to Google Drive.
 */
data class ScanDocument(
    val id: String,
    val name: String,
    val folderId: String?,
    val pageCount: Int,
    val createdAtEpochMs: Long,
    val inVault: Boolean = false,
    val deletedAtEpochMs: Long? = null,
    val modifiedAtEpochMs: Long = createdAtEpochMs,
    val vaultTemplateId: String? = null,
    val driveId: String? = null,
    /** File names (relative to the doc's own folder) of page images, in order. */
    val pageFiles: List<String> = emptyList(),
    /** File name of the generated PDF inside the doc's folder, if any. */
    val pdfFile: String? = null,
    /** On-device OCR text of all pages ("" until OCR has run). Searchable. */
    val ocrText: String = ""
) {
    val inBin: Boolean get() = deletedAtEpochMs != null
}

/** A folder inside DriveScan_Workspace (root has parentId == null). */
data class FolderItem(
    val id: String,
    val name: String,
    val parentId: String?
)

/** A pre-named slot in the Personal Vault (Aadhaar, PAN, marksheets, ...). */
data class VaultTemplate(
    val id: String,
    val displayName: String,
    val category: String
)

/** One entry of a Drive files.list response. */
data class DriveFileMeta(
    val id: String,
    val name: String,
    val mimeType: String?,
    val modifiedTime: String?
)
