package com.drivescan.shared

/**
 * Storage abstraction — the native mirror of the web app's data plan.
 * Phase A ships this in-memory implementation; Phase C backs it with
 * the Google Drive REST API (see DriveApi) behind the same interface.
 */
interface ScanStore {
    fun folders(): List<FolderItem>
    fun documents(includeBin: Boolean = false): List<ScanDocument>
    fun vaultDocuments(): List<ScanDocument> = documents().filter { it.inVault }
    fun addFolder(name: String, parentId: String? = null): FolderItem
    fun addDocument(doc: ScanDocument)
    fun moveToBin(id: String, nowEpochMs: Long)
    fun restoreFromBin(id: String)
    fun deleteForever(id: String)
}

/** In-memory store with seed data that mirrors a fresh DriveScan workspace. */
class InMemoryScanStore(seedDemoData: Boolean = true) : ScanStore {
    private val folderList = mutableListOf<FolderItem>()
    private val docList = mutableListOf<ScanDocument>()
    private var nextId = 1

    init {
        if (seedDemoData) seed()
    }

    private fun newId(prefix: String): String = "$prefix-${nextId++}"

    private fun seed() {
        val personal = addFolder("Personal")
        addFolder("Receipts")
        addFolder("Office")
        addDocument(
            ScanDocument(
                id = newId("doc"), name = "Sample Scan.pdf",
                folderId = personal.id, pageCount = 1, createdAtEpochMs = 0L
            )
        )
        addDocument(
            ScanDocument(
                id = newId("doc"), name = "Aadhaar Card.pdf",
                folderId = personal.id, pageCount = 2, createdAtEpochMs = 0L,
                inVault = true
            )
        )
    }

    override fun folders(): List<FolderItem> = folderList.toList()

    override fun documents(includeBin: Boolean): List<ScanDocument> =
        if (includeBin) docList.toList() else docList.filter { !it.inBin }

    override fun addFolder(name: String, parentId: String?): FolderItem {
        val folder = FolderItem(id = newId("folder"), name = name, parentId = parentId)
        folderList.add(folder)
        return folder
    }

    override fun addDocument(doc: ScanDocument) {
        docList.add(doc)
    }

    override fun moveToBin(id: String, nowEpochMs: Long) {
        replace(id) { it.copy(deletedAtEpochMs = nowEpochMs) }
    }

    override fun restoreFromBin(id: String) {
        replace(id) { it.copy(deletedAtEpochMs = null) }
    }

    override fun deleteForever(id: String) {
        docList.removeAll { it.id == id }
    }

    private fun replace(id: String, transform: (ScanDocument) -> ScanDocument) {
        val idx = docList.indexOfFirst { it.id == id }
        if (idx >= 0) docList[idx] = transform(docList[idx])
    }
}
