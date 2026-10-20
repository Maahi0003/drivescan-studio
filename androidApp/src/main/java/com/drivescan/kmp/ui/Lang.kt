package com.drivescan.kmp.ui

import android.content.Context
import java.util.Locale

/**
 * In-code localization for the Android app: English (default), Hindi
 * (Devanagari) and Hinglish (Roman Hindi — the strings the app originally
 * shipped with). The choice persists in SharedPreferences under
 * [Lang.PREF_KEY]; **when nothing is saved the effective language is
 * English** (see [Lang.readPref]).
 *
 * Usage from any Activity/Context: `tr(Str.SOME_KEY)` for plain strings,
 * `trf(Str.SOME_KEY, arg1, ...)` for %1$s / %1$d formatted strings.
 * Kept untranslated on purpose: brand/proper nouns ("DriveScan Studio",
 * "Google Drive", "DriveScan_Workspace"), vault template names, format
 * names (PDF/JPG).
 */
enum class AppLang(val code: String, val displayName: String) {
    EN("en", "English"),
    HI("hi", "हिन्दी"),
    HINGLISH("hinglish", "Hinglish")
}

/** String keys. Every user-visible string in the app resolves through one of these. */
enum class Str {
    // navigation / shared
    NAV_HOME, NAV_VAULT, NAV_BIN, NAV_SETTINGS,
    BTN_SAVE, BTN_CANCEL, BTN_DELETE, BTN_CREATE, BTN_UNLOCK,
    PAGE_WORD,

    // dashboard (MainActivity)
    PURGE_TOAST, SEARCH_HINT, CHIP_ALL, CHIP_ROOT, CHIP_NEW_FOLDER,
    DRIVE_CONNECTED, DRIVE_NOT_CONNECTED, SCAN_BUTTON,
    EMPTY_NO_DOCS, EMPTY_SEARCH, EMPTY_FOLDER,
    META_PAGES, META_VAULT_SUFFIX, META_DRIVE_SUFFIX,
    ACTION_OPEN, ACTION_RENAME, ACTION_MOVE_FOLDER, ACTION_SHARE,
    ACTION_ADD_VAULT, ACTION_REMOVE_VAULT, ACTION_MOVE_TO_BIN,
    TOAST_REMOVED_FROM_VAULT, TOAST_MOVED_TO_BIN,
    RENAME_TITLE, INPUT_DOC_NAME, MOVE_TITLE, MOVE_ROOT_LABEL,
    VAULT_SLOT_TITLE, VAULT_SLOT_TAKEN, TOAST_ADDED_TO_VAULT,
    NEW_FOLDER_TITLE, INPUT_FOLDER_NAME,
    TOAST_SHARE_NO_FILE, SHARE_CHOOSER,

    // vault (VaultActivity)
    VAULT_TITLE, VAULT_SUBTITLE,
    SET_PIN_INTRO, INPUT_NEW_PIN, INPUT_CONFIRM_PIN, BTN_SET_PIN,
    TOAST_PIN_TOO_SHORT, TOAST_PIN_MISMATCH, TOAST_PIN_SET,
    LOCKED_INTRO, INPUT_VAULT_PIN, TOAST_WRONG_PIN,
    BTN_BIOMETRIC, NO_BIOMETRIC_NOTE,
    SLOTS_TITLE, SLOT_EMPTY, SLOT_FILLED_NAME, OTHERS_TITLE, BTN_LOCK_VAULT,
    ADD_TO_SLOT_TITLE, ADD_PICK_EXISTING, ADD_SCAN_NOW, PICK_DOC_TITLE,
    TOAST_NO_DOC_AVAILABLE, TOAST_SLOT_SAVED,
    SLOT_OPT_REPLACE,
    BIO_PROMPT_TITLE, BIO_PROMPT_SUBTITLE, BIO_PROMPT_NEGATIVE,
    TOAST_BIO_FAILED, TOAST_BIO_UNAVAILABLE,

    // bin (BinActivity)
    BIN_TITLE, BIN_SUBTITLE, BIN_EMPTY, BIN_META,
    BIN_OPT_RESTORE, BIN_OPT_VIEW, BIN_OPT_DELETE_FOREVER,
    TOAST_RESTORED, DELETE_FOREVER_TITLE, DELETE_FOREVER_MSG, TOAST_DELETED_FOREVER,

    // scanner (ScannerActivity)
    TOAST_NO_CAMERA_PERMISSION, PILL_LOOKING,
    BTN_DOC_MODE, BTN_ID_MODE, BTN_TORCH_OFF, BTN_TORCH_ON, BTN_GALLERY, BTN_DONE,
    COUNT_NONE, COUNT_ONE, COUNT_MANY,
    TOAST_NO_CAMERA_FOUND, TOAST_PAGE_CAPTURED, TOAST_CAPTURE_SAVE_FAIL,
    TOAST_CAMERA_OPEN_FAIL, TOAST_CAMERA_ERROR, TOAST_PREVIEW_FAIL, TOAST_SESSION_ERROR,
    TOAST_CAMERA_NOT_READY_CAPTURE, TOAST_CAPTURE_FAIL,
    TOAST_CAMERA_NOT_READY, TOAST_TORCH_UNAVAILABLE,
    IMPORT_ONE, IMPORT_MANY, TOAST_IMPORT_FAIL, TOAST_NEED_ONE_PAGE,

    // editor (EditorActivity)
    EDITOR_TITLE, TOAST_NO_PAGE_FOUND, TOAST_PAGE_LOAD_FAIL, PAGE_LABEL,
    BTN_ROTATE, BTN_ADD_PAGE, BTN_DELETE_PAGE, TOAST_ALL_PAGES_DELETED, BTN_SAVE_DOC,
    FILTER_ORIGINAL, FILTER_GRAY, FILTER_BW, FILTER_ENHANCE,
    SAVE_DIALOG_TITLE, SAVE_LABEL_FOLDER, SAVE_NONE_TEMPLATE,
    SAVE_LABEL_VAULT, SAVE_LABEL_FORMAT, RADIO_JPG,
    TOAST_PROCESSING, TOAST_SAVED, TOAST_SAVE_FAIL,
    ERR_PAGE_LOAD, ERR_PAGE_SAVE, SCAN_NAME_PREFIX,

    // viewer (DocViewerActivity)
    TOAST_DOC_NOT_FOUND, BTN_PREV, BTN_NEXT, BTN_SHARE,
    BTN_OPEN_PDF, BTN_DOWNLOAD, BTN_BIN,
    VIEWER_PDF_MSG, VIEWER_DRIVE_MSG, INDICATOR_DRIVE,
    TOAST_NO_PDF_APP, TOAST_SHARE_NO_LOCAL,
    TOAST_DOWNLOADING, TOAST_DOWNLOADED, TOAST_DOWNLOAD_FAIL, TOAST_MOVED_TO_BIN_SHORT,

    // settings (SettingsActivity)
    SETTINGS_TITLE, SECTION_APPEARANCE, DARK_MODE_LABEL, SECTION_LANGUAGE,
    DRIVE_STATUS_CONNECTED, DRIVE_STATUS_NOT, DRIVE_INFO,
    INPUT_CLIENT_ID_HINT, BTN_SAVE_CLIENT_ID, TOAST_CLIENT_ID_SAVED,
    TOAST_NEED_CLIENT_ID, TOAST_CONNECT_FAIL,
    BTN_SYNC_NOW, TOAST_SYNCING, SYNC_RESULT, SYNC_FAIL, LAST_SYNC_NEVER, LAST_SYNC,
    BTN_DISCONNECT, TOAST_DISCONNECTED,
    SECTION_STORAGE, STORAGE_INFO, STORAGE_NOTE,
    SECTION_ABOUT, ABOUT_VERSION, ABOUT_TEXT,
    SECTION_APP_UPDATE, UPDATE_INSTALLED, UPDATE_URL_HINT, BTN_SAVE_URL, TOAST_URL_SAVED,
    BTN_CHECK_UPDATES, TOAST_URL_EMPTY, TOAST_CHECKING, TOAST_UP_TO_DATE, TOAST_CHECK_FAIL,
    UPDATE_AVAILABLE_TITLE, UPDATE_AVAILABLE_MSG, BTN_DOWNLOAD_INSTALL, BTN_LATER,
    UPDATE_DOWNLOADING_TITLE, UPDATE_PROGRESS_PCT, UPDATE_PROGRESS_BYTES,
    TOAST_UPDATE_DOWNLOAD_FAIL, TOAST_ALLOW_INSTALLS, TOAST_INSTALLER_FAIL,

    // auth (AuthActivity)
    AUTH_ERR_REJECTED, AUTH_INCOMPLETE, AUTH_STATE_MISMATCH, AUTH_SESSION_EXPIRED,
    AUTH_CONNECTING, AUTH_TOKEN_FAIL, AUTH_NO_TOKEN, AUTH_NETWORK_ERROR, AUTH_SUCCESS,

    // drive sync errors (DriveSync)
    SYNC_ERR_NOT_CONNECTED, SYNC_ERR_FOLDER, SYNC_ERR_DOWNLOAD, SYNC_ERR_API
}

object Lang {
    private const val PREFS = "drivescan_prefs"
    const val PREF_KEY = "app_language"

    @Volatile
    private var currentLang: AppLang = AppLang.EN

    @Volatile
    private var loaded = false

    /** Loads the saved language into the cache. Cheap; safe to call often. */
    fun init(context: Context) {
        currentLang = readPref(context)
        loaded = true
    }

    private fun ensure(context: Context) {
        if (!loaded) init(context)
    }

    /**
     * The rule that makes English the default: any missing/unknown saved
     * value resolves to [AppLang.EN].
     */
    private fun readPref(context: Context): AppLang {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_KEY, null)
        return when (saved) {
            AppLang.HI.code -> AppLang.HI
            AppLang.HINGLISH.code -> AppLang.HINGLISH
            else -> AppLang.EN
        }
    }

    fun current(context: Context): AppLang {
        ensure(context)
        return currentLang
    }

    fun set(context: Context, lang: AppLang) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, lang.code).apply()
        currentLang = lang
        loaded = true
    }

    fun text(context: Context, key: Str): String {
        ensure(context)
        val table = when (currentLang) {
            AppLang.EN -> EN_TABLE
            AppLang.HI -> HI_TABLE
            AppLang.HINGLISH -> HINGLISH_TABLE
        }
        return table[key] ?: EN_TABLE[key] ?: key.name
    }

    fun fmt(context: Context, key: Str, vararg args: Any): String =
        String.format(Locale.US, text(context, key), *args)
}

/** Plain localized string for [key] in the currently selected language. */
fun Context.tr(key: Str): String = Lang.text(this, key)

/** Localized format string; args fill %1$s / %1$d placeholders. */
fun Context.trf(key: Str, vararg args: Any): String = Lang.fmt(this, key, *args)

private val EN_TABLE: Map<Str, String> = mapOf(
    // navigation / shared
    Str.NAV_HOME to "⌂  Home",
    Str.NAV_VAULT to "🔒 Vault",
    Str.NAV_BIN to "🗑 Bin",
    Str.NAV_SETTINGS to "⚙ Settings",
    Str.BTN_SAVE to "Save",
    Str.BTN_CANCEL to "Cancel",
    Str.BTN_DELETE to "Delete",
    Str.BTN_CREATE to "Create",
    Str.BTN_UNLOCK to "Unlock",
    Str.PAGE_WORD to "page",

    // dashboard
    Str.PURGE_TOAST to "Permanently deleted %1\$d old document(s) from Bin",
    Str.SEARCH_HINT to "Search documents…",
    Str.CHIP_ALL to "All",
    Str.CHIP_ROOT to "Root",
    Str.CHIP_NEW_FOLDER to "＋ Folder",
    Str.DRIVE_CONNECTED to "☁  Google Drive — Connected",
    Str.DRIVE_NOT_CONNECTED to "☁  Google Drive — Not connected (connect from Settings)",
    Str.SCAN_BUTTON to "📷   Scan Document",
    Str.EMPTY_NO_DOCS to "No documents yet.\nTap Scan Document below to make your first scan.",
    Str.EMPTY_SEARCH to "Nothing found for “%1\$s”.",
    Str.EMPTY_FOLDER to "No documents in this folder yet.",
    Str.META_PAGES to "%1\$d page",
    Str.META_VAULT_SUFFIX to "🔒 Vault",
    Str.META_DRIVE_SUFFIX to "☁ Drive",
    Str.ACTION_OPEN to "Open",
    Str.ACTION_RENAME to "Rename",
    Str.ACTION_MOVE_FOLDER to "Change folder",
    Str.ACTION_SHARE to "Share",
    Str.ACTION_ADD_VAULT to "Add to Vault",
    Str.ACTION_REMOVE_VAULT to "Remove from Vault",
    Str.ACTION_MOVE_TO_BIN to "Move to Bin",
    Str.TOAST_REMOVED_FROM_VAULT to "Removed from Vault",
    Str.TOAST_MOVED_TO_BIN to "Moved to Bin — it can be restored within 30 days",
    Str.RENAME_TITLE to "Rename",
    Str.INPUT_DOC_NAME to "Document name",
    Str.MOVE_TITLE to "Choose a folder",
    Str.MOVE_ROOT_LABEL to "Root (no folder)",
    Str.VAULT_SLOT_TITLE to "Choose a Vault slot",
    Str.VAULT_SLOT_TAKEN to "%1\$s (occupied — will be replaced)",
    Str.TOAST_ADDED_TO_VAULT to "Saved to Vault: %1\$s",
    Str.NEW_FOLDER_TITLE to "New folder",
    Str.INPUT_FOLDER_NAME to "Folder name",
    Str.TOAST_SHARE_NO_FILE to "No local file to share (Drive-only document)",
    Str.SHARE_CHOOSER to "Share: %1\$s",

    // vault
    Str.VAULT_TITLE to "🔒 Personal Vault",
    Str.VAULT_SUBTITLE to "A safe place for Aadhaar, PAN and your other important documents.",
    Str.SET_PIN_INTRO to "First, set a Vault PIN (at least 4 digits).",
    Str.INPUT_NEW_PIN to "New PIN",
    Str.INPUT_CONFIRM_PIN to "Re-enter PIN",
    Str.BTN_SET_PIN to "Set PIN",
    Str.TOAST_PIN_TOO_SHORT to "PIN must be at least 4 digits",
    Str.TOAST_PIN_MISMATCH to "The two PINs don't match",
    Str.TOAST_PIN_SET to "Vault PIN set ✓",
    Str.LOCKED_INTRO to "Vault is locked. Enter your PIN to open it.",
    Str.INPUT_VAULT_PIN to "Vault PIN",
    Str.TOAST_WRONG_PIN to "Wrong PIN",
    Str.BTN_BIOMETRIC to "👆 Unlock with Fingerprint / Face",
    Str.NO_BIOMETRIC_NOTE to "Biometric lock isn't available on this device — your PIN will work.",
    Str.SLOTS_TITLE to "Document slots",
    Str.SLOT_EMPTY to "Empty — add a document",
    Str.SLOT_FILLED_NAME to "✓ %1\$s",
    Str.OTHERS_TITLE to "Other Vault documents",
    Str.BTN_LOCK_VAULT to "🔒 Lock Vault",
    Str.ADD_TO_SLOT_TITLE to "Add %1\$s",
    Str.ADD_PICK_EXISTING to "📄 Choose an existing document",
    Str.ADD_SCAN_NOW to "📷 Scan now",
    Str.PICK_DOC_TITLE to "Choose a document",
    Str.TOAST_NO_DOC_AVAILABLE to "No document available — scan one first",
    Str.TOAST_SLOT_SAVED to "Saved to Vault slot ✓",
    Str.SLOT_OPT_REPLACE to "Put a different document",
    Str.BIO_PROMPT_TITLE to "Vault Unlock",
    Str.BIO_PROMPT_SUBTITLE to "Verify with fingerprint or face",
    Str.BIO_PROMPT_NEGATIVE to "Use PIN",
    Str.TOAST_BIO_FAILED to "Biometric didn't work — use your PIN",
    Str.TOAST_BIO_UNAVAILABLE to "Biometric isn't available — use your PIN",

    // bin
    Str.BIN_TITLE to "🗑 Bin",
    Str.BIN_SUBTITLE to "Deleted documents stay here for 30 days, then they're removed forever.",
    Str.BIN_EMPTY to "Bin is empty.",
    Str.BIN_META to "%1\$d page • %2\$d days left (30-day retention)",
    Str.BIN_OPT_RESTORE to "↩ Restore",
    Str.BIN_OPT_VIEW to "👁 View",
    Str.BIN_OPT_DELETE_FOREVER to "✖ Delete forever",
    Str.TOAST_RESTORED to "Restored ✓",
    Str.DELETE_FOREVER_TITLE to "Delete forever?",
    Str.DELETE_FOREVER_MSG to "All files of “%1\$s” will be removed from this phone. This can't be undone.",
    Str.TOAST_DELETED_FOREVER to "Deleted forever",

    // scanner
    Str.TOAST_NO_CAMERA_PERMISSION to "Scanning needs camera permission — you can import from Gallery instead",
    Str.PILL_LOOKING to "🔍  Looking for document…",
    Str.BTN_DOC_MODE to "📄 Document",
    Str.BTN_ID_MODE to "🪪 ID Card",
    Str.BTN_TORCH_OFF to "🔦 Torch: Off",
    Str.BTN_TORCH_ON to "🔦 Torch: On",
    Str.BTN_GALLERY to "🖼 Gallery",
    Str.BTN_DONE to "Done →",
    Str.COUNT_NONE to "No pages yet",
    Str.COUNT_ONE to "1 page ready",
    Str.COUNT_MANY to "%1\$d pages ready",
    Str.TOAST_NO_CAMERA_FOUND to "No camera found on this device",
    Str.TOAST_PAGE_CAPTURED to "Page %1\$d captured ✓",
    Str.TOAST_CAPTURE_SAVE_FAIL to "Couldn't save the capture: %1\$s",
    Str.TOAST_CAMERA_OPEN_FAIL to "Camera couldn't open (error %1\$d)",
    Str.TOAST_CAMERA_ERROR to "Camera error: %1\$s",
    Str.TOAST_PREVIEW_FAIL to "Camera preview couldn't start",
    Str.TOAST_SESSION_ERROR to "Camera session error: %1\$s",
    Str.TOAST_CAMERA_NOT_READY_CAPTURE to "Camera isn't ready — wait a second and try again",
    Str.TOAST_CAPTURE_FAIL to "Capture failed: %1\$s",
    Str.TOAST_CAMERA_NOT_READY to "Camera isn't ready",
    Str.TOAST_TORCH_UNAVAILABLE to "Torch isn't available on this device",
    Str.IMPORT_ONE to "1 photo imported ✓",
    Str.IMPORT_MANY to "%1\$d photos imported ✓",
    Str.TOAST_IMPORT_FAIL to "Photos couldn't be imported",
    Str.TOAST_NEED_ONE_PAGE to "Capture or import at least one page first",

    // editor
    Str.EDITOR_TITLE to "Edit Scan",
    Str.TOAST_NO_PAGE_FOUND to "No page found",
    Str.TOAST_PAGE_LOAD_FAIL to "Page couldn't load",
    Str.PAGE_LABEL to "Page %1\$d / %2\$d",
    Str.BTN_ROTATE to "↻ Rotate",
    Str.BTN_ADD_PAGE to "＋ Page (Camera)",
    Str.BTN_DELETE_PAGE to "🗑 Page",
    Str.TOAST_ALL_PAGES_DELETED to "All pages deleted",
    Str.BTN_SAVE_DOC to "💾  Save Document",
    Str.FILTER_ORIGINAL to "Original",
    Str.FILTER_GRAY to "Gray",
    Str.FILTER_BW to "B&W",
    Str.FILTER_ENHANCE to "Enhance",
    Str.SAVE_DIALOG_TITLE to "Save document",
    Str.SAVE_LABEL_FOLDER to "Folder",
    Str.SAVE_NONE_TEMPLATE to "None",
    Str.SAVE_LABEL_VAULT to "Vault slot (optional)",
    Str.SAVE_LABEL_FORMAT to "Format",
    Str.RADIO_JPG to "JPG pages",
    Str.TOAST_PROCESSING to "Processing pages…",
    Str.TOAST_SAVED to "Saved ✓  %1\$s",
    Str.TOAST_SAVE_FAIL to "Save failed: %1\$s",
    Str.ERR_PAGE_LOAD to "Page %1\$d couldn't load",
    Str.ERR_PAGE_SAVE to "Page %1\$d couldn't be saved",
    Str.SCAN_NAME_PREFIX to "Scan ",

    // viewer
    Str.TOAST_DOC_NOT_FOUND to "Document not found",
    Str.BTN_PREV to "◀ Prev",
    Str.BTN_NEXT to "Next ▶",
    Str.BTN_SHARE to "📤 Share",
    Str.BTN_OPEN_PDF to "📄 Open PDF",
    Str.BTN_DOWNLOAD to "⬇ Download from Drive",
    Str.BTN_BIN to "🗑 Bin",
    Str.VIEWER_PDF_MSG to "This document is in PDF form.\nTap “Open PDF” below.",
    Str.VIEWER_DRIVE_MSG to "This document is only on Google Drive right now.\nDownload it to view it here.",
    Str.INDICATOR_DRIVE to "Drive",
    Str.TOAST_NO_PDF_APP to "No app found to open PDFs — try Share instead",
    Str.TOAST_SHARE_NO_LOCAL to "No local file to share — download it first",
    Str.TOAST_DOWNLOADING to "Downloading from Drive…",
    Str.TOAST_DOWNLOADED to "Downloaded ✓",
    Str.TOAST_DOWNLOAD_FAIL to "Download failed — check your Drive connection and internet",
    Str.TOAST_MOVED_TO_BIN_SHORT to "Moved to Bin",

    // settings
    Str.SETTINGS_TITLE to "⚙ Settings",
    Str.SECTION_APPEARANCE to "Appearance",
    Str.DARK_MODE_LABEL to "Dark mode",
    Str.SECTION_LANGUAGE to "Language",
    Str.DRIVE_STATUS_CONNECTED to "Status: Connected ✓",
    Str.DRIVE_STATUS_NOT to "Status: Not connected",
    Str.DRIVE_INFO to "Documents sync into the DriveScan_Workspace folder. The app can only see files it created (drive.file scope).",
    Str.INPUT_CLIENT_ID_HINT to "Google OAuth Client ID (Android type)",
    Str.BTN_SAVE_CLIENT_ID to "Save Client ID",
    Str.TOAST_CLIENT_ID_SAVED to "Client ID saved",
    Str.TOAST_NEED_CLIENT_ID to "Enter your Google OAuth Client ID first (from Google Cloud Console — see the guide)",
    Str.TOAST_CONNECT_FAIL to "Couldn't start Connect — check the Client ID",
    Str.BTN_SYNC_NOW to "🔄  Sync Now",
    Str.TOAST_SYNCING to "Starting sync…",
    Str.SYNC_RESULT to "Sync complete ✓  Upload: %1\$d, Updated: %2\$d, Downloaded: %3\$d, Deleted from Drive: %4\$d",
    Str.SYNC_FAIL to "Sync failed: %1\$s",
    Str.LAST_SYNC_NEVER to "Last sync: never",
    Str.LAST_SYNC to "Last sync: %1\$s",
    Str.BTN_DISCONNECT to "Disconnect",
    Str.TOAST_DISCONNECTED to "Drive disconnected (local files stay as they are)",
    Str.SECTION_STORAGE to "Storage",
    Str.STORAGE_INFO to "DriveScan data on this phone: %1\$s",
    Str.STORAGE_NOTE to "All scans are saved on the phone first; Drive is just a copy.",
    Str.SECTION_ABOUT to "About",
    Str.ABOUT_VERSION to "DriveScan Studio — version %1\$s",
    Str.ABOUT_TEXT to "Kotlin Multiplatform build (Android + Desktop share one codebase). Scanner, vault, bin and Drive sync — all on-device, with no paid backend.",
    Str.SECTION_APP_UPDATE to "App Update",
    Str.UPDATE_INSTALLED to "Installed version: %1\$s (code %2\$d)",
    Str.UPDATE_URL_HINT to "Update server URL (manifest JSON link)",
    Str.BTN_SAVE_URL to "Save URL",
    Str.TOAST_URL_SAVED to "Update URL saved",
    Str.BTN_CHECK_UPDATES to "🔄  Check for updates",
    Str.TOAST_URL_EMPTY to "Set the update server URL first",
    Str.TOAST_CHECKING to "Checking for updates…",
    Str.TOAST_UP_TO_DATE to "App is up to date ✓",
    Str.TOAST_CHECK_FAIL to "Update check failed: %1\$s",
    Str.UPDATE_AVAILABLE_TITLE to "Update available",
    Str.UPDATE_AVAILABLE_MSG to "Version %1\$s is ready.\n%2\$s",
    Str.BTN_DOWNLOAD_INSTALL to "Download & Install",
    Str.BTN_LATER to "Later",
    Str.UPDATE_DOWNLOADING_TITLE to "Downloading update",
    Str.UPDATE_PROGRESS_PCT to "Downloaded %1\$d%%",
    Str.UPDATE_PROGRESS_BYTES to "Downloading…",
    Str.TOAST_UPDATE_DOWNLOAD_FAIL to "Update download failed: %1\$s",
    Str.TOAST_ALLOW_INSTALLS to "Allow installs from this app, then tap Download & Install again",
    Str.TOAST_INSTALLER_FAIL to "Couldn't open the installer",

    // auth
    Str.AUTH_ERR_REJECTED to "Google sign-in declined: %1\$s",
    Str.AUTH_INCOMPLETE to "Sign-in was left incomplete",
    Str.AUTH_STATE_MISMATCH to "Sign-in couldn't be verified (state mismatch) — try again",
    Str.AUTH_SESSION_EXPIRED to "Sign-in session expired — try again",
    Str.AUTH_CONNECTING to "Connecting to Google…",
    Str.AUTH_TOKEN_FAIL to "Token exchange failed (HTTP %1\$d)",
    Str.AUTH_NO_TOKEN to "Google didn't return an access token",
    Str.AUTH_NETWORK_ERROR to "Network error: %1\$s",
    Str.AUTH_SUCCESS to "Google Drive connected ✓",

    // drive sync errors
    Str.SYNC_ERR_NOT_CONNECTED to "Drive isn't connected — connect from Settings first",
    Str.SYNC_ERR_FOLDER to "Couldn't create Drive folder: %1\$s",
    Str.SYNC_ERR_DOWNLOAD to "Download failed (HTTP %1\$d)",
    Str.SYNC_ERR_API to "Drive API error (HTTP %1\$d): %2\$s"
)

private val HI_TABLE: Map<Str, String> = mapOf(
    // navigation / shared
    Str.NAV_HOME to "⌂  होम",
    Str.NAV_VAULT to "🔒 वॉल्ट",
    Str.NAV_BIN to "🗑 कूड़ादान",
    Str.NAV_SETTINGS to "⚙ सेटिंग्स",
    Str.BTN_SAVE to "सहेजें",
    Str.BTN_CANCEL to "रद्द करें",
    Str.BTN_DELETE to "हटाएं",
    Str.BTN_CREATE to "बनाएं",
    Str.BTN_UNLOCK to "खोलें",
    Str.PAGE_WORD to "पेज",

    // dashboard
    Str.PURGE_TOAST to "कूड़ादान से %1\$d पुराने दस्तावेज़ हमेशा के लिए हटा दिए गए",
    Str.SEARCH_HINT to "दस्तावेज़ खोजें…",
    Str.CHIP_ALL to "सभी",
    Str.CHIP_ROOT to "रूट",
    Str.CHIP_NEW_FOLDER to "＋ फ़ोल्डर",
    Str.DRIVE_CONNECTED to "☁  Google Drive — कनेक्टेड",
    Str.DRIVE_NOT_CONNECTED to "☁  Google Drive — कनेक्ट नहीं है (सेटिंग्स से जोड़ें)",
    Str.SCAN_BUTTON to "📷   दस्तावेज़ स्कैन करें",
    Str.EMPTY_NO_DOCS to "अभी कोई दस्तावेज़ नहीं है।\nनीचे स्कैन बटन दबाकर पहला स्कैन बनाएं।",
    Str.EMPTY_SEARCH to "“%1\$s” के लिए कुछ नहीं मिला।",
    Str.EMPTY_FOLDER to "इस फ़ोल्डर में अभी कोई दस्तावेज़ नहीं है।",
    Str.META_PAGES to "%1\$d पेज",
    Str.META_VAULT_SUFFIX to "🔒 वॉल्ट",
    Str.META_DRIVE_SUFFIX to "☁ ड्राइव",
    Str.ACTION_OPEN to "खोलें",
    Str.ACTION_RENAME to "नाम बदलें",
    Str.ACTION_MOVE_FOLDER to "फ़ोल्डर बदलें",
    Str.ACTION_SHARE to "शेयर करें",
    Str.ACTION_ADD_VAULT to "वॉल्ट में डालें",
    Str.ACTION_REMOVE_VAULT to "वॉल्ट से हटाएं",
    Str.ACTION_MOVE_TO_BIN to "कूड़ादान में डालें",
    Str.TOAST_REMOVED_FROM_VAULT to "वॉल्ट से हटा दिया गया",
    Str.TOAST_MOVED_TO_BIN to "कूड़ादान में डाल दिया गया — 30 दिन के भीतर वापस लाया जा सकता है",
    Str.RENAME_TITLE to "नाम बदलें",
    Str.INPUT_DOC_NAME to "दस्तावेज़ का नाम",
    Str.MOVE_TITLE to "फ़ोल्डर चुनें",
    Str.MOVE_ROOT_LABEL to "रूट (कोई फ़ोल्डर नहीं)",
    Str.VAULT_SLOT_TITLE to "वॉल्ट स्लॉट चुनें",
    Str.VAULT_SLOT_TAKEN to "%1\$s (भरा हुआ — बदल जाएगा)",
    Str.TOAST_ADDED_TO_VAULT to "वॉल्ट में रखा गया: %1\$s",
    Str.NEW_FOLDER_TITLE to "नया फ़ोल्डर",
    Str.INPUT_FOLDER_NAME to "फ़ोल्डर का नाम",
    Str.TOAST_SHARE_NO_FILE to "शेयर करने के लिए स्थानीय फ़ाइल नहीं है (Drive-only दस्तावेज़)",
    Str.SHARE_CHOOSER to "शेयर करें: %1\$s",

    // vault
    Str.VAULT_TITLE to "🔒 पर्सनल वॉल्ट",
    Str.VAULT_SUBTITLE to "आधार, पैन और आपके बाकी ज़रूरी दस्तावेज़ों के लिए सुरक्षित जगह।",
    Str.SET_PIN_INTRO to "पहले एक वॉल्ट PIN सेट करें (कम से कम 4 अंक)।",
    Str.INPUT_NEW_PIN to "नया PIN",
    Str.INPUT_CONFIRM_PIN to "PIN दोबारा लिखें",
    Str.BTN_SET_PIN to "PIN सेट करें",
    Str.TOAST_PIN_TOO_SHORT to "PIN कम से कम 4 अंक का हो",
    Str.TOAST_PIN_MISMATCH to "दोनों PIN मेल नहीं खाते",
    Str.TOAST_PIN_SET to "वॉल्ट PIN सेट हो गया ✓",
    Str.LOCKED_INTRO to "वॉल्ट बंद है। खोलने के लिए PIN डालें।",
    Str.INPUT_VAULT_PIN to "वॉल्ट PIN",
    Str.TOAST_WRONG_PIN to "गलत PIN",
    Str.BTN_BIOMETRIC to "👆 फ़िंगरप्रिंट / फ़ेस से खोलें",
    Str.NO_BIOMETRIC_NOTE to "इस डिवाइस पर बायोमेट्रिक लॉक उपलब्ध नहीं है — PIN ही काम करेगा।",
    Str.SLOTS_TITLE to "दस्तावेज़ स्लॉट",
    Str.SLOT_EMPTY to "खाली है — दस्तावेज़ जोड़ें",
    Str.SLOT_FILLED_NAME to "✓ %1\$s",
    Str.OTHERS_TITLE to "अन्य वॉल्ट दस्तावेज़",
    Str.BTN_LOCK_VAULT to "🔒 वॉल्ट लॉक करें",
    Str.ADD_TO_SLOT_TITLE to "%1\$s जोड़ें",
    Str.ADD_PICK_EXISTING to "📄 मौजूदा दस्तावेज़ चुनें",
    Str.ADD_SCAN_NOW to "📷 अभी स्कैन करें",
    Str.PICK_DOC_TITLE to "दस्तावेज़ चुनें",
    Str.TOAST_NO_DOC_AVAILABLE to "कोई दस्तावेज़ उपलब्ध नहीं है — पहले स्कैन करें",
    Str.TOAST_SLOT_SAVED to "वॉल्ट स्लॉट में रखा गया ✓",
    Str.SLOT_OPT_REPLACE to "कोई और दस्तावेज़ रखें",
    Str.BIO_PROMPT_TITLE to "वॉल्ट खोलें",
    Str.BIO_PROMPT_SUBTITLE to "फ़िंगरप्रिंट या फ़ेस से पुष्टि करें",
    Str.BIO_PROMPT_NEGATIVE to "PIN इस्तेमाल करें",
    Str.TOAST_BIO_FAILED to "बायोमेट्रिक काम नहीं किया — PIN इस्तेमाल करें",
    Str.TOAST_BIO_UNAVAILABLE to "बायोमेट्रिक उपलब्ध नहीं है — PIN इस्तेमाल करें",

    // bin
    Str.BIN_TITLE to "🗑 कूड़ादान",
    Str.BIN_SUBTITLE to "हटाए गए दस्तावेज़ 30 दिन तक यहाँ रहते हैं, फिर हमेशा के लिए हट जाते हैं।",
    Str.BIN_EMPTY to "कूड़ादान खाली है।",
    Str.BIN_META to "%1\$d पेज • %2\$d दिन बचे हैं (30 दिन की अवधि)",
    Str.BIN_OPT_RESTORE to "↩ वापस लाएं",
    Str.BIN_OPT_VIEW to "👁 देखें",
    Str.BIN_OPT_DELETE_FOREVER to "✖ हमेशा के लिए हटाएं",
    Str.TOAST_RESTORED to "वापस ला दिया गया ✓",
    Str.DELETE_FOREVER_TITLE to "हमेशा के लिए हटाएं?",
    Str.DELETE_FOREVER_MSG to "“%1\$s” की सभी फ़ाइलें इस फ़ोन से हट जाएंगी। यह वापस नहीं होगा।",
    Str.TOAST_DELETED_FOREVER to "हमेशा के लिए हटा दिया गया",

    // scanner
    Str.TOAST_NO_CAMERA_PERMISSION to "स्कैन के लिए कैमरा अनुमति चाहिए — आप गैलरी से import कर सकते हैं",
    Str.PILL_LOOKING to "🔍  दस्तावेज़ ढूंढा जा रहा है…",
    Str.BTN_DOC_MODE to "📄 दस्तावेज़",
    Str.BTN_ID_MODE to "🪪 ID कार्ड",
    Str.BTN_TORCH_OFF to "🔦 टॉर्च: बंद",
    Str.BTN_TORCH_ON to "🔦 टॉर्च: चालू",
    Str.BTN_GALLERY to "🖼 गैलरी",
    Str.BTN_DONE to "आगे बढ़ें →",
    Str.COUNT_NONE to "अभी कोई पेज नहीं",
    Str.COUNT_ONE to "1 पेज तैयार",
    Str.COUNT_MANY to "%1\$d पेज तैयार",
    Str.TOAST_NO_CAMERA_FOUND to "इस डिवाइस पर कैमरा नहीं मिला",
    Str.TOAST_PAGE_CAPTURED to "पेज %1\$d कैप्चर हुआ ✓",
    Str.TOAST_CAPTURE_SAVE_FAIL to "कैप्चर सहेजा नहीं जा सका: %1\$s",
    Str.TOAST_CAMERA_OPEN_FAIL to "कैमरा नहीं खुल पाया (त्रुटि %1\$d)",
    Str.TOAST_CAMERA_ERROR to "कैमरा त्रुटि: %1\$s",
    Str.TOAST_PREVIEW_FAIL to "कैमरा प्रीव्यू शुरू नहीं हुआ",
    Str.TOAST_SESSION_ERROR to "कैमरा सत्र त्रुटि: %1\$s",
    Str.TOAST_CAMERA_NOT_READY_CAPTURE to "कैमरा तैयार नहीं है — एक सेकंड रुककर फिर कोशिश करें",
    Str.TOAST_CAPTURE_FAIL to "कैप्चर विफल: %1\$s",
    Str.TOAST_CAMERA_NOT_READY to "कैमरा तैयार नहीं है",
    Str.TOAST_TORCH_UNAVAILABLE to "इस डिवाइस पर टॉर्च उपलब्ध नहीं है",
    Str.IMPORT_ONE to "1 फ़ोटो import हुआ ✓",
    Str.IMPORT_MANY to "%1\$d फ़ोटो import हुए ✓",
    Str.TOAST_IMPORT_FAIL to "फ़ोटो import नहीं हो पाए",
    Str.TOAST_NEED_ONE_PAGE to "पहले कम से कम एक पेज capture या import करें",

    // editor
    Str.EDITOR_TITLE to "स्कैन संपादित करें",
    Str.TOAST_NO_PAGE_FOUND to "कोई पेज नहीं मिला",
    Str.TOAST_PAGE_LOAD_FAIL to "पेज load नहीं हुआ",
    Str.PAGE_LABEL to "पेज %1\$d / %2\$d",
    Str.BTN_ROTATE to "↻ घुमाएं",
    Str.BTN_ADD_PAGE to "＋ पेज (कैमरा)",
    Str.BTN_DELETE_PAGE to "🗑 पेज",
    Str.TOAST_ALL_PAGES_DELETED to "सभी पेज हटा दिए गए",
    Str.BTN_SAVE_DOC to "💾  दस्तावेज़ सहेजें",
    Str.FILTER_ORIGINAL to "Original",
    Str.FILTER_GRAY to "ग्रे",
    Str.FILTER_BW to "ब्लैक-व्हाइट",
    Str.FILTER_ENHANCE to "बेहतर करें",
    Str.SAVE_DIALOG_TITLE to "दस्तावेज़ सहेजें",
    Str.SAVE_LABEL_FOLDER to "फ़ोल्डर",
    Str.SAVE_NONE_TEMPLATE to "कोई नहीं",
    Str.SAVE_LABEL_VAULT to "वॉल्ट स्लॉट (वैकल्पिक)",
    Str.SAVE_LABEL_FORMAT to "फ़ॉर्मेट",
    Str.RADIO_JPG to "JPG पेज",
    Str.TOAST_PROCESSING to "पेज तैयार हो रहे हैं…",
    Str.TOAST_SAVED to "सहेजा गया ✓  %1\$s",
    Str.TOAST_SAVE_FAIL to "सहेजना विफल: %1\$s",
    Str.ERR_PAGE_LOAD to "पेज %1\$d load नहीं हुआ",
    Str.ERR_PAGE_SAVE to "पेज %1\$d सहेजा नहीं जा सका",
    Str.SCAN_NAME_PREFIX to "स्कैन ",

    // viewer
    Str.TOAST_DOC_NOT_FOUND to "दस्तावेज़ नहीं मिला",
    Str.BTN_PREV to "◀ पिछला",
    Str.BTN_NEXT to "अगला ▶",
    Str.BTN_SHARE to "📤 शेयर करें",
    Str.BTN_OPEN_PDF to "📄 PDF खोलें",
    Str.BTN_DOWNLOAD to "⬇ Drive से download करें",
    Str.BTN_BIN to "🗑 कूड़ादान",
    Str.VIEWER_PDF_MSG to "यह दस्तावेज़ PDF के रूप में है।\nनीचे “PDF खोलें” दबाएं।",
    Str.VIEWER_DRIVE_MSG to "यह दस्तावेज़ अभी केवल Google Drive पर है।\nइसे download करके यहाँ देखा जा सकता है।",
    Str.INDICATOR_DRIVE to "ड्राइव",
    Str.TOAST_NO_PDF_APP to "PDF खोलने वाला app नहीं मिला — शेयर करके देखें",
    Str.TOAST_SHARE_NO_LOCAL to "शेयर के लिए स्थानीय फ़ाइल नहीं है — पहले download करें",
    Str.TOAST_DOWNLOADING to "Drive से download हो रहा है…",
    Str.TOAST_DOWNLOADED to "डाउनलोड हो गया ✓",
    Str.TOAST_DOWNLOAD_FAIL to "download नहीं हुआ — Drive connection और internet जांचें",
    Str.TOAST_MOVED_TO_BIN_SHORT to "कूड़ादान में डाल दिया गया",

    // settings
    Str.SETTINGS_TITLE to "⚙ सेटिंग्स",
    Str.SECTION_APPEARANCE to "दिखावट",
    Str.DARK_MODE_LABEL to "डार्क मोड",
    Str.SECTION_LANGUAGE to "भाषा",
    Str.DRIVE_STATUS_CONNECTED to "स्थिति: कनेक्टेड ✓",
    Str.DRIVE_STATUS_NOT to "स्थिति: कनेक्ट नहीं है",
    Str.DRIVE_INFO to "दस्तावेज़ DriveScan_Workspace फ़ोल्डर में sync होते हैं। App केवल अपनी बनाई फ़ाइलें देख सकती है (drive.file scope)।",
    Str.INPUT_CLIENT_ID_HINT to "Google OAuth Client ID (Android type)",
    Str.BTN_SAVE_CLIENT_ID to "Client ID सहेजें",
    Str.TOAST_CLIENT_ID_SAVED to "Client ID सहेजा गया",
    Str.TOAST_NEED_CLIENT_ID to "पहले अपना Google OAuth Client ID डालें (Google Cloud Console से — गाइड देखें)",
    Str.TOAST_CONNECT_FAIL to "Connect शुरू नहीं हुआ — Client ID जांचें",
    Str.BTN_SYNC_NOW to "🔄  अभी Sync करें",
    Str.TOAST_SYNCING to "Sync शुरू हो रहा है…",
    Str.SYNC_RESULT to "Sync पूरा ✓  Upload: %1\$d, Update: %2\$d, नए download: %3\$d, Drive से delete: %4\$d",
    Str.SYNC_FAIL to "Sync विफल: %1\$s",
    Str.LAST_SYNC_NEVER to "पिछला sync: कभी नहीं",
    Str.LAST_SYNC to "पिछला sync: %1\$s",
    Str.BTN_DISCONNECT to "डिस्कनेक्ट करें",
    Str.TOAST_DISCONNECTED to "Drive disconnect हो गया (स्थानीय फ़ाइलें वैसी ही रहेंगी)",
    Str.SECTION_STORAGE to "स्टोरेज",
    Str.STORAGE_INFO to "इस फ़ोन पर DriveScan data: %1\$s",
    Str.STORAGE_NOTE to "सारे scans पहले फ़ोन में सहेजे जाते हैं; Drive सिर्फ़ एक copy है।",
    Str.SECTION_ABOUT to "जानकारी",
    Str.ABOUT_VERSION to "DriveScan Studio — version %1\$s",
    Str.ABOUT_TEXT to "Kotlin Multiplatform build (Android + Desktop एक ही shared code)। Scanner, वॉल्ट, कूड़ादान और Drive sync — सब on-device, बिना किसी paid backend के।",
    Str.SECTION_APP_UPDATE to "ऐप अपडेट",
    Str.UPDATE_INSTALLED to "इंस्टॉल वर्शन: %1\$s (कोड %2\$d)",
    Str.UPDATE_URL_HINT to "अपडेट सर्वर URL (मेनिफ़ेस्ट JSON लिंक)",
    Str.BTN_SAVE_URL to "URL सहेजें",
    Str.TOAST_URL_SAVED to "अपडेट URL सहेजा गया",
    Str.BTN_CHECK_UPDATES to "🔄  अपडेट जांचें",
    Str.TOAST_URL_EMPTY to "पहले अपडेट सर्वर URL सेट करें",
    Str.TOAST_CHECKING to "अपडेट जांचा जा रहा है…",
    Str.TOAST_UP_TO_DATE to "App नवीनतम है ✓",
    Str.TOAST_CHECK_FAIL to "अपडेट जांच विफल: %1\$s",
    Str.UPDATE_AVAILABLE_TITLE to "अपडेट उपलब्ध है",
    Str.UPDATE_AVAILABLE_MSG to "वर्शन %1\$s तैयार है।\n%2\$s",
    Str.BTN_DOWNLOAD_INSTALL to "डाउनलोड व इंस्टॉल करें",
    Str.BTN_LATER to "बाद में",
    Str.UPDATE_DOWNLOADING_TITLE to "अपडेट डाउनलोड हो रहा है",
    Str.UPDATE_PROGRESS_PCT to "%1\$d%% डाउनलोड हुआ",
    Str.UPDATE_PROGRESS_BYTES to "डाउनलोड हो रहा है…",
    Str.TOAST_UPDATE_DOWNLOAD_FAIL to "अपडेट डाउनलोड विफल: %1\$s",
    Str.TOAST_ALLOW_INSTALLS to "इस ऐप से इंस्टॉल की अनुमति दें, फिर Download & Install दोबारा दबाएं",
    Str.TOAST_INSTALLER_FAIL to "इंस्टॉलर नहीं खुल सका",

    // auth
    Str.AUTH_ERR_REJECTED to "Google sign-in रद्द: %1\$s",
    Str.AUTH_INCOMPLETE to "sign-in अधूरा रहा",
    Str.AUTH_STATE_MISMATCH to "sign-in सत्यापित नहीं हुआ (state mismatch) — दोबारा कोशिश करें",
    Str.AUTH_SESSION_EXPIRED to "sign-in सत्र खत्म हो गया — दोबारा कोशिश करें",
    Str.AUTH_CONNECTING to "Google से connect हो रहा है…",
    Str.AUTH_TOKEN_FAIL to "टोकन exchange विफल (HTTP %1\$d)",
    Str.AUTH_NO_TOKEN to "Google ने access token नहीं दिया",
    Str.AUTH_NETWORK_ERROR to "नेटवर्क त्रुटि: %1\$s",
    Str.AUTH_SUCCESS to "Google Drive connect हो गया ✓",

    // drive sync errors
    Str.SYNC_ERR_NOT_CONNECTED to "Drive connect नहीं है — Settings से पहले Connect करें",
    Str.SYNC_ERR_FOLDER to "Drive फ़ोल्डर नहीं बन पाया: %1\$s",
    Str.SYNC_ERR_DOWNLOAD to "download विफल (HTTP %1\$d)",
    Str.SYNC_ERR_API to "Drive API त्रुटि (HTTP %1\$d): %2\$s"
)

private val HINGLISH_TABLE: Map<Str, String> = mapOf(
    // navigation / shared — these labels were already English in the shipped UI
    Str.NAV_HOME to "⌂  Home",
    Str.NAV_VAULT to "🔒 Vault",
    Str.NAV_BIN to "🗑 Bin",
    Str.NAV_SETTINGS to "⚙ Settings",
    Str.BTN_SAVE to "Save",
    Str.BTN_CANCEL to "Cancel",
    Str.BTN_DELETE to "Delete",
    Str.BTN_CREATE to "Banayein",
    Str.BTN_UNLOCK to "Unlock",
    Str.PAGE_WORD to "page",

    // dashboard
    Str.PURGE_TOAST to "Bin se %1\$d purane document hamesha ke liye delete hue",
    Str.SEARCH_HINT to "Documents search karein…",
    Str.CHIP_ALL to "All",
    Str.CHIP_ROOT to "Root",
    Str.CHIP_NEW_FOLDER to "＋ Folder",
    Str.DRIVE_CONNECTED to "☁  Google Drive — Connected",
    Str.DRIVE_NOT_CONNECTED to "☁  Google Drive — Not connected (Settings mein jodein)",
    Str.SCAN_BUTTON to "📷   Scan Document",
    Str.EMPTY_NO_DOCS to "Abhi koi document nahi hai.\nNeeche Scan Document dabakar pehla scan banayein.",
    Str.EMPTY_SEARCH to "“%1\$s” ke liye kuch nahi mila.",
    Str.EMPTY_FOLDER to "Is folder mein abhi koi document nahi hai.",
    Str.META_PAGES to "%1\$d page",
    Str.META_VAULT_SUFFIX to "🔒 Vault",
    Str.META_DRIVE_SUFFIX to "☁ Drive",
    Str.ACTION_OPEN to "Open",
    Str.ACTION_RENAME to "Rename",
    Str.ACTION_MOVE_FOLDER to "Folder badlein",
    Str.ACTION_SHARE to "Share",
    Str.ACTION_ADD_VAULT to "Vault mein daalein",
    Str.ACTION_REMOVE_VAULT to "Vault se hatayein",
    Str.ACTION_MOVE_TO_BIN to "Bin mein daalein",
    Str.TOAST_REMOVED_FROM_VAULT to "Vault se hataya gaya",
    Str.TOAST_MOVED_TO_BIN to "Bin mein daala gaya — 30 din ke andar restore ho sakta hai",
    Str.RENAME_TITLE to "Rename",
    Str.INPUT_DOC_NAME to "Document ka naam",
    Str.MOVE_TITLE to "Folder chunein",
    Str.MOVE_ROOT_LABEL to "Root (koi folder nahi)",
    Str.VAULT_SLOT_TITLE to "Vault slot chunein",
    Str.VAULT_SLOT_TAKEN to "%1\$s (bhara hua — badal jayega)",
    Str.TOAST_ADDED_TO_VAULT to "Vault mein rakha gaya: %1\$s",
    Str.NEW_FOLDER_TITLE to "Naya folder",
    Str.INPUT_FOLDER_NAME to "Folder ka naam",
    Str.TOAST_SHARE_NO_FILE to "Share karne ke liye local file nahi hai (Drive-only document)",
    Str.SHARE_CHOOSER to "Share: %1\$s",

    // vault
    Str.VAULT_TITLE to "🔒 Personal Vault",
    Str.VAULT_SUBTITLE to "Aadhaar, PAN aur baaki zaroori documents ke liye surakshit jagah.",
    Str.SET_PIN_INTRO to "Pehle ek Vault PIN set karein (kam se kam 4 digit).",
    Str.INPUT_NEW_PIN to "Naya PIN",
    Str.INPUT_CONFIRM_PIN to "PIN dobara likhein",
    Str.BTN_SET_PIN to "PIN Set karein",
    Str.TOAST_PIN_TOO_SHORT to "PIN kam se kam 4 digit ka ho",
    Str.TOAST_PIN_MISMATCH to "Dono PIN match nahi hue",
    Str.TOAST_PIN_SET to "Vault PIN set ho gaya ✓",
    Str.LOCKED_INTRO to "Vault locked hai. Kholein ke liye PIN daalein.",
    Str.INPUT_VAULT_PIN to "Vault PIN",
    Str.TOAST_WRONG_PIN to "Galat PIN",
    Str.BTN_BIOMETRIC to "👆 Fingerprint / Face se kholein",
    Str.NO_BIOMETRIC_NOTE to "Is device par biometric lock uplabdh nahi hai — PIN hi kaam karega.",
    Str.SLOTS_TITLE to "Document slots",
    Str.SLOT_EMPTY to "Khali hai — document jodein",
    Str.SLOT_FILLED_NAME to "✓ %1\$s",
    Str.OTHERS_TITLE to "Baaki vault documents",
    Str.BTN_LOCK_VAULT to "🔒 Vault lock karein",
    Str.ADD_TO_SLOT_TITLE to "%1\$s jodein",
    Str.ADD_PICK_EXISTING to "📄 Existing document chunein",
    Str.ADD_SCAN_NOW to "📷 Abhi scan karein",
    Str.PICK_DOC_TITLE to "Document chunein",
    Str.TOAST_NO_DOC_AVAILABLE to "Koi document available nahi hai — pehle scan karein",
    Str.TOAST_SLOT_SAVED to "Vault slot mein rakha gaya ✓",
    Str.SLOT_OPT_REPLACE to "Doosra document rakhein",
    Str.BIO_PROMPT_TITLE to "Vault Unlock",
    Str.BIO_PROMPT_SUBTITLE to "Fingerprint ya face se verify karein",
    Str.BIO_PROMPT_NEGATIVE to "PIN use karein",
    Str.TOAST_BIO_FAILED to "Biometric nahi chala — PIN use karein",
    Str.TOAST_BIO_UNAVAILABLE to "Biometric available nahi hai — PIN use karein",

    // bin
    Str.BIN_TITLE to "🗑 Bin",
    Str.BIN_SUBTITLE to "Delete hue documents 30 din tak yahan rehte hain, phir hamesha ke liye hat jate hain.",
    Str.BIN_EMPTY to "Bin khali hai.",
    Str.BIN_META to "%1\$d page • %2\$d din bache hain (30-day retention)",
    Str.BIN_OPT_RESTORE to "↩ Restore karein",
    Str.BIN_OPT_VIEW to "👁 Dekhein",
    Str.BIN_OPT_DELETE_FOREVER to "✖ Hamesha ke liye delete",
    Str.TOAST_RESTORED to "Restore ho gaya ✓",
    Str.DELETE_FOREVER_TITLE to "Hamesha ke liye delete?",
    Str.DELETE_FOREVER_MSG to "“%1\$s” ki saari files phone se hat jayengi. Yeh wapas nahi hoga.",
    Str.TOAST_DELETED_FOREVER to "Hamesha ke liye delete ho gaya",

    // scanner
    Str.TOAST_NO_CAMERA_PERMISSION to "Camera permission ke bina scan nahi hoga — gallery se import kar sakte hain",
    Str.PILL_LOOKING to "🔍  Looking for document…",
    Str.BTN_DOC_MODE to "📄 Document",
    Str.BTN_ID_MODE to "🪪 ID Card",
    Str.BTN_TORCH_OFF to "🔦 Torch: Off",
    Str.BTN_TORCH_ON to "🔦 Torch: On",
    Str.BTN_GALLERY to "🖼 Gallery",
    Str.BTN_DONE to "Done →",
    Str.COUNT_NONE to "Koi page nahi",
    Str.COUNT_ONE to "1 page ready",
    Str.COUNT_MANY to "%1\$d pages ready",
    Str.TOAST_NO_CAMERA_FOUND to "Is device par camera nahi mila",
    Str.TOAST_PAGE_CAPTURED to "Page %1\$d capture hua ✓",
    Str.TOAST_CAPTURE_SAVE_FAIL to "Capture save nahi hua: %1\$s",
    Str.TOAST_CAMERA_OPEN_FAIL to "Camera khul nahi paya (error %1\$d)",
    Str.TOAST_CAMERA_ERROR to "Camera error: %1\$s",
    Str.TOAST_PREVIEW_FAIL to "Camera preview start nahi hua",
    Str.TOAST_SESSION_ERROR to "Camera session error: %1\$s",
    Str.TOAST_CAMERA_NOT_READY_CAPTURE to "Camera ready nahi hai — ek second ruk kar dobara try karein",
    Str.TOAST_CAPTURE_FAIL to "Capture fail: %1\$s",
    Str.TOAST_CAMERA_NOT_READY to "Camera ready nahi hai",
    Str.TOAST_TORCH_UNAVAILABLE to "Torch is device par available nahi hai",
    Str.IMPORT_ONE to "1 photo import hua ✓",
    Str.IMPORT_MANY to "%1\$d photo import hue ✓",
    Str.TOAST_IMPORT_FAIL to "Photo import nahi ho payi",
    Str.TOAST_NEED_ONE_PAGE to "Pehle kam se kam ek page capture ya import karein",

    // editor
    Str.EDITOR_TITLE to "Scan Edit karein",
    Str.TOAST_NO_PAGE_FOUND to "Koi page nahi mila",
    Str.TOAST_PAGE_LOAD_FAIL to "Page load nahi hua",
    Str.PAGE_LABEL to "Page %1\$d / %2\$d",
    Str.BTN_ROTATE to "↻ Rotate",
    Str.BTN_ADD_PAGE to "＋ Page (Camera)",
    Str.BTN_DELETE_PAGE to "🗑 Page",
    Str.TOAST_ALL_PAGES_DELETED to "Saare page delete ho gaye",
    Str.BTN_SAVE_DOC to "💾  Save Document",
    Str.FILTER_ORIGINAL to "Original",
    Str.FILTER_GRAY to "Gray",
    Str.FILTER_BW to "B&W",
    Str.FILTER_ENHANCE to "Enhance",
    Str.SAVE_DIALOG_TITLE to "Document save karein",
    Str.SAVE_LABEL_FOLDER to "Folder",
    Str.SAVE_NONE_TEMPLATE to "Koi nahi",
    Str.SAVE_LABEL_VAULT to "Vault slot (optional)",
    Str.SAVE_LABEL_FORMAT to "Format",
    Str.RADIO_JPG to "JPG pages",
    Str.TOAST_PROCESSING to "Pages process ho rahe hain…",
    Str.TOAST_SAVED to "Save ho gaya ✓  %1\$s",
    Str.TOAST_SAVE_FAIL to "Save fail: %1\$s",
    Str.ERR_PAGE_LOAD to "Page %1\$d load nahi hua",
    Str.ERR_PAGE_SAVE to "Page %1\$d save nahi hua",
    Str.SCAN_NAME_PREFIX to "Scan ",

    // viewer
    Str.TOAST_DOC_NOT_FOUND to "Document nahi mila",
    Str.BTN_PREV to "◀ Prev",
    Str.BTN_NEXT to "Next ▶",
    Str.BTN_SHARE to "📤 Share",
    Str.BTN_OPEN_PDF to "📄 PDF Kholein",
    Str.BTN_DOWNLOAD to "⬇ Drive se Download",
    Str.BTN_BIN to "🗑 Bin",
    Str.VIEWER_PDF_MSG to "Yeh document PDF ke roop mein hai.\nNeeche “PDF Kholein” dabayein.",
    Str.VIEWER_DRIVE_MSG to "Yeh document abhi sirf Google Drive par hai.\nDownload karke yahan dekha ja sakta hai.",
    Str.INDICATOR_DRIVE to "Drive",
    Str.TOAST_NO_PDF_APP to "PDF kholne wala app nahi mila — Share se bhej kar dekhein",
    Str.TOAST_SHARE_NO_LOCAL to "Share ke liye local file nahi hai — pehle download karein",
    Str.TOAST_DOWNLOADING to "Drive se download ho raha hai…",
    Str.TOAST_DOWNLOADED to "Download ho gaya ✓",
    Str.TOAST_DOWNLOAD_FAIL to "Download nahi hua — Drive connection aur net check karein",
    Str.TOAST_MOVED_TO_BIN_SHORT to "Bin mein daala gaya",

    // settings
    Str.SETTINGS_TITLE to "⚙ Settings",
    Str.SECTION_APPEARANCE to "Appearance",
    Str.DARK_MODE_LABEL to "Dark mode",
    Str.SECTION_LANGUAGE to "Language",
    Str.DRIVE_STATUS_CONNECTED to "Status: Connected ✓",
    Str.DRIVE_STATUS_NOT to "Status: Not connected",
    Str.DRIVE_INFO to "Documents DriveScan_Workspace folder mein sync hote hain. App sirf apni banayi files dekh sakti hai (drive.file scope).",
    Str.INPUT_CLIENT_ID_HINT to "Google OAuth Client ID (Android type)",
    Str.BTN_SAVE_CLIENT_ID to "Client ID Save karein",
    Str.TOAST_CLIENT_ID_SAVED to "Client ID save ho gaya",
    Str.TOAST_NEED_CLIENT_ID to "Pehle apna Google OAuth Client ID daalein (Google Cloud Console se — steps ke liye guide dekhein)",
    Str.TOAST_CONNECT_FAIL to "Connect start nahi hua — Client ID check karein",
    Str.BTN_SYNC_NOW to "🔄  Sync Now",
    Str.TOAST_SYNCING to "Sync shuru ho raha hai…",
    Str.SYNC_RESULT to "Sync complete ✓  Upload: %1\$d, Update: %2\$d, Naye download: %3\$d, Drive se delete: %4\$d",
    Str.SYNC_FAIL to "Sync fail: %1\$s",
    Str.LAST_SYNC_NEVER to "Last sync: kabhi nahi",
    Str.LAST_SYNC to "Last sync: %1\$s",
    Str.BTN_DISCONNECT to "Disconnect",
    Str.TOAST_DISCONNECTED to "Drive disconnect ho gaya (local files waise ke waise rahenge)",
    Str.SECTION_STORAGE to "Storage",
    Str.STORAGE_INFO to "Is phone par DriveScan data: %1\$s",
    Str.STORAGE_NOTE to "Saare scans pehle phone mein save hote hain; Drive sirf ek copy hai.",
    Str.SECTION_ABOUT to "About",
    Str.ABOUT_VERSION to "DriveScan Studio — version %1\$s",
    Str.ABOUT_TEXT to "Kotlin Multiplatform build (Android + Desktop ek hi shared code). Scanner, vault, bin aur Drive sync — sab on-device, bina kisi paid backend ke.",
    Str.SECTION_APP_UPDATE to "App Update",
    Str.UPDATE_INSTALLED to "Installed version: %1\$s (code %2\$d)",
    Str.UPDATE_URL_HINT to "Update server URL (manifest JSON link)",
    Str.BTN_SAVE_URL to "URL Save karein",
    Str.TOAST_URL_SAVED to "Update URL save ho gaya",
    Str.BTN_CHECK_UPDATES to "🔄  Update check karein",
    Str.TOAST_URL_EMPTY to "Pehle update server URL set karein",
    Str.TOAST_CHECKING to "Update check ho raha hai…",
    Str.TOAST_UP_TO_DATE to "App up to date hai ✓",
    Str.TOAST_CHECK_FAIL to "Update check fail: %1\$s",
    Str.UPDATE_AVAILABLE_TITLE to "Update available hai",
    Str.UPDATE_AVAILABLE_MSG to "Version %1\$s ready hai.\n%2\$s",
    Str.BTN_DOWNLOAD_INSTALL to "Download & Install karein",
    Str.BTN_LATER to "Baad mein",
    Str.UPDATE_DOWNLOADING_TITLE to "Update download ho raha hai",
    Str.UPDATE_PROGRESS_PCT to "%1\$d%% download hua",
    Str.UPDATE_PROGRESS_BYTES to "Download ho raha hai…",
    Str.TOAST_UPDATE_DOWNLOAD_FAIL to "Update download fail: %1\$s",
    Str.TOAST_ALLOW_INSTALLS to "Is app se install ki anumati dein, phir Download & Install dobara dabayein",
    Str.TOAST_INSTALLER_FAIL to "Installer khul nahi paya",

    // auth
    Str.AUTH_ERR_REJECTED to "Google sign-in radda: %1\$s",
    Str.AUTH_INCOMPLETE to "Sign-in adhura raha",
    Str.AUTH_STATE_MISMATCH to "Sign-in verify nahi hua (state mismatch) — dobara try karein",
    Str.AUTH_SESSION_EXPIRED to "Sign-in session expire ho gaya — dobara try karein",
    Str.AUTH_CONNECTING to "Google se connect ho raha hai…",
    Str.AUTH_TOKEN_FAIL to "Token exchange fail (HTTP %1\$d)",
    Str.AUTH_NO_TOKEN to "Google ne access token nahi diya",
    Str.AUTH_NETWORK_ERROR to "Network error: %1\$s",
    Str.AUTH_SUCCESS to "Google Drive connect ho gaya ✓",

    // drive sync errors
    Str.SYNC_ERR_NOT_CONNECTED to "Drive connect nahi hai — Settings se pehle Connect karein",
    Str.SYNC_ERR_FOLDER to "Drive folder nahi ban paya: %1\$s",
    Str.SYNC_ERR_DOWNLOAD to "Download fail (HTTP %1\$d)",
    Str.SYNC_ERR_API to "Drive API error (HTTP %1\$d): %2\$s"
)
