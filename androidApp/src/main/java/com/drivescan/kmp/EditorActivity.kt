package com.drivescan.kmp

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.drive.SyncManager
import com.drivescan.kmp.ocr.OcrHelper
import com.drivescan.kmp.scan.ImageOps
import com.drivescan.kmp.scan.PageFilter
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.bodyText
import com.drivescan.kmp.ui.ghostButton
import com.drivescan.kmp.ui.inputField
import com.drivescan.kmp.ui.palette
import com.drivescan.kmp.ui.primaryButton
import com.drivescan.kmp.ui.rounded
import com.drivescan.kmp.ui.smallText
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import com.drivescan.shared.VaultTemplates
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Per-page edit state that survives Scanner↔Editor hops inside one session. */
object EditorSession {
    val corners = HashMap<String, FloatArray>()
    val filters = HashMap<String, PageFilter>()
    val rotations = HashMap<String, Int>()

    /** Signature stamps per page path (transparent-background bitmaps). */
    val signatures = HashMap<String, Bitmap>()
    fun clear() {
        corners.clear()
        filters.clear()
        rotations.clear()
        signatures.clear()
    }
}

/**
 * Scan editor: drag the 4 corners to the document edges (perspective warp),
 * apply filters, rotate, manage pages, then save as PDF/JPG into a folder
 * or a vault template slot.
 */
class EditorActivity : Activity() {

    private val store by lazy { PersistentStore.get(this) }
    private val palette by lazy { palette() }

    private val pagePaths = mutableListOf<String>()
    private var current = 0
    private var sessionDir: File? = null
    private var initialTemplateId: String? = null
    private var initialFolderId: String? = null

    /** ID-card flow: compose the first two pages (front/back) onto one A4 page. */
    private var idCompose = false

    private lateinit var pageImage: ImageView
    private lateinit var cornerView: CornerView
    private lateinit var pageLabel: TextView
    private lateinit var strip: LinearLayout
    private var previewBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        intent.getStringArrayListExtra("pages")?.let { pagePaths.addAll(it) }
        sessionDir = intent.getStringExtra("sessionDir")?.let { File(it) }
        initialTemplateId = intent.getStringExtra("vaultTemplateId")
        initialFolderId = intent.getStringExtra("folderId")
        idCompose = intent.getBooleanExtra("idCompose", false)
        // Seed per-page corners from the scanner's auto-detection (if any).
        intent.getStringArrayListExtra("pageCorners")?.forEachIndexed { idx, encoded ->
            val path = pagePaths.getOrNull(idx) ?: return@forEachIndexed
            if (encoded.isNotBlank() && !EditorSession.corners.containsKey(path)) {
                val nums = encoded.split(",").mapNotNull { it.toFloatOrNull() }
                if (nums.size == 8) EditorSession.corners[path] = nums.toFloatArray()
            }
        }
        if (pagePaths.isEmpty()) {
            toast(tr(Str.TOAST_NO_PAGE_FOUND))
            finish()
            return
        }
        setContentView(buildUi())
        showPage(0)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.bg)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        top.addView(TextView(this).apply {
            text = tr(Str.EDITOR_TITLE)
            setTextColor(palette.text)
            textSize = 17f
        })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        pageLabel = TextView(this).apply {
            setTextColor(palette.textSecondary)
            textSize = 13f
        }
        top.addView(pageLabel)
        root.addView(top)

        val stage = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        pageImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        stage.addView(pageImage, FrameLayout.LayoutParams(-1, -1))
        cornerView = CornerView(this)
        stage.addView(cornerView, FrameLayout.LayoutParams(-1, -1))
        root.addView(stage)

        val filtersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(2))
        }
        for (f in PageFilter.values()) {
            filtersRow.addView(darkChip(filterLabel(f)) {
                EditorSession.filters[currentPath()] = f
                showPage(current)
            })
        }
        root.addView(filtersRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        actions.addView(darkChip(tr(Str.BTN_ROTATE)) {
            val path = currentPath()
            EditorSession.rotations[path] = ((EditorSession.rotations[path] ?: 0) + 90) % 360
            showPage(current)
        })
        actions.addView(darkChip(tr(Str.BTN_ADD_PAGE)) { addPageFromCamera() })
        actions.addView(darkChip(tr(Str.BTN_DELETE_PAGE)) { deleteCurrentPage() })
        root.addView(actions)

        val manageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(4))
        }
        manageRow.addView(darkChip(tr(Str.BTN_MOVE_LEFT)) { moveCurrentPage(-1) })
        manageRow.addView(darkChip(tr(Str.BTN_MOVE_RIGHT)) { moveCurrentPage(1) })
        manageRow.addView(darkChip(tr(Str.BTN_SIGN)) { showSignDialog() })
        root.addView(manageRow)

        strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        root.addView(HorizontalScrollView(this).apply { addView(strip) })

        val saveBtn = primaryButton(tr(Str.BTN_SAVE_DOC)).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(4), dp(16), dp(14)) }
            setOnClickListener { showSaveDialog() }
        }
        root.addView(saveBtn)
        return root
    }

    private fun darkChip(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(palette.text)
            background = rounded(palette.card, palette.radius, palette.cardBorder)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun filterLabel(f: PageFilter): String = when (f) {
        PageFilter.ORIGINAL -> tr(Str.FILTER_ORIGINAL)
        PageFilter.GRAY -> tr(Str.FILTER_GRAY)
        PageFilter.BW -> tr(Str.FILTER_BW)
        PageFilter.ENHANCE -> tr(Str.FILTER_ENHANCE)
    }

    private fun currentPath(): String = pagePaths[current]

    private fun cornersFor(path: String): FloatArray =
        EditorSession.corners.getOrPut(path) { ImageOps.defaultCorners() }

    private fun showPage(index: Int) {
        if (pagePaths.isEmpty()) return
        current = index.coerceIn(0, pagePaths.size - 1)
        val path = currentPath()
        val raw = ImageOps.loadBitmap(File(path), 1400) ?: run {
            toast(tr(Str.TOAST_PAGE_LOAD_FAIL))
            return
        }
        val rotated = ImageOps.rotate(raw, EditorSession.rotations[path] ?: 0)
        var filtered = ImageOps.applyFilter(rotated, EditorSession.filters[path] ?: PageFilter.ORIGINAL)
        EditorSession.signatures[path]?.let { sig -> filtered = stampSignature(filtered, sig) }
        previewBitmap = filtered
        pageImage.setImageBitmap(filtered)
        cornerView.bind(filtered, cornersFor(path))
        pageLabel.text = trf(Str.PAGE_LABEL, current + 1, pagePaths.size)
        refreshStrip()
    }

    private fun refreshStrip() {
        strip.removeAllViews()
        pagePaths.forEachIndexed { idx, path ->
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(58)).apply { marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(
                    if (idx == current) palette.primary else palette.chipBg, palette.radiusSmall
                )
                setPadding(3, 3, 3, 3)
                ImageOps.loadBitmap(File(path), 200)?.let { setImageBitmap(it) }
                setOnClickListener { showPage(idx) }
            }
            strip.addView(thumb)
        }
    }

    private fun addPageFromCamera() {
        val intent = Intent(this, ScannerActivity::class.java).apply {
            sessionDir?.let { putExtra("sessionDir", it.absolutePath) }
            putStringArrayListExtra("existingPages", ArrayList(pagePaths))
            putStringArrayListExtra(
                "existingCorners",
                ArrayList(pagePaths.map { p ->
                    EditorSession.corners[p]?.joinToString(",") { v -> v.toString() } ?: ""
                })
            )
            putExtra("idCardMode", idCompose)
            initialTemplateId?.let { putExtra("vaultTemplateId", it) }
            initialFolderId?.let { putExtra("folderId", it) }
        }
        startActivity(intent)
        finish()
    }

    private fun deleteCurrentPage() {
        val path = currentPath()
        EditorSession.corners.remove(path)
        EditorSession.filters.remove(path)
        EditorSession.rotations.remove(path)
        EditorSession.signatures.remove(path)
        pagePaths.removeAt(current)
        if (pagePaths.isEmpty()) {
            toast(tr(Str.TOAST_ALL_PAGES_DELETED))
            finish()
            return
        }
        showPage(minOf(current, pagePaths.size - 1))
    }

    /** Reorders the current page within this session (session state is path-keyed). */
    private fun moveCurrentPage(delta: Int) {
        val target = current + delta
        if (target !in pagePaths.indices) return
        val tmp = pagePaths[current]
        pagePaths[current] = pagePaths[target]
        pagePaths[target] = tmp
        showPage(target)
    }

    // ---- signature stamp ------------------------------------------------------

    /** Draws [sig] at the page's bottom-right (30% of page width, 3% margin). */
    private fun stampSignature(page: Bitmap, sig: Bitmap): Bitmap {
        if (sig.width <= 0 || sig.height <= 0) return page
        val out = page.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val targetW = page.width * 0.30f
        val targetH = sig.height * (targetW / sig.width)
        val margin = page.width * 0.03f
        val left = page.width - margin - targetW
        val top = page.height - margin - targetH
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sig, null, RectF(left, top, left + targetW, top + targetH), paint)
        return out
    }

    private fun showSignDialog() {
        val path = currentPath()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(4))
        }
        if (EditorSession.signatures.containsKey(path)) {
            container.addView(TextView(this).apply {
                text = tr(Str.SIGN_REMOVE)
                textSize = 14f
                setTextColor(palette.danger)
                setPadding(0, dp(2), 0, dp(10))
                setOnClickListener {
                    EditorSession.signatures.remove(path)
                    showPage(current)
                }
            })
        }
        val pad = SignatureView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(280)
            )
            // The pad stays paper-white in both themes: it is the signing surface.
            background = rounded(Color.WHITE, palette.radiusSmall, palette.cardBorder)
        }
        container.addView(pad)
        AlertDialog.Builder(this)
            .setTitle(tr(Str.SIGN_TITLE))
            .setView(container)
            .setPositiveButton(tr(Str.SIGN_DONE)) { _, _ ->
                val sig = pad.renderSignature()
                if (sig != null) {
                    EditorSession.signatures[path] = sig
                    showPage(current)
                } else if (pad.hasInk) {
                    toast(tr(Str.TOAST_SIGN_EMPTY))
                }
            }
            .setNegativeButton(tr(Str.BTN_CANCEL), null)
            .setNeutralButton(tr(Str.SIGN_CLEAR), null)
            .create()
            .also { dialog ->
                dialog.show()
                // Keep the dialog open on Clear so the user can redraw.
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { pad.clearPad() }
            }
    }

    /** Finger-drawing pad on a transparent background. */
    inner class SignatureView(context: Context) : View(context) {
        private val paths = mutableListOf<android.graphics.Path>()
        private var currentPath: android.graphics.Path? = null
        var hasInk = false
            private set
        private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(26, 26, 120)
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (p in paths) canvas.drawPath(p, inkPaint)
            currentPath?.let { canvas.drawPath(it, inkPaint) }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentPath = android.graphics.Path().apply { moveTo(event.x, event.y) }
                    hasInk = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentPath?.lineTo(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    currentPath?.let { paths.add(it) }
                    currentPath = null
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        fun clearPad() {
            paths.clear()
            currentPath = null
            hasInk = false
            invalidate()
        }

        /** Renders the ink, tightly cropped, on a transparent bitmap. */
        fun renderSignature(): Bitmap? {
            if (!hasInk || paths.isEmpty()) return null
            val bounds = RectF()
            for (p in paths) {
                val b = RectF()
                p.computeBounds(b, true)
                bounds.union(b)
            }
            if (bounds.width() <= 0f || bounds.height() <= 0f) return null
            val padPx = 14f
            val w = (bounds.width() + padPx * 2).toInt().coerceAtLeast(1)
            val h = (bounds.height() + padPx * 2).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.translate(-bounds.left + padPx, -bounds.top + padPx)
            for (p in paths) canvas.drawPath(p, inkPaint)
            return bmp
        }
    }

    // ---- save -----------------------------------------------------------------

    private fun showSaveDialog() {
        val folders = store.folders()
        val templates = VaultTemplates.all
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(4))
        }
        val defaultName = initialTemplateId?.let { id ->
            templates.firstOrNull { it.id == id }?.displayName
        } ?: tr(Str.SCAN_NAME_PREFIX) + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val nameInput = inputField(tr(Str.INPUT_DOC_NAME), palette).apply { setText(defaultName) }
        container.addView(nameInput)

        container.addView(smallText(tr(Str.SAVE_LABEL_FOLDER), palette).apply { setPadding(0, dp(12), 0, dp(4)) })
        val folderLabels = listOf(tr(Str.CHIP_ROOT)) + folders.map { it.name }
        val folderSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, folderLabels
            )
            initialFolderId?.let { fid ->
                val idx = folders.indexOfFirst { it.id == fid }
                if (idx >= 0) setSelection(idx + 1)
            }
        }
        container.addView(folderSpinner)

        container.addView(smallText(tr(Str.SAVE_LABEL_VAULT), palette).apply { setPadding(0, dp(12), 0, dp(4)) })
        val templateLabels = listOf(tr(Str.SAVE_NONE_TEMPLATE)) + templates.map { it.displayName }
        val templateSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, templateLabels
            )
            initialTemplateId?.let { tid ->
                val idx = templates.indexOfFirst { it.id == tid }
                if (idx >= 0) setSelection(idx + 1)
            }
        }
        container.addView(templateSpinner)

        container.addView(smallText(tr(Str.SAVE_LABEL_FORMAT), palette).apply { setPadding(0, dp(12), 0, dp(4)) })
        val formatGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val pdfRadio = RadioButton(this).apply { text = "PDF"; id = 1; isChecked = true }
        val jpgRadio = RadioButton(this).apply { text = tr(Str.RADIO_JPG); id = 2 }
        formatGroup.addView(pdfRadio)
        formatGroup.addView(jpgRadio)
        container.addView(formatGroup)

        AlertDialog.Builder(this)
            .setTitle(tr(Str.SAVE_DIALOG_TITLE))
            .setView(container)
            .setPositiveButton(tr(Str.BTN_SAVE)) { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty { defaultName }
                val folderId = if (folderSpinner.selectedItemPosition == 0) {
                    null
                } else {
                    folders[folderSpinner.selectedItemPosition - 1].id
                }
                val templateId = if (templateSpinner.selectedItemPosition == 0) {
                    null
                } else {
                    templates[templateSpinner.selectedItemPosition - 1].id
                }
                val createPdf = formatGroup.checkedRadioButtonId == 1
                processAndSave(name, folderId, templateId, createPdf)
            }
            .setNegativeButton(tr(Str.BTN_CANCEL), null)
            .show()
    }

    private fun processAndSave(name: String, folderId: String?, templateId: String?, createPdf: Boolean) {
        toast(tr(Str.TOAST_PROCESSING))
        val paths = pagePaths.toList()
        thread {
            try {
                val processedDir = File(sessionDir ?: cacheDir, "processed").apply { mkdirs() }
                val processed = mutableListOf<File>()
                paths.forEachIndexed { idx, path ->
                    val raw = ImageOps.loadBitmap(File(path), 2400)
                        ?: throw IllegalStateException(trf(Str.ERR_PAGE_LOAD, idx + 1))
                    val rotated = ImageOps.rotate(raw, EditorSession.rotations[path] ?: 0)
                    val warped = ImageOps.warp(rotated, cornersFor(path))
                    var filtered = ImageOps.applyFilter(
                        warped, EditorSession.filters[path] ?: PageFilter.ORIGINAL
                    )
                    // Burn the signature stamp into the page before save/export.
                    EditorSession.signatures[path]?.let { sig ->
                        filtered = stampSignature(filtered, sig)
                    }
                    val out = File(processedDir, "final_page${idx + 1}.jpg")
                    if (!ImageOps.saveJpeg(filtered, out)) {
                        throw IllegalStateException(trf(Str.ERR_PAGE_SAVE, idx + 1))
                    }
                    processed.add(out)
                }
                // ID-card flow: front + back composed onto a single A4 page.
                var finalPages: List<File> = processed
                if (idCompose && processed.size >= 2) {
                    val composed = composeIdPage(processed[0], processed[1], processedDir)
                    if (composed != null) {
                        finalPages = listOf(composed) + processed.drop(2)
                    }
                }
                val doc = store.importDocument(
                    name = name,
                    folderId = folderId,
                    pageSources = finalPages,
                    createPdf = createPdf,
                    vaultTemplateId = templateId,
                    nowMs = System.currentTimeMillis()
                )
                EditorSession.clear()
                runOnUiThread {
                    toast(trf(Str.TOAST_SAVED, doc.name))
                }
                // On-device OCR: searchable text in the index + a text layer in
                // the PDF. Then smart sync (no-op unless auto-sync is on).
                OcrHelper.recognizeAndAttach(this, store, doc.id, wantPdf = createPdf)
                SyncManager.onLocalChange(this)
                runOnUiThread {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread { toast(trf(Str.TOAST_SAVE_FAIL, e.message ?: "")) }
            }
        }
    }

    /**
     * Composes the front and back card images onto one white A4-proportioned
     * page (1240×1754): front in the top half, back in the bottom half, even
     * margins, aspect preserved. Returns the composed JPEG file.
     */
    private fun composeIdPage(frontFile: File, backFile: File, outDir: File): File? {
        val front = ImageOps.loadBitmap(frontFile, 2000) ?: return null
        val back = ImageOps.loadBitmap(backFile, 2000) ?: return null
        val pageW = 1240
        val pageH = 1754
        val margin = 60
        val slotH = (pageH - margin * 3) / 2
        val canvasBmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun drawFit(bmp: Bitmap, slotTop: Int) {
            val slotW = pageW - margin * 2
            val scale = minOf(slotW.toFloat() / bmp.width, slotH.toFloat() / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val left = margin + (slotW - dw) / 2f
            val top = slotTop + (slotH - dh) / 2f
            canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
        }
        drawFit(front, margin)
        drawFit(back, margin * 2 + slotH)
        val out = File(outDir, "id_combined.jpg")
        return if (ImageOps.saveJpeg(canvasBmp, out)) out else null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /**
     * Overlay that draws the page bitmap's fitted rect plus 4 draggable
     * corner handles (theme-accent circles on connecting lines).
     */
    inner class CornerView(context: Context) : View(context) {
        private var bitmap: Bitmap? = null
        private var corners: FloatArray = ImageOps.defaultCorners()
        private var dragging = -1

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            style = Paint.Style.FILL
        }
        private val handleCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        fun bind(bmp: Bitmap, cornerValues: FloatArray) {
            bitmap = bmp
            corners = cornerValues
            invalidate()
        }

        private fun fittedRect(): RectF? {
            val bmp = bitmap ?: return null
            val viewW = width.toFloat()
            val viewH = height.toFloat()
            if (viewW == 0f || viewH == 0f) return null
            val scale = minOf(viewW / bmp.width, viewH / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            val left = (viewW - w) / 2
            val top = (viewH - h) / 2
            return RectF(left, top, left + w, top + h)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rect = fittedRect() ?: return
            val pts = (0 until 4).map { i ->
                val x = rect.left + corners[i * 2] * rect.width()
                val y = rect.top + corners[i * 2 + 1] * rect.height()
                x to y
            }
            for (i in 0 until 4) {
                val a = pts[i]
                val b = pts[(i + 1) % 4]
                canvas.drawLine(a.first, a.second, b.first, b.second, linePaint)
            }
            for ((x, y) in pts) {
                canvas.drawCircle(x, y, dp(13).toFloat(), handlePaint)
                canvas.drawCircle(x, y, dp(6).toFloat(), handleCore)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val rect = fittedRect() ?: return super.onTouchEvent(event)
            val touchRadius = dp(30).toFloat()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = -1
                    var best = Float.MAX_VALUE
                    for (i in 0 until 4) {
                        val x = rect.left + corners[i * 2] * rect.width()
                        val y = rect.top + corners[i * 2 + 1] * rect.height()
                        val d = kotlin.math.hypot(event.x - x, event.y - y)
                        if (d < best) {
                            best = d
                            dragging = i
                        }
                    }
                    if (best > touchRadius) dragging = -1
                    return dragging >= 0
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging >= 0) {
                        val nx = ((event.x - rect.left) / rect.width()).coerceIn(0f, 1f)
                        val ny = ((event.y - rect.top) / rect.height()).coerceIn(0f, 1f)
                        corners[dragging * 2] = nx
                        corners[dragging * 2 + 1] = ny
                        EditorSession.corners[currentPath()] = corners
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = -1
                    // Dismissed warp preview resets to the corner-adjusted state on next draw;
                    // page preview itself updates when the user re-selects the page or saves.
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    }
}
