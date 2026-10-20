package com.drivescan.kmp.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.sqrt

enum class PageFilter { ORIGINAL, GRAY, BW, ENHANCE }

/** Pure bitmap operations for the scan editor: decode, rotate, perspective warp, filters. */
object ImageOps {

    /** Decodes [file] downsampled so the long edge is at most [maxDim] px. */
    fun loadBitmap(file: File, maxDim: Int = 2400): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / sample > maxDim) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Default crop corners (normalized 0..1, TL TR BR BL), inset 6% from edges. */
    fun defaultCorners(): FloatArray = floatArrayOf(
        0.06f, 0.06f, 0.94f, 0.06f, 0.94f, 0.94f, 0.06f, 0.94f
    )

    /**
     * Perspective-rectifies [src] using normalized corners (TL, TR, BR, BL)
     * via Matrix.setPolyToPoly. Output size comes from the quad's edge lengths.
     */
    fun warp(src: Bitmap, cornersNorm: FloatArray): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val srcPts = floatArrayOf(
            cornersNorm[0] * w, cornersNorm[1] * h,
            cornersNorm[2] * w, cornersNorm[3] * h,
            cornersNorm[4] * w, cornersNorm[5] * h,
            cornersNorm[6] * w, cornersNorm[7] * h
        )
        fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            return sqrt(dx * dx + dy * dy)
        }
        val outW = max(
            dist(srcPts[0], srcPts[1], srcPts[2], srcPts[3]),
            dist(srcPts[6], srcPts[7], srcPts[4], srcPts[5])
        ).toInt().coerceIn(64, 4000)
        val outH = max(
            dist(srcPts[0], srcPts[1], srcPts[6], srcPts[7]),
            dist(srcPts[2], srcPts[3], srcPts[4], srcPts[5])
        ).toInt().coerceIn(64, 4000)
        val dstPts = floatArrayOf(
            0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat()
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) return src
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return out
    }

    fun applyFilter(src: Bitmap, filter: PageFilter): Bitmap {
        return when (filter) {
            PageFilter.ORIGINAL -> src
            PageFilter.GRAY -> colorMatrix(src, ColorMatrix().apply { setSaturation(0f) })
            PageFilter.ENHANCE -> colorMatrix(
                src,
                ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -18f,
                        0f, 1.25f, 0f, 0f, -18f,
                        0f, 0f, 1.25f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            PageFilter.BW -> threshold(colorMatrix(src, ColorMatrix().apply { setSaturation(0f) }))
        }
    }

    private fun colorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Hard black/white threshold on a grayscale bitmap (for crisp document text). */
    private fun threshold(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (0.299 * ((p shr 16) and 0xFF) +
                0.587 * ((p shr 8) and 0xFF) +
                0.114 * (p and 0xFF))
            val v = if (lum > 150) 255 else 0
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 88): Boolean =
        try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            true
        } catch (e: Exception) {
            false
        }
}
