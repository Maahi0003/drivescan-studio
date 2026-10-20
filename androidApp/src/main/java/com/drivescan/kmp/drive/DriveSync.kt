package com.drivescan.kmp.drive

import android.content.Context
import com.drivescan.kmp.auth.AuthManager
import com.drivescan.kmp.auth.ClientSecret
import com.drivescan.kmp.data.JsonParser
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import com.drivescan.shared.DriveApi
import com.drivescan.shared.ScanDocument
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Drive sync engine. MUST be called from a background thread.
 *
 * Model: local-first. The phone's store is the source of truth; sync pushes
 * new/changed documents into DriveScan_Workspace (vault docs into its
 * "Personal Vault" subfolder, binned docs into "Bin", other docs into a
 * subfolder matching their local folder name), pulls files it doesn't know
 * yet, and applies queued permanent deletes.
 */
object DriveSync {

    data class Summary(val uploaded: Int, val updated: Int, val downloaded: Int, val trashed: Int)

    class SyncException(message: String) : Exception(message)

    private const val PREFS = "drivescan_drive"

    /** Downloads one Drive-backed document's content on demand (viewer). Background thread only. */
    fun downloadDocument(context: Context, doc: ScanDocument): Boolean {
        val driveId = doc.driveId ?: return false
        val token = freshAccessToken(context)
        val store = PersistentStore.get(context)
        val tmp = File(context.cacheDir, "pull_${doc.id}")
        downloadFile(context, token, driveId, tmp)
        // Uploads from this app are PDFs (or single JPGs); sniff the header to be safe.
        val isPdf = tmp.inputStream().use { input ->
            val head = ByteArray(4)
            input.read(head) == 4 && head.contentEquals("%PDF".toByteArray())
        }
        store.attachDownloadedFile(doc.id, tmp, isPdf)
        return true
    }

    fun lastSyncMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_sync", 0L)

    /** Records a Drive file that must be permanently deleted on next sync. */
    fun queueTrash(context: Context, driveId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("pending_trash", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(driveId)
        prefs.edit().putStringSet("pending_trash", current).apply()
    }

    fun syncNow(context: Context): Summary {
        val store = PersistentStore.get(context)
        val token = freshAccessToken(context)
        val api = DriveApi { token }

        var uploaded = 0
        var updated = 0
        var downloaded = 0
        var trashed = 0

        // 1) Core folders
        val workspaceId = findOrCreateFolder(context, api, token, DriveApi.WORKSPACE_FOLDER_NAME, null)
        rememberFolder(context, "root", workspaceId)
        val vaultId = findOrCreateFolder(context, api, token, DriveApi.VAULT_FOLDER_NAME, workspaceId)
        rememberFolder(context, "vault", vaultId)
        val binId = findOrCreateFolder(context, api, token, DriveApi.BIN_FOLDER_NAME, workspaceId)
        rememberFolder(context, "bin", binId)

        // Map local folders to Drive subfolders
        val localFolderDriveIds = HashMap<String, String>()
        for (folder in store.folders()) {
            val id = findOrCreateFolder(context, api, token, folder.name, workspaceId)
            localFolderDriveIds[folder.id] = id
            rememberFolder(context, "local_${folder.id}", id)
        }

        fun parentFor(doc: ScanDocument): String = when {
            doc.inBin -> binId
            doc.inVault -> vaultId
            doc.folderId != null -> localFolderDriveIds[doc.folderId] ?: workspaceId
            else -> workspaceId
        }

        fun categoryOf(doc: ScanDocument): String = when {
            doc.inBin -> "bin"
            doc.inVault -> "vault"
            else -> "folder_${doc.folderId ?: "root"}"
        }

        // 2) Queued permanent deletes
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trashQueue = prefs.getStringSet("pending_trash", emptySet())?.toList() ?: emptyList()
        for (driveId in trashQueue) {
            try {
                request(context, "DELETE", "${DriveApi.API_BASE}/files/$driveId", token)
                trashed++
            } catch (e: Exception) {
                // Already gone on Drive is fine.
            }
        }
        if (trashQueue.isNotEmpty()) {
            prefs.edit().remove("pending_trash").apply()
        }

        // 3) Push local docs
        for (doc in store.documents(includeBin = true)) {
            val file = store.pdfFile(doc) ?: store.pageFile(doc, 0) ?: continue
            val mime = if (file.name.endsWith(".pdf")) DriveApi.PDF_MIME else DriveApi.JPEG_MIME
            val parent = parentFor(doc)
            val syncedModified = prefs.getLong("syncedmod_${doc.id}", -1L)
            val syncedParent = prefs.getString("syncedparent_${doc.id}", null)
            try {
                if (doc.driveId == null) {
                    val meta = api.uploadMetadataJson(doc.name, parent, mime)
                    val newId = multipartUpload(context, token, meta, file, mime)
                    if (newId != null) {
                        store.setDriveId(doc.id, newId)
                        rememberSynced(context, doc, parent)
                        uploaded++
                    }
                } else {
                    var changed = false
                    if (doc.modifiedAtEpochMs > syncedModified) {
                        mediaUpdate(context, token, doc.driveId, file, mime)
                        changed = true
                    }
                    if (syncedParent != null && syncedParent != parent) {
                        moveFile(context, token, doc.driveId, syncedParent, parent)
                        changed = true
                    }
                    if (changed) {
                        rememberSynced(context, doc, parent)
                        updated++
                    }
                }
            } catch (e: SyncException) {
                throw e
            } catch (e: Exception) {
                // One bad file shouldn't kill the whole sync.
            }
        }

        // 4) Pull unknown files from workspace folders
        val knownDriveIds = store.documents(includeBin = true).mapNotNull { it.driveId }.toSet()
        val pullTargets = mutableListOf<Pair<String, String?>>(
            workspaceId to null,
            vaultId to "vault"
        )
        for ((localId, driveFolderId) in localFolderDriveIds) {
            pullTargets.add(driveFolderId to localId)
        }
        for ((driveFolderId, localKey) in pullTargets) {
            val files = listFiles(context, api, token, driveFolderId)
            for (meta in files) {
                if (meta.mimeType == DriveApi.FOLDER_MIME) continue
                if (meta.id in knownDriveIds) continue
                val isVault = localKey == "vault"
                val localFolderId = if (localKey == null || localKey == "vault") null else localKey
                val newDoc = store.addRemoteDocument(
                    name = meta.name,
                    folderId = localFolderId,
                    driveId = meta.id,
                    inVault = isVault,
                    nowMs = System.currentTimeMillis()
                )
                try {
                    val tmp = File(context.cacheDir, "pull_${meta.id}")
                    downloadFile(context, token, meta.id, tmp)
                    store.attachDownloadedFile(
                        newDoc.id, tmp,
                        isPdf = meta.mimeType == DriveApi.PDF_MIME || meta.name.endsWith(".pdf")
                    )
                    downloaded++
                } catch (e: Exception) {
                    // Metadata stays; content can be fetched later.
                }
            }
        }

        prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()
        return Summary(uploaded, updated, downloaded, trashed)
    }

    // ---- token --------------------------------------------------------------

    private fun freshAccessToken(context: Context): String {
        val token = AuthManager.accessToken(context)
            ?: throw SyncException(context.tr(Str.SYNC_ERR_NOT_CONNECTED))
        val expiry = AuthManager.tokenExpiryMs(context)
        if (System.currentTimeMillis() < expiry - 60_000) return token
        val refresh = AuthManager.refreshToken(context) ?: return token
        val cid = AuthManager.clientId(context)
        if (cid.isEmpty()) return token
        return try {
            val body = listOf(
                "client_id" to cid,
                "client_secret" to ClientSecret.VALUE,
                "grant_type" to "refresh_token",
                "refresh_token" to refresh
            ).joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val conn = URL(DriveApi.OAUTH_TOKEN_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val text = readResponse(context, conn)
            val parsed = JsonParser(text).parse() as? Map<String, Any?>
            val access = parsed?.get("access_token") as? String ?: return token
            val expiresIn = (parsed["expires_in"] as? Number)?.toLong() ?: 3600L
            AuthManager.saveTokens(context, access, null, expiresIn)
            access
        } catch (e: Exception) {
            token // fall back to the stored token; the API call will surface any real problem
        }
    }

    // ---- Drive REST primitives ----------------------------------------------

    private fun findOrCreateFolder(context: Context, api: DriveApi, token: String, name: String, parentId: String?): String {
        val q = enc(api.folderSearchQuery(name, parentId))
        val url = "${DriveApi.API_BASE}/files?q=$q&fields=files(id,name,mimeType,modifiedTime)"
        val text = request(context, "GET", url, token)
        val existing = api.parseFileList(text).firstOrNull { it.name == name }
        if (existing != null) return existing.id
        val body = api.createFolderJson(name, parentId)
        val created = request(context, "POST", "${DriveApi.API_BASE}/files", token, body)
        return DriveApi.extractJsonString(created, "id", 0)
            ?: throw SyncException(context.trf(Str.SYNC_ERR_FOLDER, name))
    }

    private fun listFiles(context: Context, api: DriveApi, token: String, parentId: String): List<com.drivescan.shared.DriveFileMeta> {
        val q = enc(api.childrenQuery(parentId))
        val url = "${DriveApi.API_BASE}/files?q=$q&fields=files(id,name,mimeType,modifiedTime)&pageSize=100"
        val text = request(context, "GET", url, token)
        return api.parseFileList(text)
    }

    private fun multipartUpload(context: Context, token: String, metadataJson: String, file: File, mime: String): String? {
        val boundary = "drivescan_${System.currentTimeMillis()}"
        val conn = URL("${DriveApi.UPLOAD_BASE}/files?uploadType=multipart")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        conn.doOutput = true
        conn.outputStream.use { out ->
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            out.write(metadataJson.toByteArray(Charsets.UTF_8))
            out.write("\r\n--$boundary\r\n".toByteArray())
            out.write("Content-Type: $mime\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val text = readResponse(context, conn)
        return DriveApi.extractJsonString(text, "id", 0)
    }

    private fun mediaUpdate(context: Context, token: String, fileId: String, file: File, mime: String) {
        val conn = URL("${DriveApi.UPLOAD_BASE}/files/$fileId?uploadType=media")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", mime)
        conn.doOutput = true
        conn.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
        readResponse(context, conn)
    }

    private fun moveFile(context: Context, token: String, fileId: String, oldParent: String, newParent: String) {
        val url = "${DriveApi.API_BASE}/files/$fileId?addParents=${enc(newParent)}&removeParents=${enc(oldParent)}"
        requestWithOverride(context, token, url, "{}")
    }

    private fun downloadFile(context: Context, token: String, fileId: String, dest: File) {
        val conn = URL("${DriveApi.API_BASE}/files/$fileId?alt=media").openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (conn.responseCode !in 200..299) {
            throw SyncException(context.trf(Str.SYNC_ERR_DOWNLOAD, conn.responseCode))
        }
        conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
    }

    private fun requestWithOverride(context: Context, token: String, url: String, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return readResponse(context, conn)
    }

    private fun request(context: Context, method: String, url: String, token: String, jsonBody: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (jsonBody != null) {
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        }
        return readResponse(context, conn)
    }

    private fun readResponse(context: Context, conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) {
            throw SyncException(context.trf(Str.SYNC_ERR_API, code, text.take(160)))
        }
        return text
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun rememberFolder(context: Context, key: String, driveId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("dfolder_$key", driveId).apply()
    }

    private fun rememberSynced(context: Context, doc: ScanDocument, parent: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("syncedmod_${doc.id}", doc.modifiedAtEpochMs)
            .putString("syncedparent_${doc.id}", parent)
            .apply()
    }
}
