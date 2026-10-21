package com.drivescan.kmp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.drivescan.kmp.data.PersistentStore
import com.drivescan.kmp.ui.Lang
import com.drivescan.kmp.ui.Str
import com.drivescan.kmp.ui.applyBackdrop
import com.drivescan.kmp.ui.bodyText
import com.drivescan.kmp.ui.bottomNav
import com.drivescan.kmp.ui.card
import com.drivescan.kmp.ui.dp
import com.drivescan.kmp.ui.ghostButton
import com.drivescan.kmp.ui.glassSubtleBg
import com.drivescan.kmp.ui.inputField
import com.drivescan.kmp.ui.palette
import com.drivescan.kmp.ui.primaryButton
import com.drivescan.kmp.ui.rounded
import com.drivescan.kmp.ui.sectionTitle
import com.drivescan.kmp.ui.smallText
import com.drivescan.kmp.ui.styleGlassDialog
import com.drivescan.kmp.ui.titleText
import com.drivescan.kmp.ui.tr
import com.drivescan.kmp.ui.trf
import com.drivescan.shared.PinHasher
import com.drivescan.shared.ScanDocument
import com.drivescan.shared.VaultTemplates

/**
 * Personal Vault: biometric (framework BiometricPrompt) or PBKDF2 PIN lock
 * over the 10 pre-named Indian document slots. Locks again when the user
 * leaves (except while flowing into the scanner/picker/viewer from here).
 *
 * Honest scope: this is an app lock — the PIN is stored only as a PBKDF2
 * record and gates the UI; document files are the app's private files.
 */
class VaultActivity : Activity() {

    private val store by lazy { PersistentStore.get(this) }
    private val palette by lazy { palette() }
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private var unlocked = false
    private var internalNav = false
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            applyBackdrop(palette)
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(container)
        })
        root.addView(bottomNav(palette, "vault"))
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        internalNav = false
        // Re-render so a language change made elsewhere shows up here too.
        Lang.init(this)
        render()
    }

    override fun onPause() {
        super.onPause()
        if (!internalNav && unlocked) {
            unlocked = false
            render()
        }
    }

    private fun pinRecord(): String? = prefs.getString(PIN_KEY, null)

    private fun render() {
        container.removeAllViews()
        container.addView(titleText(tr(Str.VAULT_TITLE), palette, 22f))
        container.addView(
            smallText(
                tr(Str.VAULT_SUBTITLE),
                palette
            ).apply { setPadding(0, dp(4), 0, dp(10)) }
        )
        when {
            pinRecord() == null -> renderSetPin()
            !unlocked -> renderLocked()
            else -> renderUnlocked()
        }
    }

    /** Centred glass lock medallion shown on the lock / set-PIN cards
     *  (Stitch vault mockup). */
    private fun lockGlyphCircle(): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(2), 0, dp(12))
        }
        wrap.addView(TextView(this).apply {
            text = "🔒"
            textSize = 30f
            gravity = android.view.Gravity.CENTER
            background = glassSubtleBg(palette, 999)
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
        })
        return wrap
    }

    /** Per-template glyph for the slot icon wells. */
    private fun templateGlyph(id: String): String = when (id) {
        "aadhaar" -> "🪪"
        "pan" -> "💳"
        "passport" -> "🛂"
        "driving-licence" -> "🚗"
        "voter-id" -> "🗳"
        "ration-card" -> "📕"
        "marksheet-10", "marksheet-12" -> "🎓"
        "degree" -> "📜"
        "bank-passbook" -> "🏦"
        else -> "📄"
    }

    private fun renderSetPin() {
        val cardLayout = card(palette)
        cardLayout.addView(lockGlyphCircle())
        cardLayout.addView(bodyText(tr(Str.SET_PIN_INTRO), palette))
        val pin = inputField(tr(Str.INPUT_NEW_PIN), palette).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        cardLayout.addView(pin)
        val confirm = inputField(tr(Str.INPUT_CONFIRM_PIN), palette).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        cardLayout.addView(confirm)
        val btn = primaryButton(tr(Str.BTN_SET_PIN)).apply {
            setOnClickListener {
                val p1 = pin.text.toString().trim()
                val p2 = confirm.text.toString().trim()
                when {
                    p1.length < 4 -> toast(tr(Str.TOAST_PIN_TOO_SHORT))
                    p1 != p2 -> toast(tr(Str.TOAST_PIN_MISMATCH))
                    else -> {
                        prefs.edit().putString(PIN_KEY, PinHasher.newRecord(p1)).apply()
                        unlocked = true
                        toast(tr(Str.TOAST_PIN_SET))
                        render()
                    }
                }
            }
        }
        cardLayout.addView(btn)
        container.addView(cardLayout)
    }

    private fun renderLocked() {
        val cardLayout = card(palette)
        cardLayout.addView(lockGlyphCircle())
        cardLayout.addView(bodyText(tr(Str.LOCKED_INTRO), palette))
        val pin = inputField(tr(Str.INPUT_VAULT_PIN), palette).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        cardLayout.addView(pin)
        val unlockBtn = primaryButton(tr(Str.BTN_UNLOCK)).apply {
            setOnClickListener {
                val entered = pin.text.toString().trim()
                val record = pinRecord()
                if (record != null && PinHasher.verify(entered, record)) {
                    unlocked = true
                    render()
                } else {
                    toast(tr(Str.TOAST_WRONG_PIN))
                }
            }
        }
        cardLayout.addView(unlockBtn)
        if (biometricsAvailable()) {
            val bioBtn = ghostButton(tr(Str.BTN_BIOMETRIC), palette).apply {
                setOnClickListener { promptBiometric() }
            }
            cardLayout.addView(bioBtn)
        } else {
            cardLayout.addView(
                smallText(tr(Str.NO_BIOMETRIC_NOTE), palette)
                    .apply { setPadding(0, dp(8), 0, 0) }
            )
        }
        container.addView(cardLayout)
    }

    private fun renderUnlocked() {
        container.addView(sectionTitle(tr(Str.SLOTS_TITLE), palette))
        for (template in VaultTemplates.all) {
            val doc = store.docForTemplate(template.id)
            val row = card(palette)
            // v1.3.0: glass tile row — icon well + name/status (Stitch vault).
            val rowInner = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            rowInner.addView(TextView(this).apply {
                text = templateGlyph(template.id)
                textSize = 19f
                gravity = android.view.Gravity.CENTER
                background = if (doc != null) {
                    rounded(palette.chipBg, 12)
                } else {
                    glassSubtleBg(palette, 12)
                }
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                    marginEnd = dp(12)
                }
            })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(bodyText(template.displayName, palette, 15.5f))
            col.addView(
                smallText(
                    if (doc != null) trf(Str.SLOT_FILLED_NAME, doc.name) else tr(Str.SLOT_EMPTY),
                    palette
                )
            )
            rowInner.addView(col)
            row.addView(rowInner)
            row.setOnClickListener {
                if (doc != null) openDoc(doc) else showAddToSlot(template.id, template.displayName)
            }
            row.setOnLongClickListener {
                if (doc != null) showFilledSlotOptions(doc, template.displayName)
                true
            }
            container.addView(row)
        }

        val others = store.vaultDocuments().filter { it.vaultTemplateId == null }
        if (others.isNotEmpty()) {
            container.addView(sectionTitle(tr(Str.OTHERS_TITLE), palette))
            for (doc in others) {
                val row = card(palette)
                row.addView(bodyText(doc.name, palette, 15f))
                row.addView(smallText(trf(Str.META_PAGES, doc.pageCount), palette))
                row.setOnClickListener { openDoc(doc) }
                container.addView(row)
            }
        }

        val lockBtn = ghostButton(tr(Str.BTN_LOCK_VAULT), palette).apply {
            setOnClickListener {
                unlocked = false
                render()
            }
        }
        container.addView(lockBtn)
    }

    // ---- actions --------------------------------------------------------------

    private fun openDoc(doc: ScanDocument) {
        internalNav = true
        startActivity(Intent(this, DocViewerActivity::class.java).putExtra("docId", doc.id))
    }

    private fun showAddToSlot(templateId: String, templateName: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(trf(Str.ADD_TO_SLOT_TITLE, templateName))
            .setItems(arrayOf(tr(Str.ADD_PICK_EXISTING), tr(Str.ADD_SCAN_NOW))) { _, which ->
                if (which == 0) {
                    pickExistingDoc(templateId)
                } else {
                    internalNav = true
                    startActivity(
                        Intent(this, ScannerActivity::class.java)
                            .putExtra("vaultTemplateId", templateId)
                            // ID-type templates start the scanner in ID-card mode
                            // (front + back composed onto one page).
                            .putExtra("idCardMode", templateId in ID_TEMPLATE_IDS)
                    )
                }
            }
            .create()
        styleGlassDialog(dialog, palette)
        dialog.show()
    }

    private fun pickExistingDoc(templateId: String) {
        val candidates = store.documents().filter { !it.inVault }
        if (candidates.isEmpty()) {
            toast(tr(Str.TOAST_NO_DOC_AVAILABLE))
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr(Str.PICK_DOC_TITLE))
            .setItems(candidates.map { it.name }.toTypedArray()) { _, which ->
                assignToSlot(candidates[which], templateId)
            }
            .create()
        styleGlassDialog(dialog, palette)
        dialog.show()
    }

    private fun assignToSlot(doc: ScanDocument, templateId: String) {
        val existing = store.docForTemplate(templateId)
        if (existing != null && existing.id != doc.id) {
            store.clearTemplate(existing.id)
        }
        store.setVault(doc.id, templateId)
        toast(tr(Str.TOAST_SLOT_SAVED))
        render()
    }

    private fun showFilledSlotOptions(doc: ScanDocument, templateName: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(templateName)
            .setItems(arrayOf(tr(Str.ACTION_OPEN), tr(Str.SLOT_OPT_REPLACE), tr(Str.ACTION_REMOVE_VAULT))) { _, which ->
                when (which) {
                    0 -> openDoc(doc)
                    1 -> showAddToSlot(doc.vaultTemplateId ?: return@setItems, templateName)
                    2 -> {
                        store.removeFromVault(doc.id)
                        toast(tr(Str.TOAST_REMOVED_FROM_VAULT))
                        render()
                    }
                }
            }
            .create()
        styleGlassDialog(dialog, palette)
        dialog.show()
    }

    // ---- biometrics -------------------------------------------------------------

    private fun biometricsAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val manager = getSystemService(BiometricManager::class.java)
            manager?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    private fun promptBiometric() {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            val executor = if (Build.VERSION.SDK_INT >= 28) mainExecutor else null
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult?
                ) {
                    runOnUiThread {
                        unlocked = true
                        render()
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    runOnUiThread { toast(tr(Str.TOAST_BIO_FAILED)) }
                }
            }
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(tr(Str.BIO_PROMPT_TITLE))
                .setSubtitle(tr(Str.BIO_PROMPT_SUBTITLE))
                .setNegativeButton(tr(Str.BIO_PROMPT_NEGATIVE), executor ?: mainExecutor) { _, _ -> }
                .build()
            prompt.authenticate(CancellationSignal(), executor ?: mainExecutor, callback)
        } catch (e: Exception) {
            toast(tr(Str.TOAST_BIO_UNAVAILABLE))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val PREFS_NAME = "drivescan_kmp"
        private const val PIN_KEY = "vault_pin_record"

        /** Vault templates that are ID cards (front+back single-page scans). */
        val ID_TEMPLATE_IDS = setOf("aadhaar", "pan", "driving-licence", "voter-id")
    }
}
