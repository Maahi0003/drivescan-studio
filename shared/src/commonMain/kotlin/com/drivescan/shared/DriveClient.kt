package com.drivescan.shared

/**
 * Google Drive REST helpers.
 *
 * Pure request-building/parsing logic lives here (unit-testable, no network);
 * the Android app performs the actual HTTPS calls (see DriveSync) using the
 * strings built here. Native auth note: unlike the web app (GIS web client),
 * the native apps need their OWN OAuth clients — Android: package name +
 * SHA-1 in Google Cloud. Scope stays drive.file, so the app only ever sees
 * files it created.
 */
class DriveApi(private val accessToken: () -> String?) {

    val isConnected: Boolean get() = accessToken() != null

    /** files.list query: children of [parentId] (or app root), not trashed. */
    fun childrenQuery(parentId: String?): String {
        val parent = parentId ?: "root"
        return "'$parent' in parents and trashed = false"
    }

    /** files.list query: find a folder by name inside [parentId]. */
    fun folderSearchQuery(name: String, parentId: String?): String {
        val parent = parentId ?: "root"
        return "name = '${name.replace("'", "\\'")}' and " +
            "mimeType = 'application/vnd.google-apps.folder' and " +
            "'$parent' in parents and trashed = false"
    }

    /** files.list query: find any (non-folder-specific) file by name inside [parentId]. */
    fun fileSearchQuery(name: String, parentId: String?): String {
        val parent = parentId ?: "root"
        return "name = '${name.replace("'", "\\'")}' and " +
            "'$parent' in parents and trashed = false"
    }

    /** Pulls the first "id" value out of a Drive files.list JSON response. */
    fun extractFirstFileId(json: String): String? = extractJsonString(json, "id", 0)

    /** JSON body for creating a folder: {"name":..,"mimeType":folder,"parents":[..]} */
    fun createFolderJson(name: String, parentId: String?): String {
        val parents = if (parentId != null) ",\"parents\":[\"${escapeJson(parentId)}\"]" else ""
        return "{\"name\":\"${escapeJson(name)}\",\"mimeType\":\"$FOLDER_MIME\"$parents}"
    }

    /** JSON metadata part for a multipart file upload. */
    fun uploadMetadataJson(name: String, parentId: String?, mimeType: String): String {
        val parents = if (parentId != null) ",\"parents\":[\"${escapeJson(parentId)}\"]" else ""
        return "{\"name\":\"${escapeJson(name)}\",\"mimeType\":\"${escapeJson(mimeType)}\"$parents}"
    }

    /**
     * Parses a Drive files.list response into metadata entries.
     * Deliberately a tolerant scanner (no JSON library in common code):
     * walks top-level objects inside the "files" array and pulls the
     * id / name / mimeType / modifiedTime string fields of each.
     */
    fun parseFileList(json: String): List<DriveFileMeta> {
        val result = mutableListOf<DriveFileMeta>()
        val filesKey = json.indexOf("\"files\"")
        if (filesKey < 0) return result
        val arrayStart = json.indexOf('[', filesKey)
        if (arrayStart < 0) return result
        var i = arrayStart + 1
        while (i < json.length) {
            val objStart = json.indexOf('{', i)
            if (objStart < 0) break
            // find matching closing brace, respecting strings
            var depth = 0
            var j = objStart
            var inString = false
            var escaped = false
            while (j < json.length) {
                val c = json[j]
                if (inString) {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') inString = false
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                }
                j++
            }
            if (j >= json.length) break
            val obj = json.substring(objStart, j + 1)
            val id = extractJsonString(obj, "id", 0)
            if (id != null) {
                result.add(
                    DriveFileMeta(
                        id = id,
                        name = extractJsonString(obj, "name", 0) ?: "",
                        mimeType = extractJsonString(obj, "mimeType", 0),
                        modifiedTime = extractJsonString(obj, "modifiedTime", 0)
                    )
                )
            }
            i = j + 1
            // stop at end of the files array
            val nextComma = json.indexOf(',', i)
            val arrayEnd = json.indexOf(']', i)
            if (arrayEnd >= 0 && (nextComma < 0 || arrayEnd < nextComma)) break
        }
        return result
    }

    companion object {
        const val WORKSPACE_FOLDER_NAME = "DriveScan_Workspace"
        const val VAULT_FOLDER_NAME = "Personal Vault"
        const val BIN_FOLDER_NAME = "Bin"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val PDF_MIME = "application/pdf"
        const val JPEG_MIME = "image/jpeg"
        const val API_BASE = "https://www.googleapis.com/drive/v3"
        const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val OAUTH_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val REDIRECT_URI = "com.googleusercontent.apps.101242821085-s1fvavfto8jm2bfabnq4up98pn15dosk:/oauth2redirect"

        /** Minimal JSON string escaping for values we place into request bodies. */
        fun escapeJson(value: String): String {
            val sb = StringBuilder(value.length + 8)
            for (c in value) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        /** Extracts the string value of "key" at/after [fromIndex], or null. */
        fun extractJsonString(json: String, key: String, fromIndex: Int): String? {
            val needle = "\"$key\""
            val keyIdx = json.indexOf(needle, fromIndex)
            if (keyIdx < 0) return null
            val colon = json.indexOf(':', keyIdx + needle.length)
            if (colon < 0) return null
            var i = colon + 1
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != '"') return null
            val sb = StringBuilder()
            i++
            while (i < json.length) {
                val c = json[i]
                if (c == '\\' && i + 1 < json.length) {
                    when (json[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        else -> sb.append(json[i + 1])
                    }
                    i += 2
                    continue
                }
                if (c == '"') return sb.toString()
                sb.append(c)
                i++
            }
            return null
        }
    }
}
