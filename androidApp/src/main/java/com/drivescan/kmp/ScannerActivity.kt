package com.drivescan.kmp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import java.io.File
import java.io.FileOutputStream

/**
 * Scanner: Camera2 live preview with a document/ID-card guide, torch,
 * gallery import and multi-page capture. Captured pages go to a session
 * folder; "Done" hands them to the Editor for crop/filter/save.
 */
class ScannerActivity : Activity() {

    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraId: String? = null
    private var sensorOrientation: Int = 0
    private var torchOn = false
    private var idCardMode = false

    private lateinit var textureView: TextureView
    private lateinit var guideView: GuideView
    private lateinit var countLabel: TextView
    private lateinit var modeButton: Button
    private lateinit var torchButton: Button

    private lateinit var sessionDir: File
    private val pageFiles = mutableListOf<File>()

    private val cameraThread = HandlerThread("camera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        val provided = intent.getStringExtra("sessionDir")
        sessionDir = if (provided != null) File(provided) else {
            File(cacheDir, "sessions/${System.currentTimeMillis()}").apply { mkdirs() }
        }
        // Pages from an earlier round of the same session (append mode).
        intent.getStringArrayListExtra("existingPages")?.forEach { pageFiles.add(File(it)) }
        setContentView(buildUi())
        updateCount()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (textureView.isAvailable) startCamera()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // startCamera is driven by the surface listener / onResume when ready
        }
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    override fun onDestroy() {
        cameraThread.quitSafely()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                if (textureView.isAvailable) startCamera()
            } else {
                toast(tr(Str.TOAST_NO_CAMERA_PERMISSION))
            }
        }
    }

    // ---- UI -----------------------------------------------------------------

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(10, 10, 14)) }

        textureView = TextureView(this)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) = startCamera()
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                closeCamera()
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
        root.addView(textureView, FrameLayout.LayoutParams(-1, -1))

        guideView = GuideView(this)
        root.addView(guideView, FrameLayout.LayoutParams(-1, -1))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val pill = TextView(this).apply {
            text = tr(Str.PILL_LOOKING)
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBg(Color.argb(170, 30, 30, 40), 20)
        }
        topBar.addView(pill)
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            background = roundedBg(Color.argb(170, 30, 30, 40), 24)
            setOnClickListener { finish() }
        }
        topBar.addView(closeBtn)
        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(18))
            setBackgroundColor(Color.argb(190, 12, 12, 18))
        }
        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeButton = smallDarkButton(tr(Str.BTN_DOC_MODE)) { toggleMode() }
        torchButton = smallDarkButton(tr(Str.BTN_TORCH_OFF)) { toggleTorch() }
        val galleryBtn = smallDarkButton(tr(Str.BTN_GALLERY)) { openGallery() }
        modes.addView(modeButton)
        modes.addView(torchButton)
        modes.addView(galleryBtn)
        bottom.addView(modes)

        val captureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        countLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        captureRow.addView(countLabel)
        val captureBtn = Button(this).apply {
            text = "◉"
            textSize = 30f
            setTextColor(Color.WHITE)
            background = roundedBg(Color.rgb(21, 101, 192), 40)
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            setOnClickListener { capturePhoto() }
        }
        captureRow.addView(captureBtn)
        val doneBtn = Button(this).apply {
            text = tr(Str.BTN_DONE)
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = roundedBg(Color.rgb(46, 125, 50), 16)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(14) }
            setOnClickListener { goToEditor() }
        }
        captureRow.addView(doneBtn)
        bottom.addView(captureRow)
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return root
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable {
        val d = android.graphics.drawable.GradientDrawable()
        d.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        d.cornerRadius = radiusDp * resources.displayMetrics.density
        d.setColor(color)
        return d
    }

    private fun smallDarkButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBg(Color.argb(140, 50, 52, 66), 14)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun updateCount() {
        countLabel.text = when {
            pageFiles.isEmpty() -> tr(Str.COUNT_NONE)
            pageFiles.size == 1 -> tr(Str.COUNT_ONE)
            else -> trf(Str.COUNT_MANY, pageFiles.size)
        }
    }

    // ---- camera -------------------------------------------------------------

    private fun startCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        if (cameraDevice != null) return
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val id = manager.cameraIdList.firstOrNull { cid ->
                manager.getCameraCharacteristics(cid)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()
            if (id == null) {
                toast(tr(Str.TOAST_NO_CAMERA_FOUND))
                return
            }
            cameraId = id
            val chars = manager.getCameraCharacteristics(id)
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSize = map?.getOutputSizes(ImageFormat.JPEG)
                ?.filter { it.width <= 4096 }
                ?.maxByOrNull { it.width * it.height }
            val reader = ImageReader.newInstance(
                jpegSize?.width ?: 1920, jpegSize?.height ?: 1080, ImageFormat.JPEG, 2
            )
            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val file = File(sessionDir, "page_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { it.write(bytes) }
                    pageFiles.add(file)
                    runOnUiThread {
                        updateCount()
                        toast(trf(Str.TOAST_PAGE_CAPTURED, pageFiles.size))
                    }
                } catch (e: Exception) {
                    runOnUiThread { toast(trf(Str.TOAST_CAPTURE_SAVE_FAIL, e.message ?: "")) }
                } finally {
                    image.close()
                }
            }, cameraHandler)
            imageReader = reader

            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    createSession()
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    cameraDevice = null
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    cameraDevice = null
                    runOnUiThread { toast(trf(Str.TOAST_CAMERA_OPEN_FAIL, error)) }
                }
            }, cameraHandler)
        } catch (e: Exception) {
            toast(trf(Str.TOAST_CAMERA_ERROR, e.message ?: ""))
        }
    }

    private fun createSession() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(1440, 1080)
        val previewSurface = Surface(texture)
        try {
            device.createCaptureSession(
                listOf(previewSurface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(sess: CameraCaptureSession) {
                        session = sess
                        try {
                            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                            }
                            sess.setRepeatingRequest(req.build(), null, cameraHandler)
                        } catch (e: Exception) {
                            // session may have closed mid-setup; ignore
                        }
                    }
                    override fun onConfigureFailed(sess: CameraCaptureSession) {
                        runOnUiThread { toast(tr(Str.TOAST_PREVIEW_FAIL)) }
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            toast(trf(Str.TOAST_SESSION_ERROR, e.message ?: ""))
        }
    }

    private fun capturePhoto() {
        val device = cameraDevice
        val sess = session
        val reader = imageReader
        if (device == null || sess == null || reader == null) {
            toast(tr(Str.TOAST_CAMERA_NOT_READY_CAPTURE))
            return
        }
        try {
            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
            }
            sess.capture(req.build(), null, cameraHandler)
        } catch (e: Exception) {
            toast(trf(Str.TOAST_CAPTURE_FAIL, e.message ?: ""))
        }
    }

    private fun jpegOrientation(): Int {
        val rotation = windowManager.defaultDisplay.rotation
        val degrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (sensorOrientation - degrees + 360) % 360
    }

    private fun closeCamera() {
        try {
            session?.close()
            cameraDevice?.close()
            imageReader?.close()
        } catch (e: Exception) {
            // already closed
        }
        session = null
        cameraDevice = null
        imageReader = null
    }

    private fun toggleTorch() {
        val id = cameraId ?: run {
            toast(tr(Str.TOAST_CAMERA_NOT_READY))
            return
        }
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchOn = !torchOn
            manager.setTorchMode(id, torchOn)
            torchButton.text = if (torchOn) tr(Str.BTN_TORCH_ON) else tr(Str.BTN_TORCH_OFF)
        } catch (e: Exception) {
            toast(tr(Str.TOAST_TORCH_UNAVAILABLE))
            torchOn = false
        }
    }

    private fun toggleMode() {
        idCardMode = !idCardMode
        modeButton.text = if (idCardMode) tr(Str.BTN_ID_MODE) else tr(Str.BTN_DOC_MODE)
        guideView.idCardMode = idCardMode
        guideView.invalidate()
    }

    // ---- gallery + navigation ----------------------------------------------

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQ_GALLERY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_GALLERY || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<android.net.Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        }
        data.data?.let { if (uris.isEmpty()) uris.add(it) }
        var added = 0
        for (uri in uris) {
            try {
                val file = File(sessionDir, "import_${System.currentTimeMillis()}_$added.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { input.copyTo(it) }
                }
                if (file.exists() && file.length() > 0) {
                    pageFiles.add(file)
                    added++
                }
            } catch (e: Exception) {
                // skip unreadable picks
            }
        }
        updateCount()
        toast(
            when {
                added == 1 -> tr(Str.IMPORT_ONE)
                added > 1 -> trf(Str.IMPORT_MANY, added)
                else -> tr(Str.TOAST_IMPORT_FAIL)
            }
        )
    }

    private fun goToEditor() {
        if (pageFiles.isEmpty()) {
            toast(tr(Str.TOAST_NEED_ONE_PAGE))
            return
        }
        val intent = Intent(this, EditorActivity::class.java).apply {
            putStringArrayListExtra("pages", ArrayList(pageFiles.map { it.absolutePath }))
            putExtra("sessionDir", sessionDir.absolutePath)
            intent.getStringExtra("vaultTemplateId")?.let { putExtra("vaultTemplateId", it) }
            intent.getStringExtra("folderId")?.let { putExtra("folderId", it) }
        }
        startActivity(intent)
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Guide overlay: a stroked rounded rectangle (full doc, or card ratio in ID mode). */
    class GuideView(context: Context) : View(context) {
        var idCardMode: Boolean = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 120, 200, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val rect = if (idCardMode) {
                val w = width * 0.86f
                val h = w / 1.586f
                RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            } else {
                val mx = width * 0.07f
                val my = height * 0.16f
                RectF(mx, my, width - mx, height - my)
            }
            canvas.drawRoundRect(rect, 24f, 24f, paint)
        }
    }

    companion object {
        private const val REQ_CAMERA = 41
        private const val REQ_GALLERY = 42
    }
}
