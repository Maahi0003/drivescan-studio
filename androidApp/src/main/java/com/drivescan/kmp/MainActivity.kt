package com.drivescan.kmp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.provider.OpenableColumns
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.drivescan.kmp.auth.AuthManager
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.drive.DriveSync
import com.drivescan.kmp.drive.SyncManager
import com.drivescan.kmp.ocr.OcrHelper
import com.drivescan.kmp.scan.ImageOps
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.bodyText
import com.drivescan.kmp.ui.bottomNav
import com.drivescan.kmp.ui.dp
import com.drivescan.kmp.ui.ghostButton
import com.drivescan.kmp.ui.inputField
import com.drivescan.kmp.ui.palette
import com.drivescan.kmp.ui.primaryButton
import com.drivescan.kmp.ui.rounded
import com.drivescan.kmp.ui.smallText
import com.drivescan.kmp.ui.titleText
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import com.drivescan.shared.ScanDocument
import com.drivescan.shared.VaultTemplates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Dashboard: folders, documents with thumbnails, search, document actions,
 * Drive status chip and the big Scan entry point. All data is local-first
 * from [PersistentStore]; Drive is only a sync layer on top.
 */
class MainActivity : Activity() {

    private val store by lazy { PersistentStore.get(this) }
    private val palette by lazy { palette() }

    private lateinit var docList: ListView
    private lateinit var emptyView: TextView
    private lateinit var folderBar: LinearLayout
    private lateinit var driveChip: TextView
    private lateinit var searchInput: EditText
    private lateinit var scanButton: android.widget.Button
    private lateinit var syncNowButton: android.widget.Button
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionLabel: TextView
    private lateinit var combineButton: android.widget.Button
    private lateinit var selectionActionsButton: android.widget.Button

    /** Multi-select state (combine flow); insertion order = combine order. */
    private val selection = LinkedHashSet<String>()

    /** null = All, ROOT = folderId==null docs, otherwise a folder id. */
    private var folderFilter: String? = null
    private var searchQuery: String = ""
    private var visibleDocs: List<ScanDocument> = emptyList()
    private val thumbCache = HashMap<String, Bitmap?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        setContentView(buildUi())
        // Smart sync: sync on connectivity return + once at app start (if enabled).
        SyncManager.registerNetworkCallback(this)
        SyncManager.onAppStart(this)
        // 30-day bin retention: purge expired items once per app start.
        thread {
            val purged = store.purgeExpiredBin(System.currentTimeMillis(), BIN_RETENTION_MS)
            if (purged > 0) {
                runOnUiThread {
                    Toast.makeText(this, trf(Str.PURGE_TOAST, purged), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        thumbCache.clear()
        refresh()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(6))
        }
        header.addView(titleText("DriveScan Studio", palette, 24f))
        driveChip = TextView(this).apply {
            textSize = 12.5f
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        header.addView(driveChip)
        syncNowButton = ghostButton(tr(Str.BTN_SYNC_NOW), palette).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            visibility = View.GONE
            setOnClickListener { manualSync() }
        }
        header.addView(syncNowButton)
        root.addView(header)

        searchInput = inputField(tr(Str.SEARCH_HINT), palette).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(20), dp(8), dp(20), dp(4)) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString()?.trim() ?: ""
                    refreshDocs()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(searchInput)

        folderBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        root.addView(HorizontalScrollView(this).apply { addView(folderBar) })

        // Multi-select action bar (combine flow); hidden unless selecting.
        selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(2), dp(20), dp(6))
            visibility = View.GONE
        }
        selectionLabel = bodyText("", palette, 13.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        selectionBar.addView(selectionLabel)
        selectionActionsButton = ghostButton(tr(Str.DOC_ACTIONS_LABEL), palette).apply {
            visibility = View.GONE
            setOnClickListener {
                val only = selection.firstOrNull()?.let { store.documentById(it) }
                if (only != null) showDocActions(only)
            }
        }
        selectionBar.addView(selectionActionsButton)
        combineButton = ghostButton("", palette).apply {
            setOnClickListener { combineSelected() }
        }
        selectionBar.addView(combineButton)
        selectionBar.addView(ghostButton(tr(Str.BTN_CANCEL), palette).apply {
            setOnClickListener {
                selection.clear()
                refreshDocs()
            }
        })
        root.addView(selectionBar)

        docList = ListView(this).apply {
            divider = null
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding(dp(20), dp(4), dp(20), dp(8))
            clipToPadding = false
        }
        root.addView(docList)

        emptyView = smallText("", palette, 14f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
            visibility = View.GONE
        }
        root.addView(emptyView)

        scanButton = primaryButton(tr(Str.SCAN_BUTTON)).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(20), dp(6), dp(20), dp(10)) }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ScannerActivity::class.java))
            }
        }
        root.addView(scanButton)
        root.addView(bottomNav(palette, "home"))

        // Floating "+" button: Scan or Upload chooser.
        val outer = FrameLayout(this).apply { setBackgroundColor(palette.bg) }
        outer.addView(root, FrameLayout.LayoutParams(-1, -1))
        val fab = TextView(this).apply {
            text = "+"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(palette.primary, 30)
            layoutParams = FrameLayout.LayoutParams(dp(58), dp(58), Gravity.END or Gravity.BOTTOM)
                .apply { setMargins(0, 0, dp(20), dp(150)) }
            setOnClickListener { showFabChooser() }
        }
        outer.addView(fab)
        return outer
    }

    private fun refresh() {
        // Strings that were baked in at build time pick up the current language too.
        Lang.init(this)
        searchInput.hint = tr(Str.SEARCH_HINT)
        scanButton.text = tr(Str.SCAN_BUTTON)
        // Drive chip (+ pending-sync / last-sync detail) and the manual Sync now button.
        val connected = AuthManager.isConnected(this)
        driveChip.text = when {
            !connected -> tr(Str.DRIVE_NOT_CONNECTED)
            else -> {
                val pending = DriveSync.pendingCount(this)
                when {
                    pending > 0 -> tr(Str.DRIVE_CONNECTED) + trf(Str.CHIP_PENDING_SYNC, pending)
                    else -> {
                        val last = DriveSync.lastSyncMs(this)
                        if (last > 0L) {
                            tr(Str.DRIVE_CONNECTED) + " • " + trf(
                                Str.CHIP_LAST_SYNCED,
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(last))
                            )
                        } else {
                            tr(Str.DRIVE_CONNECTED)
                        }
                    }
                }
            }
        }
        driveChip.setTextColor(if (connected) Color.rgb(46, 125, 50) else palette.textSecondary)
        driveChip.background = rounded(palette.chipBg, 20)
        syncNowButton.visibility = if (connected) View.VISIBLE else View.GONE
        refreshFolders()
        refreshDocs()
    }

    private fun manualSync() {
        toast(tr(Str.TOAST_SYNCING))
        thread {
            val message = try {
                val s = DriveSync.syncNow(this)
                trf(Str.SYNC_RESULT, s.uploaded, s.updated, s.downloaded, s.trashed)
            } catch (e: Exception) {
                trf(Str.SYNC_FAIL, e.message ?: "")
            }
            runOnUiThread {
                toast(message)
                refresh()
            }
        }
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13.5f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setTextColor(if (selected) Color.WHITE else palette.text)
            background = rounded(if (selected) palette.primary else palette.chipBg, 20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun refreshFolders() {
        folderBar.removeAllViews()
        folderBar.addView(chip(tr(Str.CHIP_ALL), folderFilter == null) { folderFilter = null; refresh() })
        folderBar.addView(chip(tr(Str.CHIP_ROOT), folderFilter == ROOT) { folderFilter = ROOT; refresh() })
        for (folder in store.folders()) {
            folderBar.addView(chip("📁 ${folder.name}", folderFilter == folder.id) {
                folderFilter = folder.id
                refresh()
            })
        }
        folderBar.addView(chip(tr(Str.CHIP_NEW_FOLDER), false) { showNewFolderDialog() })
    }

    private fun refreshDocs() {
        val all = store.documents()
        selection.retainAll(all.map { it.id }.toSet())
        visibleDocs = all.filter { doc ->
            val folderOk = when (folderFilter) {
                null -> true
                ROOT -> doc.folderId == null
                else -> doc.folderId == folderFilter
            }
            // Search matches the name AND the on-device OCR text (v1.1.0).
            val searchOk = searchQuery.isEmpty() ||
                doc.name.contains(searchQuery, ignoreCase = true) ||
                doc.ocrText.contains(searchQuery, ignoreCase = true)
            folderOk && searchOk
        }
        docList.adapter = DocAdapter(visibleDocs)
        updateSelectionBar()
        docList.setOnItemClickListener { _, _, pos, _ ->
            val doc = visibleDocs.getOrNull(pos) ?: return@setOnItemClickListener
            if (selection.isNotEmpty()) {
                toggleSelect(doc.id)
            } else {
                startActivity(Intent(this, DocViewerActivity::class.java).putExtra("docId", doc.id))
            }
        }
        docList.setOnItemLongClickListener { _, _, pos, _ ->
            val doc = visibleDocs.getOrNull(pos) ?: return@setOnItemLongClickListener true
            if (selection.isEmpty()) {
                // First long-press enters multi-select (combine) mode.
                toggleSelect(doc.id)
            } else {
                toggleSelect(doc.id)
            }
            true
        }
        emptyView.visibility = if (visibleDocs.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = when {
            all.isEmpty() -> tr(Str.EMPTY_NO_DOCS)
            searchQuery.isNotEmpty() -> trf(Str.EMPTY_SEARCH, searchQuery)
            else -> tr(Str.EMPTY_FOLDER)
        }
    }

    // ---- multi-select + combine --------------------------------------------

    private fun toggleSelect(docId: String) {
        if (!selection.add(docId)) selection.remove(docId)
        refreshDocs()
    }

    private fun updateSelectionBar() {
        val active = selection.isNotEmpty()
        selectionBar.visibility = if (active) View.VISIBLE else View.GONE
        if (active) {
            selectionLabel.text = trf(Str.SELECTION_COUNT, selection.size)
            combineButton.text = trf(Str.COMBINE_BUTTON, selection.size)
            selectionActionsButton.visibility = if (selection.size == 1) View.VISIBLE else View.GONE
        }
    }

    private fun combineSelected() {
        val ids = selection.toList()
        if (ids.isEmpty()) return
        thread {
            val pages = mutableListOf<java.io.File>()
            for (id in ids) {
                val d = store.documentById(id) ?: continue
                // PDF-only documents (no page images) are skipped — noted simplification.
                for (name in d.pageFiles) {
                    val f = java.io.File(store.docDir(d.id), name)
                    if (f.exists()) pages.add(f)
                }
            }
            if (pages.isEmpty()) {
                runOnUiThread { toast(tr(Str.TOAST_COMBINE_NONE)) }
                return@thread
            }
            val name = "Combined " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
            val doc = store.importDocument(
                name = name,
                folderId = targetFolderId(),
                pageSources = pages,
                createPdf = true,
                vaultTemplateId = null,
                nowMs = System.currentTimeMillis()
            )
            runOnUiThread {
                selection.clear()
                refresh()
                toast(tr(Str.COMBINE_DONE))
            }
            OcrHelper.recognizeAndAttach(this, store, doc.id, wantPdf = true)
            SyncManager.onLocalChange(this)
        }
    }

    /** Folder the dashboard currently targets for new documents (upload/combine). */
    private fun targetFolderId(): String? = when (folderFilter) {
        null, ROOT -> null
        else -> folderFilter
    }

    // ---- FAB: scan / upload ---------------------------------------------------

    private fun showFabChooser() {
        AlertDialog.Builder(this)
            .setItems(arrayOf(tr(Str.FAB_SCAN), tr(Str.FAB_UPLOAD))) { _, which ->
                if (which == 0) {
                    startActivity(Intent(this, ScannerActivity::class.java))
                } else {
                    openUploadPicker()
                }
            }
            .show()
    }

    private fun openUploadPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQ_UPLOAD)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_UPLOAD || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<android.net.Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        }
        data.data?.let { if (uris.isEmpty()) uris.add(it) }
        if (uris.isEmpty()) return
        thread {
            var imported = 0
            for (uri in uris) {
                val doc = importUpload(uri)
                if (doc != null) {
                    imported++
                    OcrHelper.recognizeAndAttach(this, store, doc.id, wantPdf = true)
                }
            }
            val count = imported
            runOnUiThread {
                refresh()
                toast(
                    when {
                        count == 1 -> tr(Str.IMPORT_ONE)
                        count > 1 -> trf(Str.IMPORT_MANY, count)
                        else -> tr(Str.TOAST_IMPORT_FAIL)
                    }
                )
            }
            if (count > 0) SyncManager.onLocalChange(this)
        }
    }

    /** Copies one picked file into scan storage as a new document (background thread). */
    private fun importUpload(uri: android.net.Uri): ScanDocument? {
        return try {
            val displayName = queryDisplayName(uri) ?: "Upload"
            val mime = contentResolver.getType(uri)
            val isPdf = mime == "application/pdf" || displayName.lowercase().endsWith(".pdf")
            val importDir = java.io.File(cacheDir, "imports").apply { mkdirs() }
            val raw = java.io.File(importDir, "up_${System.currentTimeMillis()}_${uri.hashCode()}")
            val copied = contentResolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { input.copyTo(it) }
                true
            } ?: false
            if (!copied || !raw.exists() || raw.length() == 0L) return null
            val now = System.currentTimeMillis()
            if (isPdf) {
                val base = displayName.substringBeforeLast('.').ifBlank { "Upload" }
                store.importPdfDocument(base, targetFolderId(), raw, null, now)
            } else {
                // Normalize the image to a stored JPEG page (also caps huge photos).
                val bmp = ImageOps.loadBitmap(raw, 2400) ?: return null
                val pageFile = java.io.File(importDir, "page_${System.currentTimeMillis()}.jpg")
                if (!ImageOps.saveJpeg(bmp, pageFile)) return null
                val base = displayName.substringBeforeLast('.').ifBlank {
                    "Upload " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(now))
                }
                store.importDocument(base, targetFolderId(), listOf(pageFile), true, null, now)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private inner class DocAdapter(private val docs: List<ScanDocument>) : BaseAdapter() {
        override fun getCount(): Int = docs.size
        override fun getItem(position: Int): Any = docs[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val doc = docs[position]
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(palette.card, 14, palette.cardBorder)
                setPadding(dp(12), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            val thumb = ImageView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(64)).apply { marginEnd = dp(14) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(palette.chipBg, 8)
            }
            val cached = thumbCache[doc.id]
            if (cached != null) {
                thumb.setImageBitmap(cached)
            } else {
                val firstPage = store.pageFile(doc, 0)
                val bmp = firstPage?.let { PersistentStore.decodeThumbnail(it, 160) }
                thumbCache[doc.id] = bmp
                if (bmp != null) thumb.setImageBitmap(bmp) else thumb.setImageResource(android.R.drawable.ic_menu_report_image)
            }
            row.addView(thumb)

            val texts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val selected = doc.id in selection
            if (selected) {
                row.background = rounded(palette.chipBg, 14, palette.primary)
            }
            texts.addView(bodyText(if (selected) "✓ ${doc.name}" else doc.name, palette, 15f))
            val meta = StringBuilder()
            meta.append(dateFormat.format(Date(doc.modifiedAtEpochMs)))
            meta.append("  •  " + trf(Str.META_PAGES, doc.pageCount))
            if (doc.inVault) meta.append("  •  " + tr(Str.META_VAULT_SUFFIX))
            if (doc.driveId != null) meta.append("  •  " + tr(Str.META_DRIVE_SUFFIX))
            store.folderById(doc.folderId)?.let { meta.append("  •  ${it.name}") }
            texts.addView(smallText(meta.toString(), palette))
            row.addView(texts)
            return row
        }
    }

    // ---- document actions ---------------------------------------------------

    private fun showDocActions(doc: ScanDocument) {
        // Indexes: 0 Open, 1 Rename, 2 Move, 3 Share, 4 Vault toggle, 5 Bin.
        val items = mutableListOf(
            tr(Str.ACTION_OPEN), tr(Str.ACTION_RENAME), tr(Str.ACTION_MOVE_FOLDER), tr(Str.ACTION_SHARE)
        )
        items.add(if (doc.inVault) tr(Str.ACTION_REMOVE_VAULT) else tr(Str.ACTION_ADD_VAULT))
        items.add(tr(Str.ACTION_MOVE_TO_BIN))
        AlertDialog.Builder(this)
            .setTitle(doc.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(this, DocViewerActivity::class.java).putExtra("docId", doc.id)
                    )
                    1 -> showRenameDialog(doc)
                    2 -> showMoveDialog(doc)
                    3 -> shareDoc(doc)
                    4 -> if (doc.inVault) {
                        store.removeFromVault(doc.id)
                        toast(tr(Str.TOAST_REMOVED_FROM_VAULT))
                        refresh()
                        SyncManager.onLocalChange(this)
                    } else {
                        showVaultTemplateDialog(doc)
                    }
                    5 -> {
                        store.moveToBin(doc.id)
                        toast(tr(Str.TOAST_MOVED_TO_BIN))
                        refresh()
                        SyncManager.onLocalChange(this)
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(doc: ScanDocument) {
        val input = inputField(tr(Str.INPUT_DOC_NAME), palette).apply { setText(doc.name) }
        AlertDialog.Builder(this)
            .setTitle(tr(Str.RENAME_TITLE))
            .setView(input)
            .setPositiveButton(tr(Str.BTN_SAVE)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    store.rename(doc.id, name)
                    refresh()
                    SyncManager.onLocalChange(this)
                }
            }
            .setNegativeButton(tr(Str.BTN_CANCEL), null)
            .show()
    }

    private fun showMoveDialog(doc: ScanDocument) {
        val folders = store.folders()
        val labels = listOf(tr(Str.MOVE_ROOT_LABEL)) + folders.map { it.name }
        AlertDialog.Builder(this)
            .setTitle(tr(Str.MOVE_TITLE))
            .setItems(labels.toTypedArray()) { _, which ->
                val target = if (which == 0) null else folders[which - 1].id
                store.moveToFolder(doc.id, target)
                refresh()
                SyncManager.onLocalChange(this)
            }
            .show()
    }

    private fun showVaultTemplateDialog(doc: ScanDocument) {
        val templates = VaultTemplates.all
        val labels = templates.map { t ->
            val existing = store.docForTemplate(t.id)
            if (existing != null && existing.id != doc.id) trf(Str.VAULT_SLOT_TAKEN, t.displayName)
            else t.displayName
        }
        AlertDialog.Builder(this)
            .setTitle(tr(Str.VAULT_SLOT_TITLE))
            .setItems(labels.toTypedArray()) { _, which ->
                store.setVault(doc.id, templates[which].id)
                toast(trf(Str.TOAST_ADDED_TO_VAULT, templates[which].displayName))
                refresh()
                SyncManager.onLocalChange(this)
            }
            .show()
    }

    private fun showNewFolderDialog() {
        val input = inputField(tr(Str.INPUT_FOLDER_NAME), palette)
        AlertDialog.Builder(this)
            .setTitle(tr(Str.NEW_FOLDER_TITLE))
            .setView(input)
            .setPositiveButton(tr(Str.BTN_CREATE)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    store.addFolder(name)
                    refresh()
                }
            }
            .setNegativeButton(tr(Str.BTN_CANCEL), null)
            .show()
    }

    fun shareDoc(doc: ScanDocument) {
        val pdf = store.pdfFile(doc)
        val firstPage = store.pageFile(doc, 0)
        val (fileName, mime) = when {
            pdf != null -> pdf.name to "application/pdf"
            firstPage != null -> firstPage.name to "image/jpeg"
            else -> {
                toast(tr(Str.TOAST_SHARE_NO_FILE))
                return
            }
        }
        val uri = ShareProvider.uriFor(doc.id, fileName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, trf(Str.SHARE_CHOOSER, doc.name)))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val ROOT = "__root__"
        const val BIN_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val REQ_UPLOAD = 71
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    }
}
