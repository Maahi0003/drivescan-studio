package com.drivescan.kmp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.drive.DriveSync
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.bodyText
import com.drivescan.kmp.ui.bottomNav
import com.drivescan.kmp.ui.card
import com.drivescan.kmp.ui.dp
import com.drivescan.kmp.ui.palette
import com.drivescan.kmp.ui.smallText
import com.drivescan.kmp.ui.titleText
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import com.drivescan.shared.ScanDocument

/**
 * Bin: deleted documents are kept for 30 days, then auto-purged on app
 * start (see MainActivity). Restore or delete forever from here.
 */
class BinActivity : Activity() {

    private val store by lazy { PersistentStore.get(this) }
    private val palette by lazy { palette() }
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.bg)
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(container)
        })
        root.addView(bottomNav(palette, "bin"))
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        container.removeAllViews()
        container.addView(titleText(tr(Str.BIN_TITLE), palette, 22f))
        container.addView(
            smallText(tr(Str.BIN_SUBTITLE), palette)
                .apply { setPadding(0, dp(4), 0, dp(12)) }
        )
        val docs = store.binDocuments()
        if (docs.isEmpty()) {
            container.addView(
                smallText(tr(Str.BIN_EMPTY), palette, 14f).apply { setPadding(0, dp(16), 0, 0) }
            )
            return
        }
        val now = System.currentTimeMillis()
        for (doc in docs) {
            val deletedAt = doc.deletedAtEpochMs ?: now
            val daysUsed = ((now - deletedAt) / DAY_MS).toInt()
            val daysLeft = (RETENTION_DAYS - daysUsed).coerceAtLeast(0)
            val row = card(palette)
            row.addView(bodyText(doc.name, palette, 15f))
            row.addView(
                smallText(
                    trf(Str.BIN_META, doc.pageCount, daysLeft),
                    palette
                )
            )
            row.setOnClickListener { showActions(doc) }
            container.addView(row)
        }
    }

    private fun showActions(doc: ScanDocument) {
        AlertDialog.Builder(this)
            .setTitle(doc.name)
            .setItems(arrayOf(tr(Str.BIN_OPT_RESTORE), tr(Str.BIN_OPT_VIEW), tr(Str.BIN_OPT_DELETE_FOREVER))) { _, which ->
                when (which) {
                    0 -> {
                        store.restoreFromBin(doc.id)
                        toast(tr(Str.TOAST_RESTORED))
                        render()
                    }
                    1 -> startActivity(
                        Intent(this, DocViewerActivity::class.java).putExtra("docId", doc.id)
                    )
                    2 -> confirmDeleteForever(doc)
                }
            }
            .show()
    }

    private fun confirmDeleteForever(doc: ScanDocument) {
        AlertDialog.Builder(this)
            .setTitle(tr(Str.DELETE_FOREVER_TITLE))
            .setMessage(trf(Str.DELETE_FOREVER_MSG, doc.name))
            .setPositiveButton(tr(Str.BTN_DELETE)) { _, _ ->
                doc.driveId?.let { DriveSync.queueTrash(this, it) }
                store.deleteForever(doc.id)
                toast(tr(Str.TOAST_DELETED_FOREVER))
                render()
            }
            .setNegativeButton(tr(Str.BTN_CANCEL), null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val RETENTION_DAYS = 30
    }
}
