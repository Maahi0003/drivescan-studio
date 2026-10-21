package com.drivescan.kmp

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Minimal dependency-free replacement for androidx FileProvider.
 * Serves ONLY files inside two app-private roots, each path-canonicalized:
 *  - the scans directory as content://com.drivescan.kmp.share/<docId>/<fileName>
 *    (PDFs and page images shared/viewed by other apps with a URI grant), and
 *  - the updates cache directory as content://com.drivescan.kmp.share/updates/<fileName>
 *    (a downloaded update APK handed to the system package installer).
 */
class ShareProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun resolve(uri: Uri): File {
        val segments = uri.pathSegments
        if (segments.size < 2) throw FileNotFoundException("Bad uri: $uri")
        val ctx = context ?: throw FileNotFoundException("No context")
        val base: File
        val file: File
        if (segments[0] == UPDATES_SEGMENT) {
            base = File(ctx.cacheDir, "updates").canonicalFile
            file = File(base, segments[1]).canonicalFile
            if (!file.path.startsWith(base.path)) throw FileNotFoundException("Outside updates dir")
        } else if (segments[0] == SECURED_SEGMENT) {
            base = File(ctx.cacheDir, "secured").canonicalFile
            file = File(base, segments[1]).canonicalFile
            if (!file.path.startsWith(base.path)) throw FileNotFoundException("Outside secured dir")
        } else {
            base = File(ctx.filesDir, "scans").canonicalFile
            file = File(base, "${segments[0]}/${segments[1]}").canonicalFile
            if (!file.path.startsWith(base.path)) throw FileNotFoundException("Outside scans dir")
        }
        if (!file.exists()) throw FileNotFoundException("Not found: $uri")
        return file
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = resolve(uri)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
        "pdf" -> "application/pdf"
        "apk" -> "application/vnd.android.package-archive"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val file = try {
            resolve(uri)
        } catch (e: Exception) {
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        }
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        cursor.addRow(arrayOf(file.name, file.length()))
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.drivescan.kmp.share"

        /** Reserved first path segment for files in the updates cache dir. */
        const val UPDATES_SEGMENT = "updates"

        /** Reserved first path segment for password-protected PDFs in cacheDir/secured. */
        const val SECURED_SEGMENT = "secured"

        fun uriFor(docId: String, fileName: String): Uri =
            Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(docId).appendPath(fileName).build()

        /** URI for a downloaded update APK in cacheDir/updates (installer hand-off). */
        fun updateUri(fileName: String): Uri =
            Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(UPDATES_SEGMENT).appendPath(fileName).build()

        /** URI for a password-protected PDF export in cacheDir/secured. */
        fun securedUri(fileName: String): Uri =
            Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(SECURED_SEGMENT).appendPath(fileName).build()
    }
}
