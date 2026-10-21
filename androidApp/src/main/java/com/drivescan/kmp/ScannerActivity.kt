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
import com.drivescan.kmp.scan.DocDetector
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Palette
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.palette
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
    private var analysisReader: ImageReader? = null
    private var cameraId: String? = null
    private var sensorOrientation: Int = 0
    private var torchOn = false
    private var idCardMode = false

    private val palette: Palette by lazy { palette() }

    private lateinit var textureView: TextureView
    private lateinit var guideView: GuideView
    private lateinit var countLabel: TextView
    private lateinit var pillLabel: TextView
    private lateinit var modeButton: Button
    private lateinit var torchButton: Button
    private lateinit var autoButton: Button

    private lateinit var sessionDir: File
    private val pageFiles = mutableListOf<File>()

    /** Normalized detected corners per page ("" = none), parallel to pageFiles. */
    private val pageCorners = mutableListOf<String>()

    // --- auto-capture state (detection runs on the camera handler thread) ---
    private var autoCapture = true
    private val pendingLock = Any()
    private val pendingCorners = mutableListOf<FloatArray?>()
    private var lastQuad: FloatArray? = null
    private var stableSinceMs = 0L
    private var lastAutoCaptureMs = 0L
    private var lastAnalysisMs = 0L
    private var captureInFlight = false

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
        val existingCorners = intent.getStringArrayListExtra("existingCorners")
        intent.getStringArrayListExtra("existingPages")?.forEachIndexed { idx, path ->
            pageFiles.add(File(path))
            pageCorners.add(existingCorners?.getOrNull(idx) ?: "")
        }
        // ID-card mode can be requested by the caller (vault ID templates).
        idCardMode = intent.getBooleanExtra("idCardMode", false)
        autoCapture = getSharedPreferences("drivescan_prefs", MODE_PRIVATE)
            .getBoolean("auto_capture", true)
        setContentView(buildUi())
        if (idCardMode) {
            modeButton.text = tr(Str.BTN_ID_MODE)
            guideView.idCardMode = true
        }
        autoButton.text = if (autoCapture) tr(Str.BTN_AUTO_ON) else tr(Str.BTN_AUTO_OFF)
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

    /** Themed chrome colors, derived from the active palette (v1.2.0):
     *  Midnight charcoal surfaces in dark mode, Gulmohar warm surfaces in light. */
    private val chromeText: Int get() = palette.text
    private val chromePillBg: Int
        get() = if (palette.dark) Color.argb(170, 21, 26, 33) else Color.argb(190, 255, 255, 255)
    private val chromeBarBg: Int
        get() = if (palette.dark) Color.argb(190, 12, 15, 20) else Color.argb(215, 255, 247, 236)
    private val chromeBtnBg: Int
        get() = if (palette.dark) Color.argb(160, 27, 34, 44) else Color.argb(170, 255, 233, 219)

    private fun buildUi(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(if (palette.dark) palette.scannerBg else palette.bg)
        }

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
        guideView.accentColor = palette.primary
        root.addView(guideView, FrameLayout.LayoutParams(-1, -1))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        pillLabel = TextView(this).apply {
            text = tr(Str.PILL_LOOKING)
            setTextColor(chromeText)
            textSize = 13.5f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBg(chromePillBg, 20)
        }
        topBar.addView(pillLabel)
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(chromeText)
            background = roundedBg(chromePillBg, 24)
            setOnClickListener { finish() }
        }
        topBar.addView(closeBtn)
        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(18))
            setBackgroundColor(chromeBarBg)
        }
        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeButton = smallDarkButton(tr(Str.BTN_DOC_MODE)) { toggleMode() }
        torchButton = smallDarkButton(tr(Str.BTN_TORCH_OFF)) { toggleTorch() }
        autoButton = smallDarkButton(tr(Str.BTN_AUTO_ON)) { toggleAutoCapture() }
        val galleryBtn = smallDarkButton(tr(Str.BTN_GALLERY)) { openGallery() }
        modes.addView(modeButton)
        modes.addView(torchButton)
        modes.addView(autoButton)
        modes.addView(galleryBtn)
        bottom.addView(modes)

        val captureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        countLabel = TextView(this).apply {
            setTextColor(chromeText)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        captureRow.addView(countLabel)
        val captureBtn = Button(this).apply {
            text = "◉"
            textSize = 30f
            setTextColor(palette.onPrimary)
            background = roundedBg(palette.primary, 40)
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            setOnClickListener { capturePhoto(null) }
        }
        captureRow.addView(captureBtn)
        val doneBtn = Button(this).apply {
            text = tr(Str.BTN_DONE)
            setTextColor(palette.onSuccess)
            isAllCaps = false
            background = roundedBg(palette.success, palette.radius)
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
            setTextColor(chromeText)
            background = roundedBg(chromeBtnBg, palette.radius)
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
        refreshPill()
    }

    /** The top pill: ID-mode prompts, otherwise the "looking for document" hint. */
    private fun refreshPill() {
        if (!::pillLabel.isInitialized) return
        pillLabel.text = when {
            idCardMode && pageFiles.isEmpty() -> tr(Str.ID_FRONT_PROMPT)
            idCardMode && pageFiles.size == 1 -> tr(Str.ID_BACK_PROMPT)
            idCardMode -> tr(Str.ID_BOTH_DONE)
            else -> tr(Str.PILL_LOOKING)
        }
    }

    private fun toggleAutoCapture() {
        autoCapture = !autoCapture
        getSharedPreferences("drivescan_prefs", MODE_PRIVATE)
            .edit().putBoolean("auto_capture", autoCapture).apply()
        autoButton.text = if (autoCapture) tr(Str.BTN_AUTO_ON) else tr(Str.BTN_AUTO_OFF)
        if (!autoCapture) {
            stableSinceMs = 0L
            lastQuad = null
            guideView.detectedQuad = null
            guideView.invalidate()
            refreshPill()
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
                    // Pair this capture with the corners queued by capturePhoto().
                    val corners = synchronized(pendingLock) {
                        if (pendingCorners.isNotEmpty()) pendingCorners.removeAt(0) else null
                    }
                    pageCorners.add(encodeCorners(corners))
                    runOnUiThread {
                        captureInFlight = false
                        updateCount()
                        toast(trf(Str.TOAST_PAGE_CAPTURED, pageFiles.size))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        captureInFlight = false
                        toast(trf(Str.TOAST_CAPTURE_SAVE_FAIL, e.message ?: ""))
                    }
                } finally {
                    image.close()
                }
            }, cameraHandler)
            imageReader = reader

            // Low-res YUV stream for on-device document detection (auto-capture).
            val analysisSize = map?.getOutputSizes(ImageFormat.YUV_420_888)
                ?.filter { it.width <= 1280 }
                ?.maxByOrNull { it.width * it.height }
            if (analysisSize != null) {
                val analysis = ImageReader.newInstance(
                    analysisSize.width, analysisSize.height, ImageFormat.YUV_420_888, 2
                )
                analysis.setOnImageAvailableListener({ r ->
                    val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        onAnalysisImage(image)
                    } catch (e: Exception) {
                        // Detection is best-effort; manual capture is unaffected.
                    } finally {
                        image.close()
                    }
                }, cameraHandler)
                analysisReader = analysis
            }

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
        val surfaces = mutableListOf(previewSurface, reader.surface)
        analysisReader?.let { surfaces.add(it.surface) }
        try {
            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(sess: CameraCaptureSession) {
                        session = sess
                        try {
                            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                analysisReader?.let { addTarget(it.surface) }
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

    /**
     * Captures one page. [corners] are the detected document corners to seed
     * the Editor with (auto-capture), or null for a plain manual capture.
     */
    private fun capturePhoto(corners: FloatArray?) {
        val device = cameraDevice
        val sess = session
        val reader = imageReader
        if (device == null || sess == null || reader == null) {
            toast(tr(Str.TOAST_CAMERA_NOT_READY_CAPTURE))
            return
        }
        if (idCardMode && pageFiles.size >= 2) {
            toast(tr(Str.ID_BOTH_DONE))
            return
        }
        if (captureInFlight) return
        captureInFlight = true
        synchronized(pendingLock) { pendingCorners.add(corners) }
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
            captureInFlight = false
            synchronized(pendingLock) {
                if (pendingCorners.isNotEmpty()) pendingCorners.removeAt(pendingCorners.size - 1)
            }
            toast(trf(Str.TOAST_CAPTURE_FAIL, e.message ?: ""))
        }
    }

    private fun encodeCorners(corners: FloatArray?): String =
        corners?.joinToString(",") { it.toString() } ?: ""

    // ---- auto edge detection + auto-capture ----------------------------------

    /** Downscales the Y plane to ~192px wide and runs the quad detector. */
    private fun onAnalysisImage(image: android.media.Image) {
        if (!autoCapture) return
        val now = System.currentTimeMillis()
        if (now - lastAnalysisMs < 120) return
        lastAnalysisMs = now
        val plane = image.planes.firstOrNull() ?: return
        val imgW = image.width
        val imgH = image.height
        if (imgW < 32 || imgH < 32) return
        val targetW = 192
        val stepX = maxOf(1, imgW / targetW)
        val outW = imgW / stepX
        val stepY = maxOf(1, (imgH * stepX) / imgW)
        val outH = imgH / stepY
        if (outW < 16 || outH < 16) return
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val gray = IntArray(outW * outH)
        for (y in 0 until outH) {
            val srcRow = y * stepY * rowStride
            for (x in 0 until outW) {
                val idx = srcRow + x * stepX * pixelStride
                gray[y * outW + x] = if (idx in 0 until buffer.capacity()) {
                    buffer.get(idx).toInt() and 0xFF
                } else {
                    0
                }
            }
        }
        handleDetection(DocDetector.detect(gray, outW, outH))
    }

    private fun handleDetection(quad: FloatArray?) {
        val now = System.currentTimeMillis()
        if (quad == null) {
            stableSinceMs = 0L
            lastQuad = null
            runOnUiThread {
                if (guideView.detectedQuad != null) {
                    guideView.detectedQuad = null
                    guideView.invalidate()
                }
                if (autoCapture) refreshPill()
            }
            return
        }
        val prev = lastQuad
        if (prev != null && quadsSimilar(prev, quad)) {
            if (stableSinceMs == 0L) stableSinceMs = now
            runOnUiThread {
                guideView.detectedQuad = transformForView(quad)
                guideView.invalidate()
                if (autoCapture) pillLabel.text = tr(Str.PILL_DETECTED)
            }
            val stableLongEnough = now - stableSinceMs >= 900
            val cooldownOver = now - lastAutoCaptureMs >= 1500
            if (stableLongEnough && cooldownOver && !captureInFlight) {
                lastAutoCaptureMs = now
                stableSinceMs = 0L
                lastQuad = null
                val snapshot = quad.copyOf()
                runOnUiThread { capturePhoto(snapshot) }
            }
        } else {
            lastQuad = quad
            stableSinceMs = now
            runOnUiThread {
                guideView.detectedQuad = transformForView(quad)
                guideView.invalidate()
            }
        }
    }

    private fun quadsSimilar(a: FloatArray, b: FloatArray): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (kotlin.math.abs(a[i] - b[i]) > 0.035f) return false
        }
        return true
    }

    /**
     * Detection runs in raw buffer space (which is also the saved JPEG's
     * pixel space — what the Editor expects). The preview the user sees is
     * rotated upright by the display transform, so the overlay quad is
     * rotated by the same amount for drawing only.
     */
    private fun transformForView(quad: FloatArray): FloatArray {
        val rotation = jpegOrientation()
        if (rotation == 0) return quad
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val x = quad[i * 2]
            val y = quad[i * 2 + 1]
            val (nx, ny) = when (rotation) {
                90 -> (1f - y) to x
                180 -> (1f - x) to (1f - y)
                270 -> y to (1f - x)
                else -> x to y
            }
            out[i * 2] = nx
            out[i * 2 + 1] = ny
        }
        return out
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
            analysisReader?.close()
        } catch (e: Exception) {
            // already closed
        }
        session = null
        cameraDevice = null
        imageReader = null
        analysisReader = null
        captureInFlight = false
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
        updateCount()
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
            if (idCardMode && pageFiles.size >= 2) break
            try {
                val file = File(sessionDir, "import_${System.currentTimeMillis()}_$added.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { input.copyTo(it) }
                }
                if (file.exists() && file.length() > 0) {
                    pageFiles.add(file)
                    pageCorners.add("")
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
        if (idCardMode && pageFiles.size < 2) {
            toast(tr(Str.ID_NEED_BOTH))
            return
        }
        val intent = Intent(this, EditorActivity::class.java).apply {
            putStringArrayListExtra("pages", ArrayList(pageFiles.map { it.absolutePath }))
            putStringArrayListExtra("pageCorners", ArrayList(pageCorners))
            putExtra("sessionDir", sessionDir.absolutePath)
            putExtra("idCompose", idCardMode)
            intent.getStringExtra("vaultTemplateId")?.let { putExtra("vaultTemplateId", it) }
            intent.getStringExtra("folderId")?.let { putExtra("folderId", it) }
        }
        startActivity(intent)
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Guide overlay: a stroked rounded rectangle (full doc, or card ratio in ID mode),
     *  plus the live detected document quad when auto-capture sees one.
     *  Frame and quad are drawn in the theme accent (lime in Midnight, coral in Gulmohar). */
    class GuideView(context: Context) : View(context) {
        var idCardMode: Boolean = false
        var detectedQuad: FloatArray? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 120, 200, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val quadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(76, 220, 120)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        private val quadDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(76, 220, 120)
            style = Paint.Style.FILL
        }

        /** Theme accent for the guide frame (soft) and the detected quad (solid). */
        var accentColor: Int = Color.rgb(76, 220, 120)
            set(value) {
                field = value
                paint.color = Color.argb(
                    230, Color.red(value), Color.green(value), Color.blue(value)
                )
                quadPaint.color = value
                quadDot.color = value
                invalidate()
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
            val quad = detectedQuad
            if (quad != null && quad.size == 8 && width > 0 && height > 0) {
                val pts = (0 until 4).map { i ->
                    (quad[i * 2] * width) to (quad[i * 2 + 1] * height)
                }
                for (i in 0 until 4) {
                    val a = pts[i]
                    val b = pts[(i + 1) % 4]
                    canvas.drawLine(a.first, a.second, b.first, b.second, quadPaint)
                }
                for ((x, y) in pts) canvas.drawCircle(x, y, 10f, quadDot)
            }
        }
    }

    companion object {
        private const val REQ_CAMERA = 41
        private const val REQ_GALLERY = 42
    }
}
