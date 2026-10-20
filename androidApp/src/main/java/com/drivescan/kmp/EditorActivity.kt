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
import com.drivescan.kmp.auth.AuthManager
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.drive.DriveSync
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
    fun clear() {
        corners.clear()
        filters.clear()
        rotations.clear()
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
            setBackgroundColor(Color.rgb(16, 16, 22))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        top.addView(TextView(this).apply {
            text = tr(Str.EDITOR_TITLE)
            setTextColor(Color.WHITE)
            textSize = 17f
        })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        pageLabel = TextView(this).apply {
            setTextColor(Color.rgb(180, 185, 200))
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
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(44, 46, 60), 12)
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
        val filtered = ImageOps.applyFilter(rotated, EditorSession.filters[path] ?: PageFilter.ORIGINAL)
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
                    if (idx == current) palette.primary else Color.rgb(50, 52, 66), 8
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
        pagePaths.removeAt(current)
        if (pagePaths.isEmpty()) {
            toast(tr(Str.TOAST_ALL_PAGES_DELETED))
            finish()
            return
        }
        showPage(minOf(current, pagePaths.size - 1))
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
                    val filtered = ImageOps.applyFilter(
                        warped, EditorSession.filters[path] ?: PageFilter.ORIGINAL
                    )
                    val out = File(processedDir, "final_page${idx + 1}.jpg")
                    if (!ImageOps.saveJpeg(filtered, out)) {
                        throw IllegalStateException(trf(Str.ERR_PAGE_SAVE, idx + 1))
                    }
                    processed.add(out)
                }
                val doc = store.importDocument(
                    name = name,
                    folderId = folderId,
                    pageSources = processed,
                    createPdf = createPdf,
                    vaultTemplateId = templateId,
                    nowMs = System.currentTimeMillis()
                )
                EditorSession.clear()
                runOnUiThread {
                    toast(trf(Str.TOAST_SAVED, doc.name))
                }
                if (AuthManager.isConnected(this)) {
                    try {
                        DriveSync.syncNow(this)
                    } catch (e: Exception) {
                        // Sync failure never blocks a local save; Settings → Sync now retries.
                    }
                }
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /**
     * Overlay that draws the page bitmap's fitted rect plus 4 draggable
     * corner handles (cyan circles on connecting lines).
     */
    inner class CornerView(context: Context) : View(context) {
        private var bitmap: Bitmap? = null
        private var corners: FloatArray = ImageOps.defaultCorners()
        private var dragging = -1

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(64, 196, 255)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(64, 196, 255)
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
