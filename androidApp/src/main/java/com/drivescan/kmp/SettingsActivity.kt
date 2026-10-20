package com.drivescan.kmp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.drivescan.kmp.auth.AuthManager
import com.drivescan.kmp.data.JsonParser
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.data.long
import com.drivescan.kmp.data.str
import com.drivescan.kmp.drive.DriveSync
import com.drivescan.kmp.ui.AppLang
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.bodyText
import com.drivescan.kmp.ui.bottomNav
import com.drivescan.kmp.ui.card
import com.drivescan.kmp.ui.dp
import com.drivescan.kmp.ui.ghostButton
import com.drivescan.kmp.ui.inputField
import com.drivescan.kmp.ui.palette
import com.drivescan.kmp.ui.primaryButton
import com.drivescan.kmp.ui.rounded
import com.drivescan.kmp.ui.sectionTitle
import com.drivescan.kmp.ui.smallText
import com.drivescan.kmp.ui.titleText
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Settings: appearance, Google Drive connection + sync, storage, about. */
class SettingsActivity : Activity() {

    private val store by lazy { PersistentStore.get(this) }
    private val palette by lazy { palette() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.bg)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        content.addView(titleText(tr(Str.SETTINGS_TITLE), palette, 22f))

        // ---- Appearance ------------------------------------------------------
        content.addView(sectionTitle(tr(Str.SECTION_APPEARANCE), palette))
        val appearance = card(palette)
        val darkRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        darkRow.addView(bodyText(tr(Str.DARK_MODE_LABEL), palette, 15f).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val darkSwitch = Switch(this).apply {
            isChecked = palette.dark
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("drivescan_prefs", MODE_PRIVATE)
                    .edit().putBoolean("dark_mode", checked).apply()
                recreate()
            }
        }
        darkRow.addView(darkSwitch)
        appearance.addView(darkRow)
        content.addView(appearance)

        // ---- Language ----------------------------------------------------------
        content.addView(sectionTitle(tr(Str.SECTION_LANGUAGE), palette))
        val languageCard = card(palette)
        val currentLang = Lang.current(this)
        for (lang in AppLang.values()) {
            val selected = lang == currentLang
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(12), dp(10), dp(12))
                if (selected) background = rounded(palette.chipBg, 10)
                isClickable = true
                setOnClickListener {
                    if (!selected) {
                        Lang.set(this@SettingsActivity, lang)
                        recreate()
                    }
                }
            }
            row.addView(bodyText(lang.displayName, palette, 15f).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (selected) {
                row.addView(bodyText("✓", palette, 15f))
            }
            languageCard.addView(row)
        }
        content.addView(languageCard)

        // ---- Google Drive ------------------------------------------------------
        content.addView(sectionTitle("Google Drive", palette))
        val driveCard = card(palette)
        val connected = AuthManager.isConnected(this)
        driveCard.addView(
            bodyText(
                if (connected) tr(Str.DRIVE_STATUS_CONNECTED) else tr(Str.DRIVE_STATUS_NOT),
                palette, 15f
            )
        )
        driveCard.addView(
            smallText(
                tr(Str.DRIVE_INFO),
                palette
            ).apply { setPadding(0, dp(4), 0, dp(8)) }
        )
        val clientInput = inputField(tr(Str.INPUT_CLIENT_ID_HINT), palette).apply {
            setText(AuthManager.clientId(this@SettingsActivity))
        }
        driveCard.addView(clientInput)
        val saveClientBtn = ghostButton(tr(Str.BTN_SAVE_CLIENT_ID), palette).apply {
            setOnClickListener {
                AuthManager.setClientId(this@SettingsActivity, clientInput.text.toString())
                toast(tr(Str.TOAST_CLIENT_ID_SAVED))
            }
        }
        driveCard.addView(saveClientBtn)

        if (!connected) {
            val connectBtn = primaryButton("Connect Google Drive").apply {
                setOnClickListener {
                    AuthManager.setClientId(this@SettingsActivity, clientInput.text.toString())
                    if (AuthManager.clientId(this@SettingsActivity).isEmpty()) {
                        toast(tr(Str.TOAST_NEED_CLIENT_ID))
                    } else if (!AuthManager.startAuth(this@SettingsActivity)) {
                        toast(tr(Str.TOAST_CONNECT_FAIL))
                    }
                }
            }
            driveCard.addView(connectBtn)
        } else {
            val syncBtn = primaryButton(tr(Str.BTN_SYNC_NOW)).apply {
                setOnClickListener {
                    toast(tr(Str.TOAST_SYNCING))
                    thread {
                        val message = try {
                            val s = DriveSync.syncNow(this@SettingsActivity)
                            trf(
                                Str.SYNC_RESULT,
                                s.uploaded, s.updated, s.downloaded, s.trashed
                            )
                        } catch (e: Exception) {
                            trf(Str.SYNC_FAIL, e.message ?: "")
                        }
                        runOnUiThread {
                            toast(message)
                            recreate()
                        }
                    }
                }
            }
            driveCard.addView(syncBtn)
            val lastSync = DriveSync.lastSyncMs(this)
            driveCard.addView(
                smallText(
                    if (lastSync == 0L) tr(Str.LAST_SYNC_NEVER)
                    else trf(
                        Str.LAST_SYNC,
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(lastSync))
                    ),
                    palette
                ).apply { setPadding(0, dp(8), 0, dp(8)) }
            )
            val disconnectBtn = ghostButton(tr(Str.BTN_DISCONNECT), palette).apply {
                setOnClickListener {
                    AuthManager.disconnect(this@SettingsActivity)
                    toast(tr(Str.TOAST_DISCONNECTED))
                    recreate()
                }
            }
            driveCard.addView(disconnectBtn)
        }
        content.addView(driveCard)

        // ---- Storage ------------------------------------------------------------
        content.addView(sectionTitle(tr(Str.SECTION_STORAGE), palette))
        val storageCard = card(palette)
        storageCard.addView(bodyText(trf(Str.STORAGE_INFO, formatBytes(store.totalStorageBytes())), palette, 15f))
        storageCard.addView(
            smallText(tr(Str.STORAGE_NOTE), palette)
                .apply { setPadding(0, dp(4), 0, 0) }
        )
        content.addView(storageCard)

        // ---- App Update -----------------------------------------------------------
        content.addView(sectionTitle(tr(Str.SECTION_APP_UPDATE), palette))
        val updateCard = card(palette)
        val pkgInfo = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
        val installedName = pkgInfo?.versionName ?: "?"
        @Suppress("DEPRECATION")
        val installedCode = if (Build.VERSION.SDK_INT >= 28) {
            pkgInfo?.longVersionCode ?: 0L
        } else {
            pkgInfo?.versionCode?.toLong() ?: 0L
        }
        updateCard.addView(
            bodyText(trf(Str.UPDATE_INSTALLED, installedName, installedCode), palette, 15f)
        )
        val updateUrlInput = inputField(tr(Str.UPDATE_URL_HINT), palette).apply {
            setText(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(UPDATE_URL_KEY, "")
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, dp(8)) }
        }
        updateCard.addView(updateUrlInput)
        val saveUrlBtn = ghostButton(tr(Str.BTN_SAVE_URL), palette).apply {
            setOnClickListener {
                saveUpdateUrl(updateUrlInput.text.toString())
                toast(tr(Str.TOAST_URL_SAVED))
            }
        }
        updateCard.addView(saveUrlBtn)
        val checkBtn = primaryButton(tr(Str.BTN_CHECK_UPDATES)).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }
            setOnClickListener {
                checkForUpdates(updateUrlInput.text.toString().trim(), installedCode)
            }
        }
        updateCard.addView(checkBtn)
        content.addView(updateCard)

        // ---- About ----------------------------------------------------------------
        content.addView(sectionTitle(tr(Str.SECTION_ABOUT), palette))
        val about = card(palette)
        about.addView(bodyText(trf(Str.ABOUT_VERSION, "1.0.2"), palette, 15f))
        about.addView(
            smallText(
                tr(Str.ABOUT_TEXT),
                palette
            ).apply { setPadding(0, dp(4), 0, 0) }
        )
        content.addView(about)

        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(content)
        })
        root.addView(bottomNav(palette, "settings"))
        setContentView(root)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.ENGLISH, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---- App update flow -------------------------------------------------------
    //
    // The update server is a static JSON manifest:
    // {"versionCode": 5, "versionName": "1.0.3", "apkUrl": "https://.../app.apk", "notes": "..."}
    // hosted wherever the user points [UPDATE_URL_KEY] at. Nothing is checked
    // automatically; the user taps "Check for updates".

    private fun saveUpdateUrl(url: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putString(UPDATE_URL_KEY, url.trim()).apply()
    }

    private fun checkForUpdates(url: String, installedCode: Long) {
        if (url.isEmpty()) {
            toast(tr(Str.TOAST_URL_EMPTY))
            return
        }
        saveUpdateUrl(url)
        toast(tr(Str.TOAST_CHECKING))
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val manifest = JsonParser(text).parse() as? Map<String, Any?>
                    ?: throw IllegalStateException("bad manifest JSON")
                val remoteCode = manifest.long("versionCode")
                val remoteName = manifest.str("versionName") ?: remoteCode.toString()
                val apkUrl = manifest.str("apkUrl") ?: ""
                val notes = manifest.str("notes") ?: ""
                if (remoteCode > installedCode) {
                    if (apkUrl.isEmpty()) throw IllegalStateException("manifest has no apkUrl")
                    runOnUiThread { showUpdateDialog(remoteName, notes, apkUrl) }
                } else {
                    runOnUiThread { toast(tr(Str.TOAST_UP_TO_DATE)) }
                }
            } catch (e: Exception) {
                runOnUiThread { toast(trf(Str.TOAST_CHECK_FAIL, e.message ?: "")) }
            }
        }
    }

    private fun showUpdateDialog(versionName: String, notes: String, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle(tr(Str.UPDATE_AVAILABLE_TITLE))
            .setMessage(trf(Str.UPDATE_AVAILABLE_MSG, versionName, notes))
            .setPositiveButton(tr(Str.BTN_DOWNLOAD_INSTALL)) { _, _ -> downloadUpdate(apkUrl) }
            .setNegativeButton(tr(Str.BTN_LATER), null)
            .show()
    }

    private fun downloadUpdate(apkUrl: String) {
        val progressText = TextView(this).apply {
            setPadding(dp(24), dp(18), dp(24), dp(10))
            textSize = 14.5f
            setTextColor(palette.text)
            text = tr(Str.UPDATE_PROGRESS_BYTES)
        }
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(tr(Str.UPDATE_DOWNLOADING_TITLE))
            .setView(progressText)
            .setCancelable(false)
            .create()
        progressDialog.show()
        thread {
            try {
                val dir = File(cacheDir, "updates").apply { mkdirs() }
                val dest = File(dir, "update.apk")
                val conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                            downloaded += n
                            if (total > 0) {
                                val pct = (downloaded * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    runOnUiThread {
                                        progressText.text = trf(Str.UPDATE_PROGRESS_PCT, pct)
                                    }
                                }
                            }
                        }
                    }
                }
                runOnUiThread {
                    progressDialog.dismiss()
                    promptInstall(dest)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    toast(trf(Str.TOAST_UPDATE_DOWNLOAD_FAIL, e.message ?: ""))
                }
            }
        }
    }

    private fun promptInstall(file: File) {
        // API 26+: the user must have granted "install unknown apps" for this app.
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            toast(tr(Str.TOAST_ALLOW_INSTALLS))
            try {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                // The toast above already explains what to do.
            }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                ShareProvider.updateUri(file.name),
                "application/vnd.android.package-archive"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(tr(Str.TOAST_INSTALLER_FAIL))
        }
    }

    companion object {
        private const val PREFS_NAME = "drivescan_prefs"
        private const val UPDATE_URL_KEY = "update_manifest_url"
    }
}
