package com.drivescan.kmp.data

import android.content.Context
import android.net.Uri
import com.drivescan.kmp.scan.ImageOps
import java.io.File

/**
 * Local-only user profile for the Dashboard profile corner (v1.3.0).
 *
 * Deliberately NOT tied to any account: the name lives in SharedPreferences
 * and the optional photo is a downscaled JPEG in app-private storage
 * (filesDir/profile/profile.jpg), picked through the system document
 * picker. No Google scopes, no network, no auth involvement.
 */
object ProfileStore {

    private const val PREFS = "drivescan_profile"
    private const val KEY_NAME = "profile_name"

    fun name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null)?.trim().orEmpty()

    fun setName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_NAME, name.trim()).apply()
    }

    fun photoFile(context: Context): File =
        File(File(context.filesDir, "profile").apply { mkdirs() }, "profile.jpg")

    fun hasPhoto(context: Context): Boolean = photoFile(context).exists()

    /**
     * Copies the picked image into private storage, downscaled so its long
     * edge is at most 512px, as a JPEG. Call from a background thread.
     * Returns true on success.
     */
    fun savePhoto(context: Context, uri: Uri): Boolean {
        return try {
            val tmp = File(context.cacheDir, "profile_pick_${System.currentTimeMillis()}")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
                true
            } ?: false
            if (!copied || !tmp.exists() || tmp.length() == 0L) return false
            val bmp = ImageOps.loadBitmap(tmp, 512) ?: return false
            val ok = ImageOps.saveJpeg(bmp, photoFile(context), 88)
            tmp.delete()
            ok
        } catch (e: Exception) {
            false
        }
    }

    fun removePhoto(context: Context) {
        try {
            photoFile(context).delete()
        } catch (e: Exception) {
            // Nothing to remove.
        }
    }
}
