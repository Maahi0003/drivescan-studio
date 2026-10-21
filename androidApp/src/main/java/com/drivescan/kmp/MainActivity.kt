package com.drivescan.kmp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.StatFs
import android.text.Editable
import android.text.TextUtils
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
import com.drivescan.kmp.data.ProfileStore
import com.drivescan.kmp.drive.DriveSync
import com.drivescan.kmp.drive.SyncManager
import com.drivescan.kmp.ocr.OcrHelper
import com.drivescan.kmp.scan.ImageOps
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.applyBackdrop
import com.drivescan.kmp.ui.bodyText
import com.drivescan.kmp.ui.bottomNav
import com.drivescan.kmp.ui.dp
import com.drivescan.kmp.ui.formatBytesShort
import com.drivescan.kmp.ui.ghostButton
import com.drivescan.kmp.ui.glassBg
import com.drivescan.kmp.ui.glassDrawable
import com.drivescan.kmp.ui.inputField
import com.drivescan.kmp.ui.palette
import com.drivescan.kmp.ui.primaryButton
import com.drivescan.kmp.ui.rounded
import com.drivescan.kmp.ui.smallText
import com.drivescan.kmp.ui.styleGlassDialog
import com.drivescan.kmp.ui.toCircle
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import com.drivescan.shared.ScanDocument
import com.drivescan.shared.VaultTemplates
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Dashboard: folders, documents with thumbnails, search, document actions,
 * Drive status chip and the big Scan entry point. All data is local-first
 * from [PersistentStore]; Drive is only a sync layer on top.
 *
 * v1.3.0 layout follows the Stitch mockups (Frosted Teal Glass / Obsidian):
 * glass top bar with the profile corner, greeting row, Drive-status glass
 * card (or the welcome connect card while disconnected), glass search,
 * folder chips, "Recent Scans", glass document cards, Storage & Vault
 * Usage card, big round + FAB and the glass bottom nav.
 */
class MainActivity : Activity() {

    private val store by lazy { PersistentStore.get(this) }
    private val palette by lazy { palette() }

    private lateinit var docList: ListView
    private lateinit var emptyView: TextView
    private lateinit var folderBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionLabel: TextView
    private lateinit var combineButton: android.widget.Button
    private lateinit var selectionActionsButton: android.widget.Button

    // v1.3.0 dashboard chrome
    private lateinit var topAvatarSlot: FrameLayout
    private lateinit var greetAvatarSlot: FrameLayout
    private lateinit var greetSub: TextView
    private lateinit var greetName: TextView
    private lateinit var driveCard: LinearLayout
    private lateinit var driveCardTitle: TextView
    private lateinit var driveCardStatus: TextView
    private lateinit var syncPill: android.widget.Button
    private lateinit var connectCard: LinearLayout
    private var welcomeDismissed = false

    // Storage card views
    private lateinit var storageSegLocal: View
    private lateinit var storageSegVault: View
    private lateinit var storageSpacer: View
    private lateinit var storageDeviceText: TextView
    private lateinit var storageDriveText: TextView
    private lateinit var storageCaption: TextView

    /** Multi-select state (combine flow); insertion order = combine order. */
    private val selection = LinkedHashSet<String>()

    /** null = All, ROOT = folderId==null docs, otherwise a folder id. */
    private var folderFilter: String? = null
    private var searchQuery: String = ""
    private var visibleDocs: List<ScanDocument> = emptyList()
    private val thumbCache = HashMap<String, Bitmap?>()
    private val sizeCache = HashMap<String, Long>()

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
        sizeCache.clear()
        refresh()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            applyBackdrop(palette)
        }

        // ---- Top app bar (glass): logo mark, app name, profile corner --------
        val topWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(2))
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassBg(palette, palette.radius, floating = true)
            elevation = dp(4).toFloat()
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        val logoMark = TextView(this).apply {
            text = "D"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            val g = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.primaryContainer, palette.primary)
            )
            g.cornerRadius = dp(10).toFloat()
            background = g
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) }
        }
        topBar.addView(logoMark)
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titles.addView(TextView(this).apply {
            text = "DriveScan Studio"
            textSize = 16f
            setTextColor(palette.text)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        titles.addView(TextView(this).apply {
            text = tr(Str.TOPBAR_HOME)
            textSize = 12f
            setTextColor(palette.textSecondary)
        })
        topBar.addView(titles)
        topAvatarSlot = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        }
        topBar.addView(topAvatarSlot)
        topWrap.addView(topBar)
        root.addView(topWrap)

        // ---- Greeting row -----------------------------------------------------
        val greetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        greetAvatarSlot = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) }
        }
        greetRow.addView(greetAvatarSlot)
        val greetTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        greetSub = TextView(this).apply {
            textSize = 12.5f
            setTextColor(palette.textSecondary)
        }
        greetTexts.addView(greetSub)
        greetName = TextView(this).apply {
            textSize = 23f
            setTextColor(palette.text)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        greetTexts.addView(greetName)
        greetRow.addView(greetTexts)
        root.addView(greetRow)

        // ---- Drive status card (connected state) ------------------------------
        driveCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassBg(palette, palette.radius)
            elevation = dp(4).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(10), dp(16), dp(0)) }
            setOnClickListener {
                // In the collapsed (offline, dismissed) state, tapping reopens
                // the welcome connect card.
                if (!AuthManager.isConnected(this@MainActivity) && welcomeDismissed) {
                    welcomeDismissed = false
                    refresh()
                }
            }
        }
        val cloudWell = TextView(this).apply {
            text = "☁"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(palette.primary)
            background = rounded(palette.chipBg, palette.radiusSmall)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) }
        }
        driveCard.addView(cloudWell)
        val driveTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        driveCardTitle = TextView(this).apply {
            textSize = 13.5f
            setTextColor(palette.text)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        driveTexts.addView(driveCardTitle)
        driveCardStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(palette.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        driveTexts.addView(driveCardStatus)
        driveCard.addView(driveTexts)
        syncPill = android.widget.Button(this).apply {
            textSize = 12.5f
            isAllCaps = false
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(palette.syncedText)
            background = rounded(palette.syncedBg, 999, palette.syncedBorder)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { manualSync() }
        }
        driveCard.addView(syncPill)
        root.addView(driveCard)

        // ---- Welcome connect card (disconnected state, Stitch screen 1) -------
        connectCard = buildConnectCard()
        root.addView(connectCard)

        // ---- Glass search bar ---------------------------------------------------
        val searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassBg(palette, palette.radius)
            elevation = dp(3).toFloat()
            setPadding(dp(14), dp(2), dp(14), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(12), dp(16), dp(2)) }
        }
        searchBar.addView(TextView(this).apply {
            text = "🔍"
            textSize = 15f
            setPadding(0, 0, dp(8), 0)
        })
        searchInput = EditText(this).apply {
            background = null
            textSize = 14.5f
            setTextColor(palette.text)
            setHintTextColor(palette.textSecondary)
            hint = tr(Str.SEARCH_HINT_OCR)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString()?.trim() ?: ""
                    refreshDocs()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        searchBar.addView(searchInput)
        root.addView(searchBar)

        folderBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(folderBar)
        })

        // ---- "Recent Scans" header + Auto-OCR pill ------------------------------
        val recentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(2))
        }
        recentRow.addView(TextView(this).apply {
            text = tr(Str.RECENT_SCANS)
            textSize = 16.5f
            setTextColor(palette.text)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        })
        val ocrPill = TextView(this).apply {
            text = tr(Str.AUTO_OCR_PILL)
            textSize = 10.5f
            setTextColor(palette.textSecondary)
            background = rounded(palette.glassSubtle, 999, palette.glassBorder)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }
        recentRow.addView(ocrPill)
        root.addView(recentRow)

        // Multi-select action bar (combine flow); hidden unless selecting.
        selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(6))
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
            setPadding(dp(16), dp(6), dp(16), dp(8))
            clipToPadding = false
        }
        root.addView(docList)

        emptyView = smallText("", palette, 14f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
            visibility = View.GONE
        }
        root.addView(emptyView)

        // ---- Storage & Vault Usage card ------------------------------------------
        root.addView(buildStorageCard())

        root.addView(bottomNav(palette, "home"))

        // Floating "+" button (glass-glow circle): Scan or Upload chooser.
        val outer = FrameLayout(this)
        outer.addView(root, FrameLayout.LayoutParams(-1, -1))
        val fabWrap = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(78), dp(78), Gravity.END or Gravity.BOTTOM)
                .apply { setMargins(0, 0, dp(13), dp(88)) }
        }
        val glow = View(this).apply {
            background = rounded(
                if (palette.dark) Color.argb(46, 0, 229, 163) else Color.argb(46, 12, 98, 81), 999
            )
            layoutParams = FrameLayout.LayoutParams(dp(78), dp(78))
        }
        fabWrap.addView(glow)
        val fab = TextView(this).apply {
            text = "+"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(palette.onPrimaryContainer)
            val g = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                if (palette.dark) {
                    intArrayOf(Color.rgb(110, 255, 195), Color.rgb(0, 229, 163))
                } else {
                    intArrayOf(Color.rgb(12, 98, 81), Color.rgb(0, 72, 59))
                }
            )
            g.shape = android.graphics.drawable.GradientDrawable.OVAL
            background = g
            elevation = dp(8).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER)
            setOnClickListener { showFabChooser() }
        }
        fabWrap.addView(fab)
        outer.addView(fabWrap)
        return outer
    }

    /** Welcome / connect card shown while Drive is disconnected (Stitch screen 1). */
    private fun buildConnectCard(): LinearLayout {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBg(palette, palette.radius)
            elevation = dp(5).toFloat()
            setPadding(dp(18), dp(16), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(10), dp(16), dp(0)) }
        }
        val logoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        logoRow.addView(TextView(this).apply {
            text = "D"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            val g = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.primaryContainer, palette.primary)
            )
            g.cornerRadius = dp(14).toFloat()
            background = g
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) }
        })
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = "DriveScan Studio"
            textSize = 19f
            setTextColor(palette.text)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        })
        titleCol.addView(TextView(this).apply {
            text = "• " + tr(Str.WELCOME_TAGLINE) + " •"
            textSize = 12.5f
            setTextColor(palette.secondaryText)
        })
        logoRow.addView(titleCol)
        cardLayout.addView(logoRow)

        fun featureRow(glyph: String, titleKey: Str, descKey: Str) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            row.addView(TextView(this).apply {
                text = glyph
                textSize = 17f
                gravity = Gravity.CENTER
                background = rounded(palette.chipBg, palette.radiusSmall)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) }
            })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply {
                text = tr(titleKey)
                textSize = 14.5f
                setTextColor(palette.text)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            })
            col.addView(TextView(this).apply {
                text = tr(descKey)
                textSize = 12.5f
                setTextColor(palette.textSecondary)
            })
            row.addView(col)
            cardLayout.addView(row)
        }
        featureRow("▦", Str.WELCOME_F1_T, Str.WELCOME_F1_D)
        featureRow("🛡", Str.WELCOME_F2_T, Str.WELCOME_F2_D)
        featureRow("☁", Str.WELCOME_F3_T, Str.WELCOME_F3_D)

        cardLayout.addView(primaryButton(tr(Str.BTN_CONNECT_DRIVE)).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(16), 0, 0) }
            setOnClickListener {
                // Same auth entry point the Settings connect button uses.
                if (AuthManager.clientId(this@MainActivity).isEmpty()) {
                    toast(tr(Str.TOAST_NEED_CLIENT_ID))
                } else if (!AuthManager.startAuth(this@MainActivity)) {
                    toast(tr(Str.TOAST_CONNECT_FAIL))
                }
            }
        })
        cardLayout.addView(smallText("ℹ  " + tr(Str.WELCOME_NOTE), palette, 12f).apply {
            setPadding(0, dp(10), 0, 0)
        })
        cardLayout.addView(ghostButton(tr(Str.WELCOME_OFFLINE_BTN), palette).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }
            setOnClickListener {
                welcomeDismissed = true
                refresh()
            }
        })
        return cardLayout
    }

    /** Storage & Vault Usage glass card (values filled in by [refreshStorage]). */
    private fun buildStorageCard(): LinearLayout {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBg(palette, palette.radius)
            elevation = dp(4).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(2), dp(16), dp(12)) }
        }
        cardLayout.addView(TextView(this).apply {
            text = tr(Str.STORAGE_CARD_TITLE)
            textSize = 15.5f
            setTextColor(palette.text)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        })
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(
                if (palette.dark) Color.argb(40, 255, 255, 255) else Color.argb(160, 255, 255, 255),
                999, palette.glassBorder
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(9)
            ).apply { setMargins(0, dp(12), 0, dp(10)) }
        }
        storageSegLocal = View(this).apply {
            background = rounded(palette.primaryContainer, 999)
        }
        storageSegVault = View(this).apply {
            background = rounded(palette.secondary, 999)
        }
        storageSpacer = View(this)
        track.addView(storageSegLocal)
        track.addView(storageSegVault)
        track.addView(storageSpacer)
        cardLayout.addView(track)

        fun legendRow(dotColor: Int): Pair<LinearLayout, TextView> {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, 0)
            }
            row.addView(View(this).apply {
                background = rounded(dotColor, 999)
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) }
            })
            val label = TextView(this).apply {
                textSize = 12.5f
                setTextColor(palette.textSecondary)
            }
            row.addView(label)
            return row to label
        }
        val (rowDevice, deviceLabel) = legendRow(palette.primaryContainer)
        storageDeviceText = deviceLabel
        cardLayout.addView(rowDevice)
        val (rowDrive, driveLabel) = legendRow(palette.secondary)
        storageDriveText = driveLabel
        cardLayout.addView(rowDrive)
        storageCaption = smallText("", palette, 11.5f).apply { setPadding(0, dp(6), 0, 0) }
        cardLayout.addView(storageCaption)
        return cardLayout
    }

    /**
     * Storage figures, computed honestly: the bar is the scans store's share
     * of this device's total storage (StatFs) — local (non-vault) pages in
     * the primary colour, Vault pages in marigold — and the caption says so.
     */
    private fun refreshStorage() {
        val docs = store.documents()
        var vaultBytes = 0L
        for (d in store.vaultDocuments()) vaultBytes += docSize(d)
        val totalScans = store.totalStorageBytes()
        val localBytes = (totalScans - vaultBytes).coerceAtLeast(0L)
        val totalDevice = try {
            StatFs(filesDir.absolutePath).totalBytes
        } catch (e: Exception) {
            0L
        }
        fun setWeight(view: View, weight: Float) {
            view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        }
        if (totalDevice > 0) {
            setWeight(storageSegLocal, localBytes.toFloat())
            setWeight(storageSegVault, vaultBytes.toFloat())
            setWeight(storageSpacer, (totalDevice - totalScans).coerceAtLeast(0L).toFloat())
            storageCaption.text = trf(Str.STORAGE_OF_TOTAL, formatBytesShort(totalDevice))
            storageCaption.visibility = View.VISIBLE
        } else {
            setWeight(storageSegLocal, 1f)
            setWeight(storageSegVault, 0f)
            setWeight(storageSpacer, 1f)
            storageCaption.visibility = View.GONE
        }
        storageDeviceText.text = trf(Str.STORAGE_DEVICE_USED, formatBytesShort(totalScans))
        val driveCount = docs.count { it.driveId != null }
        storageDriveText.text = trf(Str.STORAGE_DRIVE_COUNT, driveCount)
    }

    /** Total on-device size of one document's folder (pages + PDF). */
    private fun docSize(doc: ScanDocument): Long {
        sizeCache[doc.id]?.let { return it }
        var total = 0L
        try {
            store.docDir(doc.id).listFiles()?.forEach { total += it.length() }
        } catch (e: Exception) {
            // Size is display-only; a failure just shows the cached/zero value.
        }
        sizeCache[doc.id] = total
        return total
    }

    // ---- profile corner --------------------------------------------------------

    /**
     * Builds the avatar content for a slot: the local profile photo
     * (circle-cropped) if set, else the name's initial or a neutral person
     * glyph, on a primary-tint glass circle, with a Drive-status dot.
     */
    private fun buildAvatar(sizeDp: Int, withDot: Boolean): View {
        val holder = FrameLayout(this)
        val connected = AuthManager.isConnected(this)
        val photo = ProfileStore.photoFile(this)
        val name = ProfileStore.name(this)
        if (ProfileStore.hasPhoto(this)) {
            val bmp = ImageOps.loadBitmap(photo, 256)
            if (bmp != null) {
                holder.addView(ImageView(this).apply {
                    setImageBitmap(bmp.toCircle(dp(sizeDp)))
                    layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
                })
            }
        }
        if (holder.childCount == 0) {
            holder.addView(TextView(this).apply {
                text = if (name.isNotEmpty()) name.first().uppercaseChar().toString() else "👤"
                textSize = if (name.isNotEmpty()) sizeDp * 0.42f else sizeDp * 0.44f
                gravity = Gravity.CENTER
                setTextColor(palette.primary)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                background = rounded(palette.chipBg, 999, palette.glassBorder)
                layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            })
        }
        if (withDot) {
            val dotSize = (sizeDp * 0.27f).toInt().coerceAtLeast(10)
            holder.addView(View(this).apply {
                background = rounded(
                    if (connected) palette.success else palette.pendingDot, 999, palette.bg
                )
                layoutParams = FrameLayout.LayoutParams(
                    dp(dotSize), dp(dotSize), Gravity.END or Gravity.BOTTOM
                )
            })
        }
        holder.setOnClickListener { showProfileSheet() }
        return holder
    }

    private fun refreshAvatars() {
        topAvatarSlot.removeAllViews()
        topAvatarSlot.addView(buildAvatar(38, withDot = false))
        greetAvatarSlot.removeAllViews()
        greetAvatarSlot.addView(buildAvatar(52, withDot = true))
    }

    /** Profile sheet: current avatar + name, photo/name actions (local only). */
    private fun showProfileSheet() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(16))
        }
        val avatarSlot = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        }
        avatarSlot.addView(buildAvatar(76, withDot = false))
        container.addView(avatarSlot)
        val name = ProfileStore.name(this)
        container.addView(TextView(this).apply {
            text = if (name.isNotEmpty()) name else tr(Str.PROFILE_NO_NAME)
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(palette.text)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(6))
        })
        val dialog = AlertDialog.Builder(this).setView(container).create()
        fun sheetButton(label: String, action: () -> Unit) {
            container.addView(ghostButton(label, palette).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(8), 0, 0) }
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            })
        }
        sheetButton(tr(Str.PROFILE_CHANGE_PHOTO)) { openProfilePhotoPicker() }
        sheetButton(tr(Str.PROFILE_EDIT_NAME)) { showEditNameDialog() }
        if (ProfileStore.hasPhoto(this)) {
            sheetButton(tr(Str.PROFILE_REMOVE_PHOTO)) {
                ProfileStore.removePhoto(this)
                recreate()
            }
        }
        dialog.show()
        styleGlassDialog(dialog, palette)
    }

    private fun showEditNameDialog() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(0))
        }
        val input = inputField(tr(Str.PROFILE_NAME_HINT), palette).apply {
            setText(ProfileStore.name(this@MainActivity))
        }
        wrap.addView(input)
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr(Str.PROFILE_TITLE))
            .setView(wrap)
            .setPositiveButton(tr(Str.BTN_SAVE)) { _, _ ->
                ProfileStore.setName(this, input.text.toString())
                recreate()
            }
            .setNegativeButton(tr(Str.BTN_CANCEL), null)
            .create()
        dialog.show()
        styleGlassDialog(dialog, palette)
    }

    private fun openProfilePhotoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQ_PROFILE_PHOTO)
    }

    private fun refresh() {
        // Strings that were baked in at build time pick up the current language too.
        Lang.init(this)
        searchInput.hint = tr(Str.SEARCH_HINT_OCR)

        // Greeting: time-of-day line + the greeting word (with the profile
        // name when one is set). The status word/dot mirror Drive connection.
        val connected = AuthManager.isConnected(this)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val tod = when {
            hour < 12 -> tr(Str.GREETING_MORNING)
            hour < 17 -> tr(Str.GREETING_AFTERNOON)
            else -> tr(Str.GREETING_EVENING)
        }
        greetSub.text = tod + " • " + if (connected) tr(Str.STATUS_ONLINE) else tr(Str.STATUS_OFFLINE)
        val profileName = ProfileStore.name(this)
        greetName.text = if (profileName.isNotEmpty()) {
            trf(Str.GREETING_HELLO, profileName)
        } else {
            tr(Str.GREETING_PLAIN)
        }
        refreshAvatars()

        // Drive card vs welcome connect card.
        if (connected) {
            driveCard.visibility = View.VISIBLE
            connectCard.visibility = View.GONE
            driveCardTitle.text = tr(Str.DRIVE_CARD_TITLE)
            val pending = DriveSync.pendingCount(this)
            driveCardStatus.text = when {
                pending > 0 -> trf(Str.CHIP_PENDING_SYNC, pending).trim().trimStart('•').trim()
                else -> {
                    val last = DriveSync.lastSyncMs(this)
                    if (last > 0L) {
                        trf(
                            Str.CHIP_LAST_SYNCED,
                            SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(last))
                        )
                    } else {
                        tr(Str.DRIVE_ALL_SYNCED)
                    }
                }
            }
            syncPill.text = "↻ " + tr(Str.BTN_SYNC_NOW).dropWhile { !it.isLetter() }.trim()
                .ifEmpty { tr(Str.BTN_SYNC_NOW) }
            syncPill.visibility = View.VISIBLE
        } else if (welcomeDismissed) {
            driveCard.visibility = View.VISIBLE
            connectCard.visibility = View.GONE
            driveCardTitle.text = tr(Str.DRIVE_NOT_CONNECTED)
            driveCardStatus.text = ""
            syncPill.visibility = View.GONE
        } else {
            driveCard.visibility = View.GONE
            connectCard.visibility = View.VISIBLE
        }
        refreshFolders()
        refreshDocs()
        refreshStorage()
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
            if (selected) {
                setTextColor(palette.onPrimaryContainer)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                background = rounded(palette.primaryContainer, 999)
            } else {
                setTextColor(palette.text)
                background = rounded(palette.glassSubtle, 999, palette.glassBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun refreshFolders() {
        folderBar.removeAllViews()
        // Selected "All" chip carries the total count, like the mockup.
        val allCount = store.documents().size
        val allChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(10), dp(7))
            if (folderFilter == null) {
                background = rounded(palette.primaryContainer, 999)
            } else {
                background = rounded(palette.glassSubtle, 999, palette.glassBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { folderFilter = null; refresh() }
        }
        allChip.addView(TextView(this).apply {
            text = tr(Str.CHIP_ALL)
            textSize = 13.5f
            setTextColor(if (folderFilter == null) palette.onPrimaryContainer else palette.text)
            if (folderFilter == null) typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        })
        allChip.addView(TextView(this).apply {
            text = allCount.toString()
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTextColor(if (folderFilter == null) palette.onPrimaryContainer else palette.textSecondary)
            background = rounded(
                if (folderFilter == null) {
                    if (palette.dark) Color.rgb(0, 56, 37) else Color.argb(51, 255, 255, 255)
                } else {
                    palette.chipBg
                },
                999
            )
            setPadding(dp(6), dp(1), dp(6), dp(1))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        })
        folderBar.addView(allChip)
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
        val dialog = AlertDialog.Builder(this)
            .setItems(arrayOf(tr(Str.FAB_SCAN), tr(Str.FAB_UPLOAD))) { _, which ->
                if (which == 0) {
                    startActivity(Intent(this, ScannerActivity::class.java))
                } else {
                    openUploadPicker()
                }
            }
            .create()
        dialog.show()
        styleGlassDialog(dialog, palette)
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
        if (requestCode == REQ_PROFILE_PHOTO) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                thread {
                    ProfileStore.savePhoto(this, uri)
                    runOnUiThread { recreate() }
                }
            }
            return
        }
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
            val selected = doc.id in selection
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = if (selected) {
                    glassDrawable(
                        palette, palette.radius,
                        fill = palette.chipBg, border = palette.primaryContainer
                    )
                } else {
                    glassBg(palette, palette.radius)
                }
                elevation = dp(3).toFloat()
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }

            // Thumbnail with the page-count badge (Stitch card).
            val thumbWrap = FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(80)).apply { marginEnd = dp(12) }
            }
            val thumb = ImageView(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(palette.thumbBg, palette.radiusSmall)
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
            thumbWrap.addView(thumb)
            thumbWrap.addView(TextView(this@MainActivity).apply {
                text = "${doc.pageCount}p"
                textSize = 9.5f
                setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                background = rounded(Color.argb(190, 24, 24, 24), 4)
                setPadding(dp(4), dp(1), dp(4), dp(1))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.BOTTOM
                ).apply { setMargins(0, 0, dp(4), dp(4)) }
            })
            row.addView(thumbWrap)

            val texts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            nameRow.addView(TextView(this@MainActivity).apply {
                text = if (selected) "✓ ${doc.name}" else doc.name
                textSize = 15.5f
                setTextColor(palette.text)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            nameRow.addView(TextView(this@MainActivity).apply {
                text = "⋮"
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(palette.textSecondary)
                setPadding(dp(8), dp(2), dp(2), dp(2))
                setOnClickListener { showDocActions(doc) }
            })
            texts.addView(nameRow)

            // Meta: "N pages • date • size" (+ vault marker), per the mockup.
            val pagesText = if (doc.pageCount == 1) {
                trf(Str.META_PAGES, doc.pageCount)
            } else {
                trf(Str.META_PAGES_PLURAL, doc.pageCount)
            }
            val meta = StringBuilder(pagesText)
            meta.append(" • ").append(dateOnlyFormat.format(Date(doc.modifiedAtEpochMs)))
            val size = docSize(doc)
            if (size > 0) meta.append(" • ").append(formatBytesShort(size))
            if (doc.inVault) meta.append(" • ").append(tr(Str.META_VAULT_SUFFIX))
            texts.addView(smallText(meta.toString(), palette, 12f))

            // Sync status pill: Drive Synced / Pending sync.
            val synced = doc.driveId != null
            texts.addView(TextView(this@MainActivity).apply {
                text = if (synced) "✓ " + tr(Str.DOC_SYNCED) else "● " + tr(Str.DOC_PENDING)
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(if (synced) palette.syncedText else palette.pendingText)
                background = rounded(
                    if (synced) palette.syncedBg else palette.pendingBg, 999,
                    if (synced) palette.syncedBorder else palette.pendingBorder
                )
                setPadding(dp(8), dp(3), dp(8), dp(3))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) }
            })
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
        val dialog = AlertDialog.Builder(this)
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
            .create()
        dialog.show()
        styleGlassDialog(dialog, palette)
    }

    private fun paddedInput(input: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(0))
            addView(input)
        }

    private fun showRenameDialog(doc: ScanDocument) {
        val input = inputField(tr(Str.INPUT_DOC_NAME), palette).apply { setText(doc.name) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr(Str.RENAME_TITLE))
            .setView(paddedInput(input))
            .setPositiveButton(tr(Str.BTN_SAVE)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    store.rename(doc.id, name)
                    refresh()
                    SyncManager.onLocalChange(this)
                }
            }
            .setNegativeButton(tr(Str.BTN_CANCEL), null)
            .create()
        dialog.show()
        styleGlassDialog(dialog, palette)
    }

    private fun showMoveDialog(doc: ScanDocument) {
        val folders = store.folders()
        val labels = listOf(tr(Str.MOVE_ROOT_LABEL)) + folders.map { it.name }
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr(Str.MOVE_TITLE))
            .setItems(labels.toTypedArray()) { _, which ->
                val target = if (which == 0) null else folders[which - 1].id
                store.moveToFolder(doc.id, target)
                refresh()
                SyncManager.onLocalChange(this)
            }
            .create()
        dialog.show()
        styleGlassDialog(dialog, palette)
    }

    private fun showVaultTemplateDialog(doc: ScanDocument) {
        val templates = VaultTemplates.all
        val labels = templates.map { t ->
            val existing = store.docForTemplate(t.id)
            if (existing != null && existing.id != doc.id) trf(Str.VAULT_SLOT_TAKEN, t.displayName)
            else t.displayName
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr(Str.VAULT_SLOT_TITLE))
            .setItems(labels.toTypedArray()) { _, which ->
                store.setVault(doc.id, templates[which].id)
                toast(trf(Str.TOAST_ADDED_TO_VAULT, templates[which].displayName))
                refresh()
                SyncManager.onLocalChange(this)
            }
            .create()
        dialog.show()
        styleGlassDialog(dialog, palette)
    }

    private fun showNewFolderDialog() {
        val input = inputField(tr(Str.INPUT_FOLDER_NAME), palette)
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr(Str.NEW_FOLDER_TITLE))
            .setView(paddedInput(input))
            .setPositiveButton(tr(Str.BTN_CREATE)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    store.addFolder(name)
                    refresh()
                }
            }
            .setNegativeButton(tr(Str.BTN_CANCEL), null)
            .create()
        dialog.show()
        styleGlassDialog(dialog, palette)
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
        private const val REQ_PROFILE_PHOTO = 73
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    }
}
