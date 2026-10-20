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
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.drivescan.kmp.auth.AuthManager
import com.drivescan.kmp.data.PersistentStore
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

    /** null = All, ROOT = folderId==null docs, otherwise a folder id. */
    private var folderFilter: String? = null
    private var searchQuery: String = ""
    private var visibleDocs: List<ScanDocument> = emptyList()
    private val thumbCache = HashMap<String, Bitmap?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        setContentView(buildUi())
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
        return root
    }

    private fun refresh() {
        // Strings that were baked in at build time pick up the current language too.
        Lang.init(this)
        searchInput.hint = tr(Str.SEARCH_HINT)
        scanButton.text = tr(Str.SCAN_BUTTON)
        // Drive chip
        val connected = AuthManager.isConnected(this)
        driveChip.text = if (connected) {
            tr(Str.DRIVE_CONNECTED)
        } else {
            tr(Str.DRIVE_NOT_CONNECTED)
        }
        driveChip.setTextColor(if (connected) Color.rgb(46, 125, 50) else palette.textSecondary)
        driveChip.background = rounded(palette.chipBg, 20)
        refreshFolders()
        refreshDocs()
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
        visibleDocs = all.filter { doc ->
            val folderOk = when (folderFilter) {
                null -> true
                ROOT -> doc.folderId == null
                else -> doc.folderId == folderFilter
            }
            val searchOk = searchQuery.isEmpty() || doc.name.contains(searchQuery, ignoreCase = true)
            folderOk && searchOk
        }
        docList.adapter = DocAdapter(visibleDocs)
        docList.setOnItemClickListener { _, _, pos, _ ->
            val doc = visibleDocs.getOrNull(pos) ?: return@setOnItemClickListener
            startActivity(Intent(this, DocViewerActivity::class.java).putExtra("docId", doc.id))
        }
        docList.setOnItemLongClickListener { _, _, pos, _ ->
            val doc = visibleDocs.getOrNull(pos) ?: return@setOnItemLongClickListener true
            showDocActions(doc)
            true
        }
        emptyView.visibility = if (visibleDocs.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = when {
            all.isEmpty() -> tr(Str.EMPTY_NO_DOCS)
            searchQuery.isNotEmpty() -> trf(Str.EMPTY_SEARCH, searchQuery)
            else -> tr(Str.EMPTY_FOLDER)
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
            texts.addView(bodyText(doc.name, palette, 15f))
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
                    } else {
                        showVaultTemplateDialog(doc)
                    }
                    5 -> {
                        store.moveToBin(doc.id)
                        toast(tr(Str.TOAST_MOVED_TO_BIN))
                        refresh()
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
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    }
}
