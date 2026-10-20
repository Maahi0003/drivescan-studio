package com.drivescan.kmp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.drive.DriveSync
import com.drivescan.kmp.scan.ImageOps
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import com.drivescan.shared.ScanDocument
import kotlin.concurrent.thread

/**
 * Document viewer: swipe/prev-next through page images, open or share the
 * PDF through ShareProvider, download on demand for Drive-only documents,
 * and move to bin.
 */
class DocViewerActivity : Activity() {

    private val store by lazy { PersistentStore.get(this) }
    private var doc: ScanDocument? = null
    private var pageIndex = 0

    private lateinit var imageView: ImageView
    private lateinit var indicator: TextView
    private lateinit var messageView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        val docId = intent.getStringExtra("docId")
        doc = docId?.let { store.documentById(it) }
        if (doc == null) {
            // Binned docs aren't in documents(); look there too.
            doc = docId?.let { id -> store.binDocuments().firstOrNull { it.id == id } }
        }
        if (doc == null) {
            toast(tr(Str.TOAST_DOC_NOT_FOUND))
            finish()
            return
        }
        setContentView(buildUi())
        renderPage()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 14, 18))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val back = Button(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            background = null
            textSize = 20f
            setOnClickListener { finish() }
        }
        top.addView(back)
        top.addView(TextView(this).apply {
            text = doc?.name ?: "Document"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        indicator = TextView(this).apply {
            setTextColor(Color.rgb(170, 175, 190))
            textSize = 13f
        }
        top.addView(indicator)
        root.addView(top)

        val stage = FrameLayout(this)
        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        stage.addView(imageView, FrameLayout.LayoutParams(-1, -1))
        messageView = TextView(this).apply {
            setTextColor(Color.rgb(200, 205, 220))
            textSize = 14.5f
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(20), dp(28), dp(20))
            visibility = View.GONE
        }
        stage.addView(messageView, FrameLayout.LayoutParams(-1, -1))
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(2))
        }
        nav.addView(darkButton(tr(Str.BTN_PREV)) { step(-1) })
        nav.addView(darkButton(tr(Str.BTN_NEXT)) { step(1) })
        root.addView(nav)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(14))
        }
        actions.addView(darkButton(tr(Str.BTN_SHARE)) { shareCurrent() })
        val current = doc
        if (current?.pdfFile != null) {
            actions.addView(darkButton(tr(Str.BTN_OPEN_PDF)) { openPdf() })
        }
        if (current != null && current.pageFiles.isEmpty() && current.pdfFile == null && current.driveId != null) {
            actions.addView(darkButton(tr(Str.BTN_DOWNLOAD)) { downloadFromDrive() })
        }
        if (current != null && !current.inBin) {
            actions.addView(darkButton(tr(Str.BTN_BIN)) {
                store.moveToBin(current.id)
                toast(tr(Str.TOAST_MOVED_TO_BIN_SHORT))
                finish()
            })
        }
        root.addView(actions)

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (kotlin.math.abs(velocityX) > kotlin.math.abs(velocityY) && kotlin.math.abs(velocityX) > 120) {
                    step(if (velocityX < 0) 1 else -1)
                    return true
                }
                return false
            }
        })
        imageView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }
        return root
    }

    private fun darkButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13.5f
            isAllCaps = false
            setTextColor(Color.WHITE)
            val d = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.rgb(44, 46, 60))
            }
            background = d
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun step(delta: Int) {
        val d = doc ?: return
        if (d.pageFiles.isEmpty()) return
        pageIndex = (pageIndex + delta).coerceIn(0, d.pageFiles.size - 1)
        renderPage()
    }

    private fun renderPage() {
        val d = doc ?: return
        when {
            d.pageFiles.isNotEmpty() -> {
                messageView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                val file = store.pageFile(d, pageIndex)
                val bmp = file?.let { ImageOps.loadBitmap(it, 1800) }
                if (bmp != null) imageView.setImageBitmap(bmp)
                indicator.text = "${pageIndex + 1} / ${d.pageFiles.size}"
            }
            d.pdfFile != null -> {
                imageView.visibility = View.GONE
                messageView.visibility = View.VISIBLE
                messageView.text = tr(Str.VIEWER_PDF_MSG)
                indicator.text = "PDF"
            }
            else -> {
                imageView.visibility = View.GONE
                messageView.visibility = View.VISIBLE
                messageView.text = tr(Str.VIEWER_DRIVE_MSG)
                indicator.text = tr(Str.INDICATOR_DRIVE)
            }
        }
    }

    private fun shareCurrent() {
        val d = doc ?: return
        val pdf = store.pdfFile(d)
        val page = store.pageFile(d, pageIndex)
        val (fileName, mime) = when {
            pdf != null -> pdf.name to "application/pdf"
            page != null -> page.name to "image/jpeg"
            else -> {
                toast(tr(Str.TOAST_SHARE_NO_LOCAL))
                return
            }
        }
        val uri = ShareProvider.uriFor(d.id, fileName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, d.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, trf(Str.SHARE_CHOOSER, d.name)))
    }

    private fun openPdf() {
        val d = doc ?: return
        val pdf = store.pdfFile(d) ?: return
        val uri = ShareProvider.uriFor(d.id, pdf.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(tr(Str.TOAST_NO_PDF_APP))
        }
    }

    private fun downloadFromDrive() {
        val d = doc ?: return
        toast(tr(Str.TOAST_DOWNLOADING))
        thread {
            val ok = try {
                DriveSync.downloadDocument(this, d)
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (ok) {
                    doc = store.documentById(d.id)
                    toast(tr(Str.TOAST_DOWNLOADED))
                    recreate()
                } else {
                    toast(tr(Str.TOAST_DOWNLOAD_FAIL))
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
